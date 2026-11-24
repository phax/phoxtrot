/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phoxtrot.xpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based robustness tests for {@link XPathCompiler}: any text either
 * compiles or is rejected with an {@link XPathException} carrying an error
 * code, and syntax errors carry a position inside the expression text.
 *
 * @author Philip Helger
 */
public final class XPathCompilerPropertyTest
{
  /** Valid expressions covering most of the grammar */
  private static final String [] VALID = { "1 + 2 * 3 idiv 4 mod 5",
                                           "//a[@x = '1']/b[last()]",
                                           "for $i in 1 to 10 return $i * $i",
                                           "some $x in (1, 2) satisfies $x gt 1",
                                           "every $x in //a satisfies exists($x/@y)",
                                           "let $a := 1, $b := $a + 1 return ($a, $b)",
                                           "if (//a) then 'y' else 'n'",
                                           "map { 'a' : 1, 2 : [3, 4] }?a",
                                           "[1, 2, 3]?*",
                                           "array { 1 to 3 }(2)",
                                           "function ($x as xs:integer) as xs:integer { $x + 1 }(3)",
                                           "fn:concat#3('a', 'b', 'c')",
                                           "substring(?, 2)('abc')",
                                           "'a' || 'b' || \"c\"",
                                           "(1, 2) ! (. * 2)",
                                           "1 instance of xs:integer",
                                           "'1' cast as xs:integer?",
                                           "'x' castable as xs:double",
                                           "() treat as empty-sequence()",
                                           "//p:a/ancestor-or-self::node()[1]",
                                           "child::text() | attribute::* except @x intersect @y",
                                           "-+-1.5e3",
                                           "(: comment (: nested :) :) 1",
                                           "Q{urn:x}local",
                                           "processing-instruction('pi')",
                                           "element(a, xs:untyped)",
                                           "$v => string() => upper-case()",
                                           "1 eq 1 and 2 ne 3 or 4 lt 5",
                                           "//a << //b",
                                           ". is .",
                                           "xs:date('2024-02-29') + xs:dayTimeDuration('P1D')",
                                           "matches('abc', '^a.c$', 'i')",
                                           "replace('abc', '(b)', '[$1]')",
                                           "format-number(1234.5, '#,##0.00')",
                                           "(1 to 5)[. mod 2 = 0][position() = 1]",
                                           "../preceding-sibling::*[2]/following::text()",
                                           "string-join(for $c in string-to-codepoints('ab') return string($c), '-')",
                                           "1.e2 + .5E-1 + 12",
                                           "'it''s' || \"say \"\"hi\"\"\"",
                                           "function-lookup(xs:QName('fn:true'), 0)()" };

  /** Characters with a meaning in XPath, as single replacements and insertions */
  private static final String SPECIAL_CHARS = "()[]{}<>=!/@$:,.?*|+-#'\"&;\\^%~`0123456789abxyzE \t\n\r\u00e9\u0000\uffff\ud83d\ude00\ud800";

  /** Tokens for random token soup */
  private static final String [] TOKENS = { "(",
                                            ")",
                                            "[",
                                            "]",
                                            "{",
                                            "}",
                                            "/",
                                            "//",
                                            "@",
                                            "$v",
                                            "$",
                                            "::",
                                            ":",
                                            ",",
                                            ".",
                                            "..",
                                            "?",
                                            "*",
                                            "|",
                                            "||",
                                            "!",
                                            "=>",
                                            "#",
                                            "1",
                                            "1.5",
                                            "1e3",
                                            "'s'",
                                            "\"s\"",
                                            "a",
                                            "p:a",
                                            "Q{urn:x}a",
                                            "child",
                                            "for",
                                            "in",
                                            "return",
                                            "let",
                                            ":=",
                                            "if",
                                            "then",
                                            "else",
                                            "some",
                                            "every",
                                            "satisfies",
                                            "map",
                                            "array",
                                            "function",
                                            "as",
                                            "instance",
                                            "of",
                                            "cast",
                                            "castable",
                                            "treat",
                                            "xs:integer",
                                            "element",
                                            "node",
                                            "text",
                                            "to",
                                            "eq",
                                            "<",
                                            "<<",
                                            "-",
                                            "+",
                                            "div",
                                            "and",
                                            "or",
                                            "(:",
                                            ":)",
                                            " " };

  private static final IGenerator <String> MUTATED = aChoices -> {
    final StringBuilder aSB = new StringBuilder (VALID[(int) aChoices.draw (VALID.length - 1)]);
    final int nMutations = 1 + (int) aChoices.draw (3);
    for (int i = 0; i < nMutations; ++i)
    {
      final int nLen = aSB.length ();
      final char cNew = SPECIAL_CHARS.charAt ((int) aChoices.draw (SPECIAL_CHARS.length () - 1));
      final int nKind = nLen == 0 ? 1 : (int) aChoices.draw (4);
      switch (nKind)
      {
        case 0:
          aSB.setCharAt ((int) aChoices.draw (nLen - 1), cNew);
          break;
        case 1:
          aSB.insert ((int) aChoices.draw (nLen), cNew);
          break;
        case 2:
          aSB.deleteCharAt ((int) aChoices.draw (nLen - 1));
          break;
        case 3:
        {
          // Duplicate a slice
          final int nStart = (int) aChoices.draw (nLen - 1);
          final int nEnd = nStart + (int) aChoices.draw (nLen - nStart);
          aSB.insert (nEnd, aSB.substring (nStart, nEnd));
          break;
        }
        default:
          aSB.setLength ((int) aChoices.draw (nLen - 1));
          break;
      }
    }
    return aSB.toString ();
  };

  private static final IGenerator <String> TOKEN_SOUP = Generators.lists (Generators.of (TOKENS), 1, 20)
                                                                  .map (x -> String.join (" ", x));

  private static final String [] [] NESTING = { { "(", ")" },
                                                { "[", "]" },
                                                { "-", "" },
                                                { "f(", ")" },
                                                { "a[", "]" },
                                                { "map{1:", "}" },
                                                { "if (1) then ", " else 2" },
                                                { "function () { ", " }" },
                                                { "$v!(", ")" } };

  /** Deeply nested expressions of mixed kinds, up to a depth of 3000 */
  private static final IGenerator <String> DEEP = (final ChoiceSequence aChoices) -> {
    final int nDepth = 1 + (int) aChoices.draw (2999);
    final StringBuilder aOpen = new StringBuilder ();
    final StringBuilder aClose = new StringBuilder ();
    final int nKinds = 1 + (int) aChoices.draw (2);
    final String [] [] aUsed = new String [nKinds] [];
    for (int i = 0; i < nKinds; ++i)
      aUsed[i] = NESTING[(int) aChoices.draw (NESTING.length - 1)];
    for (int i = 0; i < nDepth; ++i)
    {
      final String [] aKind = aUsed[i % nKinds];
      aOpen.append (aKind[0]);
      aClose.insert (0, aKind[1]);
    }
    return aOpen + "1" + aClose;
  };

  private static void _checkCompile (@NonNull final StaticContext aSC, @NonNull final String sExpr)
  {
    try
    {
      assertNotNull (XPathCompiler.compile (sExpr, aSC));
    }
    catch (final XPathException ex)
    {
      final String sCode = ex.getCode ();
      assertNotNull (sCode);
      assertTrue (sExpr + ": " + sCode, sCode.length () == 8);
      if ("XPST0003".equals (sCode))
        assertTrue (sExpr + ": syntax error without position: " + ex.getMessage (), ex.hasExpressionPosition ());
      if (ex.hasExpressionPosition ())
      {
        final int nOffset = ex.getExpressionOffset ();
        assertTrue (sExpr + ": offset " + nOffset, nOffset >= 0 && nOffset <= sExpr.length ());
        assertTrue (ex.getExpressionLine () >= 1);
        assertTrue (ex.getExpressionColumn () >= 1);
        // The line and column must describe the offset
        int nLine = 1;
        int nLineStart = 0;
        for (int i = 0; i < nOffset; ++i)
        {
          final char c = sExpr.charAt (i);
          if (c == '\n' || (c == '\r' && (i + 1 >= sExpr.length () || sExpr.charAt (i + 1) != '\n')))
          {
            nLine++;
            nLineStart = i + 1;
          }
        }
        assertEquals (sExpr, nLine, ex.getExpressionLine ());
        assertEquals (sExpr, nOffset - nLineStart + 1, ex.getExpressionColumn ());
      }
    }
  }

  @NonNull
  private static StaticContext _createStaticContext ()
  {
    final StaticContext aSC = new StaticContext ();
    aSC.declareNamespace ("p", "urn:p");
    aSC.declareVariable (new XdmQName ("v"));
    return aSC;
  }

  /**
   * @return <code>true</code> if the expression has a digit run of more than
   *         three digits, which might build a huge range or string.
   */
  private static boolean _hasLongNumber (@NonNull final String sExpr)
  {
    int nRun = 0;
    for (int i = 0; i < sExpr.length (); ++i)
    {
      if (Character.isDigit (sExpr.charAt (i)))
      {
        if (++nRun > 3)
          return true;
      }
      else
        nRun = 0;
    }
    return false;
  }

  /**
   * Compile and evaluate an expression against a small document with a step
   * limit and a time limit; only an {@link XPathException} may be raised.
   */
  private static void _checkEvaluate (@NonNull final StaticContext aSC, @NonNull final IXdmNode aDoc, @NonNull final String sExpr)
  {
    final CompiledXPath aXPath;
    try
    {
      aXPath = XPathCompiler.compile (sExpr, aSC);
    }
    catch (final XPathException ex)
    {
      return;
    }
    final DynamicContext aCtx = aXPath.createDynamicContext ()
                                      .withVariable (new XdmQName ("v"), XdmSequence.of (XdmAtomic.string ("x")))
                                      .withFocus (aDoc, 1, 1);
    aCtx.setMaxSteps (100_000);
    final long nDeadline = System.nanoTime () + 2_000_000_000L;
    aCtx.setInterrupt ( () -> System.nanoTime () > nDeadline);
    try
    {
      aXPath.evaluate (aCtx);
    }
    catch (final XPathException ex)
    {
      assertNotNull (sExpr, ex.getCode ());
    }
  }

  /** The valid expressions of the pool compile. */
  @Test
  public void testPoolIsValid ()
  {
    final StaticContext aSC = _createStaticContext ();
    for (final String sExpr : VALID)
      assertNotNull (sExpr, XPathCompiler.compile (sExpr, aSC));
  }

  /**
   * A mutated valid expression either compiles or raises an
   * {@link XPathException} with an error code, and a syntax error has a
   * position within the text whose line and column match its offset.
   */
  @Test (timeout = 60_000)
  public void testMutatedExpressions ()
  {
    final StaticContext aSC = _createStaticContext ();
    new PropertyChecker ().setTries (5000).forAll (MUTATED, sExpr -> _checkCompile (aSC, sExpr));
  }

  /** Random token sequences either compile or raise an {@link XPathException}. */
  @Test (timeout = 60_000)
  public void testTokenSoup ()
  {
    final StaticContext aSC = _createStaticContext ();
    new PropertyChecker ().setTries (5000).forAll (TOKEN_SOUP, sExpr -> _checkCompile (aSC, sExpr));
  }

  /** Arbitrary text, also with unpaired surrogates and control characters, is handled. */
  @Test (timeout = 60_000)
  public void testArbitraryText ()
  {
    final StaticContext aSC = _createStaticContext ();
    new PropertyChecker ().setTries (2000)
                          .forAll (Generators.oneOf (Generators.strings (SPECIAL_CHARS, 0, 40),
                                                     Generators.unicodeStrings (0, 40),
                                                     Generators.strings ('\u0000', '\uffff', 0, 40)),
                                   sExpr -> _checkCompile (aSC, sExpr));
  }

  /**
   * Deeply nested expressions compile or raise an {@link XPathException}; the
   * depth never causes a {@link StackOverflowError}.
   */
  @Test (timeout = 120_000)
  public void testDeepNesting ()
  {
    final StaticContext aSC = _createStaticContext ();
    new PropertyChecker ().setTries (100).forAll (DEEP, sExpr -> _checkCompile (aSC, sExpr));
  }

  /**
   * Inserting a comment or whitespace between the tokens of a token soup does
   * not change whether it compiles or the error code (XPath 3.1 section A.2.2:
   * comments and whitespace may occur between terminals).
   */
  @Test (timeout = 60_000)
  public void testCommentsBetweenTokens ()
  {
    final StaticContext aSC = _createStaticContext ();
    final IGenerator <List <String>> aTokens = Generators.lists (Generators.of (TOKENS), 1, 12)
                                                         .filter (x -> !x.contains ("(:") &&
                                                                       !x.contains (":)") &&
                                                                       !x.contains (":") &&
                                                                       !x.contains ("::") &&
                                                                       !x.contains ("$") &&
                                                                       !x.contains ("("));
    new PropertyChecker ().setTries (2000).forAll (aTokens, Generators.of (" (: c :) ", "\n", "\t "), (aList, sSep) -> {
      final String sPlain = String.join (" ", aList);
      final String sCommented = String.join (sSep, aList);
      String sPlainCode = null;
      String sCommentedCode = null;
      try
      {
        XPathCompiler.compile (sPlain, aSC);
      }
      catch (final XPathException ex)
      {
        sPlainCode = ex.getCode ();
      }
      try
      {
        XPathCompiler.compile (sCommented, aSC);
      }
      catch (final XPathException ex)
      {
        sCommentedCode = ex.getCode ();
      }
      assertEquals (sPlain + " vs. " + sCommented, sPlainCode, sCommentedCode);
    });
  }

  /**
   * A mutated valid expression that compiles evaluates against a small
   * document to a result or an {@link XPathException}, never another
   * exception. Expressions with long numbers are left out, as they may
   * legitimately build huge ranges.
   */
  @Test (timeout = 120_000)
  public void testMutatedExpressionsEvaluate ()
  {
    final StaticContext aSC = _createStaticContext ();
    final IXdmNode aDoc = XPathPropertyTestHelper.parse ("<a xmlns:p=\"urn:p\" x=\"1\"><b y=\"2\">t<p:a/></b><!--c--><?pi d?><b/></a>");
    new PropertyChecker ().setTries (3000)
                          .forAll (Generators.oneOf (MUTATED, TOKEN_SOUP).filter (x -> !_hasLongNumber (x)),
                                   sExpr -> _checkEvaluate (aSC, aDoc, sExpr));
  }
}

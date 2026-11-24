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
package com.helger.phoxtrot.xpath.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyAssume;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the regular expression functions of
 * {@link FnRegex} (F&amp;O 3.1 section 5.6). A pattern without metacharacters
 * (and any pattern with the <code>q</code> flag alone) takes a plain string
 * search; adding the flag <code>s</code>, which does not change the meaning of
 * such a pattern, forces the compiled regular expression, so the two paths
 * can be compared. Java string operations serve as reference for literal
 * patterns.
 *
 * @author Philip Helger
 */
public final class FnRegexPropertyTest
{
  private static final String [] VAR_NAMES = { "a", "b", "c", "d" };

  /** Inputs with regex metacharacters, a combining character and a supplementary character */
  private static final IGenerator <String> INPUTS = Generators.strings ("ab.*+?()[]{}|\\^$-x \n\u00e9\ud83d\ude00", 0, 12)
                                                              .filter (FnRegexPropertyTest::_isWellFormed);

  /** Patterns with metacharacters, read literally with the q flag */
  private static final IGenerator <String> LITERALS = Generators.strings ("ab.*+?()[]{}|\\^$-x\u00e9\ud83d\ude00", 1, 3)
                                                                .filter (FnRegexPropertyTest::_isWellFormed);

  /** Patterns without metacharacters, which take the plain string search */
  private static final IGenerator <String> PLAIN = Generators.strings ("abx -\u00e9", 1, 3);

  private static final IGenerator <String> REPLACEMENTS = Generators.strings ("xy$1\\0", 0, 4);

  private static final String [] REGEX_ATOMS = { "a",
                                                 "b",
                                                 "x",
                                                 ".",
                                                 "[ab]",
                                                 "[^a]",
                                                 "\\d",
                                                 "\\s",
                                                 "\\p{L}",
                                                 "\\P{Lu}",
                                                 "[a-c-[b]]",
                                                 "\\.",
                                                 "\u00e9",
                                                 "\ud83d\ude00",
                                                 "\\i",
                                                 "\\c" };
  private static final String [] QUANTIFIERS = { "", "", "", "*", "+", "?", "{1,2}", "{2}", "*?", "+?" };

  /** Valid regular expressions of the XSD regex grammar with the F&amp;O extensions */
  private static final IGenerator <String> REGEXES = aChoices -> {
    final StringBuilder aSB = new StringBuilder ();
    if (aChoices.draw (5) == 0)
      aSB.append ('^');
    _appendRegexRecursive (aChoices, aSB, 0);
    if (aChoices.draw (5) == 0)
      aSB.append ('$');
    return aSB.toString ();
  };

  private final Map <String, CompiledXPath> m_aCache = new HashMap <> ();
  private final StaticContext m_aStaticCtx = new StaticContext ();

  public FnRegexPropertyTest ()
  {
    for (final String sName : VAR_NAMES)
      m_aStaticCtx.declareVariable (new XdmQName (sName));
  }

  /** @return <code>false</code> for a string with an unpaired surrogate */
  private static boolean _isWellFormed (@NonNull final String s)
  {
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (Character.isHighSurrogate (c))
      {
        if (i + 1 >= s.length () || !Character.isLowSurrogate (s.charAt (i + 1)))
          return false;
        i++;
      }
      else
        if (Character.isLowSurrogate (c))
          return false;
    }
    return true;
  }

  /**
   * Evaluate with the strings bound to <code>$a</code>, <code>$b</code>,
   * <code>$c</code> and <code>$d</code> in this order.
   */
  @NonNull
  private XdmSequence _eval (@NonNull final String sExpr, @NonNull final String... aValues)
  {
    final CompiledXPath aXPath = m_aCache.computeIfAbsent (sExpr, x -> XPathCompiler.compile (x, m_aStaticCtx));
    DynamicContext aCtx = aXPath.createDynamicContext ();
    for (int i = 0; i < VAR_NAMES.length; ++i)
      aCtx = aCtx.withVariable (new XdmQName (VAR_NAMES[i]),
                                i < aValues.length ? XdmSequence.of (XdmAtomic.string (aValues[i])) : XdmSequence.EMPTY);
    return aXPath.evaluate (aCtx);
  }

  @NonNull
  private static List <String> _getStrings (@NonNull final XdmSequence aSeq)
  {
    final List <String> ret = new ArrayList <> ();
    for (final IXdmItem aItem : aSeq)
      ret.add (((XdmAtomic) aItem).getString ());
    return ret;
  }

  /**
   * @return The strings or the error code, so that two outcomes can be
   *         compared with one assertion.
   */
  @NonNull
  private String _getOutcome (@NonNull final String sExpr, @NonNull final String... aValues)
  {
    try
    {
      return _getStrings (_eval (sExpr, aValues)).toString ();
    }
    catch (final XPathException ex)
    {
      return "error " + ex.getCode ();
    }
  }

  /** @return The parts of the input between the non-overlapping occurrences of the separator. */
  @NonNull
  private static List <String> _splitLiteral (@NonNull final String sInput, @NonNull final String sSeparator)
  {
    final List <String> ret = new ArrayList <> ();
    if (sInput.isEmpty ())
      return ret;
    int nStart = 0;
    int nPos;
    while ((nPos = sInput.indexOf (sSeparator, nStart)) >= 0)
    {
      ret.add (sInput.substring (nStart, nPos));
      nStart = nPos + sSeparator.length ();
    }
    ret.add (sInput.substring (nStart));
    return ret;
  }

  /**
   * With the <code>q</code> flag, <code>matches</code>, <code>replace</code>
   * and <code>tokenize</code> treat the pattern (and the replacement) as
   * literal strings: the string search path agrees with the compiled regular
   * expression (flags <code>qs</code>) and with the Java string operations.
   */
  @Test
  public void testQuotedPatternMatchesLiteralSearch ()
  {
    new PropertyChecker ().setTries (2000).forAll (INPUTS, LITERALS, REPLACEMENTS, (sInput, sPattern, sReplacement) -> {
      final String sMsg = "'" + sInput + "' / '" + sPattern + "' / '" + sReplacement + "'";
      final boolean bContains = sInput.contains (sPattern);
      assertEquals (sMsg, "[" + bContains + "]", _getOutcome ("string(matches($a, $b, 'q'))", sInput, sPattern));
      assertEquals (sMsg, "[" + bContains + "]", _getOutcome ("string(matches($a, $b, 'qs'))", sInput, sPattern));

      final String sReplaced = "[" + sInput.replace (sPattern, sReplacement) + "]";
      assertEquals (sMsg, sReplaced, _getOutcome ("replace($a, $b, $c, 'q')", sInput, sPattern, sReplacement));
      assertEquals (sMsg, sReplaced, _getOutcome ("replace($a, $b, $c, 'qs')", sInput, sPattern, sReplacement));

      final String sTokens = _splitLiteral (sInput, sPattern).toString ();
      assertEquals (sMsg, sTokens, _getOutcome ("tokenize($a, $b, 'q')", sInput, sPattern));
      assertEquals (sMsg, sTokens, _getOutcome ("tokenize($a, $b, 'qs')", sInput, sPattern));
      assertEquals (sMsg, "[" + sInput + "]", _getOutcome ("string-join(tokenize($a, $b, 'q'), $b)", sInput, sPattern));
    });
  }

  /**
   * A pattern without metacharacters gives the same results on the string
   * search path (no flags) and on the regular expression path (flag
   * <code>s</code>), also for replacements with <code>$</code> and
   * <code>\</code> (which take the regular expression path) and their errors.
   */
  @Test
  public void testPlainPatternMatchesRegexPath ()
  {
    new PropertyChecker ().setTries (2000).forAll (INPUTS, PLAIN, REPLACEMENTS, (sInput, sPattern, sReplacement) -> {
      final String sMsg = "'" + sInput + "' / '" + sPattern + "' / '" + sReplacement + "'";
      assertEquals (sMsg,
                    _getOutcome ("string(matches($a, $b, 's'))", sInput, sPattern),
                    _getOutcome ("string(matches($a, $b))", sInput, sPattern));
      assertEquals (sMsg,
                    _getOutcome ("replace($a, $b, $c, 's')", sInput, sPattern, sReplacement),
                    _getOutcome ("replace($a, $b, $c)", sInput, sPattern, sReplacement));
      assertEquals (sMsg,
                    _getOutcome ("tokenize($a, $b, 's')", sInput, sPattern),
                    _getOutcome ("tokenize($a, $b)", sInput, sPattern));
      assertEquals (sMsg,
                    _getOutcome ("string(matches($a, $b, 'q'))", sInput, sPattern),
                    _getOutcome ("string(matches($a, $b))", sInput, sPattern));
    });
  }


  private static void _appendRegexRecursive (@NonNull final ChoiceSequence aChoices,
                                             @NonNull final StringBuilder aSB,
                                             final int nDepth)
  {
    final int nTerms = 1 + (int) aChoices.draw (2);
    for (int i = 0; i < nTerms; ++i)
    {
      // Groups only up to a depth of 2
      if (nDepth < 2 && aChoices.draw (4) == 0)
      {
        aSB.append ('(');
        _appendRegexRecursive (aChoices, aSB, nDepth + 1);
        if (aChoices.draw (2) == 0)
        {
          aSB.append ('|');
          _appendRegexRecursive (aChoices, aSB, nDepth + 1);
        }
        aSB.append (')');
      }
      else
        aSB.append (REGEX_ATOMS[(int) aChoices.draw (REGEX_ATOMS.length - 1)]);
      aSB.append (QUANTIFIERS[(int) aChoices.draw (QUANTIFIERS.length - 1)]);
    }
  }


  @Nullable
  private static String _getTextOfMatches (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? null : ((XdmAtomic) aSeq.get (0)).getString ();
  }

  /**
   * For a regular expression that does not match the empty string,
   * <code>analyze-string</code>, <code>replace</code>,
   * <code>tokenize</code> and <code>matches</code> agree: the match and
   * non-match parts together give the input, replacing each match gives the
   * result of <code>replace</code>, the non-match parts are the tokens, and
   * there is a match exactly if <code>matches</code> is true.
   */
  @Test
  public void testRegexFunctionsAgree ()
  {
    new PropertyChecker ().setTries (1500)
                          .forAll (INPUTS, REGEXES, Generators.of ("", "i", "s", "m", "x", "ms"), (sInput, sRegex, sFlags) -> {
                            PropertyAssume.that (!((XdmAtomic) _eval ("matches('', $b, $c)", "", sRegex, sFlags).get (0)).getBoolean ());
                            final String sMsg = "'" + sInput + "' / '" + sRegex + "' / '" + sFlags + "'";
                            assertEquals (sMsg,
                                          sInput,
                                          _getTextOfMatches (_eval ("string-join(analyze-string($a, $b, $c)/*)", sInput, sRegex, sFlags)));
                            assertEquals (sMsg,
                                          _getTextOfMatches (_eval ("replace($a, $b, '#', $c)", sInput, sRegex, sFlags)),
                                          _getTextOfMatches (_eval ("string-join(analyze-string($a, $b, $c)/* ! (if (self::fn:match) then '#' else string(.)))",
                                                                    sInput,
                                                                    sRegex,
                                                                    sFlags)));
                            assertEquals (sMsg,
                                          _getStrings (_eval ("tokenize($a, $b, $c)", sInput, sRegex, sFlags)),
                                          _getStrings (_eval ("if ($a eq '') then () else " +
                                                              "let $r := analyze-string($a, $b, $c) return " +
                                                              "for $i in 0 to count($r/fn:match) return " +
                                                              "string-join($r/fn:non-match[count(preceding-sibling::fn:match) eq $i])",
                                                              sInput,
                                                              sRegex,
                                                              sFlags)));
                            assertEquals (sMsg,
                                          _getTextOfMatches (_eval ("string(matches($a, $b, $c))", sInput, sRegex, sFlags)),
                                          _getTextOfMatches (_eval ("string(exists(analyze-string($a, $b, $c)/fn:match))",
                                                                    sInput,
                                                                    sRegex,
                                                                    sFlags)));
                          });
  }

  /**
   * Any pattern and flags text either works or is rejected with an
   * {@link XPathException} (FORX0001 for the flags, FORX0002 for the
   * pattern); the regex translation never fails with another exception.
   */
  @Test
  public void testArbitraryPatternsAreHandled ()
  {
    final IGenerator <String> aPatterns = Generators.oneOf (Generators.strings ("ab()[]{}|\\^$-.*+?,0123456789pPdDsSiIcCwW{}&#x;", 1, 10),
                                                            REGEXES.map (x -> x.substring (0, x.length () / 2)));
    final IGenerator <String> aFlags = Generators.oneOf (Generators.of ("", "q", "i", "smix"), Generators.strings ("smixqjQ!", 0, 3));
    new PropertyChecker ().setTries (3000).forAll (INPUTS, aPatterns, aFlags, (sInput, sPattern, sFlags) -> {
      for (final String sExpr : new String [] { "matches($a, $b, $c)",
                                                "replace($a, $b, 'x', $c)",
                                                "tokenize($a, $b, $c)",
                                                "analyze-string($a, $b, $c)" })
      {
        try
        {
          _eval (sExpr, sInput, sPattern, sFlags);
        }
        catch (final XPathException ex)
        {
          final String sCode = ex.getCode ();
          assertTrue (sExpr + " '" + sPattern + "' '" + sFlags + "': " + sCode,
                      sCode.equals ("FORX0001") || sCode.equals ("FORX0002") || sCode.equals ("FORX0003"));
        }
      }
    });
  }
}

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
package com.helger.phoxtrot.xslt.instr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for xsl:analyze-string (XSLT 3.0 section 17.1) and the
 * regular expression functions of XPath 3.1 (fn:analyze-string, fn:tokenize,
 * fn:replace, fn:matches) on random strings and random simple regular
 * expressions. The reference is the algorithm of section 17.1 written in Java
 * with <code>java.util.regex</code>, restricted to constructs whose meaning is
 * the same in both regular expression languages.
 *
 * @author Philip Helger
 */
public final class AnalyzeStringPropertyTest
{
  /**
   * One piece of a regular expression in the XPath syntax and in the Java
   * syntax, with the number of capturing groups it opens.
   */
  private record Piece (@NonNull String xpath, @NonNull String java, int groups, boolean backRef, boolean negClass)
  {}

  /** A generated regular expression with its flags */
  private record Regex (@NonNull String xpath, @NonNull String java, @NonNull String flags)
  {
    @NonNull
    Pattern getJavaPattern ()
    {
      int nFlags = 0;
      if (flags.indexOf ('i') >= 0)
        nFlags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
      if (flags.indexOf ('s') >= 0)
        nFlags |= Pattern.DOTALL;
      if (flags.indexOf ('q') >= 0)
        return Pattern.compile (xpath, nFlags | Pattern.LITERAL);
      return Pattern.compile (java, nFlags);
    }

    @Override
    public String toString ()
    {
      return "regex '" + xpath + "' flags '" + flags + "'";
    }
  }

  /** One segment of the partition of section 17.1: a match with its group 1, or a non-matching substring */
  private record Segment (boolean match, @NonNull String text, @NonNull String group1)
  {}

  /**
   * Characters of the input: letters in both cases, a digit, space, a dot,
   * line ends, a non-ASCII letter and a character outside the BMP. No
   * character that one of the languages treats as a line end and the other
   * does not, no non-ASCII digit.
   */
  private static final IGenerator <String> INPUTS = Generators.lists (Generators.of ("a",
                                                                                     "b",
                                                                                     "A",
                                                                                     "1",
                                                                                     " ",
                                                                                     ".",
                                                                                     "\n",
                                                                                     "\r",
                                                                                     "ä",
                                                                                     "😀"),
                                                              0,
                                                              10)
                                                      .map (l -> String.join ("", l));

  /** Atoms of the same meaning in XPath and Java (Java needs \z for the XPath $ outside multi-line mode) */
  private static final Piece [] ATOMS = { new Piece ("a", "a", 0, false, false),
                                          new Piece ("b", "b", 0, false, false),
                                          new Piece ("ä", "ä", 0, false, false),
                                          new Piece ("😀", "😀", 0, false, false),
                                          new Piece ("\\.", "\\.", 0, false, false),
                                          new Piece (".", ".", 0, false, false),
                                          new Piece ("[ab]", "[ab]", 0, false, false),
                                          new Piece ("[a-z]", "[a-z]", 0, false, false),
                                          new Piece ("[^a]", "[^a]", 0, false, true),
                                          new Piece ("[^\\s.]", "[^\\s.]", 0, false, true),
                                          new Piece ("\\d", "\\d", 0, false, false),
                                          new Piece ("\\s", "\\s", 0, false, false),
                                          new Piece ("\\S", "\\S", 0, false, true),
                                          new Piece ("(a|b1)", "(a|b1)", 1, false, false),
                                          new Piece ("(a|ab)", "(a|ab)", 1, false, false),
                                          new Piece ("(?:b|\\s)", "(?:b|\\s)", 0, false, false),
                                          new Piece ("(\\S)", "(\\S)", 1, false, true),
                                          new Piece ("^", "^", 0, false, false),
                                          new Piece ("$", "\\z", 0, false, false) };

  private static final String [] QUANTIFIERS = { "", "", "", "*", "+", "?", "*?", "+?", "{2}", "{1,2}", "{0,1}" };

  private static final String [] FLAGS = { "", "", "i", "s", "q", "iq", "is" };

  private static final IGenerator <Regex> REGEXES = aChoices -> {
    final String sFlags = FLAGS[(int) aChoices.draw (FLAGS.length - 1)];
    final boolean bCaseInsensitive = sFlags.indexOf ('i') >= 0;
    final List <Piece> aPieces = new ArrayList <> ();
    int nGroups = 0;
    final int nCount = 1 + (int) aChoices.draw (2);
    for (int i = 0; i < nCount; ++i)
    {
      final Piece aPiece = _generatePiece (aChoices, nGroups);
      // A negated class or a back-reference is matched differently under case-insensitivity
      if (bCaseInsensitive && (aPiece.negClass () || aPiece.backRef ()))
        continue;
      aPieces.add (aPiece);
      nGroups += aPiece.groups ();
    }
    if (aPieces.isEmpty ())
      aPieces.add (ATOMS[0]);
    final StringBuilder aXPath = new StringBuilder ();
    final StringBuilder aJava = new StringBuilder ();
    // Sometimes an alternation of the first piece and the rest
    final boolean bAlternation = aPieces.size () > 1 && aChoices.draw (3) == 0;
    for (int i = 0; i < aPieces.size (); ++i)
    {
      if (bAlternation && i == 1)
      {
        aXPath.append ('|');
        aJava.append ('|');
      }
      aXPath.append (aPieces.get (i).xpath ());
      aJava.append (aPieces.get (i).java ());
    }
    return new Regex (aXPath.toString (), aJava.toString (), sFlags);
  };

  private static final String PARAMS = "<xsl:param name='s' as='xs:string'/>" +
                                       "<xsl:param name='re' as='xs:string'/>" +
                                       "<xsl:param name='flags' as='xs:string'/>";

  /** xsl:analyze-string: a match as [text|group 1], a non-matching substring as (text) */
  private static final XsltExecutable INSTRUCTION = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                          "<xsl:output method='text'/>" +
                                                                          PARAMS +
                                                                          "<xsl:template name='xsl:initial-template'>" +
                                                                          "<xsl:analyze-string select='$s' regex='{$re}' flags='{$flags}'>" +
                                                                          "<xsl:matching-substring>[<xsl:value-of select='.'/>|<xsl:value-of select='regex-group(1)'/>]</xsl:matching-substring>" +
                                                                          "<xsl:non-matching-substring>(<xsl:value-of select='.'/>)</xsl:non-matching-substring>" +
                                                                          "</xsl:analyze-string>" +
                                                                          "</xsl:template>" +
                                                                          XsltTestHelper.END,
                                                                          new XsltCompileOptions ());

  /** The functions, separated by '#' */
  private static final XsltExecutable FUNCTIONS = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                        "<xsl:output method='text'/>" +
                                                                        PARAMS +
                                                                        "<xsl:template name='xsl:initial-template'>" +
                                                                        "<xsl:value-of select=\"analyze-string($s, $re, $flags)/* ! (if (local-name() = 'match') then '[' || . || '|' || string((.//*:group[@nr = 1])[1]) || ']' else '(' || . || ')')\" separator=''/>" +
                                                                        "<xsl:text>#</xsl:text>" +
                                                                        "<xsl:value-of select=\"tokenize($s, $re, $flags) ! ('&lt;' || . || '&gt;')\" separator=''/>" +
                                                                        "<xsl:text>#</xsl:text>" +
                                                                        "<xsl:value-of select=\"replace($s, $re, '{$0}', $flags)\"/>" +
                                                                        "<xsl:text>#</xsl:text>" +
                                                                        "<xsl:value-of select='matches($s, $re, $flags)'/>" +
                                                                        "</xsl:template>" +
                                                                        XsltTestHelper.END,
                                                                        new XsltCompileOptions ());

  /** fn:tokenize alone, for the regular expressions that match a zero-length string */
  private static final XsltExecutable TOKENIZE = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                       "<xsl:output method='text'/>" +
                                                                       PARAMS +
                                                                       "<xsl:template name='xsl:initial-template'>" +
                                                                       "<xsl:value-of select='tokenize($s, $re, $flags)'/>" +
                                                                       "</xsl:template>" +
                                                                       XsltTestHelper.END,
                                                                       new XsltCompileOptions ());

  @NonNull
  private static Piece _generatePiece (@NonNull final ChoiceSequence aChoices, final int nGroupsBefore)
  {
    // A back-reference unit: a group and the reference to it, always participating
    if (aChoices.draw (9) == 0)
    {
      final String s = "([ab])\\" + (nGroupsBefore + 1);
      return new Piece (s, s, 1, true, false);
    }
    final Piece aAtom = ATOMS[(int) aChoices.draw (ATOMS.length - 1)];
    final String sQuantifier = QUANTIFIERS[(int) aChoices.draw (QUANTIFIERS.length - 1)];
    // No quantifier on an anchor
    if (aAtom.xpath ().equals ("^") || aAtom.xpath ().equals ("$"))
      return aAtom;
    return new Piece (aAtom.xpath () + sQuantifier, aAtom.java () + sQuantifier, aAtom.groups (), false, aAtom.negClass ());
  }

  /**
   * The partition of the input by the algorithm of XSLT 3.0 section 17.1:
   * test for a match at every position from left to right (code points), a
   * zero-length match moves the following character to the non-matching
   * substring.
   */
  @NonNull
  private static List <Segment> _partition (@NonNull final Pattern aPattern, @NonNull final String sInput)
  {
    final List <Segment> ret = new ArrayList <> ();
    final Matcher aMatcher = aPattern.matcher (sInput);
    // ^ and $ refer to the whole string, not to the region
    aMatcher.useAnchoringBounds (false);
    aMatcher.useTransparentBounds (true);
    final StringBuilder aNonMatching = new StringBuilder ();
    int nPos = 0;
    while (true)
    {
      aMatcher.region (nPos, sInput.length ());
      if (aMatcher.lookingAt ())
      {
        if (aNonMatching.length () > 0)
        {
          ret.add (new Segment (false, aNonMatching.toString (), ""));
          aNonMatching.setLength (0);
        }
        final String sGroup1 = aMatcher.groupCount () >= 1 && aMatcher.group (1) != null ? aMatcher.group (1) : "";
        ret.add (new Segment (true, aMatcher.group (), sGroup1));
        if (aMatcher.end () > nPos)
        {
          nPos = aMatcher.end ();
          continue;
        }
        if (nPos == sInput.length ())
          break;
        final int nCP = sInput.codePointAt (nPos);
        aNonMatching.appendCodePoint (nCP);
        nPos += Character.charCount (nCP);
      }
      else
      {
        if (nPos == sInput.length ())
        {
          if (aNonMatching.length () > 0)
            ret.add (new Segment (false, aNonMatching.toString (), ""));
          break;
        }
        final int nCP = sInput.codePointAt (nPos);
        aNonMatching.appendCodePoint (nCP);
        nPos += Character.charCount (nCP);
      }
    }
    return ret;
  }

  @NonNull
  private static String _expectedAnalyze (@NonNull final List <Segment> aSegments)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final Segment aSegment : aSegments)
      if (aSegment.match ())
        aSB.append ('[').append (aSegment.text ()).append ('|').append (aSegment.group1 ()).append (']');
      else
        aSB.append ('(').append (aSegment.text ()).append (')');
    return aSB.toString ();
  }

  /**
   * The expected result of fn:tokenize, fn:replace and fn:matches for a
   * regular expression that matches no zero-length string (F&amp;O 3.1
   * sections 5.6.3 to 5.6.5).
   */
  @NonNull
  private static String _expectedFunctions (@NonNull final List <Segment> aSegments,
                                            @NonNull final String sInput,
                                            @NonNull final Regex aRegex)
  {
    final StringBuilder aSB = new StringBuilder (_expectedAnalyze (aSegments)).append ('#');
    // The tokens are the strings between the matches; an empty input has none
    if (!sInput.isEmpty ())
    {
      final StringBuilder aToken = new StringBuilder ();
      for (final Segment aSegment : aSegments)
        if (aSegment.match ())
        {
          aSB.append ('<').append (aToken).append ('>');
          aToken.setLength (0);
        }
        else
          aToken.append (aSegment.text ());
      aSB.append ('<').append (aToken).append ('>');
    }
    aSB.append ('#');
    // With the q flag the replacement string is literal too
    final boolean bLiteral = aRegex.flags ().indexOf ('q') >= 0;
    boolean bMatches = false;
    for (final Segment aSegment : aSegments)
      if (aSegment.match ())
      {
        aSB.append (bLiteral ? "{$0}" : "{" + aSegment.text () + "}");
        bMatches = true;
      }
      else
        aSB.append (aSegment.text ());
    return aSB.append ('#').append (bMatches).toString ();
  }

  @NonNull
  private static String _run (@NonNull final XsltExecutable aExec, @NonNull final String sInput, @NonNull final Regex aRegex)
  {
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.params ().put (new XdmQName ("s"), XdmSequence.of (XdmAtomic.string (sInput)));
    aRequest.params ().put (new XdmQName ("re"), XdmSequence.of (XdmAtomic.string (aRegex.xpath ())));
    aRequest.params ().put (new XdmQName ("flags"), XdmSequence.of (XdmAtomic.string (aRegex.flags ())));
    return aExec.transform (aRequest).principalSerialized ();
  }

  /**
   * xsl:analyze-string partitions the input into matching and non-matching
   * substrings as section 17.1 describes, also for regular expressions that
   * match a zero-length string (also before a character outside the BMP),
   * with regex-group(1) as Java's group 1.
   */
  @Test
  public void testAnalyzeStringInstruction ()
  {
    new PropertyChecker ().setTries (1500).forAll (INPUTS, REGEXES, (sInput, aRegex) -> {
      final Pattern aPattern = aRegex.getJavaPattern ();
      final List <Segment> aSegments = _partition (aPattern, sInput);
      assertEquals (aRegex + " on '" + sInput + "'", _expectedAnalyze (aSegments), _run (INSTRUCTION, sInput, aRegex));
    });
  }

  /**
   * After a zero-length match, section 17.1 moves the next character (a code
   * point, "a codepoint greater than 65535 is considered as one character, not
   * as a surrogate pair") to the non-matching substring: for the input U+1F600
   * and the regex <code>a*</code> the result is an empty match, U+1F600 as one
   * non-matching substring and another empty match; the surrogate pair is never
   * split.
   */
  @Test
  public void testZeroLengthMatchBeforeSupplementaryCharacter ()
  {
    assertEquals ("[|](\uD83D\uDE00)[|]", _run (INSTRUCTION, "\uD83D\uDE00", new Regex ("a*", "a*", "")));
    assertEquals ("[|](\uD83D\uDE00)[|](b)[|]", _run (INSTRUCTION, "\uD83D\uDE00b", new Regex ("a*", "a*", "")));
  }


  /**
   * For a regular expression that matches no zero-length string,
   * fn:analyze-string gives the partition of xsl:analyze-string, fn:tokenize
   * the strings between the matches, fn:replace the input with each match
   * replaced and fn:matches whether there is a match. For one that matches a
   * zero-length string, fn:tokenize raises FORX0003.
   */
  @Test
  public void testRegexFunctions ()
  {
    new PropertyChecker ().setTries (1500).forAll (INPUTS, REGEXES, (sInput, aRegex) -> {
      final Pattern aPattern = aRegex.getJavaPattern ();
      if (aPattern.matcher ("").lookingAt ())
      {
        try
        {
          _run (TOKENIZE, sInput, aRegex);
          fail (aRegex + " matches a zero-length string, but fn:tokenize accepted it");
        }
        catch (final XPathException ex)
        {
          assertEquals (aRegex.toString (), "FORX0003", ex.getCode ());
        }
        return;
      }
      final List <Segment> aSegments = _partition (aPattern, sInput);
      assertEquals (aRegex + " on '" + sInput + "'", _expectedFunctions (aSegments, sInput, aRegex), _run (FUNCTIONS, sInput, aRegex));
    });
  }
}

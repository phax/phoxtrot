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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.string.StringHex;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the string functions of {@link FnString} (F&amp;O
 * 3.1 section 5) on strings with supplementary characters: every position and
 * length counts code points, never UTF-16 units. Java models of the
 * specification serve as reference.
 *
 * @author Philip Helger
 */
public final class FnStringPropertyTest
{
  private static final String [] VAR_NAMES = { "a", "b", "c" };

  /** Characters that are special for the string functions, each a complete code point */
  private static final String [] SPECIAL_CHARS = { " ",
                                                   "\t",
                                                   "\n",
                                                   "\r",
                                                   "a",
                                                   "b",
                                                   "A",
                                                   "B",
                                                   "-",
                                                   "_",
                                                   ".",
                                                   "~",
                                                   "%",
                                                   "\u00e9",
                                                   "\u00df",
                                                   "\ufb01",
                                                   "\u0130",
                                                   "\ufffd",
                                                   "\ud83d\ude00",
                                                   "\ud800\udc00",
                                                   "\udbff\udfff" };

  @NonNull
  private static IGenerator <String> _specialStrings (final int nMaxLength)
  {
    return Generators.lists (Generators.of (SPECIAL_CHARS), 0, nMaxLength).map (x -> String.join ("", x));
  }

  private static boolean _isXmlChar (final int nCodePoint)
  {
    return nCodePoint == 0x9 ||
           nCodePoint == 0xa ||
           nCodePoint == 0xd ||
           (nCodePoint >= 0x20 && nCodePoint <= 0xd7ff) ||
           (nCodePoint >= 0xe000 && nCodePoint <= 0xfffd) ||
           (nCodePoint >= 0x10000 && nCodePoint <= 0x10ffff);
  }

  private static boolean _isXmlString (@NonNull final String s)
  {
    // An unpaired surrogate is reported as a code point in the surrogate range
    return s.codePoints ().allMatch (FnStringPropertyTest::_isXmlChar);
  }

  /** Strings of XML characters, including supplementary ones */
  private static final IGenerator <String> STRINGS = Generators.oneOf (_specialStrings (12),
                                                                       Generators.unicodeStrings (0, 12))
                                                               .filter (FnStringPropertyTest::_isXmlString);

  /** Positions and lengths for substring, including fractions and special values */
  private static final IGenerator <Double> POSITIONS = Generators.oneOf (Generators.ints (-6, 30)
                                                                                   .map (x -> Double.valueOf (x.intValue () / 2.0)),
                                                                         Generators.of (Double.valueOf (Double.NaN),
                                                                                        Double.valueOf (Double.POSITIVE_INFINITY),
                                                                                        Double.valueOf (Double.NEGATIVE_INFINITY),
                                                                                        Double.valueOf (-0.0)));

  private final Map <String, CompiledXPath> m_aCache = new HashMap <> ();
  private final StaticContext m_aStaticCtx = new StaticContext ();

  public FnStringPropertyTest ()
  {
    for (final String sName : VAR_NAMES)
      m_aStaticCtx.declareVariable (new XdmQName (sName));
  }

  /** Evaluate with the values bound to <code>$a</code>, <code>$b</code> and <code>$c</code>. */
  @NonNull
  private XdmSequence _eval (@NonNull final String sExpr, @NonNull final XdmAtomic... aValues)
  {
    final CompiledXPath aXPath = m_aCache.computeIfAbsent (sExpr, x -> XPathCompiler.compile (x, m_aStaticCtx));
    DynamicContext aCtx = aXPath.createDynamicContext ();
    for (int i = 0; i < VAR_NAMES.length; ++i)
      aCtx = aCtx.withVariable (new XdmQName (VAR_NAMES[i]), i < aValues.length ? XdmSequence.of (aValues[i]) : XdmSequence.EMPTY);
    return aXPath.evaluate (aCtx);
  }

  @NonNull
  private String _evalString (@NonNull final String sExpr, @NonNull final String... aValues)
  {
    final XdmAtomic [] aAtomics = new XdmAtomic [aValues.length];
    for (int i = 0; i < aValues.length; ++i)
      aAtomics[i] = XdmAtomic.string (aValues[i]);
    final XdmSequence aResult = _eval (sExpr, aAtomics);
    assertEquals (sExpr, 1, aResult.size ());
    return ((XdmAtomic) aResult.get (0)).getLexical ();
  }

  /** @return fn:round of F&amp;O 3.1 section 4.4.4: half values round towards positive infinity. */
  private static double _round (final double d)
  {
    if (Double.isNaN (d) || Double.isInfinite (d))
      return d;
    return Math.floor (d + 0.5);
  }

  /**
   * The model of <code>fn:substring</code> (F&amp;O 3.1 section 5.4.3): the
   * code points at the positions <code>p</code> with
   * <code>round($start) &lt;= p &lt; round($start) + round($length)</code>.
   */
  @NonNull
  private static String _substring (@NonNull final String s, final double dStart, final double dLength, final boolean bWithLength)
  {
    final double dFirst = _round (dStart);
    final double dEnd = bWithLength ? dFirst + _round (dLength) : Double.POSITIVE_INFINITY;
    final StringBuilder aSB = new StringBuilder ();
    final int [] aCPs = s.codePoints ().toArray ();
    for (int i = 0; i < aCPs.length; ++i)
    {
      final double dPos = i + 1;
      if (dPos >= dFirst && dPos < dEnd)
        aSB.appendCodePoint (aCPs[i]);
    }
    return aSB.toString ();
  }

  /**
   * <code>fn:substring</code> with two and three arguments selects code points
   * by the rounded positions of F&amp;O 3.1 section 5.4.3, also for NaN,
   * infinite and negative arguments; and the two halves of a split give the
   * string again.
   */
  @Test
  public void testSubstring ()
  {
    new PropertyChecker ().setTries (3000).forAll (STRINGS, POSITIONS, POSITIONS, (s, aStart, aLength) -> {
      final double dStart = aStart.doubleValue ();
      final double dLength = aLength.doubleValue ();
      final XdmAtomic aS = XdmAtomic.string (s);
      final String sMsg = "'" + s + "' " + dStart + " " + dLength;
      assertEquals (sMsg,
                    _substring (s, dStart, dLength, true),
                    ((XdmAtomic) _eval ("substring($a, $b, $c)", aS, XdmAtomic.dbl (dStart), XdmAtomic.dbl (dLength)).get (0)).getString ());
      assertEquals (sMsg,
                    _substring (s, dStart, 0, false),
                    ((XdmAtomic) _eval ("substring($a, $b)", aS, XdmAtomic.dbl (dStart)).get (0)).getString ());
      if (!Double.isNaN (dStart) && !Double.isInfinite (dStart))
      {
        final XdmAtomic aN = XdmAtomic.integer ((long) Math.floor (dStart));
        assertEquals (sMsg,
                      s,
                      ((XdmAtomic) _eval ("substring($a, 1, $b - 1) || substring($a, $b)", aS, aN).get (0)).getString ());
      }
    });
  }

  /**
   * <code>string-length</code> and <code>string-to-codepoints</code> count
   * code points, and <code>codepoints-to-string</code> inverts
   * <code>string-to-codepoints</code>, also character by character.
   */
  @Test
  public void testCodepoints ()
  {
    new PropertyChecker ().setTries (2000).forAll (STRINGS, s -> {
      final XdmAtomic aS = XdmAtomic.string (s);
      assertEquals (Integer.toString (s.codePointCount (0, s.length ())), _evalString ("string(string-length($a))", s));
      final XdmSequence aCPs = _eval ("string-to-codepoints($a)", aS);
      final int [] aExpected = s.codePoints ().toArray ();
      assertEquals (aExpected.length, aCPs.size ());
      for (int i = 0; i < aExpected.length; ++i)
        assertEquals (aExpected[i], ((XdmAtomic) aCPs.get (i)).getBigInteger ().intValue ());
      assertEquals (s, _evalString ("codepoints-to-string(string-to-codepoints($a))", s));
      assertEquals (s, _evalString ("string-join(for $c in string-to-codepoints($a) return codepoints-to-string($c))", s));
    });
  }

  @NonNull
  private static String _translate (@NonNull final String s, @NonNull final String sMap, @NonNull final String sTrans)
  {
    final int [] aMap = sMap.codePoints ().toArray ();
    final int [] aTrans = sTrans.codePoints ().toArray ();
    final StringBuilder aSB = new StringBuilder ();
    s.codePoints ().forEach (nCP -> {
      int nIndex = -1;
      for (int i = 0; i < aMap.length; ++i)
        if (aMap[i] == nCP)
        {
          nIndex = i;
          break;
        }
      if (nIndex < 0)
        aSB.appendCodePoint (nCP);
      else
        if (nIndex < aTrans.length)
          aSB.appendCodePoint (aTrans[nIndex]);
    });
    return aSB.toString ();
  }

  /**
   * <code>translate</code> replaces each code point of the map string by the
   * one at the same position in the translation string (the first occurrence
   * counts) and drops it if there is none (F&amp;O 3.1 section 5.4.9).
   */
  @Test
  public void testTranslate ()
  {
    final IGenerator <String> aShort = _specialStrings (5);
    new PropertyChecker ().setTries (3000).forAll (STRINGS, aShort, aShort, (s, sMap, sTrans) -> {
      assertEquals ("'" + s + "' '" + sMap + "' '" + sTrans + "'",
                    _translate (s, sMap, sTrans),
                    _evalString ("translate($a, $b, $c)", s, sMap, sTrans));
    });
  }

  @NonNull
  private static String _normalizeSpace (@NonNull final String s)
  {
    final List <String> aParts = new ArrayList <> ();
    for (final String sPart : s.split ("[ \t\n\r]+"))
      if (!sPart.isEmpty ())
        aParts.add (sPart);
    return String.join (" ", aParts);
  }

  /**
   * <code>normalize-space</code> removes leading and trailing XML whitespace
   * and collapses inner runs to one space, and the one-argument
   * <code>tokenize</code> returns the parts between the runs.
   */
  @Test
  public void testNormalizeSpace ()
  {
    new PropertyChecker ().setTries (2000).forAll (STRINGS, s -> {
      final String sExpected = _normalizeSpace (s);
      assertEquals ("'" + s + "'", sExpected, _evalString ("normalize-space($a)", s));
      final List <String> aTokens = new ArrayList <> ();
      for (final IXdmItem aItem : _eval ("tokenize($a)", XdmAtomic.string (s)))
        aTokens.add (((XdmAtomic) aItem).getString ());
      assertEquals ("'" + s + "'", sExpected.isEmpty () ? List.of () : List.of (sExpected.split (" ")), aTokens);
    });
  }

  /** @return The comparison of the code point sequences, -1, 0 or 1. */
  private static int _compareCodepoints (@NonNull final String s1, @NonNull final String s2)
  {
    final int [] a1 = s1.codePoints ().toArray ();
    final int [] a2 = s2.codePoints ().toArray ();
    final int nLen = Math.min (a1.length, a2.length);
    for (int i = 0; i < nLen; ++i)
      if (a1[i] != a2[i])
        return a1[i] < a2[i] ? -1 : 1;
    return Integer.compare (a1.length, a2.length);
  }

  /**
   * Under the Unicode codepoint collation, <code>compare</code>, the value
   * comparisons, <code>contains</code>, <code>starts-with</code>,
   * <code>ends-with</code>, <code>substring-before</code> and
   * <code>substring-after</code> agree with the code point sequences (a
   * supplementary character sorts after U+FFFD, unlike in UTF-16 order).
   */
  @Test
  public void testCodepointComparison ()
  {
    final IGenerator <String> aShort = _specialStrings (3);
    new PropertyChecker ().setTries (3000).forAll (STRINGS, Generators.oneOf (aShort, STRINGS), (s1, s2) -> {
      final String sMsg = "'" + s1 + "' vs. '" + s2 + "'";
      final int nExpected = _compareCodepoints (s1, s2);
      assertEquals (sMsg, Integer.toString (nExpected), _evalString ("string(compare($a, $b))", s1, s2));
      assertEquals (sMsg, Boolean.toString (nExpected < 0), _evalString ("string($a lt $b)", s1, s2));
      assertEquals (sMsg, Boolean.toString (nExpected == 0), _evalString ("string($a eq $b)", s1, s2));
      assertEquals (sMsg, Boolean.toString (nExpected > 0), _evalString ("string(max(($a, $b)) eq $a and $a ne $b)", s1, s2));
      assertEquals (sMsg, Boolean.toString (s1.contains (s2)), _evalString ("string(contains($a, $b))", s1, s2));
      assertEquals (sMsg, Boolean.toString (s1.startsWith (s2)), _evalString ("string(starts-with($a, $b))", s1, s2));
      assertEquals (sMsg, Boolean.toString (s1.endsWith (s2)), _evalString ("string(ends-with($a, $b))", s1, s2));
      final int nIndex = s1.indexOf (s2);
      assertEquals (sMsg, nIndex < 0 ? "" : s1.substring (0, nIndex), _evalString ("substring-before($a, $b)", s1, s2));
      assertEquals (sMsg,
                    nIndex < 0 ? "" : s1.substring (nIndex + s2.length ()),
                    _evalString ("substring-after($a, $b)", s1, s2));
      if (nIndex >= 0)
        assertEquals (sMsg, s1, _evalString ("substring-before($a, $b) || $b || substring-after($a, $b)", s1, s2));
    });
  }

  /**
   * <code>encode-for-uri</code> percent-encodes the UTF-8 bytes of every
   * character except the unreserved ones <code>A-Z a-z 0-9 - _ . ~</code>
   * with upper-case hex digits (F&amp;O 3.1 section 6.2), and
   * <code>normalize-unicode</code> is idempotent.
   */
  @Test
  public void testEncodeForUri ()
  {
    new PropertyChecker ().setTries (2000).forAll (STRINGS, s -> {
      final StringBuilder aSB = new StringBuilder ();
      for (final byte b : s.getBytes (StandardCharsets.UTF_8))
      {
        final char c = (char) (b & 0xff);
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-_.~".indexOf (c) >= 0)
          aSB.append (c);
        else
          aSB.append ('%').append (StringHex.getHexStringLeadingZero (b & 0xff, 2).toUpperCase (Locale.ROOT));
      }
      assertEquals ("'" + s + "'", aSB.toString (), _evalString ("encode-for-uri($a)", s));
      for (final String sForm : new String [] { "NFC", "NFD", "NFKC", "NFKD" })
        assertTrue (s,
                    Boolean.parseBoolean (_evalString ("string(normalize-unicode(normalize-unicode($a, $b), $b) eq normalize-unicode($a, $b))",
                                                       s,
                                                       sForm)));
    });
  }
}

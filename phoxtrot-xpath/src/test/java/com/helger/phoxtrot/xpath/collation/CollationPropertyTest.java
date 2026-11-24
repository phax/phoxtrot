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
package com.helger.phoxtrot.xpath.collation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the collations of {@link CollationRegistry}
 * (F&amp;O 3.1 section 5.3): every collation must be a total preorder
 * (<code>fn:compare</code> reflexive, antisymmetric and transitive), agree
 * with the equality of <code>fn:collation-key</code> (section 5.3.6), with
 * <code>fn:sort</code> and <code>fn:distinct-values</code>; the HTML ASCII
 * case-insensitive collation is compared with its definition (section
 * 5.3.5).
 *
 * @author Philip Helger
 */
public final class CollationPropertyTest
{
  private static final String [] VAR_NAMES = { "a", "b", "c", "coll" };

  private static final String [] COLLATIONS = { CollationRegistry.CODEPOINT_URI,
                                                CollationRegistry.HTML_ASCII_CASE_INSENSITIVE_URI,
                                                "http://www.w3.org/2013/collation/UCA",
                                                "http://www.w3.org/2013/collation/UCA?lang=en",
                                                "http://www.w3.org/2013/collation/UCA?lang=de",
                                                "http://www.w3.org/2013/collation/UCA?lang=sv",
                                                "http://www.w3.org/2013/collation/UCA?strength=primary",
                                                "http://www.w3.org/2013/collation/UCA?strength=secondary",
                                                "http://www.w3.org/2013/collation/UCA?strength=identical",
                                                "http://www.w3.org/2013/collation/UCA?caseFirst=upper",
                                                "http://www.w3.org/2013/collation/UCA?caseFirst=lower",
                                                "http://www.w3.org/2013/collation/UCA?caseLevel=yes;strength=primary",
                                                "http://www.w3.org/2013/collation/UCA?backwards=yes",
                                                "http://www.w3.org/2013/collation/UCA?numeric=yes",
                                                "http://www.w3.org/2013/collation/UCA?alternate=shifted",
                                                "http://www.w3.org/2013/collation/UCA?alternate=shifted;strength=quaternary",
                                                "http://www.w3.org/2013/collation/UCA?alternate=blanked;maxVariable=symbol",
                                                "http://www.w3.org/2013/collation/UCA?normalization=yes",
                                                "http://www.w3.org/2013/collation/UCA?numeric=yes;caseFirst=upper;alternate=shifted;strength=quaternary" };

  private static final String [] PIECES = { "a",
                                            "A",
                                            "b",
                                            "B",
                                            "z",
                                            "1",
                                            "9",
                                            "10",
                                            "01",
                                            " ",
                                            "-",
                                            "$",
                                            "\u00e9",
                                            "e\u0301",
                                            "E",
                                            "\u00df",
                                            "ss",
                                            "\u00e4",
                                            "\u00c4",
                                            "\u00a0",
                                            "\u0131",
                                            "\ud83d\ude00",
                                            "\ufffd" };

  private static final IGenerator <String> STRINGS = Generators.lists (Generators.of (PIECES), 0, 4).map (x -> String.join ("", x));

  private final Map <String, CompiledXPath> m_aCache = new HashMap <> ();
  private final StaticContext m_aStaticCtx = new StaticContext ();

  public CollationPropertyTest ()
  {
    for (final String sName : VAR_NAMES)
      m_aStaticCtx.declareVariable (new XdmQName (sName));
  }

  /** Evaluate with the strings bound to <code>$a</code>, <code>$b</code>, <code>$c</code> and <code>$coll</code>. */
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

  private int _compare (@NonNull final String sA, @NonNull final String sB, @NonNull final String sCollation)
  {
    return ((XdmAtomic) _eval ("compare($a, $b, $coll)", sA, sB, "", sCollation).get (0)).getBigInteger ().intValue ();
  }

  /** @return The collation key as hex string, or <code>null</code> if the collation has none (FOCH0004). */
  @Nullable
  private String _key (@NonNull final String s, @NonNull final String sCollation)
  {
    try
    {
      return ((XdmAtomic) _eval ("string(xs:hexBinary(collation-key($a, $coll)))", s, "", "", sCollation).get (0)).getString ();
    }
    catch (final XPathException ex)
    {
      assertEquals (sCollation, "FOCH0004", ex.getCode ());
      return null;
    }
  }

  /**
   * <code>fn:compare</code> is reflexive, antisymmetric and transitive for
   * every collation, and two strings compare equal exactly if their collation
   * keys are equal (F&amp;O 3.1 section 5.3.6). The keys also order the
   * strings as <code>fn:compare</code> does (the contract of
   * {@link ICollation#getCollationKeyBytes(String)}).
   */
  @Test
  public void testTotalOrder ()
  {
    new PropertyChecker ().setTries (1500).forAll (STRINGS, STRINGS, STRINGS, Generators.of (COLLATIONS), (sA, sB, sC, sColl) -> {
      final String sMsg = "'" + sA + "' '" + sB + "' '" + sC + "' " + sColl;
      assertEquals (sMsg, 0, _compare (sA, sA, sColl));
      final int nAB = _compare (sA, sB, sColl);
      final int nBA = _compare (sB, sA, sColl);
      final int nBC = _compare (sB, sC, sColl);
      final int nAC = _compare (sA, sC, sColl);
      assertEquals (sMsg + ": antisymmetry", nAB, -nBA);
      if (nAB <= 0 && nBC <= 0)
        assertTrue (sMsg + ": transitivity " + nAB + " " + nBC + " " + nAC, nAC <= 0 && (nAC < 0 || (nAB == 0 && nBC == 0)));
      if (nAB >= 0 && nBC >= 0)
        assertTrue (sMsg + ": transitivity " + nAB + " " + nBC + " " + nAC, nAC >= 0 && (nAC > 0 || (nAB == 0 && nBC == 0)));
      final String sKeyA = _key (sA, sColl);
      if (sKeyA != null)
      {
        final String sKeyB = _key (sB, sColl);
        assertEquals (sMsg + ": key equality " + sKeyA + " " + sKeyB, Boolean.valueOf (nAB == 0), Boolean.valueOf (sKeyA.equals (sKeyB)));
        // Upper-case hex digits compare like the unsigned bytes they encode
        assertEquals (sMsg + ": key order " + sKeyA + " " + sKeyB, nAB, Integer.signum (sKeyA.compareTo (sKeyB)));
      }
    });
  }

  /**
   * <code>fn:sort</code>, <code>fn:min</code> and <code>fn:distinct-values</code>
   * with a collation agree with <code>fn:compare</code> under it, and the
   * value comparisons with a default collation do too.
   */
  @Test
  public void testSortAndDistinct ()
  {
    new PropertyChecker ().setTries (1000).forAll (STRINGS, STRINGS, STRINGS, Generators.of (COLLATIONS), (sA, sB, sC, sColl) -> {
      final String sMsg = "'" + sA + "' '" + sB + "' '" + sC + "' " + sColl;
      final XdmSequence aSorted = _eval ("sort(($a, $b, $c), $coll)", sA, sB, sC, sColl);
      for (int i = 0; i + 1 < aSorted.size (); ++i)
        assertTrue (sMsg + ": sorted",
                    _compare (((XdmAtomic) aSorted.get (i)).getString (), ((XdmAtomic) aSorted.get (i + 1)).getString (), sColl) <= 0);
      int nClasses = 1;
      if (_compare (sB, sA, sColl) != 0)
        nClasses++;
      if (_compare (sC, sA, sColl) != 0 && _compare (sC, sB, sColl) != 0)
        nClasses++;
      assertEquals (sMsg + ": distinct", nClasses, _eval ("distinct-values(($a, $b, $c), $coll)", sA, sB, sC, sColl).size ());
      final String sMin = ((XdmAtomic) _eval ("min(($a, $b, $c), $coll)", sA, sB, sC, sColl).get (0)).getString ();
      for (final String s : List.of (sA, sB, sC))
        assertTrue (sMsg + ": min " + sMin, _compare (sMin, s, sColl) <= 0);
      final boolean bEqual = ((XdmAtomic) _eval ("deep-equal($a, $b, $coll)", sA, sB, "", sColl).get (0)).getBoolean ();
      assertEquals (sMsg + ": deep-equal", Boolean.valueOf (_compare (sA, sB, sColl) == 0), Boolean.valueOf (bEqual));
    });
  }

  @NonNull
  private static String _asciiLower (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder (s.length ());
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      aSB.append (c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
    }
    return aSB.toString ();
  }

  private static int _compareCodepoints (@NonNull final String s1, @NonNull final String s2)
  {
    final int [] a1 = s1.codePoints ().toArray ();
    final int [] a2 = s2.codePoints ().toArray ();
    final int nLen = Math.min (a1.length, a2.length);
    for (int i = 0; i < nLen; ++i)
      if (a1[i] != a2[i])
        return Integer.compare (a1[i], a2[i]);
    return Integer.compare (a1.length, a2.length);
  }

  /**
   * The HTML ASCII case-insensitive collation compares the strings after
   * mapping A-Z to a-z by code point (F&amp;O 3.1 section 5.3.5), also for
   * <code>contains</code>, <code>starts-with</code>, <code>ends-with</code>,
   * <code>substring-before</code> and <code>substring-after</code>.
   */
  @Test
  public void testHtmlAsciiCaseInsensitive ()
  {
    final String sColl = CollationRegistry.HTML_ASCII_CASE_INSENSITIVE_URI;
    new PropertyChecker ().setTries (1500).forAll (STRINGS, STRINGS, (sA, sB) -> {
      final String sMsg = "'" + sA + "' '" + sB + "'";
      final String sLowerA = _asciiLower (sA);
      final String sLowerB = _asciiLower (sB);
      assertEquals (sMsg, Integer.signum (_compareCodepoints (sLowerA, sLowerB)), _compare (sA, sB, sColl));
      assertEquals (sMsg,
                    Boolean.valueOf (sLowerA.contains (sLowerB)),
                    Boolean.valueOf (((XdmAtomic) _eval ("contains($a, $b, $coll)", sA, sB, "", sColl).get (0)).getBoolean ()));
      assertEquals (sMsg,
                    Boolean.valueOf (sLowerA.startsWith (sLowerB)),
                    Boolean.valueOf (((XdmAtomic) _eval ("starts-with($a, $b, $coll)", sA, sB, "", sColl).get (0)).getBoolean ()));
      assertEquals (sMsg,
                    Boolean.valueOf (sLowerA.endsWith (sLowerB)),
                    Boolean.valueOf (((XdmAtomic) _eval ("ends-with($a, $b, $coll)", sA, sB, "", sColl).get (0)).getBoolean ()));
      final int nIndex = sLowerA.indexOf (sLowerB);
      assertEquals (sMsg,
                    nIndex < 0 ? "" : sA.substring (0, nIndex),
                    ((XdmAtomic) _eval ("substring-before($a, $b, $coll)", sA, sB, "", sColl).get (0)).getString ());
      assertEquals (sMsg,
                    nIndex < 0 ? "" : sA.substring (nIndex + sB.length ()),
                    ((XdmAtomic) _eval ("substring-after($a, $b, $coll)", sA, sB, "", sColl).get (0)).getString ());
    });
  }
}

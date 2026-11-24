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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for xsl:sort ({@link SortKeyDef}, XSLT 3.0 section 13)
 * against sorting in Java: the sort is stable in both directions, numbers
 * compare as doubles with NaN equal to itself and less than any other number,
 * and strings under the Unicode codepoint collation compare by code points.
 *
 * @author Philip Helger
 */
public final class SortKeyDefPropertyTest
{
  /** One item to sort: its position in the source, its number and string values */
  private record Item (int pos, @NonNull String number, @NonNull String string)
  {}

  /** The lexical forms of the numbers and the double that fn:number gives for them */
  private static final Map <String, Double> NUMBERS = Map.ofEntries (Map.entry ("1", Double.valueOf (1)),
                                                                     Map.entry ("-1", Double.valueOf (-1)),
                                                                     Map.entry ("0", Double.valueOf (0)),
                                                                     Map.entry ("-0", Double.valueOf (-0.0)),
                                                                     Map.entry ("2.5", Double.valueOf (2.5)),
                                                                     Map.entry ("10", Double.valueOf (10)),
                                                                     Map.entry ("1e2", Double.valueOf (100)),
                                                                     Map.entry (" 3 ", Double.valueOf (3)),
                                                                     Map.entry ("INF", Double.valueOf (Double.POSITIVE_INFINITY)),
                                                                     Map.entry ("-INF", Double.valueOf (Double.NEGATIVE_INFINITY)),
                                                                     Map.entry ("NaN", Double.valueOf (Double.NaN)),
                                                                     Map.entry ("abc", Double.valueOf (Double.NaN)));

  private static final IGenerator <String> STRINGS = Generators.lists (Generators.of ("a",
                                                                                      "B",
                                                                                      "1",
                                                                                      " ",
                                                                                      "\u00e4",
                                                                                      "\uff21",
                                                                                      "\ud83d\ude00",
                                                                                      "\ud800\udc00"),
                                                                       0,
                                                                       3)
                                                               .map (l -> String.join ("", l));

  private static final IGenerator <List <Item>> ITEMS = Generators.lists (Generators.of (NUMBERS.keySet ()
                                                                                                .toArray (new String [0]))
                                                                                    .flatMap (n -> STRINGS.map (s -> new Item (0,
                                                                                                                               n,
                                                                                                                               s))),
                                                                          0,
                                                                          12)
                                                                  .map (l -> {
                                                                    final List <Item> ret = new ArrayList <> ();
                                                                    for (int i = 0; i < l.size (); ++i)
                                                                      ret.add (new Item (i, l.get (i).number (), l.get (i).string ()));
                                                                    return ret;
                                                                  });

  private static final Comparator <Item> BY_NUMBER = (a, b) -> _compareNumbers (NUMBERS.get (a.number ()).doubleValue (),
                                                                                NUMBERS.get (b.number ()).doubleValue ());
  private static final Comparator <Item> BY_STRING = (a, b) -> _compareCodepoints (a.string (), b.string ());

  private static final XsltExecutable ASCENDING = _compile ("ascending");
  private static final XsltExecutable DESCENDING = _compile ("descending");

  private static int _compareNumbers (final double d1, final double d2)
  {
    // NaN is equal to NaN and less than any other value; -0 equals 0
    if (Double.isNaN (d1))
      return Double.isNaN (d2) ? 0 : -1;
    if (Double.isNaN (d2))
      return 1;
    return d1 < d2 ? -1 : d1 > d2 ? 1 : 0;
  }

  private static int _compareCodepoints (@NonNull final String s1, @NonNull final String s2)
  {
    final int [] a1 = s1.codePoints ().toArray ();
    final int [] a2 = s2.codePoints ().toArray ();
    for (int i = 0; i < a1.length && i < a2.length; ++i)
      if (a1[i] != a2[i])
        return Integer.compare (a1[i], a2[i]);
    return Integer.compare (a1.length, a2.length);
  }

  @NonNull
  private static XsltExecutable _compile (@NonNull final String sOrder)
  {
    final String sCodepoint = "http://www.w3.org/2005/xpath-functions/collation/codepoint";
    return XsltCompiler.compile (XsltTestHelper.SHEET +
                                 "<xsl:output method='text'/>" +
                                 "<xsl:template match='/'>" +
                                 "<xsl:for-each select='r/i'><xsl:sort select='@n' data-type='number' order='" +
                                 sOrder +
                                 "'/><xsl:value-of select='@p'/>,</xsl:for-each>|" +
                                 "<xsl:for-each select='r/i'><xsl:sort select='string(@s)' collation='" +
                                 sCodepoint +
                                 "' order='" +
                                 sOrder +
                                 "'/><xsl:sort select='number(@n)' order='" +
                                 sOrder +
                                 "'/><xsl:value-of select='@p'/>,</xsl:for-each>|" +
                                 "<xsl:value-of select=\"sort(r/i, '" +
                                 sCodepoint +
                                 "', function ($i) { string($i/@s) }) ! string(@p)\" separator=','/>" +
                                 "</xsl:template>" +
                                 XsltTestHelper.END,
                                 new XsltCompileOptions ());
  }

  @NonNull
  private static String _source (@NonNull final List <Item> aItems)
  {
    final StringBuilder aSB = new StringBuilder ("<r>");
    for (final Item aItem : aItems)
      aSB.append ("<i p='").append (aItem.pos ()).append ("' n='").append (aItem.number ()).append ("' s='").append (aItem.string ()).append ("'/>");
    return aSB.append ("</r>").toString ();
  }

  @NonNull
  private static String _positions (@NonNull final List <Item> aItems, @NonNull final Comparator <Item> aComparator, @NonNull final String sEnd)
  {
    final List <Item> aSorted = new ArrayList <> (aItems);
    aSorted.sort (aComparator);
    final StringBuilder aSB = new StringBuilder ();
    for (final Item aItem : aSorted)
    {
      if (aSB.length () > 0 && sEnd.isEmpty ())
        aSB.append (',');
      aSB.append (aItem.pos ()).append (sEnd);
    }
    return aSB.toString ();
  }

  /**
   * Sorting by a number, by a string and a number, and with fn:sort by a
   * string gives the order of a stable sort in Java with the comparison rules
   * of the specification, ascending and descending.
   */
  @Test
  public void testSortMatchesJava ()
  {
    new PropertyChecker ().setTries (2000).forAll (ITEMS, Generators.booleans (), (aItems, aDescending) -> {
      final boolean bDescending = aDescending.booleanValue ();
      final Comparator <Item> aByNumber = bDescending ? BY_NUMBER.reversed () : BY_NUMBER;
      final Comparator <Item> aByStringAndNumber = bDescending ? BY_STRING.thenComparing (BY_NUMBER).reversed ()
                                                               : BY_STRING.thenComparing (BY_NUMBER);
      final String sExpected = _positions (aItems, aByNumber, ",") +
                               "|" +
                               _positions (aItems, aByStringAndNumber, ",") +
                               "|" +
                               _positions (aItems, BY_STRING, "");
      final String sSource = _source (aItems);
      final String sResult = (bDescending ? DESCENDING : ASCENDING).transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                                                                   .principalSerialized ();
      assertEquals (sSource, sExpected, sResult);
    });
  }
}

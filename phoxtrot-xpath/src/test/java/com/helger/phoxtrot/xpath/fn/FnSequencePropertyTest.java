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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xpath.XPathPropertyTestHelper;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the sequence functions of {@link FnSequence}
 * (F&amp;O 3.1 sections 14 and 15.5) against Java list operations:
 * <code>fn:sort</code> (ordered, a stable permutation),
 * <code>fn:reverse</code>, <code>fn:subsequence</code>,
 * <code>fn:insert-before</code>, <code>fn:remove</code>,
 * <code>fn:index-of</code>, <code>fn:head</code>/<code>fn:tail</code>,
 * <code>fn:distinct-values</code> and <code>fn:deep-equal</code> on mixed
 * atomic values, the aggregate functions and the round trip of
 * <code>fn:string-join</code> and <code>fn:tokenize</code>.
 *
 * @author Philip Helger
 */
public final class FnSequencePropertyTest
{
  private static final IGenerator <List <Integer>> INT_LISTS = Generators.lists (Generators.ints (-6, 6), 0, 10);
  private static final IGenerator <List <String>> STRING_LISTS = Generators.lists (Generators.of ("a",
                                                                                                    "b",
                                                                                                    "",
                                                                                                    "ab",
                                                                                                    "A",
                                                                                                    "\u00e9",
                                                                                                    "\ufffd",
                                                                                                    "\ud83d\ude00"),
                                                                                   0,
                                                                                   8);
  private static final IGenerator <Integer> POSITIONS = Generators.ints (-3, 13);

  /**
   * A model of a mixed atomic value: kind 0 = xs:integer, 1 = xs:double, 2 =
   * xs:string, 3 = xs:untypedAtomic; value 99 for the double stands for NaN.
   */
  private static final IGenerator <int []> MIXED_ATOMICS = Generators.lists (Generators.ints (0, 3), 2, 2)
                                                                     .flatMap (x -> Generators.oneOf (Generators.ints (-2, 2),
                                                                                                      Generators.constant (Integer.valueOf (99)))
                                                                                              .map (v -> new int [] { x.get (0)
                                                                                                                       .intValue (),
                                                                                                                      x.get (0)
                                                                                                                       .intValue () == 1 ||
                                                                                                                                   v.intValue () != 99 ? v.intValue ()
                                                                                                                                                       : 0 }));
  private static final IGenerator <List <int []>> MIXED_LISTS = Generators.lists (MIXED_ATOMICS, 0, 6);

  private final XPathPropertyTestHelper m_aHelper = new XPathPropertyTestHelper ();

  @NonNull
  private static XdmSequence _ints (@NonNull final List <Integer> aList)
  {
    final List <IXdmItem> aItems = new ArrayList <> ();
    for (final Integer aValue : aList)
      aItems.add (XdmAtomic.integer (aValue.longValue ()));
    return XdmSequence.of (aItems);
  }

  @NonNull
  private static XdmSequence _strings (@NonNull final List <String> aList)
  {
    final List <IXdmItem> aItems = new ArrayList <> ();
    for (final String sValue : aList)
      aItems.add (XdmAtomic.string (sValue));
    return XdmSequence.of (aItems);
  }

  @NonNull
  private static XdmAtomic _mixed (@NonNull final int [] aModel)
  {
    switch (aModel[0])
    {
      case 0:
        return XdmAtomic.integer (aModel[1]);
      case 1:
        return XdmAtomic.dbl (aModel[1] == 99 ? Double.NaN : aModel[1]);
      case 2:
        return XdmAtomic.string (Integer.toString (aModel[1]));
      default:
        return XdmAtomic.untyped (Integer.toString (aModel[1]));
    }
  }

  @NonNull
  private static XdmSequence _mixedList (@NonNull final List <int []> aList)
  {
    final List <IXdmItem> aItems = new ArrayList <> ();
    for (final int [] aModel : aList)
      aItems.add (_mixed (aModel));
    return XdmSequence.of (aItems);
  }

  /** @return The integers of a result sequence. */
  @NonNull
  private static List <Integer> _toInts (@NonNull final XdmSequence aSeq)
  {
    final List <Integer> ret = new ArrayList <> ();
    for (final IXdmItem aItem : aSeq)
      ret.add (Integer.valueOf (((XdmAtomic) aItem).getBigInteger ().intValue ()));
    return ret;
  }

  @NonNull
  private static List <String> _toStrings (@NonNull final XdmSequence aSeq)
  {
    final List <String> ret = new ArrayList <> ();
    for (final IXdmItem aItem : aSeq)
      ret.add (((XdmAtomic) aItem).getString ());
    return ret;
  }

  private static boolean _bool (@NonNull final XdmSequence aSeq)
  {
    assertEquals (1, aSeq.size ());
    return ((XdmAtomic) aSeq.get (0)).getBoolean ();
  }

  @NonNull
  private static String _str (@NonNull final XdmSequence aSeq)
  {
    assertEquals (1, aSeq.size ());
    return ((XdmAtomic) aSeq.get (0)).getString ();
  }

  /** @return The comparison of the code point sequences (the codepoint collation). */
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
   * <code>fn:sort</code> returns the values in ascending order, and with a
   * key function it is stable: items with equal keys keep their order
   * (F&amp;O 3.1 section 15.5.1), as {@link List#sort(Comparator)} does.
   */
  @Test
  public void testSort ()
  {
    new PropertyChecker ().setTries (1500).forAll (INT_LISTS, STRING_LISTS, (aInts, aStrings) -> {
      final List <Integer> aSorted = new ArrayList <> (aInts);
      aSorted.sort (Comparator.naturalOrder ());
      assertEquals (aInts.toString (), aSorted, _toInts (m_aHelper.eval ("sort($v)", null, _ints (aInts), XdmSequence.EMPTY)));
      final List <Integer> aByKey = new ArrayList <> (aInts);
      aByKey.sort (Comparator.comparingInt (x -> x.intValue () % 3));
      assertEquals (aInts.toString (),
                    aByKey,
                    _toInts (m_aHelper.eval ("sort($v, (), function($x) { $x mod 3 })", null, _ints (aInts), XdmSequence.EMPTY)));
      final List <Integer> aDescending = new ArrayList <> (aInts);
      aDescending.sort (Comparator.comparingInt (x -> -x.intValue ()));
      assertEquals (aInts.toString (),
                    aDescending,
                    _toInts (m_aHelper.eval ("sort($v, (), function($x) { -$x })", null, _ints (aInts), XdmSequence.EMPTY)));
      final List <String> aSortedStrings = new ArrayList <> (aStrings);
      aSortedStrings.sort (FnSequencePropertyTest::_compareCodepoints);
      assertEquals (aStrings.toString (),
                    aSortedStrings,
                    _toStrings (m_aHelper.eval ("sort($v)", null, _strings (aStrings), XdmSequence.EMPTY)));
      // Sorting by the string length keeps the order of strings of the same length
      final List <String> aByLength = new ArrayList <> (aStrings);
      aByLength.sort (Comparator.comparingLong (x -> x.codePoints ().count ()));
      assertEquals (aStrings.toString (),
                    aByLength,
                    _toStrings (m_aHelper.eval ("sort($v, (), string-length#1)", null, _strings (aStrings), XdmSequence.EMPTY)));
    });
  }

  /**
   * <code>fn:reverse</code>, <code>fn:subsequence</code>,
   * <code>fn:insert-before</code>, <code>fn:remove</code>,
   * <code>fn:index-of</code>, <code>fn:head</code> and <code>fn:tail</code>
   * agree with the Java list operations, including positions outside the
   * sequence (F&amp;O 3.1 sections 14.1 and 14.2).
   */
  @Test
  public void testListOperations ()
  {
    new PropertyChecker ().setTries (2000).forAll (INT_LISTS, INT_LISTS, POSITIONS, POSITIONS, (aList, aOther, aPos, aLen) -> {
      final XdmSequence aV = _ints (aList);
      final XdmSequence aW = _ints (List.of (aPos, aLen));
      final int nPos = aPos.intValue ();
      final int nLen = aLen.intValue ();
      final int nSize = aList.size ();
      final String sMsg = aList + " " + nPos + " " + nLen;

      final List <Integer> aReversed = new ArrayList <> (aList);
      Collections.reverse (aReversed);
      assertEquals (sMsg, aReversed, _toInts (m_aHelper.eval ("reverse($v)", null, aV, aW)));

      final List <Integer> aSub = new ArrayList <> ();
      final List <Integer> aSubOpen = new ArrayList <> ();
      for (int i = 0; i < nSize; ++i)
      {
        final int nP = i + 1;
        if (nP >= nPos && nP < nPos + nLen)
          aSub.add (aList.get (i));
        if (nP >= nPos)
          aSubOpen.add (aList.get (i));
      }
      assertEquals (sMsg, aSub, _toInts (m_aHelper.eval ("subsequence($v, $w[1], $w[2])", null, aV, aW)));
      assertEquals (sMsg, aSubOpen, _toInts (m_aHelper.eval ("subsequence($v, $w[1])", null, aV, aW)));

      final List <Integer> aRemoved = new ArrayList <> (aList);
      if (nPos >= 1 && nPos <= nSize)
        aRemoved.remove (nPos - 1);
      assertEquals (sMsg, aRemoved, _toInts (m_aHelper.eval ("remove($v, $w[1])", null, aV, aW)));

      final List <Integer> aInserted = new ArrayList <> (aList);
      aInserted.addAll (Math.min (Math.max (nPos, 1), nSize + 1) - 1, aOther);
      final XdmSequence aInsertedResult = m_aHelper.eval ("insert-before($v[position() gt 1], $v[1], $w)",
                                                           null,
                                                           XdmSequence.concat (XdmSequence.of (XdmAtomic.integer (nPos)), aV),
                                                           _ints (aOther));
      assertEquals (sMsg + " insert " + aOther, aInserted, _toInts (aInsertedResult));

      final List <Integer> aIndexes = new ArrayList <> ();
      for (int i = 0; i < nSize; ++i)
        if (aList.get (i).intValue () == nLen)
          aIndexes.add (Integer.valueOf (i + 1));
      assertEquals (sMsg, aIndexes, _toInts (m_aHelper.eval ("index-of($v, $w[2])", null, aV, aW)));

      assertEquals (sMsg, nSize == 0 ? List.of () : List.of (aList.get (0)), _toInts (m_aHelper.eval ("head($v)", null, aV, aW)));
      assertEquals (sMsg,
                    nSize == 0 ? List.of () : aList.subList (1, nSize),
                    _toInts (m_aHelper.eval ("tail($v)", null, aV, aW)));
    });
  }

  @NonNull
  private static String _key (@NonNull final int [] aModel)
  {
    // Numbers compare by value (NaN equals NaN in distinct-values), strings and untyped values by their string
    if (aModel[0] <= 1)
      return aModel[1] == 99 ? "NaN" : "n" + aModel[1];
    return "s" + aModel[1];
  }

  /**
   * <code>fn:distinct-values</code> keeps one value per class of equal values
   * (numbers by value across types, NaN equal to itself, strings and
   * untypedAtomic values by their string, F&amp;O 3.1 section 14.1.2); the
   * result is idempotent and a subset of the input. <code>fn:deep-equal</code>
   * agrees with the pairwise model, is reflexive and symmetric (F&amp;O 3.1
   * section 14.2.1).
   */
  @Test
  public void testDistinctValuesAndDeepEqual ()
  {
    new PropertyChecker ().setTries (2000).forAll (MIXED_LISTS, MIXED_LISTS, (aList1, aList2) -> {
      final XdmSequence aV = _mixedList (aList1);
      final XdmSequence aW = _mixedList (aList2);
      final String sMsg = m_aHelper.eval ("serialize($v, map{'method':'adaptive'})", null, aV, aW) +
                          " / " +
                          m_aHelper.eval ("serialize($w, map{'method':'adaptive'})", null, aV, aW);

      final Set <String> aKeys = new HashSet <> ();
      for (final int [] aModel : aList1)
        aKeys.add (_key (aModel));
      final XdmSequence aDistinct = m_aHelper.eval ("distinct-values($v)", null, aV, aW);
      assertEquals (sMsg, aKeys.size (), aDistinct.size ());
      final Set <String> aResultKeys = new HashSet <> ();
      for (final IXdmItem aItem : aDistinct)
      {
        final XdmAtomic aAtomic = (XdmAtomic) aItem;
        final boolean bNumeric = aAtomic.getType () == EAtomicType.INTEGER || aAtomic.getType () == EAtomicType.DOUBLE;
        final double d = bNumeric ? aAtomic.getAsDouble () : 0;
        aResultKeys.add (bNumeric ? (Double.isNaN (d) ? "NaN" : "n" + (int) d) : "s" + aAtomic.getString ());
      }
      assertEquals (sMsg, aKeys, aResultKeys);
      assertTrue (sMsg, _bool (m_aHelper.eval ("count(distinct-values(distinct-values($v))) eq count(distinct-values($v))", null, aV, aW)));

      boolean bEqual = aList1.size () == aList2.size ();
      for (int i = 0; bEqual && i < aList1.size (); ++i)
      {
        final int [] a1 = aList1.get (i);
        final int [] a2 = aList2.get (i);
        // A number never equals a string; untypedAtomic compares as a string
        bEqual = (a1[0] <= 1) == (a2[0] <= 1) && _key (a1).equals (_key (a2));
      }
      assertEquals (sMsg, Boolean.valueOf (bEqual), Boolean.valueOf (_bool (m_aHelper.eval ("deep-equal($v, $w)", null, aV, aW))));
      assertEquals (sMsg, Boolean.valueOf (bEqual), Boolean.valueOf (_bool (m_aHelper.eval ("deep-equal($w, $v)", null, aV, aW))));
      assertTrue (sMsg, _bool (m_aHelper.eval ("deep-equal($v, $v)", null, aV, aW)));
      assertTrue (sMsg, _bool (m_aHelper.eval ("deep-equal([$v], [$v])", null, aV, aW)));
    });
  }

  /**
   * <code>fn:count</code>, <code>fn:sum</code>, <code>fn:min</code> and
   * <code>fn:max</code> of integers agree with Java (F&amp;O 3.1 section
   * 14.4), with the documented results for the empty sequence.
   */
  @Test
  public void testAggregates ()
  {
    new PropertyChecker ().setTries (1500).forAll (INT_LISTS, aList -> {
      final XdmSequence aV = _ints (aList);
      long nSum = 0;
      for (final Integer aValue : aList)
        nSum += aValue.intValue ();
      final String sMsg = aList.toString ();
      assertEquals (sMsg, Integer.toString (aList.size ()), _str (m_aHelper.eval ("string(count($v))", null, aV, XdmSequence.EMPTY)));
      assertEquals (sMsg, Long.toString (nSum), _str (m_aHelper.eval ("string(sum($v))", null, aV, XdmSequence.EMPTY)));
      assertEquals (sMsg,
                    aList.isEmpty () ? "" : aList.stream ().min (Comparator.naturalOrder ()).get ().toString (),
                    _str (m_aHelper.eval ("string(min($v))", null, aV, XdmSequence.EMPTY)));
      assertEquals (sMsg,
                    aList.isEmpty () ? "" : aList.stream ().max (Comparator.naturalOrder ()).get ().toString (),
                    _str (m_aHelper.eval ("string(max($v))", null, aV, XdmSequence.EMPTY)));
      assertTrue (sMsg, _bool (m_aHelper.eval ("empty($v) or avg($v) * count($v) eq sum($v) or abs(avg($v) * count($v) - sum($v)) lt 0.000001",
                                        null,
                                        aV,
                                        XdmSequence.EMPTY)));
    });
  }

  /**
   * <code>fn:tokenize</code> with a separator that does not occur in the
   * parts inverts <code>fn:string-join</code>, except that the empty string
   * tokenizes to the empty sequence (F&amp;O 3.1 sections 5.4.2 and 5.6.4).
   */
  @Test
  public void testStringJoinTokenize ()
  {
    new PropertyChecker ().setTries (1500).forAll (STRING_LISTS, Generators.of (",", ";", "--", "\ud800\udc00"), (aList, sSep) -> {
      final XdmSequence aV = _strings (aList);
      final XdmSequence aW = XdmSequence.of (XdmAtomic.string (sSep));
      final String sJoined = String.join (sSep, aList);
      assertEquals (aList.toString (), sJoined, _str (m_aHelper.eval ("string-join($v, $w)", null, aV, aW)));
      final List <String> aExpected = sJoined.isEmpty () ? List.of () : aList;
      assertEquals (aList.toString (), aExpected, _toStrings (m_aHelper.eval ("tokenize(string-join($v, $w), $w)", null, aV, aW)));
    });
  }
}

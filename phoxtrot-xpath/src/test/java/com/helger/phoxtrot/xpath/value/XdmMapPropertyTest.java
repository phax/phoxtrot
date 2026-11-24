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
package com.helger.phoxtrot.xpath.value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.XPathPropertyTestHelper;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the persistent hash trie {@link XdmMap}: random
 * sequences of <code>put</code> and <code>remove</code>, each applied to any
 * earlier version, are compared with a {@link LinkedHashMap} per version.
 * Keys are compared as with <code>op:same-key</code> (XPath 3.1 section
 * 17.1.1): strings by code points, numbers by their exact value. The string
 * keys include ones with equal Java hash codes, so that the collision lists
 * of the trie are used.
 *
 * @author Philip Helger
 */
public final class XdmMapPropertyTest
{
  /** Keys of several types; "Aa" and "BB" have the same Java hash code */
  private static final XdmAtomic [] KEYS = { XdmAtomic.string (""),
                                             XdmAtomic.string ("a"),
                                             XdmAtomic.string ("Aa"),
                                             XdmAtomic.string ("BB"),
                                             XdmAtomic.string ("AaAa"),
                                             XdmAtomic.string ("AaBB"),
                                             XdmAtomic.string ("BBAa"),
                                             XdmAtomic.string ("BBBB"),
                                             XdmAtomic.string ("1"),
                                             XdmAtomic.untyped ("a"),
                                             XdmAtomic.anyUri ("Aa"),
                                             XdmAtomic.integer (0),
                                             XdmAtomic.integer (1),
                                             XdmAtomic.integer (-1),
                                             XdmAtomic.integer (31),
                                             XdmAtomic.integer (32),
                                             XdmAtomic.integer (1024),
                                             XdmAtomic.integer (BigInteger.ONE.shiftLeft (64).add (BigInteger.ONE)),
                                             XdmAtomic.integer (BigInteger.ONE.shiftLeft (64)),
                                             XdmAtomic.decimal (new BigDecimal ("1.0")),
                                             XdmAtomic.decimal (new BigDecimal ("2.50")),
                                             XdmAtomic.decimal (new BigDecimal ("0.1")),
                                             XdmAtomic.decimal (new BigDecimal ("0.5")),
                                             XdmAtomic.dbl (1.0),
                                             XdmAtomic.dbl (0.1),
                                             XdmAtomic.dbl (2.5),
                                             XdmAtomic.dbl (-0.0),
                                             XdmAtomic.dbl (Double.NaN),
                                             XdmAtomic.dbl (Double.POSITIVE_INFINITY),
                                             XdmAtomic.flt (0.5f),
                                             XdmAtomic.flt (Float.NaN),
                                             XdmAtomic.flt (0.1f) };

  /** One map operation: put (with value) or remove, on an earlier version */
  @Immutable
  private record Op (int base, boolean put, XdmAtomic key, XdmSequence value)
  {}

  private static final IGenerator <XdmSequence> VALUES = Generators.oneOf (Generators.ints (0, 1000)
                                                                                     .map (x -> XdmSequence.of (XdmAtomic.integer (x.longValue ()))),
                                                                           Generators.of (XdmSequence.EMPTY,
                                                                                          XdmSequence.of (XdmAtomic.string ("s")),
                                                                                          XdmSequence.of (XdmAtomic.integer (1),
                                                                                                          XdmAtomic.integer (2))));

  /** Up to 80 operations, mostly on the latest version */
  private static final IGenerator <List <Op>> OPS = Generators.lists (Generators.lists (Generators.ints (0, 1000), 3, 3)
                                                                                .flatMap (x -> VALUES.map (aValue -> new Op (x.get (0)
                                                                                                                              .intValue (),
                                                                                                                             x.get (1)
                                                                                                                              .intValue () %
                                                                                                                                         4 != 0,
                                                                                                                             KEYS[x.get (2)
                                                                                                                                   .intValue () %
                                                                                                                                  KEYS.length],
                                                                                                                             aValue))),
                                                                      0,
                                                                      80);

  /** Maps from a chain of operations on the empty map */
  private static final IGenerator <XdmMap> MAPS = OPS.map (aOps -> {
    XdmMap ret = XdmMap.EMPTY;
    for (final Op aOp : aOps)
      ret = aOp.put () ? ret.put (aOp.key (), aOp.value ()) : ret.remove (aOp.key ());
    return ret;
  });

  /** @return The key of the model, equal for keys that are the same key in XPath. */
  @NonNull
  private static String _getModelKey (@NonNull final XdmAtomic aKey)
  {
    final Object aPayload = aKey.getValue ();
    if (aPayload instanceof final String sValue)
      return "s:" + sValue;
    final BigDecimal aExact;
    if (aPayload instanceof final BigInteger aBI)
      aExact = new BigDecimal (aBI);
    else
      if (aPayload instanceof final BigDecimal aBD)
        aExact = aBD;
      else
      {
        final double d = ((Number) aPayload).doubleValue ();
        if (Double.isNaN (d) || Double.isInfinite (d))
          return "n:" + d;
        aExact = new BigDecimal (d);
      }
    return aExact.signum () == 0 ? "n:0" : "n:" + aExact.stripTrailingZeros ().toPlainString ();
  }

  /** @return The version an operation works on: mostly the latest, sometimes an earlier one. */
  private static int _getBase (@NonNull final Op aOp, final int nVersions)
  {
    return aOp.base () % 3 == 0 ? aOp.base () % nVersions : nVersions - 1;
  }

  private static void _assertMatchesModel (@NonNull final String sMsg,
                                           @NonNull final Map <String, XdmMap.Entry> aModel,
                                           @NonNull final XdmMap aMap)
  {
    assertEquals (sMsg, aModel.size (), aMap.size ());
    assertEquals (sMsg, Boolean.valueOf (aModel.isEmpty ()), Boolean.valueOf (aMap.isEmpty ()));
    for (final XdmAtomic aKey : KEYS)
    {
      final XdmMap.Entry aExpected = aModel.get (_getModelKey (aKey));
      if (aExpected == null)
      {
        assertFalse (sMsg, aMap.containsKey (aKey));
        assertNull (sMsg, aMap.get (aKey));
      }
      else
      {
        assertTrue (sMsg, aMap.containsKey (aKey));
        assertSame (sMsg, aExpected.value (), aMap.get (aKey));
        assertSame (sMsg, aExpected.key (), aMap.getEntry (aKey).key ());
      }
    }
    // Iteration in insertion order; a replaced entry keeps its position
    final Iterator <XdmMap.Entry> aIt = aMap.entries ().iterator ();
    for (final XdmMap.Entry aExpected : aModel.values ())
    {
      final XdmMap.Entry aActual = aIt.next ();
      assertSame (sMsg, aExpected.key (), aActual.key ());
      assertSame (sMsg, aExpected.value (), aActual.value ());
    }
    assertFalse (sMsg, aIt.hasNext ());
    assertEquals (sMsg, aModel.size (), aMap.getAllKeys ().size ());
    // The uniform types are only a hint, but must be sound
    final EAtomicType eKeyType = aMap.getUniformKeyType ();
    if (eKeyType != null)
      for (final XdmMap.Entry aEntry : aModel.values ())
        assertSame (sMsg, eKeyType, aEntry.key ().getType ());
    final EAtomicType eValueType = aMap.getUniformValueType ();
    if (eValueType != null)
      for (final XdmMap.Entry aEntry : aModel.values ())
        assertSame (sMsg, eValueType, aEntry.value ().getSingleAtomicType ());
  }

  /**
   * Every version created by <code>put</code> and <code>remove</code>
   * (applied to the latest or to any earlier version) has the entries of its
   * model, in insertion order, and stays unchanged by later operations.
   */
  @Test
  public void testPersistentPutRemove ()
  {
    new PropertyChecker ().setTries (500).forAll (OPS, aOps -> {
      final List <XdmMap> aVersions = new ArrayList <> ();
      final List <Map <String, XdmMap.Entry>> aModels = new ArrayList <> ();
      aVersions.add (XdmMap.EMPTY);
      aModels.add (new LinkedHashMap <> ());
      for (final Op aOp : aOps)
      {
        final int nBase = _getBase (aOp, aVersions.size ());
        final Map <String, XdmMap.Entry> aModel = new LinkedHashMap <> (aModels.get (nBase));
        final String sModelKey = _getModelKey (aOp.key ());
        final XdmMap aNew;
        if (aOp.put ())
        {
          aNew = aVersions.get (nBase).put (aOp.key (), aOp.value ());
          aModel.put (sModelKey, new XdmMap.Entry (aOp.key (), aOp.value ()));
        }
        else
        {
          aNew = aVersions.get (nBase).remove (aOp.key ());
          aModel.remove (sModelKey);
        }
        aVersions.add (aNew);
        aModels.add (aModel);
      }
      for (int i = 0; i < aVersions.size (); ++i)
        _assertMatchesModel ("version " + i, aModels.get (i), aVersions.get (i));
    });
  }

  /**
   * A {@link XdmMap.Builder} started from a map and given the same operations
   * builds a map with the same entries in the same order as the persistent
   * operations.
   */
  @Test
  public void testBuilderMatchesPersistent ()
  {
    new PropertyChecker ().setTries (500).forAll (OPS, OPS, (aInitOps, aOps) -> {
      XdmMap aBase = XdmMap.EMPTY;
      for (final Op aOp : aInitOps)
        aBase = aOp.put () ? aBase.put (aOp.key (), aOp.value ()) : aBase.remove (aOp.key ());
      final XdmMap.Builder aBuilder = new XdmMap.Builder (aBase);
      XdmMap aPersistent = aBase;
      final Map <String, XdmMap.Entry> aModel = new LinkedHashMap <> ();
      for (final XdmMap.Entry aEntry : aBase.entries ())
        aModel.put (_getModelKey (aEntry.key ()), aEntry);
      for (final Op aOp : aOps)
        if (aOp.put ())
        {
          aBuilder.put (aOp.key (), aOp.value ());
          aPersistent = aPersistent.put (aOp.key (), aOp.value ());
          aModel.put (_getModelKey (aOp.key ()), new XdmMap.Entry (aOp.key (), aOp.value ()));
        }
        else
        {
          aBuilder.remove (aOp.key ());
          aPersistent = aPersistent.remove (aOp.key ());
          aModel.remove (_getModelKey (aOp.key ()));
        }
      _assertMatchesModel ("persistent", aModel, aPersistent);
      _assertMatchesModel ("builder", aModel, aBuilder.build ());
    });
  }


  /**
   * <code>map:merge</code> of two maps contains every key of both, with the
   * value chosen by the <code>duplicates</code> option (F&amp;O 3.1 section
   * 17.1.2), and raises FOJS0003 for a duplicate with <code>reject</code>;
   * <code>map:remove</code> with the keys of the other map leaves the keys
   * that are not in it.
   */
  @Test
  public void testMerge ()
  {
    final XPathPropertyTestHelper aHelper = new XPathPropertyTestHelper ();
    new PropertyChecker ().setTries (300)
                          .forAll (MAPS,
                                   MAPS,
                                   Generators.of ("use-first", "use-last", "combine", "use-any", "reject"),
                                   (aV, aW, sDuplicates) -> {
                                     final Map <String, XdmMap.Entry> aVModel = new LinkedHashMap <> ();
                                     for (final XdmMap.Entry aEntry : aV.entries ())
                                       aVModel.put (_getModelKey (aEntry.key ()), aEntry);
                                     final Map <String, XdmMap.Entry> aWModel = new LinkedHashMap <> ();
                                     for (final XdmMap.Entry aEntry : aW.entries ())
                                       aWModel.put (_getModelKey (aEntry.key ()), aEntry);
                                     boolean bDuplicate = false;
                                     for (final String sKey : aWModel.keySet ())
                                       if (aVModel.containsKey (sKey))
                                         bDuplicate = true;

                                     final String sExpr = "map:merge(($v, $w), map { 'duplicates' : '" + sDuplicates + "' })";
                                     XdmMap aMerged = null;
                                     String sError = null;
                                     try
                                     {
                                       aMerged = (XdmMap) aHelper.eval (sExpr, null, XdmSequence.of (aV), XdmSequence.of (aW))
                                                                 .get (0);
                                     }
                                     catch (final XPathException ex)
                                     {
                                       sError = ex.getCode ();
                                     }
                                     if (sDuplicates.equals ("reject") && bDuplicate)
                                     {
                                       assertEquals ("FOJS0003", sError);
                                       return;
                                     }
                                     assertNull (sError);
                                     final Map <String, XdmMap.Entry> aAll = new LinkedHashMap <> (aVModel);
                                     for (final Map.Entry <String, XdmMap.Entry> aEntry : aWModel.entrySet ())
                                       aAll.putIfAbsent (aEntry.getKey (), aEntry.getValue ());
                                     assertEquals (aAll.size (), aMerged.size ());
                                     for (final Map.Entry <String, XdmMap.Entry> aEntry : aAll.entrySet ())
                                     {
                                       final XdmMap.Entry aInV = aVModel.get (aEntry.getKey ());
                                       final XdmMap.Entry aInW = aWModel.get (aEntry.getKey ());
                                       final XdmSequence aActual = aMerged.get (aEntry.getValue ().key ());
                                       if (aInV == null || aInW == null)
                                         assertSame (aEntry.getValue ().value (), aActual);
                                       else
                                         switch (sDuplicates)
                                         {
                                           case "use-first":
                                           case "reject":
                                             assertSame (aInV.value (), aActual);
                                             break;
                                           case "use-last":
                                             assertSame (aInW.value (), aActual);
                                             break;
                                           case "combine":
                                             assertEquals (aInV.value ().size () + aInW.value ().size (), aActual.size ());
                                             break;
                                           default:
                                             assertTrue (aActual == aInV.value () || aActual == aInW.value ());
                                             break;
                                         }
                                     }

                                     final XdmMap aRemoved = (XdmMap) aHelper.eval ("map:remove($v, map:keys($w))",
                                                                                    null,
                                                                                    XdmSequence.of (aV),
                                                                                    XdmSequence.of (aW))
                                                                              .get (0);
                                     final Map <String, XdmMap.Entry> aRest = new LinkedHashMap <> (aVModel);
                                     aRest.keySet ().removeAll (aWModel.keySet ());
                                     _assertMatchesModel ("map:remove", aRest, aRemoved);
                                   });
  }
}

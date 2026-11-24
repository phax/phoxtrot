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
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.XPathPropertyTestHelper;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for {@link XdmArray}, whose <code>append</code> shares
 * its backing array with the array it was appended to: random sequences of
 * operations, each applied to the latest or an earlier version (so that two
 * arrays append to the same base), are compared with a {@link List} per
 * version. Every version must stay unchanged.
 *
 * @author Philip Helger
 */
public final class XdmArrayPropertyTest
{
  private static final int OP_APPEND = 0;
  private static final int OP_PUT = 1;
  private static final int OP_INSERT = 2;
  private static final int OP_SUBARRAY = 3;
  private static final int OP_REVERSE = 4;
  private static final int OP_COUNT = 5;

  /** One array operation on an earlier version, with two numbers for positions */
  @Immutable
  private record Op (int base, int kind, int pos1, int pos2, XdmSequence value)
  {}

  private static final IGenerator <XdmSequence> VALUES = Generators.oneOf (Generators.ints (0, 1000)
                                                                                     .map (x -> XdmSequence.of (XdmAtomic.integer (x.longValue ()))),
                                                                           Generators.of (XdmSequence.EMPTY,
                                                                                          XdmSequence.of (XdmAtomic.string ("s")),
                                                                                          XdmSequence.of (XdmAtomic.dbl (1.5)),
                                                                                          XdmSequence.of (XdmAtomic.integer (1),
                                                                                                          XdmAtomic.integer (2))));

  /** Up to 60 operations; appends are the most frequent */
  private static final IGenerator <List <Op>> OPS = Generators.lists (Generators.lists (Generators.ints (0, 1000), 4, 4)
                                                                                .flatMap (x -> VALUES.map (aValue -> {
                                                                                  final int nKind = x.get (1).intValue () % 10;
                                                                                  return new Op (x.get (0).intValue (),
                                                                                                 nKind < OP_COUNT ? nKind : OP_APPEND,
                                                                                                 x.get (2).intValue (),
                                                                                                 x.get (3).intValue (),
                                                                                                 aValue);
                                                                                })),
                                                                      0,
                                                                      60);

  @Nullable
  private static EAtomicType _getUniformType (@NonNull final List <XdmSequence> aModel)
  {
    if (aModel.isEmpty ())
      return null;
    final EAtomicType ret = aModel.get (0).getSingleAtomicType ();
    for (final XdmSequence aMember : aModel)
      if (aMember.getSingleAtomicType () != ret)
        return null;
    return ret;
  }

  private static void _assertMatchesModel (@NonNull final String sMsg,
                                           @NonNull final List <XdmSequence> aModel,
                                           @NonNull final XdmArray aArray)
  {
    assertEquals (sMsg, aModel.size (), aArray.size ());
    for (int i = 0; i < aModel.size (); ++i)
      assertSame (sMsg + " [" + i + "]", aModel.get (i), aArray.get (i));
    final List <XdmSequence> aMembers = aArray.members ();
    assertEquals (sMsg, aModel.size (), aMembers.size ());
    for (int i = 0; i < aModel.size (); ++i)
      assertSame (sMsg + " [" + i + "]", aModel.get (i), aMembers.get (i));
    assertEquals (sMsg, aModel.size (), aArray.getAllMembers ().size ());
    assertSame (sMsg, _getUniformType (aModel), aArray.getUniformMemberType ());
  }

  /**
   * <code>append</code>, <code>put</code>, <code>insertBefore</code>,
   * <code>subArray</code> and <code>reverse</code> applied to any version give
   * the members of the model, the uniform member type is exact, and no
   * operation changes an earlier version, also when two arrays append to the
   * same base.
   */
  @Test
  public void testPersistentOperations ()
  {
    new PropertyChecker ().setTries (1000).forAll (OPS, aOps -> {
      final List <XdmArray> aVersions = new ArrayList <> ();
      final List <List <XdmSequence>> aModels = new ArrayList <> ();
      aVersions.add (XdmArray.EMPTY);
      aModels.add (new ArrayList <> ());
      for (final Op aOp : aOps)
      {
        // Mostly the latest version, every third operation an earlier one
        final int nBase = aOp.base () % 3 == 0 ? aOp.base () % aVersions.size () : aVersions.size () - 1;
        final XdmArray aArray = aVersions.get (nBase);
        final List <XdmSequence> aModel = new ArrayList <> (aModels.get (nBase));
        final int nSize = aModel.size ();
        final XdmArray aNew;
        switch (aOp.kind ())
        {
          case OP_PUT:
            if (nSize == 0)
              continue;
            aNew = aArray.put (aOp.pos1 () % nSize, aOp.value ());
            aModel.set (aOp.pos1 () % nSize, aOp.value ());
            break;
          case OP_INSERT:
            aNew = aArray.insertBefore (aOp.pos1 () % (nSize + 1), aOp.value ());
            aModel.add (aOp.pos1 () % (nSize + 1), aOp.value ());
            break;
          case OP_SUBARRAY:
          {
            final int nFrom = aOp.pos1 () % (nSize + 1);
            final int nTo = nFrom + aOp.pos2 () % (nSize - nFrom + 1);
            aNew = aArray.subArray (nFrom, nTo);
            aModel.subList (nTo, nSize).clear ();
            aModel.subList (0, nFrom).clear ();
            break;
          }
          case OP_REVERSE:
            aNew = aArray.reverse ();
            Collections.reverse (aModel);
            break;
          default:
            aNew = aArray.append (aOp.value ());
            aModel.add (aOp.value ());
            break;
        }
        aVersions.add (aNew);
        aModels.add (aModel);
      }
      for (int i = 0; i < aVersions.size (); ++i)
        _assertMatchesModel ("version " + i, aModels.get (i), aVersions.get (i));
    });
  }

  @Nullable
  private static String _getErrorCode (@NonNull final XPathPropertyTestHelper aHelper,
                                       @NonNull final String sExpr,
                                       @NonNull final XdmSequence aV,
                                       @NonNull final XdmSequence aW)
  {
    try
    {
      aHelper.eval (sExpr, null, aV, aW);
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  private static void _assertMatchesValues (@NonNull final List <XdmSequence> aExpected, @NonNull final XdmSequence aResult)
  {
    final XdmArray aArray = (XdmArray) aResult.get (0);
    assertEquals (aExpected.size (), aArray.size ());
    for (int i = 0; i < aExpected.size (); ++i)
      XPathPropertyTestHelper.assertSameItems ("[" + i + "]", aExpected.get (i), aArray.get (i));
  }

  /**
   * The array functions <code>array:append</code>, <code>array:insert-before</code>,
   * <code>array:remove</code>, <code>array:subarray</code>,
   * <code>array:reverse</code> and the lookup agree with the same operations
   * on a list (F&amp;O 3.1 section 17.3), and leave the input array
   * unchanged.
   */
  @Test
  public void testArrayFunctions ()
  {
    final XPathPropertyTestHelper aHelper = new XPathPropertyTestHelper ();
    final IGenerator <List <Integer>> aLists = Generators.lists (Generators.ints (0, 99), 0, 20);
    new PropertyChecker ().setTries (500).forAll (aLists, Generators.ints (0, 25), Generators.ints (0, 25), (aList, aPos, aLen) -> {
      final List <XdmSequence> aMembers = new ArrayList <> ();
      for (final Integer aValue : aList)
        aMembers.add (XdmSequence.of (XdmAtomic.integer (aValue.longValue ())));
      final XdmArray aArray = XdmArray.of (aMembers);
      final int nSize = aMembers.size ();
      final int nPos = aPos.intValue ();
      final int nLen = aLen.intValue ();
      final XdmSequence aV = XdmSequence.of (aArray);
      final XdmSequence aW = XdmSequence.of (XdmAtomic.integer (nPos), XdmAtomic.integer (nLen));

      // The members of a result array of integers as a Java list, or the error code
      final String sAppend = "array:append($v, 1000)";
      final List <XdmSequence> aExpected = new ArrayList <> (aMembers);
      aExpected.add (XdmSequence.of (XdmAtomic.integer (1000)));
      _assertMatchesValues (aExpected, aHelper.eval (sAppend, null, aV, aW));

      if (nPos >= 1 && nPos <= nSize + 1)
      {
        final List <XdmSequence> aInserted = new ArrayList <> (aMembers);
        aInserted.add (nPos - 1, XdmSequence.of (XdmAtomic.integer (1000)));
        _assertMatchesValues (aInserted, aHelper.eval ("array:insert-before($v, $w[1], 1000)", null, aV, aW));
      }
      else
        assertEquals ("FOAY0001", _getErrorCode (aHelper, "array:insert-before($v, $w[1], 1000)", aV, aW));

      if (nPos >= 1 && nPos <= nSize)
      {
        final List <XdmSequence> aRemoved = new ArrayList <> (aMembers);
        aRemoved.remove (nPos - 1);
        _assertMatchesValues (aRemoved, aHelper.eval ("array:remove($v, $w[1])", null, aV, aW));
        _assertMatchesValues (List.of (aMembers.get (nPos - 1)), aHelper.eval ("array { $v($w[1]) }", null, aV, aW));
      }
      else
        assertEquals ("FOAY0001", _getErrorCode (aHelper, "array:remove($v, $w[1])", aV, aW));

      if (nPos >= 1 && nPos + nLen <= nSize + 1)
        _assertMatchesValues (aMembers.subList (nPos - 1, nPos - 1 + nLen),
                              aHelper.eval ("array:subarray($v, $w[1], $w[2])", null, aV, aW));
      else
        assertEquals ("FOAY0001", _getErrorCode (aHelper, "array:subarray($v, $w[1], $w[2])", aV, aW));

      final List <XdmSequence> aReversed = new ArrayList <> (aMembers);
      Collections.reverse (aReversed);
      _assertMatchesValues (aReversed, aHelper.eval ("array:reverse($v)", null, aV, aW));
      // The input is unchanged
      _assertMatchesModel ("input", aMembers, aArray);
    });
  }
}

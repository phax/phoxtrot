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
package com.helger.phoxtrot.xslt.ext.grouping;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
 * Property based tests for xsl:for-each-group ({@link ForEachGroupInstruction},
 * XSLT 3.0 section 14) against grouping in Java: group-by (with one and with
 * several keys per item) in the order of first appearance, group-adjacent,
 * group-starting-with and group-ending-with.
 *
 * @author Philip Helger
 */
public final class ForEachGroupInstructionPropertyTest
{
  /** One item: its position, grouping key, start and end marks and key tokens */
  private record Item (int pos, @NonNull String key, boolean start, boolean end, @NonNull List <String> tokens)
  {}

  private static final IGenerator <String> KEYS = Generators.of ("x", "y", "z", "1", "01");

  private static final IGenerator <Item> ITEM = aChoices -> new Item (0,
                                                                      KEYS.generate (aChoices),
                                                                      aChoices.draw (1) == 1,
                                                                      aChoices.draw (1) == 1,
                                                                      Generators.lists (KEYS, 0, 3).generate (aChoices));

  private static final IGenerator <List <Item>> ITEMS = Generators.lists (ITEM, 0, 10).map (l -> {
    final List <Item> ret = new ArrayList <> ();
    for (int i = 0; i < l.size (); ++i)
    {
      final Item aItem = l.get (i);
      ret.add (new Item (i, aItem.key (), aItem.start (), aItem.end (), aItem.tokens ()));
    }
    return ret;
  });

  private static final XsltExecutable SHEET = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                    "<xsl:output method='text'/>" +
                                                                    "<xsl:template match='/'>" +
                                                                    "<xsl:for-each-group select='r/i' group-by='@g'>" +
                                                                    "<xsl:value-of select=\"current-grouping-key(), ':', current-group()/@p, ';'\" separator=' '/>" +
                                                                    "</xsl:for-each-group>|" +
                                                                    "<xsl:for-each-group select='r/i' group-by='tokenize(@m)'>" +
                                                                    "<xsl:value-of select=\"current-grouping-key(), ':', current-group()/@p, ';'\" separator=' '/>" +
                                                                    "</xsl:for-each-group>|" +
                                                                    "<xsl:for-each-group select='r/i' group-adjacent='@g'>" +
                                                                    "<xsl:value-of select=\"current-grouping-key(), ':', current-group()/@p, ';'\" separator=' '/>" +
                                                                    "</xsl:for-each-group>|" +
                                                                    "<xsl:for-each-group select='r/i' group-starting-with='i[@s]'>" +
                                                                    "<xsl:value-of select=\"current-group()/@p, ';'\" separator=' '/>" +
                                                                    "</xsl:for-each-group>|" +
                                                                    "<xsl:for-each-group select='r/i' group-ending-with='i[@e]'>" +
                                                                    "<xsl:value-of select=\"current-group()/@p, ';'\" separator=' '/>" +
                                                                    "</xsl:for-each-group>" +
                                                                    "</xsl:template>" +
                                                                    XsltTestHelper.END,
                                                                    new XsltCompileOptions ());

  @NonNull
  private static String _source (@NonNull final List <Item> aItems)
  {
    final StringBuilder aSB = new StringBuilder ("<r>");
    for (final Item aItem : aItems)
    {
      aSB.append ("<i p='").append (aItem.pos ()).append ("' g='").append (aItem.key ()).append ("' m='").append (String.join (" ", aItem.tokens ())).append ('\'');
      if (aItem.start ())
        aSB.append (" s=''");
      if (aItem.end ())
        aSB.append (" e=''");
      aSB.append ("/>");
    }
    return aSB.append ("</r>").toString ();
  }

  private static void _appendGroup (@NonNull final StringBuilder aSB, @Nullable final String sKey, @NonNull final List <Integer> aPositions)
  {
    if (sKey != null)
      aSB.append (sKey).append (" : ");
    for (final Integer aPos : aPositions)
      aSB.append (aPos.intValue ()).append (' ');
    aSB.append (';');
  }

  private static void _appendGroups (@NonNull final StringBuilder aSB, @NonNull final Map <String, List <Integer>> aGroups)
  {
    for (final Map.Entry <String, List <Integer>> aEntry : aGroups.entrySet ())
      _appendGroup (aSB, aEntry.getKey (), aEntry.getValue ());
  }

  @NonNull
  private static String _expected (@NonNull final List <Item> aItems)
  {
    final StringBuilder aSB = new StringBuilder ();

    // group-by: groups in the order of their first item
    final Map <String, List <Integer>> aByKey = new LinkedHashMap <> ();
    for (final Item aItem : aItems)
      aByKey.computeIfAbsent (aItem.key (), k -> new ArrayList <> ()).add (Integer.valueOf (aItem.pos ()));
    _appendGroups (aSB, aByKey);
    aSB.append ('|');

    // group-by with several keys: an item is in the group of each distinct key, once
    final Map <String, List <Integer>> aByToken = new LinkedHashMap <> ();
    for (final Item aItem : aItems)
      for (final String sToken : aItem.tokens ())
      {
        final List <Integer> aGroup = aByToken.computeIfAbsent (sToken, k -> new ArrayList <> ());
        final Integer aPos = Integer.valueOf (aItem.pos ());
        if (!aGroup.contains (aPos))
          aGroup.add (aPos);
      }
    _appendGroups (aSB, aByToken);
    aSB.append ('|');

    // group-adjacent
    String sCurrent = null;
    List <Integer> aGroup = null;
    for (final Item aItem : aItems)
    {
      if (aGroup == null || !aItem.key ().equals (sCurrent))
      {
        if (aGroup != null)
          _appendGroup (aSB, sCurrent, aGroup);
        sCurrent = aItem.key ();
        aGroup = new ArrayList <> ();
      }
      aGroup.add (Integer.valueOf (aItem.pos ()));
    }
    if (aGroup != null)
      _appendGroup (aSB, sCurrent, aGroup);
    aSB.append ('|');

    // group-starting-with: a new group at each marked item except the first item
    aGroup = null;
    for (final Item aItem : aItems)
    {
      if (aGroup == null || aItem.start ())
      {
        if (aGroup != null)
          _appendGroup (aSB, null, aGroup);
        aGroup = new ArrayList <> ();
      }
      aGroup.add (Integer.valueOf (aItem.pos ()));
    }
    if (aGroup != null)
      _appendGroup (aSB, null, aGroup);
    aSB.append ('|');

    // group-ending-with: a group ends after each marked item
    aGroup = new ArrayList <> ();
    for (final Item aItem : aItems)
    {
      aGroup.add (Integer.valueOf (aItem.pos ()));
      if (aItem.end ())
      {
        _appendGroup (aSB, null, aGroup);
        aGroup = new ArrayList <> ();
      }
    }
    if (!aGroup.isEmpty ())
      _appendGroup (aSB, null, aGroup);
    return aSB.toString ();
  }

  /**
   * All five grouping methods form the groups, in the order and with the
   * members, that grouping in Java gives.
   */
  @Test
  public void testGroupsMatchJava ()
  {
    new PropertyChecker ().setTries (3000).forAll (ITEMS, aItems -> {
      final String sSource = _source (aItems);
      final String sResult = SHEET.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                                  .principalSerialized ();
      assertEquals (sSource, _expected (aItems), sResult);
    });
  }
}

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
package com.helger.phoxtrot.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigInteger;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for identity constraints (XML Schema 1.1 Part 1, section 3.11.4,
 * Identity-constraint Satisfied): random instances with items carrying an xs:int key, an xs:int
 * reference and an xs:string unique field are validated, and the verdict must equal a Java
 * computation: a key needs the field on every item and distinct values, a unique constraint only
 * distinct values among the items that have the field, and every reference must equal the value
 * of a key (or unique) field. Values compare in the value space, so <code>01</code>,
 * <code>+1</code> and <code> 1</code> are the same xs:int, while the xs:string values
 * <code>a</code> and <code>a </code> differ.
 *
 * @author Philip Helger
 */
public final class XsdIdentityConstraintPropertyTest
{
  /** Key values; null for an absent attribute */
  private static final String [] IDS = { null, "1", "01", " 2", "3", "+1", "x" };
  private static final String [] REFS = { null, "1", "+1", "2", "4", "03" };
  private static final String [] UNIQUES = { null, "a", "a ", "b", "A" };

  /**
   * A generated item: its attribute values, each <code>null</code> if absent.
   *
   * @param id
   *        The key field.
   * @param ref
   *        The reference field.
   * @param u
   *        The unique field.
   */
  private record Item (@Nullable String id, @Nullable String ref, @Nullable String u)
  {}

  @NonNull
  private static String _schema (final boolean bKey)
  {
    return XsdPropertyTestHelper.SCHEMA_START +
           "<xs:element name=\"r\"><xs:complexType><xs:sequence>" +
           "<xs:element name=\"item\" minOccurs=\"0\" maxOccurs=\"unbounded\"><xs:complexType>" +
           "<xs:attribute name=\"id\" type=\"xs:int\"/><xs:attribute name=\"ref\" type=\"xs:int\"/>" +
           "<xs:attribute name=\"u\" type=\"xs:string\"/></xs:complexType></xs:element>" +
           "</xs:sequence></xs:complexType>" +
           (bKey ? "<xs:key" : "<xs:unique") +
           " name=\"k\"><xs:selector xpath=\"item\"/><xs:field xpath=\"@id\"/>" +
           (bKey ? "</xs:key>" : "</xs:unique>") +
           "<xs:keyref name=\"kr\" refer=\"k\"><xs:selector xpath=\"item\"/><xs:field xpath=\"@ref\"/></xs:keyref>" +
           "<xs:unique name=\"uq\"><xs:selector xpath=\"item\"/><xs:field xpath=\"@u\"/></xs:unique>" +
           "</xs:element>" +
           XsdPropertyTestHelper.SCHEMA_END;
  }

  @NonNull
  private static IGenerator <Item> _items ()
  {
    return Generators.lists (Generators.ints (0, 6), 3, 3)
                     .map (aDraws -> new Item (IDS[aDraws.get (0).intValue () % IDS.length],
                                               REFS[aDraws.get (1).intValue () % REFS.length],
                                               UNIQUES[aDraws.get (2).intValue () % UNIQUES.length]));
  }

  @NonNull
  private static String _instance (@NonNull final List <Item> aItems)
  {
    final StringBuilder aSB = new StringBuilder ("<r>");
    for (final Item aItem : aItems)
    {
      aSB.append ("<item");
      if (aItem.id () != null)
        aSB.append (" id=\"").append (aItem.id ()).append ('"');
      if (aItem.ref () != null)
        aSB.append (" ref=\"").append (aItem.ref ()).append ('"');
      if (aItem.u () != null)
        aSB.append (" u=\"").append (aItem.u ()).append ('"');
      aSB.append ("/>");
    }
    return aSB.append ("</r>").toString ();
  }

  /**
   * @return The xs:int value of a lexical form (whitespace collapsed), <code>null</code> if it is
   *         no xs:int.
   */
  @Nullable
  private static BigInteger _int (@NonNull final String sLexical)
  {
    final String s = sLexical.strip ();
    if (!s.matches ("[+-]?[0-9]+"))
      return null;
    return new BigInteger (s.startsWith ("+") ? s.substring (1) : s);
  }

  /**
   * @return The expected verdict for the items.
   */
  private static boolean _isExpectedValid (@NonNull final List <Item> aItems, final boolean bKey)
  {
    final ICommonsSet <BigInteger> aIDs = new CommonsHashSet <> ();
    final ICommonsSet <String> aUniques = new CommonsHashSet <> ();
    for (final Item aItem : aItems)
    {
      if (aItem.id () == null)
      {
        if (bKey)
          return false;
      }
      else
      {
        final BigInteger aID = _int (aItem.id ());
        if (aID == null || !aIDs.add (aID))
          return false;
      }
      if (aItem.ref () != null && _int (aItem.ref ()) == null)
        return false;
      if (aItem.u () != null && !aUniques.add (aItem.u ()))
        return false;
    }
    for (final Item aItem : aItems)
      if (aItem.ref () != null && !aIDs.contains (_int (aItem.ref ())))
        return false;
    return true;
  }

  /**
   * Model property: the verdict of an instance with key (or unique), keyref and unique constraints
   * equals the Java computation of missing fields, duplicates and dangling references, under both
   * versions.
   */
  @Test
  public void testIdentityConstraints ()
  {
    final XsdSchema [] aKeySchemas = XsdPropertyTestHelper.compileBoth (_schema (true));
    final XsdSchema [] aUniqueSchemas = XsdPropertyTestHelper.compileBoth (_schema (false));
    assertNotNull (aKeySchemas);
    assertNotNull (aUniqueSchemas);
    new PropertyChecker ().setTries (500).forAll (Generators.booleans (), Generators.lists (_items (), 0, 6), (aKey, aItems) -> {
      final boolean bKey = aKey.booleanValue ();
      final XsdSchema [] aSchemas = bKey ? aKeySchemas : aUniqueSchemas;
      final String sInstance = _instance (aItems);
      final boolean bExpected = _isExpectedValid (aItems, bKey);
      assertEquals ((bKey ? "key " : "unique ") + sInstance + " (1.0)",
                    Boolean.valueOf (bExpected),
                    Boolean.valueOf (XsdPropertyTestHelper.isValidInstance (aSchemas[0], sInstance)));
      assertEquals ((bKey ? "key " : "unique ") + sInstance + " (1.1)",
                    Boolean.valueOf (bExpected),
                    Boolean.valueOf (XsdPropertyTestHelper.isValidInstance (aSchemas[1], sInstance)));
    });
  }

  /**
   * Model property for a key with two fields: every item needs both fields, and the pairs of
   * values (an xs:int and an xs:string) must be distinct.
   */
  @Test
  public void testCompositeKey ()
  {
    final String sSchema = XsdPropertyTestHelper.SCHEMA_START +
                           "<xs:element name=\"r\"><xs:complexType><xs:sequence>" +
                           "<xs:element name=\"item\" minOccurs=\"0\" maxOccurs=\"unbounded\"><xs:complexType>" +
                           "<xs:attribute name=\"id\" type=\"xs:int\"/><xs:attribute name=\"ref\" type=\"xs:int\"/>" +
                           "<xs:attribute name=\"u\" type=\"xs:string\"/></xs:complexType></xs:element>" +
                           "</xs:sequence></xs:complexType>" +
                           "<xs:key name=\"k\"><xs:selector xpath=\"item\"/><xs:field xpath=\"@id\"/><xs:field xpath=\"@u\"/></xs:key>" +
                           "</xs:element>" +
                           XsdPropertyTestHelper.SCHEMA_END;
    final XsdSchema [] aSchemas = XsdPropertyTestHelper.compileBoth (sSchema);
    assertNotNull (aSchemas);
    new PropertyChecker ().setTries (300).forAll (Generators.lists (_items (), 0, 6), aItems -> {
      final String sInstance = _instance (aItems);
      boolean bExpected = true;
      final ICommonsSet <String> aTuples = new CommonsHashSet <> ();
      for (final Item aItem : aItems)
      {
        if (aItem.id () == null || aItem.u () == null || (aItem.ref () != null && _int (aItem.ref ()) == null))
        {
          bExpected = false;
          break;
        }
        final BigInteger aID = _int (aItem.id ());
        if (aID == null || !aTuples.add (aID + "|" + aItem.u ()))
        {
          bExpected = false;
          break;
        }
      }
      assertEquals (sInstance + " (1.0)",
                    Boolean.valueOf (bExpected),
                    Boolean.valueOf (XsdPropertyTestHelper.isValidInstance (aSchemas[0], sInstance)));
      assertEquals (sInstance + " (1.1)",
                    Boolean.valueOf (bExpected),
                    Boolean.valueOf (XsdPropertyTestHelper.isValidInstance (aSchemas[1], sInstance)));
    });
  }
}

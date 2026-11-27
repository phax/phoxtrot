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
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the whiteSpace facet (XML Schema 1.1 Part 2, section 4.3.6) and for list
 * types (section 2.4.1.2 and 4.3.1 to 4.3.5 for lists): the expected verdicts are computed from a
 * Java implementation of the normalisation (<code>replace</code> turns tab, line feed and carriage
 * return into spaces, <code>collapse</code> also merges runs of spaces and strips leading and
 * trailing ones), the length facets count characters of the normalised value or items of a list,
 * the pattern facet applies to the normalised value, and the enumeration of a list type compares
 * the item values one by one.
 *
 * @author Philip Helger
 */
public final class XsdListWhiteSpacePropertyTest
{
  private static final String [] WHITE_SPACE = { "preserve", "replace", "collapse" };
  /** Patterns of the whiteSpace test, XSD and Java form (both match the whole value) */
  private static final String [] [] PATTERNS = { { "[ab ]*", "[ab ]*" },
                                                 { "\\S*", "[^ \\t\\n\\r]*" },
                                                 { "a.*", "a[^\\n\\r]*" },
                                                 { ".* .*", "[^\\n\\r]* [^\\n\\r]*" },
                                                 { "(\\s?[ab]+)*", "(?:[ \\t\\n\\r]?[ab]+)*" } };
  /** Items of the generated lists: valid xs:int forms and invalid ones */
  private static final String [] LIST_ITEMS = { "1",
                                                "-2",
                                                "007",
                                                "+3",
                                                "01",
                                                "0",
                                                "-0",
                                                "2147483647",
                                                "2147483648",
                                                "x",
                                                "1.5" };
  private static final String [] SEPARATORS = { " ", "  ", "\t", "\n", "\r\n", " \t " };
  private static final BigInteger INT_MIN = BigInteger.valueOf (Integer.MIN_VALUE);
  private static final BigInteger INT_MAX = BigInteger.valueOf (Integer.MAX_VALUE);
  private static final Pattern INTEGER = Pattern.compile ("[+-]?[0-9]+");

  /**
   * @return The value after the whiteSpace normalisation.
   */
  @NonNull
  private static String _normalise (@NonNull final String sValue, @NonNull final String sWhiteSpace)
  {
    if (sWhiteSpace.equals ("preserve"))
      return sValue;
    final String sReplaced = sValue.replace ('\t', ' ').replace ('\n', ' ').replace ('\r', ' ');
    if (sWhiteSpace.equals ("replace"))
      return sReplaced;
    final StringBuilder aSB = new StringBuilder ();
    for (final String sPart : sReplaced.split (" "))
      if (!sPart.isEmpty ())
      {
        if (!aSB.isEmpty ())
          aSB.append (' ');
        aSB.append (sPart);
      }
    return aSB.toString ();
  }

  /**
   * Model property: a restriction of xs:string with a whiteSpace facet, a length or minLength and
   * maxLength facet and optionally a pattern accepts a value exactly when its normalised form
   * satisfies the length and the pattern.
   */
  @Test
  public void testWhiteSpaceFacet ()
  {
    final IGenerator <String> aValues = Generators.strings ("ab \t\n\r", 0, 8);
    new PropertyChecker ().setTries (300)
                          .forAll (Generators.ints (0, WHITE_SPACE.length - 1),
                                   Generators.lists (Generators.ints (0, 6), 3, 3),
                                   Generators.ints (-1, PATTERNS.length - 1),
                                   Generators.lists (aValues, 1, 8),
                                   (aWhiteSpace, aLengths, aPattern, aList) -> {
                                     final String sWhiteSpace = WHITE_SPACE[aWhiteSpace.intValue ()];
                                     final int nVariant = aLengths.get (0).intValue () % 2;
                                     final int nA = aLengths.get (1).intValue ();
                                     final int nB = Math.max (nA, aLengths.get (2).intValue ());
                                     final int nPattern = aPattern.intValue ();
                                     final StringBuilder aFacets = new StringBuilder ("<xs:whiteSpace value=\"").append (sWhiteSpace)
                                                                                                                .append ("\"/>");
                                     if (nVariant == 0)
                                       aFacets.append ("<xs:length value=\"").append (nA).append ("\"/>");
                                     else
                                       aFacets.append ("<xs:minLength value=\"")
                                              .append (nA)
                                              .append ("\"/><xs:maxLength value=\"")
                                              .append (nB)
                                              .append ("\"/>");
                                     Pattern aJavaPattern = null;
                                     if (nPattern >= 0)
                                     {
                                       aFacets.append ("<xs:pattern value=\"")
                                              .append (PATTERNS[nPattern][0])
                                              .append ("\"/>");
                                       aJavaPattern = Pattern.compile (PATTERNS[nPattern][1]);
                                     }
                                     for (final EXsdVersion eVersion : EXsdVersion.values ())
                                     {
                                       final XsdSimpleType aType = XsdPropertyTestHelper.compileRestriction ("string",
                                                                                                             aFacets.toString (),
                                                                                                             eVersion);
                                       assertNotNull (aFacets.toString (), aType);
                                       for (final String sValue : aList)
                                       {
                                         final String sNormalised = _normalise (sValue, sWhiteSpace);
                                         final int nLength = sNormalised.length ();
                                         final boolean bLength = nVariant == 0 ? nLength == nA
                                                                               : nLength >= nA && nLength <= nB;
                                         final boolean bExpected = bLength &&
                                                                   (aJavaPattern == null ||
                                                                    aJavaPattern.matcher (sNormalised).matches ());
                                         assertEquals (aFacets +
                                                       " '" +
                                                       sValue.replace ("\n", "\\n")
                                                             .replace ("\r", "\\r")
                                                             .replace ("\t", "\\t") +
                                                       "' (" +
                                                       eVersion.getID () +
                                                       ")",
                                                       Boolean.valueOf (bExpected),
                                                       Boolean.valueOf (XsdPropertyTestHelper.isValidValue (aType,
                                                                                                            sValue,
                                                                                                            eVersion)));
                                       }
                                     }
                                   });
  }

  /**
   * @return The items of a list lexical after collapsing the whitespace.
   */
  @NonNull
  private static ICommonsList <String> _items (@NonNull final String sValue)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final String sItem : sValue.split ("[ \\t\\n\\r]+"))
      if (!sItem.isEmpty ())
        ret.add (sItem);
    return ret;
  }

  /**
   * @return The xs:int value of an item, <code>null</code> if it is no valid xs:int.
   */
  @Nullable
  private static BigInteger _intValue (@NonNull final String sItem)
  {
    if (!INTEGER.matcher (sItem).matches ())
      return null;
    final BigInteger ret = new BigInteger (sItem.startsWith ("+") ? sItem.substring (1) : sItem);
    return ret.compareTo (INT_MIN) >= 0 && ret.compareTo (INT_MAX) <= 0 ? ret : null;
  }

  /**
   * @return The values of the items of a list lexical, <code>null</code> if an item is no valid
   *         xs:int.
   */
  @Nullable
  private static ICommonsList <BigInteger> _listValue (@NonNull final String sValue)
  {
    final ICommonsList <BigInteger> ret = new CommonsArrayList <> ();
    for (final String sItem : _items (sValue))
    {
      final BigInteger aItem = _intValue (sItem);
      if (aItem == null)
        return null;
      ret.add (aItem);
    }
    return ret;
  }

  /**
   * @return A generator of list lexicals: items joined by whitespace, with optional leading and
   *         trailing whitespace.
   */
  @NonNull
  private static IGenerator <String> _lists ()
  {
    return Generators.lists (Generators.ints (0, LIST_ITEMS.length - 1), 0, 4)
                     .flatMap (aItems -> Generators.lists (Generators.ints (0, SEPARATORS.length - 1),
                                                           aItems.size () + 1,
                                                           aItems.size () + 1)
                                                   .flatMap (aSeparators -> Generators.booleans ().map (aEdges -> {
                                                     final StringBuilder aSB = new StringBuilder ();
                                                     if (aEdges.booleanValue ())
                                                       aSB.append (SEPARATORS[aSeparators.get (0).intValue ()]);
                                                     for (int i = 0; i < aItems.size (); ++i)
                                                     {
                                                       if (i > 0)
                                                         aSB.append (SEPARATORS[aSeparators.get (i).intValue ()]);
                                                       aSB.append (LIST_ITEMS[aItems.get (i).intValue ()]);
                                                     }
                                                     if (aEdges.booleanValue ())
                                                       aSB.append (SEPARATORS[aSeparators.get (aItems.size ())
                                                                                         .intValue ()]);
                                                     return aSB.toString ();
                                                   })));
  }

  /**
   * Model property for a list of xs:int restricted by length facets or an enumeration: a lexical is
   * accepted exactly when every whitespace separated item is an xs:int and the number of items
   * satisfies the length facets, or the item values equal those of an enumerated list one by one.
   * An enumeration value that is no valid list is a schema error.
   */
  @Test
  public void testListFacets ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (Generators.ints (0, 2),
                                   Generators.lists (Generators.ints (0, 4), 2, 2),
                                   Generators.lists (_lists (), 1, 2),
                                   Generators.lists (_lists (), 1, 8),
                                   (aVariant, aLengths, aEnums, aValues) -> {
                                     final int nVariant = aVariant.intValue ();
                                     final int nA = aLengths.get (0).intValue ();
                                     final int nB = Math.max (nA, aLengths.get (1).intValue ());
                                     final StringBuilder aFacets = new StringBuilder ();
                                     final ICommonsList <ICommonsList <BigInteger>> aEnumValues = new CommonsArrayList <> ();
                                     boolean bEnumValid = true;
                                     switch (nVariant)
                                     {
                                       case 0 -> aFacets.append ("<xs:length value=\"").append (nA).append ("\"/>");
                                       case 1 -> aFacets.append ("<xs:minLength value=\"")
                                                        .append (nA)
                                                        .append ("\"/><xs:maxLength value=\"")
                                                        .append (nB)
                                                        .append ("\"/>");
                                       default ->
                                       {
                                         for (final String sEnum : aEnums)
                                         {
                                           aFacets.append ("<xs:enumeration value=\"")
                                                  .append (XsdPropertyTestHelper.escape (sEnum))
                                                  .append ("\"/>");
                                           final ICommonsList <BigInteger> aEnumValue = _listValue (sEnum);
                                           if (aEnumValue == null)
                                             bEnumValid = false;
                                           else
                                             aEnumValues.add (aEnumValue);
                                         }
                                       }
                                     }
                                     final String sSchema = XsdPropertyTestHelper.SCHEMA_START +
                                                            "<xs:simpleType name=\"l\"><xs:list itemType=\"xs:int\"/></xs:simpleType>" +
                                                            "<xs:simpleType name=\"t\"><xs:restriction base=\"l\">" +
                                                            aFacets +
                                                            "</xs:restriction></xs:simpleType>" +
                                                            XsdPropertyTestHelper.SCHEMA_END;
                                     final XsdSchema [] aSchemas = XsdPropertyTestHelper.compileBoth (sSchema);
                                     assertEquals (sSchema,
                                                   Boolean.valueOf (bEnumValid),
                                                   Boolean.valueOf (aSchemas != null));
                                     if (aSchemas != null)
                                       for (final String sValue : aValues)
                                       {
                                         final ICommonsList <BigInteger> aValue = _listValue (sValue);
                                         final boolean bExpected;
                                         if (aValue == null)
                                           bExpected = false;
                                         else
                                           bExpected = switch (nVariant)
                                           {
                                             case 0 -> aValue.size () == nA;
                                             case 1 -> aValue.size () >= nA && aValue.size () <= nB;
                                             default -> aEnumValues.contains (aValue);
                                           };
                                         for (int i = 0; i < 2; ++i)
                                         {
                                           final EXsdVersion eVersion = i == 0 ? EXsdVersion.V10 : EXsdVersion.V11;
                                           assertEquals (aFacets +
                                                         " '" +
                                                         sValue.replace ("\n", "\\n")
                                                               .replace ("\r", "\\r")
                                                               .replace ("\t", "\\t") +
                                                         "' (" +
                                                         eVersion.getID () +
                                                         ")",
                                                         Boolean.valueOf (bExpected),
                                                         Boolean.valueOf (XsdPropertyTestHelper.isValidValue (XsdPropertyTestHelper.getType (aSchemas[i]),
                                                                                                              sValue,
                                                                                                              eVersion)));
                                         }
                                       }
                                   });
  }

  /**
   * Model property for a list of a restricted item type: the item type's facets apply to every
   * item, the list's length facet to the number of items. The item type is xs:int with maxInclusive
   * 5.
   */
  @Test
  public void testListOfRestrictedItems ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (Generators.ints (0, 4), Generators.lists (_lists (), 1, 8), (aLength, aValues) -> {
                            final int nLength = aLength.intValue ();
                            final String sSchema = XsdPropertyTestHelper.SCHEMA_START +
                                                   "<xs:simpleType name=\"i\"><xs:restriction base=\"xs:int\">" +
                                                   "<xs:maxInclusive value=\"5\"/></xs:restriction></xs:simpleType>" +
                                                   "<xs:simpleType name=\"t\"><xs:restriction><xs:simpleType>" +
                                                   "<xs:list itemType=\"i\"/></xs:simpleType>" +
                                                   "<xs:maxLength value=\"" +
                                                   nLength +
                                                   "\"/></xs:restriction></xs:simpleType>" +
                                                   XsdPropertyTestHelper.SCHEMA_END;
                            final XsdSchema [] aSchemas = XsdPropertyTestHelper.compileBoth (sSchema);
                            assertNotNull (sSchema, aSchemas);
                            for (final String sValue : aValues)
                            {
                              final ICommonsList <BigInteger> aValue = _listValue (sValue);
                              final boolean bExpected = aValue != null &&
                                                        aValue.size () <= nLength &&
                                                        aValue.findFirst (x -> x.compareTo (BigInteger.valueOf (5)) > 0) == null;
                              for (int i = 0; i < 2; ++i)
                              {
                                final EXsdVersion eVersion = i == 0 ? EXsdVersion.V10 : EXsdVersion.V11;
                                assertEquals ("maxLength " +
                                              nLength +
                                              " '" +
                                              sValue.replace ("\n", "\\n").replace ("\r", "\\r").replace ("\t", "\\t") +
                                              "' (" +
                                              eVersion.getID () +
                                              ")",
                                              Boolean.valueOf (bExpected),
                                              Boolean.valueOf (XsdPropertyTestHelper.isValidValue (XsdPropertyTestHelper.getType (aSchemas[i]),
                                                                                                   sValue,
                                                                                                   eVersion)));
                              }
                            }
                          });
  }
}

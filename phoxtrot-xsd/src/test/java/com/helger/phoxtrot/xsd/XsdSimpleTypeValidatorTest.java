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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xsd.XsdTestSupport.Compiled;
import com.helger.phoxtrot.xsd.XsdTestSupport.EVerdict;

/**
 * The value checks of simple types, beyond the table of {@link XsdFacetTest}:
 * the primitive lexical spaces, the value-space equality of fixed values and
 * the bounded walks over cyclic unions (go-xslt issue 12).
 *
 * @author Philip Helger
 */
public final class XsdSimpleTypeValidatorTest
{
  private static final String XS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">";

  @NonNull
  private static XsdSimpleType _type (@NonNull final String sBody, @NonNull final EXsdVersion eVersion)
  {
    final Compiled aCompiled = XsdTestSupport.compile (XS + sBody + "</xs:schema>", null, eVersion);
    assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    return (XsdSimpleType) aCompiled.schema ().m_aTypes.get (new XsdName ("", "t"));
  }

  private static boolean _valid (@NonNull final XsdSimpleType aType,
                                 @NonNull final String sValue,
                                 @NonNull final EXsdVersion eVersion)
  {
    try
    {
      XsdSimpleTypeValidator.validate (aType, sValue, eVersion);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  private static boolean _lexical (@NonNull final EAtomicType ePrim,
                                   @NonNull final String sValue,
                                   @NonNull final EXsdVersion eVersion)
  {
    try
    {
      XsdSimpleTypeValidator.checkPrimitiveLexical (ePrim, sValue, eVersion);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  @Test
  public void testPrimitiveLexical ()
  {
    assertTrue (_lexical (EAtomicType.INTEGER, "+12", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.INTEGER, "1.5", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.INTEGER, "123456789012345678901234567890", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.BYTE, "127", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.BYTE, "128", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.UNSIGNED_BYTE, "-1", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.UNSIGNED_LONG, "18446744073709551615", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.UNSIGNED_LONG, "18446744073709551616", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.POSITIVE_INTEGER, "0", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.NEGATIVE_INTEGER, "-1", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.DECIMAL, ".5", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.DECIMAL, "5.", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.DECIMAL, "1/2", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.DECIMAL, ".", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.DOUBLE, "1e400", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.DOUBLE, "-INF", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.FLOAT, "NaN", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.DOUBLE, "1e", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.DATE, "2024-02-29", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.DATE, "2023-02-29", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.BOOLEAN, "1", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.BOOLEAN, "yes", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.HEX_BINARY, "0aFF", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.HEX_BINARY, "0aF", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.BASE64_BINARY, "AQ==", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.BASE64_BINARY, "AQ=", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.NCNAME, "a.b-c", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.NCNAME, "a:b", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.NAME, "a:b", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.NMTOKEN, "1:2", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.LANGUAGE, "en-GB", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.LANGUAGE, "toolonglanguage", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.QNAME, "p:a", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.QNAME, "a:b:c", EXsdVersion.V11));
    assertTrue (_lexical (EAtomicType.STRING, "anything\u0001goes", EXsdVersion.V11));
  }

  @Test
  public void testFixedEqual ()
  {
    final XsdSimpleType aDecimal = _type ("<xs:simpleType name=\"t\"><xs:restriction base=\"xs:decimal\"/></xs:simpleType>",
                                          EXsdVersion.V11);
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aDecimal, "2", " 2.0 ", EXsdVersion.V11));
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aDecimal, "2", "3", EXsdVersion.V11));
    final XsdSimpleType aString = _type ("<xs:simpleType name=\"t\"><xs:restriction base=\"xs:string\"/></xs:simpleType>",
                                         EXsdVersion.V11);
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aString, "   1  2", "   1  2", EXsdVersion.V11));
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aString, "   1  2", " 1 2", EXsdVersion.V11));
    final XsdSimpleType aList = _type ("<xs:simpleType name=\"t\"><xs:list itemType=\"xs:int\"/></xs:simpleType>", EXsdVersion.V11);
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aList, "1 02", " 01   2 ", EXsdVersion.V11));
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aList, "1 2", "1 2 3", EXsdVersion.V11));
    final XsdSimpleType aUnion = _type ("<xs:simpleType name=\"t\"><xs:union memberTypes=\"xs:int xs:boolean\"/></xs:simpleType>",
                                        EXsdVersion.V11);
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aUnion, "1", "01", EXsdVersion.V11));
    // 1 is an int, true a boolean: different members
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aUnion, "1", "true", EXsdVersion.V11));
    assertNotNull (XsdSimpleTypeValidator.firstMemberRaw (aUnion, "true", EXsdVersion.V11));
    assertNull (XsdSimpleTypeValidator.firstMemberRaw (aUnion, "x", EXsdVersion.V11));
  }

  @Test
  public void testUnionRestrictionChain ()
  {
    // A restriction of a restricted union keeps the facets of every step of
    // its base chain (an enumeration value must be in the value space of the
    // base, so the enumeration is on the base and the pattern on the type)
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final XsdSimpleType aType = _type ("<xs:simpleType name='u0'><xs:union memberTypes='xs:int xs:boolean'/></xs:simpleType>" +
                                         "<xs:simpleType name='u1'><xs:restriction base='u0'>" +
                                         "<xs:enumeration value='true'/><xs:enumeration value='12'/>" +
                                         "</xs:restriction></xs:simpleType>" +
                                         "<xs:simpleType name='t'><xs:restriction base='u1'><xs:pattern value='[0-9]+'/></xs:restriction></xs:simpleType>",
                                         eVersion);
      assertTrue (_valid (aType, "12", EXsdVersion.V11));
      assertFalse (_valid (aType, "true", EXsdVersion.V11));
      assertFalse (_valid (aType, "13", EXsdVersion.V11));
    }
    // An enumeration value outside the value space of the base union is a
    // schema error (Datatypes 4.3.5.5)
    for (final EXsdVersion eVersion : EXsdVersion.values ())
      assertEquals (EVerdict.INVALID,
                    XsdTestSupport.compile (XS +
                                            "<xs:simpleType name='u0'><xs:union memberTypes='xs:int xs:boolean'/></xs:simpleType>" +
                                            "<xs:simpleType name='u1'><xs:restriction base='u0'><xs:pattern value='[0-9]+'/></xs:restriction></xs:simpleType>" +
                                            "<xs:simpleType name='t'><xs:restriction base='u1'><xs:enumeration value='true'/></xs:restriction></xs:simpleType>" +
                                            "</xs:schema>",
                                            null,
                                            eVersion)
                                  .verdict ());
  }

  @Test
  public void testCyclicUnion ()
  {
    // XSD 1.0 tolerates a pure-union cycle (stE110); the member walks end
    final XsdSimpleType aSelf = _type ("<xs:simpleType name=\"t\"><xs:union memberTypes=\"t xs:int\"/></xs:simpleType>",
                                       EXsdVersion.V10);
    assertTrue (_valid (aSelf, "12", EXsdVersion.V10));
    assertFalse (_valid (aSelf, "x", EXsdVersion.V10));
    assertNotNull (XsdSimpleTypeValidator.firstMemberRaw (aSelf, "12", EXsdVersion.V10));
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aSelf, "12", "012", EXsdVersion.V10));
    final XsdSimpleType aPair = _type ("<xs:simpleType name=\"t\"><xs:union memberTypes=\"u xs:int\"/></xs:simpleType>" +
                                       "<xs:simpleType name=\"u\"><xs:union memberTypes=\"t xs:boolean\"/></xs:simpleType>",
                                       EXsdVersion.V10);
    assertTrue (_valid (aPair, "true", EXsdVersion.V10));
    assertTrue (_valid (aPair, "7", EXsdVersion.V10));
    assertFalse (_valid (aPair, "x", EXsdVersion.V10));
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aPair, "7", "07", EXsdVersion.V10));
    // The cycle collapses to the members that do not lead back, in member
    // order (boolean, int), so "1" is the boolean true like "true"
    assertTrue (XsdSimpleTypeValidator.fixedEqual (aPair, "true", "1", EXsdVersion.V10));
    assertFalse (XsdSimpleTypeValidator.fixedEqual (aPair, "true", "2", EXsdVersion.V10));
    // Many members that all lead back: linear, not exponential
    final StringBuilder aSB = new StringBuilder ("<xs:simpleType name=\"t\"><xs:union memberTypes=\"");
    for (int i = 0; i < 40; i++)
      aSB.append ("t ");
    aSB.append ("xs:int\"/></xs:simpleType>");
    final XsdSimpleType aWide = _type (aSB.toString (), EXsdVersion.V10);
    assertTrue (_valid (aWide, "5", EXsdVersion.V10));
    assertFalse (_valid (aWide, "x", EXsdVersion.V10));
  }

  @Test
  public void testQNameBinding ()
  {
    final XsdSimpleType aQName = _type ("<xs:simpleType name=\"t\"><xs:restriction base=\"xs:QName\"/></xs:simpleType>",
                                        EXsdVersion.V11);
    // Without a node only the shape is checked
    assertTrue (_valid (aQName, "p:a", EXsdVersion.V11));
    assertFalse (_valid (aQName, "a:b:c", EXsdVersion.V11));
  }

  @NonNull
  private static EVerdict _schemaVerdict (@NonNull final String sBody, @NonNull final EXsdVersion eVersion)
  {
    return XsdTestSupport.compile (XS + sBody + "</xs:schema>", null, eVersion).verdict ();
  }

  private static boolean _instanceValid (@NonNull final String sElementType,
                                         @NonNull final String sInstance,
                                         @NonNull final EXsdVersion eVersion)
  {
    final Compiled aCompiled = XsdTestSupport.compile (XS + "<xs:element name=\"e\">" + sElementType + "</xs:element></xs:schema>",
                                                       null,
                                                       eVersion);
    assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    return aCompiled.schema ().validateText (sInstance).valid ();
  }

  /**
   * XSD 1.0 has no <code>+INF</code> for xs:float and xs:double and no year 0000 in the date
   * family. The rule applies wherever an atomic value is checked: list items, union members,
   * facet values and value constraints, not only the top-level type of an element or attribute.
   */
  @Test
  public void testXsd10LexicalRules ()
  {
    for (final EAtomicType ePrim : new EAtomicType [] { EAtomicType.DOUBLE, EAtomicType.FLOAT })
    {
      assertFalse (_lexical (ePrim, "+INF", EXsdVersion.V10));
      assertTrue (_lexical (ePrim, "+INF", EXsdVersion.V11));
      assertTrue (_lexical (ePrim, "INF", EXsdVersion.V10));
      assertTrue (_lexical (ePrim, "-INF", EXsdVersion.V10));
    }
    assertFalse (_lexical (EAtomicType.DATE, "0000-01-01", EXsdVersion.V10));
    assertTrue (_lexical (EAtomicType.DATE, "0000-01-01", EXsdVersion.V11));
    assertFalse (_lexical (EAtomicType.DATE_TIME, "-0000-01-01T00:00:00", EXsdVersion.V10));
    assertFalse (_lexical (EAtomicType.G_YEAR, "0000", EXsdVersion.V10));
    assertFalse (_lexical (EAtomicType.G_YEAR_MONTH, "0000-12", EXsdVersion.V10));
    assertTrue (_lexical (EAtomicType.G_YEAR, "0001", EXsdVersion.V10));

    final String sList = "<xs:simpleType><xs:list itemType=\"xs:double\"/></xs:simpleType>";
    final String sUnion = "<xs:simpleType><xs:union memberTypes=\"xs:double xs:boolean\"/></xs:simpleType>";
    final String sDateList = "<xs:simpleType><xs:list itemType=\"xs:date\"/></xs:simpleType>";
    final String sYearUnion = "<xs:simpleType><xs:union memberTypes=\"xs:gYear xs:boolean\"/></xs:simpleType>";
    assertFalse (_instanceValid (sList, "<e>1 +INF</e>", EXsdVersion.V10));
    assertTrue (_instanceValid (sList, "<e>1 +INF</e>", EXsdVersion.V11));
    assertFalse (_instanceValid (sUnion, "<e>+INF</e>", EXsdVersion.V10));
    assertTrue (_instanceValid (sUnion, "<e>+INF</e>", EXsdVersion.V11));
    assertFalse (_instanceValid (sDateList, "<e>0000-01-01</e>", EXsdVersion.V10));
    assertTrue (_instanceValid (sDateList, "<e>0000-01-01</e>", EXsdVersion.V11));
    assertFalse (_instanceValid (sYearUnion, "<e>0000</e>", EXsdVersion.V10));
    assertTrue (_instanceValid (sYearUnion, "<e>0000</e>", EXsdVersion.V11));

    for (final String sBody : new String [] { "<xs:simpleType name=\"t\"><xs:restriction base=\"xs:double\"><xs:enumeration value=\"+INF\"/></xs:restriction></xs:simpleType>",
                                              "<xs:simpleType name=\"t\"><xs:restriction base=\"xs:double\"><xs:maxInclusive value=\"+INF\"/></xs:restriction></xs:simpleType>",
                                              "<xs:element name=\"e\" type=\"xs:double\" default=\"+INF\"/>",
                                              "<xs:simpleType name=\"t\"><xs:restriction base=\"xs:date\"><xs:enumeration value=\"0000-01-01\"/></xs:restriction></xs:simpleType>" })
    {
      assertEquals (sBody, EVerdict.INVALID, _schemaVerdict (sBody, EXsdVersion.V10));
      assertEquals (sBody, EVerdict.VALID, _schemaVerdict (sBody, EXsdVersion.V11));
    }
  }
}

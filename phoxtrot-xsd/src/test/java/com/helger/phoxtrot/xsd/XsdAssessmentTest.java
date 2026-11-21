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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xpath.context.EXsdVersion;

/**
 * Test class for {@link XsdSchema#assessText(String, XsdValidationOptions)}.
 *
 * @author Philip Helger
 */
public final class XsdAssessmentTest
{
  private static final String NS = "urn:test";
  private static final String SCHEMA = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:t='" +
                                       NS +
                                       "' targetNamespace='" +
                                       NS +
                                       "' elementFormDefault='qualified'>" +
                                       "<xs:simpleType name='Amount'><xs:restriction base='xs:decimal'>" +
                                       "<xs:minInclusive value='0'/><xs:fractionDigits value='2'/>" +
                                       "</xs:restriction></xs:simpleType>" +
                                       "<xs:complexType name='Base'><xs:simpleContent><xs:extension base='xs:string'/>" +
                                       "</xs:simpleContent></xs:complexType>" +
                                       "<xs:complexType name='Derived'><xs:simpleContent><xs:restriction base='t:Base'>" +
                                       "<xs:maxLength value='3'/></xs:restriction></xs:simpleContent></xs:complexType>" +
                                       "<xs:element name='doc'><xs:complexType><xs:sequence>" +
                                       "<xs:element name='amount' type='t:Amount'/>" +
                                       "<xs:element name='item'><xs:complexType>" +
                                       "<xs:attribute name='qty' type='xs:positiveInteger'/>" +
                                       "</xs:complexType></xs:element>" +
                                       "<xs:element name='val' type='t:Base'/>" +
                                       "</xs:sequence></xs:complexType></xs:element>" +
                                       "</xs:schema>";

  @NonNull
  private static XsdSchema _schema ()
  {
    return XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (SCHEMA, null)), EXsdVersion.V10, null);
  }

  @NonNull
  private static ICommonsList <Integer> _path (final int... aIndices)
  {
    final ICommonsList <Integer> ret = new CommonsArrayList <> ();
    for (final int n : aIndices)
      ret.add (Integer.valueOf (n));
    return ret;
  }

  @Test
  public void testValid ()
  {
    final String sInstance = "<doc xmlns='" +
                             NS +
                             "' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n" +
                             "  <!-- text, comments and PIs do not count -->\n" +
                             "  <amount>12.50</amount><?pi x?>\n" +
                             "  <item qty='3'/>\n" +
                             "  <val xsi:type='Derived'>abc</val>\n" +
                             "</doc>";
    final XsdAssessment aAssessment = _schema ().assessText (sInstance, new XsdValidationOptions ());
    assertTrue (aAssessment.result ().errors ().toString (), aAssessment.result ().valid ());

    final ICommonsList <XsdNodeType> aTypes = aAssessment.nodeTypes ();
    assertEquals (aTypes.toString (), 5, aTypes.size ());

    // The document element with its anonymous type
    assertEquals (_path (0), aTypes.get (0).elementPath ());
    assertFalse (aTypes.get (0).isAttribute ());
    assertFalse (aTypes.get (0).type ().isSimple ());

    // The facets of the named simple type
    final XsdNodeType aAmount = aTypes.get (1);
    assertEquals (_path (0, 0), aAmount.elementPath ());
    final XsdSimpleTypeView aAmountType = (XsdSimpleTypeView) aAmount.type ();
    assertEquals (new XsdName (NS, "Amount"), aAmountType.getName ());
    assertEquals ("0", aAmountType.getFacets ().minInclusive ());
    assertEquals (Integer.valueOf (2), aAmountType.getFacets ().fractionDigits ());

    // An attribute follows its element
    assertEquals (_path (0, 1), aTypes.get (2).elementPath ());
    assertNull (aTypes.get (2).attribute ());
    final XsdNodeType aQty = aTypes.get (3);
    assertEquals (_path (0, 1), aQty.elementPath ());
    assertEquals (new XsdName ("", "qty"), aQty.attribute ());
    assertEquals (new XsdName (XsdConstants.NS_XS, "positiveInteger"), ((XsdSimpleTypeView) aQty.type ()).getName ());

    // xsi:type is resolved: the derived type governs, not the declared one
    final XsdNodeType aVal = aTypes.get (4);
    assertEquals (_path (0, 2), aVal.elementPath ());
    assertEquals (new XsdName (NS, "Derived"), ((XsdComplexTypeView) aVal.type ()).getName ());
  }

  @Test
  public void testInvalid ()
  {
    // The amount is invalid, the validation continues and still reports the following nodes
    final XsdAssessment aAssessment = _schema ().assessText ("<doc xmlns='" +
                                                             NS +
                                                             "'><amount>-1</amount><item qty='3'/><val>x</val></doc>",
                                                             new XsdValidationOptions ());
    assertFalse (aAssessment.result ().valid ());
    assertEquals (1, aAssessment.result ().errors ().size ());
    assertEquals (5, aAssessment.nodeTypes ().size ());
    assertEquals (new XsdName (NS, "Base"), ((XsdComplexTypeView) aAssessment.nodeTypes ().get (4).type ()).getName ());

    // Not well-formed: no types
    final XsdAssessment aBroken = _schema ().assessText ("<doc", new XsdValidationOptions ());
    assertFalse (aBroken.result ().valid ());
    assertTrue (aBroken.nodeTypes ().isEmpty ());
  }
}

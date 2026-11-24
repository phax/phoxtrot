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
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the public entry points of {@link XsdSchema}:
 * robustness of {@link XsdSchema#compile} and
 * {@link XsdSchema#validateText(String, XsdValidationOptions)} against mutated
 * schema and instance documents (only the documented outcomes, never a runtime
 * exception of the implementation), and the stability of the node positions of
 * {@link XsdSchema#assessText(String, XsdValidationOptions)}, which XMLPerm
 * relies on.
 *
 * @author Philip Helger
 */
public final class XsdSchemaPropertyTest
{
  private static final String ORDER_SCHEMA = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                                             "<xs:element name=\"order\"><xs:complexType><xs:sequence>" +
                                             "<xs:element name=\"id\" type=\"code\"/>" +
                                             "<xs:element name=\"line\" maxOccurs=\"unbounded\"><xs:complexType>" +
                                             "<xs:simpleContent><xs:extension base=\"xs:decimal\">" +
                                             "<xs:attribute name=\"qty\" type=\"xs:positiveInteger\" use=\"required\"/>" +
                                             "</xs:extension></xs:simpleContent></xs:complexType></xs:element>" +
                                             "<xs:choice minOccurs=\"0\"><xs:element name=\"note\" type=\"xs:string\"/>" +
                                             "<xs:element name=\"tags\" type=\"tagList\"/></xs:choice>" +
                                             "<xs:any namespace=\"##other\" processContents=\"lax\" minOccurs=\"0\"/>" +
                                             "</xs:sequence>" +
                                             "<xs:attribute name=\"date\" type=\"xs:date\"/>" +
                                             "<xs:attribute name=\"kind\" type=\"kind\" default=\"std\"/>" +
                                             "</xs:complexType>" +
                                             "<xs:key name=\"k\"><xs:selector xpath=\"line\"/><xs:field xpath=\"@qty\"/></xs:key>" +
                                             "</xs:element>" +
                                             "<xs:simpleType name=\"code\"><xs:restriction base=\"xs:token\">" +
                                             "<xs:pattern value=\"[A-Z]{2}-\\d{3}\"/></xs:restriction></xs:simpleType>" +
                                             "<xs:simpleType name=\"tagList\"><xs:list itemType=\"xs:NCName\"/></xs:simpleType>" +
                                             "<xs:simpleType name=\"kind\"><xs:union memberTypes=\"xs:int\"><xs:simpleType>" +
                                             "<xs:restriction base=\"xs:string\"><xs:enumeration value=\"std\"/>" +
                                             "<xs:enumeration value=\"exp\"/></xs:restriction></xs:simpleType></xs:union>" +
                                             "</xs:simpleType>" +
                                             "</xs:schema>";

  /** An XSD 1.1 schema with an assertion, a type alternative and open content. */
  private static final String ASSERT_SCHEMA = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                                              "<xs:element name=\"order\"><xs:complexType>" +
                                              "<xs:openContent mode=\"interleave\"><xs:any namespace=\"##other\" processContents=\"skip\"/></xs:openContent>" +
                                              "<xs:sequence><xs:element name=\"line\" type=\"xs:int\" maxOccurs=\"3\"/>" +
                                              "<xs:element name=\"id\" minOccurs=\"0\" type=\"xs:string\">" +
                                              "<xs:alternative test=\"@kind = 'n'\" type=\"xs:int\"/></xs:element>" +
                                              "</xs:sequence><xs:attribute name=\"max\" type=\"xs:int\"/>" +
                                              "<xs:assert test=\"every $l in line satisfies $l le (@max, 100)[1]\"/>" +
                                              "</xs:complexType></xs:element></xs:schema>";

  private static final String [] ORDER_INSTANCES = { "<order date=\"2024-02-29\" kind=\"exp\"><id>AB-123</id>" +
                                                     "<line qty=\"2\">1.50</line><line qty=\"3\">7</line>" +
                                                     "<note>Hello</note></order>",
                                                     "<order kind=\"42\"><!-- c --><id> XY-999 </id>" +
                                                     "<line qty=\"1\">-0.5</line><tags>a b c</tags>" +
                                                     "<x:foo xmlns:x=\"urn:x\" a=\"1\"><x:bar/></x:foo></order>",
                                                     "<order><id>AB-12</id><line qty=\"0\">x</line><line qty=\"0\">1</line></order>" };

  /**
   * The schema of {@link #testAssessmentPositionsReferToNodes()}: recursive mixed elements with an
   * optional xs:int attribute.
   */
  private static final String ITEM_SCHEMA = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                                            "<xs:element name=\"item\" type=\"T\"/>" +
                                            "<xs:complexType name=\"T\" mixed=\"true\"><xs:sequence>" +
                                            "<xs:element ref=\"item\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>" +
                                            "</xs:sequence><xs:attribute name=\"n\" type=\"xs:int\"/></xs:complexType>" +
                                            "</xs:schema>";

  /** Bytes that are meaningful in XML markup, for mutations that keep the document text-like. */
  private static final byte [] XML_BYTES = "<>/=\"' :&;!-?[]axn01#".getBytes (StandardCharsets.US_ASCII);

  /**
   * A generated instance of {@link #ITEM_SCHEMA} with the positions it must report.
   *
   * @param xml
   *        The instance text.
   * @param expected
   *        One entry per element (its position path) and per attribute (the path plus
   *        <code>@n</code>), in document order.
   * @param valid
   *        Whether every attribute value is an xs:int.
   */
  private record ItemInstance (@NonNull String xml, @NonNull ICommonsList <String> expected, boolean valid)
  {}

  /**
   * An element of an {@link ItemInstance} while it is built.
   */
  private static final class OpenElement
  {
    private final ICommonsList <Integer> m_aPath;
    private int m_nChildElements;
    private boolean m_bStartTagOpen = true;
    private boolean m_bHasAttribute;

    OpenElement (@NonNull final ICommonsList <Integer> aPath)
    {
      m_aPath = aPath;
    }
  }

  @NonNull
  private static XsdSchema _compile (@NonNull final String sSchema, @NonNull final EXsdVersion eVersion)
  {
    return XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sSchema, null)),
                              eVersion,
                              new XsdCompileOptions ().setFileAccess (XsdFileAccess.NONE));
  }

  @NonNull
  private static IGenerator <String> _mutated (@NonNull final String... aSeeds)
  {
    final byte [] [] aSeedBytes = new byte [aSeeds.length] [];
    for (int i = 0; i < aSeeds.length; i++)
      aSeedBytes[i] = aSeeds[i].getBytes (StandardCharsets.UTF_8);
    final IGenerator <byte []> aValid = Generators.of (aSeedBytes);
    final Byte [] aXmlBytes = new Byte [XML_BYTES.length];
    for (int i = 0; i < XML_BYTES.length; i++)
      aXmlBytes[i] = Byte.valueOf (XML_BYTES[i]);
    return Generators.oneOf (Generators.mutatedByteArrays (aValid, Generators.of (aXmlBytes), 4),
                             Generators.mutatedByteArrays (aValid))
                     .map (aBytes -> new String (aBytes, StandardCharsets.UTF_8));
  }

  /**
   * @return The element at the 0-based element-only position path below the document node, or
   *         <code>null</code> if there is none.
   */
  @Nullable
  private static IXdmNode _resolveElement (@NonNull final IXdmNode aDocument, @NonNull final List <Integer> aPath)
  {
    IXdmNode aCur = aDocument;
    for (final Integer aIndex : aPath)
    {
      IXdmNode aFound = null;
      int nElement = 0;
      final int nCount = aCur.getChildCount ();
      for (int i = 0; i < nCount && aFound == null; i++)
      {
        final IXdmNode aChild = aCur.getChildAtIndex (i);
        if (aChild.getKind () == ENodeKind.ELEMENT)
        {
          if (nElement == aIndex.intValue ())
            aFound = aChild;
          nElement++;
        }
      }
      if (aFound == null)
        return null;
      aCur = aFound;
    }
    return aCur;
  }

  /**
   * Check the documented contract of an assessment: the verdict equals that of validateText, an
   * invalid result has errors, a valid one none, and every reported position path (and attribute)
   * exists in the instance.
   */
  private static void _checkAssessment (@NonNull final XsdSchema aSchema, @NonNull final String sInstance)
  {
    final XsdValidationOptions aOptions = new XsdValidationOptions ();
    final XsdValidationResult aResult;
    final XsdAssessment aAssessment;
    try
    {
      aResult = aSchema.validateText (sInstance, aOptions);
      aAssessment = aSchema.assessText (sInstance, aOptions);
    }
    catch (final XsdUnsupportedException ex)
    {
      // Documented: no verdict
      return;
    }
    assertEquals (sInstance, Boolean.valueOf (aResult.valid ()), Boolean.valueOf (aResult.errors ().isEmpty ()));
    assertEquals (sInstance, Boolean.valueOf (aResult.valid ()), Boolean.valueOf (aAssessment.result ().valid ()));
    assertEquals (sInstance, aResult.errors ().size (), aAssessment.result ().errors ().size ());
    if (aAssessment.nodeTypes ().isEmpty ())
      return;
    final IXdmNode aDocument;
    try
    {
      aDocument = new XdmParser ().parse (sInstance);
    }
    catch (final XdmParseException ex)
    {
      throw new AssertionError ("Node types reported for a document that does not parse: " + sInstance, ex);
    }
    for (final XsdNodeType aNodeType : aAssessment.nodeTypes ())
    {
      assertFalse (sInstance, aNodeType.elementPath ().isEmpty ());
      final IXdmNode aElement = _resolveElement (aDocument, aNodeType.elementPath ());
      assertNotNull (sInstance + " " + aNodeType.elementPath (), aElement);
      if (aNodeType.isAttribute ())
        assertNotNull (sInstance + " " + aNodeType.elementPath () + " " + aNodeType.attribute (),
                       aElement.getAttribute (aNodeType.attribute ().space (), aNodeType.attribute ().local ()));
    }
  }

  /**
   * @return The instance built by interpreting the operations: 0 opens a child element, 1 closes
   *         the current element (never the document element), 2 adds text, 3 a comment, 4 a
   *         processing instruction and 5 the attribute <code>n</code> (with the next of the
   *         attribute values) to an element whose start tag is still open. All open elements are
   *         closed at the end.
   */
  @NonNull
  private static ItemInstance _buildItemInstance (@NonNull final List <Integer> aOps,
                                                  @NonNull final List <String> aAttrValues)
  {
    final StringBuilder aSB = new StringBuilder ("<item");
    final ICommonsList <String> aExpected = new CommonsArrayList <> ();
    final ICommonsList <OpenElement> aStack = new CommonsArrayList <> ();
    final OpenElement aRoot = new OpenElement (new CommonsArrayList <> (Integer.valueOf (0)));
    aStack.add (aRoot);
    aExpected.add (aRoot.m_aPath.toString ());
    int nAttr = 0;
    boolean bValid = true;
    for (final Integer aOp : aOps)
    {
      final int nOp = aOp.intValue ();
      final OpenElement aCur = aStack.getLastOrNull ();
      if (nOp == 5)
      {
        if (aCur.m_bStartTagOpen && !aCur.m_bHasAttribute)
        {
          final String sValue = aAttrValues.get (nAttr % aAttrValues.size ());
          nAttr++;
          aSB.append (" n=\"").append (sValue).append ('"');
          aCur.m_bHasAttribute = true;
          // The start tag is still open, so the element is the last entry and its attribute
          // follows it directly
          aExpected.add (aCur.m_aPath.toString () + "@n");
          if (!sValue.strip ().matches ("[+-]?[0-9]{1,9}"))
            bValid = false;
        }
        continue;
      }
      if (aCur.m_bStartTagOpen)
      {
        aSB.append ('>');
        aCur.m_bStartTagOpen = false;
      }
      switch (nOp)
      {
        case 0:
        {
          final ICommonsList <Integer> aPath = aCur.m_aPath.getClone ();
          aPath.add (Integer.valueOf (aCur.m_nChildElements++));
          final OpenElement aChild = new OpenElement (aPath);
          aStack.add (aChild);
          aExpected.add (aPath.toString ());
          aSB.append ("<item");
          break;
        }
        case 1:
          if (aStack.size () > 1)
          {
            aStack.removeLast ();
            aSB.append ("</item>");
          }
          break;
        case 2:
          aSB.append ("text");
          break;
        case 3:
          aSB.append ("<!--c-->");
          break;
        default:
          aSB.append ("<?pi data?>");
          break;
      }
    }
    while (!aStack.isEmpty ())
    {
      final OpenElement aCur = aStack.removeLast ();
      aSB.append (aCur.m_bStartTagOpen ? "/>" : "</item>");
    }
    return new ItemInstance (aSB.toString (), aExpected, bValid);
  }

  /**
   * Robustness property: a compile of a mutated schema document either succeeds or fails with
   * {@link XsdInvalidException} or {@link XsdUnsupportedException}, under XSD 1.0 and 1.1; a schema
   * that compiles validates the seed instances with a consistent result.
   */
  @Test
  public void testMutatedSchemaOnlyFailsAsDocumented ()
  {
    new PropertyChecker ().setTries (400)
                          .forAll (_mutated (ORDER_SCHEMA, ASSERT_SCHEMA), Generators.booleans (), (sSchema, aV11) -> {
                            final XsdSchema aSchema;
                            try
                            {
                              aSchema = _compile (sSchema, aV11.booleanValue () ? EXsdVersion.V11 : EXsdVersion.V10);
                            }
                            catch (final XsdInvalidException | XsdUnsupportedException ex)
                            {
                              // Documented outcomes
                              return;
                            }
                            for (final String sInstance : ORDER_INSTANCES)
                              _checkAssessment (aSchema, sInstance);
                          });
  }

  /**
   * Robustness property: validating and assessing a mutated instance document never throws
   * anything but {@link XsdUnsupportedException}; the verdicts of both entry points agree, an
   * invalid result has errors and every reported node position exists in the instance.
   */
  @Test
  public void testMutatedInstanceOnlyFailsAsDocumented ()
  {
    final XsdSchema aSchema10 = _compile (ORDER_SCHEMA, EXsdVersion.V10);
    final XsdSchema aSchema11 = _compile (ASSERT_SCHEMA, EXsdVersion.V11);
    for (final String sInstance : ORDER_INSTANCES)
      _checkAssessment (aSchema10, sInstance);
    new PropertyChecker ().setTries (600)
                          .forAll (_mutated (ORDER_INSTANCES), Generators.booleans (), (sInstance, aV11) -> {
                            _checkAssessment (aV11.booleanValue () ? aSchema11 : aSchema10, sInstance);
                          });
  }

  /**
   * Model property for {@link XsdSchema#assessText(String, XsdValidationOptions)}: for a generated
   * instance of recursive mixed elements, interleaved with text, comments and processing
   * instructions, the assessment reports exactly one entry per element and per attribute, in
   * document order (attributes after their element), with the element-only position paths of the
   * model; the instance is valid exactly when every attribute value is an xs:int. For an invalid
   * instance every reported position still exists.
   */
  @Test
  public void testAssessmentPositionsReferToNodes ()
  {
    final XsdSchema aSchema = _compile (ITEM_SCHEMA, EXsdVersion.V11);
    final IGenerator <List <String>> aAttrValues = Generators.lists (Generators.of ("1",
                                                                                    " -7 ",
                                                                                    "+42",
                                                                                    "x",
                                                                                    "2147483648",
                                                                                    ""),
                                                                     1,
                                                                     4);
    new PropertyChecker ().setTries (500)
                          .forAll (Generators.lists (Generators.ints (0, 5), 0, 40), aAttrValues, (aOps, aValues) -> {
                            final ItemInstance aInstance = _buildItemInstance (aOps, aValues);
                            final XsdAssessment aAssessment = aSchema.assessText (aInstance.xml (),
                                                                                  new XsdValidationOptions ());
                            assertEquals (aInstance.xml () + " " + aAssessment.result ().errors (),
                                          Boolean.valueOf (aInstance.valid ()),
                                          Boolean.valueOf (aAssessment.result ().valid ()));
                            final ICommonsList <String> aReported = new CommonsArrayList <> ();
                            for (final XsdNodeType aNodeType : aAssessment.nodeTypes ())
                            {
                              if (aNodeType.isAttribute ())
                              {
                                assertEquals (new XsdName ("", "n"), aNodeType.attribute ());
                                assertTrue (aNodeType.type ().isSimple ());
                              }
                              else
                                assertEquals (new XsdName ("", "T"), aNodeType.type ().getName ());
                              aReported.add (aNodeType.elementPath ().toString () +
                                             (aNodeType.isAttribute () ? "@n" : ""));
                            }
                            if (aInstance.valid ())
                              assertEquals (aInstance.xml (), aInstance.expected (), aReported);
                            else
                              for (final String sReported : aReported)
                                assertTrue (aInstance.xml () + " " + sReported, aInstance.expected ().contains (sReported));
                            _checkAssessment (aSchema, aInstance.xml ());
                          });
  }
}

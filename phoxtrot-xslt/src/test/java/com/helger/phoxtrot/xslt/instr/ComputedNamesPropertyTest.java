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
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for xsl:element and xsl:attribute with computed names
 * and namespaces (XSLT 3.0 sections 11.2 and 11.3) and the namespace fixup of
 * section 5.7.3: random nested elements and attributes whose prefixes clash
 * with each other and with the declarations of an enclosing literal result
 * element, with the same expanded attribute name given twice (the later
 * replaces the earlier), serialize to XML that the JDK parser reads with the
 * intended expanded names and values.
 *
 * @author Philip Helger
 */
public final class ComputedNamesPropertyTest
{
  /** A computed name: prefix ("" for none), namespace URI ("" for none) and local name */
  private record Name (@NonNull String prefix, @NonNull String uri, @NonNull String local)
  {
    @NonNull
    String getLexical ()
    {
      return prefix.isEmpty () || uri.isEmpty () ? local : prefix + ":" + local;
    }
  }

  /** An attribute to construct */
  private record Attr (@NonNull Name name, @NonNull String value, boolean dynamic)
  {}

  /** An element to construct */
  private record Elem (@NonNull Name name, boolean dynamic, @NonNull List <Attr> attrs, @NonNull List <Elem> children)
  {}

  private static final IGenerator <Name> NAMES = aChoices -> new Name (Generators.of ("", "p", "q").generate (aChoices),
                                                                       Generators.of ("", "urn:a", "urn:b").generate (aChoices),
                                                                       Generators.of ("e", "f").generate (aChoices));

  private static final IGenerator <Elem> ELEMENTS = aChoices -> _generateElement (aChoices, 3);

  @NonNull
  private static Elem _generateElement (@NonNull final ChoiceSequence aChoices, final int nDepth)
  {
    final Name aName = NAMES.generate (aChoices);
    final List <Attr> aAttrs = new ArrayList <> ();
    final int nAttrs = (int) aChoices.draw (3);
    for (int i = 0; i < nAttrs; ++i)
      aAttrs.add (new Attr (NAMES.generate (aChoices), Generators.of ("1", "x y", "").generate (aChoices), aChoices.draw (1) == 1));
    final List <Elem> aChildren = new ArrayList <> ();
    final int nChildren = nDepth <= 0 ? 0 : (int) aChoices.draw (2);
    for (int i = 0; i < nChildren; ++i)
      aChildren.add (_generateElement (aChoices, nDepth - 1));
    return new Elem (aName, aChoices.draw (1) == 1, aAttrs, aChildren);
  }

  /** The name and namespace attributes of xsl:element or xsl:attribute, as AVTs when dynamic */
  private static void _appendNameAttributes (@NonNull final StringBuilder aSB, @NonNull final Name aName, final boolean bDynamic)
  {
    if (bDynamic)
      aSB.append (" name=\"{'").append (aName.getLexical ()).append ("'}\" namespace=\"{'").append (aName.uri ()).append ("'}\"");
    else
      aSB.append (" name='").append (aName.getLexical ()).append ("' namespace='").append (aName.uri ()).append ('\'');
  }

  private static void _appendInstructionsRecursive (@NonNull final StringBuilder aSB, @NonNull final Elem aElem)
  {
    aSB.append ("<xsl:element");
    _appendNameAttributes (aSB, aElem.name (), aElem.dynamic ());
    aSB.append ('>');
    for (final Attr aAttr : aElem.attrs ())
    {
      aSB.append ("<xsl:attribute");
      _appendNameAttributes (aSB, aAttr.name (), aAttr.dynamic ());
      aSB.append (" select=\"'").append (aAttr.value ()).append ("'\"/>");
    }
    for (final Elem aChild : aElem.children ())
      _appendInstructionsRecursive (aSB, aChild);
    aSB.append ("</xsl:element>");
  }

  private static void _appendExpectedRecursive (@NonNull final Document aDoc, @NonNull final Element aParent, @NonNull final Elem aElem)
  {
    final Element aElement = aDoc.createElementNS (aElem.name ().uri ().isEmpty () ? null : aElem.name ().uri (),
                                                   aElem.name ().getLexical ());
    aParent.appendChild (aElement);
    for (final Attr aAttr : aElem.attrs ())
    {
      // The same expanded name replaces the earlier attribute
      final String sUri = aAttr.name ().uri ().isEmpty () ? null : aAttr.name ().uri ();
      if (aElement.hasAttributeNS (sUri, aAttr.name ().local ()))
        aElement.removeAttributeNS (sUri, aAttr.name ().local ());
      aElement.setAttributeNS (sUri, aAttr.name ().getLexical (), aAttr.value ());
    }
    for (final Elem aChild : aElem.children ())
      _appendExpectedRecursive (aDoc, aElement, aChild);
  }

  /**
   * The constructed tree, inside literal result elements that bind the
   * prefixes p and q to other namespaces and a default namespace, re-parses
   * with the intended expanded names and attribute values.
   */
  @Test
  public void testComputedNamesSurviveSerialization ()
  {
    new PropertyChecker ().setTries (1000).forAll (ELEMENTS, aElem -> {
      final StringBuilder aSheet = new StringBuilder (XsltTestHelper.SHEET).append ("<xsl:output method='xml' omit-xml-declaration='yes'/>")
                                                                          .append ("<xsl:template name='xsl:initial-template'>")
                                                                          .append ("<w xmlns:p='urn:b' xmlns:q='urn:c'><v xmlns='urn:d'>");
      _appendInstructionsRecursive (aSheet, aElem);
      aSheet.append ("</v></w></xsl:template>").append (XsltTestHelper.END);

      final DocumentBuilderFactory aDBF = DocumentBuilderFactory.newInstance ();
      aDBF.setNamespaceAware (true);
      final Document aExpected = aDBF.newDocumentBuilder ().newDocument ();
      final Element aW = aExpected.createElementNS (null, "w");
      aExpected.appendChild (aW);
      final Element aV = aExpected.createElementNS ("urn:d", "v");
      aW.appendChild (aV);
      _appendExpectedRecursive (aExpected, aV, aElem);

      final String sResult = XsltTestHelper.transform (aSheet.toString (), null, null);
      assertEquals (aSheet + " gave " + sResult,
                    RandomXmlDocument.getCanonical (aExpected),
                    RandomXmlDocument.getCanonical (RandomXmlDocument.parseWithJdk (sResult)));
    });
  }
}

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
package com.helger.phoxtrot.xdm.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.ArrayDeque;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmDocumentGenerator;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmParserPropertyTest;
import com.helger.phoxtrot.xdm.XdmTreeBuilder;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the DOM bridge ({@link DomToXdm}, {@link XdmToDom}, {@link XdmToSax},
 * {@link XdmDomView}) on documents of {@link XdmDocumentGenerator}: every way from XDM to DOM or SAX
 * and back gives the same tree, and a DOM read by the JDK parser converts to the tree
 * {@link XdmParser} builds.
 *
 * @author Philip Helger
 */
public final class DomBridgePropertyTest
{
  @NonNull
  private static Document _parseDom (@NonNull final String sXml, final boolean bNamespaceAware) throws Exception
  {
    final DocumentBuilderFactory aFactory = DocumentBuilderFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (bNamespaceAware);
    return aFactory.newDocumentBuilder ().parse (new InputSource (new StringReader (sXml)));
  }

  /**
   * Writing a parsed tree to a DOM ({@link XdmToDom}) or to SAX events ({@link XdmToSax}), or
   * viewing it as a DOM ({@link XdmDomView}), and converting the result back gives the same tree,
   * numbered in document order.
   */
  @Test
  public void testRoundTrips ()
  {
    new PropertyChecker ().setTries (200).forAll (XdmDocumentGenerator.documents (), aGenDoc -> {
      final IXdmNode aOriginal = new XdmParser ().parse (aGenDoc.getAsXml ());
      final Object aExpected = DomBridgeTest.describe (aOriginal);

      final IXdmNode aFromDom = DomToXdm.convertDocument (XdmToDom.toDocument (aOriginal), null);
      assertEquals ("XdmToDom", aExpected, DomBridgeTest.describe (aFromDom));
      XdmParserPropertyTest.assertReferenceNumbering (aFromDom);

      final XdmTreeBuilder aBuilder = new XdmTreeBuilder ();
      XdmToSax.emit (aOriginal, aBuilder, aBuilder);
      assertEquals ("XdmToSax", aExpected, DomBridgeTest.describe (aBuilder.getDocument ()));

      final Document aView = new XdmDomView (aOriginal).getDocument ();
      assertNotNull (aView);
      final IXdmNode aFromView = DomToXdm.convertDocument (aView, null);
      assertEquals ("XdmDomView", aExpected, DomBridgeTest.describe (aFromView));
      XdmParserPropertyTest.assertReferenceNumbering (aFromView);
    });
  }

  /**
   * A DOM read by the JDK parser, namespace aware or not, converts to the tree {@link XdmParser}
   * builds from the same text.
   */
  @Test
  public void testDomToXdmMatchesParser ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (XdmDocumentGenerator.documents (), Generators.booleans (), (aGenDoc, aNamespaceAware) -> {
                            final String sXml = aGenDoc.getAsXml ();
                            final IXdmNode aParsed = new XdmParser ().parse (sXml);
                            final IXdmNode aConverted = DomToXdm.convertDocument (_parseDom (sXml,
                                                                                             aNamespaceAware.booleanValue ()),
                                                                                  null);
                            assertEquals (DomBridgeTest.describe (aParsed), DomBridgeTest.describe (aConverted));
                            assertEquals (aGenDoc.getExpectedDescription (), XdmDocumentGenerator.describe (aConverted));
                          });
  }

  /**
   * Check that a DOM node is the source of an XDM node: same kind and name, and for a text node the
   * first DOM text of the merged run.
   */
  private static void _assertMapped (@NonNull final IXdmNode aXdm, @NonNull final Node aDom)
  {
    switch (aXdm.getKind ())
    {
      case DOCUMENT -> assertTrue (aDom instanceof Document);
      case ELEMENT ->
      {
        final Element aElement = (Element) aDom;
        assertEquals (aXdm.getName ().localName (), aElement.getLocalName ());
        assertEquals (aXdm.getName ().namespaceUri (), aElement.getNamespaceURI () == null ? "" : aElement.getNamespaceURI ());
      }
      case ATTRIBUTE ->
      {
        final Attr aAttr = (Attr) aDom;
        assertEquals (aXdm.getName ().localName (), aAttr.getLocalName ());
        assertEquals (aXdm.getValue (), aAttr.getValue ());
      }
      case TEXT -> assertTrue (aXdm.getValue ().startsWith (((Text) aDom).getData ()));
      case COMMENT -> assertEquals (aXdm.getValue (), ((Comment) aDom).getData ());
      case PROCESSING_INSTRUCTION ->
      {
        final ProcessingInstruction aPI = (ProcessingInstruction) aDom;
        assertEquals (aXdm.getName ().localName (), aPI.getTarget ());
        assertEquals (aXdm.getValue (), aPI.getData ());
      }
      default -> throw new IllegalStateException ("Unexpected node " + aXdm);
    }
  }

  /**
   * {@link DomToXdm#convertWithMapping(Node, String)} maps every node of the built tree to the DOM
   * node it came from, and {@link XdmDomView} gives one DOM node per XDM node that leads back to it.
   */
  @Test
  public void testMappings ()
  {
    new PropertyChecker ().setTries (200).forAll (XdmDocumentGenerator.documents (), aGenDoc -> {
      final DomNodeMapping aMapping = DomToXdm.convertWithMapping (_parseDom (aGenDoc.getAsXml (), true), null);
      final IXdmNode aConverted = aMapping.getXdmNode ();
      final XdmDomView aView = new XdmDomView (aConverted);
      int nNodes = 0;
      final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
      aStack.push (aConverted);
      while (!aStack.isEmpty ())
      {
        final IXdmNode aCur = aStack.pop ();
        nNodes++;
        final Node aDom = aMapping.getDomNode (aCur);
        assertNotNull (aCur.toString (), aDom);
        _assertMapped (aCur, aDom);
        final Node aViewNode = aView.getNode (aCur);
        assertSame (aViewNode, aView.getNode (aCur));
        assertSame (aCur, XdmDomView.getXdmNode (aViewNode));
        _assertMapped (aCur, aViewNode);
        for (int i = 0; i < aCur.getAttributeCount (); ++i)
          aStack.push (aCur.getAttributeAtIndex (i));
        for (int i = aCur.getChildCount () - 1; i >= 0; --i)
          aStack.push (aCur.getChildAtIndex (i));
      }
      assertEquals (nNodes, aMapping.getMappedNodeCount ());
    });
  }
}

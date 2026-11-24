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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.ArrayDeque;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmDocumentGenerator;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for {@link XdmDomView}: for documents of {@link XdmDocumentGenerator}, the
 * read-only DOM view over the parsed tree answers the W3C DOM Level 3 Core queries exactly as the
 * JDK DOM parsed from the same text (namespace aware, CDATA sections coalesced into text): node
 * types, names, values, text content, navigation, attribute maps with the <code>xmlns</code>
 * attributes, namespace lookups, <code>getElementsByTagName(NS)</code>,
 * <code>isEqualNode</code> in both directions and <code>compareDocumentPosition</code>.
 *
 * @author Philip Helger
 */
public final class XdmDomViewPropertyTest
{
  /** Prefixes and URIs of the namespace lookups: those of the generator and unused ones */
  private static final String [] PREFIXES = { null, "p", "q", "r", "s" };
  private static final String [] URIS = { null, "", "urn:a", "urn:b", "http://example.org/ns?x=1&y=\"2\"", "urn:none" };

  @NonNull
  private static Document _parseJdk (@NonNull final String sXml) throws Exception
  {
    final DocumentBuilderFactory aFactory = DocumentBuilderFactory.newDefaultInstance ();
    aFactory.setNamespaceAware (true);
    aFactory.setCoalescing (true);
    return aFactory.newDocumentBuilder ().parse (new InputSource (new StringReader (sXml)));
  }

  /**
   * @return The nodes of a DOM tree in document order (no attributes), walked iteratively through
   *         first child and next sibling.
   */
  @NonNull
  private static ICommonsList <Node> _getNodesInOrder (@NonNull final Node aRoot)
  {
    final ICommonsList <Node> ret = new CommonsArrayList <> ();
    final ArrayDeque <Node> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final Node aCur = aStack.pop ();
      ret.add (aCur);
      final ICommonsList <Node> aChildren = new CommonsArrayList <> ();
      for (Node aChild = aCur.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
        aChildren.add (aChild);
      for (int i = aChildren.size () - 1; i >= 0; --i)
        aStack.push (aChildren.get (i));
    }
    return ret;
  }

  @NonNull
  private static String _describe (@NonNull final Node aNode)
  {
    return aNode.getNodeType () + " " + aNode.getNodeName () + " '" + aNode.getNodeValue () + "'";
  }

  /** Compare the name and value queries of a node of the view and of the JDK DOM. */
  private static void _assertSameNodeProperties (@NonNull final Node aView, @NonNull final Node aJdk)
  {
    final String sMsg = _describe (aJdk);
    assertEquals (sMsg, aJdk.getNodeType (), aView.getNodeType ());
    assertEquals (sMsg, aJdk.getNodeName (), aView.getNodeName ());
    assertEquals (sMsg, aJdk.getNodeValue (), aView.getNodeValue ());
    assertEquals (sMsg, aJdk.getNamespaceURI (), aView.getNamespaceURI ());
    assertEquals (sMsg, aJdk.getPrefix (), aView.getPrefix ());
    assertEquals (sMsg, aJdk.getLocalName (), aView.getLocalName ());
    assertEquals (sMsg, aJdk.getTextContent (), aView.getTextContent ());
  }

  /** Compare the attribute maps of an element of the view and of the JDK DOM. */
  private static void _assertSameAttributes (@NonNull final Element aView, @NonNull final Element aJdk)
  {
    final String sMsg = _describe (aJdk);
    final NamedNodeMap aJdkAttrs = aJdk.getAttributes ();
    final NamedNodeMap aViewAttrs = aView.getAttributes ();
    assertEquals (sMsg, aJdkAttrs.getLength (), aViewAttrs.getLength ());
    assertEquals (sMsg, Boolean.valueOf (aJdk.hasAttributes ()), Boolean.valueOf (aView.hasAttributes ()));
    for (int i = 0; i < aJdkAttrs.getLength (); ++i)
    {
      final Attr aJdkAttr = (Attr) aJdkAttrs.item (i);
      final String sAttrMsg = sMsg + " @" + aJdkAttr.getName ();
      final Attr aViewAttr = (Attr) aViewAttrs.getNamedItemNS (aJdkAttr.getNamespaceURI (), aJdkAttr.getLocalName ());
      assertNotNull (sAttrMsg, aViewAttr);
      _assertSameNodeProperties (aViewAttr, aJdkAttr);
      assertEquals (sAttrMsg, aJdkAttr.getName (), aViewAttr.getName ());
      assertEquals (sAttrMsg, aJdkAttr.getValue (), aViewAttr.getValue ());
      assertSame (sAttrMsg, aView, aViewAttr.getOwnerElement ());
      assertNull (sAttrMsg, aViewAttr.getParentNode ());
      assertSame (sAttrMsg, aViewAttr, aViewAttrs.getNamedItem (aJdkAttr.getName ()));
      assertEquals (sAttrMsg,
                    aJdk.getAttributeNS (aJdkAttr.getNamespaceURI (), aJdkAttr.getLocalName ()),
                    aView.getAttributeNS (aJdkAttr.getNamespaceURI (), aJdkAttr.getLocalName ()));
      assertEquals (sAttrMsg, aJdk.getAttribute (aJdkAttr.getName ()), aView.getAttribute (aJdkAttr.getName ()));
      assertTrue (sAttrMsg, aView.hasAttributeNS (aJdkAttr.getNamespaceURI (), aJdkAttr.getLocalName ()));
    }
  }

  /** Compare the namespace lookups at a node of the view and of the JDK DOM. */
  private static void _assertSameNamespaceLookups (@NonNull final Node aView, @NonNull final Node aJdk)
  {
    final String sMsg = _describe (aJdk);
    for (final String sPrefix : PREFIXES)
      assertEquals (sMsg + " lookupNamespaceURI " + sPrefix,
                    aJdk.lookupNamespaceURI (sPrefix),
                    aView.lookupNamespaceURI (sPrefix));
    for (final String sUri : URIS)
    {
      // The empty URI (null in DOM Level 3 Core section 1.3.3) is left to testIsDefaultNamespaceWithoutNamespace
      if (sUri != null && !sUri.isEmpty ())
        assertEquals (sMsg + " isDefaultNamespace " + sUri,
                      Boolean.valueOf (aJdk.isDefaultNamespace (sUri)),
                      Boolean.valueOf (aView.isDefaultNamespace (sUri)));
      // Any prefix in scope for the URI may be returned, but it must be bound to it
      final String sPrefix = aView.lookupPrefix (sUri);
      if (sPrefix != null)
        assertEquals (sMsg + " lookupPrefix " + sUri, sUri, aView.lookupNamespaceURI (sPrefix));
      else
        if (sUri != null && !sUri.isEmpty ())
          for (final String sOther : PREFIXES)
            if (sOther != null)
              assertTrue (sMsg + " lookupPrefix " + sUri + " misses " + sOther,
                          !sUri.equals (aView.lookupNamespaceURI (sOther)));
    }
  }

  @NonNull
  private static String _elementNames (@NonNull final NodeList aList)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < aList.getLength (); ++i)
      aSB.append (((Element) aList.item (i)).getTagName ()).append (' ');
    return aSB.toString ();
  }

  /**
   * DOM Level 3 Core appendix B.3 <code>isDefaultNamespace (null)</code> computed on the JDK DOM.
   * The JDK answer itself is no oracle here: for a prefixed element with an <code>xmlns</code>
   * attribute it compares the element's own namespace with the declared one instead of the
   * argument, so <code>&lt;p:a xmlns="urn:a" xmlns:p="urn:a"/&gt;</code> answers <code>true</code>.
   */
  private static boolean _isDefaultNamespaceNull (@NonNull final Node aNode)
  {
    Node aCur = switch (aNode)
    {
      case final Document aDoc -> aDoc.getDocumentElement ();
      case final Attr aAttr -> aAttr.getOwnerElement ();
      case final Element aElement -> aElement;
      default -> aNode.getParentNode ();
    };
    while (aCur instanceof final Element aElement)
    {
      if (aElement.getPrefix () == null)
        return aElement.getNamespaceURI () == null;
      final Attr aDecl = aElement.getAttributeNodeNS (XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns");
      if (aDecl != null)
        return aDecl.getValue ().isEmpty ();
      aCur = aElement.getParentNode ();
    }
    return false;
  }

  /**
   * The view answers every query on every node as the JDK DOM does.
   */
  @Test
  public void testViewMatchesJdkDom ()
  {
    new PropertyChecker ().setTries (200).forAll (XdmDocumentGenerator.documents (), aGenDoc -> {
      final String sXml = aGenDoc.getAsXml ();
      final IXdmNode aXdm = new XdmParser ().parse (sXml);
      final Document aView = new XdmDomView (aXdm).getDocument ();
      final Document aJdk = _parseJdk (sXml);

      final ICommonsList <Node> aViewNodes = _getNodesInOrder (aView);
      final ICommonsList <Node> aJdkNodes = _getNodesInOrder (aJdk);
      assertEquals (aJdkNodes.size (), aViewNodes.size ());
      for (int i = 0; i < aJdkNodes.size (); ++i)
      {
        final Node aViewNode = aViewNodes.get (i);
        final Node aJdkNode = aJdkNodes.get (i);
        _assertSameNodeProperties (aViewNode, aJdkNode);
        final String sMsg = _describe (aJdkNode);
        assertEquals (sMsg,
                      Boolean.valueOf (aJdkNode.hasChildNodes ()),
                      Boolean.valueOf (aViewNode.hasChildNodes ()));
        assertEquals (sMsg, aJdkNode.getChildNodes ().getLength (), aViewNode.getChildNodes ().getLength ());
        // Navigation: parent, last child and previous sibling point to the corresponding nodes
        if (aJdkNode.getParentNode () != null)
          assertSame (sMsg, aViewNodes.get (aJdkNodes.indexOf (aJdkNode.getParentNode ())), aViewNode.getParentNode ());
        else
          assertNull (sMsg, aViewNode.getParentNode ());
        if (aJdkNode.getLastChild () != null)
          assertSame (sMsg, aViewNodes.get (aJdkNodes.indexOf (aJdkNode.getLastChild ())), aViewNode.getLastChild ());
        if (aJdkNode.getPreviousSibling () != null)
          assertSame (sMsg,
                      aViewNodes.get (aJdkNodes.indexOf (aJdkNode.getPreviousSibling ())),
                      aViewNode.getPreviousSibling ());
        if (aJdkNode.getNodeType () != Node.DOCUMENT_NODE)
          assertSame (sMsg, aView, aViewNode.getOwnerDocument ());
        if (aJdkNode instanceof final Element aJdkElement)
        {
          final Element aViewElement = (Element) aViewNode;
          _assertSameAttributes (aViewElement, aJdkElement);
          assertEquals (sMsg,
                        _elementNames (aJdkElement.getElementsByTagNameNS ("*", "*")),
                        _elementNames (aViewElement.getElementsByTagNameNS ("*", "*")));
          assertEquals (sMsg,
                        _elementNames (aJdkElement.getElementsByTagName (aJdkElement.getTagName ())),
                        _elementNames (aViewElement.getElementsByTagName (aJdkElement.getTagName ())));
          assertEquals (sMsg,
                        _elementNames (aJdk.getElementsByTagNameNS (aJdkElement.getNamespaceURI (),
                                                                    aJdkElement.getLocalName ())),
                        _elementNames (aView.getElementsByTagNameNS (aJdkElement.getNamespaceURI (),
                                                                     aJdkElement.getLocalName ())));
        }
        _assertSameNamespaceLookups (aViewNode, aJdkNode);
        assertTrue (sMsg, aViewNode.isEqualNode (aJdkNode));
        assertTrue (sMsg, aJdkNode.isEqualNode (aViewNode));
      }

      // Document order and containment
      for (int i = 0; i < aJdkNodes.size (); ++i)
        for (int j = 0; j < aJdkNodes.size (); ++j)
          assertEquals (_describe (aJdkNodes.get (i)) + " vs " + _describe (aJdkNodes.get (j)),
                        aJdkNodes.get (i).compareDocumentPosition (aJdkNodes.get (j)),
                        aViewNodes.get (i).compareDocumentPosition (aViewNodes.get (j)));
    });
  }

  /**
   * <code>isDefaultNamespace (null)</code> (also for the empty string, which DOM Level 3 Core
   * section 1.3.3 takes as null) follows the algorithm of DOM Level 3 Core appendix B.3: an
   * unprefixed element compares its own namespace, a prefixed one its default namespace
   * declaration or its ancestor's answer, and a node without element ancestor (a comment or
   * processing instruction outside the document element, an element without default namespace
   * declaration in scope and a prefix) answers <code>false</code>.
   */
  @Test
  public void testIsDefaultNamespaceWithoutNamespace ()
  {
    new PropertyChecker ().setTries (200).forAll (XdmDocumentGenerator.documents (), aGenDoc -> {
      final String sXml = aGenDoc.getAsXml ();
      final ICommonsList <Node> aViewNodes = _getNodesInOrder (new XdmDomView (new XdmParser ().parse (sXml)).getDocument ());
      final ICommonsList <Node> aJdkNodes = _getNodesInOrder (_parseJdk (sXml));
      for (int i = 0; i < aJdkNodes.size (); ++i)
      {
        final boolean bExpected = _isDefaultNamespaceNull (aJdkNodes.get (i));
        assertEquals (_describe (aJdkNodes.get (i)),
                      Boolean.valueOf (bExpected),
                      Boolean.valueOf (aViewNodes.get (i).isDefaultNamespace (null)));
        assertEquals (_describe (aJdkNodes.get (i)),
                      Boolean.valueOf (bExpected),
                      Boolean.valueOf (aViewNodes.get (i).isDefaultNamespace ("")));
      }
    });
  }
}

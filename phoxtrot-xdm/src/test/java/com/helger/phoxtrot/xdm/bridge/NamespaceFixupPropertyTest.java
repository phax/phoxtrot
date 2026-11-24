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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmTreeBuilder;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the namespace fixup of {@link XdmToDom} and {@link XdmToSax} on trees
 * built through the {@link IXdmNode} API, whose prefixes and namespace declarations need not fit
 * together: elements and attributes reuse a prefix for different namespaces, namespaced attributes
 * have no prefix, prefixes look like the generated <code>ns0</code>, declarations contradict the
 * names of their element, and the default namespace is undeclared. The DOM and the SAX events must
 * be namespace well-formed (every prefix bound to the namespace of its name, no unprefixed
 * namespaced attribute, no undeclared prefix; Namespaces in XML 1.0, sections 5 and 6) and keep
 * every expanded name, attribute value and text. The DOM is also serialised and parsed again.
 *
 * @author Philip Helger
 */
public final class NamespaceFixupPropertyTest
{
  private static final String [] PREFIXES = { "", "p", "q", "ns0", "ns1" };
  private static final String [] URIS = { "", "urn:a", "urn:b", "urn:c" };
  private static final String [] LOCAL_NAMES = { "a", "b" };
  private static final int MAX_DEPTH = 4;

  /** Access to the elements of a result tree. */
  private interface IResultNavigator
  {
    /** @return The URI the prefix is bound to at the element, "" for no default namespace. */
    @Nullable
    String lookup (@NonNull Object aElement, @NonNull String sPrefix);

    /** @return The child elements. */
    @NonNull
    ICommonsList <Object> getChildElements (@NonNull Object aElement);
  }

  /** Navigates a DOM by its <code>xmlns</code> attributes. */
  private static final IResultNavigator DOM_NAVIGATOR = new IResultNavigator ()
  {
    public String lookup (final Object aElement, final String sPrefix)
    {
      return _lookupByAttributes ((Element) aElement, sPrefix);
    }

    public ICommonsList <Object> getChildElements (final Object aElement)
    {
      final ICommonsList <Object> ret = new CommonsArrayList <> ();
      for (Node aChild = ((Node) aElement).getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
        if (aChild instanceof Element)
          ret.add (aChild);
      return ret;
    }
  };

  /** Navigates an XDM tree by its in-scope namespaces. */
  private static final IResultNavigator XDM_NAVIGATOR = new IResultNavigator ()
  {
    public String lookup (final Object aElement, final String sPrefix)
    {
      final String ret = ((IXdmNode) aElement).getInScopeNamespaces ().get (sPrefix);
      return ret == null && sPrefix.isEmpty () ? "" : ret;
    }

    public ICommonsList <Object> getChildElements (final Object aElement)
    {
      final ICommonsList <Object> ret = new CommonsArrayList <> ();
      final IXdmNode aNode = (IXdmNode) aElement;
      for (int i = 0; i < aNode.getChildCount (); ++i)
        if (aNode.getChildAtIndex (i).isElement ())
          ret.add (aNode.getChildAtIndex (i));
      return ret;
    }
  };

  private static int _draw (@NonNull final ChoiceSequence aChoices, final int nMaxInclusive)
  {
    return (int) aChoices.draw (nMaxInclusive);
  }

  /**
   * @return A name: an empty prefix with any namespace (for an attribute: none), a non-empty prefix
   *         only with a namespace.
   */
  @NonNull
  private static XdmQName _drawName (@NonNull final ChoiceSequence aChoices)
  {
    final String sLocalName = LOCAL_NAMES[_draw (aChoices, LOCAL_NAMES.length - 1)];
    final String sUri = URIS[_draw (aChoices, URIS.length - 1)];
    final String sPrefix = sUri.isEmpty () ? "" : PREFIXES[_draw (aChoices, PREFIXES.length - 1)];
    return new XdmQName (sUri, sLocalName, sPrefix);
  }

  /** Draw an element with declarations and attributes, without children. */
  @NonNull
  private static IXdmNode _drawElement (@NonNull final ChoiceSequence aChoices)
  {
    final IXdmNode ret = IXdmNode.createElement (_drawName (aChoices));
    final int nDecls = _draw (aChoices, 2);
    for (int i = 0; i < nDecls; ++i)
    {
      final String sPrefix = PREFIXES[_draw (aChoices, PREFIXES.length - 1)];
      final String sUri = URIS[_draw (aChoices, URIS.length - 1)];
      // Only the default namespace can be undeclared in XML 1.0
      if (sPrefix.isEmpty () || !sUri.isEmpty ())
        ret.declareNamespace (sPrefix, sUri);
    }
    final int nAttrs = _draw (aChoices, 3);
    for (int i = 0; i < nAttrs; ++i)
    {
      final XdmQName aName = _draw (aChoices, 5) == 5 ? new XdmQName (XdmConstants.NS_XML, "lang", "xml")
                                                      : _drawName (aChoices);
      if (ret.getAttribute (aName.namespaceUri (), aName.localName ()) == null)
        ret.addAttribute (IXdmNode.createAttribute (aName, "v" + i));
    }
    return ret;
  }

  /** Draw a document with an element tree, iteratively. */
  @NonNull
  private static IXdmNode _drawDocument (@NonNull final ChoiceSequence aChoices)
  {
    final IXdmNode aDoc = IXdmNode.createDocument ();
    final IXdmNode aRoot = _drawElement (aChoices);
    aDoc.appendChild (aRoot);
    final ArrayDeque <Object []> aStack = new ArrayDeque <> ();
    aStack.push (new Object [] { aRoot, Integer.valueOf (1) });
    while (!aStack.isEmpty ())
    {
      final Object [] aCur = aStack.pop ();
      final IXdmNode aElement = (IXdmNode) aCur[0];
      final int nDepth = ((Integer) aCur[1]).intValue ();
      if (nDepth >= MAX_DEPTH)
        continue;
      final int nChildren = _draw (aChoices, 3);
      for (int i = 0; i < nChildren; ++i)
        if (_draw (aChoices, 3) == 0)
        {
          // Adjacent text nodes would be merged when the serialisation is parsed again
          final int nCount = aElement.getChildCount ();
          if (nCount == 0 || !aElement.getChildAtIndex (nCount - 1).isText ())
            aElement.appendChild (IXdmNode.createText ("t" + i));
        }
        else
        {
          final IXdmNode aChild = _drawElement (aChoices);
          aElement.appendChild (aChild);
          aStack.push (new Object [] { aChild, Integer.valueOf (nDepth + 1) });
        }
    }
    return aDoc;
  }

  @NonNull
  private static IGenerator <IXdmNode> _documents ()
  {
    return NamespaceFixupPropertyTest::_drawDocument;
  }

  @NonNull
  private static String _expanded (@Nullable final String sUri, @NonNull final String sLocalName)
  {
    return "{" + (sUri == null ? "" : sUri) + "}" + sLocalName;
  }

  /**
   * @return One line per element (expanded name and the sorted attributes) and text node of an XDM
   *         tree, with end markers, in document order; prefixes and declarations are left out.
   */
  @NonNull
  private static ICommonsList <String> _describeXdm (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ArrayDeque <Object> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final Object aCur = aStack.pop ();
      if (aCur instanceof final String sEnd)
      {
        ret.add (sEnd);
        continue;
      }
      final IXdmNode aNode = (IXdmNode) aCur;
      if (aNode.isText ())
        ret.add ("T " + aNode.getValue ());
      if (aNode.isElement ())
      {
        final Map <String, String> aAttrs = new TreeMap <> ();
        for (int i = 0; i < aNode.getAttributeCount (); ++i)
        {
          final IXdmNode aAttr = aNode.getAttributeAtIndex (i);
          aAttrs.put (_expanded (aAttr.getName ().namespaceUri (), aAttr.getName ().localName ()), aAttr.getValue ());
        }
        ret.add ("E " + _expanded (aNode.getName ().namespaceUri (), aNode.getName ().localName ()) + " " + aAttrs);
        aStack.push ("/");
      }
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (aNode.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * @return The description of {@link #_describeXdm(IXdmNode)} for a DOM tree, without the
   *         <code>xmlns</code> attributes.
   */
  @NonNull
  private static ICommonsList <String> _describeDom (@NonNull final Node aRoot)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ArrayDeque <Object> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final Object aCur = aStack.pop ();
      if (aCur instanceof final String sEnd)
      {
        ret.add (sEnd);
        continue;
      }
      final Node aNode = (Node) aCur;
      if (aNode.getNodeType () == Node.TEXT_NODE)
        ret.add ("T " + aNode.getNodeValue ());
      if (aNode instanceof final Element aElement)
      {
        final Map <String, String> aAttrs = new TreeMap <> ();
        final NamedNodeMap aMap = aElement.getAttributes ();
        for (int i = 0; i < aMap.getLength (); ++i)
        {
          final Attr aAttr = (Attr) aMap.item (i);
          if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals (aAttr.getNamespaceURI ()))
            aAttrs.put (_expanded (aAttr.getNamespaceURI (), aAttr.getLocalName ()), aAttr.getValue ());
        }
        ret.add ("E " + _expanded (aElement.getNamespaceURI (), aElement.getLocalName ()) + " " + aAttrs);
        aStack.push ("/");
      }
      final ICommonsList <Node> aChildren = new CommonsArrayList <> ();
      for (Node aChild = aNode.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
        aChildren.add (aChild);
      for (int i = aChildren.size () - 1; i >= 0; --i)
        aStack.push (aChildren.get (i));
    }
    return ret;
  }

  /**
   * @return The URI bound to a prefix by the <code>xmlns</code> attributes of the element and its
   *         ancestors, "" for an undeclared default namespace, <code>null</code> if unbound.
   */
  @Nullable
  private static String _lookupByAttributes (@NonNull final Element aElement, @NonNull final String sPrefix)
  {
    if (sPrefix.equals ("xml"))
      return XdmConstants.NS_XML;
    for (Node aCur = aElement; aCur instanceof final Element aCurElement; aCur = aCur.getParentNode ())
    {
      final Attr aDecl = sPrefix.isEmpty () ? aCurElement.getAttributeNodeNS (XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns")
                                            : aCurElement.getAttributeNodeNS (XMLConstants.XMLNS_ATTRIBUTE_NS_URI, sPrefix);
      if (aDecl != null)
        return aDecl.getValue ();
    }
    return sPrefix.isEmpty () ? "" : null;
  }

  @NonNull
  private static String _nonNull (@Nullable final String s)
  {
    return s == null ? "" : s;
  }

  /**
   * Check that a DOM tree is namespace well-formed by its own <code>xmlns</code> attributes: every
   * element and attribute prefix is bound to the namespace of its name, unprefixed attributes have
   * no namespace, and no prefix other than the default one is undeclared.
   */
  private static void _assertNamespaceWellFormed (@NonNull final Node aRoot)
  {
    final ArrayDeque <Node> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final Node aNode = aStack.pop ();
      if (aNode instanceof final Element aElement)
      {
        final String sMsg = aElement.getTagName ();
        assertEquals (sMsg,
                      _nonNull (aElement.getNamespaceURI ()),
                      _lookupByAttributes (aElement, _nonNull (aElement.getPrefix ())));
        final NamedNodeMap aMap = aElement.getAttributes ();
        for (int i = 0; i < aMap.getLength (); ++i)
        {
          final Attr aAttr = (Attr) aMap.item (i);
          final String sAttrMsg = sMsg + " @" + aAttr.getName ();
          if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals (aAttr.getNamespaceURI ()))
          {
            if (aAttr.getPrefix () != null)
              assertFalse (sAttrMsg + " undeclares a prefix", aAttr.getValue ().isEmpty ());
          }
          else
            if (aAttr.getNamespaceURI () == null)
              assertNull (sAttrMsg, aAttr.getPrefix ());
            else
            {
              assertNotNull (sAttrMsg + " has a namespace but no prefix", aAttr.getPrefix ());
              assertEquals (sAttrMsg, aAttr.getNamespaceURI (), _lookupByAttributes (aElement, aAttr.getPrefix ()));
            }
        }
      }
      for (Node aChild = aNode.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
        aStack.push (aChild);
    }
  }

  @NonNull
  private static String _serialise (@NonNull final Node aNode) throws Exception
  {
    final Transformer aTransformer = TransformerFactory.newDefaultInstance ().newTransformer ();
    aTransformer.setOutputProperty (OutputKeys.OMIT_XML_DECLARATION, "yes");
    final StringWriter aSW = new StringWriter ();
    aTransformer.transform (new DOMSource (aNode), new StreamResult (aSW));
    return aSW.toString ();
  }

  /**
   * The DOM written by {@link XdmToDom#toDocument(IXdmNode)} is namespace well-formed, keeps every
   * expanded name, value and text, and its serialisation parses to the same tree.
   */
  @Test
  public void testToDocument ()
  {
    new PropertyChecker ().setTries (300).forAll (_documents (), aDoc -> {
      final ICommonsList <String> aExpected = _describeXdm (aDoc);
      final Document aDom = XdmToDom.toDocument (aDoc);
      _assertNamespaceWellFormed (aDom);
      assertEquals (aExpected, _describeDom (aDom));
      final String sXml = _serialise (aDom);
      assertEquals (sXml, aExpected, _describeXdm (new XdmParser ().parse (sXml)));
    });
  }

  /**
   * Appending the document element below a DOM element that binds the default namespace and the
   * prefixes <code>p</code> and <code>ns0</code> ({@link XdmToDom#appendTo(IXdmNode, Node, Node)})
   * gives a namespace well-formed DOM with the same expanded names.
   */
  @Test
  public void testAppendToBoundParent ()
  {
    new PropertyChecker ().setTries (300).forAll (_documents (), aDoc -> {
      final IXdmNode aRoot = aDoc.getChildAtIndex (0);
      final ICommonsList <String> aExpected = _describeXdm (aRoot);
      final Document aDom = XdmToDom.createEmptyDocument ();
      final Element aOuter = aDom.createElementNS ("urn:c", "outer");
      aOuter.setAttributeNS (XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", "urn:c");
      aOuter.setAttributeNS (XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:p", "urn:b");
      aOuter.setAttributeNS (XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ns0", "urn:a");
      aDom.appendChild (aOuter);
      XdmToDom.appendTo (aRoot, aOuter, null);
      _assertNamespaceWellFormed (aDom);
      assertEquals (aExpected, _describeDom (aOuter.getFirstChild ()));
    });
  }

  /**
   * A SAX handler that checks the prefix mappings of the events and builds the tree from them.
   */
  private static final class CheckingHandler extends DefaultHandler
  {
    private final XdmTreeBuilder m_aBuilder = new XdmTreeBuilder ();
    // Prefix to stack of URIs
    private final ICommonsMap <String, ArrayDeque <String>> m_aBindings = new CommonsHashMap <> ();
    // The prefixes mapped before each open element, and those mapped since the last start tag
    private final ArrayDeque <ICommonsList <String>> m_aOpen = new ArrayDeque <> ();
    private ICommonsList <String> m_aPending = new CommonsArrayList <> ();

    @Nullable
    private String _lookup (@NonNull final String sPrefix)
    {
      if (sPrefix.equals ("xml"))
        return XdmConstants.NS_XML;
      final ArrayDeque <String> aStack = m_aBindings.get (sPrefix);
      if (aStack == null || aStack.isEmpty ())
        return sPrefix.isEmpty () ? "" : null;
      return aStack.peek ();
    }

    @NonNull
    private static String _prefixOf (@NonNull final String sQName)
    {
      final int nColon = sQName.indexOf (':');
      return nColon < 0 ? "" : sQName.substring (0, nColon);
    }

    @Override
    public void startDocument () throws SAXException
    {
      m_aBuilder.startDocument ();
    }

    @Override
    public void endDocument () throws SAXException
    {
      m_aBuilder.endDocument ();
    }

    @Override
    public void startPrefixMapping (final String sPrefix, final String sUri) throws SAXException
    {
      assertFalse ("prefix " + sPrefix + " undeclared", !sPrefix.isEmpty () && sUri.isEmpty ());
      assertFalse ("prefix " + sPrefix + " mapped twice", m_aPending.contains (sPrefix));
      m_aBindings.computeIfAbsent (sPrefix, k -> new ArrayDeque <> ()).push (sUri);
      m_aPending.add (sPrefix);
      m_aBuilder.startPrefixMapping (sPrefix, sUri);
    }

    @Override
    public void endPrefixMapping (final String sPrefix) throws SAXException
    {
      m_aBuilder.endPrefixMapping (sPrefix);
    }

    @Override
    public void startElement (final String sUri,
                              final String sLocalName,
                              final String sQName,
                              final Attributes aAttrs) throws SAXException
    {
      assertEquals (sQName, sUri, _lookup (_prefixOf (sQName)));
      for (int i = 0; i < aAttrs.getLength (); ++i)
      {
        final String sAttrQName = aAttrs.getQName (i);
        final String sAttrUri = aAttrs.getURI (i);
        if (sAttrUri.isEmpty ())
          assertEquals (sAttrQName, "", _prefixOf (sAttrQName));
        else
        {
          assertFalse (sAttrQName + " has a namespace but no prefix", _prefixOf (sAttrQName).isEmpty ());
          assertEquals (sAttrQName, sAttrUri, _lookup (_prefixOf (sAttrQName)));
        }
      }
      m_aOpen.push (m_aPending);
      m_aPending = new CommonsArrayList <> ();
      m_aBuilder.startElement (sUri, sLocalName, sQName, aAttrs);
    }

    @Override
    public void endElement (final String sUri, final String sLocalName, final String sQName) throws SAXException
    {
      m_aBuilder.endElement (sUri, sLocalName, sQName);
      // The mappings of the element end after its end tag
      for (final String sPrefix : m_aOpen.pop ())
        m_aBindings.get (sPrefix).pop ();
    }

    @Override
    public void characters (final char [] aChars, final int nStart, final int nLength) throws SAXException
    {
      m_aBuilder.characters (aChars, nStart, nLength);
    }
  }

  /**
   * The SAX events of {@link XdmToSax#emit} map every prefix their names use to the namespace of
   * the name, and build a tree with the same expanded names, values and text.
   */
  @Test
  public void testEmitSax ()
  {
    new PropertyChecker ().setTries (300).forAll (_documents (), aDoc -> {
      final CheckingHandler aHandler = new CheckingHandler ();
      XdmToSax.emit (aDoc, aHandler, null);
      final IXdmNode aBuilt = aHandler.m_aBuilder.getDocument ();
      assertTrue (aHandler.m_aOpen.isEmpty ());
      assertEquals (_describeXdm (aDoc), _describeXdm (aBuilt));
    });
  }

  /**
   * @return <code>true</code> if the names of the element and its attributes fit its in-scope
   *         namespaces: every prefixed name is bound to its namespace or not bound at all, and an
   *         unprefixed element name is in the default namespace in scope (or in none without one).
   */
  private static boolean _isConsistent (@NonNull final IXdmNode aElement)
  {
    final Map <String, String> aInScope = aElement.getInScopeNamespaces ();
    final XdmQName aName = aElement.getName ();
    final String sElementBound = aInScope.get (aName.prefix ());
    if (aName.hasPrefix () ? sElementBound != null && !sElementBound.equals (aName.namespaceUri ())
                           : !_nonNull (sElementBound).equals (aName.namespaceUri ()))
      return false;
    for (int i = 0; i < aElement.getAttributeCount (); ++i)
    {
      final XdmQName aAttrName = aElement.getAttributeAtIndex (i).getName ();
      if (aAttrName.hasPrefix ())
      {
        final String sBound = aInScope.get (aAttrName.prefix ());
        if (sBound != null && !sBound.equals (aAttrName.namespaceUri ()))
          return false;
      }
    }
    return true;
  }

  /**
   * For every element whose names and those of its ancestors fit their in-scope namespaces, check
   * that the namespaces in scope in XDM are in scope for the corresponding element of the result,
   * given by the lookup function, and that an undeclared default namespace stays undeclared.
   */
  private static void _assertInScopeNamespacesKept (@NonNull final IXdmNode aXdmRoot,
                                                    @NonNull final Object aResultRoot,
                                                    @NonNull final IResultNavigator aNavigator)
  {
    final ArrayDeque <Object []> aStack = new ArrayDeque <> ();
    aStack.push (new Object [] { aXdmRoot, aResultRoot, Boolean.TRUE });
    while (!aStack.isEmpty ())
    {
      final Object [] aCur = aStack.pop ();
      final IXdmNode aXdm = (IXdmNode) aCur[0];
      final boolean bConsistent = ((Boolean) aCur[2]).booleanValue () && _isConsistent (aXdm);
      if (bConsistent)
      {
        final Map <String, String> aInScope = aXdm.getInScopeNamespaces ();
        for (final Map.Entry <String, String> aEntry : aInScope.entrySet ())
          assertEquals (aXdm.getName () + " " + aInScope + " prefix " + aEntry.getKey (),
                        aEntry.getValue (),
                        aNavigator.lookup (aCur[1], aEntry.getKey ()));
        if (!aInScope.containsKey (""))
          assertEquals (aXdm.getName () + " " + aInScope + " default", "", aNavigator.lookup (aCur[1], ""));
      }
      final ICommonsList <Object> aResultChildren = aNavigator.getChildElements (aCur[1]);
      int nIndex = 0;
      for (int i = 0; i < aXdm.getChildCount (); ++i)
        if (aXdm.getChildAtIndex (i).isElement ())
          aStack.push (new Object [] { aXdm.getChildAtIndex (i), aResultChildren.get (nIndex++), Boolean.valueOf (bConsistent) });
    }
  }

  /**
   * Where the names of an element and its ancestors fit their namespace declarations, the
   * namespaces in scope of the element (which content such as QName values may rely on) stay in
   * scope with the same URI in the DOM and in the tree built from the SAX events; an undeclared
   * default namespace stays undeclared.
   */
  @Test
  public void testInScopeNamespacesKept ()
  {
    new PropertyChecker ().setTries (300).forAll (_documents (), aDoc -> {
      final IXdmNode aRoot = aDoc.getChildAtIndex (0);
      final Document aDom = XdmToDom.toDocument (aDoc);
      _assertInScopeNamespacesKept (aRoot, aDom.getDocumentElement (), DOM_NAVIGATOR);
      final CheckingHandler aHandler = new CheckingHandler ();
      XdmToSax.emit (aDoc, aHandler, null);
      _assertInScopeNamespacesKept (aRoot, aHandler.m_aBuilder.getDocument ().getChildAtIndex (0), XDM_NAVIGATOR);
    });
  }
}

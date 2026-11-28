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

import java.util.ArrayDeque;
import java.util.Map;

import javax.xml.XMLConstants;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * Writes one SAX document piece by piece: complete nodes with their subtrees
 * ({@link #writeNode(IXdmNode)}) and elements whose start tag is written before
 * their content is known ({@link #startElement(IXdmNode)} and
 * {@link #endElement()}), so that a result can be passed on while it is built.
 * The events are those of {@link XdmToSax#emit(IXdmNode, ContentHandler, LexicalHandler)}
 * for the tree the pieces make up, which writes through this class: the
 * namespace scope spans all pieces, an element is outermost when no element is
 * open, and every walk is iterative.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XdmSaxWriter
{
  /** One step of the walk: a node to open, or (with close) an element to close. */
  @Immutable
  private record Step (@NonNull IXdmNode node, boolean close, @Nullable ICommonsList <String> prefixes)
  {}

  /** An element started by {@link XdmSaxWriter#startElement(IXdmNode)} and not ended yet. */
  @Immutable
  private record OpenElement (@NonNull XdmQName name, @NonNull ICommonsList <String> prefixes)
  {}

  private final ContentHandler m_aHandler;
  private final LexicalHandler m_aLexicalHandler;
  private final NamespaceScope m_aScope = new NamespaceScope (null);
  private final ArrayDeque <OpenElement> m_aOpen = new ArrayDeque <> ();

  /**
   * Create a writer for the given handlers.
   *
   * @param aHandler
   *        The receiver of the content events. May not be <code>null</code>.
   * @param aLexicalHandler
   *        The receiver of comments, may be <code>null</code> to drop them.
   */
  public XdmSaxWriter (@NonNull final ContentHandler aHandler, @Nullable final LexicalHandler aLexicalHandler)
  {
    ValueEnforcer.notNull (aHandler, "Handler");
    m_aHandler = aHandler;
    m_aLexicalHandler = aLexicalHandler;
  }

  @NonNull
  private static String _getSaxType (@NonNull final IXdmNode aAttr)
  {
    return switch (aAttr.getIDKind ())
    {
      case ID -> "ID";
      case IDREF -> "IDREF";
      case IDREFS -> "IDREFS";
      default -> "CDATA";
    };
  }

  /**
   * Report the start of an element: the prefix mappings it declares and needs, then the start tag,
   * and open a scope frame.
   *
   * @return The prefixes mapped, for the end of the element.
   */
  @NonNull
  private ICommonsList <String> _startElement (@NonNull final IXdmNode aElement, final boolean bOutermost) throws SAXException
  {
    final NamespaceScope aScope = m_aScope;
    final ICommonsOrderedMap <String, String> aDecls = new CommonsLinkedHashMap <> ();
    aScope.pushFrame ();
    if (bOutermost)
      aDecls.putAll (aElement.getInScopeNamespaces ());
    else
      if (aElement.hasDeclaredNamespaces ())
      {
        // Declarations that repeat the binding in scope are not reported
        for (final Map.Entry <String, String> aEntry : aElement.declaredNamespaces ().entrySet ())
          if (!aScope.isBoundTo (aEntry.getKey (), aEntry.getValue ()))
            aDecls.put (aEntry.getKey (), aEntry.getValue ());
      }
    aDecls.remove (XMLConstants.XML_NS_PREFIX);
    for (final Map.Entry <String, String> aEntry : aDecls.entrySet ())
      aScope.bind (aEntry.getKey (), aEntry.getValue ());

    final XdmQName aName = aElement.getName ();
    if (!XMLConstants.XML_NS_PREFIX.equals (aName.prefix ()) &&
        !aScope.isBoundTo (aName.prefix (), aName.namespaceUri ()))
    {
      aDecls.put (aName.prefix (), aName.namespaceUri ());
      aScope.bind (aName.prefix (), aName.namespaceUri ());
    }

    final AttributesImpl aAttrs = new AttributesImpl ();
    for (int i = 0; i < aElement.getAttributeCount (); ++i)
    {
      final IXdmNode aAttr = aElement.getAttributeAtIndex (i);
      final XdmQName aAttrName = aAttr.getName ();
      String sQName = aAttrName.getLexicalName ();
      if (aAttrName.hasNamespace () &&
          !XMLConstants.XML_NS_PREFIX.equals (aAttrName.prefix ()) &&
          (!aAttrName.hasPrefix () || !aScope.isBoundTo (aAttrName.prefix (), aAttrName.namespaceUri ())))
      {
        String sPrefix = aAttrName.prefix ();
        if (sPrefix.isEmpty () || aScope.lookup (sPrefix) != null)
          sPrefix = aScope.createPrefix (aDecls);
        aDecls.put (sPrefix, aAttrName.namespaceUri ());
        aScope.bind (sPrefix, aAttrName.namespaceUri ());
        sQName = sPrefix + ":" + aAttrName.localName ();
      }
      aAttrs.addAttribute (aAttrName.namespaceUri (),
                           aAttrName.localName (),
                           sQName,
                           _getSaxType (aAttr),
                           aAttr.getValue ());
    }

    for (final Map.Entry <String, String> aEntry : aDecls.entrySet ())
      m_aHandler.startPrefixMapping (aEntry.getKey (), aEntry.getValue ());
    m_aHandler.startElement (aName.namespaceUri (), aName.localName (), aName.getLexicalName (), aAttrs);
    return new CommonsArrayList <> (aDecls.keySet ());
  }

  /**
   * Report the end of an element: the end tag, then the end of the prefix mappings of its start,
   * and close its scope frame.
   */
  private void _endElement (@NonNull final XdmQName aName, @NonNull final ICommonsList <String> aPrefixes) throws SAXException
  {
    m_aHandler.endElement (aName.namespaceUri (), aName.localName (), aName.getLexicalName ());
    for (final String sPrefix : aPrefixes)
      m_aHandler.endPrefixMapping (sPrefix);
    m_aScope.popFrame ();
  }

  /**
   * Start the document: a locator without line numbers is passed to
   * <code>setDocumentLocator</code>, then <code>startDocument</code> is called.
   *
   * @param sSystemId
   *        The system ID of the locator, may be <code>null</code>.
   * @throws SAXException
   *         from the handler
   */
  public void startDocument (@Nullable final String sSystemId) throws SAXException
  {
    final LocatorImpl aLocator = new LocatorImpl ();
    aLocator.setSystemId (sSystemId);
    m_aHandler.setDocumentLocator (aLocator);
    m_aHandler.startDocument ();
  }

  /**
   * Write a complete node: the children of a document node, an element with its subtree, a text,
   * comment (to the lexical handler only) or processing instruction. An element is outermost if no
   * element is open: it then reports every namespace in scope on it, otherwise only the
   * declarations not bound the same way already.
   *
   * @param aNode
   *        The node. May not be <code>null</code>.
   * @throws SAXException
   *         from the handlers
   * @throws IllegalArgumentException
   *         for an attribute or namespace node
   */
  public void writeNode (@NonNull final IXdmNode aNode) throws SAXException
  {
    ValueEnforcer.notNull (aNode, "Node");
    if (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE)
      throw new IllegalArgumentException ("An attribute or namespace node cannot be written as SAX content");
    final ArrayDeque <Step> aStack = new ArrayDeque <> ();
    if (aNode.isDocument ())
    {
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (new Step (aNode.getChildAtIndex (i), false, null));
    }
    else
      aStack.push (new Step (aNode, false, null));

    int nOpen = m_aOpen.size ();
    while (!aStack.isEmpty ())
    {
      final Step aStep = aStack.pop ();
      final IXdmNode aCur = aStep.node ();
      if (aStep.close ())
      {
        _endElement (aCur.getName (), aStep.prefixes ());
        nOpen--;
        continue;
      }
      switch (aCur.getKind ())
      {
        case ELEMENT:
        {
          final ICommonsList <String> aPrefixes = _startElement (aCur, nOpen == 0);
          nOpen++;
          aStack.push (new Step (aCur, true, aPrefixes));
          for (int i = aCur.getChildCount () - 1; i >= 0; --i)
            aStack.push (new Step (aCur.getChildAtIndex (i), false, null));
          break;
        }
        case TEXT:
        {
          final char [] aChars = aCur.getValue ().toCharArray ();
          m_aHandler.characters (aChars, 0, aChars.length);
          break;
        }
        case COMMENT:
          if (m_aLexicalHandler != null)
          {
            final char [] aChars = aCur.getValue ().toCharArray ();
            m_aLexicalHandler.comment (aChars, 0, aChars.length);
          }
          break;
        case PROCESSING_INSTRUCTION:
          m_aHandler.processingInstruction (aCur.getName ().localName (), aCur.getValue ());
          break;
        default:
          break;
      }
    }
  }

  /**
   * Write the start tag of an element (prefix mappings, name and attributes as
   * {@link #writeNode(IXdmNode)} would), but not its children: they follow as separate pieces,
   * then {@link #endElement()}. The element must not change its name, attributes or namespaces
   * afterwards.
   *
   * @param aElement
   *        The element. May not be <code>null</code>.
   * @throws SAXException
   *         from the handler
   * @throws IllegalArgumentException
   *         if the node is no element
   */
  public void startElement (@NonNull final IXdmNode aElement) throws SAXException
  {
    ValueEnforcer.notNull (aElement, "Element");
    ValueEnforcer.isTrue (aElement.isElement (), "Only an element can be started");
    final ICommonsList <String> aPrefixes = _startElement (aElement, m_aOpen.isEmpty ());
    m_aOpen.push (new OpenElement (aElement.getName (), aPrefixes));
  }

  /**
   * Write the end tag of the innermost element started with {@link #startElement(IXdmNode)}.
   *
   * @throws SAXException
   *         from the handler
   * @throws IllegalStateException
   *         if no element is open
   */
  public void endElement () throws SAXException
  {
    final OpenElement aOpen = m_aOpen.poll ();
    if (aOpen == null)
      throw new IllegalStateException ("No element is open");
    _endElement (aOpen.name (), aOpen.prefixes ());
  }

  /**
   * End the document.
   *
   * @throws SAXException
   *         from the handler
   * @throws IllegalStateException
   *         if an element is still open
   */
  public void endDocument () throws SAXException
  {
    if (!m_aOpen.isEmpty ())
      throw new IllegalStateException (m_aOpen.size () + " elements are still open");
    m_aHandler.endDocument ();
  }
}

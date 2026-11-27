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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * Writes an XDM tree as namespace-aware SAX events, wrapped in
 * <code>startDocument</code>/<code>endDocument</code>: a document node with all its children
 * (comments and processing instructions outside the document element included), or an element,
 * text, comment or processing instruction as the only content. The walk is iterative.
 * <ul>
 * <li>Each element reports a <code>startPrefixMapping</code> for every namespace it declares that
 * is not bound the same way in scope already (the outermost elements: every namespace in scope
 * except <code>xml</code>) and for every binding its names need that is not in scope (namespace
 * fixup, a namespaced attribute without usable prefix gets a new one);
 * <code>endPrefixMapping</code> follows its end tag. No <code>xmlns</code> attributes are
 * reported.</li>
 * <li>Attributes have the type <code>ID</code>, <code>IDREF</code> or <code>IDREFS</code> according
 * to their ID kind, <code>CDATA</code> otherwise.</li>
 * <li>Comments go to the lexical handler, if one is given.</li>
 * <li>A locator with the document URI (or base URI) of the tree as system ID is passed to
 * <code>setDocumentLocator</code>; it has no line numbers.</li>
 * </ul>
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmToSax
{
  @PresentForCodeCoverage
  private static final XdmToSax INSTANCE = new XdmToSax ();

  private XdmToSax ()
  {}

  /**
   * Write an XDM node as a SAX document.
   *
   * @param aNode
   *        A document, element, text, comment or processing instruction node. May not be
   *        <code>null</code>.
   * @param aHandler
   *        The receiver of the content events. May not be <code>null</code>.
   * @param aLexicalHandler
   *        The receiver of comments, may be <code>null</code> to drop them.
   * @throws SAXException
   *         from the handlers
   * @throws IllegalArgumentException
   *         for an attribute or namespace node
   */
  public static void emit (@NonNull final IXdmNode aNode,
                           @NonNull final ContentHandler aHandler,
                           @Nullable final LexicalHandler aLexicalHandler) throws SAXException
  {
    ValueEnforcer.notNull (aNode, "Node");
    ValueEnforcer.notNull (aHandler, "Handler");
    if (aNode.isAttribute () || aNode.getKind () == ENodeKind.NAMESPACE)
      throw new IllegalArgumentException ("An attribute or namespace node cannot be written as a SAX document");
    final IXdmNode aRoot = aNode.getRoot ();
    final XdmSaxWriter aWriter = new XdmSaxWriter (aHandler, aLexicalHandler);
    aWriter.startDocument (aRoot.getDocumentUri () != null ? aRoot.getDocumentUri () : aRoot.getBaseUri ());
    aWriter.writeNode (aNode);
    aWriter.endDocument ();
  }
}

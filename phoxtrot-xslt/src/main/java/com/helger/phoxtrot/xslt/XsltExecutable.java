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
package com.helger.phoxtrot.xslt;

import java.io.OutputStream;
import java.io.Writer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xpath.context.StaticContext;

/**
 * A compiled stylesheet. Immutable and usable from several threads at once. Its
 * model is filled during compilation and not guarded afterwards, so it must be
 * handed to other threads safely (a <code>final</code> or <code>volatile</code>
 * field, a concurrent collection, an executor or the start of the thread);
 * through a plain field another thread may see it incomplete.
 *
 * @author Philip Helger
 */
public interface XsltExecutable
{
  /**
   * @param aRequest
   *        How to invoke the stylesheet.
   * @return The results.
   * @throws XsltException
   *         for dynamic errors (and XPath errors wrapped with their code),
   *         carrying the messages produced until then
   *         ({@link XsltException#getCollectedMessages()}).
   */
  @NonNull
  XsltTransformResult transform (@NonNull XsltTransformRequest aRequest);

  /**
   * Transform and write the serialized principal result to an output stream
   * instead of returning it, encoded with the encoding of the output
   * definition (UTF-16 is written big-endian with a byte order mark). Where
   * the unnamed xsl:output allows it (not schema-aware, an explicit method
   * that builds a tree, no xsl:result-document in the stylesheet, no indentation, item separator,
   * standalone or doctype-system declaration for xml and xhtml, no
   * Content-Type meta element for html and xhtml, no character map or
   * normalisation for text), the result is also written while it is built,
   * so that it never exists as a whole; otherwise it is built in full and
   * then written. The output is the same either way.
   *
   * @param aRequest
   *        How to invoke the stylesheet. Raw delivery does not apply: the
   *        result is always serialized.
   * @param aOS
   *        The target. It is flushed, not closed.
   * @return The results, with an empty principal result, no principal tree
   *         and an empty serialized form; the secondary results and messages
   *         as with {@link #transform(XsltTransformRequest)}.
   * @throws XsltException
   *         for dynamic errors (and XPath and serialization errors wrapped
   *         with their code), carrying the messages produced until then.
   *         Part of the output may have been written then.
   * @throws UnsupportedOperationException
   *         if this implementation does not support it
   */
  @NonNull
  default XsltTransformResult transform (@NonNull final XsltTransformRequest aRequest, @NonNull final OutputStream aOS)
  {
    throw new UnsupportedOperationException ("Writing to a stream is not supported by " + getClass ().getName ());
  }

  /**
   * Transform and write the serialized principal result to a writer instead
   * of returning it: the same characters as
   * {@link XsltTransformResult#principalSerialized()} of
   * {@link #transform(XsltTransformRequest)}. The encoding of the output
   * definition only decides which characters are written as character
   * references. See {@link #transform(XsltTransformRequest, OutputStream)}
   * for when the result is written while it is built.
   *
   * @param aRequest
   *        How to invoke the stylesheet. Raw delivery does not apply: the
   *        result is always serialized.
   * @param aWriter
   *        The target. It is flushed, not closed.
   * @return The results, with an empty principal result, no principal tree
   *         and an empty serialized form; the secondary results and messages
   *         as with {@link #transform(XsltTransformRequest)}.
   * @throws XsltException
   *         for dynamic errors (and XPath and serialization errors wrapped
   *         with their code), carrying the messages produced until then.
   *         Part of the output may have been written then.
   * @throws UnsupportedOperationException
   *         if this implementation does not support it
   */
  @NonNull
  default XsltTransformResult transform (@NonNull final XsltTransformRequest aRequest, @NonNull final Writer aWriter)
  {
    throw new UnsupportedOperationException ("Writing to a writer is not supported by " + getClass ().getName ());
  }

  /**
   * Transform and pass the principal result tree to SAX handlers instead of
   * returning it, with the events of
   * {@link com.helger.phoxtrot.xdm.bridge.XdmToSax#emit(com.helger.phoxtrot.xdm.IXdmNode, ContentHandler, LexicalHandler)}
   * for the tree. Where the stylesheet allows it (not schema-aware, no
   * xsl:result-document in the stylesheet, an unnamed xsl:output that builds
   * a tree), the events are passed on while the result is built, so that it
   * never exists as a whole; otherwise the tree is built in full and then
   * passed on. The events are the same either way. A {@link SAXException} of
   * a handler stops the transformation at once.
   *
   * @param aRequest
   *        How to invoke the stylesheet. Raw delivery does not apply. Its base
   *        output URI is the system ID of the locator.
   * @param aHandler
   *        The receiver of the content events. May not be <code>null</code>.
   * @param aLexicalHandler
   *        The receiver of comments, may be <code>null</code> to drop them.
   * @return The results, with an empty principal result, no principal tree
   *         and an empty serialized form; the secondary results and messages
   *         as with {@link #transform(XsltTransformRequest)}.
   * @throws SAXException
   *         from a handler, as it was thrown
   * @throws XsltException
   *         for dynamic errors (and XPath errors wrapped with their code),
   *         carrying the messages produced until then. Part of the result may
   *         have been passed on then.
   * @throws IllegalStateException
   *         if the principal result is no tree (build-tree is "no", or the
   *         method is json or adaptive)
   * @throws UnsupportedOperationException
   *         if this implementation does not support it
   */
  @NonNull
  default XsltTransformResult transform (@NonNull final XsltTransformRequest aRequest,
                                         @NonNull final ContentHandler aHandler,
                                         @Nullable final LexicalHandler aLexicalHandler) throws SAXException
  {
    throw new UnsupportedOperationException ("Passing the result to SAX handlers is not supported by " +
                                             getClass ().getName ());
  }

  /**
   * Create the static context for standalone XPath expressions and patterns of
   * a host (e.g. a Schematron executor) that use the declarations of this
   * stylesheet: they see what an expression written on the principal
   * stylesheet element sees, i.e. its namespaces, base URI,
   * xpath-default-namespace and default collation, the stylesheet functions
   * (xsl:function) together with the XSLT functions such as
   * <code>current()</code> and <code>key()</code>, the global variables and
   * parameters, and the decimal formats. The host may declare further
   * namespaces and variables on the returned object. Its function library is
   * the frozen one of the stylesheet (see
   * {@link com.helger.phoxtrot.xpath.fn.FunctionLibrary#freeze()}), so
   * registering into it throws.
   *
   * @return A new static context. Never <code>null</code>.
   * @throws UnsupportedOperationException
   *         if this implementation does not support host expressions
   */
  @NonNull
  default StaticContext createStaticContext ()
  {
    throw new UnsupportedOperationException ("Host expressions are not supported by " + getClass ().getName ());
  }

  /**
   * Prepare the evaluation of expressions compiled against
   * {@link #createStaticContext()} for one source document, set up like a
   * transformation with the same request: global context item (with
   * whitespace stripping), global variables and parameters evaluated on first
   * use, keys, and the resolvers and limits of the request.
   *
   * @param aRequest
   *        The request. Only the global context item, the stylesheet
   *        parameters, the resolvers and the limits are used.
   * @return A new evaluation, for one thread at a time. Never
   *         <code>null</code>.
   * @throws XsltException
   *         if the global context item does not fit the stylesheet or a
   *         required parameter is missing
   * @throws UnsupportedOperationException
   *         if this implementation does not support host expressions
   */
  @NonNull
  default XsltEvaluation createEvaluation (@NonNull final XsltTransformRequest aRequest)
  {
    throw new UnsupportedOperationException ("Host expressions are not supported by " + getClass ().getName ());
  }

  /**
   * @return The in-scope schema definitions of a schema-aware stylesheet (to
   *         validate source documents against the same components the
   *         stylesheet refers to), <code>null</code> for a basic processor.
   */
  @Nullable
  default IXdmSchemaSet getSchemaSet ()
  {
    return null;
  }

  /**
   * @return <code>true</code> if the stylesheet says
   *         <code>input-type-annotations="strip"</code>: type annotations of
   *         source documents are to be removed.
   */
  default boolean isStripInputTypeAnnotations ()
  {
    return false;
  }
}

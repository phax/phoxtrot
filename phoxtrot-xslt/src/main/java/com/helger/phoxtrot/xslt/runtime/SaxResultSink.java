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
package com.helger.phoxtrot.xslt.runtime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.bridge.XdmSaxWriter;

/**
 * Passes the principal result to a SAX content handler while it is built
 * (through {@link XdmSaxWriter}, with the events
 * {@link com.helger.phoxtrot.xdm.bridge.XdmToSax} gives for the complete tree).
 * A {@link SAXException} of the handler stops the transformation at once: it
 * is carried out of the engine as {@link HandlerException}, which no
 * <code>xsl:try</code> catches.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class SaxResultSink implements IResultDrainSink
{
  /** Carries an exception of the SAX handler out of the transformation. */
  static final class HandlerException extends RuntimeException
  {
    HandlerException (@NonNull final SAXException aCause)
    {
      super (aCause);
    }

    @NonNull
    SAXException getSAXException ()
    {
      return (SAXException) getCause ();
    }
  }

  private final XdmSaxWriter m_aWriter;

  /**
   * Create the sink and start the document.
   *
   * @param aHandler
   *        The content handler.
   * @param aLexicalHandler
   *        The receiver of comments, may be <code>null</code>.
   * @param sSystemId
   *        The system ID of the locator, may be <code>null</code>.
   * @throws HandlerException
   *         if the handler fails
   */
  SaxResultSink (@NonNull final ContentHandler aHandler,
                 @Nullable final LexicalHandler aLexicalHandler,
                 @Nullable final String sSystemId)
  {
    m_aWriter = new XdmSaxWriter (aHandler, aLexicalHandler);
    try
    {
      m_aWriter.startDocument (sSystemId);
    }
    catch (final SAXException ex)
    {
      throw new HandlerException (ex);
    }
  }

  public void writeNode (@NonNull final IXdmNode aNode)
  {
    try
    {
      m_aWriter.writeNode (aNode);
    }
    catch (final SAXException ex)
    {
      throw new HandlerException (ex);
    }
  }

  public void startElement (@NonNull final IXdmNode aElement)
  {
    try
    {
      m_aWriter.startElement (aElement);
    }
    catch (final SAXException ex)
    {
      throw new HandlerException (ex);
    }
  }

  public void endElement ()
  {
    try
    {
      m_aWriter.endElement ();
    }
    catch (final SAXException ex)
    {
      throw new HandlerException (ex);
    }
  }

  public void finish ()
  {
    try
    {
      m_aWriter.endDocument ();
    }
    catch (final SAXException ex)
    {
      throw new HandlerException (ex);
    }
  }

  public void flush ()
  {
    // The events were passed on as they came
  }
}

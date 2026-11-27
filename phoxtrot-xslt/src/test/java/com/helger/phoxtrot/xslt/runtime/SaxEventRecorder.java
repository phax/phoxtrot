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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import com.helger.annotation.concurrent.NotThreadSafe;

/**
 * Records every SAX event as text, with all details the tests compare
 * (namespace URI, local and qualified names, attribute types, the system ID of
 * the locator). Adjacent character events are recorded as one, as SAX allows
 * text to be split anywhere. Optionally throws at the start of an element with
 * a given local name, and logs element starts into a list shared with other
 * listeners.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class SaxEventRecorder extends DefaultHandler2
{
  private final StringBuilder m_aSB = new StringBuilder ();
  private final String m_sFailAt;
  private boolean m_bInText;
  private int m_nStartElementCount;
  private List <String> m_aStartLog;

  public SaxEventRecorder ()
  {
    this (null);
  }

  /**
   * @param sFailAt
   *        The local name of the element whose start throws a
   *        {@link SAXException}, or <code>null</code>.
   */
  public SaxEventRecorder (@Nullable final String sFailAt)
  {
    m_sFailAt = sFailAt;
  }

  /**
   * @param aStartLog
   *        The list that gets "start" and the local name of each element start,
   *        or <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SaxEventRecorder setStartLog (@Nullable final List <String> aStartLog)
  {
    m_aStartLog = aStartLog;
    return this;
  }

  @NonNull
  private StringBuilder _event ()
  {
    m_bInText = false;
    return m_aSB;
  }

  @Override
  public void setDocumentLocator (final Locator aLocator)
  {
    _event ().append ("[locator ").append (aLocator.getSystemId ()).append (']');
  }

  @Override
  public void startDocument ()
  {
    _event ().append ("[doc]");
  }

  @Override
  public void endDocument ()
  {
    _event ().append ("[/doc]");
  }

  @Override
  public void startPrefixMapping (final String sPrefix, final String sUri)
  {
    _event ().append ("[ns ").append (sPrefix).append ('=').append (sUri).append (']');
  }

  @Override
  public void endPrefixMapping (final String sPrefix)
  {
    _event ().append ("[/ns ").append (sPrefix).append (']');
  }

  @Override
  public void startElement (final String sUri, final String sLocal, final String sQName, final Attributes aAttrs) throws SAXException
  {
    m_nStartElementCount++;
    if (m_aStartLog != null)
      m_aStartLog.add ("start " + sLocal);
    if (sLocal.equals (m_sFailAt))
      throw new SAXException ("Stopped at " + sLocal);
    final StringBuilder aSB = _event ();
    aSB.append ("<{").append (sUri).append ('}').append (sLocal).append ('|').append (sQName);
    for (int i = 0; i < aAttrs.getLength (); ++i)
      aSB.append (' ')
         .append ('{')
         .append (aAttrs.getURI (i))
         .append ('}')
         .append (aAttrs.getLocalName (i))
         .append ('|')
         .append (aAttrs.getQName (i))
         .append ('(')
         .append (aAttrs.getType (i))
         .append (")=")
         .append (aAttrs.getValue (i));
    aSB.append ('>');
  }

  @Override
  public void endElement (final String sUri, final String sLocal, final String sQName)
  {
    _event ().append ("</{").append (sUri).append ('}').append (sLocal).append ('|').append (sQName).append ('>');
  }

  @Override
  public void characters (final char [] aChars, final int nStart, final int nLength)
  {
    if (!m_bInText)
      m_aSB.append ("[t]");
    m_bInText = true;
    m_aSB.append (aChars, nStart, nLength);
  }

  @Override
  public void comment (final char [] aChars, final int nStart, final int nLength)
  {
    _event ().append ("<!--").append (aChars, nStart, nLength).append ("-->");
  }

  @Override
  public void processingInstruction (final String sTarget, final String sData)
  {
    _event ().append ("<?").append (sTarget).append (' ').append (sData).append ("?>");
  }

  /** @return The number of startElement calls so far, including a failing one. */
  public int getStartElementCount ()
  {
    return m_nStartElementCount;
  }

  @Override
  public String toString ()
  {
    return m_aSB.toString ();
  }
}

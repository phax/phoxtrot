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
package com.helger.phoxtrot.xslt.conformance;

import org.jspecify.annotations.NonNull;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.ext.DefaultHandler2;

import com.helger.annotation.concurrent.NotThreadSafe;

/**
 * Records the SAX events of a result as text with every detail (namespace URI,
 * local and qualified names, attribute types, prefix mappings, the system ID
 * of the locator), for the comparison of the writeTo rerun. Adjacent character
 * events are recorded as one, as SAX allows text to be split anywhere.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class SaxEventText extends DefaultHandler2
{
  private final StringBuilder m_aSB = new StringBuilder ();
  private boolean m_bInText;

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
  public void startElement (final String sUri, final String sLocal, final String sQName, final Attributes aAttrs)
  {
    final StringBuilder aSB = _event ();
    aSB.append ("<{").append (sUri).append ('}').append (sLocal).append ('|').append (sQName);
    for (int i = 0; i < aAttrs.getLength (); ++i)
      aSB.append (" {")
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

  @Override
  public String toString ()
  {
    return m_aSB.toString ();
  }
}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.bridge.XdmToSax;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;
import com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory;

/**
 * Passing the principal result to SAX handlers
 * ({@link XsltExecutable#transform(XsltTransformRequest, ContentHandler, LexicalHandler)}
 * and the JAXP <code>SAXResult</code>): the events arrive while the result is
 * built, an exception of a handler stops the run, and stylesheets that do not
 * allow it get the same events from the complete tree.
 *
 * @author Philip Helger
 */
public final class SaxResultTest
{
  /** An xsl:message between two elements: the first is passed on before it. */
  private static final String MESSAGE_SHEET = XsltTestHelper.SHEET +
                                              "<xsl:output method='xml'/>" +
                                              "<xsl:template name='xsl:initial-template'>" +
                                              "<out xmlns:p='urn:p'><a p:k='1'/>" +
                                              "<xsl:message>between</xsl:message>" +
                                              "<stop/><b/></out><xsl:message>after</xsl:message>" +
                                              "</xsl:template>" +
                                              XsltTestHelper.END;

  @NonNull
  private static String _treeEvents (@NonNull final XsltExecutable aExec) throws SAXException
  {
    final SaxEventRecorder aRecorder = new SaxEventRecorder ();
    XdmToSax.emit (aExec.transform (new XsltTransformRequest ().setBaseOutputUri ("urn:out")).principalTree (),
                   aRecorder,
                   aRecorder);
    return aRecorder.toString ();
  }

  @Test
  public void testEventsBeforeTheRunEnds () throws SAXException
  {
    final XsltExecutable aExec = XsltCompiler.compile (MESSAGE_SHEET, new XsltCompileOptions ());
    final ICommonsList <String> aLog = new CommonsArrayList <> ();
    final SaxEventRecorder aRecorder = new SaxEventRecorder ().setStartLog (aLog);
    final long nDrained = ResultDrain.getDrainedRunCount ();
    final XsltTransformResult aResult = aExec.transform (new XsltTransformRequest ().setBaseOutputUri ("urn:out")
                                                                                     .setMessageListener (aMsg -> aLog.add ("message " +
                                                                                                                            aMsg.text ())),
                                                         aRecorder,
                                                         aRecorder);
    assertEquals (nDrained + 1, ResultDrain.getDrainedRunCount ());
    // The start tag of out was passed on when its first child arrived, before the message
    assertEquals ("start out", aLog.getFirstOrNull ());
    assertTrue (aLog.toString (), aLog.indexOf ("message between") < aLog.indexOf ("start stop"));
    assertEquals ("message after", aLog.getLastOrNull ());
    // The same events as the complete tree gives
    assertEquals (_treeEvents (aExec), aRecorder.toString ());
    assertTrue (aRecorder.toString (), aRecorder.toString ().startsWith ("[locator urn:out][doc]"));
    // The principal result went to the handler only
    assertTrue (aResult.principalRaw ().isEmpty ());
    assertNull (aResult.principalTree ());
    assertEquals (2, aResult.messages ().size ());
  }

  @Test
  public void testHandlerExceptionStopsTheRun ()
  {
    final XsltExecutable aExec = XsltCompiler.compile (MESSAGE_SHEET, new XsltCompileOptions ());
    final ICommonsList <String> aMessages = new CommonsArrayList <> ();
    final SaxEventRecorder aRecorder = new SaxEventRecorder ("stop");
    try
    {
      aExec.transform (new XsltTransformRequest ().setMessageListener (aMsg -> aMessages.add (aMsg.text ())),
                       aRecorder,
                       aRecorder);
      fail ();
    }
    catch (final SAXException ex)
    {
      assertEquals ("Stopped at stop", ex.getMessage ());
    }
    // The message after the failing element was never produced
    assertEquals ("[between]", aMessages.toString ());
    assertEquals (3, aRecorder.getStartElementCount ());
  }

  @Test
  public void testFallbackToTheTree () throws SAXException
  {
    // An xsl:result-document anywhere, and an output method that does not
    // build a tree by default but is told to
    for (final String sDecl : new String [] { "<xsl:template name='unused'><xsl:result-document><x/></xsl:result-document></xsl:template>",
                                              "<xsl:output method='json' build-tree='yes'/>" })
    {
      final String sSheet = XsltTestHelper.SHEET +
                            sDecl +
                            "<xsl:template name='xsl:initial-template'><r xmlns:q='urn:q'><q:e/>t<!--c--></r></xsl:template>" +
                            XsltTestHelper.END;
      final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
      final SaxEventRecorder aRecorder = new SaxEventRecorder ();
      final long nDrained = ResultDrain.getDrainedRunCount ();
      aExec.transform (new XsltTransformRequest ().setBaseOutputUri ("urn:out"), aRecorder, aRecorder);
      if (sDecl.contains ("result-document"))
        assertEquals (nDrained, ResultDrain.getDrainedRunCount ());
      assertEquals (sSheet, _treeEvents (aExec), aRecorder.toString ());
    }

    // No tree at all
    final XsltExecutable aNoTree = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                         "<xsl:output method='xml' build-tree='no'/>" +
                                                         "<xsl:template name='xsl:initial-template'><xsl:sequence select='1, 2'/></xsl:template>" +
                                                         XsltTestHelper.END,
                                                         new XsltCompileOptions ());
    try
    {
      aNoTree.transform (new XsltTransformRequest (), new SaxEventRecorder (), null);
      fail ();
    }
    catch (final IllegalStateException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("The principal result is no tree"));
    }
  }

  @NonNull
  private static Templates _templates (@NonNull final String sSheet) throws TransformerException
  {
    return new PhoxtrotTransformerFactory ().newTemplates (new StreamSource (new StringReader (sSheet)));
  }

  @Test
  public void testJaxpSaxResult () throws Exception
  {
    final String sSheet = XsltTestHelper.SHEET +
                          "<xsl:output method='xml'/>" +
                          "<xsl:template match='/'><out xmlns:p='urn:p'><stop/><b/></out></xsl:template>" +
                          XsltTestHelper.END;
    // Events while the result is built
    final SaxEventRecorder aRecorder = new SaxEventRecorder ();
    final long nDrained = ResultDrain.getDrainedRunCount ();
    _templates (sSheet.replace ("<stop/>", "<a/>")).newTransformer ()
                                                   .transform (new StreamSource (new StringReader ("<x/>")),
                                                               new SAXResult (aRecorder));
    assertEquals (nDrained + 1, ResultDrain.getDrainedRunCount ());
    assertEquals ("[locator null][doc][ns xs=http://www.w3.org/2001/XMLSchema][ns p=urn:p]<{}out|out><{}a|a></{}a|a><{}b|b></{}b|b></{}out|out>[/ns xs][/ns p][/doc]",
                  aRecorder.toString ());

    // A handler exception is the cause of the TransformerException
    final SaxEventRecorder aFailing = new SaxEventRecorder ("stop");
    try
    {
      _templates (sSheet).newTransformer ()
                         .transform (new StreamSource (new StringReader ("<x/>")), new SAXResult (aFailing));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getCause () instanceof SAXException);
      assertEquals ("Stopped at stop", ex.getCause ().getMessage ());
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("The SAXResult handler failed"));
    }
    assertEquals (2, aFailing.getStartElementCount ());

    // No tree
    try
    {
      _templates (XsltTestHelper.SHEET +
                  "<xsl:output method='json'/>" +
                  "<xsl:template match='/'><xsl:sequence select='map{}'/></xsl:template>" +
                  XsltTestHelper.END).newTransformer ()
                                     .transform (new StreamSource (new StringReader ("<x/>")),
                                                 new SAXResult (new SaxEventRecorder ()));
      fail ();
    }
    catch (final TransformerException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("The principal result is no tree"));
    }
  }

  @Test
  public void testSameHandlerTwice () throws SAXException
  {
    // Each run starts its own document and namespace scope
    final XsltExecutable aExec = XsltCompiler.compile (MESSAGE_SHEET, new XsltCompileOptions ());
    final SaxEventRecorder aOnce = new SaxEventRecorder ();
    aExec.transform (new XsltTransformRequest (), aOnce, aOnce);
    final SaxEventRecorder aTwice = new SaxEventRecorder ();
    aExec.transform (new XsltTransformRequest (), aTwice, aTwice);
    aExec.transform (new XsltTransformRequest (), aTwice, aTwice);
    assertEquals (aOnce.toString () + aOnce.toString (), aTwice.toString ());
  }
}

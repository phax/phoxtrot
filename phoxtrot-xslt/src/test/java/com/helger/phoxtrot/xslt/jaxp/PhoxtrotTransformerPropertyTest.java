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
package com.helger.phoxtrot.xslt.jaxp;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the JAXP adapter ({@link PhoxtrotTransformer}): on
 * generated documents ({@link RandomXmlDocument}), a transformation through
 * JAXP gives the result of the native API, for a stream source and for a DOM
 * source, and a DOM result holds the nodes of the source.
 *
 * @author Philip Helger
 */
public final class PhoxtrotTransformerPropertyTest
{
  private static final String IDENTITY = XsltTestHelper.SHEET +
                                         "<xsl:output method='xml'/>" +
                                         "<xsl:mode on-no-match='shallow-copy'/>" +
                                         XsltTestHelper.END;
  /** A text summary of every node */
  private static final String SUMMARY = XsltTestHelper.SHEET +
                                        "<xsl:output method='text'/>" +
                                        "<xsl:template match='/'>" +
                                        "<xsl:for-each select='//node() | //@*'>" +
                                        "<xsl:value-of select='node-name(.), namespace-uri(.), string-length(string(.)), path(.)' separator='~'/>|" +
                                        "</xsl:for-each>" +
                                        "</xsl:template>" +
                                        XsltTestHelper.END;

  private static final Templates IDENTITY_TEMPLATES = _templates (IDENTITY);
  private static final Templates SUMMARY_TEMPLATES = _templates (SUMMARY);
  private static final XsltExecutable IDENTITY_NATIVE = XsltCompiler.compile (IDENTITY, new XsltCompileOptions ());
  private static final XsltExecutable SUMMARY_NATIVE = XsltCompiler.compile (SUMMARY, new XsltCompileOptions ());

  @NonNull
  private static Templates _templates (@NonNull final String sSheet)
  {
    try
    {
      return new PhoxtrotTransformerFactory ().newTemplates (new StreamSource (new StringReader (sSheet)));
    }
    catch (final Exception ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  @NonNull
  private static String _jaxp (@NonNull final Templates aTemplates, @NonNull final Source aSource) throws Exception
  {
    final StringWriter aSW = new StringWriter ();
    aTemplates.newTransformer ().transform (aSource, new StreamResult (aSW));
    return aSW.toString ();
  }

  @NonNull
  private static String _native (@NonNull final XsltExecutable aExec, @NonNull final String sSource)
  {
    return aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                .principalSerialized ();
  }

  @NonNull
  private static List <String> _sorted (@NonNull final String sSummary)
  {
    final List <String> ret = new ArrayList <> (Arrays.asList (sSummary.split ("\\|")));
    Collections.sort (ret);
    return ret;
  }

  /**
   * The identity transformation and a summary of every node give the same
   * characters through JAXP, from a stream source and from a DOM source, as
   * through the native API. From a DOM source the attributes may come in
   * another order.
   */
  @Test
  public void testJaxpMatchesNative ()
  {
    new PropertyChecker ().setTries (1000).forAll (RandomXmlDocument.DOCUMENTS, aDoc -> {
      final String sSource = aDoc.toString ();
      final String sIdentity = _native (IDENTITY_NATIVE, sSource);
      assertEquals (sIdentity, _jaxp (IDENTITY_TEMPLATES, new StreamSource (new StringReader (sSource))));
      // The JDK DOM keeps attributes in another order, which XDM leaves implementation-dependent
      assertEquals (RandomXmlDocument.getCanonical (RandomXmlDocument.parseWithJdk (sIdentity)),
                    RandomXmlDocument.getCanonical (RandomXmlDocument.parseWithJdk (_jaxp (IDENTITY_TEMPLATES,
                                                                                         new DOMSource (RandomXmlDocument.parseWithJdk (sSource))))));

      final String sSummary = _native (SUMMARY_NATIVE, sSource);
      assertEquals (sSummary, _jaxp (SUMMARY_TEMPLATES, new StreamSource (new StringReader (sSource))));
      assertEquals (_sorted (sSummary), _sorted (_jaxp (SUMMARY_TEMPLATES, new DOMSource (RandomXmlDocument.parseWithJdk (sSource)))));
    });
  }

  /**
   * The identity transformation into a DOM result has the expanded names,
   * attributes, text, comments and processing instructions of the source.
   */
  @Test
  public void testDomResultKeepsTheDocument ()
  {
    new PropertyChecker ().setTries (1000).forAll (RandomXmlDocument.DOCUMENTS, aDoc -> {
      final DOMResult aResult = new DOMResult ();
      IDENTITY_TEMPLATES.newTransformer ().transform (new StreamSource (new StringReader (aDoc.toString ())), aResult);
      assertEquals (aDoc.toString (), aDoc.getCanonical (), RandomXmlDocument.getCanonical (aResult.getNode ()));
    });
  }
}

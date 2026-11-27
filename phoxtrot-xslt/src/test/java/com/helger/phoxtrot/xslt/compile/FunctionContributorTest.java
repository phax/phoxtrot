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
package com.helger.phoxtrot.xslt.compile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.function.Consumer;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.junit.BeforeClass;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory;

/**
 * Extension functions contributed per compilation
 * ({@link XsltCompileOptions#addFunctionContributor(Consumer)}) and per JAXP factory
 * ({@link PhoxtrotTransformerFactory#ATTR_FUNCTION_CONTRIBUTOR}): visible in the stylesheets of
 * that compilation or factory (calls, <code>function-available</code>, <code>use-when</code>,
 * nested <code>fn:transform</code>), unknown in others; globally registered functions stay
 * available to both.
 *
 * @author Philip Helger
 */
public final class FunctionContributorTest
{
  private static final String NS = "urn:phoxtrot:test:function-contributor";
  private static final String NS_GLOBAL = "urn:phoxtrot:test:function-contributor-global";
  private static final String HEAD = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                                     " xmlns:p='" +
                                     NS +
                                     "' xmlns:g='" +
                                     NS_GLOBAL +
                                     "'><xsl:output method='text'/>";
  private static final String END = "</xsl:stylesheet>";
  private static final Consumer <FunctionLibrary> CONTRIBUTOR = aLib -> aLib.register (NS,
                                                                                    "twice",
                                                                                    "(xs:integer) as xs:integer",
                                                                                    (aCtx, aArgs) -> _times (aArgs, 2));
  /** Reports what the stylesheet sees: use-when, function-available and the global function. */
  private static final String PROBE = HEAD +
                                      "<xsl:variable name='v' select='\"yes\"' use-when=\"function-available('p:twice', 1)\"/>" +
                                      "<xsl:variable name='v' select='\"no\"' use-when=\"not(function-available('p:twice', 1))\"/>" +
                                      "<xsl:template match='/' name='xsl:initial-template'>" +
                                      "<xsl:value-of select=\"$v, function-available('p:twice', 1), g:thrice(2)\"/>" +
                                      "</xsl:template>" +
                                      END;

  @NonNull
  private static XdmSequence _times (@NonNull final List <XdmSequence> aArgs, final long nFactor)
  {
    final long nValue = ((XdmAtomic) aArgs.get (0).get (0)).getBigInteger ().longValue ();
    return XdmSequence.of (XdmAtomic.integer (nValue * nFactor));
  }

  @BeforeClass
  public static void registerGlobalFunction ()
  {
    // The registry is shared by the JVM: the function lives in a namespace of this test only
    XsltExtensionRegistry.getInstance ()
                         .registerFunctions (aLib -> aLib.register (NS_GLOBAL,
                                                                    "thrice",
                                                                    "(xs:integer) as xs:integer",
                                                                    (aCtx, aArgs) -> _times (aArgs, 3)));
  }

  @NonNull
  private static String _run (@NonNull final String sSheet, @NonNull final XsltCompileOptions aOptions)
  {
    return XsltCompiler.compile (sSheet, aOptions).transform (new XsltTransformRequest ()).principalSerialized ();
  }

  @Test
  public void testCompileOptions ()
  {
    final XsltCompileOptions aWith = new XsltCompileOptions ().addFunctionContributor (CONTRIBUTOR);
    assertEquals ("yes true 6", _run (PROBE, aWith));
    assertEquals ("no false 6", _run (PROBE, new XsltCompileOptions ()));

    // A call and a static variable
    final String sCall = HEAD +
                         "<xsl:variable name='s' static='yes' select='p:twice(5)'/>" +
                         "<xsl:template name='xsl:initial-template'><xsl:value-of select='p:twice(21), $s'/></xsl:template>" +
                         END;
    assertEquals ("42 10", _run (sCall, aWith));
    try
    {
      _run (sCall, new XsltCompileOptions ());
      fail ();
    }
    catch (final XPathException ex)
    {
      assertEquals ("XPST0017", ex.getCode ());
    }

    // Visible to xsl:evaluate, which uses the functions of the stylesheet
    assertEquals ("8",
                  _run (HEAD +
                        "<xsl:template name='xsl:initial-template'><xsl:evaluate xpath=\"'p:twice(4)'\"/></xsl:template>" +
                        END,
                        aWith));
  }

  @Test
  public void testInheritedByFnTransform ()
  {
    // The nested stylesheet is compiled by the same processor and sees the same functions
    final String sNested = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:p='" +
                           NS +
                           "'><xsl:template name='xsl:initial-template'><xsl:sequence select='p:twice(7)'/></xsl:template></xsl:stylesheet>";
    final String sSheet = HEAD +
                          "<xsl:param name='nested'/>" +
                          "<xsl:template name='xsl:initial-template'>" +
                          "<xsl:value-of select=\"transform(map{'stylesheet-text': $nested, 'delivery-format': 'raw'})?output\"/>" +
                          "</xsl:template>" +
                          END;
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.params ()
            .put (new XdmQName ("nested"), XdmSequence.of (XdmAtomic.string (sNested)));
    assertEquals ("14",
                  XsltCompiler.compile (sSheet, new XsltCompileOptions ().addFunctionContributor (CONTRIBUTOR))
                              .transform (aRequest)
                              .principalSerialized ());
  }

  @NonNull
  private static String _jaxp (@NonNull final PhoxtrotTransformerFactory aTF) throws Exception
  {
    final Templates aTemplates = aTF.newTemplates (new StreamSource (new StringReader (PROBE)));
    final StringWriter aSW = new StringWriter ();
    aTemplates.newTransformer ().transform (new StreamSource (new StringReader ("<a/>")), new StreamResult (aSW));
    return aSW.toString ();
  }

  @Test
  public void testFactoryAttribute () throws Exception
  {
    final PhoxtrotTransformerFactory aWith = new PhoxtrotTransformerFactory ();
    assertNull (aWith.getAttribute (PhoxtrotTransformerFactory.ATTR_FUNCTION_CONTRIBUTOR));
    aWith.setAttribute (PhoxtrotTransformerFactory.ATTR_FUNCTION_CONTRIBUTOR, CONTRIBUTOR);
    assertSame (CONTRIBUTOR, aWith.getAttribute (PhoxtrotTransformerFactory.ATTR_FUNCTION_CONTRIBUTOR));
    final PhoxtrotTransformerFactory aWithout = new PhoxtrotTransformerFactory ();

    assertEquals ("yes true 6", _jaxp (aWith));
    assertEquals ("no false 6", _jaxp (aWithout));
    // Again in the other order: nothing leaks between the factories
    assertEquals ("no false 6", _jaxp (aWithout));
    assertEquals ("yes true 6", _jaxp (aWith));

    // A call compiles only on the factory with the contributor
    final String sCall = HEAD + "<xsl:template match='/'><xsl:value-of select='p:twice(21)'/></xsl:template>" + END;
    aWith.newTemplates (new StreamSource (new StringReader (sCall)));
    try
    {
      aWithout.newTemplates (new StreamSource (new StringReader (sCall)));
      fail ();
    }
    catch (final TransformerConfigurationException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().startsWith ("XPST0017"));
    }

    // null removes it, a wrong type is rejected
    aWith.setAttribute (PhoxtrotTransformerFactory.ATTR_FUNCTION_CONTRIBUTOR, null);
    assertEquals ("no false 6", _jaxp (aWith));
    try
    {
      aWith.setAttribute (PhoxtrotTransformerFactory.ATTR_FUNCTION_CONTRIBUTOR, "x");
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }
}

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
package com.helger.phoxtrot.xpath.bench;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.math.RoundingMode;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTransformRequest;

/**
 * Rounding every price of the invoice in the seven rounding modes of {@link RoundingMode} other
 * than <code>UNNECESSARY</code>, at the scales 1, 0 and -1 (21 roundings per line), with the
 * rounding stylesheets of the property tests of <code>phoxtrot-xslt</code>
 * (<code>src/test/resources/rounding</code>, put into the resources by the POM). The cases:
 * <ul>
 * <li><code>functions-3.0</code>: the XSLT 3.0 functions (<code>rm:round</code>, decimal
 * arithmetic)</li>
 * <li><code>templates-1.0</code>: the XSLT 1.0 named templates (digit strings) with XSLT 1.0
 * behaviour</li>
 * <li><code>templates-1.0-as-3.0</code>: the same templates with <code>version="3.0"</code> and
 * without the backwards compatibility claim</li>
 * <li><code>templates-1.0-jdk</code>: the same templates on the XSLT 1.0 processor of the JDK
 * (XSLTC, compiled to byte code), as a reference independent of the engine; this case parses the
 * document in every run</li>
 * </ul>
 * The library stylesheet and the driver are one module (the driver's declarations are added to
 * the library text), so no resolver is needed.
 *
 * @author Philip Helger
 */
public final class RoundingWorkload implements IWorkload
{
  /** The case IDs, in the order of the benchmark */
  public static final String [] CASES = { "functions-3.0", "templates-1.0", "templates-1.0-as-3.0", "templates-1.0-jdk" };

  static final int [] SCALES = { 1, 0, -1 };

  static final RoundingMode [] MODES = { RoundingMode.UP,
                                         RoundingMode.DOWN,
                                         RoundingMode.CEILING,
                                         RoundingMode.FLOOR,
                                         RoundingMode.HALF_UP,
                                         RoundingMode.HALF_DOWN,
                                         RoundingMode.HALF_EVEN };

  private final BenchData m_aData;
  private final IXdmNode m_aDoc;
  private final XsltExecutable m_aExecutable;
  private final Templates m_aJdkTemplates;

  /**
   * @param bXslt30
   *        <code>true</code> for the driver of the XSLT 3.0 functions, <code>false</code> for the
   *        one of the XSLT 1.0 templates.
   * @return The template that writes the 21 rounded values of every line, each followed by a
   *         space, and a line end per line.
   */
  @NonNull
  private static String _getDriverDeclarations (final boolean bXslt30)
  {
    final StringBuilder aSB = new StringBuilder ();
    aSB.append ("<xsl:output method='text'/><xsl:template match='/'><xsl:for-each select='invoice/lines/line'>");
    for (final int nScale : SCALES)
      for (final RoundingMode eMode : MODES)
      {
        if (bXslt30)
          aSB.append ("<xsl:value-of select=\"rm:round(xs:decimal(price), ")
             .append (nScale)
             .append (", '")
             .append (eMode.name ())
             .append ("')\"/>");
        else
          aSB.append ("<xsl:call-template name='rm:round'><xsl:with-param name='value' select='string(price)'/>" +
                      "<xsl:with-param name='scale' select='")
             .append (nScale)
             .append ("'/><xsl:with-param name='mode' select=\"'")
             .append (eMode.name ())
             .append ("'\"/></xsl:call-template>");
        aSB.append ("<xsl:text> </xsl:text>");
      }
    aSB.append ("<xsl:text>&#10;</xsl:text></xsl:for-each></xsl:template>");
    return aSB.toString ();
  }

  /**
   * @return The text of a rounding library with the driver declarations added before its end tag,
   *         and with <code>version="3.0"</code> if requested.
   */
  @NonNull
  private static String _getStylesheet (@NonNull final String sLibrary, final boolean bXslt30Driver, final boolean bAsXslt30)
  {
    String sText = BenchResources.getText ("rounding/" + sLibrary);
    if (bAsXslt30)
      sText = sText.replace ("<xsl:stylesheet version=\"1.0\"", "<xsl:stylesheet version=\"3.0\"");
    final String sEnd = "</xsl:stylesheet>";
    if (!sText.contains (sEnd))
      throw new IllegalStateException ("Unexpected text of " + sLibrary);
    return sText.replace (sEnd, _getDriverDeclarations (bXslt30Driver) + sEnd);
  }

  /**
   * Switch off the backwards compatibility claim by reflection, so the benchmark also links against
   * an engine without the option (which then claims nothing).
   */
  private static void _setNoBackwardsCompatibility (@NonNull final XsltCompileOptions aOptions) throws Exception
  {
    Method aMethod = null;
    try
    {
      aMethod = XsltCompileOptions.class.getMethod ("setBackwardsCompatibleClaim", boolean.class);
    }
    catch (final NoSuchMethodException ex)
    {
      // An engine without the option
    }
    if (aMethod != null)
      aMethod.invoke (aOptions, Boolean.FALSE);
  }

  /**
   * @param sCase
   *        One of {@link #CASES}.
   * @param nLines
   *        The number of invoice lines of the document.
   * @throws Exception
   *         if parsing or compiling fails
   */
  public RoundingWorkload (@NonNull final String sCase, final int nLines) throws Exception
  {
    m_aData = BenchData.create (nLines, true);
    m_aDoc = BenchData.parse (m_aData.getBytes (), "urn:bench:invoice.xml");
    XsltExecutable aExecutable = null;
    Templates aJdkTemplates = null;
    switch (sCase)
    {
      case "functions-3.0":
        aExecutable = XsltCompiler.compile (_getStylesheet ("rounding-modes.xsl", true, false), new XsltCompileOptions ());
        break;
      case "templates-1.0":
        aExecutable = XsltCompiler.compile (_getStylesheet ("rounding-modes-xslt1.xsl", false, false), new XsltCompileOptions ());
        break;
      case "templates-1.0-as-3.0":
      {
        final XsltCompileOptions aOptions = new XsltCompileOptions ();
        _setNoBackwardsCompatibility (aOptions);
        aExecutable = XsltCompiler.compile (_getStylesheet ("rounding-modes-xslt1.xsl", false, true), aOptions);
        break;
      }
      case "templates-1.0-jdk":
        aJdkTemplates = TransformerFactory.newDefaultInstance ()
                                          .newTemplates (new StreamSource (new StringReader (_getStylesheet ("rounding-modes-xslt1.xsl",
                                                                                                              false,
                                                                                                              false))));
        break;
      default:
        throw new IllegalArgumentException ("Unknown rounding case '" + sCase + "'");
    }
    m_aExecutable = aExecutable;
    m_aJdkTemplates = aJdkTemplates;
  }

  @NonNull
  public Object run () throws Exception
  {
    if (m_aJdkTemplates != null)
    {
      final StringWriter aSW = new StringWriter ();
      m_aJdkTemplates.newTransformer ()
                     .transform (new StreamSource (new ByteArrayInputStream (m_aData.getBytes ())), new StreamResult (aSW));
      return aSW.toString ();
    }
    // No instruction limit: the default limit would stop large sizes
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (m_aDoc)
                                                                     .setMaxSteps (Long.MAX_VALUE);
    return m_aExecutable.transform (aRequest).principalSerialized ();
  }

  public void verify (@NonNull final Object aResult)
  {
    BenchAssert.assertEquals ("rounding output", BenchExpected.rounding (m_aData), (String) aResult);
  }

  @Nullable
  static String getCaseOrNull (@Nullable final String sCase)
  {
    for (final String s : CASES)
      if (s.equals (sCase))
        return s;
    return null;
  }
}

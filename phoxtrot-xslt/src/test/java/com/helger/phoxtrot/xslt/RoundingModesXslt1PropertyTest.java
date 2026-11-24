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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.RoundingTestCases.Case;
import com.helger.phoxtrot.xslt.compile.FileResourceResolver;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the XSLT 1.0 rounding templates of
 * <code>src/test/resources/rounding/rounding-modes-xslt1.xsl</code>: for random
 * decimals in the various xs:decimal spellings and random scales, every
 * template (<code>rm:up</code> to <code>rm:unnecessary</code>) and the generic
 * <code>rm:round</code> with the mode name in any case and with surrounding
 * spaces give the value of <code>BigDecimal.setScale (scale, RoundingMode)</code>,
 * and stop with <code>rm:ROUNDING_NECESSARY</code> exactly where Java throws
 * an <code>ArithmeticException</code>. Every case runs on phoxtrot with XSLT
 * 1.0 behaviour (backwards compatibility claim), on phoxtrot with XSLT 3.0
 * behaviour (the templates with <code>version="3.0"</code>, so string and
 * number comparisons follow the XPath 3.1 rules) and on the XSLT 1.0 processor
 * of the JDK (which also refuses functions XPath 1.0 does not have).
 *
 * @author Philip Helger
 */
public final class RoundingModesXslt1PropertyTest
{
  /**
   * The outcome of one run on each processor: phoxtrot with XSLT 1.0
   * behaviour, phoxtrot with XSLT 3.0 behaviour (the templates in a copy with
   * <code>version="3.0"</code>, without the backwards compatibility claim) and
   * the XSLT 1.0 processor of the JDK.
   */
  private record Outcome (@NonNull String phoxtrot10, @NonNull String phoxtrot30, @NonNull String jdk)
  {}

  private static final List <RoundingMode> MODES = RoundingTestCases.MODES;

  private static final String DRIVER_URI = RoundingTestCases.getResourceUri ("driver1.xsl");

  private static final XsltExecutable PHOXTROT10 = _compilePhoxtrot10 ();

  private static final XsltExecutable PHOXTROT30 = _compilePhoxtrot30 ();

  private static final Templates JDK = _compileJdk ();

  /**
   * @return The top-level elements of the driver: its parameters, and the
   *         template <code>main</code> (also called for the document node)
   *         that calls the template of <code>$mode</code> and then rm:round
   *         with <code>$spelling</code>, separated by <code>;</code>.
   */
  @NonNull
  private static String _getDriverDeclarations ()
  {
    final StringBuilder aSB = new StringBuilder ();
    aSB.append ("<xsl:output method='text'/>" +
                "<xsl:param name='v'/>" +
                "<xsl:param name='s'/>" +
                "<xsl:param name='mode'/>" +
                "<xsl:param name='spelling'/>" +
                "<xsl:template match='/'><xsl:call-template name='main'/></xsl:template>" +
                "<xsl:template name='main'><xsl:choose>");
    for (final RoundingMode eMode : MODES)
      aSB.append ("<xsl:when test=\"$mode = '")
         .append (eMode.name ())
         .append ("'\"><xsl:call-template name='")
         .append (RoundingTestCases.getFunctionName (eMode))
         .append ("'><xsl:with-param name='value' select='$v'/><xsl:with-param name='scale' select='number($s)'/>" +
                  "</xsl:call-template></xsl:when>");
    aSB.append ("</xsl:choose><xsl:text>;</xsl:text>" +
                "<xsl:call-template name='rm:round'><xsl:with-param name='value' select='$v'/>" +
                "<xsl:with-param name='scale' select='number($s)'/><xsl:with-param name='mode' select='$spelling'/>" +
                "</xsl:call-template></xsl:template>");
    return aSB.toString ();
  }

  /**
   * @return The XSLT 1.0 driver that imports the templates.
   */
  @NonNull
  private static String _getDriver ()
  {
    return "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
           " xmlns:rm='urn:x-rounding-mode' exclude-result-prefixes='rm'>" +
           "<xsl:import href='rounding-modes-xslt1.xsl'/>" +
           _getDriverDeclarations () +
           "</xsl:stylesheet>";
  }

  @NonNull
  private static XsltExecutable _compilePhoxtrot10 ()
  {
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    aOptions.setBaseUri (DRIVER_URI);
    aOptions.setResolver (FileResourceResolver.INSTANCE);
    return XsltCompiler.compile (_getDriver (), aOptions);
  }

  /**
   * Compiles the templates as XSLT 3.0: the text of the stylesheet with
   * <code>version="3.0"</code> and the driver declarations added, without the
   * backwards compatibility claim, so that nothing runs with XSLT 1.0
   * behaviour.
   */
  @NonNull
  private static XsltExecutable _compilePhoxtrot30 ()
  {
    final String sUri = RoundingTestCases.getResourceUri ("rounding-modes-xslt1.xsl");
    final String sText;
    try
    {
      sText = Files.readString (Path.of (URI.create (sUri)), StandardCharsets.UTF_8);
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
    final String sStart = "<xsl:stylesheet version=\"1.0\"";
    final String sEnd = "</xsl:stylesheet>";
    if (!sText.contains (sStart) || !sText.contains (sEnd))
      throw new IllegalStateException ("Unexpected stylesheet text");
    final String sText30 = sText.replace (sStart, "<xsl:stylesheet version=\"3.0\"")
                                .replace (sEnd, _getDriverDeclarations () + sEnd);
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    aOptions.setBaseUri (sUri);
    aOptions.setBackwardsCompatibleClaim (false);
    return XsltCompiler.compile (sText30, aOptions);
  }

  @NonNull
  private static Templates _compileJdk ()
  {
    try
    {
      final StreamSource aSource = new StreamSource (new StringReader (_getDriver ()), DRIVER_URI);
      return TransformerFactory.newDefaultInstance ().newTemplates (aSource);
    }
    catch (final TransformerException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  /**
   * @return The marker of a message that stopped the run (<code>!</code> and
   *         the local name of the rm code), or the whole text if it has no
   *         code.
   */
  @NonNull
  private static String _getMarker (@NonNull final String sMessage)
  {
    final int nStart = sMessage.indexOf ("rm:");
    if (nStart < 0)
      return "!?" + sMessage;
    final int nEnd = sMessage.indexOf (':', nStart + 3);
    return "!" + sMessage.substring (nStart + 3, nEnd < 0 ? sMessage.length () : nEnd);
  }

  @NonNull
  private static String _runPhoxtrot (@NonNull final XsltExecutable aExec,
                                      @NonNull final String sValue,
                                      @NonNull final String sScale,
                                      @NonNull final RoundingMode eMode,
                                      @NonNull final String sSpelling)
  {
    final StringBuilder aMessages = new StringBuilder ();
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.setInitialTemplate (new XdmQName ("main"));
    aRequest.setMessageListener (aMessage -> aMessages.append (aMessage.text ()));
    aRequest.params ().put (new XdmQName ("v"), XdmSequence.of (XdmAtomic.string (sValue)));
    aRequest.params ().put (new XdmQName ("s"), XdmSequence.of (XdmAtomic.string (sScale)));
    aRequest.params ().put (new XdmQName ("mode"), XdmSequence.of (XdmAtomic.string (eMode.name ())));
    aRequest.params ().put (new XdmQName ("spelling"), XdmSequence.of (XdmAtomic.string (sSpelling)));
    try
    {
      return aExec.transform (aRequest).principalSerialized ();
    }
    catch (final RuntimeException ex)
    {
      if (aMessages.length () == 0)
        throw ex;
      return _getMarker (aMessages.toString ());
    }
  }

  @NonNull
  private static String _runJdk (@NonNull final String sValue,
                                 @NonNull final String sScale,
                                 @NonNull final RoundingMode eMode,
                                 @NonNull final String sSpelling)
  {
    final StringBuilder aMessages = new StringBuilder ();
    try
    {
      final Transformer aTransformer = JDK.newTransformer ();
      // The JDK processor reports xsl:message through the error listener
      aTransformer.setErrorListener (new ErrorListener ()
      {
        public void warning (final TransformerException ex)
        {
          aMessages.append (ex.getMessage ());
        }

        public void error (final TransformerException ex)
        {
          aMessages.append (ex.getMessage ());
        }

        public void fatalError (final TransformerException ex) throws TransformerException
        {
          aMessages.append (ex.getMessage ());
          throw ex;
        }
      });
      aTransformer.setParameter ("v", sValue);
      aTransformer.setParameter ("s", sScale);
      aTransformer.setParameter ("mode", eMode.name ());
      aTransformer.setParameter ("spelling", sSpelling);
      final StringWriter aSW = new StringWriter ();
      aTransformer.transform (new StreamSource (new StringReader ("<x/>")), new StreamResult (aSW));
      return aSW.toString ();
    }
    catch (final TransformerException ex)
    {
      if (aMessages.indexOf ("rm:") < 0)
        aMessages.append (ex.getMessage ());
      return _getMarker (aMessages.toString ());
    }
  }

  @NonNull
  private static Outcome _run (@NonNull final String sValue,
                               @NonNull final String sScale,
                               @NonNull final RoundingMode eMode,
                               @NonNull final String sSpelling)
  {
    return new Outcome (_runPhoxtrot (PHOXTROT10, sValue, sScale, eMode, sSpelling),
                        _runPhoxtrot (PHOXTROT30, sValue, sScale, eMode, sSpelling),
                        _runJdk (sValue, sScale, eMode, sSpelling));
  }

  /**
   * @return What the driver writes for one mode: the result of the mode's
   *         template and of rm:round, or only the marker if the run stopped.
   */
  @NonNull
  private static String _getExpected (@NonNull final Case aCase, @NonNull final RoundingMode eMode)
  {
    final String sExpected = RoundingTestCases.getExpected (aCase, eMode);
    return sExpected.equals (RoundingTestCases.ROUNDING_NECESSARY) ? sExpected : sExpected + ";" + sExpected;
  }

  /**
   * @return The value in one of the xs:decimal spellings the templates accept:
   *         plain, with a plus sign, without the zero before the decimal point,
   *         with a decimal point but no fraction digits, or with surrounding
   *         whitespace.
   */
  @NonNull
  private static String _getValueSpelling (@NonNull final BigDecimal aValue, final int nKind)
  {
    final String sPlain = aValue.toPlainString ();
    return switch (nKind)
    {
      case 1 -> aValue.signum () >= 0 ? "+" + sPlain : sPlain;
      case 2 -> sPlain.startsWith ("0.") ? sPlain.substring (1) : sPlain.replace ("-0.", "-.");
      case 3 -> sPlain.indexOf ('.') < 0 ? sPlain + "." : sPlain;
      case 4 -> " \n" + sPlain + "\t ";
      default -> sPlain;
    };
  }

  private static final IGenerator <Integer> VALUE_SPELLINGS = Generators.ints (0, 4);

  /**
   * Every template and rm:round agree with
   * <code>BigDecimal.setScale (scale, RoundingMode)</code> on both
   * processors, for arbitrary values, ties and values that need no rounding,
   * in every mode.
   */
  @Test
  public void testTemplatesMatchJava ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (RoundingTestCases.CASES,
                                   VALUE_SPELLINGS,
                                   RoundingTestCases.SPELLING_KINDS,
                                   (aCase, aValueKind, aSpellingKind) -> {
                                     final String sValue = _getValueSpelling (aCase.value (), aValueKind.intValue ());
                                     for (final RoundingMode eMode : MODES)
                                     {
                                       final String sExpected = _getExpected (aCase, eMode);
                                       final Outcome aOutcome = _run (sValue,
                                                                      Integer.toString (aCase.scale ()),
                                                                      eMode,
                                                                      RoundingTestCases.getSpelling (eMode,
                                                                                                     aSpellingKind.intValue ()));
                                       final String sMsg = "'" + sValue + "' at scale " + aCase.scale () + " " + eMode;
                                       assertEquals ("phoxtrot 1.0: " + sMsg, sExpected, aOutcome.phoxtrot10 ());
                                       assertEquals ("phoxtrot 3.0: " + sMsg, sExpected, aOutcome.phoxtrot30 ());
                                       assertEquals ("JDK: " + sMsg, sExpected, aOutcome.jdk ());
                                     }
                                   });
  }

  /**
   * The examples of the stylesheet's comments, and the errors for an unknown
   * mode, an invalid value and an invalid scale, on both processors.
   */
  @Test
  public void testDocumentedExamples ()
  {
    final String [] [] aCases = { { "5.5", "0", "UP", "6;6" },
                                  { "-5.5", "0", "FLOOR", "-6;-6" },
                                  { "-1.1", "0", "CEILING", "-1;-1" },
                                  { "2.345", "2", "HALF_UP", "2.35;2.35" },
                                  { "-2.5", "0", "HALF_UP", "-3;-3" },
                                  { "-2.5", "0", "HALF_DOWN", "-2;-2" },
                                  { "2.5", "0", "HALF_EVEN", "2;2" },
                                  { "1234.5", "-2", "UP", "1300;1300" },
                                  { "99.96", "1", "HALF_UP", "100;100" },
                                  { "-0.4", "0", "DOWN", "0;0" },
                                  { "2.50", "1", "UNNECESSARY", "2.5;2.5" },
                                  { "2.345", "2", "UNNECESSARY", "!ROUNDING_NECESSARY" },
                                  { "1.2.3", "0", "UP", "!INVALID_VALUE" },
                                  { "", "0", "UP", "!INVALID_VALUE" },
                                  { "1e3", "0", "UP", "!INVALID_VALUE" },
                                  { "1.5", "0.5", "UP", "!INVALID_SCALE" } };
    for (final String [] aCase : aCases)
    {
      final Outcome aOutcome = _run (aCase[0], aCase[1], RoundingMode.valueOf (aCase[2]), aCase[2]);
      final String sMsg = "'" + aCase[0] + "' at scale " + aCase[1] + " " + aCase[2];
      assertEquals ("phoxtrot 1.0: " + sMsg, aCase[3], aOutcome.phoxtrot10 ());
      assertEquals ("phoxtrot 3.0: " + sMsg, aCase[3], aOutcome.phoxtrot30 ());
      assertEquals ("JDK: " + sMsg, aCase[3], aOutcome.jdk ());
    }
    // An unknown mode name for rm:round, after a valid template call
    final Outcome aUnknown = _run ("2.345", "2", RoundingMode.UP, "HALF-UP");
    assertEquals ("phoxtrot 1.0", "!UNKNOWN_MODE", aUnknown.phoxtrot10 ());
    assertEquals ("phoxtrot 3.0", "!UNKNOWN_MODE", aUnknown.phoxtrot30 ());
    assertEquals ("JDK", "!UNKNOWN_MODE", aUnknown.jdk ());
  }
}

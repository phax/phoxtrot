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

import java.math.RoundingMode;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.compile.FileResourceResolver;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the XSLT 3.0 rounding functions of
 * <code>src/test/resources/rounding/rounding-modes.xsl</code>: for random
 * decimals and scales, every function (<code>rm:up</code> to
 * <code>rm:unnecessary</code>) and the generic <code>rm:round</code> with the
 * mode name in any case and with surrounding spaces give the value of
 * <code>BigDecimal.setScale (scale, RoundingMode)</code>, and
 * <code>rm:ROUNDING_NECESSARY</code> exactly where Java throws an
 * <code>ArithmeticException</code>.
 *
 * @author Philip Helger
 */
public final class RoundingModesPropertyTest
{
  private static final List <RoundingMode> MODES = RoundingTestCases.MODES;

  private static final XdmQName PARAM_VALUE = new XdmQName ("v");
  private static final XdmQName PARAM_SCALE = new XdmQName ("s");
  private static final XdmQName PARAM_MODES = new XdmQName ("modes");

  /** The mode names as rm:round gets them: exact, lower case, padded */
  private static final IGenerator <List <Integer>> SPELLINGS = Generators.lists (RoundingTestCases.SPELLING_KINDS,
                                                                                 MODES.size (),
                                                                                 MODES.size ());

  private static final XsltExecutable DRIVER = _compileDriver ();

  @NonNull
  private static String _getCatch (@NonNull final String sSelect)
  {
    return "<xsl:try><xsl:value-of select=\"" +
           sSelect +
           "\"/><xsl:catch><xsl:value-of select=\"'!' || local-name-from-QName($err:code)\"/></xsl:catch></xsl:try>" +
           "<xsl:text>;</xsl:text>";
  }

  /**
   * Compiles a stylesheet that imports the rounding functions and writes, for
   * the parameters <code>$v</code> (the value as string), <code>$s</code> (the
   * scale) and <code>$modes</code> (the mode names for rm:round, in the order
   * of {@link RoundingMode#values()}), the result of every function and then
   * of every rm:round call, each followed by <code>;</code>. An error is
   * written as <code>!</code> and the local name of its code.
   */
  @NonNull
  private static XsltExecutable _compileDriver ()
  {
    final StringBuilder aSB = new StringBuilder ();
    aSB.append ("<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                " xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:rm='urn:x-rounding-mode'" +
                " xmlns:err='http://www.w3.org/2005/xqt-errors' exclude-result-prefixes='#all'>" +
                "<xsl:import href='rounding-modes.xsl'/>" +
                "<xsl:output method='text'/>" +
                "<xsl:param name='v' as='xs:string' required='yes'/>" +
                "<xsl:param name='s' as='xs:integer' required='yes'/>" +
                "<xsl:param name='modes' as='xs:string*' required='yes'/>" +
                "<xsl:template name='xsl:initial-template'>" +
                "<xsl:variable name='d' as='xs:decimal' select='xs:decimal($v)'/>");
    for (final RoundingMode eMode : MODES)
      aSB.append (_getCatch (RoundingTestCases.getFunctionName (eMode) + "($d, $s)"));
    for (int i = 0; i < MODES.size (); ++i)
      aSB.append (_getCatch ("rm:round($d, $s, $modes[" + (i + 1) + "])"));
    aSB.append ("</xsl:template></xsl:stylesheet>");
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    aOptions.setBaseUri (RoundingTestCases.getResourceUri ("driver.xsl"));
    aOptions.setResolver (FileResourceResolver.INSTANCE);
    return XsltCompiler.compile (aSB.toString (), aOptions);
  }

  @NonNull
  private static String _run (@NonNull final String sValue, final int nScale, @NonNull final List <String> aModes)
  {
    final ICommonsList <XdmAtomic> aModeItems = new CommonsArrayList <> ();
    for (final String sMode : aModes)
      aModeItems.add (XdmAtomic.string (sMode));
    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    aRequest.params ().put (PARAM_VALUE, XdmSequence.of (XdmAtomic.string (sValue)));
    aRequest.params ().put (PARAM_SCALE, XdmSequence.of (XdmAtomic.integer (nScale)));
    aRequest.params ().put (PARAM_MODES, XdmSequence.of (aModeItems));
    return DRIVER.transform (aRequest).principalSerialized ();
  }

  /**
   * Every function and rm:round agree with
   * <code>BigDecimal.setScale (scale, RoundingMode)</code> for arbitrary
   * values, ties and values that need no rounding.
   */
  @Test
  public void testFunctionsMatchJava ()
  {
    new PropertyChecker ().setTries (1000).forAll (RoundingTestCases.CASES, SPELLINGS, (aCase, aSpellings) -> {
      final StringBuilder aExpected = new StringBuilder ();
      for (final RoundingMode eMode : MODES)
        aExpected.append (RoundingTestCases.getExpected (aCase, eMode)).append (';');
      final List <String> aModes = new CommonsArrayList <> ();
      for (int i = 0; i < MODES.size (); ++i)
      {
        aExpected.append (RoundingTestCases.getExpected (aCase, MODES.get (i))).append (';');
        aModes.add (RoundingTestCases.getSpelling (MODES.get (i), aSpellings.get (i).intValue ()));
      }
      assertEquals (aCase.value ().toPlainString () + " at scale " + aCase.scale (),
                    aExpected.toString (),
                    _run (aCase.value ().toPlainString (), aCase.scale (), aModes));
    });
  }

  /**
   * The examples of the stylesheet's comments, and the error for an unknown
   * mode name.
   */
  @Test
  public void testDocumentedExamples ()
  {
    final List <String> aNames = new CommonsArrayList <> ();
    for (final RoundingMode eMode : MODES)
      aNames.add (eMode.name ());
    // UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY
    assertEquals ("6;5;6;5;6;5;6;!ROUNDING_NECESSARY;6;5;6;5;6;5;6;!ROUNDING_NECESSARY;", _run ("5.5", 0, aNames));
    assertEquals ("-3;-2;-2;-3;-3;-2;-2;!ROUNDING_NECESSARY;-3;-2;-2;-3;-3;-2;-2;!ROUNDING_NECESSARY;",
                  _run ("-2.5", 0, aNames));
    assertEquals ("2.35;2.34;2.35;2.34;2.35;2.34;2.34;!ROUNDING_NECESSARY;2.35;2.34;2.35;2.34;2.35;2.34;2.34;!ROUNDING_NECESSARY;",
                  _run ("2.345", 2, aNames));
    assertEquals ("1300;1200;1300;1200;1200;1200;1200;!ROUNDING_NECESSARY;1300;1200;1300;1200;1200;1200;1200;!ROUNDING_NECESSARY;",
                  _run ("1234.5", -2, aNames));
    final List <String> aUnknown = new CommonsArrayList <> ();
    for (int i = 0; i < MODES.size (); ++i)
      aUnknown.add ("HALF-UP");
    assertEquals ("2.35;2.34;2.35;2.34;2.35;2.34;2.34;!ROUNDING_NECESSARY;" +
                  "!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;",
                  _run ("2.345", 2, aUnknown));
  }
}

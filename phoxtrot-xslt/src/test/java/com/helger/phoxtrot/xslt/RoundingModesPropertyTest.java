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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

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
  /**
   * One rounding case.
   *
   * @param value
   *        The value to round.
   * @param scale
   *        The number of fraction digits to keep (negative: tens, hundreds,
   *        ...).
   */
  private record Case (@NonNull BigDecimal value, int scale)
  {}

  private static final RoundingMode [] MODES = RoundingMode.values ();

  private static final XdmQName PARAM_VALUE = new XdmQName ("v");
  private static final XdmQName PARAM_SCALE = new XdmQName ("s");
  private static final XdmQName PARAM_MODES = new XdmQName ("modes");

  /** Digit strings of 1 to 30 digits, leading zeros included */
  private static final IGenerator <BigInteger> DIGITS = Generators.strings ("0123456789", 1, 30).map (BigInteger::new);

  /**
   * Cases of three kinds for a random scale: an arbitrary value, a value that
   * is exactly halfway between two neighbours at the scale (the tie the HALF_*
   * modes decide), and a value that needs no rounding at the scale.
   */
  private static final IGenerator <Case> CASES = Generators.ints (-6, 12)
                                                           .flatMap (nScale -> Generators.oneOf (_arbitrary (nScale),
                                                                                                 _tie (nScale),
                                                                                                 _exact (nScale)));

  /** The mode names as rm:round gets them: exact, lower case, padded */
  private static final IGenerator <List <Integer>> SPELLINGS = Generators.lists (Generators.ints (0, 2),
                                                                                 MODES.length,
                                                                                 MODES.length);

  private static final XsltExecutable DRIVER = _compileDriver ();

  @NonNull
  private static IGenerator <BigDecimal> _signed (@NonNull final IGenerator <BigDecimal> aGen)
  {
    return Generators.booleans ().flatMap (aNegative -> aGen.map (a -> aNegative.booleanValue () ? a.negate () : a));
  }

  @NonNull
  private static IGenerator <Case> _arbitrary (final int nScale)
  {
    return _signed (Generators.ints (0, 15).flatMap (nValueScale -> DIGITS.map (a -> new BigDecimal (a, nValueScale.intValue ()))))
                                                                                                                                     .map (a -> new Case (a,
                                                                                                                                                          nScale));
  }

  @NonNull
  private static IGenerator <Case> _tie (final int nScale)
  {
    // digits followed by a 5 one place behind the kept digits, optionally
    // with trailing zeros (2.50 is the same tie as 2.5)
    return _signed (Generators.ints (0, 2)
                              .flatMap (nZeros -> DIGITS.map (a -> new BigDecimal (a.multiply (BigInteger.TEN)
                                                                                    .add (BigInteger.valueOf (5))
                                                                                    .multiply (BigInteger.TEN.pow (nZeros.intValue ())),
                                                                                   nScale + 1 + nZeros.intValue ())))).map (a -> new Case (a,
                                                                                                                                         nScale));
  }

  @NonNull
  private static IGenerator <Case> _exact (final int nScale)
  {
    return _signed (Generators.ints (-6, 0).flatMap (nLess -> DIGITS.map (a -> new BigDecimal (a,
                                                                                               nScale +
                                                                                                  nLess.intValue ())))).map (a -> new Case (a,
                                                                                                                                            nScale));
  }

  /**
   * @return The local name of the function of a mode: <code>HALF_UP</code> is
   *         <code>rm:half-up</code>.
   */
  @NonNull
  private static String _getFunctionName (@NonNull final RoundingMode eMode)
  {
    return "rm:" + eMode.name ().toLowerCase (Locale.ROOT).replace ('_', '-');
  }

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
      aSB.append (_getCatch (_getFunctionName (eMode) + "($d, $s)"));
    for (int i = 0; i < MODES.length; ++i)
      aSB.append (_getCatch ("rm:round($d, $s, $modes[" + (i + 1) + "])"));
    aSB.append ("</xsl:template></xsl:stylesheet>");
    final XsltCompileOptions aOptions = new XsltCompileOptions ();
    try
    {
      aOptions.setBaseUri (RoundingModesPropertyTest.class.getResource ("/rounding/rounding-modes.xsl")
                                                          .toURI ()
                                                          .resolve ("driver.xsl")
                                                          .toString ());
    }
    catch (final URISyntaxException ex)
    {
      throw new IllegalStateException (ex);
    }
    aOptions.setResolver (FileResourceResolver.INSTANCE);
    return XsltCompiler.compile (aSB.toString (), aOptions);
  }

  /**
   * @return The canonical xs:decimal form of a value (no exponent, no
   *         trailing fraction zeros, no decimal point for an integer).
   */
  @NonNull
  private static String _getCanonical (@NonNull final BigDecimal aValue)
  {
    if (aValue.signum () == 0)
      return "0";
    return aValue.stripTrailingZeros ().toPlainString ();
  }

  /**
   * @return What the driver writes for one mode: the canonical result of
   *         <code>setScale</code>, or <code>!ROUNDING_NECESSARY</code>.
   */
  @NonNull
  private static String _getExpected (@NonNull final Case aCase, @NonNull final RoundingMode eMode)
  {
    try
    {
      return _getCanonical (aCase.value ().setScale (aCase.scale (), eMode));
    }
    catch (final ArithmeticException ex)
    {
      return "!ROUNDING_NECESSARY";
    }
  }

  @NonNull
  private static String _getSpelling (@NonNull final RoundingMode eMode, final int nKind)
  {
    return switch (nKind)
    {
      case 0 -> eMode.name ();
      case 1 -> eMode.name ().toLowerCase (Locale.ROOT);
      default -> " \t" + eMode.name ().charAt (0) + eMode.name ().substring (1).toLowerCase (Locale.ROOT) + "\n ";
    };
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
    new PropertyChecker ().setTries (1000).forAll (CASES, SPELLINGS, (aCase, aSpellings) -> {
      final StringBuilder aExpected = new StringBuilder ();
      for (final RoundingMode eMode : MODES)
        aExpected.append (_getExpected (aCase, eMode)).append (';');
      final List <String> aModes = new CommonsArrayList <> ();
      for (int i = 0; i < MODES.length; ++i)
      {
        aExpected.append (_getExpected (aCase, MODES[i])).append (';');
        aModes.add (_getSpelling (MODES[i], aSpellings.get (i).intValue ()));
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
    for (int i = 0; i < MODES.length; ++i)
      aUnknown.add ("HALF-UP");
    assertEquals ("2.35;2.34;2.35;2.34;2.35;2.34;2.34;!ROUNDING_NECESSARY;" +
                  "!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;!UNKNOWN_MODE;",
                  _run ("2.345", 2, aUnknown));
  }
}

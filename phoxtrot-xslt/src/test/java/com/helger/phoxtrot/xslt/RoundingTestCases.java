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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;

/**
 * Shared parts of the property tests of the rounding stylesheets in
 * <code>src/test/resources/rounding</code> (XSLT 3.0 and XSLT 1.0): the random
 * cases, the mode name spellings and the reference result of
 * <code>BigDecimal.setScale (scale, RoundingMode)</code>.
 *
 * @author Philip Helger
 */
@Immutable
public final class RoundingTestCases
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
  @Immutable
  public record Case (@NonNull BigDecimal value, int scale)
  {}

  /** All modes, in the order of {@link RoundingMode#values()} */
  public static final List <RoundingMode> MODES = List.of (RoundingMode.values ());

  /** The marker of a run that stopped because rounding was necessary */
  public static final String ROUNDING_NECESSARY = "!ROUNDING_NECESSARY";

  /** Digit strings of 1 to 30 digits, leading zeros included */
  private static final IGenerator <BigInteger> DIGITS = Generators.strings ("0123456789", 1, 30).map (BigInteger::new);

  private RoundingTestCases ()
  {}

  @NonNull
  private static IGenerator <BigDecimal> _signed (@NonNull final IGenerator <BigDecimal> aGen)
  {
    return Generators.booleans ().flatMap (aNegative -> aGen.map (a -> aNegative.booleanValue () ? a.negate () : a));
  }

  @NonNull
  private static IGenerator <Case> _arbitrary (final int nScale)
  {
    final IGenerator <BigDecimal> aValues = Generators.ints (0, 15)
                                                      .flatMap (nValueScale -> DIGITS.map (a -> new BigDecimal (a,
                                                                                                                nValueScale.intValue ())));
    return _signed (aValues).map (a -> new Case (a, nScale));
  }

  @NonNull
  private static IGenerator <Case> _tie (final int nScale)
  {
    // Digits followed by a 5 one place behind the kept digits, optionally with
    // trailing zeros (2.50 is the same tie as 2.5)
    final IGenerator <BigDecimal> aValues = Generators.ints (0, 2).flatMap (nZeros -> {
      final int nZeroCount = nZeros.intValue ();
      return DIGITS.map (a -> new BigDecimal (a.multiply (BigInteger.TEN)
                                               .add (BigInteger.valueOf (5))
                                               .multiply (BigInteger.TEN.pow (nZeroCount)),
                                              nScale + 1 + nZeroCount));
    });
    return _signed (aValues).map (a -> new Case (a, nScale));
  }

  @NonNull
  private static IGenerator <Case> _exact (final int nScale)
  {
    final IGenerator <BigDecimal> aValues = Generators.ints (-6, 0)
                                                      .flatMap (nLess -> DIGITS.map (a -> new BigDecimal (a,
                                                                                                          nScale +
                                                                                                             nLess.intValue ())));
    return _signed (aValues).map (a -> new Case (a, nScale));
  }

  /**
   * Cases of three kinds for a random scale from -6 to 12: an arbitrary value,
   * a value exactly halfway between two neighbours at the scale (the tie the
   * HALF_* modes decide), and a value that needs no rounding at the scale.
   */
  public static final IGenerator <Case> CASES = Generators.ints (-6, 12)
                                                          .flatMap (nScale -> Generators.oneOf (_arbitrary (nScale.intValue ()),
                                                                                                _tie (nScale.intValue ()),
                                                                                                _exact (nScale.intValue ())));

  /** Spelling kinds of a mode name, see {@link #getSpelling(RoundingMode, int)} */
  public static final IGenerator <Integer> SPELLING_KINDS = Generators.ints (0, 2);

  /**
   * @param eMode
   *        The mode.
   * @param nKind
   *        0 for the constant name, 1 for lower case, 2 for capitalised with
   *        surrounding whitespace.
   * @return The mode name as the generic entry point gets it.
   */
  @NonNull
  public static String getSpelling (@NonNull final RoundingMode eMode, final int nKind)
  {
    return switch (nKind)
    {
      case 0 -> eMode.name ();
      case 1 -> eMode.name ().toLowerCase (Locale.ROOT);
      default -> " \t" + eMode.name ().charAt (0) + eMode.name ().substring (1).toLowerCase (Locale.ROOT) + "\n ";
    };
  }

  /**
   * @return The local name of the function or template of a mode:
   *         <code>HALF_UP</code> is <code>rm:half-up</code>.
   */
  @NonNull
  public static String getFunctionName (@NonNull final RoundingMode eMode)
  {
    return "rm:" + eMode.name ().toLowerCase (Locale.ROOT).replace ('_', '-');
  }

  /**
   * @return The canonical xs:decimal form of a value (no exponent, no trailing
   *         fraction zeros, no decimal point for an integer).
   */
  @NonNull
  public static String getCanonical (@NonNull final BigDecimal aValue)
  {
    if (aValue.signum () == 0)
      return "0";
    return aValue.stripTrailingZeros ().toPlainString ();
  }

  /**
   * @return The canonical result of <code>setScale</code>, or
   *         {@link #ROUNDING_NECESSARY} where Java throws an
   *         <code>ArithmeticException</code>.
   */
  @NonNull
  public static String getExpected (@NonNull final Case aCase, @NonNull final RoundingMode eMode)
  {
    try
    {
      return getCanonical (aCase.value ().setScale (aCase.scale (), eMode));
    }
    catch (final ArithmeticException ex)
    {
      return ROUNDING_NECESSARY;
    }
  }

  /**
   * @param sFileName
   *        A file name in <code>src/test/resources/rounding</code>.
   * @return The absolute URI of that file, to use as a base URI.
   */
  @NonNull
  public static String getResourceUri (@NonNull final String sFileName)
  {
    try
    {
      return RoundingTestCases.class.getResource ("/rounding/rounding-modes.xsl")
                                    .toURI ()
                                    .resolve (sFileName)
                                    .toString ();
    }
    catch (final URISyntaxException ex)
    {
      throw new IllegalStateException (ex);
    }
  }
}

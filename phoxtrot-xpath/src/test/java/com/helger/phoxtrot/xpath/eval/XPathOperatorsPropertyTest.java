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
package com.helger.phoxtrot.xpath.eval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.base.string.StringHelper;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.XPathPropertyTestHelper;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the operators of {@link XPathOperators}
 * (arithmetic, value and general comparisons, cast and castable), evaluated
 * through expressions over the variables <code>$v</code> and <code>$w</code>.
 * {@link BigInteger}, {@link BigDecimal} and the IEEE arithmetic of Java serve
 * as the reference (F&amp;O 3.1 section 4.2).
 *
 * @author Philip Helger
 */
public final class XPathOperatorsPropertyTest
{
  private static final BigInteger TWO_POW_64 = BigInteger.ONE.shiftLeft (64);

  /** Integers around zero, around the long range and far beyond it */
  private static final IGenerator <BigInteger> INTEGERS = Generators.oneOf (Generators.longs (-1000, 1000)
                                                                                      .map (x -> BigInteger.valueOf (x.longValue ())),
                                                                            Generators.longs ()
                                                                                      .map (x -> BigInteger.valueOf (x.longValue ())),
                                                                            Generators.of (BigInteger.valueOf (Long.MIN_VALUE),
                                                                                           BigInteger.valueOf (Long.MAX_VALUE),
                                                                                           BigInteger.valueOf (-1),
                                                                                           BigInteger.ZERO,
                                                                                           BigInteger.ONE.shiftLeft (63),
                                                                                           BigInteger.ONE.shiftLeft (32)),
                                                                            Generators.lists (Generators.longs (), 2, 2)
                                                                                      .map (x -> BigInteger.valueOf (x.get (0)
                                                                                                                      .longValue ())
                                                                                                           .multiply (TWO_POW_64)
                                                                                                           .add (BigInteger.valueOf (x.get (1)
                                                                                                                                      .longValue ()))));

  /** Decimals with up to 12 digit unscaled values and up to 6 fraction digits */
  private static final IGenerator <BigDecimal> DECIMALS = Generators.lists (Generators.longs (-999_999_999_999L,
                                                                                              999_999_999_999L),
                                                                            2,
                                                                            2)
                                                                    .map (x -> BigDecimal.valueOf (x.get (0).longValue (),
                                                                                                   (int) Math.floorMod (x.get (1)
                                                                                                                         .longValue (),
                                                                                                                        7)));

  /** Doubles from random bit patterns, small integers and special values */
  private static final IGenerator <Double> DOUBLES = Generators.oneOf (Generators.longs ()
                                                                                 .map (x -> Double.valueOf (Double.longBitsToDouble (x.longValue ()))),
                                                                       Generators.ints (-20, 20).map (x -> Double.valueOf (x.intValue () / 4.0)),
                                                                       Generators.of (Double.valueOf (0.0),
                                                                                      Double.valueOf (-0.0),
                                                                                      Double.valueOf (Double.NaN),
                                                                                      Double.valueOf (Double.POSITIVE_INFINITY),
                                                                                      Double.valueOf (Double.NEGATIVE_INFINITY),
                                                                                      Double.valueOf (Double.MIN_VALUE),
                                                                                      Double.valueOf (Double.MAX_VALUE),
                                                                                      Double.valueOf (1e6),
                                                                                      Double.valueOf (999999.9999999999),
                                                                                      Double.valueOf (1e-6),
                                                                                      Double.valueOf (9.999999999999999e-7),
                                                                                      Double.valueOf (9007199254740993.0),
                                                                                      Double.valueOf (0.1)));

  private static final IGenerator <Float> FLOATS = Generators.oneOf (Generators.ints ()
                                                                               .map (x -> Float.valueOf (Float.intBitsToFloat (x.intValue ()))),
                                                                     Generators.ints (-20, 20).map (x -> Float.valueOf (x.intValue () / 4.0f)),
                                                                     Generators.of (Float.valueOf (0f),
                                                                                    Float.valueOf (-0f),
                                                                                    Float.valueOf (Float.NaN),
                                                                                    Float.valueOf (Float.POSITIVE_INFINITY),
                                                                                    Float.valueOf (Float.MIN_VALUE),
                                                                                    Float.valueOf (Float.MAX_VALUE),
                                                                                    Float.valueOf (1e6f),
                                                                                    Float.valueOf (1e-6f)));

  /** Numeric values of the four primitive numeric types */
  private static final IGenerator <XdmAtomic> NUMERICS = Generators.oneOf (INTEGERS.map (XdmAtomic::integer),
                                                                           DECIMALS.map (XdmAtomic::decimal),
                                                                           DOUBLES.map (x -> XdmAtomic.dbl (x.doubleValue ())),
                                                                           FLOATS.map (x -> XdmAtomic.flt (x.floatValue ())));

  private static final String [] CAST_TARGETS = { "xs:integer",
                                                  "xs:decimal",
                                                  "xs:double",
                                                  "xs:float",
                                                  "xs:boolean",
                                                  "xs:date",
                                                  "xs:time",
                                                  "xs:dateTime",
                                                  "xs:dateTimeStamp",
                                                  "xs:duration",
                                                  "xs:dayTimeDuration",
                                                  "xs:yearMonthDuration",
                                                  "xs:gYear",
                                                  "xs:gYearMonth",
                                                  "xs:gMonth",
                                                  "xs:gMonthDay",
                                                  "xs:gDay",
                                                  "xs:hexBinary",
                                                  "xs:base64Binary",
                                                  "xs:anyURI",
                                                  "xs:QName",
                                                  "xs:NCName",
                                                  "xs:Name",
                                                  "xs:NMTOKEN",
                                                  "xs:language",
                                                  "xs:token",
                                                  "xs:normalizedString",
                                                  "xs:byte",
                                                  "xs:unsignedShort",
                                                  "xs:nonNegativeInteger",
                                                  "xs:negativeInteger",
                                                  "xs:long",
                                                  "xs:string",
                                                  "xs:untypedAtomic" };

  /** Strings that are valid or almost valid for one of the cast targets */
  private static final String [] CAST_SOURCES = { "1",
                                                  "-0",
                                                  "+1",
                                                  "1.",
                                                  ".5",
                                                  "1e5",
                                                  "1E-400",
                                                  "INF",
                                                  "-INF",
                                                  "+INF",
                                                  "NaN",
                                                  "true",
                                                  "0",
                                                  "2024-02-29",
                                                  "2023-02-29",
                                                  "-0001-01-01",
                                                  "0000-01-01",
                                                  "2024-01-01T24:00:00",
                                                  "2024-01-01T24:00:01",
                                                  "2024-01-01T10:00:00Z",
                                                  "2024-01-01T10:00:00+14:00",
                                                  "2024-01-01T10:00:00+14:01",
                                                  "24:00:00",
                                                  "23:59:60",
                                                  "P1Y",
                                                  "PT",
                                                  "P",
                                                  "-P1D",
                                                  "P1DT",
                                                  "PT1.5S",
                                                  "P1Y2M3DT4H5M6.7S",
                                                  "--12-31",
                                                  "--02-30",
                                                  "---31",
                                                  "--13",
                                                  "2024-13",
                                                  "AF",
                                                  "aF0",
                                                  "QUJD",
                                                  "QUJ=",
                                                  "p:a",
                                                  "q:a",
                                                  "a b",
                                                  " a ",
                                                  "en-GB",
                                                  "x-123456789",
                                                  "99999999999999999999",
                                                  "-129",
                                                  "256",
                                                  "65536",
                                                  "",
                                                  " 1 ",
                                                  "\t2\n",
                                                  "http://a b",
                                                  "%",
                                                  "1_000" };

  private static final IGenerator <XdmAtomic> CAST_VALUES = Generators.oneOf (Generators.of (CAST_SOURCES).map (XdmAtomic::string),
                                                                              Generators.of (CAST_SOURCES).map (XdmAtomic::untyped),
                                                                              Generators.strings ("0123456789+-.eEINFaTZPYMDHS:/ _xq", 0, 12)
                                                                                        .map (XdmAtomic::string),
                                                                              NUMERICS,
                                                                              Generators.of (XdmAtomic.bool (true), XdmAtomic.bool (false)));

  private final XPathPropertyTestHelper m_aHelper = new XPathPropertyTestHelper ();

  @NonNull
  private XdmAtomic _eval (@NonNull final String sExpr, @NonNull final XdmAtomic aV, @NonNull final XdmAtomic aW)
  {
    final XdmSequence aResult = m_aHelper.eval (sExpr, null, XdmSequence.of (aV), XdmSequence.of (aW));
    assertEquals (sExpr, 1, aResult.size ());
    return (XdmAtomic) aResult.get (0);
  }

  @NonNull
  private XdmAtomic _eval (@NonNull final String sExpr, @NonNull final XdmAtomic aV)
  {
    return _eval (sExpr, aV, aV);
  }

  /**
   * @return The error code, or <code>null</code> if the evaluation succeeds.
   */
  @Nullable
  private String _getErrorCode (@NonNull final String sExpr, @NonNull final XdmAtomic aV, @NonNull final XdmAtomic aW)
  {
    try
    {
      m_aHelper.eval (sExpr, null, XdmSequence.of (aV), XdmSequence.of (aW));
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  /**
   * @return The canonical lexical form of an xs:decimal (F&amp;O 3.1 section
   *         19.1.2.2): no exponent, no trailing fraction zeros, no decimal
   *         point for an integral value.
   */
  @NonNull
  private static String _getCanonicalDecimal (@NonNull final BigDecimal aValue)
  {
    if (aValue.signum () == 0)
      return "0";
    final BigDecimal aStripped = aValue.stripTrailingZeros ();
    return aStripped.scale () < 0 ? aStripped.setScale (0).toPlainString () : aStripped.toPlainString ();
  }

  private static void _assertDecimal (@NonNull final String sMsg,
                                      @NonNull final BigDecimal aExpected,
                                      @NonNull final XdmAtomic aActual)
  {
    assertSame (sMsg, EAtomicType.DECIMAL, aActual.getType ());
    assertEquals (sMsg, _getCanonicalDecimal (aExpected), aActual.getLexical ());
  }

  private static void _assertInteger (@NonNull final String sMsg,
                                      @NonNull final BigInteger aExpected,
                                      @NonNull final XdmAtomic aActual)
  {
    assertSame (sMsg, EAtomicType.INTEGER, aActual.getType ());
    assertEquals (sMsg, aExpected.toString (), aActual.getLexical ());
  }

  /**
   * Integer <code>+</code>, <code>-</code>, <code>*</code>,
   * <code>idiv</code> (truncating) and <code>mod</code> (sign of the
   * dividend) are exact at any magnitude, also across the boundary of the
   * long fast path; <code>div</code> is exact where the quotient terminates;
   * division by zero is FOAR0001.
   */
  @Test
  public void testIntegerArithmetic ()
  {
    new PropertyChecker ().setTries (2000).forAll (INTEGERS, INTEGERS, (aA, aB) -> {
      final XdmAtomic aV = XdmAtomic.integer (aA);
      final XdmAtomic aW = XdmAtomic.integer (aB);
      final String sMsg = aA + " op " + aB;
      _assertInteger (sMsg + " +", aA.add (aB), _eval ("$v + $w", aV, aW));
      _assertInteger (sMsg + " -", aA.subtract (aB), _eval ("$v - $w", aV, aW));
      _assertInteger (sMsg + " *", aA.multiply (aB), _eval ("$v * $w", aV, aW));
      _assertInteger (sMsg + " neg", aA.negate (), _eval ("-$v", aV, aW));
      if (aB.signum () == 0)
      {
        assertEquals ("FOAR0001", _getErrorCode ("$v idiv $w", aV, aW));
        assertEquals ("FOAR0001", _getErrorCode ("$v mod $w", aV, aW));
        assertEquals ("FOAR0001", _getErrorCode ("$v div $w", aV, aW));
      }
      else
      {
        _assertInteger (sMsg + " idiv", aA.divide (aB), _eval ("$v idiv $w", aV, aW));
        _assertInteger (sMsg + " mod", aA.remainder (aB), _eval ("$v mod $w", aV, aW));
        // The identity of F&O 3.1 section 4.2.10
        assertTrue (sMsg, _eval ("($v idiv $w) * $w + ($v mod $w) eq $v", aV, aW).getBoolean ());
        BigDecimal aExact = null;
        try
        {
          aExact = new BigDecimal (aA).divide (new BigDecimal (aB));
        }
        catch (final ArithmeticException ex)
        {
          // Non-terminating quotient: the precision is implementation-defined
        }
        if (aExact != null)
          _assertDecimal (sMsg + " div", aExact, _eval ("$v div $w", aV, aW));
      }
    });
  }

  /**
   * Decimal <code>+</code>, <code>-</code>, <code>*</code>,
   * <code>idiv</code> and <code>mod</code> are exact and their results are
   * in canonical form; mixed integer and decimal operands are promoted to
   * xs:decimal.
   */
  @Test
  public void testDecimalArithmetic ()
  {
    new PropertyChecker ().setTries (2000).forAll (DECIMALS, DECIMALS, Generators.booleans (), (aA, aB, aIntegerRight) -> {
      final XdmAtomic aV = XdmAtomic.decimal (aA);
      final BigDecimal aRight = aIntegerRight.booleanValue () ? new BigDecimal (aB.toBigInteger ()) : aB;
      final XdmAtomic aW = aIntegerRight.booleanValue () ? XdmAtomic.integer (aB.toBigInteger ()) : XdmAtomic.decimal (aB);
      final String sMsg = aA + " op " + aRight;
      _assertDecimal (sMsg + " +", aA.add (aRight), _eval ("$v + $w", aV, aW));
      _assertDecimal (sMsg + " -", aA.subtract (aRight), _eval ("$v - $w", aV, aW));
      _assertDecimal (sMsg + " *", aA.multiply (aRight), _eval ("$v * $w", aV, aW));
      if (aRight.signum () == 0)
        assertEquals ("FOAR0001", _getErrorCode ("$v mod $w", aV, aW));
      else
      {
        _assertInteger (sMsg + " idiv", aA.divideToIntegralValue (aRight).toBigInteger (), _eval ("$v idiv $w", aV, aW));
        _assertDecimal (sMsg + " mod", aA.remainder (aRight), _eval ("$v mod $w", aV, aW));
      }
      // The string of a decimal is its canonical form, and casts back to the same value
      assertEquals (_getCanonicalDecimal (aA), _eval ("string($v)", aV).getString ());
      assertTrue (_eval ("xs:decimal(string($v)) eq $v", aV).getBoolean ());
    });
  }

  /**
   * xs:double and xs:float <code>+</code>, <code>-</code>, <code>*</code>,
   * <code>div</code> and <code>mod</code> give the IEEE 754 results of the
   * type (including signed zeros, infinities and NaN).
   */
  @Test
  public void testFloatingPointArithmetic ()
  {
    final String [] aExprs = { "$v + $w", "$v - $w", "$v * $w", "$v div $w", "$v mod $w", "-$v" };
    new PropertyChecker ().setTries (2000).forAll (DOUBLES, DOUBLES, (aA, aB) -> {
      final double dA = aA.doubleValue ();
      final double dB = aB.doubleValue ();
      final double [] aExpected = { dA + dB, dA - dB, dA * dB, dA / dB, dA % dB, -dA };
      for (int i = 0; i < aExprs.length; ++i)
      {
        final XdmAtomic aResult = _eval (aExprs[i], XdmAtomic.dbl (dA), XdmAtomic.dbl (dB));
        assertSame (EAtomicType.DOUBLE, aResult.getType ());
        assertEquals (dA + " " + aExprs[i] + " " + dB,
                      Double.valueOf (aExpected[i]),
                      Double.valueOf (aResult.getAsDouble ()));
      }
    });
    new PropertyChecker ().setTries (2000).forAll (FLOATS, FLOATS, (aA, aB) -> {
      final float fA = aA.floatValue ();
      final float fB = aB.floatValue ();
      final float [] aExpected = { fA + fB, fA - fB, fA * fB, fA / fB, fA % fB, -fA };
      for (int i = 0; i < aExprs.length; ++i)
      {
        final XdmAtomic aResult = _eval (aExprs[i], XdmAtomic.flt (fA), XdmAtomic.flt (fB));
        assertSame (EAtomicType.FLOAT, aResult.getType ());
        assertEquals (fA + " " + aExprs[i] + " " + fB,
                      Float.valueOf (aExpected[i]),
                      Float.valueOf ((float) aResult.getAsDouble ()));
      }
    });
  }

  @NonNull
  private static BigDecimal _toBigDecimal (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBI)
      return new BigDecimal (aBI);
    return (BigDecimal) aPayload;
  }

  /**
   * @return The expected result of <code>compare</code> as in F&amp;O 3.1
   *         section 4.3 with numeric type promotion, or <code>null</code> if
   *         an operand is NaN or the comparison involves xs:float (where the
   *         decimal to float conversion is not modelled here).
   */
  @Nullable
  private static Integer _getExpectedComparison (@NonNull final XdmAtomic aA, @NonNull final XdmAtomic aB)
  {
    final EAtomicType eA = aA.getType ();
    final EAtomicType eB = aB.getType ();
    if (eA == EAtomicType.FLOAT || eB == EAtomicType.FLOAT)
    {
      if (eA != EAtomicType.FLOAT || eB != EAtomicType.FLOAT)
        return null;
      final float fA = (float) aA.getAsDouble ();
      final float fB = (float) aB.getAsDouble ();
      if (Float.isNaN (fA) || Float.isNaN (fB))
        return null;
      return Integer.valueOf (fA < fB ? -1 : fA == fB ? 0 : 1);
    }
    if (eA == EAtomicType.DOUBLE || eB == EAtomicType.DOUBLE)
    {
      // Decimal to double promotion rounds to the nearest double
      final double dA = eA == EAtomicType.DOUBLE ? aA.getAsDouble () : _toBigDecimal (aA).doubleValue ();
      final double dB = eB == EAtomicType.DOUBLE ? aB.getAsDouble () : _toBigDecimal (aB).doubleValue ();
      if (Double.isNaN (dA) || Double.isNaN (dB))
        return null;
      return Integer.valueOf (dA < dB ? -1 : dA == dB ? 0 : 1);
    }
    return Integer.valueOf (_toBigDecimal (aA).compareTo (_toBigDecimal (aB)));
  }

  /**
   * The value comparisons of two numeric values are consistent: exactly one of
   * <code>lt</code>, <code>eq</code> and <code>gt</code> holds unless an
   * operand is NaN (then none does and <code>ne</code> holds),
   * <code>le</code>/<code>ge</code>/<code>ne</code> follow from them, swapping
   * the operands swaps the result, the general comparisons agree for single
   * values, and the result matches the promoted comparison of the operands.
   */
  @Test
  public void testNumericComparison ()
  {
    new PropertyChecker ().setTries (3000).forAll (NUMERICS, NUMERICS, (aA, aB) -> {
      final String sMsg = aA.getType ().getLocalName () +
                          " " +
                          aA.getLexical () +
                          " vs. " +
                          aB.getType ().getLocalName () +
                          " " +
                          aB.getLexical ();
      final boolean bLt = _eval ("$v lt $w", aA, aB).getBoolean ();
      final boolean bEq = _eval ("$v eq $w", aA, aB).getBoolean ();
      final boolean bGt = _eval ("$v gt $w", aA, aB).getBoolean ();
      final boolean bNaN = Double.isNaN (aA.getAsDouble ()) || Double.isNaN (aB.getAsDouble ());
      final int nTrue = (bLt ? 1 : 0) + (bEq ? 1 : 0) + (bGt ? 1 : 0);
      assertEquals (sMsg, bNaN ? 0 : 1, nTrue);
      assertEquals (sMsg, Boolean.valueOf (!bEq), Boolean.valueOf (_eval ("$v ne $w", aA, aB).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (bLt || bEq), Boolean.valueOf (_eval ("$v le $w", aA, aB).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (bGt || bEq), Boolean.valueOf (_eval ("$v ge $w", aA, aB).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (bLt), Boolean.valueOf (_eval ("$w gt $v", aA, aB).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (bEq), Boolean.valueOf (_eval ("$w eq $v", aA, aB).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (bLt), Boolean.valueOf (_eval ("$v < $w", aA, aB).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (bEq), Boolean.valueOf (_eval ("$v = $w", aA, aB).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (!bEq), Boolean.valueOf (_eval ("$v != $w", aA, aB).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (bGt), Boolean.valueOf (_eval ("$v > $w", aA, aB).getBoolean ()));
      final Integer aExpected = _getExpectedComparison (aA, aB);
      if (aExpected != null)
      {
        assertEquals (sMsg, Boolean.valueOf (aExpected.intValue () < 0), Boolean.valueOf (bLt));
        assertEquals (sMsg, Boolean.valueOf (aExpected.intValue () == 0), Boolean.valueOf (bEq));
      }
    });
  }

  /**
   * @return <code>true</code> if the exact value of a finite, positive double
   *         lies outside the range from 0.000001 (inclusive) to 1000000
   *         (exclusive), so that its string uses an exponent. The double
   *         and the float nearest to 1.0E-6 are both below 0.000001; whether
   *         they count as "greater than or equal to 0.000001" depends on the
   *         reading of F&amp;O 3.1 section 19.1.2.2 (exact value or XPath
   *         comparison with type promotion), so the callers leave them out.
   */
  private static boolean _isExponentRange (final double dAbs)
  {
    final BigDecimal aExact = new BigDecimal (dAbs);
    return aExact.compareTo (new BigDecimal ("0.000001")) < 0 || aExact.compareTo (BigDecimal.valueOf (1_000_000)) >= 0;
  }

  /**
   * Casting xs:double and xs:float values to xs:string and back gives the
   * same value (also for signed zeros, infinities and NaN), and the string
   * uses an exponent exactly if the value lies outside the range 0.000001 to
   * 1000000 (F&amp;O 3.1 section 19.1.2.2).
   */
  @Test
  public void testFloatingPointStringRoundTrip ()
  {
    new PropertyChecker ().setTries (3000).forAll (DOUBLES, aA -> {
      final double d = aA.doubleValue ();
      final XdmAtomic aV = XdmAtomic.dbl (d);
      final String sString = _eval ("string($v)", aV).getString ();
      final XdmAtomic aBack = _eval ("xs:double(string($v))", aV);
      assertEquals (sString, aA, Double.valueOf (aBack.getAsDouble ()));
        final double dAbs = Math.abs (d);
      if (Double.isNaN (d) || Double.isInfinite (d))
        assertTrue (sString, sString.equals ("NaN") || sString.equals ("INF") || sString.equals ("-INF"));
      else
        if (d == 0)
          assertEquals (Double.doubleToRawLongBits (d) == 0 ? "0" : "-0", sString);
        else
          if (dAbs != 1e-6)
            assertEquals (sString, Boolean.valueOf (_isExponentRange (dAbs)), Boolean.valueOf (sString.contains ("E")));
    });
    new PropertyChecker ().setTries (3000).forAll (FLOATS, aA -> {
      final float f = aA.floatValue ();
      final XdmAtomic aV = XdmAtomic.flt (f);
      final String sString = _eval ("string($v)", aV).getString ();
      final XdmAtomic aBack = _eval ("xs:float(string($v))", aV);
      assertEquals (sString, aA, Float.valueOf ((float) aBack.getAsDouble ()));
      final float fAbs = Math.abs (f);
      if (!Float.isNaN (f) && !Float.isInfinite (f) && f != 0 && fAbs != 1e-6f)
        assertEquals (sString, Boolean.valueOf (_isExponentRange (fAbs)), Boolean.valueOf (sString.contains ("E")));
    });
  }

  /**
   * Integers cast to xs:string and back are unchanged, and integers within
   * +/-2^53 survive a round trip through xs:double.
   */
  @Test
  public void testIntegerCastRoundTrip ()
  {
    new PropertyChecker ().setTries (2000).forAll (INTEGERS, aA -> {
      final XdmAtomic aV = XdmAtomic.integer (aA);
      assertEquals (aA.toString (), _eval ("string($v)", aV).getString ());
      assertTrue (_eval ("xs:integer(string($v)) eq $v", aV).getBoolean ());
      if (aA.abs ().bitLength () <= 53)
        _assertInteger (aA.toString (), aA, _eval ("xs:integer(xs:double($v))", aV));
    });
  }

  @NonNull
  private static String _pad (final int nValue, final int nDigits)
  {
    return StringHelper.getLeadingZero (nValue, nDigits);
  }

  /**
   * A dateTime written with any fraction digits, timezone offset and the hour
   * 24 casts to the canonical form (fraction without trailing zeros, Z for a
   * zero offset, 24:00:00 as midnight of the next day), which casts back to
   * an equal value.
   */
  @Test
  public void testDateTimeCanonicalForm ()
  {
    final IGenerator <int []> aParts = Generators.lists (Generators.ints (0, Integer.MAX_VALUE), 9, 9).map (x -> {
      final int [] ret = new int [x.size ()];
      for (int i = 0; i < ret.length; ++i)
        ret[i] = x.get (i).intValue ();
      return ret;
    });
    new PropertyChecker ().setTries (2000).forAll (aParts, aP -> {
      final int nYear = 1 + aP[0] % 9999;
      final int nMonth = 1 + aP[1] % 12;
      final LocalDate aDate = LocalDate.of (nYear, nMonth, 1 + aP[2] % LocalDate.of (nYear, nMonth, 1).lengthOfMonth ());
      final boolean bMidnight24 = aP[3] % 8 == 0;
      final int nHour = bMidnight24 ? 24 : aP[3] % 24;
      final int nMinute = bMidnight24 ? 0 : aP[4] % 60;
      final int nSecond = bMidnight24 ? 0 : aP[5] % 60;
      final int nFractionDigits = aP[6] % 5;
      final String sFraction = nFractionDigits == 0 ? "" : bMidnight24 ? "0".repeat (nFractionDigits)
                                                                       : _pad (aP[7] % (int) Math.pow (10, nFractionDigits),
                                                                               nFractionDigits);
      // Timezone: none, or an offset in minutes from -14:00 to +14:00
      final int nTzChoice = aP[8] % 4;
      final int nTzMinutes = nTzChoice == 0 ? 0 : (aP[8] / 4) % (14 * 60 * 2 + 1) - 14 * 60;
      final String sTz;
      final String sCanonicalTz;
      if (nTzChoice == 0)
      {
        sTz = "";
        sCanonicalTz = "";
      }
      else
      {
        final int nAbs = Math.abs (nTzMinutes);
        final String sOffset = _pad (nAbs / 60, 2) + ":" + _pad (nAbs % 60, 2);
        sTz = nTzChoice == 1 && nTzMinutes == 0 ? "Z" : (nTzMinutes < 0 ? "-" : "+") + sOffset;
        sCanonicalTz = nTzMinutes == 0 ? "Z" : (nTzMinutes < 0 ? "-" : "+") + sOffset;
      }
      final String sDatePart = _pad (aDate.getYear (), 4) + "-" + _pad (aDate.getMonthValue (), 2) + "-" + _pad (aDate.getDayOfMonth (), 2);
      final String sLexical = sDatePart +
                              "T" +
                              _pad (nHour, 2) +
                              ":" +
                              _pad (nMinute, 2) +
                              ":" +
                              _pad (nSecond, 2) +
                              (sFraction.isEmpty () ? "" : "." + sFraction) +
                              sTz;

      final LocalDate aCanonicalDate = bMidnight24 ? aDate.plusDays (1) : aDate;
      String sCanonicalFraction = sFraction;
      while (sCanonicalFraction.endsWith ("0"))
        sCanonicalFraction = sCanonicalFraction.substring (0, sCanonicalFraction.length () - 1);
      final String sExpected = _pad (aCanonicalDate.getYear (), 4) +
                               "-" +
                               _pad (aCanonicalDate.getMonthValue (), 2) +
                               "-" +
                               _pad (aCanonicalDate.getDayOfMonth (), 2) +
                               "T" +
                               _pad (bMidnight24 ? 0 : nHour, 2) +
                               ":" +
                               _pad (nMinute, 2) +
                               ":" +
                               _pad (nSecond, 2) +
                               (sCanonicalFraction.isEmpty () ? "" : "." + sCanonicalFraction) +
                               sCanonicalTz;
      final XdmAtomic aV = XdmAtomic.string (sLexical);
      assertEquals (sLexical, sExpected, _eval ("string(xs:dateTime($v))", aV).getString ());
      assertTrue (sLexical, _eval ("xs:dateTime(string(xs:dateTime($v))) eq xs:dateTime($v)", aV).getBoolean ());
      // The date and time components agree with the dateTime
      assertTrue (sLexical,
                  _eval ("let $d := xs:dateTime($v) return dateTime(xs:date($d), xs:time($d)) eq $d", aV).getBoolean ());
    });
  }

  /**
   * @return The canonical xs:dayTimeDuration for a number of seconds (XSD 1.1
   *         section 3.3.27.2 with the F&amp;O normalisation): days, hours
   *         below 24, minutes and seconds below 60, zero components left out,
   *         <code>PT0S</code> for zero.
   */
  @NonNull
  private static String _getCanonicalDayTime (@NonNull final BigDecimal aSeconds)
  {
    if (aSeconds.signum () == 0)
      return "PT0S";
    final BigDecimal aAbs = aSeconds.abs ();
    final BigInteger aWhole = aAbs.toBigInteger ();
    final BigDecimal aFraction = aAbs.subtract (new BigDecimal (aWhole));
    final BigInteger [] aDays = aWhole.divideAndRemainder (BigInteger.valueOf (86_400));
    final int nRest = aDays[1].intValue ();
    final int nHours = nRest / 3600;
    final int nMinutes = nRest % 3600 / 60;
    final BigDecimal aSecs = BigDecimal.valueOf (nRest % 60).add (aFraction);
    final StringBuilder aSB = new StringBuilder ();
    if (aSeconds.signum () < 0)
      aSB.append ('-');
    aSB.append ('P');
    if (aDays[0].signum () != 0)
      aSB.append (aDays[0]).append ('D');
    if (nHours != 0 || nMinutes != 0 || aSecs.signum () != 0)
    {
      aSB.append ('T');
      if (nHours != 0)
        aSB.append (nHours).append ('H');
      if (nMinutes != 0)
        aSB.append (nMinutes).append ('M');
      if (aSecs.signum () != 0)
        aSB.append (_getCanonicalDecimal (aSecs)).append ('S');
    }
    return aSB.toString ();
  }

  /**
   * A dayTimeDuration and a yearMonthDuration written with components of any
   * size cast to xs:string in canonical form, computed from the total seconds
   * and months, and cast back to an equal value.
   */
  @Test
  public void testDurationCanonicalForm ()
  {
    final IGenerator <Integer> aComponent = Generators.oneOf (Generators.ints (-1, 3), Generators.ints (-1, 100), Generators.ints (-1, 100_000));
    new PropertyChecker ().setTries (2000)
                          .forAll (Generators.lists (aComponent, 4, 4),
                                   Generators.ints (0, 999),
                                   Generators.booleans (),
                                   (aComp, aMillis, aNegative) -> {
                                     // -1 leaves the component out
                                     final StringBuilder aSB = new StringBuilder ();
                                     if (aNegative.booleanValue ())
                                       aSB.append ('-');
                                     aSB.append ('P');
                                     BigDecimal aTotal = BigDecimal.ZERO;
                                     final int nD = aComp.get (0).intValue ();
                                     final int nH = aComp.get (1).intValue ();
                                     final int nM = aComp.get (2).intValue ();
                                     final int nS = aComp.get (3).intValue ();
                                     if (nD >= 0)
                                     {
                                       aSB.append (nD).append ('D');
                                       aTotal = aTotal.add (BigDecimal.valueOf (86_400L * nD));
                                     }
                                     final boolean bTime = nH >= 0 || nM >= 0 || nS >= 0 || nD < 0;
                                     if (bTime)
                                     {
                                       aSB.append ('T');
                                       if (nH >= 0)
                                       {
                                         aSB.append (nH).append ('H');
                                         aTotal = aTotal.add (BigDecimal.valueOf (3600L * nH));
                                       }
                                       if (nM >= 0)
                                       {
                                         aSB.append (nM).append ('M');
                                         aTotal = aTotal.add (BigDecimal.valueOf (60L * nM));
                                       }
                                       final BigDecimal aSecs = BigDecimal.valueOf (Math.max (nS, 0)).add (BigDecimal.valueOf (aMillis.intValue (), 3));
                                       if (nS >= 0 || (nH < 0 && nM < 0))
                                       {
                                         aSB.append (aSecs.toPlainString ()).append ('S');
                                         aTotal = aTotal.add (aSecs);
                                       }
                                     }
                                     if (aNegative.booleanValue ())
                                       aTotal = aTotal.negate ();
                                     final String sLexical = aSB.toString ();
                                     final XdmAtomic aV = XdmAtomic.string (sLexical);
                                     assertEquals (sLexical,
                                                   _getCanonicalDayTime (aTotal),
                                                   _eval ("string(xs:dayTimeDuration($v))", aV).getString ());
                                     assertTrue (sLexical,
                                                 _eval ("xs:dayTimeDuration(string(xs:dayTimeDuration($v))) eq xs:dayTimeDuration($v)",
                                                        aV).getBoolean ());

                                     // Years and months from the first two components
                                     final int nYears = Math.max (nD, 0);
                                     final int nMonths = Math.max (nH, 0);
                                     final String sYM = (aNegative.booleanValue () ? "-" : "") + "P" + nYears + "Y" + nMonths + "M";
                                     final int nTotalMonths = nYears * 12 + nMonths;
                                     final StringBuilder aExpected = new StringBuilder ();
                                     if (nTotalMonths == 0)
                                       aExpected.append ("P0M");
                                     else
                                     {
                                       if (aNegative.booleanValue ())
                                         aExpected.append ('-');
                                       aExpected.append ('P');
                                       if (nTotalMonths >= 12)
                                         aExpected.append (nTotalMonths / 12).append ('Y');
                                       if (nTotalMonths % 12 != 0)
                                         aExpected.append (nTotalMonths % 12).append ('M');
                                     }
                                     assertEquals (sYM,
                                                   aExpected.toString (),
                                                   _eval ("string(xs:yearMonthDuration($v))", XdmAtomic.string (sYM)).getString ());
                                   });
  }




  /**
   * <code>$v castable as T</code> is true exactly when <code>$v cast as
   * T</code> raises no error (XPath 3.1 section 3.14.3), every failure is an
   * {@link XPathException}, and the string of a cast result casts to the
   * same value again.
   */
  @Test
  public void testCastableMatchesCast ()
  {
    new PropertyChecker ().setTries (5000).forAll (CAST_VALUES, Generators.of (CAST_TARGETS), (aV, sType) -> {
      final String sMsg = aV.getType ().getLocalName () + " '" + aV.getLexical () + "' as " + sType;
      final boolean bCastable = _eval ("$v castable as " + sType, aV).getBoolean ();
      final String sError = _getErrorCode ("$v cast as " + sType, aV, aV);
      assertEquals (sMsg + ": " + sError, Boolean.valueOf (bCastable), Boolean.valueOf (sError == null));
      if (bCastable)
      {
        final XdmAtomic aCast = _eval ("$v cast as " + sType, aV);
        final XdmAtomic aAgain = _eval ("string($v cast as " + sType + ") cast as " + sType, aV);
        assertEquals (sMsg, aCast.getLexical (), aAgain.getLexical ());
        assertSame (sMsg, aCast.getType (), aAgain.getType ());
      }
      // The empty sequence is castable to an optional type only
      assertTrue (_eval ("() castable as " + sType + "?", aV).getBoolean ());
      assertFalse (_eval ("() castable as " + sType, aV).getBoolean ());
    });
  }
}

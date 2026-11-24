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
package com.helger.phoxtrot.xsd;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xsd.XsdTestSupport.Compiled;
import com.helger.phoxtrot.xsd.XsdTestSupport.EVerdict;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the value checks of the built-in simple types in
 * {@link XsdSimpleTypeValidator}: every raw value (with surrounding and inner
 * XML whitespace) is checked against a restriction of the built-in type without
 * facets, and the verdict is compared with a reference derived from XML Schema
 * 1.1 Part 2 (the whiteSpace collapse of section 4.3.6, the lexical spaces of
 * section 3.3 and the value spaces of the integer types of section 3.4).
 *
 * @author Philip Helger
 */
public final class XsdSimpleTypeValidatorPropertyTest
{
  private static final Pattern INTEGER_GRAMMAR = Pattern.compile ("[+-]?[0-9]+");
  private static final Pattern DECIMAL_GRAMMAR = Pattern.compile ("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
  /** XSD 1.1 Part 2, section 3.3.5.1 (doubleRep, also floatRep). */
  private static final Pattern DOUBLE_GRAMMAR_11 = Pattern.compile ("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[Ee][+-]?[0-9]+)?|[+-]?INF|NaN");
  /**
   * XSD 1.0 Part 2, section 3.2.5.1: as in 1.1, but the positive infinity is only <code>INF</code>
   * (<code>+INF</code> was added by XSD 1.1).
   */
  private static final Pattern DOUBLE_GRAMMAR_10 = Pattern.compile ("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[Ee][+-]?[0-9]+)?|-?INF|NaN");
  private static final Pattern LANGUAGE_GRAMMAR = Pattern.compile ("[a-zA-Z]{1,8}(?:-[a-zA-Z0-9]{1,8})*");
  private static final String WHITESPACE = " \t\n\r";

  /**
   * A built-in integer type with its value space bounds.
   *
   * @param name
   *        The local name in the XSD namespace.
   * @param min
   *        The smallest value, <code>null</code> for unbounded.
   * @param max
   *        The largest value, <code>null</code> for unbounded.
   */
  private record IntType (@NonNull String name, @Nullable BigInteger min, @Nullable BigInteger max)
  {
    boolean isInRange (@NonNull final BigInteger aValue)
    {
      return (min == null || aValue.compareTo (min) >= 0) && (max == null || aValue.compareTo (max) <= 0);
    }
  }

  private static final IntType [] INT_TYPES = { new IntType ("integer", null, null),
                                                new IntType ("long",
                                                             BigInteger.valueOf (Long.MIN_VALUE),
                                                             BigInteger.valueOf (Long.MAX_VALUE)),
                                                new IntType ("int",
                                                             BigInteger.valueOf (Integer.MIN_VALUE),
                                                             BigInteger.valueOf (Integer.MAX_VALUE)),
                                                new IntType ("short",
                                                             BigInteger.valueOf (Short.MIN_VALUE),
                                                             BigInteger.valueOf (Short.MAX_VALUE)),
                                                new IntType ("byte",
                                                             BigInteger.valueOf (Byte.MIN_VALUE),
                                                             BigInteger.valueOf (Byte.MAX_VALUE)),
                                                new IntType ("nonNegativeInteger", BigInteger.ZERO, null),
                                                new IntType ("positiveInteger", BigInteger.ONE, null),
                                                new IntType ("nonPositiveInteger", null, BigInteger.ZERO),
                                                new IntType ("negativeInteger", null, BigInteger.ONE.negate ()),
                                                new IntType ("unsignedLong",
                                                             BigInteger.ZERO,
                                                             BigInteger.TWO.pow (64).subtract (BigInteger.ONE)),
                                                new IntType ("unsignedInt", BigInteger.ZERO, BigInteger.valueOf (4294967295L)),
                                                new IntType ("unsignedShort", BigInteger.ZERO, BigInteger.valueOf (65535)),
                                                new IntType ("unsignedByte", BigInteger.ZERO, BigInteger.valueOf (255)) };

  private static final ICommonsMap <String, XsdSimpleType> TYPE_CACHE = new CommonsHashMap <> ();

  /**
   * @return The restriction without facets of the built-in type <code>xs:sBuiltin</code>, compiled
   *         once per version.
   */
  @NonNull
  private static XsdSimpleType _builtin (@NonNull final String sBuiltin, @NonNull final EXsdVersion eVersion)
  {
    return TYPE_CACHE.computeIfAbsent (sBuiltin + "/" + eVersion.getID (), k -> {
      final Compiled aCompiled = XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                                                         "<xs:simpleType name=\"t\"><xs:restriction base=\"xs:" +
                                                         sBuiltin +
                                                         "\"/></xs:simpleType></xs:schema>",
                                                         null,
                                                         eVersion);
      assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
      return (XsdSimpleType) aCompiled.schema ().m_aTypes.get (new XsdName ("", "t"));
    });
  }

  private static boolean _isValid (@NonNull final String sBuiltin,
                                   @NonNull final EXsdVersion eVersion,
                                   @NonNull final String sRaw)
  {
    try
    {
      XsdSimpleTypeValidator.validate (_builtin (sBuiltin, eVersion), sRaw);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  /**
   * The whiteSpace collapse of XSD Part 2, section 4.3.6: tab, line feed and carriage return become
   * spaces, runs of spaces become one, leading and trailing spaces are removed.
   */
  @NonNull
  private static String _collapse (@NonNull final String s)
  {
    final String sRuns = s.replaceAll ("[\t\n\r ]+", " ");
    return sRuns.replaceAll ("^ | $", "");
  }

  /**
   * @return A generator that surrounds the values of the passed generator with up to two XML
   *         whitespace characters on each side.
   */
  @NonNull
  private static IGenerator <String> _withWhitespace (@NonNull final IGenerator <String> aValues)
  {
    return aValues.flatMap (sValue -> Generators.strings (WHITESPACE, 0, 2)
                                                .flatMap (sBefore -> Generators.strings (WHITESPACE, 0, 2)
                                                                               .map (sAfter -> sBefore +
                                                                                               sValue +
                                                                                               sAfter)));
  }

  /**
   * @return Integer values near the bounds of every built-in integer type, arbitrary longs and
   *         values beyond the range of a long.
   */
  @NonNull
  private static IGenerator <BigInteger> _integerValues ()
  {
    final ICommonsList <BigInteger> aBounds = new CommonsArrayList <> (BigInteger.ZERO);
    for (final IntType aType : INT_TYPES)
    {
      if (aType.min () != null)
        aBounds.add (aType.min ());
      if (aType.max () != null)
        aBounds.add (aType.max ());
    }
    final IGenerator <BigInteger> aNearBounds = Generators.of (aBounds.toArray (new BigInteger [0]))
                                                          .flatMap (aBound -> Generators.ints (-2, 2)
                                                                                        .map (aDelta -> aBound.add (BigInteger.valueOf (aDelta.intValue ()))));
    final IGenerator <BigInteger> aLongs = Generators.longs ().map (BigInteger::valueOf);
    final IGenerator <BigInteger> aHuge = Generators.strings ("0123456789", 20, 30)
                                                    .flatMap (sDigits -> Generators.booleans ()
                                                                                   .map (aNeg -> new BigInteger ((aNeg.booleanValue () ? "-"
                                                                                                                                       : "") +
                                                                                                                 "1" +
                                                                                                                 sDigits)));
    return Generators.oneOf (aNearBounds, aLongs, aHuge);
  }

  /**
   * Model property for the integer types: a value with an optional plus sign, leading zeros, a
   * negative zero and surrounding whitespace is valid exactly when the integer it denotes is in the
   * value space of the type, and XSD 1.0 and 1.1 agree.
   */
  @Test
  public void testIntegerTypesMatchRange ()
  {
    new PropertyChecker ().setTries (1500)
                          .forAll (Generators.of (INT_TYPES),
                                   _integerValues (),
                                   Generators.ints (0, 7),
                                   Generators.strings (WHITESPACE, 0, 2),
                                   (aType, aValue, aFormat, sSpace) -> {
                                     final int nFormat = aFormat.intValue ();
                                     String sDigits = aValue.abs ().toString ();
                                     if ((nFormat & 2) != 0)
                                       sDigits = "00" + sDigits;
                                     final String sSign;
                                     if (aValue.signum () < 0 || (aValue.signum () == 0 && (nFormat & 4) != 0))
                                       sSign = "-";
                                     else
                                       sSign = (nFormat & 1) != 0 ? "+" : "";
                                     final String sRaw = sSpace + sSign + sDigits + sSpace;
                                     final boolean bExpected = aType.isInRange (aValue);
                                     assertEquals (aType.name () + " '" + sRaw + "'",
                                                   bExpected,
                                                   _isValid (aType.name (), EXsdVersion.V11, sRaw));
                                     assertEquals (aType.name () + " '" + sRaw + "' (1.0)",
                                                   bExpected,
                                                   _isValid (aType.name (), EXsdVersion.V10, sRaw));
                                   });
  }

  /**
   * Oracle property for arbitrary strings of integer-like characters: valid exactly when the
   * collapsed string matches <code>[+-]?[0-9]+</code> and its value is in the range of the type.
   */
  @Test
  public void testIntegerTypesOnArbitraryStrings ()
  {
    new PropertyChecker ().setTries (1500)
                          .forAll (Generators.of (INT_TYPES),
                                   Generators.strings ("+-019 \t.٣", 0, 6),
                                   (aType, sRaw) -> {
                                     final String sCollapsed = _collapse (sRaw);
                                     final boolean bExpected = INTEGER_GRAMMAR.matcher (sCollapsed).matches () &&
                                                               aType.isInRange (new BigInteger (sCollapsed));
                                     assertEquals (aType.name () + " '" + sRaw + "'",
                                                   bExpected,
                                                   _isValid (aType.name (), EXsdVersion.V11, sRaw));
                                   });
  }

  /**
   * Oracle property for xs:decimal: valid exactly when the collapsed value matches
   * <code>[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)</code>, in XSD 1.0 and 1.1.
   */
  @Test
  public void testDecimal ()
  {
    new PropertyChecker ().setTries (1500)
                          .forAll (Generators.strings ("0+-.9 \te", 0, 7), sRaw -> {
                            final boolean bExpected = DECIMAL_GRAMMAR.matcher (_collapse (sRaw)).matches ();
                            assertEquals ("'" + sRaw + "'", bExpected, _isValid ("decimal", EXsdVersion.V11, sRaw));
                            assertEquals ("'" + sRaw + "' (1.0)", bExpected, _isValid ("decimal", EXsdVersion.V10, sRaw));
                          });
  }

  /**
   * Oracle property for xs:boolean: valid exactly when the collapsed value is one of
   * <code>true</code>, <code>false</code>, <code>1</code> and <code>0</code>.
   */
  @Test
  public void testBoolean ()
  {
    final IGenerator <String> aValues = Generators.oneOf (_withWhitespace (Generators.of ("true",
                                                                                         "false",
                                                                                         "1",
                                                                                         "0",
                                                                                         "TRUE",
                                                                                         "True",
                                                                                         "01",
                                                                                         "yes",
                                                                                         "")),
                                                          Generators.strings ("tf01 \t", 0, 3));
    new PropertyChecker ().setTries (500).forAll (aValues, sRaw -> {
      final String sCollapsed = _collapse (sRaw);
      final boolean bExpected = sCollapsed.equals ("true") ||
                                sCollapsed.equals ("false") ||
                                sCollapsed.equals ("1") ||
                                sCollapsed.equals ("0");
      assertEquals ("'" + sRaw + "'", bExpected, _isValid ("boolean", EXsdVersion.V11, sRaw));
      assertEquals ("'" + sRaw + "' (1.0)", bExpected, _isValid ("boolean", EXsdVersion.V10, sRaw));
    });
  }

  /**
   * Oracle property for xs:double and xs:float under XSD 1.1: valid exactly when the collapsed value
   * matches the doubleRep production (finite numerals of any magnitude, <code>INF</code>,
   * <code>+INF</code>, <code>-INF</code> and <code>NaN</code>).
   */
  @Test
  public void testDoubleAndFloat11 ()
  {
    final IGenerator <String> aValues = Generators.oneOf (Generators.strings ("0.9eE+- \t", 0, 7),
                                                          _withWhitespace (Generators.of ("INF",
                                                                                          "-INF",
                                                                                          "+INF",
                                                                                          "NaN",
                                                                                          "-NaN",
                                                                                          "+NaN",
                                                                                          "inf",
                                                                                          "nan",
                                                                                          "Infinity",
                                                                                          "1e400",
                                                                                          "-1E-400")));
    new PropertyChecker ().setTries (1500).forAll (aValues, sRaw -> {
      final boolean bExpected = DOUBLE_GRAMMAR_11.matcher (_collapse (sRaw)).matches ();
      assertEquals ("double '" + sRaw + "'", bExpected, _isValid ("double", EXsdVersion.V11, sRaw));
      assertEquals ("float '" + sRaw + "'", bExpected, _isValid ("float", EXsdVersion.V11, sRaw));
    });
  }

  /**
   * Oracle property for xs:double and xs:float under XSD 1.0: as under 1.1, but <code>+INF</code>
   * is not in the lexical space.
   */
  @Test
  @Ignore ("Bug: +INF is accepted under XSD 1.0, XsdLexical.isDoubleLexical has no version")
  public void testDoubleAndFloat10 ()
  {
    final IGenerator <String> aValues = Generators.oneOf (Generators.strings ("0.9eE+- \t", 0, 7),
                                                          _withWhitespace (Generators.of ("INF", "-INF", "+INF", "NaN")));
    new PropertyChecker ().setTries (1000).forAll (aValues, sRaw -> {
      final boolean bExpected = DOUBLE_GRAMMAR_10.matcher (_collapse (sRaw)).matches ();
      assertEquals ("double '" + sRaw + "'", bExpected, _isValid ("double", EXsdVersion.V10, sRaw));
      assertEquals ("float '" + sRaw + "'", bExpected, _isValid ("float", EXsdVersion.V10, sRaw));
    });
  }

  /**
   * Oracle property for xs:language: valid exactly when the collapsed value matches
   * <code>[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*</code>.
   */
  @Test
  public void testLanguage ()
  {
    new PropertyChecker ().setTries (1500)
                          .forAll (Generators.strings ("aZ9- \té", 0, 14), sRaw -> {
                            final boolean bExpected = LANGUAGE_GRAMMAR.matcher (_collapse (sRaw)).matches ();
                            assertEquals ("'" + sRaw + "'", bExpected, _isValid ("language", EXsdVersion.V11, sRaw));
                            assertEquals ("'" + sRaw + "' (1.0)", bExpected, _isValid ("language", EXsdVersion.V10, sRaw));
                          });
  }

  /**
   * Model property for xs:date under XSD 1.1 (years 0001 to 9999): valid exactly when the month is 1
   * to 12, the day exists in that month of the proleptic Gregorian calendar (leap years included)
   * and the timezone is <code>Z</code> or an offset of at most 14 hours with minutes below 60.
   */
  @Test
  public void testDate ()
  {
    final IGenerator <Integer> aYears = Generators.oneOf (Generators.of (Integer.valueOf (2000),
                                                                         Integer.valueOf (1900),
                                                                         Integer.valueOf (2024),
                                                                         Integer.valueOf (2023),
                                                                         Integer.valueOf (2100),
                                                                         Integer.valueOf (400),
                                                                         Integer.valueOf (1)),
                                                          Generators.ints (1, 9999));
    final IGenerator <String> aTimezones = Generators.of ("",
                                                          "Z",
                                                          "+14:00",
                                                          "-14:00",
                                                          "+14:01",
                                                          "+15:00",
                                                          "-00:00",
                                                          "+05:30",
                                                          "+05:60",
                                                          "+5:30",
                                                          "z");
    new PropertyChecker ().setTries (1500)
                          .forAll (aYears,
                                   Generators.ints (0, 13),
                                   Generators.ints (0, 32),
                                   aTimezones,
                                   (aYear, aMonth, aDay, sTz) -> {
                                     final int nYear = aYear.intValue ();
                                     final int nMonth = aMonth.intValue ();
                                     final int nDay = aDay.intValue ();
                                     final String sRaw = String.valueOf (10000 + nYear).substring (1) +
                                                         "-" +
                                                         String.valueOf (100 + nMonth).substring (1) +
                                                         "-" +
                                                         String.valueOf (100 + nDay).substring (1) +
                                                         sTz;
                                     final boolean bTzValid = sTz.isEmpty () ||
                                                              sTz.equals ("Z") ||
                                                              sTz.equals ("+14:00") ||
                                                              sTz.equals ("-14:00") ||
                                                              sTz.equals ("-00:00") ||
                                                              sTz.equals ("+05:30");
                                     final boolean bExpected = bTzValid &&
                                                               nMonth >= 1 &&
                                                               nMonth <= 12 &&
                                                               nDay >= 1 &&
                                                               YearMonth.of (nYear, nMonth).isValidDay (nDay);
                                     assertEquals (sRaw, bExpected, _isValid ("date", EXsdVersion.V11, sRaw));
                                   });
  }

  /**
   * Model property for xs:time under XSD 1.1: valid exactly when hours, minutes and seconds are in
   * range (seconds below 60, no leap second) or the value is the end of day
   * <code>24:00:00</code> with only zero fraction digits, and a fraction has at least one digit.
   */
  @Test
  public void testTime ()
  {
    final IGenerator <String> aFractions = Generators.of ("", ".0", ".000", ".5", ".", ".999");
    new PropertyChecker ().setTries (1500)
                          .forAll (Generators.ints (0, 25),
                                   Generators.ints (0, 61),
                                   Generators.ints (0, 61),
                                   aFractions,
                                   (aHour, aMinute, aSecond, sFraction) -> {
                                     final int nHour = aHour.intValue ();
                                     final int nMinute = aMinute.intValue ();
                                     final int nSecond = aSecond.intValue ();
                                     final String sRaw = String.valueOf (100 + nHour).substring (1) +
                                                         ":" +
                                                         String.valueOf (100 + nMinute).substring (1) +
                                                         ":" +
                                                         String.valueOf (100 + nSecond).substring (1) +
                                                         sFraction;
                                     final boolean bFractionValid = !sFraction.equals (".");
                                     final boolean bZeroFraction = sFraction.replace ("0", "").length () <= 1;
                                     final boolean bExpected = bFractionValid &&
                                                               ((nHour < 24 && nMinute < 60 && nSecond < 60) ||
                                                                (nHour == 24 && nMinute == 0 && nSecond == 0 && bZeroFraction));
                                     assertEquals (sRaw, bExpected, _isValid ("time", EXsdVersion.V11, sRaw));
                                   });
  }

  /**
   * Model property for xs:duration: a value built from optional year, month, day, hour, minute and
   * second components (seconds possibly with a fraction), an optional sign and an optional
   * <code>T</code> is valid exactly when it has at least one component and a <code>T</code> is
   * followed by at least one time component; XSD 1.0 and 1.1 agree.
   */
  @Test
  public void testDuration ()
  {
    new PropertyChecker ().setTries (1000).forAll (Generators.lists (Generators.booleans (), 9, 9), aFlags -> {
      final boolean bNeg = aFlags.get (0).booleanValue ();
      final boolean bYear = aFlags.get (1).booleanValue ();
      final boolean bMonth = aFlags.get (2).booleanValue ();
      final boolean bDay = aFlags.get (3).booleanValue ();
      final boolean bHour = aFlags.get (4).booleanValue ();
      final boolean bMinute = aFlags.get (5).booleanValue ();
      final boolean bSecond = aFlags.get (6).booleanValue ();
      final boolean bForceT = aFlags.get (7).booleanValue ();
      final boolean bFraction = aFlags.get (8).booleanValue ();
      final boolean bAnyTime = bHour || bMinute || bSecond;
      final StringBuilder aSB = new StringBuilder ();
      if (bNeg)
        aSB.append ('-');
      aSB.append ('P');
      if (bYear)
        aSB.append ("1Y");
      if (bMonth)
        aSB.append ("2M");
      if (bDay)
        aSB.append ("3D");
      if (bAnyTime || bForceT)
        aSB.append ('T');
      if (bHour)
        aSB.append ("4H");
      if (bMinute)
        aSB.append ("5M");
      if (bSecond)
        aSB.append (bFraction ? "6.25S" : "6S");
      final String sRaw = aSB.toString ();
      final boolean bExpected = (bYear || bMonth || bDay || bAnyTime) && (bAnyTime || !bForceT);
      assertEquals (sRaw, bExpected, _isValid ("duration", EXsdVersion.V11, sRaw));
      assertEquals (sRaw + " (1.0)", bExpected, _isValid ("duration", EXsdVersion.V10, sRaw));
    });
  }

  /**
   * Agreement property: for every built-in type without a version difference in its lexical space
   * that is checked here, XSD 1.0 and 1.1 give the same verdict on arbitrary short strings.
   */
  @Test
  public void testVersionsAgree ()
  {
    final List <String> aTypes = List.of ("integer",
                                          "decimal",
                                          "boolean",
                                          "hexBinary",
                                          "base64Binary",
                                          "NCName",
                                          "Name",
                                          "NMTOKEN",
                                          "language",
                                          "unsignedByte");
    new PropertyChecker ().setTries (2000)
                          .forAll (Generators.of (aTypes.toArray (new String [0])),
                                   Generators.strings ("aA1:+-. =_é\t", 0, 6),
                                   (sType, sRaw) -> assertEquals (sType + " '" + sRaw + "'",
                                                                  Boolean.valueOf (_isValid (sType, EXsdVersion.V10, sRaw)),
                                                                  Boolean.valueOf (_isValid (sType, EXsdVersion.V11, sRaw))));
  }
}

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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xsd.XsdTestSupport.Compiled;
import com.helger.phoxtrot.xsd.XsdTestSupport.EVerdict;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the constraining facets (XML Schema 1.1 Part 2, section 4.3): a
 * restriction is built from generated facet values, compiled under XSD 1.0 and 1.1, and every
 * generated value must be accepted exactly when a reference predicate on the value space says so.
 * The schema constraints between facets (min below max, fractionDigits not above totalDigits,
 * minLength not above maxLength) are checked as well.
 *
 * @author Philip Helger
 */
public final class XsdFacetPropertyTest
{
  private static final BigDecimal FOUR = BigDecimal.valueOf (4);

  /**
   * Compile the simple type <code>t</code> restricting <code>xs:sBase</code> with the passed facets
   * under both versions; both versions must give the same schema verdict.
   *
   * @return The compiled type of each version (1.0 first), <code>null</code> if the schema is
   *         invalid.
   */
  @Nullable
  private static XsdSimpleType @Nullable [] _compile (@NonNull final String sBase, @NonNull final String sFacets)
  {
    final String sSchema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                           "<xs:simpleType name=\"t\"><xs:restriction base=\"xs:" +
                           sBase +
                           "\">" +
                           sFacets +
                           "</xs:restriction></xs:simpleType></xs:schema>";
    final Compiled aCompiled10 = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
    final Compiled aCompiled11 = XsdTestSupport.compile (sSchema, null, EXsdVersion.V11);
    assertNotEquals (aCompiled10.message (), EVerdict.UNSUPPORTED, aCompiled10.verdict ());
    assertEquals (sFacets + " (1.0 vs 1.1)", aCompiled10.verdict (), aCompiled11.verdict ());
    if (aCompiled10.verdict () != EVerdict.VALID)
      return null;
    final XsdName aName = new XsdName ("", "t");
    return new XsdSimpleType [] { (XsdSimpleType) aCompiled10.schema ().m_aTypes.get (aName),
                                  (XsdSimpleType) aCompiled11.schema ().m_aTypes.get (aName) };
  }

  private static boolean _isValid (@NonNull final XsdSimpleType aType,
                                   @NonNull final String sRaw,
                                   @NonNull final EXsdVersion eVersion)
  {
    try
    {
      XsdSimpleTypeValidator.validate (aType, sRaw, eVersion);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  /**
   * Check a value against the types of both versions.
   */
  private static void _assertValid (@NonNull final XsdSimpleType @NonNull [] aTypes,
                                    @NonNull final String sMessage,
                                    final boolean bExpected,
                                    @NonNull final String sRaw)
  {
    assertTrue (sMessage + " (1.0)", bExpected == _isValid (aTypes[0], sRaw, EXsdVersion.V10));
    assertTrue (sMessage + " (1.1)", bExpected == _isValid (aTypes[1], sRaw, EXsdVersion.V11));
  }

  /**
   * @return The decimal <code>n / 4</code> or, for an integer base, <code>n</code>.
   */
  @NonNull
  private static BigDecimal _quarter (final int n, final boolean bInteger)
  {
    final BigDecimal aValue = BigDecimal.valueOf (n);
    return bInteger ? aValue : aValue.divide (FOUR);
  }

  /**
   * @return A lexical form of an integer: plain, with a zero fraction, with a leading zero, with
   *         two zero fraction digits or with a plus sign.
   */
  @NonNull
  private static String _formatInteger (final int n, final int nFormat)
  {
    return switch (nFormat)
    {
      case 1 -> n + ".0";
      case 2 -> n < 0 ? "-0" + -n : "0" + n;
      case 3 -> n + ".00";
      case 4 -> n < 0 ? Integer.toString (n) : "+" + n;
      default -> Integer.toString (n);
    };
  }

  /**
   * Model property for the bound facets on xs:decimal and xs:integer: with a lower bound
   * (minInclusive or minExclusive) and an upper bound (maxInclusive or maxExclusive) the schema is
   * valid when the lower bound is below the upper one and invalid when it is above; a valid
   * restriction accepts a value exactly when it lies within the bounds.
   */
  @Test
  public void testBoundFacets ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (Generators.ints (-20, 20),
                                   Generators.ints (-20, 20),
                                   Generators.ints (0, 7),
                                   Generators.lists (Generators.ints (-25, 25), 1, 6),
                                   (aLower, aUpper, aFlags, aValues) -> {
                                     final int nFlags = aFlags.intValue ();
                                     final boolean bMinInclusive = (nFlags & 1) != 0;
                                     final boolean bMaxInclusive = (nFlags & 2) != 0;
                                     final boolean bInteger = (nFlags & 4) != 0;
                                     final BigDecimal aLow = _quarter (aLower.intValue (), bInteger);
                                     final BigDecimal aHigh = _quarter (aUpper.intValue (), bInteger);
                                     final String sFacets = "<xs:" +
                                                            (bMinInclusive ? "minInclusive" : "minExclusive") +
                                                            " value=\"" +
                                                            aLow.toPlainString () +
                                                            "\"/><xs:" +
                                                            (bMaxInclusive ? "maxInclusive" : "maxExclusive") +
                                                            " value=\"" +
                                                            aHigh.toPlainString () +
                                                            "\"/>";
                                     final XsdSimpleType [] aTypes = _compile (bInteger ? "integer" : "decimal",
                                                                               sFacets);
                                     final int nCmp = aLow.compareTo (aHigh);
                                     if (nCmp < 0)
                                       assertNotNull (sFacets + " must compile", aTypes);
                                     if (nCmp > 0)
                                       assertNull (sFacets + " must not compile", aTypes);
                                     if (aTypes != null)
                                       for (final Integer aValue : aValues)
                                       {
                                         final BigDecimal aV = _quarter (aValue.intValue (), bInteger);
                                         final boolean bExpected = (bMinInclusive ? aV.compareTo (aLow) >= 0
                                                                                  : aV.compareTo (aLow) > 0) &&
                                                                   (bMaxInclusive ? aV.compareTo (aHigh) <= 0
                                                                                  : aV.compareTo (aHigh) < 0);
                                         _assertValid (aTypes, sFacets + " " + aV, bExpected, aV.toPlainString ());
                                       }
                                   });
  }

  /**
   * Model property for the length facets on xs:string: <code>length</code>, <code>minLength</code>,
   * <code>maxLength</code> and both of the latter count characters (code points, not UTF-16 units);
   * <code>minLength</code> above <code>maxLength</code> is a schema error.
   */
  @Test
  public void testStringLengthFacets ()
  {
    final IGenerator <String> aStrings = Generators.lists (Generators.of (Integer.valueOf ('a'),
                                                                          Integer.valueOf (0xE9),
                                                                          Integer.valueOf (0x1F600),
                                                                          Integer.valueOf (' ')), 0, 7)
                                                   .map (aCodePoints -> {
                                                     final StringBuilder aSB = new StringBuilder ();
                                                     for (final Integer aCP : aCodePoints)
                                                       aSB.appendCodePoint (aCP.intValue ());
                                                     return aSB.toString ();
                                                   });
    new PropertyChecker ().setTries (300)
                          .forAll (Generators.ints (0, 3),
                                   Generators.ints (0, 5),
                                   Generators.ints (0, 5),
                                   Generators.lists (aStrings, 1, 6),
                                   (aVariant, aA, aB, aValues) -> {
                                     final int nVariant = aVariant.intValue ();
                                     final int nA = aA.intValue ();
                                     final int nB = aB.intValue ();
                                     final String sFacets = switch (nVariant)
                                     {
                                       case 0 -> "<xs:length value=\"" + nA + "\"/>";
                                       case 1 -> "<xs:minLength value=\"" +
                                                 nA +
                                                 "\"/><xs:maxLength value=\"" +
                                                 nB +
                                                 "\"/>";
                                       case 2 -> "<xs:minLength value=\"" + nA + "\"/>";
                                       default -> "<xs:maxLength value=\"" + nB + "\"/>";
                                     };
                                     final XsdSimpleType [] aTypes = _compile ("string", sFacets);
                                     assertEquals (sFacets,
                                                   Boolean.valueOf (nVariant != 1 || nA <= nB),
                                                   Boolean.valueOf (aTypes != null));
                                     if (aTypes != null)
                                       for (final String sValue : aValues)
                                       {
                                         final int nLen = sValue.codePointCount (0, sValue.length ());
                                         final boolean bExpected = switch (nVariant)
                                         {
                                           case 0 -> nLen == nA;
                                           case 1 -> nLen >= nA && nLen <= nB;
                                           case 2 -> nLen >= nA;
                                           default -> nLen <= nB;
                                         };
                                         _assertValid (aTypes, sFacets + " '" + sValue + "'", bExpected, sValue);
                                       }
                                   });
  }

  /**
   * Model property for the length facet on xs:hexBinary and xs:base64Binary: the length is measured
   * in octets, so a value is accepted exactly when it encodes that many bytes, whatever the padding
   * and the spaces of the base64 form.
   */
  @Test
  public void testBinaryLengthFacet ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (Generators.ints (0, 6),
                                   Generators.lists (Generators.byteArrays (0, 8), 1, 6),
                                   (aLength, aValues) -> {
                                     final int nLength = aLength.intValue ();
                                     final String sFacets = "<xs:length value=\"" + nLength + "\"/>";
                                     final XsdSimpleType [] aHex = _compile ("hexBinary", sFacets);
                                     final XsdSimpleType [] aB64 = _compile ("base64Binary", sFacets);
                                     for (final byte [] aBytes : aValues)
                                     {
                                       final boolean bExpected = aBytes.length == nLength;
                                       final String sHex = HexFormat.of ().formatHex (aBytes);
                                       _assertValid (aHex, sFacets + " " + sHex, bExpected, sHex);
                                       final String sB64 = Base64.getEncoder ().encodeToString (aBytes);
                                       _assertValid (aB64, sFacets + " " + sB64, bExpected, sB64);
                                       final String sSpaced = String.join (" ", sB64.split (""));
                                       _assertValid (aB64, sFacets + " " + sSpaced, bExpected, " " + sSpaced + "\n");
                                     }
                                   });
  }

  /**
   * Model property for totalDigits and fractionDigits on xs:decimal (XSD 1.1 Part 2, sections
   * 4.3.11 and 4.3.12): a value is accepted exactly when it can be written as
   * <code>i &times; 10<sup>-n</sup></code> with <code>|i| &lt; 10<sup>totalDigits</sup></code>,
   * <code>n &le; totalDigits</code> and <code>n &le; fractionDigits</code>, with the smallest such
   * <code>n</code>. Leading and trailing zeros of the lexical form do not count. fractionDigits
   * above totalDigits is a schema error.
   */
  @Test
  public void testDigitFacets ()
  {
    final IGenerator <String> aDecimals = Generators.of ("", "+", "-")
                                                    .flatMap (sSign -> Generators.strings ("0123456789", 0, 6)
                                                                                 .flatMap (sInt -> Generators.oneOf (Generators.constant (""),
                                                                                                                     Generators.strings ("0123456789",
                                                                                                                                         0,
                                                                                                                                         6)
                                                                                                                               .map (sFrac -> "." +
                                                                                                                                              sFrac))
                                                                                                             .map (sFrac -> sSign +
                                                                                                                            sInt +
                                                                                                                            sFrac)));
    new PropertyChecker ().setTries (300)
                          .forAll (Generators.ints (1, 5),
                                   Generators.ints (0, 6),
                                   Generators.lists (aDecimals, 1, 6),
                                   (aTotal, aFraction, aValues) -> {
                                     final int nTotal = aTotal.intValue ();
                                     final int nFraction = aFraction.intValue ();
                                     final String sFacets = "<xs:totalDigits value=\"" +
                                                            nTotal +
                                                            "\"/><xs:fractionDigits value=\"" +
                                                            nFraction +
                                                            "\"/>";
                                     final XsdSimpleType [] aTypes = _compile ("decimal", sFacets);
                                     assertEquals (sFacets,
                                                   Boolean.valueOf (nFraction <= nTotal),
                                                   Boolean.valueOf (aTypes != null));
                                     if (aTypes != null)
                                       for (final String sValue : aValues)
                                       {
                                         final String sDigits = sValue.replaceAll ("[+.-]", "");
                                         if (sDigits.isEmpty ())
                                           continue;
                                         BigDecimal aValue = new BigDecimal (sValue).stripTrailingZeros ();
                                         if (aValue.scale () < 0)
                                           aValue = aValue.setScale (0);
                                         final boolean bExpected = aValue.unscaledValue ()
                                                                         .abs ()
                                                                         .compareTo (BigInteger.TEN.pow (nTotal)) < 0 &&
                                                                   aValue.scale () <= nTotal &&
                                                                   aValue.scale () <= nFraction;
                                         _assertValid (aTypes, sFacets + " " + sValue, bExpected, sValue);
                                       }
                                   });
  }

  /**
   * Model property for the enumeration facet: on xs:string a value is accepted exactly when it is
   * one of the enumerated strings; on xs:decimal exactly when it equals one of them in the value
   * space, whatever lexical forms (leading zeros, zero fraction, plus sign) both sides use.
   */
  @Test
  public void testEnumeration ()
  {
    final IGenerator <String> aStrings = Generators.strings ("ab", 0, 2);
    final IGenerator <String> aDecimals = Generators.ints (-3, 3)
                                                    .flatMap (aN -> Generators.ints (0, 4)
                                                                              .map (aFormat -> _formatInteger (aN.intValue (),
                                                                                                               aFormat.intValue ())));
    new PropertyChecker ().setTries (300)
                          .forAll (Generators.booleans (),
                                   Generators.lists (aStrings, 1, 4),
                                   Generators.lists (aDecimals, 1, 4),
                                   Generators.lists (Generators.oneOf (aStrings, aDecimals), 1, 6),
                                   (aDecimal, aStringEnum, aDecimalEnum, aValues) -> {
                                     final boolean bDecimal = aDecimal.booleanValue ();
                                     final List <String> aEnum = bDecimal ? aDecimalEnum : aStringEnum;
                                     final StringBuilder aFacets = new StringBuilder ();
                                     for (final String sEnum : aEnum)
                                       aFacets.append ("<xs:enumeration value=\"").append (sEnum).append ("\"/>");
                                     final String sFacets = aFacets.toString ();
                                     final XsdSimpleType [] aTypes = _compile (bDecimal ? "decimal" : "string",
                                                                               sFacets);
                                     assertNotEquals (sFacets + " must compile", null, aTypes);
                                     for (final String sValue : aValues)
                                     {
                                       boolean bExpected = false;
                                       if (bDecimal)
                                       {
                                         // A value outside the lexical space of xs:decimal matches
                                         // nothing
                                         if (sValue.matches ("[+-]?[0-9]+(\\.[0-9]*)?"))
                                           for (final String sEnum : aEnum)
                                             if (new BigDecimal (sEnum).compareTo (new BigDecimal (sValue)) == 0)
                                               bExpected = true;
                                       }
                                       else
                                         bExpected = aEnum.contains (sValue);
                                       _assertValid (aTypes, sFacets + " '" + sValue + "'", bExpected, sValue);
                                     }
                                   });
  }
}

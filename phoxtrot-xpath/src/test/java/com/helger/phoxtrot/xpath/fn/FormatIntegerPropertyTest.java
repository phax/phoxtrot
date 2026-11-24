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
package com.helger.phoxtrot.xpath.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.XPathPropertyTestHelper;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for <code>fn:format-integer</code> ({@link FormatInteger},
 * F&amp;O 3.1 section 4.6.1): decimal digit patterns against a Java model, Roman
 * numerals and alphabetic sequences by decoding them again, and words by their
 * uniqueness.
 *
 * @author Philip Helger
 */
public final class FormatIntegerPropertyTest
{
  /** A canonical Roman numeral from 1 to 4999 */
  private static final Pattern ROMAN = Pattern.compile ("M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})");

  private static final IGenerator <BigInteger> INTEGERS = Generators.oneOf (Generators.longs (-100_000, 100_000)
                                                                                      .map (x -> BigInteger.valueOf (x.longValue ())),
                                                                            Generators.longs ().map (x -> BigInteger.valueOf (x.longValue ())),
                                                                            Generators.longs ()
                                                                                      .map (x -> BigInteger.valueOf (x.longValue ())
                                                                                                           .multiply (BigInteger.TEN.pow (12))));

  private final XPathPropertyTestHelper m_aHelper = new XPathPropertyTestHelper ();

  @NonNull
  private String _format (@NonNull final BigInteger aValue, @NonNull final String sPicture)
  {
    final XdmSequence aResult = m_aHelper.eval ("format-integer($v, $w)",
                                                null,
                                                XdmSequence.of (XdmAtomic.integer (aValue)),
                                                XdmSequence.of (XdmAtomic.string (sPicture)));
    return ((XdmAtomic) aResult.get (0)).getString ();
  }

  @NonNull
  private static String _group (@NonNull final String sDigits)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < sDigits.length (); ++i)
    {
      if (i > 0 && (sDigits.length () - i) % 3 == 0)
        aSB.append (',');
      aSB.append (sDigits.charAt (i));
    }
    return aSB.toString ();
  }

  /**
   * The decimal digit pictures <code>1</code>, <code>0001</code> and
   * <code>#,##0</code> give the digits with a sign for negative values, padded
   * to the number of mandatory digits and grouped by three.
   */
  @Test
  public void testDecimalDigitPatterns ()
  {
    new PropertyChecker ().setTries (2000).forAll (INTEGERS, aValue -> {
      final String sSign = aValue.signum () < 0 ? "-" : "";
      final String sDigits = aValue.abs ().toString ();
      assertEquals (aValue.toString (), _format (aValue, "1"));
      assertEquals (sSign + "0".repeat (Math.max (0, 4 - sDigits.length ())) + sDigits, _format (aValue, "0001"));
      assertEquals (sSign + _group (sDigits), _format (aValue, "#,##0"));
      assertEquals (sSign + _group ("0".repeat (Math.max (0, 6 - sDigits.length ())) + sDigits), _format (aValue, "000,000"));
    });
  }

  private static int _getRomanValue (final char c)
  {
    return switch (c)
    {
      case 'I' -> 1;
      case 'V' -> 5;
      case 'X' -> 10;
      case 'L' -> 50;
      case 'C' -> 100;
      case 'D' -> 500;
      case 'M' -> 1000;
      default -> throw new IllegalArgumentException ("Not a Roman digit: " + c);
    };
  }

  private static int _decodeRoman (@NonNull final String sRoman)
  {
    int ret = 0;
    for (int i = 0; i < sRoman.length (); ++i)
    {
      final int nValue = _getRomanValue (sRoman.charAt (i));
      if (i + 1 < sRoman.length () && nValue < _getRomanValue (sRoman.charAt (i + 1)))
        ret -= nValue;
      else
        ret += nValue;
    }
    return ret;
  }

  private static long _decodeAlphabetic (@NonNull final String sText, final char cFirst)
  {
    long ret = 0;
    for (int i = 0; i < sText.length (); ++i)
    {
      final int nDigit = sText.charAt (i) - cFirst + 1;
      assertTrue (sText, nDigit >= 1 && nDigit <= 26);
      ret = ret * 26 + nDigit;
    }
    return ret;
  }

  /**
   * The pictures <code>I</code> and <code>i</code> give canonical Roman
   * numerals of the value (1 to 4999), and <code>A</code> and <code>a</code>
   * the bijective base-26 letter sequence of any positive value, which decode
   * to the value again.
   */
  @Test
  public void testRomanAndAlphabetic ()
  {
    new PropertyChecker ().setTries (2000).forAll (Generators.ints (1, 4999), Generators.longs (1, Long.MAX_VALUE - 1), (aRoman, aAlpha) -> {
      final BigInteger aR = BigInteger.valueOf (aRoman.longValue ());
      final String sUpper = _format (aR, "I");
      assertTrue (sUpper, ROMAN.matcher (sUpper).matches ());
      assertEquals (sUpper, aRoman.intValue (), _decodeRoman (sUpper));
      assertEquals (sUpper.toLowerCase (Locale.ROOT), _format (aR, "i"));
      assertEquals ("-" + sUpper, _format (aR.negate (), "I"));

      final BigInteger aA = BigInteger.valueOf (aAlpha.longValue ());
      final String sAlpha = _format (aA, "a");
      assertEquals (sAlpha, aAlpha.longValue (), _decodeAlphabetic (sAlpha, 'a'));
      assertEquals (aAlpha.longValue (), _decodeAlphabetic (_format (aA, "A"), 'A'));
    });
  }

  /**
   * Different numbers are spelled differently in words (<code>w</code>), also
   * as ordinals (<code>w;o</code>).
   */
  @Test
  public void testWordsAreUnique ()
  {
    final IGenerator <Long> aSmall = Generators.longs (-1_000_000, 1_000_000);
    new PropertyChecker ().setTries (2000).forAll (aSmall, aSmall, (aFirst, aSecond) -> {
      final BigInteger aA = BigInteger.valueOf (aFirst.longValue ());
      final BigInteger aB = BigInteger.valueOf (aSecond.longValue ());
      if (aFirst.longValue () != aSecond.longValue ())
      {
        assertNotEquals (_format (aA, "w"), _format (aB, "w"));
        assertNotEquals (_format (aA, "w;o"), _format (aB, "w;o"));
      }
      assertEquals (_format (aA, "w").toUpperCase (Locale.ROOT), _format (aA, "W"));
    });
  }

  /**
   * Any picture text either formats the value or is rejected with FODF1310,
   * never with another exception.
   */
  @Test
  public void testArbitraryPictures ()
  {
    final IGenerator <String> aPictures = Generators.strings ("019#,.;:aAiIwWot()-c\u0661\u0669\u2460", 0, 8);
    new PropertyChecker ().setTries (3000).forAll (INTEGERS, aPictures, (aValue, sPicture) -> {
      try
      {
        _format (aValue, sPicture);
      }
      catch (final XPathException ex)
      {
        assertEquals (sPicture + ": " + ex.getMessage (), "FODF1310", ex.getCode ());
      }
    });
  }
}

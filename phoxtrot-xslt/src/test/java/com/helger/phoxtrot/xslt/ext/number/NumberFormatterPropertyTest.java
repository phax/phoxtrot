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
package com.helger.phoxtrot.xslt.ext.number;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for {@link NumberFormatter}, the formatting of
 * xsl:number (XSLT 3.0 section 12.4), against references written in Java:
 * decimal digits with zero padding and grouping, Roman numerals and the
 * alphabetic sequences, and the assembly of a format string from prefix,
 * format tokens, separators and suffix.
 *
 * @author Philip Helger
 */
public final class NumberFormatterPropertyTest
{
  /** A format token and its separator before it */
  private record Token (@NonNull String token, @NonNull String separator)
  {}

  private static final String [] ROMAN_SYMBOLS = { "m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i" };
  private static final int [] ROMAN_VALUES = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };

  private static final IGenerator <BigInteger> SMALL_NUMBERS = Generators.ints (1, 3999).map (n -> BigInteger.valueOf (n.intValue ()));
  private static final IGenerator <BigInteger> NUMBERS = Generators.oneOf (SMALL_NUMBERS,
                                                                           Generators.longs (0, Long.MAX_VALUE - 1)
                                                                                     .map (n -> BigInteger.valueOf (n.longValue ())),
                                                                           Generators.longs (0, Long.MAX_VALUE - 1)
                                                                                     .map (n -> BigInteger.valueOf (n.longValue ())
                                                                                                          .multiply (BigInteger.TEN.pow (10))));

  @NonNull
  private static String _roman (final int nValue)
  {
    final StringBuilder aSB = new StringBuilder ();
    int nRest = nValue;
    for (int i = 0; i < ROMAN_VALUES.length; ++i)
      while (nRest >= ROMAN_VALUES[i])
      {
        aSB.append (ROMAN_SYMBOLS[i]);
        nRest -= ROMAN_VALUES[i];
      }
    return aSB.toString ();
  }

  /** The bijective base 26 of the sequence a, b, ..., z, aa, ab, ... */
  @NonNull
  private static String _alphabetic (@NonNull final BigInteger aValue)
  {
    final StringBuilder aSB = new StringBuilder ();
    BigInteger aRest = aValue;
    final BigInteger a26 = BigInteger.valueOf (26);
    while (aRest.signum () > 0)
    {
      aRest = aRest.subtract (BigInteger.ONE);
      aSB.append ((char) ('a' + aRest.mod (a26).intValue ()));
      aRest = aRest.divide (a26);
    }
    return aSB.reverse ().toString ();
  }

  @NonNull
  private static String _grouped (@NonNull final String sDigits, @NonNull final String sSep, final int nSize)
  {
    final StringBuilder aSB = new StringBuilder ();
    final int nLen = sDigits.length ();
    for (int i = 0; i < nLen; ++i)
    {
      if (i > 0 && (nLen - i) % nSize == 0)
        aSB.append (sSep);
      aSB.append (sDigits.charAt (i));
    }
    return aSB.toString ();
  }

  @NonNull
  private static String _formatOne (@NonNull final BigInteger aValue, @NonNull final String sToken)
  {
    return switch (sToken)
    {
      case "i" -> aValue.signum () > 0 && aValue.bitLength () < 32 && aValue.intValue () < 4000 ? _roman (aValue.intValue ())
                                                                                                    : aValue.toString ();
      case "I" -> aValue.signum () > 0 && aValue.bitLength () < 32 && aValue.intValue () < 4000 ? _roman (aValue.intValue ()).toUpperCase (Locale.ROOT)
                                                                                                    : aValue.toString ();
      // The alphabetic sequence is implemented for the range of long only
      case "a" -> aValue.signum () > 0 && aValue.bitLength () < 64 ? _alphabetic (aValue) : aValue.toString ();
      case "A" -> aValue.signum () > 0 && aValue.bitLength () < 64 ? _alphabetic (aValue).toUpperCase (Locale.ROOT)
                                                                   : aValue.toString ();
      default -> {
        // Decimal digits: the token length is the minimum width
        final StringBuilder aSB = new StringBuilder (aValue.toString ());
        while (aSB.length () < sToken.length ())
          aSB.insert (0, '0');
        yield aSB.toString ();
      }
    };
  }

  /**
   * A single number with a single format token gives the Java reference:
   * decimal digits padded to the width of the token, Roman numerals for 1 to
   * 3999 and the alphabetic sequence for positive numbers (of the long range,
   * decimal beyond it). Roman numerals are only checked where they are defined.
   */
  @Test
  public void testSingleToken ()
  {
    new PropertyChecker ().setTries (2000)
                          .forAll (Generators.of ("1", "01", "0001", "a", "A", "i", "I"), NUMBERS, (sToken, aValue) -> {
                            final boolean bRoman = sToken.equalsIgnoreCase ("i");
                            if (bRoman && (aValue.signum () <= 0 || aValue.compareTo (BigInteger.valueOf (3999)) > 0))
                              return;
                            assertEquals (_formatOne (aValue, sToken),
                                          NumberFormatter.format (List.of (aValue), sToken, "", 0, null, ""));
                          });
  }

  /**
   * Grouping a decimal number inserts the separator every
   * <code>grouping-size</code> digits from the right.
   */
  @Test
  public void testGrouping ()
  {
    new PropertyChecker ().setTries (1000)
                          .forAll (NUMBERS, Generators.ints (1, 5), Generators.of (",", ".", " ", "'"), (aValue, aSize, sSep) -> {
                            assertEquals (_grouped (aValue.toString (), sSep, aSize.intValue ()),
                                          NumberFormatter.format (List.of (aValue), "1", sSep, aSize.intValue (), null, ""));
                          });
  }

  /**
   * A list of numbers is formatted with the prefix, then each number with its
   * format token (the last one reused for surplus numbers) preceded by the
   * separator before that token (or "." if there is only one token), then the
   * suffix.
   */
  @Test
  public void testFormatString ()
  {
    final IGenerator <Token> aTokens = Generators.of ("1", "01", "a", "A", "i", "I")
                                                 .flatMap (t -> Generators.of (".", "-", ") (", ", ", "/").map (s -> new Token (t, s)));
    new PropertyChecker ().setTries (1000)
                          .forAll (Generators.of ("", "(", "[", "# "),
                                   Generators.lists (aTokens, 1, 4),
                                   Generators.of ("", ")", ".", " ]"),
                                   Generators.lists (SMALL_NUMBERS, 0, 6),
                                   (sPrefix, aFormat, sSuffix, aNums) -> {
                                     final StringBuilder aFormatString = new StringBuilder (sPrefix);
                                     for (int i = 0; i < aFormat.size (); ++i)
                                     {
                                       if (i > 0)
                                         aFormatString.append (aFormat.get (i).separator ());
                                       aFormatString.append (aFormat.get (i).token ());
                                     }
                                     aFormatString.append (sSuffix);

                                     final StringBuilder aExpected = new StringBuilder (sPrefix);
                                     for (int i = 0; i < aNums.size (); ++i)
                                     {
                                       final int nToken = Math.min (i, aFormat.size () - 1);
                                       if (i > 0)
                                         aExpected.append (aFormat.size () == 1 ? "." : aFormat.get (Math.max (1, nToken)).separator ());
                                       aExpected.append (_formatOne (aNums.get (i), aFormat.get (nToken).token ()));
                                     }
                                     aExpected.append (sSuffix);
                                     assertEquals (aFormatString.toString (),
                                                   aExpected.toString (),
                                                   NumberFormatter.format (new ArrayList <> (aNums),
                                                                           aFormatString.toString (),
                                                                           "",
                                                                           0,
                                                                           null,
                                                                           ""));
                                   });
  }
}

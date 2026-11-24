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
import static org.junit.Assert.assertTrue;

import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xpath.ops.XsdLexical;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the hand-written lexical scanners of
 * {@link XsdLexScan}. Each scanner claims to accept exactly the language of a
 * regular expression of XML Schema 1.1 Part 2, so the reference is that
 * expression, written down from the specification.
 *
 * @author Philip Helger
 */
public final class XsdLexScanPropertyTest
{
  private static final String B64_CHAR = "[A-Za-z0-9+/]";
  private static final String B64 = B64_CHAR + " ?";

  /**
   * XSD 1.1 Part 2, section 3.3.16.1: <code>Base64Binary ::= (B64quad* B64final)?</code> with
   * <code>B64final ::= B64finalquad | Padded16 | Padded8</code>.
   */
  private static final Pattern BASE64_GRAMMAR = Pattern.compile ("(?:(?:" +
                                                                 B64 +
                                                                 "){4})*(?:" +
                                                                 B64 +
                                                                 B64 +
                                                                 B64 +
                                                                 B64_CHAR +
                                                                 "|" +
                                                                 B64 +
                                                                 B64 +
                                                                 "[AEIMQUYcgkosw048] ?=" +
                                                                 "|" +
                                                                 B64 +
                                                                 "[AQgw] ?= ?=)|");
  private static final Pattern HEX_GRAMMAR = Pattern.compile ("(?:[0-9A-Fa-f]{2})*");
  private static final Pattern INTEGER_GRAMMAR = Pattern.compile ("[+-]?[0-9]+");
  private static final Pattern DECIMAL_GRAMMAR = Pattern.compile ("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");

  /**
   * Characters that decide the base64 grammar: the B04 and B16 characters, other base64 characters,
   * padding, space, a tab and characters outside the ASCII lookup table.
   */
  private static final String BASE64_ALPHABET = "AQgwEcB+/z9= \tÁŁ";

  /**
   * @return Candidate strings for the base64Binary scanner: arbitrary strings over
   *         {@link #BASE64_ALPHABET}, and strings over the padding-relevant characters alone, which
   *         are in the lexical space much more often.
   */
  @NonNull
  private static IGenerator <String> _base64Candidates ()
  {
    return Generators.oneOf (Generators.strings (BASE64_ALPHABET, 0, 16),
                             Generators.strings ("AQgwE=", 0, 12),
                             Generators.strings ("AQgwE= ", 0, 12));
  }

  /**
   * @return The JDK base64 encoding of random bytes with a single space inserted after randomly
   *         chosen characters (never after the last one).
   */
  @NonNull
  private static IGenerator <String> _spacedBase64 ()
  {
    return Generators.byteArrays (0, 40).flatMap (aBytes -> {
      final String sEncoded = Base64.getEncoder ().encodeToString (aBytes);
      return Generators.lists (Generators.booleans (), sEncoded.length (), sEncoded.length ()).map (aSpaces -> {
        final StringBuilder aSB = new StringBuilder ();
        for (int i = 0; i < sEncoded.length (); i++)
        {
          aSB.append (sEncoded.charAt (i));
          if (i < sEncoded.length () - 1 && aSpaces.get (i).booleanValue ())
            aSB.append (' ');
        }
        return aSB.toString ();
      });
    });
  }

  /**
   * The canonical reading of the base64 grammar: without spaces the value must be exactly what the
   * JDK encoder produces for the bytes it decodes to (that is, padded and with zero padding bits),
   * and the spaces must be single ones between characters.
   */
  private static boolean _isCanonicalBase64 (@NonNull final String sValue)
  {
    if (sValue.startsWith (" ") || sValue.endsWith (" ") || sValue.contains ("  "))
      return false;
    final String sStripped = sValue.replace (" ", "");
    try
    {
      return Base64.getEncoder ().encodeToString (Base64.getDecoder ().decode (sStripped)).equals (sStripped);
    }
    catch (final IllegalArgumentException ex)
    {
      return false;
    }
  }

  /**
   * Oracle property: the base64Binary scanner with its lookup table accepts a string exactly when
   * the grammar of the specification does.
   */
  @Test
  public void testBase64MatchesGrammar ()
  {
    new PropertyChecker ().setTries (3000)
                          .forAll (_base64Candidates (),
                                   sValue -> assertEquals (sValue,
                                                           BASE64_GRAMMAR.matcher (sValue).matches (),
                                                           XsdLexScan.isBase64BinaryLex (sValue)));
  }

  /**
   * Second oracle: the base64Binary scanner accepts a string exactly when it is the canonical JDK
   * encoding of some bytes with single spaces between characters.
   */
  @Test
  public void testBase64MatchesJdkCanonicalEncoding ()
  {
    new PropertyChecker ().setTries (3000)
                          .forAll (_base64Candidates (),
                                   sValue -> assertEquals (sValue,
                                                           _isCanonicalBase64 (sValue),
                                                           XsdLexScan.isBase64BinaryLex (sValue)));
  }

  /**
   * Every JDK encoding of arbitrary bytes, with or without single spaces between the characters, is
   * in the lexical space; replacing one of its characters keeps it in there exactly when the result
   * is still canonical.
   */
  @Test
  public void testBase64AcceptsEncodedBytes ()
  {
    new PropertyChecker ().forAll (_spacedBase64 (),
                                   Generators.ints (0, 1000),
                                   Generators.strings (BASE64_ALPHABET, 1, 1),
                                   (sValue, aPos, sReplacement) -> {
                                     assertTrue (sValue, XsdLexScan.isBase64BinaryLex (sValue));
                                     if (!sValue.isEmpty ())
                                     {
                                       final int nPos = aPos.intValue () % sValue.length ();
                                       final String sMutated = sValue.substring (0, nPos) +
                                                               sReplacement +
                                                               sValue.substring (nPos + 1);
                                       assertEquals (sMutated,
                                                     BASE64_GRAMMAR.matcher (sMutated).matches (),
                                                     XsdLexScan.isBase64BinaryLex (sMutated));
                                     }
                                   });
  }

  /**
   * Oracle property for hexBinary: the scanner accepts exactly <code>([0-9A-Fa-f]{2})*</code>, and
   * the JDK hex encoding of arbitrary bytes, upper or lower case, is always accepted.
   */
  @Test
  public void testHexBinary ()
  {
    new PropertyChecker ().setTries (1000)
                          .forAll (Generators.strings ("0aF9gG ٠", 0, 10),
                                   sValue -> assertEquals (sValue,
                                                           HEX_GRAMMAR.matcher (sValue).matches (),
                                                           XsdLexScan.isHexBinaryLex (sValue)));
    new PropertyChecker ().forAll (Generators.byteArrays (0, 64), aBytes -> {
      assertTrue (XsdLexScan.isHexBinaryLex (HexFormat.of ().formatHex (aBytes)));
      assertTrue (XsdLexScan.isHexBinaryLex (HexFormat.of ().withUpperCase ().formatHex (aBytes)));
    });
  }

  /**
   * Oracle property for the integer and decimal scanners: they accept exactly
   * <code>[+-]?[0-9]+</code> and <code>[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)</code>; non-ASCII digits
   * are no digits.
   */
  @Test
  public void testIntegerAndDecimal ()
  {
    new PropertyChecker ().setTries (2000)
                          .forAll (Generators.strings ("0+-.9 e٣", 0, 8), sValue -> {
                            assertEquals (sValue,
                                          INTEGER_GRAMMAR.matcher (sValue).matches (),
                                          XsdLexScan.isIntegerLex (sValue));
                            assertEquals (sValue,
                                          DECIMAL_GRAMMAR.matcher (sValue).matches (),
                                          XsdLexScan.isDecimalLex (sValue));
                          });
  }

  /**
   * Differential property: the ASCII fast path of the NCName scanner decides every string like the
   * full XML name production.
   */
  @Test
  public void testNCNameFastPathMatchesFullProduction ()
  {
    final IGenerator <String> aNames = Generators.strings ("aZ_-.09:é·̀‿× \uD800", 0, 6);
    new PropertyChecker ().setTries (3000)
                          .forAll (aNames,
                                   sValue -> assertEquals (sValue,
                                                           XsdLexical.isNCName (sValue),
                                                           XsdLexScan.isNCNameLex (sValue)));
  }
}

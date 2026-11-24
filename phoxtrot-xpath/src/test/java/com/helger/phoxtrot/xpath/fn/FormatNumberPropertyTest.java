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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.XPathPropertyTestHelper;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyAssume;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for <code>fn:format-number</code> ({@link FormatNumber},
 * F&amp;O 3.1 section 4.7) with xs:decimal input, whose exact value makes the
 * rounding (half to even) predictable; {@link BigDecimal} serves as reference.
 *
 * @author Philip Helger
 */
public final class FormatNumberPropertyTest
{
  /** Decimals with up to 10 digit unscaled values and up to 6 fraction digits */
  private static final IGenerator <BigDecimal> DECIMALS = Generators.lists (Generators.longs (-9_999_999_999L, 9_999_999_999L), 2, 2)
                                                                    .map (x -> BigDecimal.valueOf (x.get (0).longValue (),
                                                                                                   (int) Math.floorMod (x.get (1)
                                                                                                                         .longValue (),
                                                                                                                        7)));

  private final XPathPropertyTestHelper m_aHelper = new XPathPropertyTestHelper ();

  @NonNull
  private String _format (@NonNull final BigDecimal aValue, @NonNull final String sPicture)
  {
    final XdmSequence aResult = m_aHelper.eval ("format-number($v, $w)",
                                                null,
                                                XdmSequence.of (XdmAtomic.decimal (aValue)),
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
   * The model of a picture with one mandatory integer digit, optional grouping
   * by three, and the given minimum and maximum number of fraction digits.
   */
  @NonNull
  private static String _model (@NonNull final BigDecimal aValue,
                                final boolean bGrouping,
                                final int nMinFraction,
                                final int nMaxFraction)
  {
    BigDecimal aRounded = aValue.abs ().setScale (nMaxFraction, RoundingMode.HALF_EVEN);
    // Drop the optional fraction digits that are zero
    while (aRounded.scale () > nMinFraction && aRounded.unscaledValue ().mod (BigInteger.TEN).signum () == 0)
      aRounded = aRounded.setScale (aRounded.scale () - 1);
    final String sPlain = aRounded.toPlainString ();
    final int nDot = sPlain.indexOf ('.');
    final String sInt = nDot < 0 ? sPlain : sPlain.substring (0, nDot);
    final String sFraction = nDot < 0 ? "" : sPlain.substring (nDot);
    return (aValue.signum () < 0 ? "-" : "") + (bGrouping ? _group (sInt) : sInt) + sFraction;
  }

  /**
   * <code>0.###</code>, <code>#,##0.00</code>, <code>0.0%</code> and a
   * negative sub-picture round the exact decimal value half to even to the
   * maximum number of fraction digits, drop optional trailing zeros and group
   * the integer digits by three (F&amp;O 3.1 section 4.7.5).
   */
  @Test
  public void testDecimalPictures ()
  {
    new PropertyChecker ().setTries (3000).forAll (DECIMALS, aValue -> {
      // Whether a negative value that rounds to zero keeps its sign is left out
      PropertyAssume.that (aValue.signum () >= 0 || aValue.abs ().setScale (0, RoundingMode.HALF_EVEN).signum () != 0);
      final String sMsg = aValue.toPlainString ();
      assertEquals (sMsg, _model (aValue, false, 0, 3), _format (aValue, "0.###"));
      assertEquals (sMsg, _model (aValue, true, 2, 2), _format (aValue, "#,##0.00"));
      assertEquals (sMsg, _model (aValue.movePointRight (2), false, 1, 1) + "%", _format (aValue, "0.0%"));
      final String sPositive = _model (aValue.abs (), true, 0, 0);
      assertEquals (sMsg, aValue.signum () < 0 ? "(" + sPositive + ")" : sPositive, _format (aValue, "#,##0;(#,##0)"));
    });
  }

  /**
   * Any picture text either formats the value or is rejected with FODF1310,
   * never with another exception.
   */
  @Test
  public void testArbitraryPictures ()
  {
    final IGenerator <String> aPictures = Generators.strings ("0#.,;%\u2030eE-+9a' ", 0, 10);
    new PropertyChecker ().setTries (3000).forAll (DECIMALS, aPictures, (aValue, sPicture) -> {
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

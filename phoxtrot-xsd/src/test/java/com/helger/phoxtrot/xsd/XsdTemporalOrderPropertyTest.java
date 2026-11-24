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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.base.string.StringHelper;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the bound facets (minInclusive, minExclusive, maxInclusive,
 * maxExclusive) on the date and time types and on xs:duration (XML Schema 1.1 Part 2, sections
 * 3.3.6 to 3.3.13 and 4.3.7 to 4.3.10). The expected order is computed with
 * <code>java.time</code>: values with a timezone are compared on the time line, values without
 * among themselves by their local fields, and a value without timezone against one with a timezone
 * only where all offsets from -14:00 to +14:00 agree (otherwise the two are incomparable, and a
 * bound facet is not satisfied). Durations are ordered by adding them to the four reference
 * dateTimes of the specification; where these disagree the two durations are incomparable.
 *
 * @author Philip Helger
 */
public final class XsdTemporalOrderPropertyTest
{
  private static final String [] TEMPORAL_TYPES = { "dateTime", "date", "time", "gYearMonth", "gYear" };
  private static final String [] BOUND_FACETS = { "minInclusive", "minExclusive", "maxInclusive", "maxExclusive" };
  private static final int [] YEARS = { 2000, 1999, 2024, 1, 9999, 10000 };
  private static final int [] MONTHS = { 1, 2, 12 };
  private static final int [] DAYS = { 1, 2, 28, 31 };
  private static final int [] HOURS = { 0, 1, 12, 23, 24 };
  private static final int [] MINUTES = { 0, 30, 59 };
  private static final String [] FRACTIONS = { "", ".5", ".50", ".001" };
  /** Timezone offsets in minutes and their lexical forms; null for no timezone */
  private static final int [] OFFSETS = { 0, 0, 60, -330, 840, -840 };
  private static final String [] OFFSET_LEXICAL = { "Z", "+00:00", "+01:00", "-05:30", "+14:00", "-14:00" };
  /** The day fields of the generated durations, -1 for none */
  private static final int [] DURATION_DAYS = { -1, 0, 1, 27, 28, 29, 30, 31 };
  /** The maximum timezone offset in seconds */
  private static final long MAX_OFFSET_SECONDS = 14 * 3600;

  /** The four dateTimes the order of durations is defined with */
  private static final LocalDateTime [] DURATION_REFERENCES = { LocalDateTime.of (1696, 9, 1, 0, 0),
                                                                LocalDateTime.of (1697, 2, 1, 0, 0),
                                                                LocalDateTime.of (1903, 3, 1, 0, 0),
                                                                LocalDateTime.of (1903, 7, 1, 0, 0) };

  /** The result of a comparison in a partial order */
  private enum EOrder
  {
    LESS,
    EQUAL,
    GREATER,
    INDETERMINATE
  }

  /**
   * A generated value of a date or time type.
   *
   * @param lexical
   *        The lexical form.
   * @param seconds
   *        The seconds of its local date and time since 1970 (UTC as if it were in UTC), the end of
   *        day 24:00:00 already moved to the next day.
   * @param fraction
   *        The fraction of a second.
   * @param offsetSeconds
   *        The timezone offset in seconds, <code>null</code> for no timezone.
   */
  private record Temporal (@NonNull String lexical,
                           long seconds,
                           @NonNull BigDecimal fraction,
                           @Nullable Integer offsetSeconds)
  {
    @Override
    public String toString ()
    {
      return lexical;
    }
  }

  /**
   * A date or time type with generated values of it.
   *
   * @param type
   *        The local name of the type.
   * @param values
   *        The values.
   */
  private record TemporalCase (@NonNull String type, @NonNull List <Temporal> values)
  {}

  /**
   * A generated duration.
   *
   * @param lexical
   *        The lexical form.
   * @param months
   *        The months of the value.
   * @param seconds
   *        The seconds of the value.
   */
  private record Duration (@NonNull String lexical, long months, @NonNull BigDecimal seconds)
  {
    @Override
    public String toString ()
    {
      return lexical;
    }
  }

  @NonNull
  private static EOrder _fromCompare (final int nCmp)
  {
    return nCmp < 0 ? EOrder.LESS : nCmp > 0 ? EOrder.GREATER : EOrder.EQUAL;
  }

  /**
   * @return The instant of a timezoned value as seconds since 1970, with fraction.
   */
  @NonNull
  private static BigDecimal _instant (@NonNull final Temporal aValue, final long nOffsetSeconds)
  {
    return BigDecimal.valueOf (aValue.seconds () - nOffsetSeconds).add (aValue.fraction ());
  }

  /**
   * Compare two values of the same date or time type in the partial order of XML Schema.
   */
  @NonNull
  private static EOrder _compare (@NonNull final Temporal aLeft, @NonNull final Temporal aRight)
  {
    final Integer aLeftOffset = aLeft.offsetSeconds ();
    final Integer aRightOffset = aRight.offsetSeconds ();
    if (aLeftOffset != null && aRightOffset != null)
      return _fromCompare (_instant (aLeft, aLeftOffset.intValue ()).compareTo (_instant (aRight,
                                                                                         aRightOffset.intValue ())));
    if (aLeftOffset == null && aRightOffset == null)
      return _fromCompare (_instant (aLeft, 0).compareTo (_instant (aRight, 0)));
    if (aLeftOffset == null)
    {
      // The value without timezone stands for any instant between its local time at +14:00 and at -14:00
      if (_instant (aLeft, -MAX_OFFSET_SECONDS).compareTo (_instant (aRight, aRightOffset.intValue ())) < 0)
        return EOrder.LESS;
      if (_instant (aLeft, MAX_OFFSET_SECONDS).compareTo (_instant (aRight, aRightOffset.intValue ())) > 0)
        return EOrder.GREATER;
      return EOrder.INDETERMINATE;
    }
    final EOrder eReverse = _compare (aRight, aLeft);
    return switch (eReverse)
    {
      case LESS -> EOrder.GREATER;
      case GREATER -> EOrder.LESS;
      default -> eReverse;
    };
  }

  /**
   * @return <code>true</code> if a comparison result satisfies the bound facet with the value on
   *         the left.
   */
  private static boolean _satisfies (@NonNull final String sFacet, @NonNull final EOrder eOrder)
  {
    return switch (sFacet)
    {
      case "minInclusive" -> eOrder == EOrder.GREATER || eOrder == EOrder.EQUAL;
      case "minExclusive" -> eOrder == EOrder.GREATER;
      case "maxInclusive" -> eOrder == EOrder.LESS || eOrder == EOrder.EQUAL;
      default -> eOrder == EOrder.LESS;
    };
  }

  @NonNull
  private static String _year (final int nYear)
  {
    return StringHelper.getLeadingZero (nYear, 4);
  }

  @NonNull
  private static String _two (final int n)
  {
    return StringHelper.getLeadingZero (n, 2);
  }

  /**
   * @return A generator of values of the type; with <code>nTimezoneMode</code> 0 none has a
   *         timezone, with 1 all have one, with 2 each may have one.
   */
  @NonNull
  private static IGenerator <Temporal> _temporals (@NonNull final String sType, final int nTimezoneMode)
  {
    return Generators.lists (Generators.ints (0, 63), 9, 9).map (aDraws -> {
      final int nYear = YEARS[aDraws.get (0).intValue () % YEARS.length];
      final int nMonth = MONTHS[aDraws.get (1).intValue () % MONTHS.length];
      int nDay = DAYS[aDraws.get (2).intValue () % DAYS.length];
      if (nMonth == 2 && nDay > 28)
        nDay = 28;
      int nHour = HOURS[aDraws.get (3).intValue () % HOURS.length];
      int nMinute = MINUTES[aDraws.get (4).intValue () % MINUTES.length];
      int nSecond = aDraws.get (5).intValue () % 2 == 0 ? 0 : 59;
      String sFraction = FRACTIONS[aDraws.get (6).intValue () % FRACTIONS.length];
      if (nHour == 24)
      {
        // The end of the day is only allowed with zero minutes and seconds
        nMinute = 0;
        nSecond = 0;
        sFraction = "";
        if (!sType.equals ("dateTime"))
          nHour = 23;
      }
      final boolean bTimezone = nTimezoneMode == 1 || (nTimezoneMode == 2 && aDraws.get (7).intValue () % 2 == 0);
      final int nOffsetIndex = aDraws.get (8).intValue () % OFFSETS.length;

      final StringBuilder aLexical = new StringBuilder ();
      LocalDateTime aLocal;
      switch (sType)
      {
        case "dateTime" ->
        {
          aLexical.append (_year (nYear))
                  .append ('-')
                  .append (_two (nMonth))
                  .append ('-')
                  .append (_two (nDay))
                  .append ('T')
                  .append (_two (nHour))
                  .append (':')
                  .append (_two (nMinute))
                  .append (':')
                  .append (_two (nSecond))
                  .append (sFraction);
          aLocal = nHour == 24 ? LocalDateTime.of (nYear, nMonth, nDay, 0, 0).plusDays (1)
                               : LocalDateTime.of (nYear, nMonth, nDay, nHour, nMinute, nSecond);
        }
        case "date" ->
        {
          aLexical.append (_year (nYear)).append ('-').append (_two (nMonth)).append ('-').append (_two (nDay));
          aLocal = LocalDateTime.of (nYear, nMonth, nDay, 0, 0);
          sFraction = "";
        }
        case "time" ->
        {
          aLexical.append (_two (nHour))
                  .append (':')
                  .append (_two (nMinute))
                  .append (':')
                  .append (_two (nSecond))
                  .append (sFraction);
          // Any reference date gives the same order
          aLocal = LocalDateTime.of (1972, 12, 31, nHour, nMinute, nSecond);
        }
        case "gYearMonth" ->
        {
          aLexical.append (_year (nYear)).append ('-').append (_two (nMonth));
          aLocal = LocalDateTime.of (nYear, nMonth, 1, 0, 0);
          sFraction = "";
        }
        default ->
        {
          aLexical.append (_year (nYear));
          aLocal = LocalDateTime.of (nYear, 1, 1, 0, 0);
          sFraction = "";
        }
      }
      Integer aOffset = null;
      if (bTimezone)
      {
        aLexical.append (OFFSET_LEXICAL[nOffsetIndex]);
        aOffset = Integer.valueOf (OFFSETS[nOffsetIndex] * 60);
      }
      final BigDecimal aFraction = sFraction.isEmpty () ? BigDecimal.ZERO : new BigDecimal ("0" + sFraction);
      return new Temporal (aLexical.toString (), aLocal.toEpochSecond (ZoneOffset.UTC), aFraction, aOffset);
    });
  }

  private static void _assertValid (@NonNull final XsdSimpleType @NonNull [] aTypes,
                                    @NonNull final String sMessage,
                                    final boolean bExpected,
                                    @NonNull final String sValue)
  {
    assertEquals (sMessage + " (1.0)",
                  Boolean.valueOf (bExpected),
                  Boolean.valueOf (XsdPropertyTestHelper.isValidValue (aTypes[0], sValue, EXsdVersion.V10)));
    assertEquals (sMessage + " (1.1)",
                  Boolean.valueOf (bExpected),
                  Boolean.valueOf (XsdPropertyTestHelper.isValidValue (aTypes[1], sValue, EXsdVersion.V11)));
  }

  @Nullable
  private static XsdSimpleType @Nullable [] _compile (@NonNull final String sBase, @NonNull final String sFacets)
  {
    final XsdSimpleType aType10 = XsdPropertyTestHelper.compileRestriction (sBase, sFacets, EXsdVersion.V10);
    final XsdSimpleType aType11 = XsdPropertyTestHelper.compileRestriction (sBase, sFacets, EXsdVersion.V11);
    assertEquals (sFacets + " (1.0 vs 1.1)", Boolean.valueOf (aType10 != null), Boolean.valueOf (aType11 != null));
    return aType10 == null ? null : new XsdSimpleType [] { aType10, aType11 };
  }

  /**
   * @return A generator of a type index, a timezone mode and <code>nCount</code> values of that
   *         type, the first being the facet value.
   */
  @NonNull
  private static IGenerator <TemporalCase> _cases (final int nCount)
  {
    return Generators.ints (0, TEMPORAL_TYPES.length - 1)
                     .flatMap (aType -> Generators.ints (0, 2)
                                                  .flatMap (aMode -> Generators.lists (_temporals (TEMPORAL_TYPES[aType.intValue ()],
                                                                                                   aMode.intValue ()),
                                                                                       nCount,
                                                                                       nCount)
                                                                               .map (aValues -> new TemporalCase (TEMPORAL_TYPES[aType.intValue ()],
                                                                                                                  aValues))));
  }

  /**
   * Model property: a restriction of a date or time type with one bound facet accepts a value
   * exactly when it stands in the required relation to the bound in the partial order, with and
   * without timezones, also mixed.
   */
  @Test
  public void testSingleBound ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (_cases (7), Generators.ints (0, BOUND_FACETS.length - 1), (aCase, aFacet) -> {
                            final String sType = aCase.type ();
                            final String sFacet = BOUND_FACETS[aFacet.intValue ()];
                            final Temporal aBound = aCase.values ().get (0);
                            final String sFacets = "<xs:" + sFacet + " value=\"" + aBound.lexical () + "\"/>";
                            final XsdSimpleType [] aTypes = _compile (sType, sFacets);
                            assertNotNull (sType + " " + sFacets, aTypes);
                            for (final Temporal aValue : aCase.values ())
                            {
                              final EOrder eOrder = _compare (aValue, aBound);
                              _assertValid (aTypes,
                                            sType + " " + sFacets + " " + aValue + " (" + eOrder + ")",
                                            _satisfies (sFacet, eOrder),
                                            aValue.lexical ());
                            }
                          });
  }

  /**
   * Model property: with a lower and an upper bound the restriction is a schema error when the
   * lower bound is greater than the upper one, valid when it is less, and accepts a value exactly
   * when both bounds are satisfied.
   */
  @Test
  public void testTwoBounds ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (_cases (8), Generators.booleans (), Generators.booleans (), (aCase, aMinInclusive, aMaxInclusive) -> {
                            final String sType = aCase.type ();
                            final String sMin = aMinInclusive.booleanValue () ? "minInclusive" : "minExclusive";
                            final String sMax = aMaxInclusive.booleanValue () ? "maxInclusive" : "maxExclusive";
                            final Temporal aLow = aCase.values ().get (0);
                            final Temporal aHigh = aCase.values ().get (1);
                            final String sFacets = "<xs:" +
                                                   sMin +
                                                   " value=\"" +
                                                   aLow.lexical () +
                                                   "\"/><xs:" +
                                                   sMax +
                                                   " value=\"" +
                                                   aHigh.lexical () +
                                                   "\"/>";
                            final XsdSimpleType [] aTypes = _compile (sType, sFacets);
                            final EOrder eBounds = _compare (aLow, aHigh);
                            if (eBounds == EOrder.LESS)
                              assertNotNull (sType + " " + sFacets + " must compile", aTypes);
                            if (eBounds == EOrder.GREATER)
                              assertNull (sType + " " + sFacets + " must not compile", aTypes);
                            if (aTypes != null)
                              for (final Temporal aValue : aCase.values ())
                              {
                                final EOrder eLow = _compare (aValue, aLow);
                                final EOrder eHigh = _compare (aValue, aHigh);
                                _assertValid (aTypes,
                                              sType + " " + sFacets + " " + aValue + " (" + eLow + ", " + eHigh + ")",
                                              _satisfies (sMin, eLow) && _satisfies (sMax, eHigh),
                                              aValue.lexical ());
                              }
                          });
  }

  /**
   * @return A generator of durations with small field values, possibly negative, with fractional
   *         seconds.
   */
  @NonNull
  private static IGenerator <Duration> _durations ()
  {
    return Generators.lists (Generators.ints (0, 40), 7, 7).map (aDraws -> {
      final boolean bNegative = aDraws.get (0).intValue () % 3 == 2;
      // Each field is absent (-1) or has a small value
      final int nYears = aDraws.get (1).intValue () % 4 - 2;
      final int nMonths = aDraws.get (2).intValue () % 5 - 2;
      // Day counts around the month lengths make incomparable pairs likely
      final int nDays = DURATION_DAYS[aDraws.get (3).intValue () % DURATION_DAYS.length];
      final int nHours = aDraws.get (4).intValue () % 30 - 2;
      final int nMinutes = aDraws.get (5).intValue () % 4 - 2;
      final int nSecondsDraw = aDraws.get (6).intValue () % 5 - 1;
      final String sSeconds = nSecondsDraw < 0 ? null : new String [] { "0", "1.5", "59", "3600" }[nSecondsDraw];

      final StringBuilder aSB = new StringBuilder ();
      if (bNegative)
        aSB.append ('-');
      aSB.append ('P');
      long nTotalMonths = 0;
      BigDecimal aTotalSeconds = BigDecimal.ZERO;
      if (nYears >= 0)
      {
        aSB.append (nYears).append ('Y');
        nTotalMonths += 12L * nYears;
      }
      if (nMonths >= 0)
      {
        aSB.append (nMonths).append ('M');
        nTotalMonths += nMonths;
      }
      if (nDays >= 0)
      {
        aSB.append (nDays).append ('D');
        aTotalSeconds = aTotalSeconds.add (BigDecimal.valueOf (86400L * nDays));
      }
      if (nHours >= 0 || nMinutes >= 0 || sSeconds != null)
      {
        aSB.append ('T');
        if (nHours >= 0)
        {
          aSB.append (nHours).append ('H');
          aTotalSeconds = aTotalSeconds.add (BigDecimal.valueOf (3600L * nHours));
        }
        if (nMinutes >= 0)
        {
          aSB.append (nMinutes).append ('M');
          aTotalSeconds = aTotalSeconds.add (BigDecimal.valueOf (60L * nMinutes));
        }
        if (sSeconds != null)
        {
          aSB.append (sSeconds).append ('S');
          aTotalSeconds = aTotalSeconds.add (new BigDecimal (sSeconds));
        }
      }
      else
        if (nYears < 0 && nMonths < 0 && nDays < 0)
        {
          // At least one field is needed
          aSB.append ("0D");
        }
      if (bNegative)
        return new Duration (aSB.toString (), -nTotalMonths, aTotalSeconds.negate ());
      return new Duration (aSB.toString (), nTotalMonths, aTotalSeconds);
    });
  }

  /**
   * @return The duration added to a reference dateTime, as seconds since 1970 with fraction.
   */
  @NonNull
  private static BigDecimal _addTo (@NonNull final LocalDateTime aReference, @NonNull final Duration aDuration)
  {
    final LocalDateTime aMonthsAdded = aReference.plusMonths (aDuration.months ());
    return BigDecimal.valueOf (aMonthsAdded.toEpochSecond (ZoneOffset.UTC)).add (aDuration.seconds ());
  }

  /**
   * Compare two durations in the partial order of XML Schema (XML Schema 1.1 Part 2, section
   * 3.3.6.2): by the results of adding both to each of the four reference dateTimes.
   */
  @NonNull
  private static EOrder _compareDurations (@NonNull final Duration aLeft, @NonNull final Duration aRight)
  {
    if (aLeft.months () == aRight.months () && aLeft.seconds ().compareTo (aRight.seconds ()) == 0)
      return EOrder.EQUAL;
    EOrder ret = null;
    for (final LocalDateTime aReference : DURATION_REFERENCES)
    {
      final EOrder eCur = _fromCompare (_addTo (aReference, aLeft).compareTo (_addTo (aReference, aRight)));
      if (ret == null)
        ret = eCur;
      else
        if (ret != eCur)
          return EOrder.INDETERMINATE;
    }
    // Equal at all four references but different values cannot happen
    return ret == EOrder.EQUAL ? EOrder.INDETERMINATE : ret;
  }

  /**
   * Model property: a restriction of xs:duration with one bound facet accepts a value exactly when
   * it stands in the required relation to the bound in the partial order of durations; values like
   * <code>P1M</code> and <code>P30D</code> are incomparable.
   */
  @Test
  public void testDurationBound ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (Generators.lists (_durations (), 7, 7),
                                   Generators.ints (0, BOUND_FACETS.length - 1),
                                   (aValues, aFacet) -> {
                                     final String sFacet = BOUND_FACETS[aFacet.intValue ()];
                                     final Duration aBound = aValues.get (0);
                                     final String sFacets = "<xs:" + sFacet + " value=\"" + aBound.lexical () + "\"/>";
                                     final XsdSimpleType [] aTypes = _compile ("duration", sFacets);
                                     assertNotNull (sFacets, aTypes);
                                     for (final Duration aValue : aValues)
                                     {
                                       final EOrder eOrder = _compareDurations (aValue, aBound);
                                       _assertValid (aTypes,
                                                     sFacets + " " + aValue + " (" + eOrder + ")",
                                                     _satisfies (sFacet, eOrder),
                                                     aValue.lexical ());
                                     }
                                   });
  }
}

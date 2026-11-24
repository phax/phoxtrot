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
package com.helger.phoxtrot.xpath.value;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.string.StringHelper;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the date, time and duration arithmetic of
 * {@link DateTimeOps} and the functions on these types (F&amp;O 3.1 sections
 * 9 and 10), with <code>java.time</code> (proleptic Gregorian, like XSD) as
 * the reference: addition and subtraction of durations, the difference of two
 * dateTimes, comparison across timezones, timezone adjustment, component
 * extraction, month arithmetic of dates and <code>fn:format-dateTime</code>
 * with numeric pictures.
 *
 * @author Philip Helger
 */
public final class DateTimeArithmeticPropertyTest
{
  private static final String [] VAR_NAMES = { "a", "b", "c" };
  private static final LocalDateTime MIN = LocalDateTime.of (1, 1, 1, 0, 0);
  private static final LocalDateTime MAX = LocalDateTime.of (9999, 12, 31, 23, 59, 59, 999_000_000);

  /** Timezone offsets in minutes: whole and quarter hours, odd minutes and the extremes */
  private static final IGenerator <Integer> OFFSETS = Generators.oneOf (Generators.ints (-56, 56).map (x -> Integer.valueOf (x.intValue () * 15)),
                                                                        Generators.ints (-840, 840),
                                                                        Generators.of (Integer.valueOf (0),
                                                                                       Integer.valueOf (-840),
                                                                                       Integer.valueOf (840)));

  /** Local dateTimes with millisecond precision between the years 1 and 9999 */
  private static final IGenerator <LocalDateTime> LOCAL_DATE_TIMES = Generators.oneOf (Generators.lists (Generators.ints (0,
                                                                                                                         Integer.MAX_VALUE),
                                                                                                         4,
                                                                                                         4)
                                                                                                 .map (x -> LocalDateTime.of (1 +
                                                                                                                              x.get (0)
                                                                                                                               .intValue () %
                                                                                                                                   9999,
                                                                                                                              1,
                                                                                                                              1,
                                                                                                                              0,
                                                                                                                              0)
                                                                                                                          .plusDays (x.get (1)
                                                                                                                                      .intValue () %
                                                                                                                                     366)
                                                                                                                          .plusSeconds (x.get (2)
                                                                                                                                         .intValue () %
                                                                                                                                        86400)
                                                                                                                          .plusNanos (x.get (3)
                                                                                                                                       .intValue () %
                                                                                                                                      1000 *
                                                                                                                                      1_000_000L)),
                                                                                       Generators.lists (Generators.ints (0, 3000), 2, 2)
                                                                                                 .map (x -> LocalDateTime.of (2000, 1, 1, 0, 0)
                                                                                                                          .plusDays (x.get (0)
                                                                                                                                      .intValue ())
                                                                                                                          .plusMinutes (x.get (1)
                                                                                                                                         .intValue ()))
                                                                                                 .filter (x -> !x.isAfter (MAX)))
                                                                               .filter (x -> !x.isAfter (MAX));

  private static final IGenerator <OffsetDateTime> DATE_TIMES = LOCAL_DATE_TIMES.flatMap (aLDT -> OFFSETS.map (aOffset -> OffsetDateTime.of (aLDT,
                                                                                                                                       ZoneOffset.ofTotalSeconds (aOffset.intValue () *
                                                                                                                                                                  60))));

  /** Day-time durations in milliseconds, up to about 300 years either way */
  private static final IGenerator <Long> DURATION_MILLIS = Generators.oneOf (Generators.longs (-10_000_000_000_000L, 10_000_000_000_000L),
                                                                             Generators.longs (-100_000_000L, 100_000_000L),
                                                                             Generators.longs (-86_400_000L * 400, 86_400_000L * 400)
                                                                                       .map (x -> Long.valueOf (x.longValue () / 1000 * 1000)),
                                                                             Generators.of (Long.valueOf (0),
                                                                                            Long.valueOf (1),
                                                                                            Long.valueOf (-1),
                                                                                            Long.valueOf (86_400_000L)));

  private final Map <String, CompiledXPath> m_aCache = new HashMap <> ();
  private final StaticContext m_aStaticCtx = new StaticContext ();

  public DateTimeArithmeticPropertyTest ()
  {
    for (final String sName : VAR_NAMES)
      m_aStaticCtx.declareVariable (new XdmQName (sName));
  }

  /** Evaluate with the strings bound to <code>$a</code>, <code>$b</code> and <code>$c</code>. */
  @NonNull
  private XdmAtomic _eval (@NonNull final String sExpr, @NonNull final String... aValues)
  {
    final CompiledXPath aXPath = m_aCache.computeIfAbsent (sExpr, x -> XPathCompiler.compile (x, m_aStaticCtx));
    DynamicContext aCtx = aXPath.createDynamicContext ();
    for (int i = 0; i < VAR_NAMES.length; ++i)
      aCtx = aCtx.withVariable (new XdmQName (VAR_NAMES[i]),
                                i < aValues.length ? XdmSequence.of (XdmAtomic.string (aValues[i])) : XdmSequence.EMPTY);
    final XdmSequence aResult = aXPath.evaluate (aCtx);
    assertEquals (sExpr, 1, aResult.size ());
    return (XdmAtomic) aResult.get (0);
  }

  private void _assertTrue (@NonNull final String sMsg, @NonNull final String sExpr, @NonNull final String... aValues)
  {
    assertTrue (sMsg + ": " + sExpr, _eval (sExpr, aValues).getBoolean ());
  }

  @NonNull
  private static String _timezone (final int nOffsetSeconds, final boolean bCanonical)
  {
    if (nOffsetSeconds == 0 && bCanonical)
      return "Z";
    final int nMinutes = Math.abs (nOffsetSeconds / 60);
    return (nOffsetSeconds < 0 ? "-" : "+") +
           StringHelper.getLeadingZero (nMinutes / 60, 2) +
           ":" +
           StringHelper.getLeadingZero (nMinutes % 60, 2);
  }

  /** @return The local part in the canonical form: no trailing fraction zeros. */
  @NonNull
  private static String _localLexical (@NonNull final LocalDateTime aLDT)
  {
    final StringBuilder aSB = new StringBuilder ();
    aSB.append (StringHelper.getLeadingZero (aLDT.getYear (), 4))
       .append ('-')
       .append (StringHelper.getLeadingZero (aLDT.getMonthValue (), 2))
       .append ('-')
       .append (StringHelper.getLeadingZero (aLDT.getDayOfMonth (), 2))
       .append ('T')
       .append (StringHelper.getLeadingZero (aLDT.getHour (), 2))
       .append (':')
       .append (StringHelper.getLeadingZero (aLDT.getMinute (), 2))
       .append (':')
       .append (StringHelper.getLeadingZero (aLDT.getSecond (), 2));
    final int nMillis = aLDT.getNano () / 1_000_000;
    if (nMillis != 0)
    {
      String sFraction = StringHelper.getLeadingZero (nMillis, 3);
      while (sFraction.endsWith ("0"))
        sFraction = sFraction.substring (0, sFraction.length () - 1);
      aSB.append ('.').append (sFraction);
    }
    return aSB.toString ();
  }

  /** @return The canonical lexical form of an xs:dateTime (XSD 1.1 section 3.3.8). */
  @NonNull
  private static String _lexical (@NonNull final OffsetDateTime aODT)
  {
    return _localLexical (aODT.toLocalDateTime ()) + _timezone (aODT.getOffset ().getTotalSeconds (), true);
  }

  /** @return A lexical form that is not canonical for a zero offset (+00:00 instead of Z). */
  @NonNull
  private static String _nonCanonicalLexical (@NonNull final OffsetDateTime aODT)
  {
    return _localLexical (aODT.toLocalDateTime ()) + _timezone (aODT.getOffset ().getTotalSeconds (), false);
  }

  /** @return A dayTimeDuration lexical form with the seconds only, e.g. <code>-PT12.5S</code>. */
  @NonNull
  private static String _durationLexical (final long nMillis)
  {
    final BigDecimal aSeconds = BigDecimal.valueOf (Math.abs (nMillis), 3).stripTrailingZeros ();
    return (nMillis < 0 ? "-" : "") + "PT" + aSeconds.toPlainString () + "S";
  }

  private static boolean _isInRange (@NonNull final LocalDateTime aLDT)
  {
    return !aLDT.isBefore (MIN) && !aLDT.isAfter (MAX);
  }

  /**
   * Adding a dayTimeDuration to a dateTime adds the time to the local value
   * and keeps the timezone (F&amp;O 3.1 section 10.8.4, XSD 1.1 appendix E);
   * subtracting it again gives the original value.
   */
  @Test
  public void testAddDayTimeDuration ()
  {
    new PropertyChecker ().setTries (2000).forAll (DATE_TIMES, DURATION_MILLIS, Generators.booleans (), (aODT, aMillis, aCanonical) -> {
      final long nMillis = aMillis.longValue ();
      final OffsetDateTime aExpected = aODT.plus (Duration.ofMillis (nMillis));
      if (!_isInRange (aExpected.toLocalDateTime ()))
        return;
      final String sDT = aCanonical.booleanValue () ? _lexical (aODT) : _nonCanonicalLexical (aODT);
      final String sDur = _durationLexical (nMillis);
      final String sMsg = sDT + " + " + sDur;
      assertEquals (sMsg, _lexical (aExpected), _eval ("string(xs:dateTime($a) + xs:dayTimeDuration($b))", sDT, sDur).getString ());
      assertEquals (sMsg, _lexical (aExpected), _eval ("string(xs:dayTimeDuration($b) + xs:dateTime($a))", sDT, sDur).getString ());
      _assertTrue (sMsg, "xs:dateTime($a) + xs:dayTimeDuration($b) - xs:dayTimeDuration($b) eq xs:dateTime($a)", sDT, sDur);
      _assertTrue (sMsg, "xs:dateTime($a) - xs:dayTimeDuration($b) eq xs:dateTime($a) + xs:dayTimeDuration($b) * -1", sDT, sDur);
    });
  }

  /**
   * The difference of two dateTimes is the dayTimeDuration between their
   * instants, whatever their timezones (F&amp;O 3.1 section 10.8.1), and
   * adding it back gives the same instant.
   */
  @Test
  public void testSubtractDateTimes ()
  {
    new PropertyChecker ().setTries (2000).forAll (DATE_TIMES, DATE_TIMES, (aODT1, aODT2) -> {
      final long nMillis = ChronoUnit.MILLIS.between (aODT2, aODT1);
      final String s1 = _lexical (aODT1);
      final String s2 = _lexical (aODT2);
      final String sMsg = s1 + " - " + s2;
      _assertTrue (sMsg, "xs:dateTime($a) - xs:dateTime($b) eq xs:dayTimeDuration($c)", s1, s2, _durationLexical (nMillis));
      _assertTrue (sMsg, "xs:dateTime($b) + (xs:dateTime($a) - xs:dateTime($b)) eq xs:dateTime($a)", s1, s2);
    });
  }

  /**
   * The value comparisons of dateTimes with different timezones compare the
   * instants (F&amp;O 3.1 section 10.4.6), as do <code>min</code>,
   * <code>max</code> and <code>distinct-values</code>.
   */
  @Test
  public void testCompareAcrossTimezones ()
  {
    // The second dateTime is often the same instant in another timezone
    final IGenerator <OffsetDateTime []> aPairs = Generators.oneOf (Generators.lists (DATE_TIMES, 2, 2)
                                                                              .map (x -> new OffsetDateTime [] { x.get (0), x.get (1) }),
                                                                    DATE_TIMES.flatMap (aODT -> OFFSETS.map (aOffset -> new OffsetDateTime [] { aODT,
                                                                                                                                              aODT.withOffsetSameInstant (ZoneOffset.ofTotalSeconds (aOffset.intValue () *
                                                                                                                                                                                                     60)) }))
                                                                              .filter (x -> _isInRange (x[1].toLocalDateTime ())));
    new PropertyChecker ().setTries (2000).forAll (aPairs, aPair -> {
      final int nCmp = Integer.signum (aPair[0].toInstant ().compareTo (aPair[1].toInstant ()));
      final String s1 = _lexical (aPair[0]);
      final String s2 = _lexical (aPair[1]);
      final String sMsg = s1 + " vs. " + s2 + " expected " + nCmp;
      assertEquals (sMsg, Boolean.valueOf (nCmp < 0), Boolean.valueOf (_eval ("xs:dateTime($a) lt xs:dateTime($b)", s1, s2).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (nCmp == 0), Boolean.valueOf (_eval ("xs:dateTime($a) eq xs:dateTime($b)", s1, s2).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (nCmp > 0), Boolean.valueOf (_eval ("xs:dateTime($a) gt xs:dateTime($b)", s1, s2).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (nCmp <= 0), Boolean.valueOf (_eval ("xs:dateTime($a) le xs:dateTime($b)", s1, s2).getBoolean ()));
      assertEquals (sMsg,
                    nCmp == 0 ? "1" : "2",
                    _eval ("string(count(distinct-values((xs:dateTime($a), xs:dateTime($b)))))", s1, s2).getString ());
      assertEquals (sMsg,
                    nCmp <= 0 ? s1 : s2,
                    _eval ("string(min((xs:dateTime($a), xs:dateTime($b))))", s1, s2).getString ());
      assertEquals (sMsg,
                    nCmp >= 0 ? s1 : s2,
                    _eval ("string(max((xs:dateTime($a), xs:dateTime($b))))", s1, s2).getString ());
    });
  }

  /**
   * <code>fn:adjust-dateTime-to-timezone</code> keeps the instant and sets
   * the timezone, and the component functions of the result give the local
   * value in the new timezone (F&amp;O 3.1 sections 10.5 and 10.7.1);
   * adjusting to the empty sequence drops the timezone and keeps the local
   * value.
   */
  @Test
  public void testAdjustToTimezone ()
  {
    new PropertyChecker ().setTries (2000).forAll (DATE_TIMES, OFFSETS, (aODT, aOffset) -> {
      final OffsetDateTime aExpected = aODT.withOffsetSameInstant (ZoneOffset.ofTotalSeconds (aOffset.intValue () * 60));
      if (!_isInRange (aExpected.toLocalDateTime ()))
        return;
      final String sDT = _lexical (aODT);
      final String sTZ = _durationLexical (aOffset.intValue () * 60_000L);
      final String sMsg = sDT + " to " + sTZ;
      final String sAdjust = "adjust-dateTime-to-timezone(xs:dateTime($a), xs:dayTimeDuration($b))";
      assertEquals (sMsg, _lexical (aExpected), _eval ("string(" + sAdjust + ")", sDT, sTZ).getString ());
      _assertTrue (sMsg, sAdjust + " eq xs:dateTime($a)", sDT, sTZ);
      _assertTrue (sMsg, "timezone-from-dateTime(" + sAdjust + ") eq xs:dayTimeDuration($b)", sDT, sTZ);
      final LocalDateTime aLocal = aExpected.toLocalDateTime ();
      assertEquals (sMsg,
                    aLocal.getYear () +
                          " " +
                          aLocal.getMonthValue () +
                          " " +
                          aLocal.getDayOfMonth () +
                          " " +
                          aLocal.getHour () +
                          " " +
                          aLocal.getMinute () +
                          " " +
                          BigDecimal.valueOf (aLocal.getSecond () * 1000L + aLocal.getNano () / 1_000_000, 3)
                                    .stripTrailingZeros ()
                                    .toPlainString (),
                    _eval ("let $d := " +
                           sAdjust +
                           " return string-join((year-from-dateTime($d), month-from-dateTime($d), day-from-dateTime($d), " +
                           "hours-from-dateTime($d), minutes-from-dateTime($d), seconds-from-dateTime($d)), ' ')",
                           sDT,
                           sTZ).getString ());
      assertEquals (sMsg,
                    _localLexical (aODT.toLocalDateTime ()),
                    _eval ("string(adjust-dateTime-to-timezone(xs:dateTime($a), ()))", sDT).getString ());
    });
  }

  /**
   * Adding months to a date pins the day to the last day of the resulting
   * month (F&amp;O 3.1 section 10.8.3 with XSD 1.1 appendix E.3.3), as
   * {@link LocalDate#plusMonths(long)} does; the difference of two dates
   * without timezone is a whole number of days.
   */
  @Test
  public void testDateArithmetic ()
  {
    final IGenerator <LocalDate> aDates = LOCAL_DATE_TIMES.map (LocalDateTime::toLocalDate);
    new PropertyChecker ().setTries (2000).forAll (aDates, aDates, Generators.ints (-30_000, 30_000), (aDate1, aDate2, aMonths) -> {
      final String s1 = aDate1.toString ();
      final String s2 = aDate2.toString ();
      final int nMonths = aMonths.intValue ();
      final LocalDate aExpected = aDate1.plusMonths (nMonths);
      final String sYM = (nMonths < 0 ? "-P" : "P") + Math.abs (nMonths) + "M";
      final String sMsg = s1 + " + " + sYM;
      if (aExpected.getYear () >= 1 && aExpected.getYear () <= 9999)
      {
        assertEquals (sMsg, aExpected.toString (), _eval ("string(xs:date($a) + xs:yearMonthDuration($b))", s1, sYM).getString ());
        assertEquals (sMsg,
                      aExpected.toString (),
                      _eval ("string(xs:date($a) - xs:yearMonthDuration($b))", s1, (nMonths > 0 ? "-P" : "P") + Math.abs (nMonths) + "M")
                        .getString ());
      }
      final long nDays = ChronoUnit.DAYS.between (aDate2, aDate1);
      _assertTrue (s1 + " - " + s2, "xs:date($a) - xs:date($b) eq xs:dayTimeDuration($c)", s1, s2, (nDays < 0 ? "-P" : "P") + Math.abs (nDays) + "D");
    });
  }

  /**
   * <code>fn:format-dateTime</code> with zero-padded numeric components gives
   * the digits of the value (F&amp;O 3.1 section 9.8.4), the day in year and
   * the English day name agree with <code>java.time</code>.
   */
  @Test
  public void testFormatDateTime ()
  {
    new PropertyChecker ().setTries (2000).forAll (DATE_TIMES, aODT -> {
      final String sDT = _lexical (aODT);
      final LocalDateTime aLocal = aODT.toLocalDateTime ();
      final String sExpectedLocal = StringHelper.getLeadingZero (aLocal.getYear (), 4) +
                                    "-" +
                                    StringHelper.getLeadingZero (aLocal.getMonthValue (), 2) +
                                    "-" +
                                    StringHelper.getLeadingZero (aLocal.getDayOfMonth (), 2) +
                                    "T" +
                                    StringHelper.getLeadingZero (aLocal.getHour (), 2) +
                                    ":" +
                                    StringHelper.getLeadingZero (aLocal.getMinute (), 2) +
                                    ":" +
                                    StringHelper.getLeadingZero (aLocal.getSecond (), 2) +
                                    "." +
                                    StringHelper.getLeadingZero (aLocal.getNano () / 1_000_000, 3);
      assertEquals (sDT,
                    sExpectedLocal,
                    _eval ("format-dateTime(xs:dateTime($a), '[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001]')", sDT).getString ());
      assertEquals (sDT, Integer.toString (aLocal.getDayOfYear ()), _eval ("format-dateTime(xs:dateTime($a), '[d]')", sDT).getString ());
      final DayOfWeek eDay = aLocal.getDayOfWeek ();
      assertEquals (sDT,
                    eDay.getDisplayName (TextStyle.FULL, Locale.ENGLISH),
                    _eval ("format-dateTime(xs:dateTime($a), '[FNn]', 'en', (), ())", sDT).getString ());
      assertEquals (sDT,
                    Integer.toString (aLocal.get (IsoFields.WEEK_OF_WEEK_BASED_YEAR)),
                    _eval ("format-dateTime(xs:dateTime($a), '[W]')", sDT).getString ());
      assertEquals (sDT,
                    _timezone (aODT.getOffset ().getTotalSeconds (), false),
                    _eval ("format-dateTime(xs:dateTime($a), '[Z]')", sDT).getString ());
    });
  }

  /**
   * Sums, differences and integer multiples of dayTimeDurations agree with
   * millisecond arithmetic, and the component functions return the parts of
   * the canonical value with its sign (F&amp;O 3.1 sections 8.2 and 8.4).
   */
  @Test
  public void testDurationArithmetic ()
  {
    new PropertyChecker ().setTries (2000).forAll (DURATION_MILLIS, DURATION_MILLIS, Generators.ints (-1000, 1000), (aM1, aM2, aFactor) -> {
      final long n1 = aM1.longValue ();
      final long n2 = aM2.longValue ();
      final String s1 = _durationLexical (n1);
      final String s2 = _durationLexical (n2);
      final String sMsg = s1 + " / " + s2;
      _assertTrue (sMsg, "xs:dayTimeDuration($a) + xs:dayTimeDuration($b) eq xs:dayTimeDuration($c)", s1, s2, _durationLexical (n1 + n2));
      _assertTrue (sMsg, "xs:dayTimeDuration($a) - xs:dayTimeDuration($b) eq xs:dayTimeDuration($c)", s1, s2, _durationLexical (n1 - n2));
      _assertTrue (sMsg,
                   "xs:dayTimeDuration($a) * xs:integer($b) eq xs:dayTimeDuration($c)",
                   s1,
                   aFactor.toString (),
                   _durationLexical (n1 * aFactor.intValue ()));
      assertEquals (sMsg,
                    Boolean.valueOf (n1 < n2),
                    Boolean.valueOf (_eval ("xs:dayTimeDuration($a) lt xs:dayTimeDuration($b)", s1, s2).getBoolean ()));
      final Duration aDuration = Duration.ofMillis (Math.abs (n1));
      final long nSign = n1 < 0 ? -1 : 1;
      final BigDecimal aSeconds = BigDecimal.valueOf (aDuration.toSecondsPart () * 1000L + aDuration.toMillisPart (), 3)
                                            .multiply (BigDecimal.valueOf (nSign))
                                            .stripTrailingZeros ();
      assertEquals (sMsg,
                    (nSign * aDuration.toDaysPart ()) +
                          " " +
                          (nSign * aDuration.toHoursPart ()) +
                          " " +
                          (nSign * aDuration.toMinutesPart ()) +
                          " " +
                          aSeconds.toPlainString (),
                    _eval ("let $d := xs:dayTimeDuration($a) return string-join((days-from-duration($d), hours-from-duration($d), " +
                           "minutes-from-duration($d), seconds-from-duration($d)), ' ')",
                           s1).getString ());
    });
  }
}

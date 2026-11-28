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
package com.helger.phoxtrot.xpath.ops;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the termination check of
 * {@link AtomicArithmetic#divideDecimal(java.math.BigDecimal, java.math.BigDecimal)}:
 * the <code>long</code> form for unscaled values below 2<sup>62</sup> must
 * decide as the {@link BigInteger} form, for dividends and divisors built from
 * the primes 2, 3, 5 and 7 (terminating and not, shared factors, a zero
 * dividend) and for arbitrary values.
 *
 * @author Philip Helger
 */
public final class AtomicArithmeticPropertyTest
{
  private static final long MAX = (1L << 62) - 1;

  /** A product of powers of 2, 3, 5 and 7 below 2^62, or 0 for the exponents all -1 */
  private static final IGenerator <Long> PRODUCTS = Generators.lists (Generators.ints (-1, 20), 4, 4).map (aExp -> {
    if (aExp.stream ().allMatch (x -> x.intValue () < 0))
      return Long.valueOf (0);
    final long [] aPrimes = { 2, 3, 5, 7 };
    long ret = 1;
    for (int i = 0; i < 4; ++i)
      for (int n = 0; n < aExp.get (i).intValue (); ++n)
        if (ret <= MAX / aPrimes[i])
          ret *= aPrimes[i];
    return Long.valueOf (ret);
  });

  private static void _assertSame (final long nA, final long nB)
  {
    assertEquals (nA + " / " + nB,
                  Boolean.valueOf (AtomicArithmetic.isTerminatingWide (BigInteger.valueOf (nA), BigInteger.valueOf (nB))),
                  Boolean.valueOf (AtomicArithmetic.isTerminatingLong (nA, nB)));
  }

  @Test
  public void testLongTerminationMatchesWide ()
  {
    new PropertyChecker ().setTries (5000).forAll (PRODUCTS, PRODUCTS, (aA, aB) -> {
      if (aB.longValue () != 0)
        _assertSame (aA.longValue (), aB.longValue ());
    });
    new PropertyChecker ().setTries (5000).forAll (Generators.longs (), Generators.longs (), (aA, aB) -> {
      final long nB = aB.longValue () & MAX;
      if (nB != 0)
        _assertSame (aA.longValue () & MAX, nB);
    });
    for (final long nB : new long [] { 1, MAX, 1L << 61, 3 })
      _assertSame (MAX, nB);
  }
}

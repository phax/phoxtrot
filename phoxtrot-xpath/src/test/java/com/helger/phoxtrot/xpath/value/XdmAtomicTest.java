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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.math.BigInteger;

import org.junit.Test;

import com.helger.phoxtrot.xpath.ops.AtomicCaster;

/**
 * Test class for class {@link XdmAtomic}.
 *
 * @author Philip Helger
 */
public final class XdmAtomicTest
{
  @Test
  public void testSmallIntegersShared ()
  {
    for (long n = -130; n <= 130; n++)
    {
      final XdmAtomic aValue = XdmAtomic.integer (n);
      assertEquals (EAtomicType.INTEGER, aValue.getType ());
      assertEquals (BigInteger.valueOf (n), aValue.getValue ());
      assertEquals (Long.toString (n), aValue.getLexical ());
      if (n >= -128 && n <= 127)
      {
        assertSame (aValue, XdmAtomic.integer (n));
        assertSame (aValue, XdmAtomic.integer (BigInteger.valueOf (n)));
        assertSame (aValue, XdmAtomic.of (EAtomicType.INTEGER, BigInteger.valueOf (n)));
      }
      else
        assertNotSame (aValue, XdmAtomic.integer (n));
    }
    // A cast to xs:integer takes the shared value, a derived type keeps its own
    assertSame (XdmAtomic.integer (42), AtomicCaster.castFromString ("42", EAtomicType.INTEGER, null));
    final XdmAtomic aInt = AtomicCaster.castFromString ("42", EAtomicType.INT, null);
    assertEquals (EAtomicType.INT, aInt.getType ());
    assertEquals (BigInteger.valueOf (42), aInt.getValue ());
  }
}

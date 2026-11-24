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

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for <code>xs:hexBinary</code> and
 * <code>xs:base64Binary</code>: the casts between strings and both types and
 * between the two types agree with {@link HexFormat} and {@link Base64}, give
 * the canonical forms (upper-case hex digits, base64 without spaces, XSD 1.1
 * sections 3.3.15 and 3.3.16), and the value comparisons order the octets as
 * unsigned bytes (F&amp;O 3.1 section 12.1).
 *
 * @author Philip Helger
 */
public final class BinaryCastPropertyTest
{
  private static final String [] VAR_NAMES = { "a", "b" };

  private final Map <String, CompiledXPath> m_aCache = new HashMap <> ();
  private final StaticContext m_aStaticCtx = new StaticContext ();

  public BinaryCastPropertyTest ()
  {
    for (final String sName : VAR_NAMES)
      m_aStaticCtx.declareVariable (new XdmQName (sName));
  }

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

  /** @return The base64 text with a single space after some of the characters, as XSD allows. */
  @NonNull
  private static String _withSpaces (@NonNull final String sBase64, final int nSeed)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < sBase64.length (); ++i)
    {
      aSB.append (sBase64.charAt (i));
      if (i + 1 < sBase64.length () && ((nSeed >>> (i % 31)) & 1) != 0)
        aSB.append (' ');
    }
    return aSB.toString ();
  }

  /**
   * Casting a hex string of either case gives the canonical upper-case form,
   * and the casts to and from <code>xs:base64Binary</code> give the base64
   * encoding of the same octets.
   */
  @Test
  public void testCastRoundTrip ()
  {
    new PropertyChecker ().setTries (2000).forAll (Generators.byteArrays (0, 40), Generators.booleans (), Generators.ints (), (aBytes, aUpper, aSeed) -> {
      final String sHexUpper = HexFormat.of ().withUpperCase ().formatHex (aBytes);
      final String sHex = aUpper.booleanValue () ? sHexUpper : sHexUpper.toLowerCase (Locale.ROOT);
      final String sBase64 = Base64.getEncoder ().encodeToString (aBytes);
      assertEquals (sHex, sHexUpper, _eval ("string(xs:hexBinary($a))", sHex).getString ());
      assertEquals (sHex, sBase64, _eval ("string(xs:base64Binary(xs:hexBinary($a)))", sHex).getString ());
      assertEquals (sBase64, sHexUpper, _eval ("string(xs:hexBinary(xs:base64Binary($a)))", sBase64).getString ());
      assertEquals (sBase64, sBase64, _eval ("string(xs:base64Binary($a))", sBase64).getString ());
      final String sSpaced = _withSpaces (sBase64, aSeed.intValue ());
      assertEquals (sSpaced, sBase64, _eval ("string(xs:base64Binary($a))", sSpaced).getString ());
      assertEquals (sHex, Boolean.TRUE, Boolean.valueOf (_eval ("xs:hexBinary($a) eq xs:hexBinary(upper-case($a))", sHex).getBoolean ()));
      if (!sHex.isEmpty ())
        assertEquals (sHex, Boolean.FALSE, Boolean.valueOf (_eval ("$a castable as xs:hexBinary", sHex.substring (1)).getBoolean ()));
    });
  }

  /**
   * <code>eq</code> and <code>lt</code> on binary values compare the octets
   * as unsigned bytes, a proper prefix being less (F&amp;O 3.1 sections
   * 12.1.1 and 12.1.2), as {@link Arrays#compareUnsigned(byte[], byte[])}.
   */
  @Test
  public void testComparison ()
  {
    new PropertyChecker ().setTries (2000).forAll (Generators.byteArrays (0, 6), Generators.byteArrays (0, 6), (aBytes1, aBytes2) -> {
      final HexFormat aHex = HexFormat.of ().withUpperCase ();
      final String s1 = aHex.formatHex (aBytes1);
      final String s2 = aHex.formatHex (aBytes2);
      final int nCmp = Integer.signum (Arrays.compareUnsigned (aBytes1, aBytes2));
      final String sMsg = s1 + " vs. " + s2;
      assertEquals (sMsg, Boolean.valueOf (nCmp == 0), Boolean.valueOf (_eval ("xs:hexBinary($a) eq xs:hexBinary($b)", s1, s2).getBoolean ()));
      assertEquals (sMsg, Boolean.valueOf (nCmp < 0), Boolean.valueOf (_eval ("xs:hexBinary($a) lt xs:hexBinary($b)", s1, s2).getBoolean ()));
      assertEquals (sMsg,
                    Boolean.valueOf (nCmp > 0),
                    Boolean.valueOf (_eval ("xs:base64Binary(xs:hexBinary($a)) gt xs:base64Binary(xs:hexBinary($b))", s1, s2).getBoolean ()));
    });
  }
}

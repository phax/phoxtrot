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
package com.helger.phoxtrot.xpath.context;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.collation.CollationRegistry;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;

/**
 * Test the run-time equivalence of static contexts
 * ({@link StaticContext#internRunTimeIdentity(Map)}) and its use in
 * {@link DynamicContext#withStaticContext(StaticContext)}.
 *
 * @author Philip Helger
 */
public final class StaticContextRunTimeIdentityTest
{
  @Test
  public void testDeclaredVariablesDoNotCount ()
  {
    final Map <Object, Object> aIdentities = new HashMap <> ();
    final StaticContext a = new StaticContext ().declareVariable (new XdmQName ("a")).internRunTimeIdentity (aIdentities);
    final StaticContext b = new StaticContext ().declareVariable (new XdmQName ("b")).internRunTimeIdentity (aIdentities);
    assertTrue (a.isRunTimeEquivalent (b));
    assertTrue (b.isRunTimeEquivalent (a));
    // A dynamic context is kept for an equivalent static context
    final DynamicContext aDyn = new DynamicContext (a);
    assertSame (aDyn, aDyn.withStaticContext (b));
    assertSame (a, aDyn.withStaticContext (b).getStaticContext ());
  }

  @Test
  public void testNotInterned ()
  {
    final StaticContext a = new StaticContext ();
    final StaticContext b = new StaticContext ();
    assertTrue (a.isRunTimeEquivalent (a));
    assertFalse (a.isRunTimeEquivalent (b));
    assertFalse (a.isRunTimeEquivalent (null));
    final DynamicContext aDyn = new DynamicContext (a);
    assertNotSame (aDyn, aDyn.withStaticContext (b));
    assertSame (aDyn, aDyn.withStaticContext (a));
    // Interned into different maps
    final StaticContext c = new StaticContext ().internRunTimeIdentity (new HashMap <> ());
    final StaticContext d = new StaticContext ().internRunTimeIdentity (new HashMap <> ());
    assertFalse (c.isRunTimeEquivalent (d));
  }

  private static void _assertDiffers (final Consumer <StaticContext> aChange)
  {
    final Map <Object, Object> aIdentities = new HashMap <> ();
    final StaticContext a = new StaticContext ().internRunTimeIdentity (aIdentities);
    final StaticContext b = new StaticContext ();
    aChange.accept (b);
    b.internRunTimeIdentity (aIdentities);
    assertFalse (a.isRunTimeEquivalent (b));
    final DynamicContext aDyn = new DynamicContext (a);
    assertSame (b, aDyn.withStaticContext (b).getStaticContext ());
  }

  @Test
  public void testRunTimeFieldsCount ()
  {
    _assertDiffers (x -> x.declareNamespace ("p", "urn:p"));
    _assertDiffers (x -> x.declareNamespace ("xs", "urn:other"));
    _assertDiffers (StaticContext::removeAllNamespacesExceptXml);
    _assertDiffers (x -> x.setDefaultElementNamespace ("urn:e"));
    _assertDiffers (x -> x.setInScopeDefaultNamespace ("urn:e"));
    _assertDiffers (x -> x.setDefaultFunctionNamespace ("urn:f"));
    _assertDiffers (x -> x.setBaseUri ("http://example.org/"));
    _assertDiffers (x -> x.setFunctionLibrary (FunctionLibrary.getDefault ().createChild ()));
    _assertDiffers (x -> x.setXPath10Compatibility (true));
    _assertDiffers (x -> x.setUnknownFunctionErrorCode ("XTDE1425"));
    _assertDiffers (x -> x.setStaticTyping (true));
    _assertDiffers (x -> x.setDefaultCollation (CollationRegistry.HTML_ASCII_CASE_INSENSITIVE_URI));
    _assertDiffers (x -> x.setXmlVersion (EXmlVersion.V11));
    _assertDiffers (x -> x.setXsdVersion (StaticContext.DEFAULT_XSD_VERSION));
  }

  @Test
  public void testChangeAfterInterningDropsEquivalence ()
  {
    final Map <Object, Object> aIdentities = new HashMap <> ();
    final StaticContext a = new StaticContext ().internRunTimeIdentity (aIdentities);
    final StaticContext b = new StaticContext ().internRunTimeIdentity (aIdentities);
    assertTrue (a.isRunTimeEquivalent (b));
    b.declareNamespace ("p", "urn:p");
    assertFalse (a.isRunTimeEquivalent (b));
    assertFalse (b.isRunTimeEquivalent (a));
    // The shared key is a copy: the change of b does not change what a was interned with
    final StaticContext c = new StaticContext ().internRunTimeIdentity (aIdentities);
    assertTrue (a.isRunTimeEquivalent (c));
    // Interned again with the change, b matches a context with the same change
    b.internRunTimeIdentity (aIdentities);
    final StaticContext d = new StaticContext ().declareNamespace ("p", "urn:p").internRunTimeIdentity (aIdentities);
    assertTrue (b.isRunTimeEquivalent (d));
    assertFalse (b.isRunTimeEquivalent (a));
  }
}

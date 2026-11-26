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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for {@link DynamicContext#forHostCall(com.helger.phoxtrot.xdm.IXdmItem, int, int, Object, VariableFrame)}:
 * it gives the same context as the chain of derivations it replaces.
 *
 * @author Philip Helger
 */
public final class DynamicContextHostCallTest
{
  private static final XdmQName NAME_V = new XdmQName ("v");

  private static void _assertSameContent (@NonNull final DynamicContext aExpected, @NonNull final DynamicContext aActual)
  {
    assertSame (aExpected.getStaticContext (), aActual.getStaticContext ());
    assertSame (aExpected.getContextItem (), aActual.getContextItem ());
    assertSame (aExpected.getCurrentItem (), aActual.getCurrentItem ());
    assertEquals (aExpected.getPosition (), aActual.getPosition ());
    assertEquals (aExpected.getSize (), aActual.getSize ());
    assertSame (aExpected.getHostState (), aActual.getHostState ());
    assertSame (aExpected.getFrame (), aActual.getFrame ());
    assertSame (aExpected.getLocalFrame (), aActual.getLocalFrame ());
    assertSame (aExpected.getVariable (NAME_V), aActual.getVariable (NAME_V));
    assertSame (aExpected.getHost (), aActual.getHost ());
  }

  @Test
  public void testSameAsChain ()
  {
    final DynamicContext aRoot = new DynamicContext (new StaticContext ());
    aRoot.setHost ("host");
    final VariableFrameLayout aCallerLayout = new VariableFrameLayout ();
    aCallerLayout.allocateSlot (NAME_V);
    // A caller with focus, current item, a variable by name, a host frame and a local frame
    final DynamicContext aCaller = aRoot.withFocus (XdmAtomic.integer (1000), 2, 3)
                                        .withCurrentItem (XdmAtomic.integer (2000))
                                        .withHostState ("caller")
                                        .withVariable (NAME_V, XdmSequence.of (XdmAtomic.integer (3000)))
                                        .withFrame (new VariableFrame (aCallerLayout))
                                        .withNewLocalFrame (new VariableFrameLayout ());
    final VariableFrame aFrame = new VariableFrame (new VariableFrameLayout ());
    for (final XdmAtomic aItem : new XdmAtomic [] { XdmAtomic.integer (4000), null })
      for (final VariableFrame aBodyFrame : new VariableFrame [] { aFrame, null })
      {
        final int nPos = aItem == null ? 0 : 5;
        final int nSize = aItem == null ? 0 : 7;
        final DynamicContext aExpected = aCaller.withoutFocusAndVariables ()
                                                .withFocus (aItem, nPos, nSize)
                                                .withCurrentItem (aItem)
                                                .withHostState ("body")
                                                .withFrame (aBodyFrame);
        final DynamicContext aActual = aCaller.forHostCall (aItem, nPos, nSize, "body", aBodyFrame);
        _assertSameContent (aExpected, aActual);
        assertNull (aActual.getVariable (NAME_V));
        assertNull (aActual.getLocalFrame ());
      }
  }

  @Test
  public void testLocalFrameRejected ()
  {
    final DynamicContext aCtx = new DynamicContext (new StaticContext ()).withNewLocalFrame (new VariableFrameLayout ());
    try
    {
      aCtx.forHostCall (null, 0, 0, null, aCtx.getLocalFrame ());
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }
}

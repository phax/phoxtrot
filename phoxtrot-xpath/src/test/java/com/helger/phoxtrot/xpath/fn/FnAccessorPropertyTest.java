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
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.XPathPropertyTestHelper;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for <code>fn:path</code> ({@link FnAccessor}, F&amp;O
 * 3.1 section 14.5.2) on random documents, also with parents of more than 16
 * children, whose sibling positions are kept in the run cache.
 *
 * @author Philip Helger
 */
public final class FnAccessorPropertyTest
{
  private final XPathPropertyTestHelper m_aHelper = new XPathPropertyTestHelper ();

  /**
   * The path of every node (computed for all nodes in one run, so that the
   * cached sibling positions are reused) equals the path computed in a run of
   * its own, and evaluating it as an expression from the document node
   * selects exactly that node.
   */
  @Test
  public void testPathSelectsNode ()
  {
    new PropertyChecker ().setTries (300).forAll (XPathPropertyTestHelper.DOCUMENTS, sXml -> {
      final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
      final XdmSequence aNodes = m_aHelper.eval ("(/, //node(), //@*)", aDoc);
      final XdmSequence aPaths = m_aHelper.eval ("(/, //node(), //@*) ! path(.)", aDoc);
      assertEquals (aNodes.size (), aPaths.size ());
      for (int i = 0; i < aNodes.size (); ++i)
      {
        final String sPath = ((XdmAtomic) aPaths.get (i)).getString ();
        assertEquals (sPath, ((XdmAtomic) m_aHelper.eval ("path(.)", aNodes.get (i)).get (0)).getString ());
        final XdmSequence aSelected = m_aHelper.eval (sPath, aDoc);
        assertEquals (sXml + " " + sPath, 1, aSelected.size ());
        assertSame (sXml + " " + sPath, aNodes.get (i), aSelected.get (0));
      }
    });
  }
}

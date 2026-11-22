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
package com.helger.phoxtrot.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.context.StaticContext;

/**
 * Test class for class {@link CliHelper}.
 *
 * @author Philip Helger
 */
public final class CliHelperTest
{
  @Test
  public void testSplitNameValue ()
  {
    CliHelper.NameValue aNV = CliHelper.splitNameValue ("a=b=c");
    assertEquals ("a", aNV.name ());
    assertEquals ("b=c", aNV.value ());

    aNV = CliHelper.splitNameValue ("a=");
    assertEquals ("a", aNV.name ());
    assertEquals ("", aNV.value ());

    aNV = CliHelper.splitNameValue ("a");
    assertEquals ("a", aNV.name ());
    assertNull (aNV.value ());
  }

  @Test
  public void testParseName ()
  {
    assertEquals (new XdmQName ("local"), CliHelper.parseName ("local", null));
    assertEquals (new XdmQName ("urn:x", "local"), CliHelper.parseName ("Q{urn:x}local", null));
    assertEquals (new XdmQName ("urn:x", "local"), CliHelper.parseName ("{urn:x}local", null));
    assertEquals (new XdmQName ("", "local"), CliHelper.parseName ("Q{}local", null));

    final StaticContext aSC = new StaticContext ().declareNamespace ("p", "urn:p");
    final XdmQName aName = CliHelper.parseName ("p:local", aSC);
    assertEquals ("urn:p", aName.namespaceUri ());
    assertEquals ("local", aName.localName ());
  }

  @Test
  public void testParseNameErrors ()
  {
    for (final String sName : new String [] { "", "1abc", "{urn:x", "Q{urn:x}1", "a:b:c", "p:local" })
      try
      {
        CliHelper.parseName (sName, null);
        fail (sName);
      }
      catch (final IllegalArgumentException ex)
      {
        // expected
      }
  }
}

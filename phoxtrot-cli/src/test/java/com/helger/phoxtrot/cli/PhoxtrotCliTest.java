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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.phoxtrot.cli.CliRunner.CliResult;
import com.helger.phoxtrot.xdm.PhoxtrotVersion;

/**
 * Test class for class {@link PhoxtrotCli}.
 *
 * @author Philip Helger
 */
public final class PhoxtrotCliTest
{
  @Test
  public void testNoCommand ()
  {
    final CliResult aResult = CliRunner.run ();
    assertEquals (PhoxtrotCli.EXIT_USAGE, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("xslt"));
    assertTrue (aResult.err ().contains ("xpath"));
    assertTrue (aResult.err ().contains ("xsd"));
  }

  @Test
  public void testGroupWithoutCommand ()
  {
    for (final String sGroup : new String [] { "xslt", "xpath", "xsd" })
    {
      final CliResult aResult = CliRunner.run (sGroup);
      assertEquals (sGroup, PhoxtrotCli.EXIT_USAGE, aResult.exitCode ());
      assertTrue (sGroup, aResult.err ().contains ("check"));
    }
  }

  @Test
  public void testVersion ()
  {
    CliResult aResult = CliRunner.run ("--version");
    assertEquals (PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("phoxtrot " + PhoxtrotVersion.BUILD_VERSION, aResult.out ().trim ());

    // Also on a sub command
    aResult = CliRunner.run ("xsd", "validate", "-V");
    assertEquals (PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("phoxtrot " + PhoxtrotVersion.BUILD_VERSION, aResult.out ().trim ());
  }

  @Test
  public void testUnknownOption ()
  {
    final CliResult aResult = CliRunner.run ("xslt", "check", "--unknown", "a.xsl");
    assertEquals (PhoxtrotCli.EXIT_USAGE, aResult.exitCode ());
  }

  @Test
  public void testIsVerboseRequested ()
  {
    assertTrue (PhoxtrotCli.isVerboseRequested (new String [] { "xslt", "check", "-v" }));
    assertTrue (PhoxtrotCli.isVerboseRequested (new String [] { "--verbose" }));
    assertTrue (PhoxtrotCli.isVerboseRequested (new String [] { "-qv" }));
    assertFalse (PhoxtrotCli.isVerboseRequested (new String [] { "-q" }));
    assertFalse (PhoxtrotCli.isVerboseRequested (new String [] { "--", "-v" }));
    assertFalse (PhoxtrotCli.isVerboseRequested (new String [] { "xpath", "eval", "--var", "v=1" }));
  }
}

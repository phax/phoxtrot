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
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.cli.CliRunner.CliResult;

/**
 * Test class for the commands <code>xpath check</code>, <code>xpath streamability</code> and
 * <code>xpath eval</code>.
 *
 * @author Philip Helger
 */
public final class XPathCommandTest
{
  private static final String BASIC = "../examples/basic/";
  private static final String NS = "inv=urn:example:invoice";

  @NonNull
  private static String _output (@NonNull final CliResult aResult)
  {
    return aResult.out () + aResult.err ();
  }

  @Test
  public void testCheck ()
  {
    final CliResult aResult = CliRunner.run ("xpath",
                                             "check",
                                             "--ns",
                                             NS,
                                             "-f",
                                             BASIC + "total-check.xpath",
                                             "-f",
                                             BASIC + "cardinality.xpath",
                                             "sum(//inv:Line/@qty)");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out ().contains ("3 expression(s) checked without errors"));
  }

  @Test
  public void testCheckSyntaxError ()
  {
    final CliResult aResult = CliRunner.run ("xpath", "check", "count(//a", "1 +");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("count(//a (line 1, column 10): XPST0003"));
    assertTrue (aResult.err (), aResult.err ().contains ("2 of 2 expression(s) have errors"));
  }

  @Test
  public void testCheckUndeclared ()
  {
    CliResult aResult = CliRunner.run ("xpath", "check", "$limit + 1", "inv:a");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("XPST0008"));
    assertTrue (aResult.err (), aResult.err ().contains ("XPST0081"));

    aResult = CliRunner.run ("xpath", "check", "--declare", "limit", "--ns", NS, "$limit + 1", "inv:a");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
  }

  @Test
  public void testCheckStaticTyping ()
  {
    final CliResult aResult = CliRunner.run ("xpath",
                                             "check",
                                             "--static-typing",
                                             "--context-type",
                                             "document-node()",
                                             "--ns",
                                             NS,
                                             "-f",
                                             BASIC + "total-check.xpath",
                                             "-f",
                                             BASIC + "cardinality.xpath");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.out (), aResult.out ().contains ("total-check.xpath: OK"));
    assertTrue (aResult.err (), aResult.err ().contains ("cardinality.xpath: XPTY0004"));
    assertTrue (aResult.err (), aResult.err ().contains ("1 of 2 expression(s) have errors"));
  }

  @Test
  public void testCheckStaticTypingVariable ()
  {
    CliResult aResult = CliRunner.run ("xpath", "check", "--static-typing", "--declare", "s=xs:string", "$s + 1");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("XPTY0004"));

    aResult = CliRunner.run ("xpath", "check", "--static-typing", "--declare", "s=xs:integer", "$s + 1");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
  }

  @Test
  public void testStreamability ()
  {
    CliResult aResult = CliRunner.run ("xpath", "streamability", "--ns", NS, "sum(inv:Line/@price)", "@id");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out (), aResult.out ().contains ("sum(inv:Line/@price): CONSUMING"));
    assertTrue (aResult.out (), aResult.out ().contains ("@id: MOTIONLESS"));

    aResult = CliRunner.run ("xpath", "streamability", "--ns", NS, "inv:Line[last()]");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("FREE_RANGING"));
    assertTrue (aResult.err (), aResult.err ().contains ("last#0"));

    // Needs only the subtree: fine in burst mode
    aResult = CliRunner.run ("xpath", "streamability", "--mode", "BURST", "--ns", NS, "inv:Line[last()]");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out (), aResult.out ().contains ("BURST"));
  }

  @Test
  public void testStreamabilityPattern ()
  {
    CliResult aResult = CliRunner.run ("xpath", "streamability", "--pattern", "a/b[@c]");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());

    aResult = CliRunner.run ("xpath", "streamability", "--pattern", "a[following-sibling::b]");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
  }

  @Test
  public void testEval ()
  {
    final CliResult aResult = CliRunner.run ("xpath",
                                             "eval",
                                             "--ns",
                                             NS,
                                             "-c",
                                             BASIC + "invoice.xml",
                                             "sum(//inv:Line/@qty)",
                                             "string(/inv:Invoice/@id)",
                                             "-f",
                                             BASIC + "total-check.xpath");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("8.0e0\n\"INV-1\"\ntrue()\n", aResult.out ().replace ("\r\n", "\n"));
  }

  @Test
  public void testEvalVariablesAndMethod ()
  {
    CliResult aResult = CliRunner.run ("xpath", "eval", "--var", "a=4", "--xvar", "b=(1, 2, 3)", "$a * count($b)");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("1.2e1", aResult.out ().trim ());

    aResult = CliRunner.run ("xpath", "eval", "--method", "json", "map { 'a': [1, 2] }");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("{\"a\":[1,2]}", aResult.out ().trim ());
  }

  @Test
  public void testEvalErrors ()
  {
    final CliResult aResult = CliRunner.run ("xpath", "eval", "1 div 0", "2");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("FOAR0001"));
    // The other expressions are still evaluated
    assertEquals ("2", aResult.out ().trim ());
  }

  @Test
  public void testEvalDocAccess ()
  {
    // Without a context document no folder is allowed
    CliResult aResult = CliRunner.run ("xpath", "eval", "doc('" + BASIC + "invoice.xml')/*/@id/string()");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("is refused"));

    aResult = CliRunner.run ("xpath",
                             "eval",
                             "--resource-dir",
                             BASIC,
                             "doc('" + BASIC + "invoice.xml')/*/@id/string()");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("\"INV-1\"", aResult.out ().trim ());
  }
}

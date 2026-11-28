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
 * Test class for the commands <code>xsd check</code> and <code>xsd validate</code>.
 *
 * @author Philip Helger
 */
public final class XsdCommandTest
{
  private static final String BASIC = "../examples/basic/";

  @NonNull
  private static String _output (@NonNull final CliResult aResult)
  {
    return aResult.out () + aResult.err ();
  }

  @Test
  public void testCheck ()
  {
    // xs:assert needs XSD 1.1
    CliResult aResult = CliRunner.run ("xsd", "check", BASIC + "invoice.xsd");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("invoice.xsd:42:"));

    aResult = CliRunner.run ("xsd", "check", "--xsd-version", "1.1", BASIC + "invoice.xsd");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());

    aResult = CliRunner.run ("xsd", "check", "--xsd-version", "2.0", BASIC + "invoice.xsd");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_USAGE, aResult.exitCode ());
  }

  @Test
  public void testValidate ()
  {
    final CliResult aResult = CliRunner.run ("xsd",
                                             "validate",
                                             "--xsd-version",
                                             "1.1",
                                             "--xsd",
                                             BASIC + "invoice.xsd",
                                             BASIC + "invoice.xml",
                                             BASIC + "invoice-invalid.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.out (), aResult.out ().contains ("invoice.xml: valid"));
    assertTrue (aResult.err (), aResult.err ().contains ("invoice-invalid.xml:20:"));
    assertTrue (aResult.err (), aResult.err ().contains ("cvc-assertion"));
    assertTrue (aResult.err (), aResult.err ().contains ("invoice-invalid.xml:22:"));
    assertTrue (aResult.err (), aResult.err ().contains ("cvc-datatype-valid"));
    assertTrue (aResult.err (), aResult.err ().contains ("1 of 2 document(s) are invalid"));
  }

  @Test
  public void testValidateMaxErrors ()
  {
    final CliResult aResult = CliRunner.run ("xsd",
                                             "validate",
                                             "--xsd-version",
                                             "1.1",
                                             "--max-errors",
                                             "1",
                                             "--xsd",
                                             BASIC + "invoice.xsd",
                                             BASIC + "invoice-invalid.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("invalid (1 error(s))"));
  }

  @Test
  public void testValidateInvalidSchema ()
  {
    final CliResult aResult = CliRunner.run ("xsd", "validate", "--xsd", BASIC + "invoice.xsd", BASIC + "invoice.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_ERROR, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("The schema is invalid"));
  }
}

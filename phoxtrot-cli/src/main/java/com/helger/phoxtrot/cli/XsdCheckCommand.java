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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xsd.XsdInvalidException;
import com.helger.phoxtrot.xsd.XsdUnsupportedException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * <code>xsd check</code>: compile schema documents as one schema and report the first error.
 *
 * @author Philip Helger
 */
@Command (name = "check",
          mixinStandardHelpOptions = true,
          description = "Compile schema documents as one schema (the first is the entry document) and report its error.")
@NotThreadSafe
final class XsdCheckCommand implements Callable <Integer>
{
  /** The model of this command. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The schema documents. */
  @Parameters (paramLabel = "SCHEMA", arity = "1..*", description = "The schema documents.")
  List <Path> m_aSchemas;

  /** The XSD options. */
  @Mixin
  CliXsdOptions m_aXsdOptions;

  /** The file access options. */
  @Mixin
  CliAccessOptions m_aAccessOptions;

  /** The log options. */
  @Mixin
  CliLogOptions m_aLogOptions;

  /** Constructor. Called by picocli. */
  XsdCheckCommand ()
  {}

  public Integer call ()
  {
    try
    {
      m_aXsdOptions.getVersion ();
    }
    catch (final IllegalArgumentException ex)
    {
      throw new ParameterException (m_aSpec.commandLine (), ex.getMessage (), ex);
    }
    final CliLogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);
    try
    {
      m_aXsdOptions.compile (m_aSchemas, m_aAccessOptions.getAllowedDirs (m_aSchemas.toArray (new Path [0])));
    }
    catch (final IOException ex)
    {
      aLog.error ("A schema document cannot be read: " + ex.getMessage (), aLog.isVerbose () ? ex : null);
      return Integer.valueOf (PhoxtrotCli.EXIT_ERROR);
    }
    catch (final XsdInvalidException ex)
    {
      aLog.error (CliXsdOptions.getErrorText (m_aSchemas.size () == 1 ? m_aSchemas.get (0) : null, ex.getError ()),
                  aLog.isVerbose () ? ex : null);
      return Integer.valueOf (PhoxtrotCli.EXIT_FAILURE);
    }
    catch (final XsdUnsupportedException ex)
    {
      aLog.error ("The schema uses a construct that is not supported: " + ex.getMessage (), aLog.isVerbose () ? ex : null);
      return Integer.valueOf (PhoxtrotCli.EXIT_ERROR);
    }
    aLog.info ("The schema of " + m_aSchemas.size () + " document(s) is valid (XSD " + m_aXsdOptions.getVersion ().getID () + ")");
    return Integer.valueOf (PhoxtrotCli.EXIT_OK);
  }
}

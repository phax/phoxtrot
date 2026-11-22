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
import com.helger.phoxtrot.xsd.XsdError;
import com.helger.phoxtrot.xsd.XsdInvalidException;
import com.helger.phoxtrot.xsd.XsdSchema;
import com.helger.phoxtrot.xsd.XsdUnsupportedException;
import com.helger.phoxtrot.xsd.XsdValidationOptions;
import com.helger.phoxtrot.xsd.XsdValidationResult;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * <code>xsd validate</code>: validate documents against an XML Schema.
 *
 * @author Philip Helger
 */
@Command (name = "validate",
          mixinStandardHelpOptions = true,
          description = "Validate documents against an XML Schema and report every error with line and column.")
@NotThreadSafe
final class XsdValidateCommand implements Callable <Integer>
{
  /** The model of this command. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The schema documents. */
  @Option (names = "--xsd",
           paramLabel = "FILE",
           required = true,
           description = "A schema document; several are compiled as one schema (the first is the entry document). May be repeated.")
  List <Path> m_aSchemas;

  /** The documents to validate. */
  @Parameters (paramLabel = "FILE", arity = "1..*", description = "The documents to validate.")
  List <Path> m_aInstances;

  /** The error limit. */
  @Option (names = "--max-errors",
           paramLabel = "N",
           defaultValue = "0",
           description = "Stop the validation of a document after that many errors (default 0: no limit).")
  int m_nMaxErrors;

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
  XsdValidateCommand ()
  {}

  public Integer call ()
  {
    if (m_nMaxErrors < 0)
      throw new ParameterException (m_aSpec.commandLine (), "--max-errors must not be negative");
    try
    {
      m_aXsdOptions.getVersion ();
    }
    catch (final IllegalArgumentException ex)
    {
      throw new ParameterException (m_aSpec.commandLine (), ex.getMessage (), ex);
    }
    final CliLogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);

    final XsdSchema aSchema;
    try
    {
      aSchema = m_aXsdOptions.compile (m_aSchemas, m_aAccessOptions.getAllowedDirs (m_aSchemas.toArray (new Path [0])));
    }
    catch (final IOException ex)
    {
      aLog.error ("A schema document cannot be read: " + ex.getMessage (), aLog.isVerbose () ? ex : null);
      return Integer.valueOf (PhoxtrotCli.EXIT_ERROR);
    }
    catch (final XsdInvalidException ex)
    {
      aLog.error ("The schema is invalid: " +
                  CliXsdOptions.getErrorText (m_aSchemas.size () == 1 ? m_aSchemas.get (0) : null, ex.getError ()),
                  aLog.isVerbose () ? ex : null);
      return Integer.valueOf (PhoxtrotCli.EXIT_ERROR);
    }
    catch (final XsdUnsupportedException ex)
    {
      aLog.error ("The schema uses a construct that is not supported: " + ex.getMessage (), aLog.isVerbose () ? ex : null);
      return Integer.valueOf (PhoxtrotCli.EXIT_ERROR);
    }

    // The xsi:schemaLocation hints of a document are never followed: a document must not choose files
    final XsdValidationOptions aOptions = new XsdValidationOptions ().setMaxErrors (m_nMaxErrors)
                                                                     .setFollowSchemaLocationHints (false);
    int nInvalid = 0;
    int nUndecided = 0;
    for (final Path aInstance : m_aInstances)
      try
      {
        final XsdValidationResult aResult = aSchema.validate (aInstance, aOptions);
        if (aResult.valid ())
          aLog.info (aInstance + ": valid");
        else
        {
          for (final XsdError aError : aResult.errors ())
            aLog.error (CliXsdOptions.getErrorText (aInstance, aError));
          aLog.error (aInstance + ": invalid (" + aResult.errors ().size () + " error(s))");
          nInvalid++;
        }
      }
      catch (final XsdUnsupportedException ex)
      {
        aLog.error (aInstance + ": no verdict, the validator does not support a construct: " + ex.getMessage (),
                    aLog.isVerbose () ? ex : null);
        nUndecided++;
      }
    if (nUndecided > 0)
      return Integer.valueOf (PhoxtrotCli.EXIT_ERROR);
    if (nInvalid > 0)
    {
      aLog.error (nInvalid + " of " + m_aInstances.size () + " document(s) are invalid");
      return Integer.valueOf (PhoxtrotCli.EXIT_FAILURE);
    }
    aLog.info (m_aInstances.size () + " document(s) are valid");
    return Integer.valueOf (PhoxtrotCli.EXIT_OK);
  }
}

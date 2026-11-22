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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xdm.XdmParseException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * <code>xslt check</code>: compile stylesheets and report their static errors, without running
 * them.
 *
 * @author Philip Helger
 */
@Command (name = "check",
          mixinStandardHelpOptions = true,
          description = "Compile stylesheets and report their static errors with module, line and column, without running them.")
@NotThreadSafe
final class XsltCheckCommand implements Callable <Integer>
{
  /** The model of this command. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The stylesheets to check. */
  @Parameters (paramLabel = "STYLESHEET", arity = "1..*", description = "The stylesheets to check.")
  List <Path> m_aStylesheets;

  /** <code>true</code> to check the streamability rules. */
  @Option (names = "--streamable",
           description = "Claim streaming and enforce the streamability rules of XSLT 3.0 section 19: a construct declared streamable that is not guaranteed streamable is a static error (XTSE3430).")
  boolean m_bStreamable;

  /** The compile options. */
  @Mixin
  CliXsltCompileOptions m_aCompileOptions;

  /** The file access options. */
  @Mixin
  CliAccessOptions m_aAccessOptions;

  /** The engine options. */
  @Mixin
  CliEngineOptions m_aEngineOptions;

  /** The log options. */
  @Mixin
  CliLogOptions m_aLogOptions;

  /** Constructor. Called by picocli. */
  XsltCheckCommand ()
  {}

  public Integer call ()
  {
    final CliLogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);
    int nFailed = 0;
    for (final Path aStylesheet : m_aStylesheets)
    {
      final CliResourceResolver aResolver = m_aAccessOptions.createResolver (aLog, aStylesheet);
      try
      {
        m_aCompileOptions.compile (aStylesheet,
                                   aResolver,
                                   m_aAccessOptions.m_bAllowFileAccess,
                                   m_aEngineOptions.m_eThreading,
                                   m_bStreamable,
                                   m_bStreamable,
                                   true);
        aLog.info (aStylesheet + ": OK" + (m_bStreamable ? " (streamability rules met)" : ""));
      }
      catch (final IllegalArgumentException ex)
      {
        // Malformed static parameter
        throw new ParameterException (m_aSpec.commandLine (), ex.getMessage (), ex);
      }
      catch (final XdmParseException ex)
      {
        aLog.error (aStylesheet + ": XTSE0010: The stylesheet is not well-formed: " + ex.getMessage ());
        nFailed++;
      }
      catch (final RuntimeException ex)
      {
        // Static errors (XsltException, XPathException) and anything the compiler throws otherwise
        aLog.error (CliHelper.getErrorText (ex, aStylesheet), aLog.isVerbose () ? ex : null);
        nFailed++;
      }
    }
    if (nFailed > 0)
    {
      aLog.error (nFailed + " of " + m_aStylesheets.size () + " stylesheet(s) have errors");
      return Integer.valueOf (PhoxtrotCli.EXIT_FAILURE);
    }
    aLog.info (m_aStylesheets.size () + " stylesheet(s) checked without errors");
    return Integer.valueOf (PhoxtrotCli.EXIT_OK);
  }
}

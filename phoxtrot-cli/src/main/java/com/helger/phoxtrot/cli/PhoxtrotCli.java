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

import java.util.concurrent.Callable;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.VisibleForTesting;
import com.helger.phoxtrot.xdm.PhoxtrotVersion;

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * The phoxtrot command line client. The executable JAR calls {@link #main(String[])}.
 *
 * @author Philip Helger
 */
@Command (name = "phoxtrot",
          mixinStandardHelpOptions = true,
          versionProvider = PhoxtrotCli.VersionProvider.class,
          description = "Check and run XPath 3.1 expressions and XSLT 3.0 stylesheets, and check and use XML Schemas, with phoxtrot.",
          subcommands = { XsltCommand.class,
                          XPathCommand.class,
                          XsdCommand.class,
                          HelpCommand.class,
                          AutoComplete.GenerateCompletion.class })
@NotThreadSafe
public final class PhoxtrotCli implements Callable <Integer>
{
  /** Exit code: success */
  public static final int EXIT_OK = 0;
  /** Exit code: a check failed (static error, not streamable, invalid instance) or a run raised an error */
  public static final int EXIT_FAILURE = 1;
  /** Exit code: invalid command line */
  public static final int EXIT_USAGE = CommandLine.ExitCode.USAGE;
  /** Exit code: technical error (I/O, unreadable input) */
  public static final int EXIT_ERROR = 3;

  /** The slf4j-simple system property with the default log level */
  private static final String SLF4J_SIMPLE_DEFAULT_LOG_LEVEL = "org.slf4j.simpleLogger.defaultLogLevel";

  /**
   * Provides the version for <code>--version</code>.
   */
  public static final class VersionProvider implements CommandLine.IVersionProvider
  {
    /** Create a new version provider. Called by picocli. */
    public VersionProvider ()
    {}

    @NonNull
    public String [] getVersion ()
    {
      return new String [] { getHeadline () };
    }
  }

  /** The model of the top level command, for the usage message. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** Constructor. Called by {@link #createCommandLine()}. */
  public PhoxtrotCli ()
  {}

  /**
   * Get the product name and version, printed by <code>--version</code>, as header of the usage
   * message and at the start of each command. This is a method and not a constant on purpose:
   * reading the version creates a logger, which must not happen before {@link #main(String[])} set
   * the log level for verbose mode.
   *
   * @return The headline. Never <code>null</code>.
   */
  @NonNull
  static String getHeadline ()
  {
    return "phoxtrot " + PhoxtrotVersion.BUILD_VERSION;
  }

  /**
   * Called when no command is given: print the usage.
   *
   * @return Always {@link #EXIT_USAGE}.
   */
  public Integer call ()
  {
    m_aSpec.commandLine ().usage (m_aSpec.commandLine ().getErr ());
    return Integer.valueOf (EXIT_USAGE);
  }

  /**
   * Set the headline as header heading of the usage message of the provided command and all its
   * sub commands, and the version provider for their <code>--version</code>. This cannot be done in
   * the annotation, because the version is not a compile time constant.
   *
   * @param aCmd
   *        The command line to modify. May not be <code>null</code>.
   */
  private static void _setHeaderRecursive (@NonNull final CommandLine aCmd)
  {
    aCmd.getCommandSpec ().usageMessage ().headerHeading (getHeadline () + "%n");
    aCmd.getCommandSpec ().versionProvider (new VersionProvider ());
    for (final CommandLine aSubCmd : aCmd.getSubcommands ().values ())
      _setHeaderRecursive (aSubCmd);
  }

  /**
   * Create the picocli command line of this client. Used by {@link #main(String[])} and by tests
   * that run the client in-process.
   *
   * @return A new command line for this client, with technical errors mapped to
   *         {@link #EXIT_ERROR}. Never <code>null</code>.
   */
  @NonNull
  public static CommandLine createCommandLine ()
  {
    final CommandLine ret = new CommandLine (new PhoxtrotCli ());
    _setHeaderRecursive (ret);
    // Usage errors are handled by picocli itself; this only gets exceptions thrown by a command
    ret.setExecutionExceptionHandler ( (ex, aCmd, aParseResult) -> {
      aCmd.getErr ().println ("[error] " + ex.getClass ().getSimpleName () + ": " + ex.getMessage ());
      // The parse result of the (sub) command that failed contains its -v option
      if (aCmd.getParseResult ().hasMatchedOption ("--verbose"))
        ex.printStackTrace (aCmd.getErr ());
      aCmd.getErr ().flush ();
      return EXIT_ERROR;
    });
    return ret;
  }

  /**
   * Check if verbose output is requested, before the command line is parsed. This is needed,
   * because the log level of the libraries must be set before the first logger is created.
   *
   * @param aArgs
   *        The command line arguments. May not be <code>null</code>.
   * @return <code>true</code> if <code>-v</code> or <code>--verbose</code> is contained, also as
   *         part of combined short options like <code>-qv</code>.
   */
  @VisibleForTesting
  static boolean isVerboseRequested (@NonNull final String [] aArgs)
  {
    for (final String sArg : aArgs)
    {
      // End of options
      if ("--".equals (sArg))
        break;
      if ("--verbose".equals (sArg))
        return true;
      // The combinable short flag options are -h, -q, -v and -V; look for a 'v' after the dash
      if (sArg.matches ("-[hqvV]+") && sArg.indexOf ('v') > 0)
        return true;
    }
    return false;
  }

  /**
   * Entry point of the executable JAR. Exits the JVM with the exit code of the command.
   *
   * @param aArgs
   *        The command line arguments. May not be <code>null</code>.
   */
  public static void main (final String [] aArgs)
  {
    // In verbose mode also show the info messages of the libraries. Must happen before anything
    // creates a logger; an explicit -D setting wins.
    if (isVerboseRequested (aArgs) && System.getProperty (SLF4J_SIMPLE_DEFAULT_LOG_LEVEL) == null)
      System.setProperty (SLF4J_SIMPLE_DEFAULT_LOG_LEVEL, "info");

    System.exit (createCommandLine ().execute (aArgs));
  }
}

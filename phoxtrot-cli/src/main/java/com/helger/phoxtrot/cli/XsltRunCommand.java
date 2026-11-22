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
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.LargeStackRunner;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * <code>xslt run</code>: run a stylesheet and write the principal result to a file or the standard
 * output.
 *
 * @author Philip Helger
 */
@Command (name = "run",
          mixinStandardHelpOptions = true,
          description = { "Run a stylesheet on a source document (or from an initial template) and write the principal result to a file or the standard output.",
                          "xsl:message output, failed xsl:assert and the trace go to the error output." })
@NotThreadSafe
final class XsltRunCommand implements Callable <Integer>
{
  /** The value of <code>--initial-template</code> without a name. */
  private static final String DEFAULT_INITIAL_TEMPLATE = "xsl:initial-template";

  /** The model of this command. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The stylesheet. */
  @Option (names = { "-s", "--stylesheet" }, paramLabel = "FILE", required = true, description = "The stylesheet to run.")
  Path m_aStylesheet;

  /** The source document. */
  @Parameters (paramLabel = "SOURCE",
               arity = "0..1",
               description = "The source document: the global context item and the initial match selection. Optional with --initial-template.")
  Path m_aSource;

  /** The output file. */
  @Option (names = { "-o", "--output" },
           paramLabel = "FILE",
           description = "The file to write the principal result to (created with its folder if needed, overwritten if it exists, deleted if the run fails). Default: the standard output.")
  Path m_aOutput;

  /** Stylesheet parameters with a string value. */
  @Option (names = { "-p", "--param" },
           paramLabel = "NAME=VALUE",
           description = "Set a stylesheet parameter to a value of type xs:untypedAtomic (converted to the declared type). NAME is local, Q{uri}local or {uri}local. May be repeated.")
  List <String> m_aParams;

  /** Stylesheet parameters with an XPath value. */
  @Option (names = "--xparam",
           paramLabel = "NAME=XPATH",
           description = "Set a stylesheet parameter to the result of an XPath expression, e.g. --xparam 'limit=10' or --xparam 'codes=(\"A\",\"B\")'. The context item is the source document. May be repeated.")
  List <String> m_aXParams;

  /** The initial template. */
  @Option (names = "--initial-template",
           paramLabel = "NAME",
           arity = "0..1",
           fallbackValue = DEFAULT_INITIAL_TEMPLATE,
           description = "Start with this named template instead of applying templates to the source (default name: xsl:initial-template).")
  String m_sInitialTemplate;

  /** The initial mode. */
  @Option (names = "--initial-mode", paramLabel = "NAME", description = "The initial mode.")
  String m_sInitialMode;

  /** <code>true</code> to read the source as a stream. */
  @Option (names = "--streamed",
           description = "Claim streaming and read the source as a stream where the initial mode is streamable (otherwise it is read in full).")
  boolean m_bStreamed;

  /** The folder for the results of xsl:result-document. */
  @Option (names = "--result-documents",
           paramLabel = "DIR",
           description = "Allow xsl:result-document with href and write its results to this folder (hrefs are resolved against it and must stay inside; written in UTF-8). Without it such a stylesheet is rejected with XTSE0010.")
  Path m_aResultDir;

  /** <code>true</code> to trace templates and functions. */
  @Option (names = "--trace", description = "Print the template rules, named templates and functions entered to the error output.")
  boolean m_bTrace;

  /** <code>true</code> to trace also every instruction. */
  @Option (names = "--trace-instructions", description = "Like --trace, and also every instruction.")
  boolean m_bTraceInstructions;

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
  XsltRunCommand ()
  {}

  /**
   * @param sName
   *        The name from the command line. May not be <code>null</code>.
   * @return The expanded name; the prefix <code>xsl</code> stands for the XSLT namespace.
   */
  @NonNull
  private XdmQName _parseName (@NonNull final String sName)
  {
    final StaticContext aNameCtx = new StaticContext ().declareNamespace ("xsl", XdmConstants.NS_XSL);
    try
    {
      return CliHelper.parseName (sName, aNameCtx);
    }
    catch (final IllegalArgumentException ex)
    {
      throw new ParameterException (m_aSpec.commandLine (), ex.getMessage (), ex);
    }
  }

  /**
   * Add the stylesheet parameters to the request.
   *
   * @param aRequest
   *        The request. May not be <code>null</code>.
   * @param aSourceDoc
   *        The source document as context of the <code>--xparam</code> expressions. May be
   *        <code>null</code>.
   */
  private void _addParams (@NonNull final XsltTransformRequest aRequest, @Nullable final IXdmNode aSourceDoc)
  {
    if (m_aParams != null)
      for (final String sParam : m_aParams)
      {
        final CliHelper.NameValue aNV = CliHelper.splitNameValue (sParam);
        if (aNV.value () == null)
          throw new ParameterException (m_aSpec.commandLine (), "The parameter '" + sParam + "' is not of the form NAME=VALUE");
        aRequest.params ().put (_parseName (aNV.name ()), XdmSequence.of (XdmAtomic.untyped (aNV.value ())));
      }
    if (m_aXParams != null)
      for (final String sParam : m_aXParams)
      {
        final CliHelper.NameValue aNV = CliHelper.splitNameValue (sParam);
        if (aNV.value () == null)
          throw new ParameterException (m_aSpec.commandLine (), "The parameter '" + sParam + "' is not of the form NAME=XPATH");
        final CompiledXPath aXPath = XPathCompiler.compile (aNV.value (), new StaticContext ());
        DynamicContext aCtx = aXPath.createDynamicContext ();
        if (aSourceDoc != null)
          aCtx = aCtx.withFocus (aSourceDoc, 1, 1);
        final DynamicContext aFinalCtx = aCtx;
        aRequest.params ()
                .put (_parseName (aNV.name ()),
                      LargeStackRunner.run ( () -> aXPath.evaluate (aFinalCtx), "phoxtrot-cli-param", m_aEngineOptions.m_eThreading));
      }
  }

  /**
   * @return The URI of the result folder, always with a trailing slash, so that relative hrefs
   *         resolve inside it also before the folder exists.
   */
  @NonNull
  private URI _getResultDirUri ()
  {
    final String sUri = m_aResultDir.toAbsolutePath ().normalize ().toUri ().toString ();
    return URI.create (sUri.endsWith ("/") ? sUri : sUri + "/");
  }

  /**
   * Write the results of xsl:result-document to the result folder.
   *
   * @param aResult
   *        The result of the transformation. May not be <code>null</code>.
   * @param aLog
   *        The log handler. May not be <code>null</code>.
   * @throws IOException
   *         if a result cannot be written
   */
  private void _writeSecondaryResults (@NonNull final XsltTransformResult aResult, @NonNull final CliLogHandler aLog) throws IOException
  {
    if (m_aResultDir == null)
      return;
    final Path aDir = m_aResultDir.toAbsolutePath ().normalize ();
    final URI aBase = _getResultDirUri ();
    for (final XsltTransformResult.SecondaryResult aSecondary : aResult.secondaryResults ())
    {
      final Path aTarget;
      try
      {
        final URI aUri = aBase.resolve (aSecondary.href ());
        aTarget = "file".equalsIgnoreCase (aUri.getScheme ()) ? Path.of (aUri).normalize () : null;
      }
      catch (final RuntimeException ex)
      {
        throw new IOException ("The href '" + aSecondary.href () + "' of xsl:result-document cannot be resolved: " + ex.getMessage (), ex);
      }
      if (aTarget == null || !aTarget.startsWith (aDir))
        throw new IOException ("The href '" + aSecondary.href () + "' of xsl:result-document is outside of the folder '" + aDir + "'");
      if (aTarget.getParent () != null)
        Files.createDirectories (aTarget.getParent ());
      Files.writeString (aTarget, aSecondary.serialized (), StandardCharsets.UTF_8);
      aLog.info ("Wrote " + CliHelper.getDisplayLocation (aTarget.toUri ().toString ()));
    }
  }

  public Integer call () throws IOException
  {
    if (m_aSource == null && m_sInitialTemplate == null)
      throw new ParameterException (m_aSpec.commandLine (), "A source document or --initial-template is required");

    final CliLogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, m_aOutput == null);
    final CliResourceResolver aResolver = m_aAccessOptions.createResolver (aLog, m_aStylesheet, m_aSource);

    final XsltExecutable aExecutable;
    try
    {
      aExecutable = m_aCompileOptions.compile (m_aStylesheet,
                                               aResolver,
                                               m_aAccessOptions.m_bAllowFileAccess,
                                               m_aEngineOptions.m_eThreading,
                                               m_bStreamed,
                                               false,
                                               m_aResultDir != null);
    }
    catch (final IllegalArgumentException ex)
    {
      throw new ParameterException (m_aSpec.commandLine (), ex.getMessage (), ex);
    }
    catch (final XdmParseException ex)
    {
      aLog.error (m_aStylesheet + ": XTSE0010: The stylesheet is not well-formed: " + ex.getMessage ());
      return Integer.valueOf (PhoxtrotCli.EXIT_FAILURE);
    }
    catch (final RuntimeException ex)
    {
      aLog.error (CliHelper.getErrorText (ex, m_aStylesheet), aLog.isVerbose () ? ex : null);
      return Integer.valueOf (PhoxtrotCli.EXIT_FAILURE);
    }

    final XsltTransformRequest aRequest = new XsltTransformRequest ();
    IXdmNode aSourceDoc = null;
    if (m_aSource != null)
    {
      final String sSourceUri = m_aSource.toAbsolutePath ().toUri ().toString ();
      if (m_bStreamed)
      {
        // Opened through the resolver, which allows the folder of the source
        aRequest.setStreamedSourceUri (sSourceUri).setSourceStreamed (true);
      }
      else
      {
        try
        {
          aSourceDoc = CliHelper.parseDocument (m_aSource);
        }
        catch (final XdmParseException ex)
        {
          aLog.error (m_aSource + ": The source document cannot be read: " + ex.getMessage ());
          return Integer.valueOf (PhoxtrotCli.EXIT_ERROR);
        }
        aRequest.setGlobalContextItem (aSourceDoc);
      }
    }
    if (m_sInitialTemplate != null)
      aRequest.setInitialTemplate (_parseName (m_sInitialTemplate));
    if (m_sInitialMode != null)
      aRequest.setInitialMode (_parseName (m_sInitialMode));

    final CliRunListener aListener = new CliRunListener (aLog, m_bTraceInstructions);
    aRequest.setResolver (aResolver).setMessageListener (aListener).setThreading (m_aEngineOptions.m_eThreading);
    if (m_bTrace || m_bTraceInstructions)
      aRequest.setTraceListener (aListener);
    if (m_aEngineOptions.m_nMaxSteps > 0)
      aRequest.setMaxSteps (m_aEngineOptions.m_nMaxSteps);
    if (m_aResultDir != null)
      aRequest.setBaseOutputUri (_getResultDirUri ().toString ());
    else
      if (m_aOutput != null)
        aRequest.setBaseOutputUri (m_aOutput.toAbsolutePath ().toUri ().toString ());

    final XsltTransformResult aResult;
    try
    {
      _addParams (aRequest, aSourceDoc);
      if (m_aOutput != null)
      {
        final Path aParent = m_aOutput.toAbsolutePath ().getParent ();
        if (aParent != null)
          Files.createDirectories (aParent);
        try (final OutputStream aOS = Files.newOutputStream (m_aOutput))
        {
          aResult = aExecutable.transform (aRequest, aOS);
        }
      }
      else
        aResult = aExecutable.transform (aRequest, m_aSpec.commandLine ().getOut ());
    }
    catch (final ParameterException ex)
    {
      throw ex;
    }
    catch (final RuntimeException ex)
    {
      // Dynamic errors; the messages were printed when they were produced
      aLog.error (CliHelper.getErrorText (ex), aLog.isVerbose () ? ex : null);
      // Part of the result may have been written: leave no incomplete file behind
      if (m_aOutput != null)
        Files.deleteIfExists (m_aOutput);
      return Integer.valueOf (PhoxtrotCli.EXIT_FAILURE);
    }
    _writeSecondaryResults (aResult, aLog);
    if (m_aOutput != null)
      aLog.info ("Wrote " + m_aOutput);
    return Integer.valueOf (PhoxtrotCli.EXIT_OK);
  }
}

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
import java.util.List;
import java.util.concurrent.Callable;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.EStreamContextMode;
import com.helger.phoxtrot.xpath.LargeStackRunner;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.XPathStreamabilityAnalyzer;
import com.helger.phoxtrot.xpath.XPathStreamabilityResult;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * <code>xpath streamability</code>: analyse whether expressions or patterns can be evaluated while
 * streaming (XSLT 3.0 section 19).
 *
 * @author Philip Helger
 */
@Command (name = "streamability",
          mixinStandardHelpOptions = true,
          description = { "Analyse whether XPath expressions or match patterns can be evaluated while streaming, with the rules of XSLT 3.0 section 19.",
                          "Prints the verdict (MOTIONLESS, CONSUMING, BURST, FREE_RANGING), posture and sweep, and for a verdict other than MOTIONLESS the construct that causes it.",
                          "The exit code is 1 if an expression is not streamable in the selected mode (BURST counts as streamable in burst mode)." })
@NotThreadSafe
final class XPathStreamabilityCommand implements Callable <Integer>
{
  /** The model of this command. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The context mode. */
  @Option (names = "--mode",
           paramLabel = "MODE",
           defaultValue = "STREAMED",
           description = "How the context node is available: STREAMED (a streamed node, default), BURST (a buffered copy of its subtree) or GROUNDED (the whole tree in memory).")
  EStreamContextMode m_eMode;

  /** <code>true</code> to analyse match patterns. */
  @Option (names = "--pattern", description = "Analyse the arguments as XSLT match patterns instead of expressions.")
  boolean m_bPattern;

  /** Variables bound to streamed nodes. */
  @Option (names = "--streamed-var",
           paramLabel = "NAME",
           description = "Declare a variable that is bound to a streamed node. May be repeated.")
  List <String> m_aStreamedVars;

  /** Other variables. */
  @Option (names = "--declare",
           paramLabel = "NAME",
           description = "Declare a variable bound to a grounded value. May be repeated.")
  List <String> m_aVariables;

  /** <code>true</code> to know the XSLT functions. */
  @Option (names = "--xslt-functions",
           description = "Also know the signatures of the XSLT functions (key, current, document, ...).")
  boolean m_bXsltFunctions;

  /** The expression options. */
  @Mixin
  CliXPathOptions m_aXPathOptions;

  /** The engine options. */
  @Mixin
  CliEngineOptions m_aEngineOptions;

  /** The log options. */
  @Mixin
  CliLogOptions m_aLogOptions;

  /** Constructor. Called by picocli. */
  XPathStreamabilityCommand ()
  {}

  /**
   * @param aResult
   *        The analysis result. May not be <code>null</code>.
   * @return <code>true</code> if the result is streamable in the selected mode.
   */
  private boolean _isAccepted (@NonNull final XPathStreamabilityResult aResult)
  {
    return m_eMode == EStreamContextMode.BURST ? aResult.isBurstStreamable () : aResult.isStreamable ();
  }

  public Integer call () throws IOException
  {
    final ICommonsList <CliXPathOptions.Expression> aExprs = m_aXPathOptions.getAllExpressions ();
    if (aExprs.isEmpty ())
      throw new ParameterException (m_aSpec.commandLine (), "At least one expression or --file is required");

    final StaticContext aSC;
    final XPathStreamabilityAnalyzer aAnalyzer = new XPathStreamabilityAnalyzer ().setContextMode (m_eMode);
    try
    {
      aSC = m_aXPathOptions.createStaticContext ();
      if (m_bXsltFunctions)
        aSC.setFunctionLibrary (FunctionLibrary.createDefaultWithXsltSignatures ());
      if (m_aStreamedVars != null)
        for (final String sVar : m_aStreamedVars)
        {
          final XdmQName aName = CliHelper.parseName (sVar, aSC);
          aSC.declareVariable (aName);
          aAnalyzer.addStreamedVariable (aName);
        }
      if (m_aVariables != null)
        for (final String sVar : m_aVariables)
          aSC.declareVariable (CliHelper.parseName (sVar, aSC));
    }
    catch (final IllegalArgumentException ex)
    {
      throw new ParameterException (m_aSpec.commandLine (), ex.getMessage (), ex);
    }

    final CliLogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);
    int nFailed = 0;
    for (final CliXPathOptions.Expression aExpr : aExprs)
      try
      {
        final XPathStreamabilityResult aResult = LargeStackRunner.run ( () -> m_bPattern ? aAnalyzer.analyzePattern (aExpr.text (),
                                                                                                                       aSC)
                                                                                          : aAnalyzer.analyze (aExpr.text (), aSC),
                                                                         "phoxtrot-cli-xpath",
                                                                         m_aEngineOptions.m_eThreading);
        final String sText = aExpr.getLabel () + ": " + aResult;
        if (_isAccepted (aResult))
          aLog.info (sText);
        else
        {
          aLog.error (sText);
          nFailed++;
        }
      }
      catch (final XPathException ex)
      {
        aLog.error (aExpr.getErrorText (ex), aLog.isVerbose () ? ex : null);
        nFailed++;
      }
    if (nFailed > 0)
    {
      aLog.error (nFailed + " of " + aExprs.size () + " expression(s) are not streamable in mode " + m_eMode + " or have errors");
      return Integer.valueOf (PhoxtrotCli.EXIT_FAILURE);
    }
    aLog.info (aExprs.size () + " expression(s) are streamable in mode " + m_eMode);
    return Integer.valueOf (PhoxtrotCli.EXIT_OK);
  }
}

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

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xpath.LargeStackRunner;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.parser.XPathParser;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * <code>xpath check</code>: compile expressions and report their static errors, optionally with
 * static typing.
 *
 * @author Philip Helger
 */
@Command (name = "check",
          mixinStandardHelpOptions = true,
          description = "Compile XPath expressions and report their static errors with line and column, without evaluating them.")
@NotThreadSafe
final class XPathCheckCommand implements Callable <Integer>
{
  /** The model of this command. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The variables to declare. */
  @Option (names = "--declare",
           paramLabel = "NAME[=TYPE]",
           description = "Declare a variable the expressions may reference, optionally with its sequence type for static typing, e.g. --declare limit=xs:integer. May be repeated.")
  List <String> m_aVariables;

  /** <code>true</code> for static typing. */
  @Option (names = "--static-typing",
           description = "Infer the static types and raise type errors (XPTY0004, XPTY0019, XPTY0020, XPST0005) at compile time. Pessimistic: declare the context item type and the variable types.")
  boolean m_bStaticTyping;

  /** The static type of the context item. */
  @Option (names = "--context-type",
           paramLabel = "ITEMTYPE",
           description = "The static type of the context item for --static-typing, e.g. 'document-node()' or 'element(Invoice)'.")
  String m_sContextType;

  /** <code>true</code> for XPath 1.0 compatibility mode. */
  @Option (names = "--xpath10-compatibility", description = "Compile in XPath 1.0 compatibility mode.")
  boolean m_bXPath10;

  /** <code>true</code> to know the XSLT functions. */
  @Option (names = "--xslt-functions",
           description = "Also know the signatures of the XSLT functions (key, current, document, ...), for expressions of an XSLT-based language such as Schematron.")
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
  XPathCheckCommand ()
  {}

  /**
   * @return The static context of the expressions, from the options.
   * @throws IllegalArgumentException
   *         for a malformed option value
   */
  private StaticContext _createStaticContext ()
  {
    final StaticContext ret = m_aXPathOptions.createStaticContext ();
    if (m_bXsltFunctions)
      ret.setFunctionLibrary (FunctionLibrary.createDefaultWithXsltSignatures ());
    ret.setXPath10Compatibility (m_bXPath10);
    ret.setStaticTyping (m_bStaticTyping);
    if (m_sContextType != null)
    {
      final SeqType aType = XPathParser.parseSequenceType (m_sContextType, ret);
      if (aType.itemType () == null)
        throw new IllegalArgumentException ("The context item type '" + m_sContextType + "' is no item type");
      ret.setContextItemStaticType (aType.itemType ());
    }
    if (m_aVariables != null)
      for (final String sVar : m_aVariables)
      {
        final CliHelper.NameValue aNV = CliHelper.splitNameValue (sVar);
        ret.declareVariable (CliHelper.parseName (aNV.name (), ret),
                             aNV.value () == null ? null : XPathParser.parseSequenceType (aNV.value (), ret));
      }
    return ret;
  }

  public Integer call () throws IOException
  {
    final ICommonsList <CliXPathOptions.Expression> aExprs = m_aXPathOptions.getAllExpressions ();
    if (aExprs.isEmpty ())
      throw new ParameterException (m_aSpec.commandLine (), "At least one expression or --file is required");
    final StaticContext aSC;
    try
    {
      aSC = _createStaticContext ();
    }
    catch (final IllegalArgumentException | XPathException ex)
    {
      throw new ParameterException (m_aSpec.commandLine (), ex.getMessage (), ex);
    }

    final CliLogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);
    int nFailed = 0;
    for (final CliXPathOptions.Expression aExpr : aExprs)
      try
      {
        LargeStackRunner.run ( () -> XPathCompiler.compile (aExpr.text (), aSC),
                               "phoxtrot-cli-xpath",
                               m_aEngineOptions.m_eThreading);
        aLog.info (aExpr.getLabel () + ": OK");
      }
      catch (final XPathException ex)
      {
        aLog.error (aExpr.getErrorText (ex), aLog.isVerbose () ? ex : null);
        nFailed++;
      }
    if (nFailed > 0)
    {
      aLog.error (nFailed + " of " + aExprs.size () + " expression(s) have errors");
      return Integer.valueOf (PhoxtrotCli.EXIT_FAILURE);
    }
    aLog.info (aExprs.size () + " expression(s) checked without errors");
    return Integer.valueOf (PhoxtrotCli.EXIT_OK);
  }
}

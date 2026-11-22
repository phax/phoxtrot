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
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.LargeStackRunner;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.serialize.XdmSerializer;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * <code>xpath eval</code>: evaluate expressions against an optional context document and print the
 * results.
 *
 * @author Philip Helger
 */
@Command (name = "eval",
          mixinStandardHelpOptions = true,
          description = "Evaluate XPath expressions, optionally against a context document, and print each result serialized to the standard output.")
@NotThreadSafe
final class XPathEvalCommand implements Callable <Integer>
{
  /** The model of this command. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The context document. */
  @Option (names = { "-c", "--context" }, paramLabel = "FILE", description = "The XML document that is the context item.")
  Path m_aContext;

  /** Variables with a string value. */
  @Option (names = "--var",
           paramLabel = "NAME=VALUE",
           description = "Bind a variable to a value of type xs:untypedAtomic. May be repeated.")
  List <String> m_aVars;

  /** Variables with an XPath value. */
  @Option (names = "--xvar",
           paramLabel = "NAME=XPATH",
           description = "Bind a variable to the result of an XPath expression (with the same context item), e.g. --xvar 'limit=10'. May be repeated.")
  List <String> m_aXVars;

  /** The serialization method. */
  @Option (names = "--method",
           paramLabel = "METHOD",
           defaultValue = "adaptive",
           description = "The serialization method of the results: adaptive (default), xml, json or text.")
  String m_sMethod;

  /** <code>true</code> to indent. */
  @Option (names = "--indent", description = "Indent the serialized result.")
  boolean m_bIndent;

  /** The expression options. */
  @Mixin
  CliXPathOptions m_aXPathOptions;

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
  XPathEvalCommand ()
  {}

  /**
   * Create the root dynamic context of an expression: resolver, step limit, focus and variables.
   *
   * @param aXPath
   *        The compiled expression. May not be <code>null</code>.
   * @param aResolver
   *        The resolver. May not be <code>null</code>.
   * @param aContextDoc
   *        The context item. May be <code>null</code>.
   * @param aVars
   *        The variable values. May not be <code>null</code>.
   * @return The dynamic context. Never <code>null</code>.
   */
  @NonNull
  private DynamicContext _createDynamicContext (@NonNull final CompiledXPath aXPath,
                                                @NonNull final CliResourceResolver aResolver,
                                                @Nullable final IXdmNode aContextDoc,
                                                @NonNull final ICommonsOrderedMap <XdmQName, XdmSequence> aVars)
  {
    DynamicContext ret = aXPath.createDynamicContext ();
    ret.setResourceResolver (aResolver);
    if (m_aEngineOptions.m_nMaxSteps > 0)
      ret.setMaxSteps (m_aEngineOptions.m_nMaxSteps);
    if (aContextDoc != null)
      ret = ret.withFocus (aContextDoc, 1, 1);
    for (final var aEntry : aVars.entrySet ())
      ret = ret.withVariable (aEntry.getKey (), aEntry.getValue ());
    return ret;
  }

  /**
   * Compile and evaluate one expression on the thread of the engine options.
   *
   * @param sExpr
   *        The expression. May not be <code>null</code>.
   * @param aSC
   *        The static context. May not be <code>null</code>.
   * @param aResolver
   *        The resolver. May not be <code>null</code>.
   * @param aContextDoc
   *        The context item. May be <code>null</code>.
   * @param aVars
   *        The variable values. May not be <code>null</code>.
   * @return The result. Never <code>null</code>.
   */
  @NonNull
  private XdmSequence _evaluate (@NonNull final String sExpr,
                                 @NonNull final StaticContext aSC,
                                 @NonNull final CliResourceResolver aResolver,
                                 @Nullable final IXdmNode aContextDoc,
                                 @NonNull final ICommonsOrderedMap <XdmQName, XdmSequence> aVars)
  {
    return LargeStackRunner.run ( () -> {
      final CompiledXPath aXPath = XPathCompiler.compile (sExpr, aSC);
      return aXPath.evaluate (_createDynamicContext (aXPath, aResolver, aContextDoc, aVars));
    }, "phoxtrot-cli-xpath", m_aEngineOptions.m_eThreading);
  }

  public Integer call () throws IOException
  {
    final ICommonsList <CliXPathOptions.Expression> aExprs = m_aXPathOptions.getAllExpressions ();
    if (aExprs.isEmpty ())
      throw new ParameterException (m_aSpec.commandLine (), "At least one expression or --file is required");

    final CliLogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, true);
    final CliResourceResolver aResolver = m_aAccessOptions.createResolver (aLog, m_aContext);

    IXdmNode aContextDoc = null;
    if (m_aContext != null)
      try
      {
        aContextDoc = CliHelper.parseDocument (m_aContext);
      }
      catch (final XdmParseException ex)
      {
        aLog.error (m_aContext + ": The context document cannot be read: " + ex.getMessage ());
        return Integer.valueOf (PhoxtrotCli.EXIT_ERROR);
      }

    final StaticContext aSC;
    final ICommonsOrderedMap <XdmQName, XdmSequence> aVars = new CommonsLinkedHashMap <> ();
    try
    {
      aSC = m_aXPathOptions.createStaticContext ();
      aSC.setBaseUri (aContextDoc != null ? aContextDoc.getDocumentUri ()
                                          : Path.of ("").toAbsolutePath ().toUri ().toString ());
      if (m_aVars != null)
        for (final String sVar : m_aVars)
        {
          final CliHelper.NameValue aNV = CliHelper.splitNameValue (sVar);
          if (aNV.value () == null)
            throw new IllegalArgumentException ("The variable '" + sVar + "' is not of the form NAME=VALUE");
          final XdmQName aName = CliHelper.parseName (aNV.name (), aSC);
          aSC.declareVariable (aName);
          aVars.put (aName, XdmSequence.of (XdmAtomic.untyped (aNV.value ())));
        }
      if (m_aXVars != null)
        for (final String sVar : m_aXVars)
        {
          final CliHelper.NameValue aNV = CliHelper.splitNameValue (sVar);
          if (aNV.value () == null)
            throw new IllegalArgumentException ("The variable '" + sVar + "' is not of the form NAME=XPATH");
          final XdmQName aName = CliHelper.parseName (aNV.name (), aSC);
          // Evaluated with the variables bound so far
          final XdmSequence aValue = _evaluate (aNV.value (), aSC, aResolver, aContextDoc, aVars);
          aSC.declareVariable (aName);
          aVars.put (aName, aValue);
        }
    }
    catch (final IllegalArgumentException | XPathException ex)
    {
      throw new ParameterException (m_aSpec.commandLine (), ex.getMessage (), ex);
    }

    final SerializationParams aParams = new SerializationParams ().setMethod (m_sMethod).setIndent (m_bIndent);
    final PrintWriter aOut = m_aSpec.commandLine ().getOut ();
    int nFailed = 0;
    for (final CliXPathOptions.Expression aExpr : aExprs)
      try
      {
        final XdmSequence aResult = _evaluate (aExpr.text (), aSC, aResolver, aContextDoc, aVars);
        XdmSerializer.serialize (aResult, aParams, aOut);
        aOut.println ();
        aOut.flush ();
      }
      catch (final XPathException ex)
      {
        aLog.error (aExpr.getErrorText (ex), aLog.isVerbose () ? ex : null);
        nFailed++;
      }
    return Integer.valueOf (nFailed > 0 ? PhoxtrotCli.EXIT_FAILURE : PhoxtrotCli.EXIT_OK);
  }
}

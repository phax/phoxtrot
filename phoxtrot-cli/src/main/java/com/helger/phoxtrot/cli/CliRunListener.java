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

import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xslt.EXsltMessageKind;
import com.helger.phoxtrot.xslt.IXsltMessageListener;
import com.helger.phoxtrot.xslt.IXsltTraceListener;
import com.helger.phoxtrot.xslt.XsltMessage;
import com.helger.phoxtrot.xslt.XsltTraceFunction;
import com.helger.phoxtrot.xslt.XsltTraceInstruction;
import com.helger.phoxtrot.xslt.XsltTraceTemplate;

/**
 * Prints the messages (xsl:message, failed xsl:assert, mode warnings) and, if requested, the trace
 * of a transformation to the error output. It is called on the transformation thread only.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class CliRunListener implements IXsltMessageListener, IXsltTraceListener
{
  /** The output. */
  private final CliLogHandler m_aLog;
  /** <code>true</code> to trace also every instruction. */
  private final boolean m_bTraceInstructions;
  /** The current nesting of the trace, for the indentation. */
  private int m_nDepth;

  /**
   * Constructor.
   *
   * @param aLog
   *        The output. May not be <code>null</code>.
   * @param bTraceInstructions
   *        <code>true</code> to trace also every instruction (only used if this is the trace
   *        listener of the run).
   */
  CliRunListener (@NonNull final CliLogHandler aLog, final boolean bTraceInstructions)
  {
    ValueEnforcer.notNull (aLog, "Log");
    m_aLog = aLog;
    m_bTraceInstructions = bTraceInstructions;
  }

  /**
   * @param sModuleUri
   *        The module URI. May be <code>null</code>.
   * @param nLine
   *        The line, 0 if unknown.
   * @param nColumn
   *        The column, 0 if unknown.
   * @return <code>module:line:column</code> as far as known, empty if nothing is known.
   */
  @NonNull
  private static String _getLocation (@Nullable final String sModuleUri, final int nLine, final int nColumn)
  {
    final StringBuilder aSB = new StringBuilder ();
    final String sModule = CliHelper.getDisplayLocation (sModuleUri);
    if (sModule != null)
      aSB.append (sModule);
    if (nLine > 0)
    {
      aSB.append (':').append (nLine);
      if (nColumn > 0)
        aSB.append (':').append (nColumn);
    }
    return aSB.toString ();
  }

  /**
   * @param aName
   *        The name. May be <code>null</code>.
   * @return The lexical name if it has a prefix, otherwise the EQName for a name in a namespace.
   */
  @NonNull
  private static String _getName (@Nullable final XdmQName aName)
  {
    if (aName == null)
      return "";
    return aName.hasPrefix () || !aName.hasNamespace () ? aName.getLexicalName () : aName.getEQName ();
  }

  /**
   * @param aItem
   *        The context item. May be <code>null</code>.
   * @return A short description: the node kind and name of a node, otherwise the item text.
   */
  @NonNull
  private static String _getItem (@Nullable final IXdmItem aItem)
  {
    if (aItem == null)
      return "no context item";
    if (aItem instanceof final IXdmNode aNode)
    {
      final XdmQName aName = aNode.getName ();
      return aNode.getKind ().name ().toLowerCase (Locale.ROOT) + (aName == null ? "" : " " + _getName (aName));
    }
    return aItem.toString ();
  }

  /**
   * Print one trace line, indented by the current depth.
   *
   * @param sText
   *        The text. May not be <code>null</code>.
   */
  private void _trace (@NonNull final String sText)
  {
    m_aLog.printRunLine ("[trace] " + "  ".repeat (m_nDepth) + sText);
  }

  public void onMessage (@NonNull final XsltMessage aMessage)
  {
    // A terminating message (also a failed xsl:assert) is printed as the error of the run
    if (aMessage.terminate ())
      return;
    final String sLocation = _getLocation (aMessage.moduleUri (), aMessage.line (), aMessage.column ());
    final String sKind = aMessage.kind () == EXsltMessageKind.ASSERTION ? "[xsl:assert] " : "[xsl:message] ";
    m_aLog.printRunLine (sKind + (sLocation.isEmpty () ? "" : sLocation + ": ") + aMessage.text ());
  }

  @Override
  public void onWarning (@NonNull final XsltMessage aWarning)
  {
    final String sLocation = _getLocation (aWarning.moduleUri (), aWarning.line (), aWarning.column ());
    m_aLog.warn ((sLocation.isEmpty () ? "" : sLocation + ": ") + aWarning.text ());
  }

  @Override
  public void onTemplateRuleEnter (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
  {
    _trace ("template match=\"" +
            aTemplate.matchPattern () +
            "\"" +
            (aTemplate.mode () == null || XdmConstants.NS_XSL.equals (aTemplate.mode ().namespaceUri ()) ? ""
                                                                                                      : " mode=" +
                                                                                                        _getName (aTemplate.mode ())) +
            " for " +
            _getItem (aContextItem) +
            " (" +
            _getLocation (aTemplate.moduleUri (), aTemplate.line (), aTemplate.column ()) +
            ")");
    m_nDepth++;
  }

  @Override
  public void onTemplateRuleLeave (@NonNull final XsltTraceTemplate aTemplate, @NonNull final IXdmItem aContextItem)
  {
    m_nDepth--;
  }

  @Override
  public void onNamedTemplateEnter (@NonNull final XsltTraceTemplate aTemplate, @Nullable final IXdmItem aContextItem)
  {
    _trace ("template name=" +
            _getName (aTemplate.name ()) +
            " (" +
            _getLocation (aTemplate.moduleUri (), aTemplate.line (), aTemplate.column ()) +
            ")");
    m_nDepth++;
  }

  @Override
  public void onNamedTemplateLeave (@NonNull final XsltTraceTemplate aTemplate, @Nullable final IXdmItem aContextItem)
  {
    m_nDepth--;
  }

  @Override
  public void onFunctionEnter (@NonNull final XsltTraceFunction aFunction)
  {
    _trace ("function " +
            _getName (aFunction.name ()) +
            "#" +
            aFunction.arity () +
            " (" +
            _getLocation (aFunction.moduleUri (), aFunction.line (), aFunction.column ()) +
            ")");
    m_nDepth++;
  }

  @Override
  public void onFunctionLeave (@NonNull final XsltTraceFunction aFunction)
  {
    m_nDepth--;
  }

  @Override
  public boolean isTraceInstructions ()
  {
    return m_bTraceInstructions;
  }

  @Override
  public void onInstructionEnter (@NonNull final XsltTraceInstruction aInstruction)
  {
    _trace (_getName (aInstruction.name ()) +
            " (" +
            _getLocation (aInstruction.moduleUri (), aInstruction.line (), aInstruction.column ()) +
            ")");
    m_nDepth++;
  }

  @Override
  public void onInstructionLeave (@NonNull final XsltTraceInstruction aInstruction)
  {
    m_nDepth--;
  }
}

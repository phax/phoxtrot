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
package com.helger.phoxtrot.xpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.wrapper.Wrapper;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.IGenerator;

/**
 * Shared parts of the property based tests that evaluate expressions against
 * random documents: a generator of small XML documents, a static context with
 * the namespaces and variables the expressions use, and the comparison of two
 * evaluation outcomes. Compiled expressions are cached, as the same few
 * expressions are evaluated against many documents.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XPathPropertyTestHelper
{
  public static final String NS_P = "urn:p";
  public static final String NS_D = "urn:d";
  /** The names of the variables every expression may reference */
  public static final XdmQName VAR_V = new XdmQName ("v");
  public static final XdmQName VAR_W = new XdmQName ("w");

  private static final String [] ELEMENT_NAMES = { "a", "b", "c", "p:a", "p:b" };
  private static final String [] ATTRIBUTE_NAMES = { "x", "y", "p:x" };
  private static final String [] ATTRIBUTE_VALUES = { "1", "2", "a", "" };
  private static final String [] TEXTS = { "a", "1", " ", "x y", "2" };
  private static final int MAX_DEPTH = 4;
  private static final int MAX_CHILDREN = 3;

  /**
   * Small documents with elements in no namespace, in <code>urn:p</code> and in
   * the default namespace <code>urn:d</code>, attributes, text, comments and
   * processing instructions. Now and then an element below the root has 16 to
   * 40 children.
   */
  public static final IGenerator <String> DOCUMENTS = aChoices -> {
    final StringBuilder aSB = new StringBuilder ();
    _appendElement (aChoices, aSB, 0);
    return aSB.toString ();
  };

  private final Map <String, CompiledXPath> m_aCache = new HashMap <> ();
  private final StaticContext m_aStaticCtx;

  public XPathPropertyTestHelper ()
  {
    m_aStaticCtx = new StaticContext ();
    m_aStaticCtx.declareNamespace ("p", NS_P);
    m_aStaticCtx.declareNamespace ("d", NS_D);
    m_aStaticCtx.declareVariable (VAR_V);
    m_aStaticCtx.declareVariable (VAR_W);
  }

  private static void _appendChild (@NonNull final ChoiceSequence aChoices,
                                    @NonNull final StringBuilder aSB,
                                    final int nDepth,
                                    final boolean bMostlyElements)
  {
    final int nKind = (int) aChoices.draw (bMostlyElements ? 9 : 5);
    switch (nKind)
    {
      case 1:
        aSB.append (TEXTS[(int) aChoices.draw (TEXTS.length - 1)]);
        break;
      case 2:
        aSB.append ("<!--c-->");
        break;
      case 3:
        aSB.append ("<?pi d?>");
        break;
      default:
        // Elements are the most frequent kind; the recursion is bounded by MAX_DEPTH
        _appendElement (aChoices, aSB, nDepth + 1);
        break;
    }
  }

  private static void _appendElement (@NonNull final ChoiceSequence aChoices,
                                      @NonNull final StringBuilder aSB,
                                      final int nDepth)
  {
    final String sName = ELEMENT_NAMES[(int) aChoices.draw (ELEMENT_NAMES.length - 1)];
    aSB.append ('<').append (sName);
    if (nDepth == 0)
      aSB.append (" xmlns:p=\"" + NS_P + "\"");
    else
      if (aChoices.draw (9) == 0)
      {
        // A default namespace: the unprefixed names below are in it
        aSB.append (" xmlns=\"" + NS_D + "\"");
      }
    final Set <String> aUsed = new HashSet <> ();
    final int nAttrs = (int) aChoices.draw (2);
    for (int i = 0; i < nAttrs; ++i)
    {
      final String sAttr = ATTRIBUTE_NAMES[(int) aChoices.draw (ATTRIBUTE_NAMES.length - 1)];
      final String sValue = ATTRIBUTE_VALUES[(int) aChoices.draw (ATTRIBUTE_VALUES.length - 1)];
      if (aUsed.add (sAttr))
        aSB.append (' ').append (sAttr).append ("=\"").append (sValue).append ('"');
    }
    aSB.append ('>');
    if (nDepth < MAX_DEPTH)
    {
      // Now and then a wide element below the root, for the code paths of parents with many children
      final boolean bWide = nDepth == 1 && aChoices.draw (5) == 0;
      final int nChildren = bWide ? 16 + (int) aChoices.draw (24) : (int) aChoices.draw (MAX_CHILDREN);
      for (int i = 0; i < nChildren; ++i)
        _appendChild (aChoices, aSB, bWide ? MAX_DEPTH - 1 : nDepth, bWide);
    }
    aSB.append ("</").append (sName).append ('>');
  }


  @NonNull
  public static IXdmNode parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException ("Generated XML is invalid: " + sXml, ex);
    }
  }

  @NonNull
  public CompiledXPath compile (@NonNull final String sExpr)
  {
    return m_aCache.computeIfAbsent (sExpr, x -> XPathCompiler.compile (x, m_aStaticCtx));
  }

  /**
   * Evaluate an expression with a fresh dynamic context (so nothing is shared
   * between two evaluations through the run memo).
   *
   * @param sExpr
   *        The expression.
   * @param aContextItem
   *        The context item, may be <code>null</code>.
   * @param aV
   *        The value of <code>$v</code>.
   * @param aW
   *        The value of <code>$w</code>.
   * @return The result.
   */
  @NonNull
  public XdmSequence eval (@NonNull final String sExpr,
                           @Nullable final IXdmItem aContextItem,
                           @NonNull final XdmSequence aV,
                           @NonNull final XdmSequence aW)
  {
    final CompiledXPath aXPath = compile (sExpr);
    DynamicContext aCtx = aXPath.createDynamicContext ().withVariable (VAR_V, aV).withVariable (VAR_W, aW);
    if (aContextItem != null)
      aCtx = aCtx.withFocus (aContextItem, 1, 1);
    return aXPath.evaluate (aCtx);
  }

  @NonNull
  public XdmSequence eval (@NonNull final String sExpr, @Nullable final IXdmItem aContextItem)
  {
    return eval (sExpr, aContextItem, XdmSequence.EMPTY, XdmSequence.EMPTY);
  }

  /**
   * @return The error code of the evaluation, or <code>null</code> if it
   *         succeeded (the result is then put into the holder).
   */
  @Nullable
  private String _tryEval (@NonNull final String sExpr,
                           @Nullable final IXdmItem aContextItem,
                           @NonNull final XdmSequence aV,
                           @NonNull final XdmSequence aW,
                           @NonNull final Wrapper <XdmSequence> aResult)
  {
    try
    {
      aResult.set (eval (sExpr, aContextItem, aV, aW));
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  /**
   * Assert that two sequences are the same: the same nodes (by identity) and
   * atomic values of the same type and lexical form, in the same order.
   *
   * @param sMsg
   *        Message prefix.
   * @param aExpected
   *        Expected sequence.
   * @param aActual
   *        Actual sequence.
   */
  public static void assertSameItems (@NonNull final String sMsg,
                                      @NonNull final XdmSequence aExpected,
                                      @NonNull final XdmSequence aActual)
  {
    assertEquals (sMsg + ": size", aExpected.size (), aActual.size ());
    for (int i = 0; i < aExpected.size (); ++i)
    {
      final IXdmItem aE = aExpected.get (i);
      final IXdmItem aA = aActual.get (i);
      if (aE instanceof final XdmAtomic aEAtomic)
      {
        if (!(aA instanceof final XdmAtomic aAAtomic))
        {
          fail (sMsg + ": item " + i + " is no atomic value");
          return;
        }
        assertSame (sMsg + ": type of item " + i, aEAtomic.getType (), aAAtomic.getType ());
        assertEquals (sMsg + ": item " + i, aEAtomic.getLexical (), aAAtomic.getLexical ());
      }
      else
        assertSame (sMsg + ": item " + i, aE, aA);
    }
  }

  /**
   * Evaluate two expressions that must be equivalent and assert that they
   * either raise the same error or return the same items.
   *
   * @param sExpr
   *        The expression under test, typically one an optimisation applies to.
   * @param sReference
   *        The equivalent reference expression, written so that the
   *        optimisation does not apply.
   * @param aContextItem
   *        The context item, may be <code>null</code>.
   * @param aV
   *        The value of <code>$v</code>.
   * @param aW
   *        The value of <code>$w</code>.
   */
  public void assertEquivalent (@NonNull final String sExpr,
                                @NonNull final String sReference,
                                @Nullable final IXdmItem aContextItem,
                                @NonNull final XdmSequence aV,
                                @NonNull final XdmSequence aW)
  {
    final Wrapper <XdmSequence> aExprResult = new Wrapper <> ();
    final Wrapper <XdmSequence> aRefResult = new Wrapper <> ();
    final String sExprError = _tryEval (sExpr, aContextItem, aV, aW, aExprResult);
    final String sRefError = _tryEval (sReference, aContextItem, aV, aW, aRefResult);
    final String sMsg = "'" + sExpr + "' vs. '" + sReference + "'";
    assertEquals (sMsg + ": error code", sRefError, sExprError);
    if (sRefError == null)
      assertSameItems (sMsg, aRefResult.get (), aExprResult.get ());
  }

  public void assertEquivalent (@NonNull final String sExpr,
                                @NonNull final String sReference,
                                @Nullable final IXdmItem aContextItem)
  {
    assertEquivalent (sExpr, sReference, aContextItem, XdmSequence.EMPTY, XdmSequence.EMPTY);
  }
}

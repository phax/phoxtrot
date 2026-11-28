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
package com.helger.phoxtrot.xpath.eval;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * An {@link IExpr} compiled into a tree of evaluation nodes (closure compilation, see
 * {@link XPathEvaluator#compile(IExpr)}): every node holds its compiled children, so an evaluation
 * dispatches by a virtual call per node instead of a type switch over the expression tree. The
 * semantics are those of the expression; the static context still comes from the dynamic context
 * of each evaluation. Compiled expressions keep no state of a run and can be shared by concurrent
 * evaluations. Evaluate them through {@link XPathEvaluator#evaluate(AbstractCompiledExpr, DynamicContext)}
 * and {@link XPathEvaluator#evaluateEffectiveBooleanValue(AbstractCompiledExpr, DynamicContext)}, which
 * map a too deep Java stack to XPDY0130.
 *
 * @author Philip Helger
 */
@Immutable
public abstract class AbstractCompiledExpr
{
  private final IExpr m_aExpr;

  AbstractCompiledExpr (@NonNull final IExpr aExpr)
  {
    m_aExpr = aExpr;
  }

  /**
   * Get the expression this node was compiled from.
   *
   * @return The expression this node was compiled from, for the analyses that look at its shape.
   *         Never <code>null</code>.
   */
  @NonNull
  public final IExpr getExpression ()
  {
    return m_aExpr;
  }

  /**
   * Evaluate the node. Errors from a too deep Java stack are not mapped here.
   *
   * @param aCtx
   *        The dynamic context.
   * @return The value. Never <code>null</code>.
   */
  @NonNull
  abstract XdmSequence evaluate (@NonNull DynamicContext aCtx);

  /**
   * The effective boolean value for the shapes that can stop at the first decisive item (XPath
   * 3.1 section 2.3.4 allows to skip the evaluation of parts that do not change the result).
   *
   * @param aCtx
   *        The dynamic context.
   * @param bPredicate
   *        <code>true</code> if the expression is a predicate. Then only shapes whose value can
   *        never be a number are handled, since a number would be compared with the position.
   * @return <code>null</code> for any other shape: the caller evaluates it completely. This
   *         implementation always returns <code>null</code>.
   */
  @Nullable
  Boolean tryEarlyEbv (@NonNull final DynamicContext aCtx, final boolean bPredicate)
  {
    return null;
  }

  /**
   * The effective boolean value, stopping at the first decisive item where the shape allows it
   * ({@link #tryEarlyEbv(DynamicContext, boolean)}) and evaluating completely otherwise.
   *
   * @param aCtx
   *        The dynamic context.
   * @return The effective boolean value.
   */
  final boolean getEbv (@NonNull final DynamicContext aCtx)
  {
    // Optimisation (early exit): stop at the first decisive item where the shape allows it
    final Boolean aEarly = tryEarlyEbv (aCtx, false);
    if (aEarly != null)
      return aEarly.booleanValue ();
    return Atomizer.getEffectiveBooleanValue (evaluate (aCtx));
  }

  /**
   * Is the value non-empty? Paths whose last step is an axis step and filter expressions override
   * this to stop at the first item; this implementation evaluates completely.
   *
   * @param aCtx
   *        The dynamic context.
   * @return <code>true</code> if the value has at least one item.
   */
  boolean exists (@NonNull final DynamicContext aCtx)
  {
    return !evaluate (aCtx).isEmpty ();
  }

  @Override
  public String toString ()
  {
    return getClass ().getSimpleName () + "[" + m_aExpr + "]";
  }
}

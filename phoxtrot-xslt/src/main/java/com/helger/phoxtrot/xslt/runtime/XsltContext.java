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
package com.helger.phoxtrot.xslt.runtime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.context.VariableFrame;
import com.helger.phoxtrot.xpath.context.VariableSlot;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * The run-time context of an instruction: the XPath dynamic context (focus,
 * current item, variables, run-wide settings) whose host state is the
 * {@link XsltState}, plus the {@link Engine} of the transformation. Every
 * change creates a derived context; the only mutable field is a cache of the
 * dynamic context derived for expressions ({@link #getDynamicContextFor(StaticContext)}).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsltContext
{
  private final Engine m_aEngine;
  private final DynamicContext m_aDyn;
  // The dynamic context of the last expression evaluated in this context (getDynamicContextFor);
  // only a cache: a context is used by one thread, and a lost update only costs a new context
  private DynamicContext m_aLastExprDyn;

  public XsltContext (@NonNull final Engine aEngine, @NonNull final DynamicContext aDyn)
  {
    m_aEngine = aEngine;
    m_aDyn = aDyn;
  }

  /**
   * Get the XSLT context of an XPath function call made during a
   * transformation (for XSLT functions such as current-group()).
   *
   * @param aDyn
   *        The dynamic context the function was called with.
   * @return The XSLT context, or <code>null</code> outside a transformation
   *         (e.g. in a use-when expression).
   */
  @Nullable
  public static XsltContext of (@NonNull final DynamicContext aDyn)
  {
    if (aDyn.getHost () instanceof final Engine aEngine)
      return new XsltContext (aEngine, aDyn);
    return null;
  }

  @NonNull
  public Engine getEngine ()
  {
    return m_aEngine;
  }

  @NonNull
  public DynamicContext getDynamicContext ()
  {
    return m_aDyn;
  }

  /**
   * Get the dynamic context to evaluate an expression compiled with the given
   * static context: this context's dynamic context with that static context
   * ({@link DynamicContext#withStaticContext(StaticContext)}). The context
   * derived for the last expression is reused if its static context is
   * equivalent at run time
   * ({@link StaticContext#isRunTimeEquivalent(StaticContext)}).
   *
   * @param aStatic
   *        The static context of the expression.
   * @return The dynamic context. Never <code>null</code>.
   */
  @NonNull
  public DynamicContext getDynamicContextFor (@NonNull final StaticContext aStatic)
  {
    final DynamicContext aLast = m_aLastExprDyn;
    // Optimisation: the expressions evaluated in one context (a template body, an iteration) mostly
    // have equivalent static contexts, so only the first creates a context
    if (aLast != null && aLast.getStaticContext ().isRunTimeEquivalent (aStatic))
      return aLast;
    final DynamicContext ret = m_aDyn.withStaticContext (aStatic);
    m_aLastExprDyn = ret;
    return ret;
  }

  /** @return The XSLT state, never <code>null</code>. */
  @NonNull
  public XsltState getState ()
  {
    return m_aDyn.getHostState () instanceof final XsltState aState ? aState : XsltState.EMPTY;
  }

  /** @return The context item or <code>null</code> if the focus is absent. */
  @Nullable
  public IXdmItem getContextItem ()
  {
    return m_aDyn.getContextItem ();
  }

  public int getPosition ()
  {
    return m_aDyn.getPosition ();
  }

  public int getSize ()
  {
    return m_aDyn.getSize ();
  }

  /**
   * Change the focus; the XSLT current item (fn:current()) follows the
   * context item.
   *
   * @param aItem
   *        New context item, <code>null</code> for an absent focus.
   * @param nPosition
   *        1-based position.
   * @param nSize
   *        Size.
   * @return The derived context.
   */
  @NonNull
  public XsltContext withFocus (@Nullable final IXdmItem aItem, final int nPosition, final int nSize)
  {
    return new XsltContext (m_aEngine, m_aDyn.withFocus (aItem, nPosition, nSize).withCurrentItem (aItem));
  }

  /**
   * @return A context whose frame has the layout of the slot: this context if
   *         it has one, otherwise a derived context with a new frame (the first
   *         binding of the body in this activation).
   */
  @NonNull
  private XsltContext _withFrameFor (@NonNull final VariableSlot aSlot)
  {
    final VariableFrame aFrame = m_aDyn.getFrame ();
    if (aFrame != null && aFrame.getLayout () == aSlot.layout ())
      return this;
    return new XsltContext (m_aEngine, m_aDyn.withFrame (new VariableFrame (aSlot.layout ())));
  }

  /**
   * Bind a local variable by its slot (see
   * {@link com.helger.phoxtrot.xslt.compile.CompileContext#withVariable(XdmQName)}).
   * The frame of the body is changed in place, so the result is this context
   * unless it is the first binding of the body.
   *
   * @param aSlot
   *        The slot of the declaration.
   * @param aValue
   *        The value.
   * @return The context for the scope of the variable.
   */
  @NonNull
  public XsltContext withSlotValue (@NonNull final VariableSlot aSlot, @NonNull final XdmSequence aValue)
  {
    final XsltContext ret = _withFrameFor (aSlot);
    ret.m_aDyn.getFrame ().set (aSlot, aValue);
    return ret;
  }

  /**
   * Bind a local variable by its slot to a value computed on first use, see
   * {@link #withSlotValue(VariableSlot, XdmSequence)}.
   *
   * @param aSlot
   *        The slot of the declaration.
   * @param aValue
   *        The lazy value.
   * @return The context for the scope of the variable.
   */
  @NonNull
  public XsltContext withLazySlotValue (@NonNull final VariableSlot aSlot, final VariableFrame.@NonNull AbstractLazyValue aValue)
  {
    final XsltContext ret = _withFrameFor (aSlot);
    ret.m_aDyn.getFrame ().setLazy (aSlot, aValue);
    return ret;
  }

  /**
   * @param aFrame
   *        The frame of the variables bound by slot.
   * @return This context if it has that frame, otherwise a derived context
   *         with it.
   */
  @NonNull
  public XsltContext withFrame (@NonNull final VariableFrame aFrame)
  {
    return m_aDyn.getFrame () == aFrame ? this : new XsltContext (m_aEngine, m_aDyn.withFrame (aFrame));
  }

  @NonNull
  public XsltContext withState (@NonNull final XsltState aState)
  {
    return new XsltContext (m_aEngine, m_aDyn.withHostState (aState));
  }

  /**
   * @return A context with the same state but without focus and without local
   *         variables (global variables remain visible).
   */
  @NonNull
  public XsltContext withoutFocusAndVariables ()
  {
    return new XsltContext (m_aEngine, m_aDyn.withoutFocusAndVariables ());
  }

  /**
   * @param aKey
   *        Slot key.
   * @param <T>
   *        Value type.
   * @return The value of the slot in the current XSLT state.
   */
  @Nullable
  public <T> T get (@NonNull final XsltStateKey <T> aKey)
  {
    return getState ().get (aKey);
  }

  /**
   * @param aKey
   *        Slot key.
   * @param aValue
   *        New value.
   * @param <T>
   *        Value type.
   * @return The derived context.
   */
  @NonNull
  public <T> XsltContext with (@NonNull final XsltStateKey <T> aKey, @Nullable final T aValue)
  {
    return withState (getState ().with (aKey, aValue));
  }
}

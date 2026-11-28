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
import com.helger.phoxtrot.xpath.context.VariableFrameLayout;
import com.helger.phoxtrot.xpath.context.VariableSlot;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * The run-time context of an instruction: the XPath dynamic context (focus,
 * current item, variables, run-wide settings) whose host state is the
 * {@link XsltState}, plus the {@link Engine} of the transformation. Every
 * change creates a derived context. The dynamic context of the last
 * expression evaluated in it is kept
 * ({@link #getDynamicContextFor(StaticContext)}), and derived contexts start
 * from it; {@link #getDynamicContext()} gives the dynamic context with the
 * static context of the instructions.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsltContext
{
  private final Engine m_aEngine;
  // The dynamic context: the one the context was created with, or for a derived context that of
  // its parent's last expression with the change applied
  private final DynamicContext m_aDyn;
  // The static context of the instructions (that of the context passed to the public constructor)
  private final StaticContext m_aInstrStatic;
  // The dynamic context of the last expression evaluated in this context (getDynamicContextFor),
  // null while it is m_aDyn itself; only a cache: a context is used by one thread, and a lost
  // update only costs a new context. Kept apart from m_aDyn, so that the JIT can still remove a
  // context that does not escape
  private DynamicContext m_aLastExprDyn;
  // m_aDyn with m_aInstrStatic, created on first use when m_aDyn has another static context
  private DynamicContext m_aInstrDyn;

  private XsltContext (@NonNull final Engine aEngine,
                       @NonNull final DynamicContext aDyn,
                       @NonNull final StaticContext aInstrStatic)
  {
    m_aEngine = aEngine;
    m_aDyn = aDyn;
    m_aInstrStatic = aInstrStatic;
  }

  /**
   * @return The dynamic context a derived context starts from: that of the last expression
   *         evaluated in this context, or the dynamic context of this context. Both differ only in
   *         the static context.
   */
  @NonNull
  private DynamicContext _getBase ()
  {
    final DynamicContext aLast = m_aLastExprDyn;
    return aLast != null ? aLast : m_aDyn;
  }

  /**
   * Create an XSLT context for the given engine and dynamic context.
   *
   * @param aEngine
   *        The engine of the transformation.
   * @param aDyn
   *        The dynamic context; its static context is the static context of
   *        the instructions.
   */
  public XsltContext (@NonNull final Engine aEngine, @NonNull final DynamicContext aDyn)
  {
    this (aEngine, aDyn, aDyn.getStaticContext ());
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

  /**
   * Get the engine of the transformation.
   *
   * @return The engine, never <code>null</code>.
   */
  @NonNull
  public Engine getEngine ()
  {
    return m_aEngine;
  }

  /**
   * Get the dynamic context of the instructions.
   *
   * @return The dynamic context with the static context of the instructions
   *         (the one the context was created with), for instructions that
   *         read it (type conversions, collations, the function library).
   *         Never <code>null</code>.
   */
  @NonNull
  public DynamicContext getDynamicContext ()
  {
    final DynamicContext aDyn = m_aDyn;
    if (aDyn.getStaticContext () == m_aInstrStatic)
      return aDyn;
    DynamicContext ret = m_aInstrDyn;
    if (ret == null)
    {
      // Every dynamic context this one held differs only in the static context, so the view
      // created once stays valid
      ret = aDyn.withStaticContext (m_aInstrStatic);
      m_aInstrDyn = ret;
    }
    return ret;
  }

  /**
   * Get the dynamic context to evaluate an expression compiled with the given
   * static context: this context's dynamic context with that static context
   * ({@link DynamicContext#withStaticContext(StaticContext)}). The result is
   * kept, so the next expression with an equivalent static context at run
   * time ({@link StaticContext#isRunTimeEquivalent(StaticContext)}) reuses it,
   * and every context derived from this one starts from it.
   *
   * @param aStatic
   *        The static context of the expression.
   * @return The dynamic context. Never <code>null</code>.
   */
  @NonNull
  public DynamicContext getDynamicContextFor (@NonNull final StaticContext aStatic)
  {
    final DynamicContext aLast = m_aLastExprDyn;
    // Optimisation: the expressions of a stylesheet mostly have equivalent static contexts, and a
    // derived context (template call, iteration, variable body) starts from the one kept here, so
    // only a change of the static context creates a dynamic context
    if (aLast != null && aLast.getStaticContext ().isRunTimeEquivalent (aStatic))
      return aLast;
    final DynamicContext ret = m_aDyn.withStaticContext (aStatic);
    if (ret != m_aDyn)
      m_aLastExprDyn = ret;
    return ret;
  }

  /**
   * Get the XSLT state of this context.
   *
   * @return The XSLT state, never <code>null</code>.
   */
  @NonNull
  public XsltState getState ()
  {
    return m_aDyn.getHostState () instanceof final XsltState aState ? aState : XsltState.EMPTY;
  }

  /**
   * Get the context item.
   *
   * @return The context item or <code>null</code> if the focus is absent.
   */
  @Nullable
  public IXdmItem getContextItem ()
  {
    return m_aDyn.getContextItem ();
  }

  /**
   * Get the context position.
   *
   * @return The 1-based context position of the focus.
   */
  public int getPosition ()
  {
    return m_aDyn.getPosition ();
  }

  /**
   * Get the context size.
   *
   * @return The context size of the focus.
   */
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
    return new XsltContext (m_aEngine, _getBase ().withFocus (aItem, nPosition, nSize).withCurrentItem (aItem), m_aInstrStatic);
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
    return new XsltContext (m_aEngine, _getBase ().withFrame (new VariableFrame (aSlot.layout ())), m_aInstrStatic);
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
   * Get a context with the given variable frame.
   *
   * @param aFrame
   *        The frame of the variables bound by slot.
   * @return This context if it has that frame, otherwise a derived context
   *         with it.
   */
  @NonNull
  public XsltContext withFrame (@NonNull final VariableFrame aFrame)
  {
    return m_aDyn.getFrame () == aFrame ? this : new XsltContext (m_aEngine, _getBase ().withFrame (aFrame), m_aInstrStatic);
  }

  /**
   * Create the context of a template or stylesheet function body in one step: the focus of the
   * body (its context item also the current item), no local variables (global variables stay
   * visible), the given state and, if the body binds variables by slot, a new frame for them.
   * It replaces a chain of {@link #withoutFocusAndVariables()}, {@link #withFocus(IXdmItem, int, int)},
   * {@link #withState(XsltState)} and the frame created at the first binding.
   *
   * @param aItem
   *        The context item of the body, <code>null</code> for an absent focus.
   * @param nPosition
   *        1-based position, 0 with an absent focus.
   * @param nSize
   *        Size, 0 with an absent focus.
   * @param aState
   *        The state of the body.
   * @param aLayout
   *        The layout of the variables the body binds by slot, <code>null</code> to create the
   *        frame at the first binding (see {@link #withSlotValue(VariableSlot, XdmSequence)}).
   * @return The new context.
   */
  @NonNull
  public XsltContext forCall (@Nullable final IXdmItem aItem,
                              final int nPosition,
                              final int nSize,
                              @NonNull final XsltState aState,
                              @Nullable final VariableFrameLayout aLayout)
  {
    // Optimisation: one dynamic context per call instead of one per changed property, and the
    // frame of the parameters created with it
    return new XsltContext (m_aEngine,
                            _getBase ().forHostCall (aItem,
                                                nPosition,
                                                nSize,
                                                aState,
                                                aLayout == null ? null : new VariableFrame (aLayout)),
                            m_aInstrStatic);
  }

  /**
   * Derive a context with another XSLT state.
   *
   * @param aState
   *        The new XSLT state. May not be <code>null</code>.
   * @return The derived context, never <code>null</code>.
   */
  @NonNull
  public XsltContext withState (@NonNull final XsltState aState)
  {
    return new XsltContext (m_aEngine, _getBase ().withHostState (aState), m_aInstrStatic);
  }

  /**
   * Derive a context without focus and local variables.
   *
   * @return A context with the same state but without focus and without local
   *         variables (global variables remain visible).
   */
  @NonNull
  public XsltContext withoutFocusAndVariables ()
  {
    return new XsltContext (m_aEngine, _getBase ().withoutFocusAndVariables (), m_aInstrStatic);
  }

  /**
   * Get the value of an extension slot of the XSLT state.
   *
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
   * Derive a context whose XSLT state has an extension slot bound to a value.
   *
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

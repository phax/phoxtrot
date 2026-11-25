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
package com.helger.phoxtrot.xpath.context;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * The variables of one activation of a body (a template or function call, an iteration of a loop
 * whose body declares the first variable) by slot, see {@link VariableFrameLayout}. Unlike the
 * bindings of a {@link DynamicContext}, a frame is changed in place: binding a variable writes its
 * slot and creates no new context. This is safe because every declaration has its own slot and a
 * reference can only see declarations whose binding has run before it; a slot is written again
 * only by the next execution of the same declaration (the next iteration of a loop), when the
 * previous binding is out of scope. Whatever keeps a context beyond the scope of its variables (an
 * inline function item) keeps a {@link #getSnapshot() snapshot} instead. A lazily bound value is
 * evaluated with the frame it is read from, so that it sees the same variables through a snapshot
 * as through the frame itself.
 * <p>
 * A frame belongs to one evaluation thread, like the dynamic context.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class VariableFrame
{
  /**
   * A value computed when the variable is first read, and only then; computed once (also when
   * read through a snapshot), a failure is kept as well and raised again on every later read, so
   * that every reference sees the same outcome.
   */
  @NotThreadSafe
  public abstract static class AbstractLazyValue
  {
    private static final int STATE_OPEN = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_DONE = 2;

    private int m_nState = STATE_OPEN;
    private XdmSequence m_aValue;
    private RuntimeException m_aFailure;

    /**
     * Compute the value. Called at most once.
     *
     * @param aHolder
     *        The frame the value is read from: the frame it was bound in or a snapshot of it. The
     *        computation must read the variables of the body from this frame.
     * @return The value. May not be <code>null</code>.
     */
    @NonNull
    protected abstract XdmSequence compute (@NonNull VariableFrame aHolder);

    /**
     * Called once after the computation, also after a failed one: a subclass releases what it
     * only needed for the computation (the context of the declaration), so that a kept value
     * holds nothing else alive. This implementation does nothing.
     */
    protected void onComputed ()
    {}

    /**
     * @param aHolder
     *        The frame the value is read from.
     * @return The value, computed on the first call and kept.
     * @throws RuntimeException
     *         the exception the computation raised, on the first and on every later call
     * @throws IllegalStateException
     *         if the computation reads this same value (a variable that depends on itself)
     */
    @NonNull
    final XdmSequence get (@NonNull final VariableFrame aHolder)
    {
      if (m_nState == STATE_DONE)
      {
        if (m_aFailure != null)
          throw m_aFailure;
        return m_aValue;
      }
      if (m_nState == STATE_RUNNING)
        throw new IllegalStateException ("A lazily bound variable depends on its own value");
      m_nState = STATE_RUNNING;
      try
      {
        final XdmSequence aValue = compute (aHolder);
        ValueEnforcer.notNull (aValue, "Value");
        m_aValue = aValue;
        return aValue;
      }
      catch (final RuntimeException ex)
      {
        m_aFailure = ex;
        throw ex;
      }
      finally
      {
        m_nState = STATE_DONE;
        onComputed ();
      }
    }
  }

  private final VariableFrameLayout m_aLayout;
  // XdmSequence or AbstractLazyValue; null for a slot not bound yet
  private final Object [] m_aValues;

  private VariableFrame (@NonNull final VariableFrameLayout aLayout, @NonNull final Object [] aValues)
  {
    m_aLayout = aLayout;
    m_aValues = aValues;
  }

  /**
   * @param aLayout
   *        The layout of the body. Its size must be final.
   */
  public VariableFrame (@NonNull final VariableFrameLayout aLayout)
  {
    this (aLayout, new Object [aLayout.getSize ()]);
  }

  @NonNull
  public VariableFrameLayout getLayout ()
  {
    return m_aLayout;
  }

  /**
   * Bind a slot to a value.
   *
   * @param aSlot
   *        The slot, of the layout of this frame.
   * @param aValue
   *        The value.
   */
  public void set (@NonNull final VariableSlot aSlot, @NonNull final XdmSequence aValue)
  {
    m_aValues[aSlot.index ()] = aValue;
  }

  /**
   * Bind a slot to a value computed on first read.
   *
   * @param aSlot
   *        The slot, of the layout of this frame.
   * @param aValue
   *        The lazy value.
   */
  public void setLazy (@NonNull final VariableSlot aSlot, @NonNull final AbstractLazyValue aValue)
  {
    m_aValues[aSlot.index ()] = aValue;
  }

  /**
   * @param nIndex
   *        The slot index.
   * @return The value of the slot, computed first if it is bound lazily.
   * @throws XPathException
   *         XPDY0002 if the slot is not bound
   */
  @NonNull
  public XdmSequence get (final int nIndex)
  {
    final Object aValue = m_aValues[nIndex];
    if (aValue instanceof final XdmSequence aSeq)
      return aSeq;
    if (aValue instanceof final AbstractLazyValue aLazy)
      return aLazy.get (this);
    throw XPathException.noContext ("No value is bound to the variable in slot " + nIndex);
  }

  /**
   * @param nIndex
   *        The slot index.
   * @return An identity token of the binding of the slot (see
   *         {@link DynamicContext#getVariableToken(com.helger.phoxtrot.xdm.XdmQName)}), or
   *         <code>null</code> if the slot is not bound.
   */
  @Nullable
  public Object getToken (final int nIndex)
  {
    return m_aValues[nIndex];
  }

  /**
   * @return A copy of this frame with the bindings as they are now, for a context that is kept
   *         beyond the scope of the variables. Lazy values are shared, so they are computed once.
   */
  @NonNull
  public VariableFrame getSnapshot ()
  {
    return new VariableFrame (m_aLayout, m_aValues.clone ());
  }

  @Override
  public String toString ()
  {
    return "VariableFrame[" + m_aValues.length + "]";
  }
}

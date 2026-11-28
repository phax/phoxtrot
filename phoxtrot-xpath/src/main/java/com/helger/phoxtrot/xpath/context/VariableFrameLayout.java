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

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * The slots of the variables of one body of a host language (for XSLT: the local variables and
 * parameters of a template, function or other construct whose variables start from no local
 * variable). Every declaration gets its own slot, so a slot is never shared by two variables in
 * scope at the same time, and the value of a variable evaluated later (lazily) never overwrites
 * another one. Filled while the host compiles the body and only read afterwards; a compiled
 * artefact that holds it is handed over safely before it is shared.
 *
 * @author Philip Helger
 * @see VariableFrame
 * @see VariableSlot
 */
@NotThreadSafe
public final class VariableFrameLayout
{
  private int m_nSize;

  /** Create a new layout without slots. */
  public VariableFrameLayout ()
  {}

  /**
   * Allocate the slot of one more variable declaration.
   *
   * @param aName
   *        The variable name.
   * @return The slot. Never <code>null</code>.
   */
  @NonNull
  public VariableSlot allocateSlot (@NonNull final XdmQName aName)
  {
    return new VariableSlot (this, m_nSize++, aName);
  }

  /**
   * Get the number of slots of this layout.
   *
   * @return The number of slots allocated so far.
   */
  @Nonnegative
  public int getSize ()
  {
    return m_nSize;
  }

  @Override
  public String toString ()
  {
    return "VariableFrameLayout[" + m_nSize + "]";
  }
}

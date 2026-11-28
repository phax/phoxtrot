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
package com.helger.phoxtrot.xslt.tree;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;

/**
 * A node of a stylesheet module after static processing (use-when, shadow
 * attributes): either an element or a text node. Comments and processing
 * instructions of the stylesheet are not represented, adjacent text is merged.
 * The class is sealed: every node is an {@link XslElement} or an {@link XslText}.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public abstract sealed class AbstractXslNode permits XslElement, XslText
{
  private final XslModule m_aModule;
  private XslElement m_aParent;

  /**
   * Constructor.
   *
   * @param aModule
   *        The module this node belongs to. May not be <code>null</code>.
   */
  protected AbstractXslNode (@NonNull final XslModule aModule)
  {
    m_aModule = aModule;
  }

  /**
   * Get the stylesheet module of this node.
   *
   * @return The module this node belongs to.
   */
  @NonNull
  public final XslModule getModule ()
  {
    return m_aModule;
  }

  /**
   * Get the parent element of this node.
   *
   * @return The parent element or <code>null</code> for a module root.
   */
  @Nullable
  public final XslElement getParent ()
  {
    return m_aParent;
  }

  final void setParent (@Nullable final XslElement aParent)
  {
    m_aParent = aParent;
  }

  /**
   * Get the source line of this node.
   *
   * @return The 1-based source line, 0 if unknown.
   */
  public abstract int getLine ();

  /**
   * Get the source column of this node.
   *
   * @return The 1-based source column, 0 if unknown.
   */
  public abstract int getColumn ();
}

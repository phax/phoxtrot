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
package com.helger.phoxtrot.xslt.model;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xslt.compile.CompiledPattern;

/**
 * One template rule of a mode: a template with one alternative of its match
 * pattern (a union pattern without explicit priority gives one rule per
 * alternative, XSLT 3.0 section 6.4), its priority and its position in the
 * declaration order.
 *
 * @author Philip Helger
 */
@Immutable
public final class TemplateRule
{
  private final Template m_aTemplate;
  private final CompiledPattern m_aPattern;
  private final double m_dPriority;
  private final int m_nOrder;

  /**
   * Create a template rule.
   *
   * @param aTemplate
   *        The owning template. May not be <code>null</code>.
   * @param aPattern
   *        The pattern alternative of this rule. May not be
   *        <code>null</code>.
   * @param dPriority
   *        The priority of the rule.
   * @param nOrder
   *        The position in the declaration order.
   */
  public TemplateRule (@NonNull final Template aTemplate,
                       @NonNull final CompiledPattern aPattern,
                       final double dPriority,
                       final int nOrder)
  {
    m_aTemplate = aTemplate;
    m_aPattern = aPattern;
    m_dPriority = dPriority;
    m_nOrder = nOrder;
  }

  /**
   * Get the owning template.
   *
   * @return The template, never <code>null</code>.
   */
  @NonNull
  public Template getTemplate ()
  {
    return m_aTemplate;
  }

  /**
   * Get the pattern alternative of this rule.
   *
   * @return The pattern, never <code>null</code>.
   */
  @NonNull
  public CompiledPattern getPattern ()
  {
    return m_aPattern;
  }

  /**
   * Get the priority of this rule.
   *
   * @return The priority (explicit or default).
   */
  public double getPriority ()
  {
    return m_dPriority;
  }

  /**
   * Get the import precedence of the owning template.
   *
   * @return The import precedence of the owning template.
   */
  public int getPrecedence ()
  {
    return m_aTemplate.getPrecedence ();
  }

  /**
   * Get the position of this rule in the declaration order.
   *
   * @return Position in the declaration order (later is higher).
   */
  public int getOrder ()
  {
    return m_nOrder;
  }

  @Override
  public String toString ()
  {
    return "rule " + m_aPattern.pattern ().getSource () + " priority " + m_dPriority + " precedence " + getPrecedence ();
  }
}

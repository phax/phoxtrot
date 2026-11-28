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
package com.helger.phoxtrot.xpath.ast;

/**
 * Comparison operators of the three comparison families.
 *
 * @author Philip Helger
 */
public enum EComparisonOp
{
  /** The general comparison <code>=</code>. */
  GENERAL_EQ,
  /** The general comparison <code>!=</code>. */
  GENERAL_NE,
  /** The general comparison <code>&lt;</code>. */
  GENERAL_LT,
  /** The general comparison <code>&lt;=</code>. */
  GENERAL_LE,
  /** The general comparison <code>&gt;</code>. */
  GENERAL_GT,
  /** The general comparison <code>&gt;=</code>. */
  GENERAL_GE,
  /** The value comparison <code>eq</code>. */
  VALUE_EQ,
  /** The value comparison <code>ne</code>. */
  VALUE_NE,
  /** The value comparison <code>lt</code>. */
  VALUE_LT,
  /** The value comparison <code>le</code>. */
  VALUE_LE,
  /** The value comparison <code>gt</code>. */
  VALUE_GT,
  /** The value comparison <code>ge</code>. */
  VALUE_GE,
  /** <code>is</code> */
  NODE_IS,
  /** <code>&lt;&lt;</code> */
  NODE_PRECEDES,
  /** <code>&gt;&gt;</code> */
  NODE_FOLLOWS;

  /**
   * Check whether this is a general comparison.
   *
   * @return <code>true</code> for the general comparisons <code>=</code>,
   *         <code>!=</code>, <code>&lt;</code>, <code>&lt;=</code>,
   *         <code>&gt;</code> and <code>&gt;=</code>.
   */
  public boolean isGeneral ()
  {
    return ordinal () <= GENERAL_GE.ordinal ();
  }

  /**
   * Check whether this is a value comparison.
   *
   * @return <code>true</code> for the value comparisons <code>eq</code>,
   *         <code>ne</code>, <code>lt</code>, <code>le</code>, <code>gt</code>
   *         and <code>ge</code>.
   */
  public boolean isValue ()
  {
    return ordinal () >= VALUE_EQ.ordinal () && ordinal () <= VALUE_GE.ordinal ();
  }

  /**
   * Check whether this is a node comparison.
   *
   * @return <code>true</code> for the node comparisons <code>is</code>,
   *         <code>&lt;&lt;</code> and <code>&gt;&gt;</code>.
   */
  public boolean isNode ()
  {
    return ordinal () >= NODE_IS.ordinal ();
  }
}

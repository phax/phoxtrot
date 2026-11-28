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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The XPath axes.
 *
 * @author Philip Helger
 */
public enum EAxis
{
  /** The <code>child</code> axis. */
  CHILD ("child"),
  /** The <code>descendant</code> axis. */
  DESCENDANT ("descendant"),
  /** The <code>attribute</code> axis. */
  ATTRIBUTE ("attribute"),
  /** The <code>self</code> axis. */
  SELF ("self"),
  /** The <code>descendant-or-self</code> axis. */
  DESCENDANT_OR_SELF ("descendant-or-self"),
  /** The <code>following-sibling</code> axis. */
  FOLLOWING_SIBLING ("following-sibling"),
  /** The <code>following</code> axis. */
  FOLLOWING ("following"),
  /** The <code>namespace</code> axis. */
  NAMESPACE ("namespace"),
  /** The <code>parent</code> axis. */
  PARENT ("parent"),
  /** The <code>ancestor</code> axis. */
  ANCESTOR ("ancestor"),
  /** The <code>preceding-sibling</code> axis. */
  PRECEDING_SIBLING ("preceding-sibling"),
  /** The <code>preceding</code> axis. */
  PRECEDING ("preceding"),
  /** The <code>ancestor-or-self</code> axis. */
  ANCESTOR_OR_SELF ("ancestor-or-self");

  private final String m_sName;

  EAxis (@NonNull final String sName)
  {
    m_sName = sName;
  }

  /**
   * Get the name of the axis as written in XPath.
   *
   * @return The axis name, e.g. <code>descendant-or-self</code>. Never
   *         <code>null</code>.
   */
  @NonNull
  public String getName ()
  {
    return m_sName;
  }

  /**
   * Check whether this is a reverse axis.
   *
   * @return <code>true</code> for axes that deliver nodes in reverse document order.
   */
  public boolean isReverse ()
  {
    return this == PARENT ||
           this == ANCESTOR ||
           this == PRECEDING_SIBLING ||
           this == PRECEDING ||
           this == ANCESTOR_OR_SELF;
  }

  /**
   * Find the axis with the passed name.
   *
   * @param sName
   *        An axis name as written in XPath, e.g.
   *        <code>descendant-or-self</code>. May be <code>null</code>.
   * @return The axis of that name, or <code>null</code> if there is none.
   */
  @Nullable
  public static EAxis getFromName (@Nullable final String sName)
  {
    for (final EAxis e : values ())
      if (e.m_sName.equals (sName))
        return e;
    return null;
  }
}

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
import org.jspecify.annotations.Nullable;

/**
 * The on-no-match values of xsl:mode (XSLT 3.0 section 6.7).
 *
 * @author Philip Helger
 */
public enum EOnNoMatch
{
  /** on-no-match="deep-copy" */
  DEEP_COPY ("deep-copy"),
  /** on-no-match="shallow-copy" */
  SHALLOW_COPY ("shallow-copy"),
  /** on-no-match="deep-skip" */
  DEEP_SKIP ("deep-skip"),
  /** on-no-match="shallow-skip" */
  SHALLOW_SKIP ("shallow-skip"),
  /** on-no-match="text-only-copy" */
  TEXT_ONLY_COPY ("text-only-copy"),
  /** on-no-match="fail" */
  FAIL ("fail");

  private final String m_sID;

  EOnNoMatch (@NonNull final String sID)
  {
    m_sID = sID;
  }

  /**
   * Get the value of the on-no-match attribute this constant stands for.
   *
   * @return The attribute value, e.g. <code>"shallow-copy"</code>. Never
   *         <code>null</code>.
   */
  @NonNull
  public String getID ()
  {
    return m_sID;
  }

  /**
   * Find the constant for an on-no-match attribute value.
   *
   * @param sID
   *        The value of the on-no-match attribute, e.g.
   *        <code>"shallow-copy"</code>. Compared exactly, so the caller trims.
   * @return The matching value, or <code>null</code> if the ID is
   *         <code>null</code> or not one of the six values.
   */
  @Nullable
  public static EOnNoMatch getFromIDOrNull (@Nullable final String sID)
  {
    for (final EOnNoMatch e : values ())
      if (e.m_sID.equals (sID))
        return e;
    return null;
  }
}

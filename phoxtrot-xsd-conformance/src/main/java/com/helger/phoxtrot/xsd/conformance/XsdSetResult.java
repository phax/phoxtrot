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
package com.helger.phoxtrot.xsd.conformance;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSortedMap;

/**
 * The counts of one test set in one version run.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XsdSetResult
{
  private final String m_sName;
  private int m_nPass;
  private int m_nFail;
  private int m_nSkip;
  private int m_nUnsupported;
  private int m_nQueried;
  private int m_nQueriedCrash;
  private int m_nSchemaPass;
  private int m_nSchemaFail;
  private int m_nSchemaUnsupported;
  private final ICommonsSortedMap <String, Integer> m_aSkipReasons = new CommonsTreeMap <> ();
  private final ICommonsList <String> m_aFailNames = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aLines = new CommonsArrayList <> ();
  private String m_sLoadError;

  /**
   * Create the result of a test set.
   *
   * @param sName
   *        The test-set name.
   */
  public XsdSetResult (@NonNull final String sName)
  {
    m_sName = sName;
  }

  /**
   * Get the test-set name.
   *
   * @return The test-set name.
   */
  @NonNull
  public String getName ()
  {
    return m_sName;
  }

  /**
   * Count one passing case.
   */
  public void addPass ()
  {
    m_nPass++;
  }

  /**
   * Count one failing case.
   *
   * @param sCaseName
   *        The failing schema or instance test.
   * @param sLine
   *        Fail dump line, may be <code>null</code> if not collected.
   */
  public void addFail (@NonNull final String sCaseName, @Nullable final String sLine)
  {
    m_nFail++;
    m_aFailNames.add (sCaseName);
    if (sLine != null)
      m_aLines.add (sLine);
  }

  /**
   * Count one unsupported case.
   *
   * @param sLine
   *        Dump line, may be <code>null</code> if not collected.
   */
  public void addUnsupported (@Nullable final String sLine)
  {
    m_nUnsupported++;
    if (sLine != null)
      m_aLines.add (sLine);
  }

  /**
   * Count one queried (W3C-disputed) case.
   */
  public void addQueried ()
  {
    m_nQueried++;
  }

  /**
   * Count a queried (W3C-disputed) schema test whose compilation crashed: it
   * is counted as queried, outside the pass/fail denominator, and in
   * addition as a queried crash, so that the crash stays visible.
   *
   * @param sLine
   *        Dump line, may be <code>null</code> if not collected.
   */
  public void addQueriedCrash (@Nullable final String sLine)
  {
    m_nQueried++;
    m_nQueriedCrash++;
    if (sLine != null)
      m_aLines.add (sLine);
  }

  /**
   * Book the outcome of a schema test (in addition to the totals, which count
   * schema and instance tests alike).
   *
   * @param nPass
   *        Passes to add.
   * @param nFail
   *        Fails to add.
   * @param nUnsupported
   *        Unsupported cases to add.
   */
  public void addSchemaTestOutcome (final int nPass, final int nFail, final int nUnsupported)
  {
    m_nSchemaPass += nPass;
    m_nSchemaFail += nFail;
    m_nSchemaUnsupported += nUnsupported;
  }

  /**
   * Count one skipped case and its reason.
   *
   * @param sReason
   *        Why the case was skipped.
   */
  public void addSkip (@NonNull final String sReason)
  {
    m_nSkip++;
    final Integer aOld = m_aSkipReasons.get (sReason);
    m_aSkipReasons.put (sReason, Integer.valueOf (aOld == null ? 1 : aOld.intValue () + 1));
  }

  /**
   * Remember why the test set could not be read.
   *
   * @param sLoadError
   *        The error that prevented reading the test set.
   */
  public void setLoadError (@NonNull final String sLoadError)
  {
    m_sLoadError = sLoadError;
  }

  /**
   * Get the load error.
   *
   * @return The error that prevented reading the test set, or <code>null</code>.
   */
  @Nullable
  public String getLoadError ()
  {
    return m_sLoadError;
  }

  /**
   * Get the number of passing cases.
   *
   * @return The number of passes.
   */
  public int getPass ()
  {
    return m_nPass;
  }

  /**
   * Get the number of failing cases.
   *
   * @return The number of fails.
   */
  public int getFail ()
  {
    return m_nFail;
  }

  /**
   * Get the number of skipped cases.
   *
   * @return The number of skips.
   */
  public int getSkip ()
  {
    return m_nSkip;
  }

  /**
   * Get the number of unsupported cases.
   *
   * @return The number of unsupported cases.
   */
  public int getUnsupported ()
  {
    return m_nUnsupported;
  }

  /**
   * Get the number of queried cases.
   *
   * @return The number of queried (W3C-disputed) cases.
   */
  public int getQueried ()
  {
    return m_nQueried;
  }

  /**
   * Get the number of queried schema tests whose compilation crashed.
   *
   * @return The queried schema tests whose compilation crashed.
   */
  public int getQueriedCrash ()
  {
    return m_nQueriedCrash;
  }

  /**
   * Get the number of passing schema tests.
   *
   * @return The passing schema tests (part of {@link #getPass()}).
   */
  public int getSchemaPass ()
  {
    return m_nSchemaPass;
  }

  /**
   * Get the number of failing schema tests.
   *
   * @return The failing schema tests (part of {@link #getFail()}).
   */
  public int getSchemaFail ()
  {
    return m_nSchemaFail;
  }

  /**
   * Get the number of unsupported schema tests.
   *
   * @return The unsupported schema tests (part of {@link #getUnsupported()}).
   */
  public int getSchemaUnsupported ()
  {
    return m_nSchemaUnsupported;
  }

  /**
   * Get the skip reasons.
   *
   * @return A copy of the skip reasons with their counts.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsSortedMap <String, Integer> getSkipReasons ()
  {
    return m_aSkipReasons.getClone ();
  }

  /**
   * Get the names of the failing cases.
   *
   * @return A copy of the failing case names in the order they were added.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getFailNames ()
  {
    return m_aFailNames.getClone ();
  }

  /**
   * Get the collected dump lines.
   *
   * @return The collected fail (and unsupported) lines.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getLines ()
  {
    return m_aLines.getClone ();
  }
}

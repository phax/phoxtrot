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
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * One stylesheet module (the principal module or one reached through
 * xsl:include / xsl:import).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class XslModule
{
  private final String m_sUri;
  private final IXdmNode m_aSourceDocument;
  private XslElement m_aRoot;
  private boolean m_bSimplified;
  private String m_sPackageQualifier = "";

  /**
   * Constructor.
   *
   * @param sUri
   *        The URI the module was loaded from. May be <code>null</code>.
   * @param aSourceDocument
   *        The parsed source of the module. May not be <code>null</code>.
   */
  public XslModule (@Nullable final String sUri, @NonNull final IXdmNode aSourceDocument)
  {
    m_sUri = sUri;
    m_aSourceDocument = aSourceDocument;
  }

  /**
   * Get the URI of the module.
   *
   * @return The URI the module was loaded from, or <code>null</code>.
   */
  @Nullable
  public String getUri ()
  {
    return m_sUri;
  }

  /**
   * Get the parsed source of the module.
   *
   * @return The parsed source of the module: its document node, or the root
   *         of the tree an embedded module element belongs to. document('')
   *         returns it.
   */
  @NonNull
  public IXdmNode getSourceDocument ()
  {
    return m_aSourceDocument;
  }

  /**
   * Get the root element of the module.
   *
   * @return The module's outermost element after static processing, or
   *         <code>null</code> if none was set yet.
   */
  @Nullable
  public XslElement getRoot ()
  {
    return m_aRoot;
  }

  /**
   * Set the root element of the module.
   *
   * @param aRoot
   *        The module's outermost element after static processing. May not
   *        be <code>null</code>.
   */
  public void setRoot (@NonNull final XslElement aRoot)
  {
    m_aRoot = aRoot;
  }

  /**
   * Check whether this is a simplified stylesheet module.
   *
   * @return <code>true</code> for a simplified stylesheet module (a literal
   *         result element as root, equivalent to a template rule for "/").
   */
  public boolean isSimplified ()
  {
    return m_bSimplified;
  }

  /**
   * Set whether this is a simplified stylesheet module.
   *
   * @param bSimplified
   *        <code>true</code> for a simplified stylesheet module (a literal
   *        result element as root).
   */
  public void setSimplified (final boolean bSimplified)
  {
    m_bSimplified = bSimplified;
  }

  /**
   * Get the package qualifier of the module.
   *
   * @return The qualifier of the package the module belongs to: empty for the
   *         principal package, else distinct per used package. Names of
   *         package-local declarations (outputs, character maps) are
   *         qualified with it.
   */
  @NonNull
  public String getPackageQualifier ()
  {
    return m_sPackageQualifier;
  }

  /**
   * Set the package qualifier of the module.
   *
   * @param sPackageQualifier
   *        The qualifier of the package the module belongs to: empty for the
   *        principal package. May not be <code>null</code>.
   */
  public void setPackageQualifier (@NonNull final String sPackageQualifier)
  {
    m_sPackageQualifier = sPackageQualifier;
  }

  @Override
  public String toString ()
  {
    return "module " + m_sUri;
  }
}

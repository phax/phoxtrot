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

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xslt.compile.CompiledPattern;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.tree.XslElement;

/**
 * One compiled xsl:key declaration (XSLT 3.0 section 20.2.1): the match
 * pattern and the key value given either by the use expression or by the
 * sequence constructor. Several declarations may share a name; the compiler
 * requires the declarations of one name within a package to agree in collation
 * (XTSE1220) and composite (XTSE1222).
 *
 * @author Philip Helger
 */
@Immutable
public final class KeyDefinition
{
  private final XslElement m_aElement;
  private final XdmQName m_aName;
  private final CompiledPattern m_aMatch;
  private final XPathExpr m_aUse;
  private final SequenceConstructor m_aBody;
  private final boolean m_bComposite;
  private final String m_sCollation;
  private final boolean m_bXslt10;

  /**
   * Create a key declaration.
   *
   * @param aElement
   *        The xsl:key element. May not be <code>null</code>.
   * @param aName
   *        The key name. May not be <code>null</code>.
   * @param aMatch
   *        The match pattern. May not be <code>null</code>.
   * @param aUse
   *        The use expression, <code>null</code> if the body gives the value.
   * @param aBody
   *        The sequence constructor, <code>null</code> if use is given.
   * @param bComposite
   *        <code>true</code> for composite="yes".
   * @param sCollation
   *        The absolute collation URI. May not be <code>null</code>.
   * @param bXslt10
   *        <code>true</code> if the declaration is processed with XSLT 1.0
   *        behaviour.
   */
  public KeyDefinition (@NonNull final XslElement aElement,
                        @NonNull final XdmQName aName,
                        @NonNull final CompiledPattern aMatch,
                        @Nullable final XPathExpr aUse,
                        @Nullable final SequenceConstructor aBody,
                        final boolean bComposite,
                        @NonNull final String sCollation,
                        final boolean bXslt10)
  {
    m_aElement = aElement;
    m_aName = aName;
    m_aMatch = aMatch;
    m_aUse = aUse;
    m_aBody = aBody;
    m_bComposite = bComposite;
    m_sCollation = sCollation;
    m_bXslt10 = bXslt10;
  }

  /**
   * Get the declaring element.
   *
   * @return The xsl:key element, never <code>null</code>.
   */
  @NonNull
  public XslElement getElement ()
  {
    return m_aElement;
  }

  /**
   * Get the key name.
   *
   * @return The key name, never <code>null</code>.
   */
  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  /**
   * Get the match pattern.
   *
   * @return The match pattern, never <code>null</code>.
   */
  @NonNull
  public CompiledPattern getMatch ()
  {
    return m_aMatch;
  }

  /**
   * Get the use expression.
   *
   * @return The use expression, <code>null</code> if the body gives the
   *         value.
   */
  @Nullable
  public XPathExpr getUse ()
  {
    return m_aUse;
  }

  /**
   * Get the sequence constructor giving the key value.
   *
   * @return The sequence constructor, <code>null</code> if use is given.
   */
  @Nullable
  public SequenceConstructor getBody ()
  {
    return m_aBody;
  }

  /**
   * Check whether the key is composite.
   *
   * @return <code>true</code> for composite="yes": the key value is the whole
   *         sequence of atomic values, not each value on its own.
   */
  public boolean isComposite ()
  {
    return m_bComposite;
  }

  /**
   * Get the collation of the key.
   *
   * @return The absolute collation URI: the collation attribute resolved
   *         against the static base URI, or else the default collation of
   *         the declaration.
   */
  @NonNull
  public String getCollation ()
  {
    return m_sCollation;
  }

  /**
   * Check whether this declaration uses XSLT 1.0 behaviour.
   *
   * @return <code>true</code> if this declaration is processed with XSLT 1.0
   *         behaviour (XSLT 3.0 section 20.2.2): with composite="no" one such
   *         declaration puts the whole key into backwards compatible mode.
   */
  public boolean isXslt10 ()
  {
    return m_bXslt10;
  }
}

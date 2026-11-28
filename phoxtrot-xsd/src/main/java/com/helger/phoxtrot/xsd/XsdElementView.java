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
package com.helger.phoxtrot.xsd;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * An element declaration of the read-only component model (go-xslt
 * <code>ElementView</code>).
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdElementView implements IXsdTermView
{
  private final XsdSchemaView m_aView;
  private final XsdElementDecl m_aDecl;

  XsdElementView (@NonNull final XsdSchemaView aView, @NonNull final XsdElementDecl aDecl)
  {
    m_aView = aView;
    m_aDecl = aDecl;
  }

  /**
   * Get the name of the declared element.
   *
   * @return The expanded name.
   */
  @NonNull
  public XsdName getName ()
  {
    return m_aDecl.m_aName;
  }

  /**
   * Get the documentation of the declaration.
   *
   * @return The declaration's own xs:documentation (needs documentation kept
   *         at compile time). For a reference the reference's annotation is on
   *         the {@link XsdParticleView}.
   */
  @NonNull
  public ICommonsList <XsdDocumentation> getDocumentation ()
  {
    return m_aDecl.m_aDocs.getClone ();
  }

  /**
   * Get the declared type.
   *
   * @return The declared type; xs:anyType when the declaration names none.
   */
  @NonNull
  public IXsdTypeDefinitionView getType ()
  {
    final IXsdTypeDefinitionView ret = m_aView.typeDef (m_aDecl.m_aType);
    return ret != null ? ret : m_aView.anyTypeView ();
  }

  /**
   * Check whether the element declaration is nillable.
   *
   * @return <code>true</code> if the declaration is nillable.
   */
  public boolean isNillable ()
  {
    return m_aDecl.m_bNillable;
  }

  /**
   * Check whether the element declaration is abstract.
   *
   * @return <code>true</code> if the declaration is abstract.
   */
  public boolean isAbstract ()
  {
    return m_aDecl.m_bAbstract;
  }

  /**
   * Get the substitution group heads of this element.
   *
   * @return The heads this element is a substitution-group member of.
   */
  @NonNull
  public ICommonsList <XsdName> getSubstitutionGroups ()
  {
    return new CommonsArrayList <> (m_aDecl.m_aSubstGroups);
  }

  /**
   * Get the default value of the declared element.
   *
   * @return The default value, <code>null</code> if none.
   */
  @Nullable
  public String getDefault ()
  {
    return m_aDecl.m_bHasDefault ? m_aDecl.m_sDefault : null;
  }

  /**
   * Get the fixed value of the declared element.
   *
   * @return The fixed value, <code>null</code> if none.
   */
  @Nullable
  public String getFixed ()
  {
    return m_aDecl.m_bHasFixed ? m_aDecl.m_sFixed : null;
  }

  @Override
  public String toString ()
  {
    return "XsdElementView[" + m_aDecl.m_aName + "]";
  }
}

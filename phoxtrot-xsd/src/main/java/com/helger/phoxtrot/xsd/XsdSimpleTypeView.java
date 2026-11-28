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
import com.helger.phoxtrot.xpath.value.EAtomicType;

/**
 * A simple type definition of the read-only component model (go-xslt
 * <code>SimpleTypeView</code>).
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdSimpleTypeView implements IXsdTypeDefinitionView
{
  private static final int MAX_CHAIN = 64;
  private static final XsdName ANY_SIMPLE_TYPE = new XsdName (XsdConstants.NS_XS, "anySimpleType");

  private final XsdSchemaView m_aView;
  private final XsdSimpleType m_aType;

  XsdSimpleTypeView (@NonNull final XsdSchemaView aView, @NonNull final XsdSimpleType aType)
  {
    m_aView = aView;
    m_aType = aType;
  }

  public boolean isSimple ()
  {
    return true;
  }

  /**
   * Get the documentation of the type.
   *
   * @return The type's xs:documentation (needs documentation kept at compile
   *         time).
   */
  @NonNull
  public ICommonsList <XsdDocumentation> getDocumentation ()
  {
    return m_aType.m_aDocs.getClone ();
  }

  /**
   * Get the constraining facets in force.
   *
   * @return The constraining facets in force: each as declared by the nearest
   *         restriction step that sets it. Intrinsic bounds of built-ins are not
   *         reported; facets of a list type constrain the list.
   */
  @NonNull
  public XsdFacetsView getFacets ()
  {
    Integer aLength = null;
    Integer aMinLength = null;
    Integer aMaxLength = null;
    String sMinIncl = null;
    String sMaxIncl = null;
    String sMinExcl = null;
    String sMaxExcl = null;
    Integer aTotalDigits = null;
    Integer aFractionDigits = null;
    String sExplicitTZ = "";
    final ICommonsList <ICommonsList <String>> aPatterns = new CommonsArrayList <> ();
    XsdSimpleType s = m_aType;
    for (int n = 0; s != null && n < MAX_CHAIN; n++)
    {
      final XsdFacetSet f = s.m_aFacets;
      if (aLength == null && f.m_bHasLength)
        aLength = Integer.valueOf (f.m_nLength);
      if (aMinLength == null && f.m_bHasMinLength)
        aMinLength = Integer.valueOf (f.m_nMinLength);
      if (aMaxLength == null && f.m_bHasMaxLength)
        aMaxLength = Integer.valueOf (f.m_nMaxLength);
      if (sMinIncl == null && f.m_bHasMinIncl)
        sMinIncl = f.m_sMinIncl;
      if (sMaxIncl == null && f.m_bHasMaxIncl)
        sMaxIncl = f.m_sMaxIncl;
      if (sMinExcl == null && f.m_bHasMinExcl)
        sMinExcl = f.m_sMinExcl;
      if (sMaxExcl == null && f.m_bHasMaxExcl)
        sMaxExcl = f.m_sMaxExcl;
      if (aTotalDigits == null && f.m_bHasTotalDigits)
        aTotalDigits = Integer.valueOf (f.m_nTotalDigits);
      if (aFractionDigits == null && f.m_bHasFractionDigits)
        aFractionDigits = Integer.valueOf (f.m_nFractionDigits);
      if (sExplicitTZ.isEmpty ())
        sExplicitTZ = f.m_sExplicitTZ;
      // Every step's patterns must hold; base-most step last
      for (final ICommonsList <String> aGroup : f.m_aPatternSrc)
        aPatterns.add (aGroup.getClone ());
      s = s.m_aBase;
    }
    return new XsdFacetsView (aLength,
                              aMinLength,
                              aMaxLength,
                              sMinIncl,
                              sMaxIncl,
                              sMinExcl,
                              sMaxExcl,
                              aTotalDigits,
                              aFractionDigits,
                              m_aType.effectiveWhiteSpace (),
                              aPatterns,
                              sExplicitTZ);
  }

  /**
   * Check a value against this type: the whiteSpace facet is applied, then the
   * lexical space and all facets (patterns, enumerations, bounds, lengths,
   * digits, list items, union members) are checked, exactly as the validator
   * checks an element or attribute value. A value of a QName- or
   * NOTATION-based type is checked without namespace context.
   *
   * @param sValue
   *        The raw value. May not be <code>null</code>.
   * @return <code>null</code> if the value is valid, the first error
   *         otherwise.
   * @throws XsdUnsupportedException
   *         if the check uses a construct that is not implemented (no
   *         verdict)
   */
  @Nullable
  public XsdError getValueError (@NonNull final String sValue)
  {
    try
    {
      XsdSimpleTypeValidator.validate (m_aType, sValue, m_aView.getSchema ().getVersion ());
      return null;
    }
    catch (final XsdInvalidException ex)
    {
      return ex.getError ();
    }
  }

  /**
   * Check whether a value is valid against this type.
   *
   * @param sValue
   *        The raw value. May not be <code>null</code>.
   * @return <code>true</code> if the value is valid against this type, see
   *         {@link #getValueError(String)}.
   * @throws XsdUnsupportedException
   *         if the check uses a construct that is not implemented (no
   *         verdict)
   */
  public boolean isValidValue (@NonNull final String sValue)
  {
    return getValueError (sValue) == null;
  }

  /**
   * @param ePrim
   *        A built-in type.
   * @return The inclusive lower and upper bound XSD Datatypes defines for a
   *         built-in integer type (each <code>null</code> if unbounded), or
   *         <code>null</code> for a type without built-in bounds.
   */
  @Nullable
  private static String [] _getBuiltinBounds (@NonNull final EAtomicType ePrim)
  {
    return switch (ePrim)
    {
      case LONG -> new String [] { "-9223372036854775808", "9223372036854775807" };
      case INT -> new String [] { "-2147483648", "2147483647" };
      case SHORT -> new String [] { "-32768", "32767" };
      case BYTE -> new String [] { "-128", "127" };
      case NON_NEGATIVE_INTEGER -> new String [] { "0", null };
      case UNSIGNED_LONG -> new String [] { "0", "18446744073709551615" };
      case UNSIGNED_INT -> new String [] { "0", "4294967295" };
      case UNSIGNED_SHORT -> new String [] { "0", "65535" };
      case UNSIGNED_BYTE -> new String [] { "0", "255" };
      case POSITIVE_INTEGER -> new String [] { "1", null };
      case NON_POSITIVE_INTEGER -> new String [] { null, "0" };
      case NEGATIVE_INTEGER -> new String [] { null, "-1" };
      default -> null;
    };
  }

  /**
   * Like {@link #getFacets()}, but including the facets XSD Datatypes defines
   * for the built-in derived types: <code>fractionDigits</code> 0 for
   * <code>xs:integer</code> and all types derived from it, and the bounds of
   * <code>xs:long</code>, <code>xs:int</code>, <code>xs:short</code>,
   * <code>xs:byte</code>, the unsigned types and the
   * (non-)positive/(non-)negative integer types. On each side a declared bound
   * (inclusive or exclusive) replaces the built-in one, as a restriction can
   * only narrow it; a declared <code>fractionDigits</code> replaces the
   * built-in 0. The built-in pattern of <code>xs:integer</code> is not added.
   * For list and union types this is the same as {@link #getFacets()}.
   *
   * @return The effective constraining facets. Never <code>null</code>.
   */
  @NonNull
  public XsdFacetsView getEffectiveFacets ()
  {
    final XsdFacetsView aDeclared = getFacets ();
    if (m_aType.m_eVariety != EXsdVariety.ATOMIC)
      return aDeclared;
    final EAtomicType ePrim = m_aType.resolvePrim ();
    if (!ePrim.isDerivedFrom (EAtomicType.INTEGER))
      return aDeclared;

    String sMinIncl = aDeclared.minInclusive ();
    String sMaxIncl = aDeclared.maxInclusive ();
    final String [] aBounds = _getBuiltinBounds (ePrim);
    if (aBounds != null)
    {
      if (sMinIncl == null && aDeclared.minExclusive () == null)
        sMinIncl = aBounds[0];
      if (sMaxIncl == null && aDeclared.maxExclusive () == null)
        sMaxIncl = aBounds[1];
    }
    return new XsdFacetsView (aDeclared.length (),
                              aDeclared.minLength (),
                              aDeclared.maxLength (),
                              sMinIncl,
                              sMaxIncl,
                              aDeclared.minExclusive (),
                              aDeclared.maxExclusive (),
                              aDeclared.totalDigits (),
                              aDeclared.fractionDigits () != null ? aDeclared.fractionDigits () : Integer.valueOf (0),
                              aDeclared.whiteSpace (),
                              aDeclared.patterns (),
                              aDeclared.explicitTimezone ());
  }

  /**
   * @return The expanded name; for a reference to a built-in type that is the
   *         built-in's own; {@link XsdName#NONE} for an anonymous type.
   */
  @NonNull
  public XsdName getName ()
  {
    if (!m_aType.m_aName.isZero ())
      return m_aType.m_aName;
    if (m_aType.m_bBaseBuiltin && !m_aType.m_bDeclared && m_aType.m_eVariety == EXsdVariety.ATOMIC)
      return getBuiltinBase ();
    return XsdName.NONE;
  }

  /**
   * The built-in type this simple type ultimately rests on. For an atomic
   * type that is the <code>xs:</code> name of the atomic type recorded on the
   * first type of its base chain whose base is built-in (or that has no
   * base), with <code>xs:anyAtomicType</code> (also the answer for a chain
   * longer than 64 steps) reported as <code>xs:anySimpleType</code>. A list answers for its item type, or
   * <code>xs:anySimpleType</code> without one; a union always answers
   * <code>xs:anySimpleType</code>.
   *
   * @return The built-in type name, never <code>null</code>.
   */
  @NonNull
  public XsdName getBuiltinBase ()
  {
    switch (m_aType.m_eVariety)
    {
      case LIST:
        if (m_aType.m_aItem != null)
          return new XsdSimpleTypeView (m_aView, m_aType.m_aItem).getBuiltinBase ();
        return ANY_SIMPLE_TYPE;
      case UNION:
        return ANY_SIMPLE_TYPE;
      default:
        final EAtomicType ePrim = m_aType.resolvePrim ();
        if (ePrim == EAtomicType.ANY_ATOMIC_TYPE)
          return ANY_SIMPLE_TYPE;
        return new XsdName (XsdConstants.NS_XS, ePrim.getLocalName ());
    }
  }

  /**
   * Get the variety of the type.
   *
   * @return The variety.
   */
  @NonNull
  public EXsdVariety getVariety ()
  {
    return m_aType.m_eVariety;
  }

  /**
   * Get the type this one restricts.
   *
   * @return The type this one restricts; <code>null</code> when it is or
   *         derives directly from a built-in.
   */
  @Nullable
  public XsdSimpleTypeView getBase ()
  {
    return m_aView.simple (m_aType.m_aBase);
  }

  /**
   * Get the item type of a list type.
   *
   * @return A list's item type; <code>null</code> otherwise.
   */
  @Nullable
  public XsdSimpleTypeView getItemType ()
  {
    return m_aView.simple (m_aType.m_aItem);
  }

  /**
   * Get the member types of a union type.
   *
   * @return A union's member types; empty otherwise.
   */
  @NonNull
  public ICommonsList <XsdSimpleTypeView> getMemberTypes ()
  {
    final ICommonsList <XsdSimpleTypeView> ret = new CommonsArrayList <> ();
    for (final XsdSimpleType aMember : m_aType.m_aMembers)
      ret.add (m_aView.simple (aMember));
    return ret;
  }

  /**
   * Get the enumeration values in force.
   *
   * @return The enumeration values of the nearest restriction step that
   *         declares any, in document order; empty if none.
   */
  @NonNull
  public ICommonsList <String> getEnumeration ()
  {
    XsdSimpleType s = m_aType;
    for (int n = 0; s != null && n < MAX_CHAIN; n++)
    {
      if (s.m_aFacets.m_bHasEnum)
        return s.m_aFacets.m_aEnumeration.getClone ();
      s = s.m_aBase;
    }
    return new CommonsArrayList <> ();
  }

  @Override
  public String toString ()
  {
    return "XsdSimpleTypeView[" + getName () + "]";
  }
}

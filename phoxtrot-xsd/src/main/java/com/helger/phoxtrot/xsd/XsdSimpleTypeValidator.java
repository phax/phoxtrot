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
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xpath.ops.XsdLexical;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Validation of values against simple types (go-xslt <code>simpletype.go</code>:
 * <code>validate</code>, <code>validateIn</code>, <code>check</code>,
 * <code>checkIn</code>, <code>fixedEqual</code>, <code>atomicEqual</code>,
 * <code>firstMemberRaw</code>, the primitive lexical checks).
 * <p>
 * Union member lists may be cyclic (XSD 1.0 tolerates a pure-union cycle), so
 * every walk over members and item types keeps the unions it is inside of and
 * a depth counter: re-entering a union that is being checked is a member that
 * does not accept the value, and a nesting beyond {@link #MAX_DEPTH} is no
 * verdict. The base chain is walked iteratively.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdSimpleTypeValidator
{
  /** The deepest nesting of union members and list item types followed. */
  private static final int MAX_DEPTH = 256;
  /** The longest base chain followed. */
  private static final int MAX_CHAIN = 10_000;

  @PresentForCodeCoverage
  private static final XsdSimpleTypeValidator INSTANCE = new XsdSimpleTypeValidator ();

  private XsdSimpleTypeValidator ()
  {}

  /**
   * The range check of the built-in integer types for a value that fits a
   * <code>long</code>: xs:byte, xs:short, xs:int and the unsigned types up to
   * xs:unsignedInt by their bounds, xs:unsignedLong and
   * xs:nonNegativeInteger from 0, xs:positiveInteger from 1,
   * xs:nonPositiveInteger up to 0 and xs:negativeInteger up to -1. Every other
   * type (xs:integer, xs:long) accepts any <code>long</code>.
   *
   * @param n
   *        The value.
   * @param ePrim
   *        The built-in integer type.
   * @return <code>true</code> if the value is in the type's range.
   */
  private static boolean _integerInRange (final long n, @NonNull final EAtomicType ePrim)
  {
    switch (ePrim)
    {
      case BYTE:
        return n >= -128 && n <= 127;
      case SHORT:
        return n >= -32768 && n <= 32767;
      case INT:
        return n >= -2147483648L && n <= 2147483647L;
      case UNSIGNED_BYTE:
        return n >= 0 && n <= 255;
      case UNSIGNED_SHORT:
        return n >= 0 && n <= 65535;
      case UNSIGNED_INT:
        return n >= 0 && n <= 4294967295L;
      case UNSIGNED_LONG:
      case NON_NEGATIVE_INTEGER:
        return n >= 0;
      case POSITIVE_INTEGER:
        return n >= 1;
      case NON_POSITIVE_INTEGER:
        return n <= 0;
      case NEGATIVE_INTEGER:
        return n <= -1;
      default:
        // xs:integer, xs:long: unbounded within long
        return true;
    }
  }

  /**
   * @param sValue
   *        The value.
   * @param ePrim
   *        The built-in type it was checked against.
   * @return A new cvc-datatype-valid error for a value outside the lexical
   *         space or range of the type.
   */
  @NonNull
  private static XsdInvalidException _notValid (@NonNull final String sValue, @NonNull final EAtomicType ePrim)
  {
    return XsdCompiler.invalid ("cvc-datatype-valid",
                                XsdLexHelper.quote (sValue) + " is not a valid " + ePrim.getQualifiedName ());
  }

  /**
   * XSD 1.0 prohibits the year 0000 in the date family; XSD 1.1 reads it as
   * 1 BCE (proleptic calendar).
   *
   * @param ePrim
   *        The built-in primitive.
   * @param sValue
   *        The normalised value.
   * @return <code>true</code> if the primitive has a year and the year of the
   *         value (after an optional minus) starts with <code>0000</code>.
   */
  private static boolean _isYearZero (@NonNull final EAtomicType ePrim, @NonNull final String sValue)
  {
    switch (ePrim)
    {
      case DATE:
      case DATE_TIME:
      case G_YEAR:
      case G_YEAR_MONTH:
        return sValue.startsWith ("0000") || sValue.startsWith ("-0000");
      default:
        return false;
    }
  }

  /**
   * go-xslt <code>checkPrimitiveLexical</code>: validate against the built-in
   * primitive's lexical space. The string-family types are handled directly;
   * everything else defers to the cast of phoxtrot-xpath, with the integer and
   * decimal lexical spaces guarded explicitly.
   *
   * @param ePrim
   *        The built-in primitive.
   * @param sValue
   *        The normalised value.
   * @param eVersion
   *        The XSD version of the schema: XSD 1.0 has no <code>+INF</code> for
   *        xs:float and xs:double and no year 0000 in the date family.
   * @throws XsdInvalidException
   *         if the value is not in the primitive's lexical space
   */
  static void checkPrimitiveLexical (@NonNull final EAtomicType ePrim,
                                     @NonNull final String sValue,
                                     @NonNull final EXsdVersion eVersion)
  {
    switch (ePrim)
    {
      case STRING:
      case NORMALIZED_STRING:
      case TOKEN:
      case ANY_ATOMIC_TYPE:
      case UNTYPED_ATOMIC:
        // String family: every value is lexically valid
        return;
      case QNAME:
      case NOTATION:
        // Structural lexical check only, no namespace resolution, so an unbound
        // prefix is not an error here
        if (!XsdLexHelper.validQNameLexical (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case LANGUAGE:
        if (!XsdLexical.isLanguage (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case NAME:
        if (!XsdLexical.isName (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case NCNAME:
      case ID:
      case IDREF:
      case ENTITY:
        if (!XsdLexScan.isNCNameLex (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case NMTOKEN:
        if (!XsdLexical.isNmtoken (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case HEX_BINARY:
        if (!XsdLexScan.isHexBinaryLex (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case BASE64_BINARY:
        if (!XsdLexScan.isBase64BinaryLex (sValue))
          throw _notValid (sValue, ePrim);
        return;
      case DOUBLE:
      case FLOAT:
        // Overflow maps to the infinities and is a valid double/float, so the
        // lexical shape is validated directly rather than through the cast
        // "+INF" entered the float/double lexical space only in XSD 1.1
        if (!XsdLexical.isDoubleLexical (sValue) || (eVersion == EXsdVersion.V10 && "+INF".equals (sValue)))
          throw _notValid (sValue, ePrim);
        return;
      default:
        break;
    }
    // The integer/decimal lexical spaces need an explicit guard
    if (ePrim.isIntegerFamily ())
    {
      if (!XsdLexScan.isIntegerLex (sValue))
        throw _notValid (sValue, ePrim);
      // A value that fits a long is range-checked here without building the
      // cast's atomic; one that overflows takes the cast
      try
      {
        final long n = Long.parseLong (sValue);
        if (!_integerInRange (n, ePrim))
          throw _notValid (sValue, ePrim);
        return;
      }
      catch (final NumberFormatException ex)
      {
        // Falls through to the cast
      }
    }
    if (ePrim == EAtomicType.DECIMAL)
    {
      if (!XsdLexScan.isDecimalLex (sValue))
        throw _notValid (sValue, ePrim);
      return;
    }
    if (eVersion == EXsdVersion.V10 && _isYearZero (ePrim, sValue))
      throw XsdCompiler.invalid ("cvc-datatype-valid", "year 0000 is not a valid year in XSD 1.0");
    if (XsdValueCompare.castOrNull (sValue, ePrim) == null)
      throw _notValid (sValue, ePrim);
  }

  /**
   * @param ePrimA
   *        The built-in type of the first value.
   * @param ePrimB
   *        The built-in type of the second value.
   * @return <code>true</code> if both belong to the value space of one
   *         primitive datatype; values of different primitives are never equal
   *         (Datatypes 1.1 2.2.2).
   */
  private static boolean _isSamePrimitive (@NonNull final EAtomicType ePrimA, @NonNull final EAtomicType ePrimB)
  {
    final EAtomicType eA = XsdComponentChecks.xsdPrimitiveOf (ePrimA);
    final EAtomicType eB = XsdComponentChecks.xsdPrimitiveOf (ePrimB);
    // xsdPrimitiveOf keeps yearMonthDuration and dayTimeDuration, whose values
    // are durations
    return eA == eB || (eA.isDurationFamily () && eB.isDurationFamily ());
  }

  /**
   * Compare two atomic lexicals in the value spaces of their built-in types:
   * each is cast to its own and they are compared with
   * {@link XsdValueCompare#isEqual(XdmAtomic, XdmAtomic)}.
   *
   * @param ePrimA
   *        The built-in type of the first lexical.
   * @param sA
   *        The first lexical, normalised.
   * @param ePrimB
   *        The built-in type of the second lexical.
   * @param sB
   *        The second lexical, normalised.
   * @return <code>true</code> if both cast and are equal; <code>false</code>
   *         if either does not cast or the values are not equal.
   */
  private static boolean _atomicEqual (@NonNull final EAtomicType ePrimA,
                                       @NonNull final String sA,
                                       @NonNull final EAtomicType ePrimB,
                                       @NonNull final String sB)
  {
    final XdmAtomic aA = XsdValueCompare.castOrNull (sA, ePrimA);
    final XdmAtomic aB = XsdValueCompare.castOrNull (sB, ePrimB);
    if (aA == null || aB == null)
      return false;
    return XsdValueCompare.isEqual (aA, aB);
  }

  /**
   * @return <code>true</code> if the list value items equal one of the
   *         enumerated lists item by item in the value space of the item type
   *         (without an item type the lexical comparison of the caller stays).
   */
  private static boolean _listEnumMatch (@NonNull final XsdFacetSet aFacets,
                                         @NonNull final ICommonsList <String> aItems,
                                         @Nullable final XsdSimpleType aItem,
                                         final int nDepth,
                                         @NonNull final EXsdVersion eVersion)
  {
    if (aItem == null)
      return false;
    for (final String sEnum : aFacets.m_aEnumeration)
    {
      final ICommonsList <String> aEnumItems = XsdLexHelper.xmlFields (sEnum);
      if (aEnumItems.size () != aItems.size ())
        continue;
      boolean bEqual = true;
      for (int i = 0; i < aEnumItems.size (); i++)
      {
        final String sE = aEnumItems.get (i);
        final String sI = aItems.get (i);
        if (!sE.equals (sI) && !_valuesEqualRecursive (aItem, sI, aItem, sE, nDepth + 1, eVersion))
        {
          bEqual = false;
          break;
        }
      }
      if (bEqual)
        return true;
    }
    return false;
  }

  /**
   * The XSD Part 2 3.2.18 value of an xs:QName (and of an xs:NOTATION) is an
   * expanded name, so a prefixed lexical is valid only where that prefix is
   * bound; an unprefixed name is always fine.
   */
  private static void _checkQNamePrefixBound (@NonNull final String sValue,
                                              @NonNull final EAtomicType ePrim,
                                              @NonNull final IXdmNode aAt)
  {
    final int i = sValue.indexOf (':');
    if (i <= 0)
      return;
    final String sPrefix = sValue.substring (0, i);
    if (XsdNodes.lookupPrefix (aAt, sPrefix) == null)
      throw XsdCompiler.invalid ("cvc-datatype-valid",
                                 "the prefix " +
                                                       XsdLexHelper.quote (sPrefix) +
                                                       " of " +
                                                       ePrim.getQualifiedName () +
                                                       " value " +
                                                       XsdLexHelper.quote (sValue) +
                                                       " is not bound to a namespace");
  }

  /**
   * @return A new error for a nesting of union members and list item types
   *         beyond {@link #MAX_DEPTH}, or a base chain beyond
   *         {@link #MAX_CHAIN}: no verdict.
   */
  @NonNull
  private static XsdUnsupportedException _tooDeep ()
  {
    return new XsdUnsupportedException ("Simple type nesting (union members, list item types) deeper than " +
                                        MAX_DEPTH +
                                        " levels, possibly a cyclic union");
  }

  /**
   * Check a normalised value against a list type. The value is split at XML
   * whitespace. For every step of the base chain the length facets apply to
   * the item count, and pattern and enumeration to the whole lexical; an
   * enumeration miss is forgiven when the items equal an enumerated list item
   * by item in the value space of an atomic item type. Then every item is
   * checked against the item type. Finally, if any step carries assertions,
   * they are all evaluated with <code>$value</code> bound to the sequence of
   * typed items.
   *
   * @param aType
   *        The list type.
   * @param sValue
   *        The whitespace-normalised value.
   * @param aAt
   *        The instance node for QName/NOTATION prefixes; may be
   *        <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path.
   * @param nDepth
   *        The current nesting depth.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if the value is not valid
   * @throws XsdUnsupportedException
   *         if the nesting or base chain is too deep
   */
  private static void _checkListRecursive (@NonNull final XsdSimpleType aType,
                                           @NonNull final String sValue,
                                           @Nullable final IXdmNode aAt,
                                           @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                           final int nDepth,
                                           @NonNull final EXsdVersion eVersion)
  {
    final ICommonsList <String> aItems = XsdLexHelper.xmlFields (sValue);
    // Every restriction step's facets apply (a derived list must still honour
    // the base's, e.g. the built-in list types' minLength 1); item checks run
    // once against the resolved item type
    XsdSimpleType s = aType;
    boolean bHasAsserts = false;
    for (int n = 0; s != null; n++)
    {
      if (n > MAX_CHAIN)
        throw _tooDeep ();
      XsdFacetValidator.checkListLength (s.m_aFacets, aItems.size ());
      try
      {
        XsdFacetValidator.checkPatternEnumIn (s.m_aFacets, sValue, EAtomicType.STRING, aAt);
      }
      catch (final XsdInvalidException ex)
      {
        // The patterns passed (they are checked first); the enumeration
        // compares values, so "01 2" is the enumerated "1 2" of an xs:int list
        if (!"cvc-enumeration-valid".equals (ex.getError ().code ()) ||
            !_listEnumMatch (s.m_aFacets, aItems, aType.m_aItem, nDepth, eVersion))
          throw ex;
      }
      if (s.m_aAssertions.isNotEmpty ())
        bHasAsserts = true;
      s = s.m_aBase;
    }
    if (aType.m_aItem != null)
      for (final String sItem : aItems)
        _checkInRecursive (aType.m_aItem, sItem, aAt, aActive, nDepth + 1, eVersion);
    if (bHasAsserts)
    {
      // $value is the sequence of typed items
      final XdmSequence aValue = XsdAssertions.typedListValue (aItems,
                                                               aType.m_aItem == null ? null
                                                                                     : aType.m_aItem.resolvePrim ());
      int n = 0;
      for (XsdSimpleType aStep = aType; aStep != null && n <= MAX_CHAIN; aStep = aStep.m_aBase, n++)
        XsdAssertions.evalSimpleAssertions (aStep.m_aAssertions, aValue);
    }
  }

  /**
   * Check a value against a union type. The members are tried in order, each
   * with its own whiteSpace processing applied to the value; the first one
   * that accepts it is the accepting member. Then, for the union and every
   * union in its base chain (a restriction of a union restricts all facets of
   * its base), the pattern facets must hold for the value as given or, failing
   * that, for the value as normalised by the accepting member, the value in
   * the accepting member must equal the value of an enumeration literal (see
   * {@link #_unionEnumMatch}), and the assertions are evaluated with
   * <code>$value</code> typed by the accepting member.
   *
   * @param aType
   *        The union type. It is in <code>aActive</code> while the members are
   *        tried.
   * @param sValue
   *        The value.
   * @param aAt
   *        The instance node for QName/NOTATION prefixes; may be
   *        <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path. Modified and
   *        restored.
   * @param nDepth
   *        The current nesting depth.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if no member accepts the value, or a facet or assertion of the
   *         union or of a union in its base chain fails
   * @throws XsdUnsupportedException
   *         if the nesting or base chain is too deep
   */
  private static void _checkUnionRecursive (@NonNull final XsdSimpleType aType,
                                            @NonNull final String sValue,
                                            @Nullable final IXdmNode aAt,
                                            @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                            final int nDepth,
                                            @NonNull final EXsdVersion eVersion)
  {
    XsdSimpleType aAccepting = null;
    aActive.add (aType);
    try
    {
      for (final XsdSimpleType aMember : aType.m_aMembers)
      {
        // Each member applies its own whiteSpace processing (a union has none
        // of its own), so validate, not check, the value against the member
        try
        {
          _validateInRecursive (aMember, sValue, aAt, aActive, nDepth + 1, eVersion);
          aAccepting = aMember;
          break;
        }
        catch (final XsdInvalidException ex)
        {
          // Next member
        }
      }
    }
    finally
    {
      aActive.remove (aType);
    }
    if (aAccepting == null)
      throw XsdCompiler.invalid ("cvc-datatype-valid", "value " + XsdLexHelper.quote (sValue) + " is not valid for any union member");
    // Pattern facets on the union restriction constrain the whole lexical in
    // addition to member validity. They also hold if they match the value as
    // normalised by the accepting member's whiteSpace. The enumeration compares
    // values: the value of the accepting member with the value of each
    // enumeration literal in its own first accepting member. Every union step
    // of the base chain contributes its facets and assertions.
    final String sMemberValue = XsdWhiteSpace.apply (aAccepting.effectiveWhiteSpace (), sValue);
    XsdSimpleType aStep = aType;
    for (int n = 0; aStep != null && aStep.m_eVariety == EXsdVariety.UNION; n++)
    {
      if (n > MAX_CHAIN)
        throw _tooDeep ();
      try
      {
        XsdFacetValidator.checkPatternIn (aStep.m_aFacets, sValue);
      }
      catch (final XsdInvalidException ex)
      {
        if (sMemberValue.equals (sValue))
          throw ex;
        try
        {
          XsdFacetValidator.checkPatternIn (aStep.m_aFacets, sMemberValue);
        }
        catch (final XsdInvalidException ex2)
        {
          throw ex;
        }
      }
      if (aStep.m_aFacets.m_bHasEnum && !_unionEnumMatch (aStep, sValue, aAccepting, nDepth, eVersion))
        throw XsdFacetValidator.enumerationError (sValue);
      if (aStep.m_aAssertions.isNotEmpty ())
      {
        // $value carries the accepting member's type
        XsdAssertions.evalSimpleAssertions (aStep.m_aAssertions,
                                            XsdAssertions.typedAtomicValue (sMemberValue, aAccepting.resolvePrim ()));
      }
      aStep = aStep.m_aBase;
    }
  }

  /**
   * Check a normalised value against an atomic type and its base chain. The
   * chain is collected up to the first base that is not a plain atomic type
   * (a list, a union, the absent type or xs:error); such a base is checked
   * first as a whole. Then, from the deepest base to the type itself, each
   * step applies its facets and its assertions (with <code>$value</code> typed
   * by the step's primitive). The lexical space of the built-in primitive -
   * and, for xs:QName and xs:NOTATION with a node available, that the prefix
   * is bound - is checked at the deepest step only when the chain ends at a
   * built-in.
   *
   * @param aType
   *        The atomic type.
   * @param sValue
   *        The whitespace-normalised value.
   * @param aAt
   *        The instance node for QName/NOTATION prefixes; may be
   *        <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path.
   * @param nDepth
   *        The current nesting depth.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if the value is not valid
   * @throws XsdUnsupportedException
   *         if the nesting or base chain is too deep
   */
  private static void _checkAtomicRecursive (@NonNull final XsdSimpleType aType,
                                             @NonNull final String sValue,
                                             @Nullable final IXdmNode aAt,
                                             @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                             final int nDepth,
                                             @NonNull final EXsdVersion eVersion)
  {
    // The base chain, walked iteratively: the deepest base is checked first
    final ICommonsList <XsdSimpleType> aChain = new CommonsArrayList <> ();
    XsdSimpleType aDelegate = null;
    XsdSimpleType s = aType;
    while (true)
    {
      if (aChain.size () > MAX_CHAIN)
        throw _tooDeep ();
      aChain.add (s);
      final XsdSimpleType aBase = s.m_aBase;
      if (aBase == null)
        break;
      if (aBase == XsdSimpleType.ABSENT || aBase == XsdSimpleType.ERROR || aBase.m_eVariety != EXsdVariety.ATOMIC)
      {
        aDelegate = aBase;
        break;
      }
      s = aBase;
    }
    if (aDelegate != null)
      _checkInRecursive (aDelegate, sValue, aAt, aActive, nDepth + 1, eVersion);
    for (int i = aChain.size () - 1; i >= 0; i--)
    {
      final XsdSimpleType aLevel = aChain.get (i);
      final EAtomicType ePrim = aLevel.resolvePrim ();
      if (i == aChain.size () - 1 && aDelegate == null)
      {
        checkPrimitiveLexical (ePrim, sValue, eVersion);
        // Skipped when no node is available (a value checked outside any
        // instance, e.g. a schema's own default): only the shape is checked
        if (aAt != null && (ePrim == EAtomicType.QNAME || ePrim == EAtomicType.NOTATION))
          _checkQNamePrefixBound (sValue, ePrim, aAt);
      }
      XsdFacetValidator.checkIn (aLevel.m_aFacets, sValue, ePrim, aAt);
      if (aLevel.m_aAssertions.isNotEmpty ())
        XsdAssertions.evalSimpleAssertions (aLevel.m_aAssertions, XsdAssertions.typedAtomicValue (sValue, ePrim));
    }
  }

  /**
   * Check an already normalised value against a simple type, dispatching on
   * its variety. The absent type (a type reference that never resolved) and
   * xs:error refuse every value with cvc-type. A union that is already being
   * checked on the current path refuses the value, which bounds a cyclic
   * member list.
   *
   * @param aType
   *        The simple type.
   * @param sValue
   *        The whitespace-normalised value.
   * @param aAt
   *        The instance node for QName/NOTATION prefixes; may be
   *        <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path.
   * @param nDepth
   *        The current nesting depth.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if the value is not valid
   * @throws XsdUnsupportedException
   *         beyond {@link #MAX_DEPTH}
   */
  private static void _checkInRecursive (@NonNull final XsdSimpleType aType,
                                         @NonNull final String sValue,
                                         @Nullable final IXdmNode aAt,
                                         @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                         final int nDepth,
                                         @NonNull final EXsdVersion eVersion)
  {
    if (nDepth > MAX_DEPTH)
      throw _tooDeep ();
    if (aType == XsdSimpleType.ABSENT)
    {
      // Using a declaration whose type reference never resolved is the error
      // the Missing family defers to validation time
      throw XsdCompiler.invalid ("cvc-type",
                                 "the declaration's type " + aType.m_aName.local ().substring (1) + " is absent from the schema");
    }
    if (aType == XsdSimpleType.ERROR)
      throw XsdCompiler.invalid ("cvc-type", "xs:error has an empty value space");
    switch (aType.m_eVariety)
    {
      case LIST:
        _checkListRecursive (aType, sValue, aAt, aActive, nDepth, eVersion);
        break;
      case UNION:
        // A union that is being checked already is a member that accepts no
        // value, which is what bounds a cyclic member list
        if (aActive.contains (aType))
          throw XsdCompiler.invalid ("cvc-datatype-valid", "value " + XsdLexHelper.quote (sValue) + " reaches a cyclic union");
        _checkUnionRecursive (aType, sValue, aAt, aActive, nDepth, eVersion);
        break;
      default:
        _checkAtomicRecursive (aType, sValue, aAt, aActive, nDepth, eVersion);
        break;
    }
  }

  /**
   * Apply the type's effective whiteSpace facet to a raw value and check the
   * result with {@link #_checkInRecursive}.
   *
   * @param aType
   *        The simple type.
   * @param sRaw
   *        The raw value.
   * @param aAt
   *        The instance node; may be <code>null</code>.
   * @param aActive
   *        The unions being checked on the current path.
   * @param nDepth
   *        The current nesting depth.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  private static void _validateInRecursive (@NonNull final XsdSimpleType aType,
                                            @NonNull final String sRaw,
                                            @Nullable final IXdmNode aAt,
                                            @NonNull final ICommonsSet <XsdSimpleType> aActive,
                                            final int nDepth,
                                            @NonNull final EXsdVersion eVersion)
  {
    _checkInRecursive (aType, XsdWhiteSpace.apply (aType.effectiveWhiteSpace (), sRaw), aAt, aActive, nDepth, eVersion);
  }

  /**
   * The first member of a union that accepts a raw value with its own
   * whiteSpace processing, checked without a namespace context (so a prefixed
   * QName is only checked for its shape). The union itself counts as active,
   * so a cyclic member refuses the value.
   *
   * @param aType
   *        The union type.
   * @param sRaw
   *        The raw value.
   * @param eVersion
   *        The XSD version of the schema.
   * @return The accepting member, or <code>null</code> if none accepts it.
   */
  @Nullable
  private static XsdSimpleType _firstMember (@NonNull final XsdSimpleType aType,
                                             @NonNull final String sRaw,
                                             @NonNull final EXsdVersion eVersion)
  {
    final ICommonsSet <XsdSimpleType> aActive = new CommonsHashSet <> ();
    aActive.add (aType);
    for (final XsdSimpleType aMember : aType.m_aMembers)
      try
      {
        _validateInRecursive (aMember, sRaw, null, aActive, 1, eVersion);
        return aMember;
      }
      catch (final XsdInvalidException ex)
      {
        // Next member
      }
    return null;
  }

  /**
   * Value equality of two raw lexicals, each in the value space of its own
   * simple type (Datatypes 1.1 2.2.2, as the enumeration facet and value
   * constraints use it). A union side takes the value of its first member
   * that accepts the lexical (with that member's whiteSpace processing), so a
   * union of xs:int and xs:boolean reads <code>1</code> as an integer. Two
   * lists are equal if they have the same number of items and the items are
   * pairwise equal in the item types (lexically without an item type); a list
   * of one item equals an atomic value equal to that item. Two atomic values
   * are equal if their built-in types share a primitive and the lexicals,
   * normalised with each type's whiteSpace, are equal strings or cast to equal
   * values ({@link XsdValueCompare#isEqual(XdmAtomic, XdmAtomic)}).
   *
   * @param aTypeA
   *        The type of the first lexical.
   * @param sA
   *        The first raw lexical.
   * @param aTypeB
   *        The type of the second lexical.
   * @param sB
   *        The second raw lexical.
   * @param nDepth
   *        The current nesting depth.
   * @param eVersion
   *        The XSD version of the schema.
   * @return <code>true</code> if equal; <code>false</code> if not, or if a
   *         union side has no member that accepts its lexical.
   * @throws XsdUnsupportedException
   *         beyond {@link #MAX_DEPTH}
   */
  private static boolean _valuesEqualRecursive (@NonNull final XsdSimpleType aTypeA,
                                                @NonNull final String sA,
                                                @NonNull final XsdSimpleType aTypeB,
                                                @NonNull final String sB,
                                                final int nDepth,
                                                @NonNull final EXsdVersion eVersion)
  {
    if (nDepth > MAX_DEPTH)
      throw _tooDeep ();
    if (aTypeA.m_eVariety == EXsdVariety.UNION)
    {
      final XsdSimpleType aMember = _firstMember (aTypeA, sA, eVersion);
      return aMember != null && _valuesEqualRecursive (aMember, sA, aTypeB, sB, nDepth + 1, eVersion);
    }
    if (aTypeB.m_eVariety == EXsdVariety.UNION)
    {
      final XsdSimpleType aMember = _firstMember (aTypeB, sB, eVersion);
      return aMember != null && _valuesEqualRecursive (aTypeA, sA, aMember, sB, nDepth + 1, eVersion);
    }
    final boolean bListA = aTypeA.m_eVariety == EXsdVariety.LIST;
    final boolean bListB = aTypeB.m_eVariety == EXsdVariety.LIST;
    if (bListA && bListB)
    {
      final ICommonsList <String> aFA = XsdLexHelper.xmlFields (sA);
      final ICommonsList <String> aFB = XsdLexHelper.xmlFields (sB);
      if (aFA.size () != aFB.size ())
        return false;
      for (int i = 0; i < aFA.size (); i++)
      {
        // Without an item type on both sides the items stay strings
        if (aTypeA.m_aItem == null || aTypeB.m_aItem == null)
        {
          if (!aFA.get (i).equals (aFB.get (i)))
            return false;
          continue;
        }
        if (!_valuesEqualRecursive (aTypeA.m_aItem, aFA.get (i), aTypeB.m_aItem, aFB.get (i), nDepth + 1, eVersion))
          return false;
      }
      return true;
    }
    if (bListA || bListB)
    {
      // A list of length one equals an atomic value equal to its item
      final XsdSimpleType aList = bListA ? aTypeA : aTypeB;
      final ICommonsList <String> aItems = XsdLexHelper.xmlFields (bListA ? sA : sB);
      if (aItems.size () != 1 || aList.m_aItem == null)
        return false;
      return bListA ? _valuesEqualRecursive (aList.m_aItem, aItems.get (0), aTypeB, sB, nDepth + 1, eVersion)
                    : _valuesEqualRecursive (aTypeA, sA, aList.m_aItem, aItems.get (0), nDepth + 1, eVersion);
    }
    final EAtomicType ePrimA = aTypeA.resolvePrim ();
    final EAtomicType ePrimB = aTypeB.resolvePrim ();
    if (!_isSamePrimitive (ePrimA, ePrimB))
      return false;
    final String sNA = XsdWhiteSpace.apply (aTypeA.effectiveWhiteSpace (), sA);
    final String sNB = XsdWhiteSpace.apply (aTypeB.effectiveWhiteSpace (), sB);
    // Fast path: equal normalised lexicals of one primitive are one value
    if (sNA.equals (sNB))
      return true;
    return _atomicEqual (ePrimA, sNA, ePrimB, sNB);
  }

  /**
   * The enumeration facet of one union step (Datatypes 1.1 4.3.5, 4.1.2.3):
   * the value, read in the member that accepted it, must equal the value of
   * an enumeration literal, read in the first member of the step that accepts
   * the literal. Values of different primitives are never equal, so with
   * <code>memberTypes="xs:int xs:boolean"</code> the literal <code>1</code>
   * admits <code>01</code> but not <code>true</code>.
   *
   * @param aStep
   *        The union step that carries the enumeration.
   * @param sValue
   *        The value as given to the union.
   * @param aAccepting
   *        The member that accepted the value.
   * @param nDepth
   *        The current nesting depth.
   * @param eVersion
   *        The XSD version of the schema.
   * @return <code>true</code> if the value is in the enumeration.
   */
  private static boolean _unionEnumMatch (@NonNull final XsdSimpleType aStep,
                                          @NonNull final String sValue,
                                          @NonNull final XsdSimpleType aAccepting,
                                          final int nDepth,
                                          @NonNull final EXsdVersion eVersion)
  {
    // Fast path: a literal spelled like the value has the same first member
    // and the same value, without validating the literals against the members
    for (final String sEnum : aStep.m_aFacets.m_aEnumeration)
      if (sEnum.equals (sValue))
        return true;
    for (final String sEnum : aStep.m_aFacets.m_aEnumeration)
    {
      final XsdSimpleType aEnumMember = _firstMember (aStep, sEnum, eVersion);
      if (aEnumMember != null && _valuesEqualRecursive (aAccepting, sValue, aEnumMember, sEnum, nDepth + 1, eVersion))
        return true;
    }
    return false;
  }

  /**
   * go-xslt <code>SimpleType.validate</code>: apply the type's whiteSpace and
   * check the value (lexical space, facets, list items, union members). A value
   * of a QName/NOTATION-based type is checked without namespace context.
   *
   * @param aType
   *        The simple type. {@link XsdSimpleType#ABSENT} refuses every value.
   * @param sRaw
   *        The raw (not yet normalised) value.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void validate (@NonNull final XsdSimpleType aType,
                        @NonNull final String sRaw,
                        @NonNull final EXsdVersion eVersion)
  {
    validateIn (aType, sRaw, null, eVersion);
  }

  /**
   * go-xslt <code>SimpleType.validateIn</code>: {@link #validate} with the
   * namespace context of an instance node (for QName/NOTATION prefixes).
   *
   * @param aType
   *        The simple type.
   * @param sRaw
   *        The raw value.
   * @param aAt
   *        The element or attribute node the value sits on; <code>null</code>
   *        for none.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void validateIn (@NonNull final XsdSimpleType aType,
                          @NonNull final String sRaw,
                          @Nullable final IXdmNode aAt,
                          @NonNull final EXsdVersion eVersion)
  {
    _validateInRecursive (aType, sRaw, aAt, new CommonsHashSet <> (), 0, eVersion);
  }

  /**
   * go-xslt <code>SimpleType.check</code>: check an already whitespace
   * normalised value.
   *
   * @param aType
   *        The simple type.
   * @param sValue
   *        The normalised value.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void check (@NonNull final XsdSimpleType aType,
                     @NonNull final String sValue,
                     @NonNull final EXsdVersion eVersion)
  {
    _checkInRecursive (aType, sValue, null, new CommonsHashSet <> (), 0, eVersion);
  }

  /**
   * go-xslt <code>SimpleType.checkIn</code>: {@link #check} with the instance
   * node of a QName/NOTATION value.
   *
   * @param aType
   *        The simple type.
   * @param sValue
   *        The normalised value.
   * @param aAt
   *        The instance node; may be <code>null</code>.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void checkIn (@NonNull final XsdSimpleType aType,
                       @NonNull final String sValue,
                       @Nullable final IXdmNode aAt,
                       @NonNull final EXsdVersion eVersion)
  {
    _checkInRecursive (aType, sValue, aAt, new CommonsHashSet <> (), 0, eVersion);
  }

  /**
   * go-xslt <code>SimpleType.fixedEqual</code>: whether instance content equals
   * a fixed value constraint in the type's value space. Both lexicals are
   * normalised with the governing type's whiteSpace (for a union, the
   * whiteSpace of the first member accepting each side), then compared as
   * values with {@link #_valuesEqualRecursive}: for a union the two sides may
   * land in different members and are equal if their values are.
   *
   * @param aType
   *        The simple type.
   * @param sContent
   *        The instance value.
   * @param sFixed
   *        The fixed value.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @return <code>true</code> if equal.
   */
  static boolean fixedEqual (@NonNull final XsdSimpleType aType,
                             @NonNull final String sContent,
                             @NonNull final String sFixed,
                             @NonNull final EXsdVersion eVersion)
  {
    return _valuesEqualRecursive (aType, sContent, aType, sFixed, 0, eVersion);
  }

  /**
   * go-xslt <code>SimpleType.firstMemberRaw</code>: the first union member whose
   * whiteSpace-normalised reading of the raw value validates (XSD 1.1
   * assertions and CTA need it).
   *
   * @param aType
   *        The union type.
   * @param sRaw
   *        The raw value.
   * @param eVersion
   *        The XSD version of the schema, for the lexical rules that differ.
   * @return The member, or <code>null</code>.
   */
  @Nullable
  static XsdSimpleType firstMemberRaw (@NonNull final XsdSimpleType aType,
                                       @NonNull final String sRaw,
                                       @NonNull final EXsdVersion eVersion)
  {
    return _firstMember (aType, sRaw, eVersion);
  }
}

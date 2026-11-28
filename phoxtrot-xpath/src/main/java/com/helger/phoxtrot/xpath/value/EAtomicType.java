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
package com.helger.phoxtrot.xpath.value;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The built-in atomic types of XML Schema that XPath 3.1 knows, with their
 * derivation hierarchy. {@link #NUMERIC} and {@link #ERROR} are pseudo types:
 * the union <code>xs:numeric</code> and the empty type <code>xs:error</code>.
 *
 * @author Philip Helger
 */
public enum EAtomicType
{
  /** The type <code>xs:anyAtomicType</code>, the root of the atomic type hierarchy. */
  ANY_ATOMIC_TYPE ("anyAtomicType", null),
  /** The type <code>xs:untypedAtomic</code>, derived from <code>xs:anyAtomicType</code>. */
  UNTYPED_ATOMIC ("untypedAtomic", ANY_ATOMIC_TYPE),
  /** The type <code>xs:string</code>, derived from <code>xs:anyAtomicType</code>. */
  STRING ("string", ANY_ATOMIC_TYPE),
  /** The type <code>xs:normalizedString</code>, derived from <code>xs:string</code>. */
  NORMALIZED_STRING ("normalizedString", STRING),
  /** The type <code>xs:token</code>, derived from <code>xs:normalizedString</code>. */
  TOKEN ("token", NORMALIZED_STRING),
  /** The type <code>xs:language</code>, derived from <code>xs:token</code>. */
  LANGUAGE ("language", TOKEN),
  /** The type <code>xs:NMTOKEN</code>, derived from <code>xs:token</code>. */
  NMTOKEN ("NMTOKEN", TOKEN),
  /** The type <code>xs:Name</code>, derived from <code>xs:token</code>. */
  NAME ("Name", TOKEN),
  /** The type <code>xs:NCName</code>, derived from <code>xs:Name</code>. */
  NCNAME ("NCName", NAME),
  /** The type <code>xs:ID</code>, derived from <code>xs:NCName</code>. */
  ID ("ID", NCNAME),
  /** The type <code>xs:IDREF</code>, derived from <code>xs:NCName</code>. */
  IDREF ("IDREF", NCNAME),
  /** The type <code>xs:ENTITY</code>, derived from <code>xs:NCName</code>. */
  ENTITY ("ENTITY", NCNAME),
  /** The type <code>xs:boolean</code>, derived from <code>xs:anyAtomicType</code>. */
  BOOLEAN ("boolean", ANY_ATOMIC_TYPE),
  /** The type <code>xs:decimal</code>, derived from <code>xs:anyAtomicType</code>. */
  DECIMAL ("decimal", ANY_ATOMIC_TYPE),
  /** The type <code>xs:integer</code>, derived from <code>xs:decimal</code>. */
  INTEGER ("integer", DECIMAL),
  /** The type <code>xs:nonPositiveInteger</code>, derived from <code>xs:integer</code>. */
  NON_POSITIVE_INTEGER ("nonPositiveInteger", INTEGER),
  /** The type <code>xs:negativeInteger</code>, derived from <code>xs:nonPositiveInteger</code>. */
  NEGATIVE_INTEGER ("negativeInteger", NON_POSITIVE_INTEGER),
  /** The type <code>xs:long</code>, derived from <code>xs:integer</code>. */
  LONG ("long", INTEGER),
  /** The type <code>xs:int</code>, derived from <code>xs:long</code>. */
  INT ("int", LONG),
  /** The type <code>xs:short</code>, derived from <code>xs:int</code>. */
  SHORT ("short", INT),
  /** The type <code>xs:byte</code>, derived from <code>xs:short</code>. */
  BYTE ("byte", SHORT),
  /** The type <code>xs:nonNegativeInteger</code>, derived from <code>xs:integer</code>. */
  NON_NEGATIVE_INTEGER ("nonNegativeInteger", INTEGER),
  /** The type <code>xs:unsignedLong</code>, derived from <code>xs:nonNegativeInteger</code>. */
  UNSIGNED_LONG ("unsignedLong", NON_NEGATIVE_INTEGER),
  /** The type <code>xs:unsignedInt</code>, derived from <code>xs:unsignedLong</code>. */
  UNSIGNED_INT ("unsignedInt", UNSIGNED_LONG),
  /** The type <code>xs:unsignedShort</code>, derived from <code>xs:unsignedInt</code>. */
  UNSIGNED_SHORT ("unsignedShort", UNSIGNED_INT),
  /** The type <code>xs:unsignedByte</code>, derived from <code>xs:unsignedShort</code>. */
  UNSIGNED_BYTE ("unsignedByte", UNSIGNED_SHORT),
  /** The type <code>xs:positiveInteger</code>, derived from <code>xs:nonNegativeInteger</code>. */
  POSITIVE_INTEGER ("positiveInteger", NON_NEGATIVE_INTEGER),
  /** The type <code>xs:double</code>, derived from <code>xs:anyAtomicType</code>. */
  DOUBLE ("double", ANY_ATOMIC_TYPE),
  /** The type <code>xs:float</code>, derived from <code>xs:anyAtomicType</code>. */
  FLOAT ("float", ANY_ATOMIC_TYPE),
  /** The type <code>xs:dateTime</code>, derived from <code>xs:anyAtomicType</code>. */
  DATE_TIME ("dateTime", ANY_ATOMIC_TYPE),
  /** The type <code>xs:dateTimeStamp</code>, derived from <code>xs:dateTime</code>. */
  DATE_TIME_STAMP ("dateTimeStamp", DATE_TIME),
  /** The type <code>xs:date</code>, derived from <code>xs:anyAtomicType</code>. */
  DATE ("date", ANY_ATOMIC_TYPE),
  /** The type <code>xs:time</code>, derived from <code>xs:anyAtomicType</code>. */
  TIME ("time", ANY_ATOMIC_TYPE),
  /** The type <code>xs:duration</code>, derived from <code>xs:anyAtomicType</code>. */
  DURATION ("duration", ANY_ATOMIC_TYPE),
  /** The type <code>xs:yearMonthDuration</code>, derived from <code>xs:duration</code>. */
  YEAR_MONTH_DURATION ("yearMonthDuration", DURATION),
  /** The type <code>xs:dayTimeDuration</code>, derived from <code>xs:duration</code>. */
  DAY_TIME_DURATION ("dayTimeDuration", DURATION),
  /** The type <code>xs:gYearMonth</code>, derived from <code>xs:anyAtomicType</code>. */
  G_YEAR_MONTH ("gYearMonth", ANY_ATOMIC_TYPE),
  /** The type <code>xs:gYear</code>, derived from <code>xs:anyAtomicType</code>. */
  G_YEAR ("gYear", ANY_ATOMIC_TYPE),
  /** The type <code>xs:gMonthDay</code>, derived from <code>xs:anyAtomicType</code>. */
  G_MONTH_DAY ("gMonthDay", ANY_ATOMIC_TYPE),
  /** The type <code>xs:gDay</code>, derived from <code>xs:anyAtomicType</code>. */
  G_DAY ("gDay", ANY_ATOMIC_TYPE),
  /** The type <code>xs:gMonth</code>, derived from <code>xs:anyAtomicType</code>. */
  G_MONTH ("gMonth", ANY_ATOMIC_TYPE),
  /** The type <code>xs:hexBinary</code>, derived from <code>xs:anyAtomicType</code>. */
  HEX_BINARY ("hexBinary", ANY_ATOMIC_TYPE),
  /** The type <code>xs:base64Binary</code>, derived from <code>xs:anyAtomicType</code>. */
  BASE64_BINARY ("base64Binary", ANY_ATOMIC_TYPE),
  /** The type <code>xs:anyURI</code>, derived from <code>xs:anyAtomicType</code>. */
  ANY_URI ("anyURI", ANY_ATOMIC_TYPE),
  /** The type <code>xs:QName</code>, derived from <code>xs:anyAtomicType</code>. */
  QNAME ("QName", ANY_ATOMIC_TYPE),
  /** The type <code>xs:NOTATION</code>, derived from <code>xs:anyAtomicType</code>. */
  NOTATION ("NOTATION", ANY_ATOMIC_TYPE),
  /** The union of xs:double, xs:float and xs:decimal. Never the type of a value. */
  NUMERIC ("numeric", ANY_ATOMIC_TYPE),
  /** The empty type; no value has it. */
  ERROR ("error", ANY_ATOMIC_TYPE);

  private static final Map <String, EAtomicType> BY_LOCAL_NAME = new HashMap <> ();

  private final String m_sLocalName;
  private final EAtomicType m_eParent;
  // Set once in the static initialiser (before any other thread can see the
  // constants): the bits of the ordinals of this type and all its ancestors,
  // and the answer of isNumeric
  private long m_nAncestorBits;
  private boolean m_bNumeric;

  static
  {
    final EAtomicType [] aAll = values ();
    if (aAll.length > Long.SIZE)
      throw new IllegalStateException ("Too many atomic types for the ancestor bits: " + aAll.length);
    for (final EAtomicType e : aAll)
    {
      BY_LOCAL_NAME.put (e.m_sLocalName, e);
      long nBits = 0;
      for (EAtomicType eAnc = e; eAnc != null; eAnc = eAnc.m_eParent)
        nBits |= 1L << eAnc.ordinal ();
      e.m_nAncestorBits = nBits;
    }
    final long nDecimalBit = 1L << DECIMAL.ordinal ();
    for (final EAtomicType e : aAll)
      e.m_bNumeric = e == DOUBLE || e == FLOAT || ((e.m_nAncestorBits & nDecimalBit) != 0 && e != NUMERIC);
  }

  EAtomicType (@NonNull final String sLocalName, @Nullable final EAtomicType eParent)
  {
    m_sLocalName = sLocalName;
    m_eParent = eParent;
  }

  /**
   * Get the local name of this type.
   *
   * @return The local name, e.g. <code>integer</code>.
   */
  @NonNull
  public String getLocalName ()
  {
    return m_sLocalName;
  }

  /**
   * Get the name of this type with the conventional <code>xs</code> prefix.
   *
   * @return The name with the conventional prefix, e.g. <code>xs:integer</code>.
   */
  @NonNull
  public String getQualifiedName ()
  {
    return "xs:" + m_sLocalName;
  }

  /**
   * Get the base type of this type.
   *
   * @return The base type, <code>null</code> for xs:anyAtomicType.
   */
  @Nullable
  public EAtomicType getParent ()
  {
    return m_eParent;
  }

  /**
   * Check if this type is the same as or derived from another type.
   *
   * @param eOther
   *        Candidate ancestor.
   * @return <code>true</code> if this is the same as or derived from the other type. {@link #NUMERIC} is treated as the
   *         union: DOUBLE, FLOAT, DECIMAL and everything derived from them are derived from it. Answered from bits computed
   *         once per type.
   */
  public boolean isDerivedFrom (@NonNull final EAtomicType eOther)
  {
    if (eOther == NUMERIC)
      return m_bNumeric;
    return (m_nAncestorBits & (1L << eOther.ordinal ())) != 0;
  }

  /**
   * Check if this is a numeric type.
   *
   * @return <code>true</code> for xs:double, xs:float, xs:decimal and all types derived from xs:decimal;
   *         <code>false</code> for the pseudo type {@link #NUMERIC} itself and for every other type.
   */
  public boolean isNumeric ()
  {
    return m_bNumeric;
  }

  /**
   * Check if this type is xs:integer or derived from it.
   *
   * @return <code>true</code> for xs:integer and all its derived types.
   */
  public boolean isIntegerFamily ()
  {
    return isDerivedFrom (INTEGER);
  }

  /**
   * Check if this type is xs:string or derived from it.
   *
   * @return <code>true</code> for xs:string and all its derived types.
   */
  public boolean isStringFamily ()
  {
    return isDerivedFrom (STRING);
  }

  /**
   * Check if this type is xs:duration or derived from it.
   *
   * @return <code>true</code> for xs:duration and its two subtypes.
   */
  public boolean isDurationFamily ()
  {
    return isDerivedFrom (DURATION);
  }

  /**
   * Check if this type is one of the date and time types.
   *
   * @return <code>true</code> for dateTime, dateTimeStamp, date, time and the g* types.
   */
  public boolean isDateTimeFamily ()
  {
    return isDerivedFrom (DATE_TIME) ||
           this == DATE ||
           this == TIME ||
           this == G_YEAR_MONTH ||
           this == G_YEAR ||
           this == G_MONTH_DAY ||
           this == G_DAY ||
           this == G_MONTH;
  }

  /**
   * Check if this type is one of the binary types.
   *
   * @return <code>true</code> for hexBinary and base64Binary.
   */
  public boolean isBinary ()
  {
    return this == HEX_BINARY || this == BASE64_BINARY;
  }

  /**
   * Find the built-in type with the given local name.
   *
   * @param sLocalName
   *        Local name without prefix, e.g. <code>integer</code>. May be <code>null</code>.
   * @return The matching type or <code>null</code> if there is no such built-in type.
   */
  @Nullable
  public static EAtomicType getFromLocalName (@Nullable final String sLocalName)
  {
    return sLocalName == null ? null : BY_LOCAL_NAME.get (sLocalName);
  }
}

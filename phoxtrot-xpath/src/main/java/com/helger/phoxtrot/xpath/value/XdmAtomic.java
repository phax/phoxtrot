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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.IXdmTypeDefinition;

/**
 * An immutable typed atomic value. The Java class of the payload is fixed per
 * type family:
 * <ul>
 * <li>xs:string and derived, xs:untypedAtomic, xs:anyURI: {@link String}</li>
 * <li>xs:boolean: {@link Boolean}</li>
 * <li>xs:integer and derived: {@link BigInteger}</li>
 * <li>xs:decimal: {@link BigDecimal}</li>
 * <li>xs:double: {@link Double}; xs:float: {@link Float}</li>
 * <li>date/time types including the g* types: {@link XdmDateTimeValue}</li>
 * <li>duration types: {@link XdmDurationValue}</li>
 * <li>xs:hexBinary, xs:base64Binary: <code>byte[]</code> (never modified)</li>
 * <li>xs:QName, xs:NOTATION: {@link XdmQName}</li>
 * </ul>
 * The type is always the most specific type of the value, e.g.
 * {@link EAtomicType#BYTE} for a byte.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmAtomic implements IXdmItem
{
  /** The shared xs:boolean value <code>true</code>. */
  public static final XdmAtomic TRUE = new XdmAtomic (EAtomicType.BOOLEAN, Boolean.TRUE);
  /** The shared xs:boolean value <code>false</code>. */
  public static final XdmAtomic FALSE = new XdmAtomic (EAtomicType.BOOLEAN, Boolean.FALSE);
  // The xs:integer values from -128 to 127 are shared (go-xslt 6772c77): counts,
  // positions and lengths are mostly small, and a value is immutable
  private static final int SMALL_INTEGER_MIN = -128;
  private static final int SMALL_INTEGER_MAX = 127;
  private static final XdmAtomic [] SMALL_INTEGERS = new XdmAtomic [SMALL_INTEGER_MAX - SMALL_INTEGER_MIN + 1];

  static
  {
    for (int i = 0; i < SMALL_INTEGERS.length; i++)
      SMALL_INTEGERS[i] = new XdmAtomic (EAtomicType.INTEGER, BigInteger.valueOf (SMALL_INTEGER_MIN + i));
  }

  private final EAtomicType m_eType;
  private final Object m_aValue;
  // The user-defined schema type the value was validated against; null for
  // values of built-in types
  private final IXdmTypeDefinition m_aSchemaType;

  private XdmAtomic (@NonNull final EAtomicType eType,
                     @NonNull final Object aValue,
                     @Nullable final IXdmTypeDefinition aSchemaType)
  {
    m_eType = eType;
    m_aValue = aValue;
    m_aSchemaType = aSchemaType;
  }

  private XdmAtomic (@NonNull final EAtomicType eType, @NonNull final Object aValue)
  {
    this (eType, aValue, null);
  }

  /**
   * Generic factory. The payload class must match the family of the type, see
   * the class comment. No validation of the value space (range of derived
   * integers, string facets) is done here; that is the job of the casting
   * code.
   *
   * @param eType
   *        The most specific type.
   * @param aValue
   *        The payload.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic of (@NonNull final EAtomicType eType, @NonNull final Object aValue)
  {
    ValueEnforcer.notNull (eType, "Type");
    ValueEnforcer.notNull (aValue, "Value");
    // Fast path: a small xs:integer is a shared value (a BigInteger of at most
    // 7 bits is in -128..127)
    if (eType == EAtomicType.INTEGER && aValue instanceof final BigInteger aInt && aInt.bitLength () < 8)
      return SMALL_INTEGERS[aInt.intValue () - SMALL_INTEGER_MIN];
    return new XdmAtomic (eType, aValue);
  }

  /**
   * Create an xs:string value.
   *
   * @param sValue
   *        The string. May not be <code>null</code>.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic string (@NonNull final String sValue)
  {
    return of (EAtomicType.STRING, sValue);
  }

  /**
   * Create an xs:untypedAtomic value.
   *
   * @param sValue
   *        The string. May not be <code>null</code>.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic untyped (@NonNull final String sValue)
  {
    return of (EAtomicType.UNTYPED_ATOMIC, sValue);
  }

  /**
   * Create an xs:anyURI value.
   *
   * @param sValue
   *        The URI string. May not be <code>null</code>.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic anyUri (@NonNull final String sValue)
  {
    return of (EAtomicType.ANY_URI, sValue);
  }

  /**
   * Get the xs:boolean value for a Java boolean.
   *
   * @param bValue
   *        The boolean.
   * @return {@link #TRUE} or {@link #FALSE}.
   */
  @NonNull
  public static XdmAtomic bool (final boolean bValue)
  {
    return bValue ? TRUE : FALSE;
  }

  /**
   * Create an xs:integer value. Values from -128 to 127 are shared instances.
   *
   * @param nValue
   *        The integer.
   * @return The value.
   */
  @NonNull
  public static XdmAtomic integer (final long nValue)
  {
    // Fast path: a small value is shared, without creating a BigInteger
    if (nValue >= SMALL_INTEGER_MIN && nValue <= SMALL_INTEGER_MAX)
      return SMALL_INTEGERS[(int) nValue - SMALL_INTEGER_MIN];
    return new XdmAtomic (EAtomicType.INTEGER, BigInteger.valueOf (nValue));
  }

  /**
   * Create an xs:integer value. Values from -128 to 127 are shared instances.
   *
   * @param aValue
   *        The integer. May not be <code>null</code>.
   * @return The value.
   */
  @NonNull
  public static XdmAtomic integer (@NonNull final BigInteger aValue)
  {
    return of (EAtomicType.INTEGER, aValue);
  }

  /**
   * Create an xs:decimal value.
   *
   * @param aValue
   *        The decimal. May not be <code>null</code>.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic decimal (@NonNull final BigDecimal aValue)
  {
    return of (EAtomicType.DECIMAL, aValue);
  }

  /**
   * Create an xs:double value.
   *
   * @param dValue
   *        The double.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic dbl (final double dValue)
  {
    return of (EAtomicType.DOUBLE, Double.valueOf (dValue));
  }

  /**
   * Create an xs:float value.
   *
   * @param fValue
   *        The float.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic flt (final float fValue)
  {
    return of (EAtomicType.FLOAT, Float.valueOf (fValue));
  }

  /**
   * Create an xs:QName value.
   *
   * @param aValue
   *        The qualified name. May not be <code>null</code>.
   * @return The new value.
   */
  @NonNull
  public static XdmAtomic qname (@NonNull final XdmQName aValue)
  {
    return of (EAtomicType.QNAME, aValue);
  }

  /**
   * Get the user-defined schema type of this value.
   *
   * @return The user-defined schema type this value was validated against (by atomising a validated node, by a cast to
   *         or a constructor function of a user-defined type), or <code>null</code>. {@link #getType()} is its built-in base
   *         type then.
   */
  @Nullable
  public IXdmTypeDefinition getSchemaType ()
  {
    return m_aSchemaType;
  }

  /**
   * Get this value with another user-defined schema type annotation.
   *
   * @param aSchemaType
   *        The user-defined schema type, or <code>null</code> for none.
   * @return The same value annotated with a user-defined schema type. This object if the schema type is unchanged.
   */
  @NonNull
  public XdmAtomic withSchemaType (@Nullable final IXdmTypeDefinition aSchemaType)
  {
    if (aSchemaType == m_aSchemaType)
      return this;
    return new XdmAtomic (m_eType, m_aValue, aSchemaType);
  }

  /**
   * Get the built-in type of this value.
   *
   * @return The most specific built-in type of this value.
   */
  public EAtomicType getType ()
  {
    return m_eType;
  }

  /**
   * Get the payload of this value.
   *
   * @return The payload, see the class comment for its Java class.
   */
  @NonNull
  public Object getValue ()
  {
    return m_aValue;
  }

  /**
   * Check if this value is of a numeric type.
   *
   * @return <code>true</code> if the type is numeric, see {@link EAtomicType#isNumeric()}.
   */
  public boolean isNumeric ()
  {
    return m_eType.isNumeric ();
  }

  /**
   * Check if this value is string-like.
   *
   * @return <code>true</code> for xs:string and its derived types, xs:anyURI and xs:untypedAtomic, i.e. the types whose
   *         payload is a {@link String} and which string functions and comparisons accept.
   */
  public boolean isStringLike ()
  {
    return m_eType.isStringFamily () || m_eType == EAtomicType.ANY_URI || m_eType == EAtomicType.UNTYPED_ATOMIC;
  }

  /**
   * Get the payload of a string-like value.
   *
   * @return The payload of a string-like value.
   */
  @NonNull
  public String getString ()
  {
    return (String) m_aValue;
  }

  /**
   * Get the payload of an xs:boolean value.
   *
   * @return The boolean payload.
   */
  public boolean getBoolean ()
  {
    return ((Boolean) m_aValue).booleanValue ();
  }

  /**
   * Get the payload of an xs:integer value or one derived from it.
   *
   * @return The integer payload.
   */
  @NonNull
  public BigInteger getBigInteger ()
  {
    return (BigInteger) m_aValue;
  }

  /**
   * Get the payload of an xs:decimal value.
   *
   * @return The decimal payload.
   */
  @NonNull
  public BigDecimal getBigDecimal ()
  {
    return (BigDecimal) m_aValue;
  }

  /**
   * Get the numeric value as double.
   *
   * @return The numeric value as double. For integer, decimal and float values this is the nearest double. Only valid
   *         for numeric types.
   */
  public double getAsDouble ()
  {
    if (m_aValue instanceof final Double aDouble)
      return aDouble.doubleValue ();
    if (m_aValue instanceof final Float aFloat)
      return aFloat.doubleValue ();
    if (m_aValue instanceof final BigInteger aBigInt)
      return aBigInt.doubleValue ();
    if (m_aValue instanceof final BigDecimal aBigDec)
      return aBigDec.doubleValue ();
    throw new IllegalStateException ("Not a numeric value: " + m_eType);
  }

  /**
   * Get the canonical lexical representation of this value.
   *
   * @return The canonical lexical representation, as xs:string cast would produce it.
   */
  @NonNull
  public String getLexical ()
  {
    return AtomicLexical.toLexical (this);
  }

  @Override
  public String toString ()
  {
    return m_eType.getQualifiedName () + "(" + getLexical () + ")";
  }
}

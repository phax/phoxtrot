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
package com.helger.phoxtrot.xpath.serialize;

import java.math.BigDecimal;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * The serialization parameters of Serialization 3.1 section 3. The defaults
 * are those of <code>fn:serialize</code> (F&amp;O 3.1 section 14.7.3), e.g.
 * <code>omit-xml-declaration</code> is <code>true</code>. Instances are
 * mutable; the setters return this for chaining.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class SerializationParams
{
  /** The namespace of the serialization parameter elements. */
  public static final String NS_OUTPUT = "http://www.w3.org/2010/xslt-xquery-serialization";

  /** The <code>xml</code> output method. */
  public static final String METHOD_XML = "xml";
  /** The <code>xhtml</code> output method. */
  public static final String METHOD_XHTML = "xhtml";
  /** The <code>html</code> output method. */
  public static final String METHOD_HTML = "html";
  /** The <code>text</code> output method. */
  public static final String METHOD_TEXT = "text";
  /** The <code>json</code> output method. */
  public static final String METHOD_JSON = "json";
  /** The <code>adaptive</code> output method. */
  public static final String METHOD_ADAPTIVE = "adaptive";

  /** The <code>standalone</code> value <code>yes</code>. */
  public static final String STANDALONE_YES = "yes";
  /** The <code>standalone</code> value <code>no</code>. */
  public static final String STANDALONE_NO = "no";
  /** The <code>standalone</code> value <code>omit</code> (no standalone declaration is written). */
  public static final String STANDALONE_OMIT = "omit";

  private String m_sMethod = METHOD_XML;
  private boolean m_bAllowDuplicateNames = false;
  private boolean m_bByteOrderMark = false;
  private final ICommonsOrderedSet <XdmQName> m_aCdataSectionElements = new CommonsLinkedHashSet <> ();
  private String m_sDoctypePublic;
  private String m_sDoctypeSystem;
  private String m_sEncoding = "UTF-8";
  private boolean m_bEscapeUriAttributes = true;
  private BigDecimal m_aHtmlVersion;
  private boolean m_bIncludeContentType = true;
  private boolean m_bIndent = false;
  private String m_sItemSeparator;
  private String m_sJsonNodeOutputMethod = METHOD_XML;
  private String m_sMediaType;
  private String m_sNormalizationForm = "none";
  private boolean m_bOmitXmlDeclaration = true;
  private String m_sStandalone = STANDALONE_OMIT;
  private final ICommonsOrderedSet <XdmQName> m_aSuppressIndentation = new CommonsLinkedHashSet <> ();
  private boolean m_bUndeclarePrefixes = false;
  private final ICommonsMap <Integer, String> m_aCharacterMap = new CommonsHashMap <> ();
  private String m_sVersion;

  /** Create serialisation parameters with the defaults of <code>fn:serialize</code>. */
  public SerializationParams ()
  {}

  /**
   * Copy constructor.
   *
   * @param aOther
   *        The parameters to copy.
   */
  public SerializationParams (@NonNull final SerializationParams aOther)
  {
    ValueEnforcer.notNull (aOther, "Other");
    m_sMethod = aOther.m_sMethod;
    m_bAllowDuplicateNames = aOther.m_bAllowDuplicateNames;
    m_bByteOrderMark = aOther.m_bByteOrderMark;
    m_aCdataSectionElements.addAll (aOther.m_aCdataSectionElements);
    m_sDoctypePublic = aOther.m_sDoctypePublic;
    m_sDoctypeSystem = aOther.m_sDoctypeSystem;
    m_sEncoding = aOther.m_sEncoding;
    m_bEscapeUriAttributes = aOther.m_bEscapeUriAttributes;
    m_aHtmlVersion = aOther.m_aHtmlVersion;
    m_bIncludeContentType = aOther.m_bIncludeContentType;
    m_bIndent = aOther.m_bIndent;
    m_sItemSeparator = aOther.m_sItemSeparator;
    m_sJsonNodeOutputMethod = aOther.m_sJsonNodeOutputMethod;
    m_sMediaType = aOther.m_sMediaType;
    m_sNormalizationForm = aOther.m_sNormalizationForm;
    m_bOmitXmlDeclaration = aOther.m_bOmitXmlDeclaration;
    m_sStandalone = aOther.m_sStandalone;
    m_aSuppressIndentation.addAll (aOther.m_aSuppressIndentation);
    m_bUndeclarePrefixes = aOther.m_bUndeclarePrefixes;
    m_aCharacterMap.putAll (aOther.m_aCharacterMap);
    m_sVersion = aOther.m_sVersion;
  }

  /**
   * Get the output method.
   *
   * @return The output method: xml, xhtml, html, text, json or adaptive.
   */
  @NonNull
  public String getMethod ()
  {
    return m_sMethod;
  }

  /**
   * Set the output method.
   *
   * @param sMethod
   *        The output method, e.g. one of the <code>METHOD_*</code> constants. May neither be <code>null</code> nor
   *        empty.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setMethod (@NonNull final String sMethod)
  {
    ValueEnforcer.notEmpty (sMethod, "Method");
    m_sMethod = sMethod;
    return this;
  }

  /**
   * Get the allow-duplicate-names parameter.
   *
   * @return <code>true</code> if duplicate names are allowed, <code>false</code> by default.
   */
  public boolean isAllowDuplicateNames ()
  {
    return m_bAllowDuplicateNames;
  }

  /**
   * Set the allow-duplicate-names parameter.
   *
   * @param b
   *        <code>true</code> to allow duplicate names.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setAllowDuplicateNames (final boolean b)
  {
    m_bAllowDuplicateNames = b;
    return this;
  }

  /**
   * Get the byte-order-mark parameter.
   *
   * @return <code>true</code> if a byte order mark is written, <code>false</code> by default.
   */
  public boolean isByteOrderMark ()
  {
    return m_bByteOrderMark;
  }

  /**
   * Set the byte-order-mark parameter.
   *
   * @param b
   *        <code>true</code> to write a byte order mark.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setByteOrderMark (final boolean b)
  {
    m_bByteOrderMark = b;
    return this;
  }

  /**
   * Get the cdata-section-elements parameter.
   *
   * @return A copy of the element names whose text content is written as CDATA sections, in insertion order. Never
   *         <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedSet <XdmQName> getAllCdataSectionElements ()
  {
    return m_aCdataSectionElements.getClone ();
  }

  /**
   * Check if an element name is one of the cdata-section-elements.
   *
   * @param aName
   *        The element name to check. May not be <code>null</code>.
   * @return <code>true</code> if the text content of such elements is written as CDATA sections.
   */
  public boolean isCdataSectionElement (@NonNull final XdmQName aName)
  {
    return m_aCdataSectionElements.contains (aName);
  }

  /**
   * Add an element name to the cdata-section-elements.
   *
   * @param aName
   *        The element name to add. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams addCdataSectionElement (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aCdataSectionElements.add (aName);
    return this;
  }

  /**
   * Get the doctype-public parameter.
   *
   * @return The public identifier of the document type declaration, <code>null</code> if absent.
   */
  @Nullable
  public String getDoctypePublic ()
  {
    return m_sDoctypePublic;
  }

  /**
   * Set the doctype-public parameter.
   *
   * @param s
   *        The public identifier of the document type declaration. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setDoctypePublic (@Nullable final String s)
  {
    m_sDoctypePublic = s;
    return this;
  }

  /**
   * Get the doctype-system parameter.
   *
   * @return The system identifier of the document type declaration, <code>null</code> if absent.
   */
  @Nullable
  public String getDoctypeSystem ()
  {
    return m_sDoctypeSystem;
  }

  /**
   * Set the doctype-system parameter.
   *
   * @param s
   *        The system identifier of the document type declaration. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setDoctypeSystem (@Nullable final String s)
  {
    m_sDoctypeSystem = s;
    return this;
  }

  /**
   * Get the encoding parameter.
   *
   * @return The output encoding, <code>UTF-8</code> by default. Never <code>null</code>.
   */
  @NonNull
  public String getEncoding ()
  {
    return m_sEncoding;
  }

  /**
   * Set the encoding parameter.
   *
   * @param s
   *        The output encoding. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setEncoding (@NonNull final String s)
  {
    ValueEnforcer.notNull (s, "Encoding");
    m_sEncoding = s;
    return this;
  }

  /**
   * Get the escape-uri-attributes parameter.
   *
   * @return <code>true</code> if URI attributes are escaped, <code>true</code> by default.
   */
  public boolean isEscapeUriAttributes ()
  {
    return m_bEscapeUriAttributes;
  }

  /**
   * Set the escape-uri-attributes parameter.
   *
   * @param b
   *        <code>true</code> to escape URI attributes.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setEscapeUriAttributes (final boolean b)
  {
    m_bEscapeUriAttributes = b;
    return this;
  }

  /**
   * Get the html-version parameter.
   *
   * @return The html-version parameter, <code>null</code> if absent.
   */
  @Nullable
  public BigDecimal getHtmlVersion ()
  {
    return m_aHtmlVersion;
  }

  /**
   * Set the html-version parameter.
   *
   * @param a
   *        The HTML version. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setHtmlVersion (@Nullable final BigDecimal a)
  {
    m_aHtmlVersion = a;
    return this;
  }

  /**
   * Get the include-content-type parameter.
   *
   * @return <code>true</code> if a content type <code>meta</code> element is included, <code>true</code> by default.
   */
  public boolean isIncludeContentType ()
  {
    return m_bIncludeContentType;
  }

  /**
   * Set the include-content-type parameter.
   *
   * @param b
   *        <code>true</code> to include a content type <code>meta</code> element.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setIncludeContentType (final boolean b)
  {
    m_bIncludeContentType = b;
    return this;
  }

  /**
   * Get the indent parameter.
   *
   * @return <code>true</code> if the output is indented, <code>false</code> by default.
   */
  public boolean isIndent ()
  {
    return m_bIndent;
  }

  /**
   * Set the indent parameter.
   *
   * @param b
   *        <code>true</code> to indent the output.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setIndent (final boolean b)
  {
    m_bIndent = b;
    return this;
  }

  /**
   * Get the item-separator parameter.
   *
   * @return The item-separator, <code>null</code> if absent.
   */
  @Nullable
  public String getItemSeparator ()
  {
    return m_sItemSeparator;
  }

  /**
   * Set the item-separator parameter.
   *
   * @param s
   *        The item separator. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setItemSeparator (@Nullable final String s)
  {
    m_sItemSeparator = s;
    return this;
  }

  /**
   * Get the json-node-output-method parameter.
   *
   * @return The output method used for nodes in JSON output, <code>xml</code> by default. Never <code>null</code>.
   */
  @NonNull
  public String getJsonNodeOutputMethod ()
  {
    return m_sJsonNodeOutputMethod;
  }

  /**
   * Set the json-node-output-method parameter.
   *
   * @param s
   *        The output method used for nodes in JSON output. May neither be <code>null</code> nor empty.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setJsonNodeOutputMethod (@NonNull final String s)
  {
    ValueEnforcer.notEmpty (s, "JsonNodeOutputMethod");
    m_sJsonNodeOutputMethod = s;
    return this;
  }

  /**
   * Get the media-type parameter.
   *
   * @return The media type, <code>null</code> if absent.
   */
  @Nullable
  public String getMediaType ()
  {
    return m_sMediaType;
  }

  /**
   * Set the media-type parameter.
   *
   * @param s
   *        The media type. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setMediaType (@Nullable final String s)
  {
    m_sMediaType = s;
    return this;
  }

  /**
   * Get the normalization-form parameter.
   *
   * @return NFC, NFD, NFKC, NFKD, fully-normalized or none.
   */
  @NonNull
  public String getNormalizationForm ()
  {
    return m_sNormalizationForm;
  }

  /**
   * Set the normalization-form parameter.
   *
   * @param s
   *        The normalisation form, e.g. NFC or none. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setNormalizationForm (@NonNull final String s)
  {
    ValueEnforcer.notNull (s, "NormalizationForm");
    m_sNormalizationForm = s;
    return this;
  }

  /**
   * Get the omit-xml-declaration parameter.
   *
   * @return <code>true</code> if the XML declaration is omitted, <code>true</code> by default.
   */
  public boolean isOmitXmlDeclaration ()
  {
    return m_bOmitXmlDeclaration;
  }

  /**
   * Set the omit-xml-declaration parameter.
   *
   * @param b
   *        <code>true</code> to omit the XML declaration.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setOmitXmlDeclaration (final boolean b)
  {
    m_bOmitXmlDeclaration = b;
    return this;
  }

  /**
   * Get the standalone parameter.
   *
   * @return yes, no or omit.
   */
  @NonNull
  public String getStandalone ()
  {
    return m_sStandalone;
  }

  /**
   * Set the standalone parameter.
   *
   * @param s
   *        One of the <code>STANDALONE_*</code> constants. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setStandalone (@NonNull final String s)
  {
    ValueEnforcer.notNull (s, "Standalone");
    m_sStandalone = s;
    return this;
  }

  /**
   * Get the suppress-indentation parameter.
   *
   * @return A copy of the element names whose content is not indented, in insertion order. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedSet <XdmQName> getAllSuppressIndentation ()
  {
    return m_aSuppressIndentation.getClone ();
  }

  /**
   * Check if an element name is one of the suppress-indentation elements.
   *
   * @param aName
   *        The element name to check. May not be <code>null</code>.
   * @return <code>true</code> if the content of such elements is not indented.
   */
  public boolean isSuppressIndentation (@NonNull final XdmQName aName)
  {
    return m_aSuppressIndentation.contains (aName);
  }

  /**
   * Add an element name to the suppress-indentation elements.
   *
   * @param aName
   *        The element name to add. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams addSuppressIndentation (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aSuppressIndentation.add (aName);
    return this;
  }

  /**
   * Get the undeclare-prefixes parameter.
   *
   * @return <code>true</code> if namespace prefixes are undeclared, <code>false</code> by default.
   */
  public boolean isUndeclarePrefixes ()
  {
    return m_bUndeclarePrefixes;
  }

  /**
   * Set the undeclare-prefixes parameter.
   *
   * @param b
   *        <code>true</code> to undeclare namespace prefixes.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setUndeclarePrefixes (final boolean b)
  {
    m_bUndeclarePrefixes = b;
    return this;
  }

  /**
   * Get a copy of the character map.
   *
   * @return The character map (use-character-maps), code point to replacement.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsMap <Integer, String> getCharacterMap ()
  {
    return m_aCharacterMap.getClone ();
  }

  /**
   * Check if a character map is present.
   *
   * @return <code>true</code> if at least one character mapping is defined.
   */
  public boolean hasCharacterMap ()
  {
    return !m_aCharacterMap.isEmpty ();
  }

  /**
   * Get the replacement of a code point from the character map.
   *
   * @param nCodePoint
   *        A code point.
   * @return The replacement or <code>null</code> if the character is not mapped.
   */
  @Nullable
  public String getCharacterMapping (final int nCodePoint)
  {
    return m_aCharacterMap.get (Integer.valueOf (nCodePoint));
  }

  /**
   * Replace the whole character map.
   *
   * @param aMap
   *        The new mappings from code point to replacement. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setCharacterMap (@NonNull final Map <Integer, String> aMap)
  {
    ValueEnforcer.notNull (aMap, "Map");
    m_aCharacterMap.setAll (aMap);
    return this;
  }

  /**
   * Add or replace one character mapping.
   *
   * @param nCodePoint
   *        The code point to map.
   * @param sReplacement
   *        The replacement string. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams addCharacterMapping (final int nCodePoint, @NonNull final String sReplacement)
  {
    ValueEnforcer.notNull (sReplacement, "Replacement");
    m_aCharacterMap.put (Integer.valueOf (nCodePoint), sReplacement);
    return this;
  }

  /**
   * Get the version parameter.
   *
   * @return The version parameter, <code>null</code> if absent.
   */
  @Nullable
  public String getVersion ()
  {
    return m_sVersion;
  }

  /**
   * Set the version parameter.
   *
   * @param s
   *        The version. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public SerializationParams setVersion (@Nullable final String s)
  {
    m_sVersion = s;
    return this;
  }

  /**
   * Check if the XML version to write is 1.1.
   *
   * @return <code>true</code> if the XML version to write is 1.1.
   */
  public boolean isXml11 ()
  {
    return "1.1".equals (m_sVersion);
  }
}

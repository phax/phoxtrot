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
package com.helger.phoxtrot.xpath.context;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.collation.CollationRegistry;
import com.helger.phoxtrot.xpath.collation.ICollation;
import com.helger.phoxtrot.xpath.fn.FormatDecimalFormat;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;

/**
 * The static context of an expression: in-scope namespaces, default
 * namespaces, base URI, function library and language version switches. Fill it
 * before compiling; treat it as read-only afterwards.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class StaticContext
{
  /** The XML version used unless set otherwise. */
  public static final EXmlVersion DEFAULT_XML_VERSION = EXmlVersion.V10;
  /** The XSD version used unless set otherwise. */
  public static final EXsdVersion DEFAULT_XSD_VERSION = EXsdVersion.V10;

  /**
   * The fields of a static context that evaluation can read (everything except the declared
   * variables, which only the compilation reads), compared by value; function library, decimal
   * formats and schema set by identity.
   */
  @Immutable
  private record RunTimeKey (Map <String, String> namespaces,
                             String defaultElementNamespace,
                             String inScopeDefaultNamespace,
                             String defaultFunctionNamespace,
                             String baseUri,
                             FunctionLibrary functions,
                             boolean xpath10Compatibility,
                             String unknownFunctionErrorCode,
                             boolean staticTyping,
                             ItemType contextItemStaticType,
                             String defaultCollation,
                             FormatDecimalFormat defaultDecimalFormat,
                             Map <XdmQName, FormatDecimalFormat> decimalFormats,
                             EXmlVersion xmlVersion,
                             EXsdVersion xsdVersion,
                             boolean xsdVersionSet,
                             IXdmSchemaSet schemaSet)
  {}

  private final ICommonsMap <String, String> m_aNamespaces = new CommonsHashMap <> ();
  private String m_sDefaultElementNamespace = "";
  private String m_sInScopeDefaultNamespace = "";
  private String m_sDefaultFunctionNamespace = XdmConstants.NS_FN;
  private String m_sBaseUri;
  private FunctionLibrary m_aFunctions = FunctionLibrary.getDefault ();
  private boolean m_bXPath10Compatibility = false;
  private String m_sUnknownFunctionErrorCode;
  private boolean m_bStaticTyping = false;
  private ItemType m_aContextItemStaticType;
  // The declared types of variables declared with a type; null for none
  private ICommonsMap <XdmQName, SeqType> m_aVariableTypes;
  private String m_sDefaultCollation = CollationRegistry.CODEPOINT_URI;
  private FormatDecimalFormat m_aDefaultDecimalFormat = FormatDecimalFormat.DEFAULT;
  private final ICommonsMap <XdmQName, FormatDecimalFormat> m_aDecimalFormats = new CommonsHashMap <> ();
  private final ICommonsSet <XdmQName> m_aDeclaredVariables = new CommonsHashSet <> ();
  // The declared variables bound by slot (declareFrameVariable); null for none
  private ICommonsMap <XdmQName, VariableSlot> m_aFrameVariables;
  // Further declared variables in a set shared with other static contexts
  // (declareSharedVariables), never changed while in use; null for none
  private Set <XdmQName> m_aSharedVariables;
  // The name of m_aSharedVariables that is not declared; null for none
  private XdmQName m_aSharedVariableExcluded;
  private EXmlVersion m_eXmlVersion = DEFAULT_XML_VERSION;
  private EXsdVersion m_eXsdVersion = DEFAULT_XSD_VERSION;
  private boolean m_bXsdVersionSet = false;
  private IXdmSchemaSet m_aSchemaSet;
  // The default collation as resolved by getResolvedDefaultCollation; null until the first successful resolution
  private volatile ICollation m_aResolvedDefaultCollation;
  // The shared key of all static contexts with the same run-time fields (internRunTimeIdentity); null
  // if not interned or changed since
  private Object m_aRunTimeIdentity;

  /**
   * Create a static context with the prefixes <code>xml</code>,
   * <code>xs</code>, <code>xsi</code>, <code>fn</code>, <code>math</code>,
   * <code>map</code>, <code>array</code> and <code>err</code> predeclared, no
   * default element namespace, <code>fn</code> as default function namespace,
   * the default function library and the codepoint collation.
   */
  public StaticContext ()
  {
    m_aNamespaces.put ("xml", XdmConstants.NS_XML);
    m_aNamespaces.put ("xs", XdmConstants.NS_XS);
    m_aNamespaces.put ("xsi", XdmConstants.NS_XSI);
    m_aNamespaces.put ("fn", XdmConstants.NS_FN);
    m_aNamespaces.put ("math", "http://www.w3.org/2005/xpath-functions/math");
    m_aNamespaces.put ("map", "http://www.w3.org/2005/xpath-functions/map");
    m_aNamespaces.put ("array", "http://www.w3.org/2005/xpath-functions/array");
    m_aNamespaces.put ("err", "http://www.w3.org/2005/xqt-errors");
  }

  /**
   * Declare or replace a prefix.
   *
   * @param sPrefix
   *        Prefix, non-empty; use {@link #setDefaultElementNamespace(String)}
   *        for the default namespace.
   * @param sNamespaceUri
   *        Namespace URI.
   * @return this for chaining
   */
  @NonNull
  public StaticContext declareNamespace (@NonNull final String sPrefix, @NonNull final String sNamespaceUri)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    ValueEnforcer.notEmpty (sPrefix, "Prefix");
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    m_aNamespaces.put (sPrefix, sNamespaceUri);
    return this;
  }

  /**
   * Remove all prefix bindings except <code>xml</code>, including the
   * predeclared ones (XSLT knows only the namespaces in scope in the
   * stylesheet).
   *
   * @return this for chaining
   */
  @NonNull
  public StaticContext removeAllNamespacesExceptXml ()
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_aNamespaces.entrySet ().removeIf (e -> !"xml".equals (e.getKey ()));
    return this;
  }

  /**
   * Look up the namespace URI bound to a prefix.
   *
   * @param sPrefix
   *        The prefix.
   * @return The namespace URI or <code>null</code> if the prefix is unbound.
   */
  @Nullable
  public String getNamespaceUri (@NonNull final String sPrefix)
  {
    return m_aNamespaces.get (sPrefix);
  }

  /**
   * Get the default element/type namespace.
   *
   * @return The default element/type namespace of XPath: the namespace of an
   *         unprefixed element or type name. Empty for no namespace (the
   *         default).
   */
  @NonNull
  public String getDefaultElementNamespace ()
  {
    return m_sDefaultElementNamespace;
  }

  /**
   * Set the default element/type namespace.
   *
   * @param sNamespaceUri
   *        The namespace URI, empty for no namespace. May not be
   *        <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setDefaultElementNamespace (@NonNull final String sNamespaceUri)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_sDefaultElementNamespace = ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    return this;
  }

  /**
   * Get the default namespace declared in scope of the expression.
   *
   * @return The default namespace declared in scope of the expression
   *         (<code>xmlns="..."</code> in a host language such as XSLT), which
   *         is not the default element namespace of XPath. Empty if none.
   */
  @NonNull
  public String getInScopeDefaultNamespace ()
  {
    return m_sInScopeDefaultNamespace;
  }

  /**
   * Set the default namespace declared in scope of the expression.
   *
   * @param sNamespaceUri
   *        The default namespace declared in scope of the expression, empty
   *        for none.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setInScopeDefaultNamespace (@NonNull final String sNamespaceUri)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    m_sInScopeDefaultNamespace = sNamespaceUri;
    return this;
  }

  /**
   * Get the default function namespace.
   *
   * @return The namespace of an unprefixed function name; the
   *         <code>fn</code> namespace unless set otherwise.
   */
  @NonNull
  public String getDefaultFunctionNamespace ()
  {
    return m_sDefaultFunctionNamespace;
  }

  /**
   * Set the default function namespace.
   *
   * @param sNamespaceUri
   *        The namespace of an unprefixed function name. May not be
   *        <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setDefaultFunctionNamespace (@NonNull final String sNamespaceUri)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_sDefaultFunctionNamespace = ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    return this;
  }

  /**
   * Get the static base URI.
   *
   * @return The static base URI, used to resolve relative URIs (e.g. of
   *         <code>fn:doc</code>), or <code>null</code> if none is known.
   */
  @Nullable
  public String getBaseUri ()
  {
    return m_sBaseUri;
  }

  /**
   * Set the static base URI.
   *
   * @param sBaseUri
   *        The static base URI. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setBaseUri (@Nullable final String sBaseUri)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_sBaseUri = sBaseUri;
    m_aResolvedDefaultCollation = null;
    return this;
  }

  /**
   * Get the function library of this static context.
   *
   * @return The function library. Never <code>null</code>.
   */
  @NonNull
  public FunctionLibrary getFunctionLibrary ()
  {
    return m_aFunctions;
  }

  /**
   * Set the function library of this static context.
   *
   * @param aFunctions
   *        The function library. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setFunctionLibrary (@NonNull final FunctionLibrary aFunctions)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_aFunctions = ValueEnforcer.notNull (aFunctions, "Functions");
    return this;
  }

  /**
   * Check whether XPath 1.0 compatibility mode is on.
   *
   * @return <code>true</code> if XPath 1.0 compatibility mode is on: general
   *         comparisons, arithmetic and the conversion of function arguments
   *         (XPath 3.1 section 3.1.5.2) follow the XPath 1.0 rules. Default
   *         <code>false</code>.
   */
  public boolean isXPath10Compatibility ()
  {
    return m_bXPath10Compatibility;
  }

  /**
   * Switch XPath 1.0 compatibility mode on or off.
   *
   * @param bXPath10Compatibility
   *        <code>true</code> to switch it on.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setXPath10Compatibility (final boolean bXPath10Compatibility)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_bXPath10Compatibility = bXPath10Compatibility;
    return this;
  }

  /**
   * Get the error code of the fallback error function of a host language.
   *
   * @return The error code of the fallback error function of a host language,
   *         or <code>null</code> (the default) if a static call of an unknown
   *         function is the static error XPST0017. See
   *         {@link #setUnknownFunctionErrorCode(String)}.
   */
  @Nullable
  public String getUnknownFunctionErrorCode ()
  {
    return m_sUnknownFunctionErrorCode;
  }

  /**
   * Bind the static calls of unknown functions to a fallback error function,
   * as XSLT 3.0 section 3.9.1 asks for expressions processed with XSLT 1.0
   * behaviour: a static function call whose name is not in the standard
   * function namespace (<code>fn</code>) and for which the function library
   * has no function of that name and arity is then no static error XPST0017;
   * evaluating it raises a dynamic error with this code (XTDE1425 for XSLT).
   * Named function references (<code>f#1</code>) and names in the standard
   * function namespace stay static errors.
   *
   * @param sUnknownFunctionErrorCode
   *        The error code of the fallback error function, or
   *        <code>null</code> for the XPath rule (XPST0017).
   * @return this for chaining
   */
  @NonNull
  public StaticContext setUnknownFunctionErrorCode (@Nullable final String sUnknownFunctionErrorCode)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_sUnknownFunctionErrorCode = sUnknownFunctionErrorCode;
    return this;
  }

  /**
   * Check whether the Static Typing Feature is in effect.
   *
   * @return <code>true</code> if the narrow part of the optional Static Typing
   *         Feature that this engine implements is in effect: an axis step
   *         that provably selects nothing is the static error XPST0005.
   *         Default <code>false</code>.
   */
  public boolean isStaticTyping ()
  {
    return m_bStaticTyping;
  }

  /**
   * Opt in to the narrow part of XPath 3.1's optional Static Typing Feature
   * that this engine implements: axis steps that can be proven, from the axis
   * and node-test shape alone, to always select nothing raise XPST0005 at
   * compile time. Off, the same expression evaluates to an empty sequence.
   *
   * @param bStaticTyping
   *        <code>true</code> to enable the check.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setStaticTyping (final boolean bStaticTyping)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_bStaticTyping = bStaticTyping;
    return this;
  }

  /**
   * Get the context item static type.
   *
   * @return The context item static type (XPath 3.1 section 2.1.1) the Static
   *         Typing Feature assumes, or <code>null</code> for
   *         <code>item()</code> (the default).
   * @since 0.3.0
   */
  @Nullable
  public ItemType getContextItemStaticType ()
  {
    return m_aContextItemStaticType;
  }

  /**
   * Declare the static type of the context item the expressions will be
   * evaluated with. Only the Static Typing Feature
   * ({@link #setStaticTyping(boolean)}) uses it; without a declaration the
   * context item has the static type <code>item()</code>, so that a relative
   * path is a type error under the feature. The context item is not checked
   * against it at run time.
   *
   * @param aType
   *        The item type; <code>null</code> for <code>item()</code>.
   * @return this for chaining
   * @since 0.3.0
   */
  @NonNull
  public StaticContext setContextItemStaticType (@Nullable final ItemType aType)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_aContextItemStaticType = aType;
    return this;
  }

  /**
   * Get the in-scope schema definitions.
   *
   * @return The in-scope schema definitions beyond the built-in types (the
   *         components of a schema-aware host), or <code>null</code> if there
   *         are none.
   */
  @Nullable
  public IXdmSchemaSet getSchemaSet ()
  {
    return m_aSchemaSet;
  }

  /**
   * Set the in-scope schema definitions: type names, element and attribute
   * declarations that <code>element(N, T)</code>,
   * <code>schema-element(N)</code>, <code>schema-attribute(N)</code>, casts
   * and constructor functions may refer to.
   *
   * @param aSchemaSet
   *        The schema set, or <code>null</code> for the built-in types only.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setSchemaSet (@Nullable final IXdmSchemaSet aSchemaSet)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_aSchemaSet = aSchemaSet;
    return this;
  }

  /**
   * Declare an in-scope variable, so that a reference to it compiles. The
   * value is bound in the dynamic context.
   *
   * @param aName
   *        Variable name.
   * @return this for chaining
   */
  @NonNull
  public StaticContext declareVariable (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aDeclaredVariables.add (aName);
    return this;
  }

  /**
   * Declare an in-scope variable with its static type, so that a reference to
   * it compiles and, with the Static Typing Feature
   * ({@link #setStaticTyping(boolean)}), has that type instead of
   * <code>item()*</code>. The value is bound in the dynamic context and is not
   * checked against the type.
   *
   * @param aName
   *        Variable name.
   * @param aType
   *        The static type of the variable; <code>null</code> for
   *        <code>item()*</code>.
   * @return this for chaining
   * @since 0.3.0
   */
  @NonNull
  public StaticContext declareVariable (@NonNull final XdmQName aName, @Nullable final SeqType aType)
  {
    declareVariable (aName);
    if (aType != null)
    {
      if (m_aVariableTypes == null)
        m_aVariableTypes = new CommonsHashMap <> ();
      m_aVariableTypes.put (aName, aType);
    }
    else
      if (m_aVariableTypes != null)
        m_aVariableTypes.remove (aName);
    return this;
  }

  /**
   * Get the declared static type of a variable.
   *
   * @param aName
   *        Variable name.
   * @return The static type declared with
   *         {@link #declareVariable(XdmQName, SeqType)}, or <code>null</code>
   *         if none was declared (<code>item()*</code>).
   * @since 0.3.0
   */
  @Nullable
  public SeqType getVariableType (@Nullable final XdmQName aName)
  {
    return m_aVariableTypes == null || aName == null ? null : m_aVariableTypes.get (aName);
  }

  /**
   * Declare all variables of a set that many static contexts share, such as
   * the global variables of a stylesheet, without copying it: every name in it
   * except the excluded one counts as declared, in addition to the variables
   * declared with {@link #declareVariable(XdmQName)}. The set is kept by
   * reference, so it must not change while this static context is used. A
   * second call replaces the shared set of the first.
   *
   * @param aNames
   *        The variable names. Not copied.
   * @param aExcluded
   *        A name of the set that is not declared (unless it is declared with
   *        {@link #declareVariable(XdmQName)}), or <code>null</code> for none.
   * @return this for chaining
   * @since 0.2.0
   */
  @NonNull
  public StaticContext declareSharedVariables (@NonNull final Set <XdmQName> aNames, @Nullable final XdmQName aExcluded)
  {
    ValueEnforcer.notNull (aNames, "Names");
    m_aSharedVariables = aNames;
    m_aSharedVariableExcluded = aExcluded;
    return this;
  }

  /**
   * Declare an in-scope variable whose value the host binds in a slot of the {@link VariableFrame}
   * of the dynamic context instead of by name, so that the compiled references read the slot. The
   * first declaration of a name wins: declare the innermost variable of a name first.
   *
   * @param aSlot
   *        The slot, with the variable name.
   * @return this for chaining
   */
  @NonNull
  public StaticContext declareFrameVariable (@NonNull final VariableSlot aSlot)
  {
    ValueEnforcer.notNull (aSlot, "Slot");
    declareVariable (aSlot.name ());
    if (m_aFrameVariables == null)
      m_aFrameVariables = new CommonsHashMap <> ();
    m_aFrameVariables.putIfAbsent (aSlot.name (), aSlot);
    return this;
  }

  /**
   * Get the slot of a variable that is bound by slot.
   *
   * @param aName
   *        Variable name.
   * @return The slot of a variable declared with {@link #declareFrameVariable(VariableSlot)}, or
   *         <code>null</code> if the variable is bound by name or not declared.
   */
  @Nullable
  public VariableSlot getFrameVariable (@Nullable final XdmQName aName)
  {
    return m_aFrameVariables == null || aName == null ? null : m_aFrameVariables.get (aName);
  }

  /**
   * Check whether a variable is declared.
   *
   * @param aName
   *        Variable name.
   * @return <code>true</code> if the variable was declared via
   *         {@link #declareVariable(XdmQName)} or is in the set of
   *         {@link #declareSharedVariables(Set, XdmQName)} and not excluded
   *         there.
   */
  public boolean isVariableDeclared (@Nullable final XdmQName aName)
  {
    if (aName == null)
      return false;
    if (m_aDeclaredVariables.contains (aName))
      return true;
    return m_aSharedVariables != null && m_aSharedVariables.contains (aName) && !aName.equals (m_aSharedVariableExcluded);
  }

  /**
   * Get the default collation URI.
   *
   * @return The default collation URI; the codepoint collation unless set.
   */
  @NonNull
  public String getDefaultCollation ()
  {
    return m_sDefaultCollation;
  }

  /**
   * Set the default collation URI.
   *
   * @param sCollationUri
   *        The default collation URI. May neither be <code>null</code> nor
   *        empty.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setDefaultCollation (@NonNull final String sCollationUri)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_sDefaultCollation = ValueEnforcer.notEmpty (sCollationUri, "CollationUri");
    m_aResolvedDefaultCollation = null;
    return this;
  }

  /**
   * Resolve a collation URI as written in a function call: the codepoint collation URI directly, any other URI is
   * parsed, resolved against the static base URI if it is relative and there is one (with
   * {@link URI#resolve(URI)}), and looked up in the {@link CollationRegistry}.
   *
   * @param sUri
   *        The collation URI as written.
   * @return The collation, never <code>null</code> ({@link CollationRegistry#CODEPOINT} for the codepoint
   *         collation).
   * @throws XPathException
   *         FOCH0002 for a syntactically invalid or unsupported collation URI.
   */
  @NonNull
  public ICollation resolveCollation (@NonNull final String sUri)
  {
    // An absolute URI: resolving it changes nothing
    if (CollationRegistry.CODEPOINT_URI.equals (sUri))
      return CollationRegistry.CODEPOINT;
    String sAbsUri = sUri;
    try
    {
      final URI aUri = new URI (sUri);
      if (!aUri.isAbsolute ())
      {
        final String sBase = m_sBaseUri;
        if (sBase != null)
          sAbsUri = new URI (sBase).resolve (aUri).toString ();
      }
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      throw XPathException.of ("FOCH0002", "Invalid collation URI '" + sUri + "'");
    }
    final ICollation ret = CollationRegistry.getCollation (sAbsUri);
    if (ret == null)
      throw XPathException.of ("FOCH0002", "Unsupported collation '" + sUri + "'");
    return ret;
  }

  /**
   * The default collation resolved with {@link #resolveCollation(String)}. The first successful resolution is kept
   * until the default collation or the base URI is set again, so that functions which use the default collation do
   * not parse its URI on every call; a failure is not kept and raises the error again on every call. Safe for
   * concurrent readers once the context is filled (the kept value is published through a volatile field, and two
   * threads that resolve at the same time get equal collations).
   *
   * @return The default collation, never <code>null</code> ({@link CollationRegistry#CODEPOINT} for the codepoint
   *         collation).
   * @throws XPathException
   *         FOCH0002 for a syntactically invalid or unsupported default collation URI.
   */
  @NonNull
  public ICollation getResolvedDefaultCollation ()
  {
    ICollation ret = m_aResolvedDefaultCollation;
    if (ret == null)
    {
      ret = resolveCollation (m_sDefaultCollation);
      m_aResolvedDefaultCollation = ret;
    }
    return ret;
  }

  /**
   * Get the default decimal format.
   *
   * @return The unnamed decimal format used by fn:format-number.
   */
  @NonNull
  public FormatDecimalFormat getDefaultDecimalFormat ()
  {
    return m_aDefaultDecimalFormat;
  }

  /**
   * Set the unnamed decimal format.
   *
   * @param aFormat
   *        The decimal format. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setDefaultDecimalFormat (@NonNull final FormatDecimalFormat aFormat)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_aDefaultDecimalFormat = ValueEnforcer.notNull (aFormat, "Format");
    return this;
  }

  /**
   * Look up a named decimal format.
   *
   * @param aName
   *        Expanded name of a decimal format.
   * @return The named decimal format or <code>null</code> if there is none.
   */
  @Nullable
  public FormatDecimalFormat getDecimalFormat (@NonNull final XdmQName aName)
  {
    return m_aDecimalFormats.get (aName);
  }

  /**
   * Declare or replace a named decimal format (e.g. an XSLT
   * <code>xsl:decimal-format</code>).
   *
   * @param aName
   *        Expanded name.
   * @param aFormat
   *        The format.
   * @return this for chaining
   */
  @NonNull
  public StaticContext addDecimalFormat (@NonNull final XdmQName aName, @NonNull final FormatDecimalFormat aFormat)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (aFormat, "Format");
    m_aDecimalFormats.put (aName, aFormat);
    return this;
  }

  /**
   * Get the XML version.
   *
   * @return The XML version whose rules apply where XML 1.0 and 1.1 differ.
   *         Default is {@link #DEFAULT_XML_VERSION}.
   */
  @NonNull
  public EXmlVersion getXmlVersion ()
  {
    return m_eXmlVersion;
  }

  /**
   * Set the XML version whose rules apply where XML 1.0 and 1.1 differ.
   *
   * @param eXmlVersion
   *        The XML version. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setXmlVersion (@NonNull final EXmlVersion eXmlVersion)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_eXmlVersion = ValueEnforcer.notNull (eXmlVersion, "XmlVersion");
    return this;
  }

  /**
   * Get the XSD datatype version.
   *
   * @return The XSD datatype version whose rules apply where XSD 1.0 and 1.1
   *         differ. Default is {@link #DEFAULT_XSD_VERSION}.
   */
  @NonNull
  public EXsdVersion getXsdVersion ()
  {
    return m_eXsdVersion;
  }

  /**
   * Check whether the XSD version was set explicitly.
   *
   * @return <code>true</code> if {@link #setXsdVersion(EXsdVersion)} was
   *         called, <code>false</code> if the default applies.
   */
  public boolean isXsdVersionSet ()
  {
    return m_bXsdVersionSet;
  }

  /**
   * Set the XSD datatype version whose rules apply where XSD 1.0 and 1.1
   * differ.
   *
   * @param eXsdVersion
   *        The XSD version. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public StaticContext setXsdVersion (@NonNull final EXsdVersion eXsdVersion)
  {
    // Any change of a run-time field ends the run-time equivalence with other contexts
    m_aRunTimeIdentity = null;
    m_eXsdVersion = ValueEnforcer.notNull (eXsdVersion, "XsdVersion");
    m_bXsdVersionSet = true;
    return this;
  }

  /**
   * Give this context the run-time identity that all contexts with the same run-time fields
   * interned in the same map share (see {@link #isRunTimeEquivalent(StaticContext)}). Call it when
   * the context is filled; any later change of a run-time field drops the identity again, so a
   * changed context is only equivalent to itself.
   *
   * @param aIdentities
   *        The map the host keeps for one compilation (key to identity). Filled by this method.
   * @return this for chaining
   */
  @NonNull
  public StaticContext internRunTimeIdentity (@NonNull final Map <Object, Object> aIdentities)
  {
    ValueEnforcer.notNull (aIdentities, "Identities");
    // Copies of the maps, so that a later change of this context cannot change the shared key
    final RunTimeKey aKey = new RunTimeKey (new HashMap <> (m_aNamespaces),
                                            m_sDefaultElementNamespace,
                                            m_sInScopeDefaultNamespace,
                                            m_sDefaultFunctionNamespace,
                                            m_sBaseUri,
                                            m_aFunctions,
                                            m_bXPath10Compatibility,
                                            m_sUnknownFunctionErrorCode,
                                            m_bStaticTyping,
                                            m_aContextItemStaticType,
                                            m_sDefaultCollation,
                                            m_aDefaultDecimalFormat,
                                            new HashMap <> (m_aDecimalFormats),
                                            m_eXmlVersion,
                                            m_eXsdVersion,
                                            m_bXsdVersionSet,
                                            m_aSchemaSet);
    m_aRunTimeIdentity = aIdentities.computeIfAbsent (aKey, k -> k);
    return this;
  }

  /**
   * Two static contexts are equivalent at run time if they are the same or were interned into the
   * same map with the same run-time fields and not changed since (see
   * {@link #internRunTimeIdentity(Map)}). Evaluation gives the same results with either of them, as
   * evaluation reads no declared variables from the static context, so a dynamic context can keep
   * its static context for an expression compiled with an equivalent one
   * ({@link DynamicContext#withStaticContext(StaticContext)}).
   *
   * @param aOther
   *        The other context. May be <code>null</code>.
   * @return <code>true</code> if they are equivalent at run time.
   */
  public boolean isRunTimeEquivalent (@Nullable final StaticContext aOther)
  {
    if (aOther == this)
      return true;
    return aOther != null && m_aRunTimeIdentity != null && m_aRunTimeIdentity == aOther.m_aRunTimeIdentity;
  }
}

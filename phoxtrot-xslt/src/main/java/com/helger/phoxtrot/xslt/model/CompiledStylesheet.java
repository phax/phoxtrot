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

import java.io.OutputStream;
import java.io.Writer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.context.VariableSlot;
import com.helger.phoxtrot.xpath.fn.FormatDecimalFormat;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltEvaluation;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.phoxtrot.xslt.XsltTransformResult;
import com.helger.phoxtrot.xslt.compile.StaticContexts;
import com.helger.phoxtrot.xslt.pkg.PackageModel;
import com.helger.phoxtrot.xslt.pkg.PackageUnit;
import com.helger.phoxtrot.xslt.runtime.XsltTransformer;
import com.helger.phoxtrot.xslt.stream.StreamVerdicts;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslModule;

/**
 * A compiled stylesheet. Built by the compiler; after compilation it is not
 * modified any more and can be used by several transformations at once, if it
 * is handed to their threads safely (see {@link XsltExecutable}).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class CompiledStylesheet implements XsltExecutable
{
  /**
   * The global context item declaration (xsl:global-context-item).
   *
   * @param as
   *        The declared type of the global context item. May be
   *        <code>null</code>.
   * @param use
   *        The value of the <code>use</code> attribute. Never
   *        <code>null</code>.
   */
  @Immutable
  public record GlobalContextItem (@Nullable SeqType as, @NonNull String use)
  {}

  /**
   * What an expression written on the principal stylesheet element sees:
   * the element (namespaces, base URI, xpath-default-namespace, default
   * collation), the function library, the visible global variables and the
   * decimal formats.
   *
   * @param element
   *        The principal stylesheet element. Never <code>null</code>.
   * @param functions
   *        The function library. Never <code>null</code>.
   * @param variables
   *        The names of the visible global variables. Never
   *        <code>null</code>.
   * @param defaultDecimalFormat
   *        The default decimal format. May be <code>null</code>.
   * @param decimalFormats
   *        The named decimal formats. Never <code>null</code>.
   */
  @Immutable
  public record HostScope (@NonNull XslElement element,
                           @NonNull FunctionLibrary functions,
                           @NonNull ICommonsList <XdmQName> variables,
                           @Nullable FormatDecimalFormat defaultDecimalFormat,
                           @NonNull ICommonsMap <XdmQName, FormatDecimalFormat> decimalFormats)
  {}

  private final FunctionLibrary m_aFunctions;
  private final XslModule m_aPrincipalModule;
  private final ICommonsList <Template> m_aTemplates = new CommonsArrayList <> ();
  private final ICommonsMap <XdmQName, Template> m_aNamedTemplates = new CommonsHashMap <> ();
  private final ICommonsOrderedMap <XdmQName, Mode> m_aModes = new CommonsLinkedHashMap <> ();
  private XdmQName m_aDefaultMode = Mode.UNNAMED;
  private final ICommonsOrderedMap <XdmQName, VariableDef> m_aGlobals = new CommonsLinkedHashMap <> ();
  private final ICommonsMap <XdmQName, XdmSequence> m_aStaticValues = new CommonsHashMap <> ();
  private final ICommonsList <UserFunction> m_aFunctionsList = new CommonsArrayList <> ();
  private final ICommonsMap <XdmQName, ICommonsList <KeyDefinition>> m_aKeys = new CommonsHashMap <> ();
  private final OutputDefinition m_aOutput = new OutputDefinition (null);
  private final ICommonsMap <XdmQName, OutputDefinition> m_aNamedOutputs = new CommonsHashMap <> ();
  private final ICommonsMap <XdmQName, AttributeSet> m_aAttributeSets = new CommonsHashMap <> ();
  private final SpaceRules m_aSpaceRules = new SpaceRules ();
  private GlobalContextItem m_aGlobalContextItem;
  private final ICommonsMap <Object, Object> m_aExtensionData = new CommonsHashMap <> ();
  private final ICommonsList <XslModule> m_aModules = new CommonsArrayList <> ();
  private boolean m_bPackage;
  private IXdmSchemaSet m_aSchemaSet;
  private boolean m_bStripInputTypeAnnotations;
  private final ICommonsSet <XdmQName> m_aExposedTemplates = new CommonsHashSet <> ();
  private final ICommonsSet <XdmQName> m_aPrivateModes = new CommonsHashSet <> ();
  private final ICommonsSet <XdmQName> m_aPrivateTemplates = new CommonsHashSet <> ();
  private final ICommonsSet <XdmQName> m_aExposedModes = new CommonsHashSet <> ();
  private PackageModel m_aPackages;
  private final ICommonsMap <VariableDef, VariableDef> m_aOriginalVariables = new CommonsHashMap <> ();
  private final ICommonsMap <VariableDef, VariableSlot> m_aOriginalVariableSlots = new CommonsHashMap <> ();
  private final ICommonsMap <PackageUnit, ICommonsMap <XdmQName, VariableDef>> m_aPackageGlobals = new CommonsHashMap <> ();
  private HostScope m_aHostScope;
  private StreamVerdicts m_aStreamVerdicts;
  private boolean m_bHasResultDocument;

  /**
   * Create a new, empty compiled stylesheet. The compiler fills it.
   *
   * @param aFunctions
   *        The function library of the stylesheet. May not be
   *        <code>null</code>.
   * @param aPrincipalModule
   *        The principal stylesheet module. May not be <code>null</code>.
   */
  public CompiledStylesheet (@NonNull final FunctionLibrary aFunctions, @NonNull final XslModule aPrincipalModule)
  {
    m_aFunctions = aFunctions;
    m_aPrincipalModule = aPrincipalModule;
  }

  /**
   * Get the function library of the stylesheet.
   *
   * @return The function library of the stylesheet (built-in, XSLT and
   *         stylesheet functions).
   */
  @NonNull
  public FunctionLibrary getFunctionLibrary ()
  {
    return m_aFunctions;
  }

  /**
   * Get the principal stylesheet module.
   *
   * @return The principal module, never <code>null</code>.
   */
  @NonNull
  public XslModule getPrincipalModule ()
  {
    return m_aPrincipalModule;
  }

  /**
   * Check whether the stylesheet is schema-aware, i.e. whether a schema set
   * is present.
   *
   * @return <code>true</code> if the stylesheet was compiled schema-aware.
   */
  public boolean isSchemaAware ()
  {
    return m_aSchemaSet != null;
  }

  /**
   * @return The in-scope schema definitions of a schema-aware stylesheet (the
   *         imported schemas and the built-in types), <code>null</code> for a
   *         basic processor.
   */
  @Override
  @Nullable
  public IXdmSchemaSet getSchemaSet ()
  {
    return m_aSchemaSet;
  }

  /**
   * Set the in-scope schema definitions.
   *
   * @param aSchemaSet
   *        The schema set, <code>null</code> for a basic processor.
   */
  public void setSchemaSet (@Nullable final IXdmSchemaSet aSchemaSet)
  {
    m_aSchemaSet = aSchemaSet;
  }

  /**
   * Get the results of the streamability analysis.
   *
   * @return What the streamability analysis proved guaranteed streamable, or
   *         <code>null</code> if nothing is executed streamed (streaming is not
   *         claimed, {@link com.helger.phoxtrot.xslt.XsltCompileOptions#isStreamingClaim()}).
   */
  @Nullable
  public StreamVerdicts getStreamVerdicts ()
  {
    return m_aStreamVerdicts;
  }

  /**
   * Set the results of the streamability analysis.
   *
   * @param aStreamVerdicts
   *        The verdicts. May be <code>null</code> if nothing is executed
   *        streamed.
   */
  public void setStreamVerdicts (@Nullable final StreamVerdicts aStreamVerdicts)
  {
    m_aStreamVerdicts = aStreamVerdicts;
  }

  /**
   * Check whether any module contains an xsl:result-document.
   *
   * @return <code>true</code> if any module has an xsl:result-document, which
   *         may write the principal result with another output definition.
   *         Determined once at the end of the compilation.
   */
  public boolean hasResultDocument ()
  {
    return m_bHasResultDocument;
  }

  /**
   * Set whether any module contains an xsl:result-document.
   *
   * @param bHasResultDocument
   *        <code>true</code> if any module has an xsl:result-document.
   */
  public void setHasResultDocument (final boolean bHasResultDocument)
  {
    m_bHasResultDocument = bHasResultDocument;
  }

  /**
   * @return <code>true</code> if a module says
   *         <code>input-type-annotations="strip"</code>.
   */
  @Override
  public boolean isStripInputTypeAnnotations ()
  {
    return m_bStripInputTypeAnnotations;
  }

  /**
   * Set whether input type annotations are stripped.
   *
   * @param bStrip
   *        <code>true</code> if a module says
   *        <code>input-type-annotations="strip"</code>.
   */
  public void setStripInputTypeAnnotations (final boolean bStrip)
  {
    m_bStripInputTypeAnnotations = bStrip;
  }

  /**
   * Check whether the principal module is an xsl:package.
   *
   * @return <code>true</code> if the principal module is an xsl:package (named
   *         templates and modes are then private unless declared otherwise).
   */
  public boolean isPackage ()
  {
    return m_bPackage;
  }

  /**
   * Set whether the principal module is an xsl:package.
   *
   * @param bPackage
   *        <code>true</code> if the principal module is an xsl:package.
   */
  public void setPackage (final boolean bPackage)
  {
    m_bPackage = bPackage;
  }

  /**
   * Get the packages of the stylesheet.
   *
   * @return The packages of the stylesheet, <code>null</code> before
   *         compilation finished.
   */
  @Nullable
  public PackageModel getPackages ()
  {
    return m_aPackages;
  }

  /**
   * Set the packages of the stylesheet.
   *
   * @param aPackages
   *        The package model. May be <code>null</code>.
   */
  public void setPackages (@Nullable final PackageModel aPackages)
  {
    m_aPackages = aPackages;
  }

  /**
   * Get the overriding global variables and parameters (mutable, filled
   * during compilation).
   *
   * @return The global variables and parameters declared in xsl:override,
   *         mapped to the declaration they override ($xsl:original).
   */
  @NonNull
  public ICommonsMap <VariableDef, VariableDef> originalVariables ()
  {
    return m_aOriginalVariables;
  }

  /**
   * Get the slots of $xsl:original (mutable, filled during compilation).
   *
   * @return The slot $xsl:original is bound in, for every key of
   *         {@link #originalVariables()}.
   */
  @NonNull
  public ICommonsMap <VariableDef, VariableSlot> originalVariableSlots ()
  {
    return m_aOriginalVariableSlots;
  }

  /**
   * Get the package specific global variables (mutable, filled during
   * compilation).
   *
   * @return Per package, the global variables for which the package sees
   *         another declaration than the one with the highest import
   *         precedence.
   */
  @NonNull
  public ICommonsMap <PackageUnit, ICommonsMap <XdmQName, VariableDef>> packageGlobals ()
  {
    return m_aPackageGlobals;
  }

  /**
   * Check whether more than one package takes part in the stylesheet.
   *
   * @return <code>true</code> if more than one package takes part.
   */
  public boolean isMultiPackage ()
  {
    return m_aPackages != null && m_aPackages.isMultiPackage ();
  }

  /**
   * Get the named templates made public by xsl:expose (mutable, filled during
   * compilation).
   *
   * @return The names of the named templates an xsl:expose makes public (a
   *         <code>null</code> element stands for all).
   */
  @NonNull
  public ICommonsSet <XdmQName> exposedTemplates ()
  {
    return m_aExposedTemplates;
  }

  /**
   * Get the named templates made private by xsl:expose (mutable, filled
   * during compilation).
   *
   * @return The named templates an xsl:expose naming them explicitly makes
   *         private, which takes precedence over their declared visibility
   *         (not eligible as initial template).
   */
  @NonNull
  public ICommonsSet <XdmQName> privateTemplates ()
  {
    return m_aPrivateTemplates;
  }

  /**
   * Get the modes made public or final by xsl:expose (mutable, filled during
   * compilation).
   *
   * @return The modes an xsl:expose makes public or final.
   */
  @NonNull
  public ICommonsSet <XdmQName> exposedModes ()
  {
    return m_aExposedModes;
  }

  /**
   * Get the modes made private by xsl:expose (mutable, filled during
   * compilation).
   *
   * @return The modes an xsl:expose makes private (not eligible as initial
   *         mode).
   */
  @NonNull
  public ICommonsSet <XdmQName> privateModes ()
  {
    return m_aPrivateModes;
  }

  /**
   * Get all stylesheet modules (mutable, filled during compilation).
   *
   * @return All stylesheet modules (for document('')).
   */
  @NonNull
  public ICommonsList <XslModule> modules ()
  {
    return m_aModules;
  }

  /**
   * Add a template to the list of all templates.
   *
   * @param aTemplate
   *        The template to add. May not be <code>null</code>.
   */
  public void addTemplate (@NonNull final Template aTemplate)
  {
    m_aTemplates.add (aTemplate);
  }

  /**
   * Get all templates of the stylesheet.
   *
   * @return All templates in the order they were added (do not modify).
   */
  @NonNull
  public ICommonsList <Template> templates ()
  {
    return m_aTemplates;
  }

  /**
   * Get the named templates by name (mutable, filled during compilation).
   *
   * @return The named templates (the winning one of each name).
   */
  @NonNull
  public ICommonsMap <XdmQName, Template> namedTemplates ()
  {
    return m_aNamedTemplates;
  }

  /**
   * Look up a named template.
   *
   * @param aName
   *        Template name.
   * @return The winning named template of that name, or <code>null</code>.
   */
  @Nullable
  public Template getNamedTemplate (@NonNull final XdmQName aName)
  {
    return m_aNamedTemplates.get (aName);
  }

  /**
   * Get a mode, creating it if it does not exist yet.
   *
   * @param aName
   *        Mode name ({@link Mode#UNNAMED} for the unnamed mode).
   * @return The mode, created if needed (during compilation only).
   */
  @NonNull
  public Mode getOrCreateMode (@NonNull final XdmQName aName)
  {
    return m_aModes.computeIfAbsent (aName, Mode::new);
  }

  /**
   * Look up an existing mode.
   *
   * @param aName
   *        Mode name ({@link Mode#UNNAMED} for the unnamed mode).
   * @return The mode, or <code>null</code> if no template rule or xsl:mode
   *         declaration introduced it.
   */
  @Nullable
  public Mode getMode (@NonNull final XdmQName aName)
  {
    return m_aModes.get (aName);
  }

  /**
   * Get all modes by name, in the order they were created (mutable, filled
   * during compilation).
   *
   * @return The modes by name, never <code>null</code>.
   */
  @NonNull
  public ICommonsOrderedMap <XdmQName, Mode> modes ()
  {
    return m_aModes;
  }

  /**
   * Get the name of the default mode.
   *
   * @return The default mode of the principal module ([xsl:]default-mode).
   */
  @NonNull
  public XdmQName getDefaultModeName ()
  {
    return m_aDefaultMode;
  }

  /**
   * Set the name of the default mode.
   *
   * @param aName
   *        The default mode name. May not be <code>null</code>.
   */
  public void setDefaultModeName (@NonNull final XdmQName aName)
  {
    m_aDefaultMode = aName;
  }

  /**
   * Get the global variables and parameters (mutable, filled during
   * compilation).
   *
   * @return The global variables and parameters by name (the winning
   *         declaration of each name).
   */
  @NonNull
  public ICommonsOrderedMap <XdmQName, VariableDef> globals ()
  {
    return m_aGlobals;
  }

  /**
   * Get the values of the static variables and parameters (mutable, filled
   * during compilation).
   *
   * @return The values of static variables and parameters, settled at compile
   *         time.
   */
  @NonNull
  public ICommonsMap <XdmQName, XdmSequence> staticValues ()
  {
    return m_aStaticValues;
  }

  /**
   * Get all user functions of the stylesheet (mutable, filled during
   * compilation).
   *
   * @return The user functions, never <code>null</code>.
   */
  @NonNull
  public ICommonsList <UserFunction> functions ()
  {
    return m_aFunctionsList;
  }

  /**
   * Look up the declarations of a key.
   *
   * @param aName
   *        Key name.
   * @return The declarations of that key, or <code>null</code>.
   */
  @Nullable
  public ICommonsList <KeyDefinition> getKey (@NonNull final XdmQName aName)
  {
    return m_aKeys.get (aName);
  }

  /**
   * Append a key declaration to the list of its name.
   *
   * @param aKey
   *        The declaration.
   */
  public void addKey (@NonNull final KeyDefinition aKey)
  {
    m_aKeys.computeIfAbsent (aKey.getName (), k -> new CommonsArrayList <> ()).add (aKey);
  }

  /**
   * Get all key declarations by key name.
   *
   * @return The key declarations by name, never <code>null</code>.
   */
  @NonNull
  public ICommonsMap <XdmQName, ICommonsList <KeyDefinition>> keys ()
  {
    return m_aKeys;
  }

  /**
   * Get the unnamed output definition.
   *
   * @return The unnamed output definition.
   */
  @NonNull
  public OutputDefinition getOutput ()
  {
    return m_aOutput;
  }

  /**
   * Get a named output definition, creating it if it does not exist yet.
   *
   * @param aName
   *        Name.
   * @return The named output definition, created if needed (during
   *         compilation only).
   */
  @NonNull
  public OutputDefinition getOrCreateNamedOutput (@NonNull final XdmQName aName)
  {
    return m_aNamedOutputs.computeIfAbsent (aName, OutputDefinition::new);
  }

  /**
   * Look up a named output definition.
   *
   * @param aName
   *        The output definition name. May not be <code>null</code>.
   * @return The named output definition, or <code>null</code> if there is
   *         none of that name.
   */
  @Nullable
  public OutputDefinition getNamedOutput (@NonNull final XdmQName aName)
  {
    return m_aNamedOutputs.get (aName);
  }

  /**
   * Get all attribute sets by name (mutable, filled during compilation).
   *
   * @return The attribute sets by name, never <code>null</code>.
   */
  @NonNull
  public ICommonsMap <XdmQName, AttributeSet> attributeSets ()
  {
    return m_aAttributeSets;
  }

  /**
   * Look up an attribute set.
   *
   * @param aName
   *        The attribute set name. May not be <code>null</code>.
   * @return The attribute set, or <code>null</code> if there is none of that
   *         name.
   */
  @Nullable
  public AttributeSet getAttributeSet (@NonNull final XdmQName aName)
  {
    return m_aAttributeSets.get (aName);
  }

  /**
   * Get the whitespace stripping rules (xsl:strip-space and
   * xsl:preserve-space).
   *
   * @return The space rules, never <code>null</code>.
   */
  @NonNull
  public SpaceRules getSpaceRules ()
  {
    return m_aSpaceRules;
  }

  /**
   * Get the global context item declaration.
   *
   * @return The declaration, or <code>null</code> if there is none.
   */
  @Nullable
  public GlobalContextItem getGlobalContextItem ()
  {
    return m_aGlobalContextItem;
  }

  /**
   * Set the global context item declaration.
   *
   * @param aDecl
   *        The declaration. May be <code>null</code>.
   */
  public void setGlobalContextItem (@Nullable final GlobalContextItem aDecl)
  {
    m_aGlobalContextItem = aDecl;
  }

  /**
   * Compile-time data of instruction families (accumulators, character maps,
   * ...). Write it only during compilation.
   *
   * @param aKey
   *        Key, compared with equals.
   * @return The value or <code>null</code>.
   */
  @Nullable
  public Object getExtensionData (@NonNull final Object aKey)
  {
    return m_aExtensionData.get (aKey);
  }

  /**
   * Store compile-time data of an instruction family. Call it only during
   * compilation.
   *
   * @param aKey
   *        Key, compared with equals. May not be <code>null</code>.
   * @param aValue
   *        The value. May be <code>null</code>.
   */
  public void putExtensionData (@NonNull final Object aKey, @Nullable final Object aValue)
  {
    m_aExtensionData.put (aKey, aValue);
  }

  /**
   * Get all user functions with a given name (all arities).
   *
   * @param aName
   *        The function name. May not be <code>null</code>.
   * @return All user functions with the given name.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <UserFunction> getFunctionsByName (@NonNull final XdmQName aName)
  {
    return m_aFunctionsList.getAll (x -> x.getName ().equals (aName));
  }

  /**
   * Set by the compiler for {@link #createStaticContext()}.
   *
   * @param aHostScope
   *        The scope of the principal stylesheet element. May be
   *        <code>null</code>.
   */
  public void setHostScope (@Nullable final HostScope aHostScope)
  {
    m_aHostScope = aHostScope;
  }

  /**
   * {@inheritDoc} Each call runs a new {@link XsltTransformer}.
   */
  @NonNull
  public XsltTransformResult transform (@NonNull final XsltTransformRequest aRequest)
  {
    return new XsltTransformer (this, aRequest).run ();
  }

  /**
   * {@inheritDoc} Each call runs a new {@link XsltTransformer}.
   */
  @Override
  @NonNull
  public XsltTransformResult transform (@NonNull final XsltTransformRequest aRequest, @NonNull final OutputStream aOS)
  {
    return new XsltTransformer (this, aRequest).runTo (aOS);
  }

  /**
   * {@inheritDoc} Each call runs a new {@link XsltTransformer}.
   */
  @Override
  @NonNull
  public XsltTransformResult transform (@NonNull final XsltTransformRequest aRequest, @NonNull final Writer aWriter)
  {
    return new XsltTransformer (this, aRequest).runTo (aWriter);
  }

  /**
   * {@inheritDoc} Each call runs a new {@link XsltTransformer}.
   */
  @Override
  @NonNull
  public XsltTransformResult transform (@NonNull final XsltTransformRequest aRequest,
                                        @NonNull final ContentHandler aHandler,
                                        @Nullable final LexicalHandler aLexicalHandler) throws SAXException
  {
    ValueEnforcer.notNull (aHandler, "Handler");
    return new XsltTransformer (this, aRequest).runTo (aHandler, aLexicalHandler);
  }

  /**
   * {@inheritDoc} The context is built from the host scope the compiler set
   * ({@link #setHostScope(HostScope)}), plus the schema set; without a host
   * scope only the function library and the schema set are set.
   */
  @Override
  @NonNull
  public StaticContext createStaticContext ()
  {
    if (m_aHostScope == null)
      return new StaticContext ().setFunctionLibrary (m_aFunctions).setSchemaSet (m_aSchemaSet);
    return StaticContexts.create (m_aHostScope.element (),
                                  m_aHostScope.functions (),
                                  m_aHostScope.variables (),
                                  m_aHostScope.defaultDecimalFormat (),
                                  m_aHostScope.decimalFormats ())
                         .setSchemaSet (m_aSchemaSet);
  }

  /**
   * {@inheritDoc} This implementation never throws
   * UnsupportedOperationException.
   */
  @Override
  @NonNull
  public XsltEvaluation createEvaluation (@NonNull final XsltTransformRequest aRequest)
  {
    return new XsltEvaluation (this, aRequest);
  }
}

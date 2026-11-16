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
package com.helger.phoxtrot.xdm.schema;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;

/**
 * Entry point to schema processing (the schema component model and validation of XDM trees), which
 * is the basis of schema-awareness. It is backed by the native validator
 * (<code>com.helger.phoxtrot.xsd.XsdSchemaProvider</code> of the optional module
 * <code>phoxtrot-xsd</code>), loaded by name only if present. When it is not on the class path,
 * {@link #isAvailable()} is <code>false</code>. The JDK's own validator is available separately and
 * with less information, as {@link com.helger.phoxtrot.xdm.jaxp.JdkSchemaProvider}.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XdmSchemaSupport
{
  /** The native provider (module phoxtrot-xsd), only loaded when present. */
  private static final String NATIVE_PROVIDER_CLASS = "com.helger.phoxtrot.xsd.XsdSchemaProvider";

  private static final IXdmSchemaProvider PROVIDER = _loadNativeProvider (XdmSchemaSupport.class.getClassLoader ());

  @PresentForCodeCoverage
  private static final XdmSchemaSupport INSTANCE = new XdmSchemaSupport ();

  private XdmSchemaSupport ()
  {}

  /**
   * Instantiate the native provider by name through its public no-argument constructor.
   *
   * @param aLoader
   *        The class loader to load it with.
   * @return The provider, or <code>null</code> if it is not on the class path or cannot be created.
   */
  @Nullable
  private static IXdmSchemaProvider _loadNativeProvider (@NonNull final ClassLoader aLoader)
  {
    try
    {
      return (IXdmSchemaProvider) Class.forName (NATIVE_PROVIDER_CLASS, true, aLoader)
                                       .getDeclaredConstructor ()
                                       .newInstance ();
    }
    catch (final ReflectiveOperationException | LinkageError ex)
    {
      return null;
    }
  }

  /**
   * @return <code>true</code> if schema processing is available, i.e. the native validator
   *         (<code>phoxtrot-xsd</code>) is on the class path.
   */
  public static boolean isAvailable ()
  {
    return PROVIDER != null;
  }

  /**
   * @return The provider in use.
   * @throws IllegalStateException
   *         if schema processing is not available
   */
  @NonNull
  private static IXdmSchemaProvider _getProvider ()
  {
    if (PROVIDER == null)
      throw new IllegalStateException ("Schema processing needs the native validator (phoxtrot-xsd) on the class path");
    return PROVIDER;
  }

  /**
   * @return The simple class name of the provider in use, <code>XsdSchemaProvider</code>;
   *         <code>null</code> if schema processing is not
   *         available.
   */
  @Nullable
  public static String getProviderName ()
  {
    return PROVIDER == null ? null : PROVIDER.getClass ().getSimpleName ();
  }

  /**
   * Compile schema documents into one schema set.
   *
   * @param aSources
   *        The schema documents. May not be <code>null</code>.
   * @return The schema set.
   * @throws XdmSchemaException
   *         if the documents cannot be read or are not a valid schema
   * @throws IllegalStateException
   *         if schema processing is not available
   */
  @NonNull
  public static IXdmSchemaSet compile (@NonNull final List <XdmSchemaSource> aSources) throws XdmSchemaException
  {
    ValueEnforcer.notNull (aSources, "Sources");
    return _getProvider ().compile (aSources);
  }

  /**
   * Compile schema documents into one schema set, reading referenced documents only if allowed
   * (see {@link IXdmSchemaProvider#compile(List, boolean)}).
   *
   * @param aSources
   *        The schema documents. May not be <code>null</code>.
   * @param bReadReferencedFiles
   *        <code>true</code> to read referenced documents from local files, <code>false</code> to
   *        read none.
   * @return The schema set.
   * @throws XdmSchemaException
   *         if the documents cannot be read or are not a valid schema
   * @throws IllegalStateException
   *         if schema processing is not available
   */
  @NonNull
  public static IXdmSchemaSet compile (@NonNull final List <XdmSchemaSource> aSources,
                                       final boolean bReadReferencedFiles) throws XdmSchemaException
  {
    ValueEnforcer.notNull (aSources, "Sources");
    return _getProvider ().compile (aSources, bReadReferencedFiles);
  }

  /**
   * @return The schema set with only the built-in types.
   * @throws IllegalStateException
   *         if schema processing is not available
   */
  @NonNull
  public static IXdmSchemaSet getBuiltinSchemaSet ()
  {
    try
    {
      return _getProvider ().compile (new CommonsArrayList <> ());
    }
    catch (final XdmSchemaException ex)
    {
      throw new IllegalStateException ("Failed to create the built-in schema set", ex);
    }
  }
}

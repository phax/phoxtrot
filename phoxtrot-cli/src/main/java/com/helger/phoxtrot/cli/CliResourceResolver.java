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
package com.helger.phoxtrot.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xpath.context.ResourceUri;
import com.helger.phoxtrot.xslt.compile.FileResourceResolver;

/**
 * The resolver of the command line client for stylesheet modules (include, import, packages,
 * schema locations) and the resources a run reads (<code>doc()</code>, <code>document()</code>,
 * <code>unparsed-text()</code>, <code>json-doc()</code>). It reads local files only, never the
 * network, and without external entities. Unless file access is unrestricted, a file must be inside
 * one of the allowed folders; a refused access is reported as warning and the resource is then
 * unavailable (the engine raises its error for that).
 *
 * @author Philip Helger
 */
@ThreadSafe
final class CliResourceResolver implements IResourceResolver
{
  /** The resolver that does the reading. */
  private static final FileResourceResolver DELEGATE = FileResourceResolver.INSTANCE;

  /** The allowed folders as real paths; <code>null</code> for any local file. */
  private final ICommonsList <Path> m_aAllowedDirs;
  /** Gets the warnings about refused accesses. */
  private final CliLogHandler m_aLog;

  /**
   * Constructor.
   *
   * @param aAllowedDirs
   *        The folders files may be read from, <code>null</code> for any local file. Folders that
   *        do not exist are ignored.
   * @param aLog
   *        The log handler for refused accesses. May not be <code>null</code>.
   */
  CliResourceResolver (@Nullable final List <Path> aAllowedDirs, @NonNull final CliLogHandler aLog)
  {
    ValueEnforcer.notNull (aLog, "Log");
    if (aAllowedDirs == null)
      m_aAllowedDirs = null;
    else
    {
      m_aAllowedDirs = new CommonsArrayList <> ();
      for (final Path aDir : aAllowedDirs)
        try
        {
          m_aAllowedDirs.add (aDir.toRealPath ());
        }
        catch (final IOException ex)
        {
          // Not existing: allows nothing
        }
    }
    m_aLog = aLog;
  }

  /**
   * Check if the URI may be read. Only <code>file:</code> URIs of existing regular files qualify,
   * and, unless access is unrestricted, only below an allowed folder (compared as real paths, so a
   * symbolic link cannot lead outside).
   *
   * @param aUri
   *        The URI to check. May not be <code>null</code>.
   * @return <code>true</code> if it may be read.
   */
  private boolean _isAllowed (@NonNull final ResourceUri aUri)
  {
    final Path aPath = FileResourceResolver.getLocalPath (aUri.absoluteUri ());
    if (aPath == null)
    {
      m_aLog.warn ("The resource '" + aUri.absoluteUri () + "' is not available: only existing local files can be read");
      return false;
    }
    // Fast path: unrestricted file access
    if (m_aAllowedDirs == null)
      return true;
    try
    {
      final Path aReal = aPath.toRealPath ();
      for (final Path aDir : m_aAllowedDirs)
        if (aReal.startsWith (aDir))
          return true;
    }
    catch (final IOException ex)
    {
      // Fall through to the refusal
    }
    m_aLog.warn ("Access to '" +
                 aPath +
                 "' is refused: it is outside the allowed folders (add its folder with --resource-dir, or use --allow-file-access)");
    return false;
  }

  @Nullable
  public IXdmNode resolveDocument (@NonNull final ResourceUri aUri)
  {
    return _isAllowed (aUri) ? DELEGATE.resolveDocument (aUri) : null;
  }

  @Override
  @Nullable
  public IXdmNode resolveStylesheetModule (@NonNull final ResourceUri aUri)
  {
    return _isAllowed (aUri) ? DELEGATE.resolveStylesheetModule (aUri) : null;
  }

  @Override
  @Nullable
  public XdmStreamReader openDocumentStream (@NonNull final ResourceUri aUri,
                                             @Nullable final Predicate <XdmQName> aStripWhitespace) throws XdmParseException
  {
    return _isAllowed (aUri) ? DELEGATE.openDocumentStream (aUri, aStripWhitespace) : null;
  }

  @Nullable
  public String resolveText (@NonNull final ResourceUri aUri, @Nullable final String sEncoding)
  {
    return _isAllowed (aUri) ? DELEGATE.resolveText (aUri, sEncoding) : null;
  }
}

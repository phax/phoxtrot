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

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

import picocli.CommandLine.Option;

/**
 * The file access options shared by the commands that read further files: by default only files in
 * the folders of the files named on the command line and in the <code>--resource-dir</code>
 * folders may be read.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class CliAccessOptions
{
  /** Further folders files may be read from. */
  @Option (names = "--resource-dir",
           paramLabel = "DIR",
           description = "A further folder from which included modules, documents and texts may be read (besides the folders of the files on the command line). May be repeated.")
  List <Path> m_aResourceDirs;

  /** <code>true</code> to allow reading any local file. */
  @Option (names = "--allow-file-access",
           description = "Allow reading any local file (never the network), instead of only files in the folders of the files on the command line and the --resource-dir folders.")
  boolean m_bAllowFileAccess;

  /** Constructor. Called by picocli. */
  CliAccessOptions ()
  {}

  /**
   * Get the folders files may be read from.
   *
   * @param aFiles
   *        The files named on the command line; their folders are allowed. May not be
   *        <code>null</code>, but may contain <code>null</code> entries, which are ignored.
   * @return The allowed folders, <code>null</code> if any local file may be read.
   */
  @Nullable
  ICommonsList <Path> getAllowedDirs (@NonNull final Path... aFiles)
  {
    if (m_bAllowFileAccess)
      return null;
    final ICommonsList <Path> ret = new CommonsArrayList <> ();
    for (final Path aFile : aFiles)
      if (aFile != null)
      {
        final Path aParent = aFile.toAbsolutePath ().getParent ();
        if (aParent != null)
          ret.add (aParent);
      }
    if (m_aResourceDirs != null)
      ret.addAll (m_aResourceDirs);
    return ret;
  }

  /**
   * Create the resolver for the files named on the command line.
   *
   * @param aLog
   *        The log handler for refused accesses. May not be <code>null</code>.
   * @param aFiles
   *        The files named on the command line; their folders are allowed. <code>null</code>
   *        entries are ignored.
   * @return A new resolver. Never <code>null</code>.
   */
  @NonNull
  CliResourceResolver createResolver (@NonNull final CliLogHandler aLog, @NonNull final Path... aFiles)
  {
    return new CliResourceResolver (getAllowedDirs (aFiles), aLog);
  }
}

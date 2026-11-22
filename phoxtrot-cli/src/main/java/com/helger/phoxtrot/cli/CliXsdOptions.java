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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xsd.XsdCompileOptions;
import com.helger.phoxtrot.xsd.XsdError;
import com.helger.phoxtrot.xsd.XsdFileAccess;
import com.helger.phoxtrot.xsd.XsdSchema;

import picocli.CommandLine.Option;

/**
 * The XML Schema options shared by the XSD commands.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class CliXsdOptions
{
  /** The XSD version. */
  @Option (names = "--xsd-version",
           paramLabel = "VERSION",
           defaultValue = "1.0",
           description = "The XML Schema version: 1.0 (default) or 1.1.")
  String m_sVersion;

  /** Constructor. Called by picocli. */
  CliXsdOptions ()
  {}

  /**
   * @return The selected version.
   * @throws IllegalArgumentException
   *         for an unknown version
   */
  @NonNull
  EXsdVersion getVersion ()
  {
    final EXsdVersion ret = EXsdVersion.getFromIDOrNull (m_sVersion);
    if (ret == null)
      throw new IllegalArgumentException ("The XML Schema version '" + m_sVersion + "' is unknown - use 1.0 or 1.1");
    return ret;
  }

  /**
   * Compile the schema documents as one schema.
   *
   * @param aSchemas
   *        The schema documents; the first is the entry document. May not be <code>null</code>.
   * @param aAllowedDirs
   *        The folders referenced schema documents may be read from, <code>null</code> for any
   *        local file.
   * @return The compiled schema. Never <code>null</code>.
   * @throws IOException
   *         if a schema document cannot be read
   */
  @NonNull
  XsdSchema compile (@NonNull final List <Path> aSchemas, @Nullable final ICommonsList <Path> aAllowedDirs) throws IOException
  {
    final XsdFileAccess aAccess = aAllowedDirs == null ? XsdFileAccess.LOCAL_FILES
                                                       : XsdFileAccess.confinedTo (aAllowedDirs.toArray (new Path [0]));
    return XsdSchema.compileFiles (aSchemas, getVersion (), new XsdCompileOptions ().setFileAccess (aAccess));
  }

  /**
   * Get the text of an XML Schema error for the output.
   *
   * @param aFile
   *        The file the error belongs to, <code>null</code> if unknown.
   * @param aError
   *        The error. May not be <code>null</code>.
   * @return <code>file:line:column: code: message (path)</code>, as far as known. Never
   *         <code>null</code>.
   */
  @NonNull
  static String getErrorText (@Nullable final Path aFile, @NonNull final XsdError aError)
  {
    final StringBuilder aSB = new StringBuilder ();
    if (aFile != null)
      aSB.append (aFile).append (':');
    if (aError.line () > 0)
    {
      aSB.append (aError.line ()).append (':');
      if (aError.column () > 0)
        aSB.append (aError.column ()).append (':');
    }
    if (aSB.length () > 0)
      aSB.append (' ');
    aSB.append (aError.toString ());
    if (!aError.path ().isEmpty ())
      aSB.append (" (").append (aError.path ()).append (')');
    return aSB.toString ();
  }
}

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

import java.net.URI;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XmlNames;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xslt.XsltException;

/**
 * Helpers shared by the commands.
 *
 * @author Philip Helger
 */
@Immutable
final class CliHelper
{
  /**
   * A <code>name=value</code> command line argument.
   *
   * @param name
   *        The part before the first equals sign. Never <code>null</code>.
   * @param value
   *        The part after it, <code>null</code> if there is no equals sign.
   */
  record NameValue (@NonNull String name, @Nullable String value)
  {}

  private CliHelper ()
  {}

  /**
   * Split a <code>name=value</code> argument at the first equals sign.
   *
   * @param sArg
   *        The argument. May not be <code>null</code>.
   * @return The parts. Never <code>null</code>.
   */
  @NonNull
  static NameValue splitNameValue (@NonNull final String sArg)
  {
    final int nIdx = sArg.indexOf ('=');
    if (nIdx < 0)
      return new NameValue (sArg.trim (), null);
    return new NameValue (sArg.substring (0, nIdx).trim (), sArg.substring (nIdx + 1));
  }

  /**
   * Parse a name given on the command line: <code>local</code>, <code>prefix:local</code> (with a
   * namespace of the static context), <code>Q{uri}local</code> or <code>{uri}local</code>.
   *
   * @param sName
   *        The name. May not be <code>null</code>.
   * @param aStaticCtx
   *        The static context with the namespaces of prefixed names, <code>null</code> to allow
   *        no prefix.
   * @return The expanded name. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         if the name has none of these forms or its prefix is not declared
   */
  @NonNull
  static XdmQName parseName (@NonNull final String sName, @Nullable final StaticContext aStaticCtx)
  {
    final String s = sName.startsWith ("Q{") ? sName.substring (1) : sName;
    if (s.startsWith ("{"))
    {
      final int nEnd = s.indexOf ('}');
      if (nEnd < 0)
        throw new IllegalArgumentException ("The name '" + sName + "' has no closing brace");
      final String sLocal = s.substring (nEnd + 1);
      if (!XmlNames.isNCName (sLocal))
        throw new IllegalArgumentException ("The local part of the name '" + sName + "' is no NCName");
      return new XdmQName (s.substring (1, nEnd), sLocal);
    }
    final int nColon = sName.indexOf (':');
    if (nColon < 0)
    {
      if (!XmlNames.isNCName (sName))
        throw new IllegalArgumentException ("The name '" + sName + "' is no NCName");
      return new XdmQName (sName);
    }
    final String sPrefix = sName.substring (0, nColon);
    final String sLocal = sName.substring (nColon + 1);
    if (!XmlNames.isNCName (sPrefix) || !XmlNames.isNCName (sLocal))
      throw new IllegalArgumentException ("The name '" + sName + "' is no QName");
    final String sUri = aStaticCtx == null ? null : aStaticCtx.getNamespaceUri (sPrefix);
    if (sUri == null)
      throw new IllegalArgumentException ("The prefix of the name '" + sName + "' is not declared (use --ns " + sPrefix + "=URI)");
    return new XdmQName (sUri, sLocal, sPrefix);
  }

  /**
   * Get a URI of an error location for display: the path of a <code>file:</code> URI, relative to
   * the working folder if it is inside, any other URI as it is.
   *
   * @param sUri
   *        The URI. May be <code>null</code>.
   * @return The text, <code>null</code> if the URI is <code>null</code> or empty.
   */
  @Nullable
  static String getDisplayLocation (@Nullable final String sUri)
  {
    if (sUri == null || sUri.isEmpty ())
      return null;
    if (sUri.startsWith ("file:"))
      try
      {
        final Path aPath = Path.of (URI.create (sUri)).normalize ();
        final Path aWorkDir = Path.of ("").toAbsolutePath ();
        return aPath.startsWith (aWorkDir) ? aWorkDir.relativize (aPath).toString () : aPath.toString ();
      }
      catch (final RuntimeException ex)
      {
        // Show it as it is
      }
    return sUri;
  }

  /**
   * Get the text of an error for the output: the location of a located stylesheet error
   * (<code>module:line:column: </code>) followed by the code and the message.
   *
   * @param ex
   *        The error. May not be <code>null</code>.
   * @return The text. Never <code>null</code>.
   */
  @NonNull
  static String getErrorText (@NonNull final Throwable ex)
  {
    return getErrorText (ex, null);
  }

  /**
   * Get the text of an error for the output: the location of a located stylesheet error
   * (<code>module:line:column: </code>), or the provided file if the error names no module,
   * followed by the code and the message.
   *
   * @param ex
   *        The error. May not be <code>null</code>.
   * @param aDefaultFile
   *        The file to name if the error names no module. May be <code>null</code>.
   * @return The text. Never <code>null</code>.
   */
  @NonNull
  static String getErrorText (@NonNull final Throwable ex, @Nullable final Path aDefaultFile)
  {
    final StringBuilder aSB = new StringBuilder ();
    final String sModule = ex instanceof final XsltException aXEx && aXEx.isLocated () ? getDisplayLocation (aXEx.getModuleUri ())
                                                                                      : null;
    if (sModule != null)
      aSB.append (sModule).append (':');
    else
      if (aDefaultFile != null)
        aSB.append (aDefaultFile).append (':');
    if (ex instanceof final XsltException aXEx && aXEx.isLocated ())
    {
      if (aXEx.getLine () > 0)
      {
        aSB.append (aXEx.getLine ()).append (':');
        if (aXEx.getColumn () > 0)
          aSB.append (aXEx.getColumn ()).append (':');
      }
    }
    if (aSB.length () > 0)
      aSB.append (' ');
    if (ex instanceof XPathException)
      aSB.append (ex.getMessage ());
    else
      aSB.append (ex.getClass ().getSimpleName ()).append (": ").append (ex.getMessage ());
    return aSB.toString ();
  }

  /**
   * Parse an XML document from a file, without node locations and without external entities. The
   * document URI is the URI of the file.
   *
   * @param aFile
   *        The file. May not be <code>null</code>.
   * @return The document node. Never <code>null</code>.
   * @throws XdmParseException
   *         if the file cannot be read or is not well-formed
   */
  @NonNull
  static IXdmNode parseDocument (@NonNull final Path aFile) throws XdmParseException
  {
    final IXdmNode ret = new XdmParser ().setRecordLocations (false).setLoadExternalEntities (false).parse (aFile);
    if (ret.getDocumentUri () == null)
      ret.setDocumentUri (aFile.toAbsolutePath ().toUri ().toString ());
    return ret;
  }
}

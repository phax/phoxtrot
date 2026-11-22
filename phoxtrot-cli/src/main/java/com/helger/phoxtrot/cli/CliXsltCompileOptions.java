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

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xpath.EThreading;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;

import picocli.CommandLine.Option;

/**
 * The stylesheet compile options shared by <code>xslt check</code> and <code>xslt run</code>.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class CliXsltCompileOptions
{
  /** <code>true</code> to compile as schema-aware processor. */
  @Option (names = "--schema-aware",
           description = "Compile as schema-aware processor (xsl:import-schema, validation, schema type tests).")
  boolean m_bSchemaAware;

  /** <code>true</code> to compile without the backwards compatibility feature. */
  @Option (names = "--no-backwards-compatible",
           description = "Compile without the XSLT 1.0 compatibility feature: an instruction with version below 2.0 raises XTDE0160 when evaluated.")
  boolean m_bNoBackwardsCompatible;

  /** The static parameters. */
  @Option (names = "--static-param",
           paramLabel = "NAME=VALUE",
           description = "Set a static stylesheet parameter to a value of type xs:untypedAtomic (converted to the declared type). NAME is local, Q{uri}local or {uri}local. May be repeated.")
  List <String> m_aStaticParams;

  /** Constructor. Called by picocli. */
  CliXsltCompileOptions ()
  {}

  /**
   * Parse and compile a stylesheet.
   *
   * @param aStylesheet
   *        The stylesheet file. May not be <code>null</code>.
   * @param aResolver
   *        The resolver for included modules and schema locations. May not be <code>null</code>.
   * @param bAllowFileAccess
   *        <code>true</code> if any local file may be read: only then the schema documents that an
   *        imported schema references are read.
   * @param eThreading
   *        The thread to compile on. May not be <code>null</code>.
   * @param bStreamingClaim
   *        <code>true</code> to claim streaming.
   * @param bEnforceStreamability
   *        <code>true</code> to raise the static streamability errors.
   * @param bSecondaryResultsAllowed
   *        <code>true</code> to allow xsl:result-document with href.
   * @return The compiled stylesheet. Never <code>null</code>.
   * @throws XdmParseException
   *         if the stylesheet cannot be read or is not well-formed
   * @throws IllegalArgumentException
   *         if a static parameter is malformed
   */
  @NonNull
  XsltExecutable compile (@NonNull final Path aStylesheet,
                          @NonNull final CliResourceResolver aResolver,
                          final boolean bAllowFileAccess,
                          @NonNull final EThreading eThreading,
                          final boolean bStreamingClaim,
                          final boolean bEnforceStreamability,
                          final boolean bSecondaryResultsAllowed) throws XdmParseException
  {
    final String sUri = aStylesheet.toAbsolutePath ().toUri ().toString ();
    final XsltCompileOptions aOptions = new XsltCompileOptions ().setBaseUri (sUri)
                                                                 .setResolver (aResolver)
                                                                 .setLoadExternalEntities (false)
                                                                 .setReadLocalSchemaFiles (bAllowFileAccess)
                                                                 .setSchemaAware (m_bSchemaAware)
                                                                 .setBackwardsCompatibleClaim (!m_bNoBackwardsCompatible)
                                                                 .setStreamingClaim (bStreamingClaim)
                                                                 .setEnforceStreamability (bEnforceStreamability)
                                                                 .setSecondaryResultsAllowed (bSecondaryResultsAllowed)
                                                                 .setThreading (eThreading);
    if (m_aStaticParams != null)
      for (final String sParam : m_aStaticParams)
      {
        final CliHelper.NameValue aNV = CliHelper.splitNameValue (sParam);
        if (aNV.value () == null)
          throw new IllegalArgumentException ("The static parameter '" + sParam + "' is not of the form NAME=VALUE");
        aOptions.setStaticParam (CliHelper.parseName (aNV.name (), null),
                                 XdmSequence.of (XdmAtomic.untyped (aNV.value ())));
      }

    // Stylesheet modules are parsed with locations, for the error positions
    final IXdmNode aDoc = new XdmParser ().setRecordLocations (true).setLoadExternalEntities (false).parse (aStylesheet);
    if (aDoc.getDocumentUri () == null)
      aDoc.setDocumentUri (sUri);
    return XsltCompiler.compile (aDoc, aOptions);
  }
}

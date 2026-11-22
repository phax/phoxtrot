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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.StaticContext;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The expression and namespace options shared by the XPath commands.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class CliXPathOptions
{
  /**
   * One expression to process.
   *
   * @param file
   *        The file it was read from, <code>null</code> for one from the command line.
   * @param text
   *        The expression text. Never <code>null</code>.
   */
  record Expression (@Nullable Path file, @NonNull String text)
  {
    /**
     * @return The label of the expression in the output: the file name, or the text itself.
     */
    @NonNull
    String getLabel ()
    {
      return file != null ? file.toString () : text;
    }

    /**
     * Get the text of a static error of this expression for the output: with the line and column in
     * the file, or the column in the text of an expression from the command line.
     *
     * @param ex
     *        The error. May not be <code>null</code>.
     * @return The text. Never <code>null</code>.
     */
    @NonNull
    String getErrorText (@NonNull final XPathException ex)
    {
      final StringBuilder aSB = new StringBuilder (getLabel ());
      if (ex.hasExpressionPosition ())
      {
        if (file != null)
          aSB.append (':').append (ex.getExpressionLine ()).append (':').append (ex.getExpressionColumn ());
        else
          aSB.append (" (line ").append (ex.getExpressionLine ()).append (", column ").append (ex.getExpressionColumn ()).append (')');
      }
      return aSB.append (": ").append (ex.getMessage ()).toString ();
    }
  }

  /** The expressions from the command line. */
  @Parameters (paramLabel = "XPATH", arity = "0..*", description = "The XPath expressions.")
  List <String> m_aExpressions;

  /** The files with one expression each. */
  @Option (names = { "-f", "--file" },
           paramLabel = "FILE",
           description = "A file with one expression (read as UTF-8). May be repeated.")
  List <Path> m_aFiles;

  /** The namespace bindings. */
  @Option (names = "--ns",
           paramLabel = "PREFIX=URI",
           description = "Bind a namespace prefix for the expressions, e.g. --ns cbc=urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2. May be repeated.")
  List <String> m_aNamespaces;

  /** Constructor. Called by picocli. */
  CliXPathOptions ()
  {}

  /**
   * Get all expressions: first those of the command line, then those of the files.
   *
   * @return The expressions. Never <code>null</code>, empty if none is given.
   * @throws IOException
   *         if a file cannot be read
   */
  @NonNull
  ICommonsList <Expression> getAllExpressions () throws IOException
  {
    final ICommonsList <Expression> ret = new CommonsArrayList <> ();
    if (m_aExpressions != null)
      for (final String sExpr : m_aExpressions)
        ret.add (new Expression (null, sExpr));
    if (m_aFiles != null)
      for (final Path aFile : m_aFiles)
        ret.add (new Expression (aFile, Files.readString (aFile, StandardCharsets.UTF_8)));
    return ret;
  }

  /**
   * Create a static context with the namespace bindings.
   *
   * @return A new static context. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         if a binding is not of the form <code>PREFIX=URI</code>
   */
  @NonNull
  StaticContext createStaticContext ()
  {
    final StaticContext ret = new StaticContext ();
    if (m_aNamespaces != null)
      for (final String sNS : m_aNamespaces)
      {
        final CliHelper.NameValue aNV = CliHelper.splitNameValue (sNS);
        if (aNV.value () == null)
          throw new IllegalArgumentException ("The namespace binding '" + sNS + "' is not of the form PREFIX=URI");
        ret.declareNamespace (aNV.name (), aNV.value ());
      }
    return ret;
  }
}

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
package com.helger.phoxtrot.xslt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.runtime.ResultDrain;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for running stylesheets with {@link XsltExecutable}
 * on generated documents ({@link RandomXmlDocument}). The JDK DOM parser
 * serves as the independent reference for the serialized results.
 *
 * @author Philip Helger
 */
public final class XsltExecutablePropertyTest
{
  /** The identity transformation by the built-in shallow-copy rules */
  private static final XsltExecutable SHALLOW_COPY = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                           "<xsl:output method='xml'/>" +
                                                                           "<xsl:mode on-no-match='shallow-copy'/>" +
                                                                           XsltTestHelper.END,
                                                                           new XsltCompileOptions ());
  /** The identity transformation by one deep copy */
  private static final XsltExecutable DEEP_COPY = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                        "<xsl:output method='xml'/>" +
                                                                        "<xsl:template match='/'><xsl:copy-of select='.'/></xsl:template>" +
                                                                        XsltTestHelper.END,
                                                                        new XsltCompileOptions ());

  @NonNull
  private static XsltTransformRequest _request (@NonNull final String sSource)
  {
    return new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource));
  }

  @NonNull
  private static String _run (@NonNull final XsltExecutable aExec, @NonNull final String sSource)
  {
    return aExec.transform (_request (sSource)).principalSerialized ();
  }

  /**
   * The identity transformation keeps every node: the serialized result, read
   * by the JDK parser, has the expanded names, attributes, text, comments and
   * processing instructions of the source. Serializing it again gives the same
   * characters, and a deep copy gives the same characters as the template rule
   * walk.
   */
  @Test
  public void testIdentityKeepsTheDocument ()
  {
    new PropertyChecker ().setTries (1000).forAll (RandomXmlDocument.DOCUMENTS, aDoc -> {
      final String sSource = aDoc.toString ();
      final String sResult = _run (SHALLOW_COPY, sSource);
      assertEquals (sResult, aDoc.getCanonical (), RandomXmlDocument.getCanonical (RandomXmlDocument.parseWithJdk (sResult)));
      assertEquals (sResult, _run (SHALLOW_COPY, sResult));
      assertEquals (sResult, _run (DEEP_COPY, sSource));
    });
  }

  /**
   * Writing the principal result to a writer, where the result is drained
   * while it is built, gives the characters of the string result, and to a
   * stream their UTF-8 bytes.
   */
  @Test
  public void testWriterMatchesStringResult ()
  {
    new PropertyChecker ().setTries (500).forAll (RandomXmlDocument.DOCUMENTS, aDoc -> {
      final String sSource = aDoc.toString ();
      for (final XsltExecutable aExec : new XsltExecutable [] { SHALLOW_COPY, DEEP_COPY })
      {
        final String sExpected = _run (aExec, sSource);

        final long nBefore = ResultDrain.getDrainedRunCount ();
        final StringWriter aSW = new StringWriter ();
        aExec.transform (_request (sSource), aSW);
        assertEquals (1, ResultDrain.getDrainedRunCount () - nBefore);
        assertEquals (sExpected, aSW.toString ());

        final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
        aExec.transform (_request (sSource), aBAOS);
        assertArrayEquals (sExpected.getBytes (StandardCharsets.UTF_8), aBAOS.toByteArray ());
      }
    });
  }
}

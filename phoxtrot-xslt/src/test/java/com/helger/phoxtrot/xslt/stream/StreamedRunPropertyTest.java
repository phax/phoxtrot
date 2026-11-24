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
package com.helger.phoxtrot.xslt.stream;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the streamed execution ({@link StreamedRun}): for
 * generated record documents, every body shape that the dispatcher streams
 * gives the output of the buffered execution, and the streamed run really
 * streams.
 *
 * @author Philip Helger
 */
public final class StreamedRunPropertyTest
{
  /** A stylesheet compiled with and without the streaming claim */
  private record SheetPair (@NonNull String body, @NonNull XsltExecutable buffered, @NonNull XsltExecutable streamed)
  {}

  private static final String BASE = "file:/streamed/";

  /** One record: its text, group key and whether it has an extra child element and a comment */
  private record Rec (@NonNull String text, @NonNull String group, boolean extra)
  {}

  private static final IGenerator <Rec> RECS = aChoices -> new Rec (RandomXmlDocument.TEXT.generate (aChoices),
                                                                    Generators.of ("a", "b", "1").generate (aChoices),
                                                                    aChoices.draw (1) == 1);

  private static final List <SheetPair> SHEETS = List.of (_pair ("<xsl:for-each select='/data/rec'><xsl:value-of select='v'/>,</xsl:for-each>"),
                                                          _pair ("<xsl:for-each select='/data/rec'>[<xsl:value-of select='position()'/>:<xsl:value-of select='@id'/>]</xsl:for-each>"),
                                                          _pair ("<out><xsl:for-each select='/data/rec'><item id='{@id}' v='{v}'/></xsl:for-each></out>"),
                                                          _pair ("<xsl:copy-of select='/data/rec'/>"),
                                                          _pair ("<xsl:iterate select='data/rec'><xsl:param name='n' select='0'/>" +
                                                                 "<xsl:on-completion><t n='{$n}'/></xsl:on-completion>" +
                                                                 "<xsl:value-of select='position()'/>," +
                                                                 "<xsl:next-iteration><xsl:with-param name='n' select='$n + string-length(v)'/></xsl:next-iteration>" +
                                                                 "</xsl:iterate>"),
                                                          _pair ("<xsl:for-each-group select='data/rec' group-adjacent='@g'>" +
                                                                 "<g k='{current-grouping-key()}' n='{position()}' ids='{current-group()/@id}'/>" +
                                                                 "</xsl:for-each-group>"));

  @NonNull
  private static String _sheet (@NonNull final String sBody)
  {
    return XsltTestHelper.SHEET.replace ("<xsl:stylesheet ", "<xsl:stylesheet exclude-result-prefixes='xs' xml:base='" + BASE + "' ") +
           "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
           "<xsl:template name='xsl:initial-template'><xsl:source-document href='d.xml' streamable='yes'>" +
           sBody +
           "</xsl:source-document></xsl:template>" +
           XsltTestHelper.END;
  }

  @NonNull
  private static SheetPair _pair (@NonNull final String sBody)
  {
    final String sSheet = _sheet (sBody);
    return new SheetPair (sBody,
                          XsltCompiler.compile (sSheet, new XsltCompileOptions ().setStreamingClaim (false)),
                          XsltCompiler.compile (sSheet, new XsltCompileOptions ().setStreamingClaim (true)));
  }

  private static void _escape (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      switch (c)
      {
        case '<' -> aSB.append ("&lt;");
        case '>' -> aSB.append ("&gt;");
        case '&' -> aSB.append ("&amp;");
        case '\'' -> aSB.append ("&apos;");
        case '\r' -> aSB.append ("&#13;");
        default -> aSB.append (c);
      }
    }
  }

  @NonNull
  private static String _document (@NonNull final List <Rec> aRecs)
  {
    final StringBuilder aSB = new StringBuilder ("<?xml version='1.0'?>\n<data id='d'>\n");
    for (int i = 0; i < aRecs.size (); ++i)
    {
      final Rec aRec = aRecs.get (i);
      aSB.append ("  <rec id='r").append (i).append ("' g='").append (aRec.group ()).append ("' t='");
      _escape (aSB, aRec.text ());
      aSB.append ("'><v>");
      _escape (aSB, aRec.text ());
      aSB.append ("</v>");
      if (aRec.extra ())
        aSB.append ("<x a='1'>y<!--c--></x><?pi d?>");
      aSB.append ("</rec>\n");
    }
    return aSB.append ("</data>\n").toString ();
  }

  /**
   * For-each, literal result elements, copy-of, iterate and group-adjacent over
   * the records of a generated document give the same result streamed as
   * buffered, and each streamed run is counted as one.
   */
  @Test
  public void testStreamedMatchesBuffered ()
  {
    new PropertyChecker ().setTries (500).forAll (Generators.lists (RECS, 0, 12), aRecs -> {
      final StreamedExecutionTest.MemoryResolver aResolver = new StreamedExecutionTest.MemoryResolver ().add (BASE + "d.xml",
                                                                                                              _document (aRecs));
      for (final SheetPair aPair : SHEETS)
      {
        final String sBuffered = aPair.buffered ().transform (new XsltTransformRequest ().setResolver (aResolver)).principalSerialized ();
        final long nBefore = StreamedRun.getStreamedRunCount ();
        final String sStreamed = aPair.streamed ().transform (new XsltTransformRequest ().setResolver (aResolver)).principalSerialized ();
        assertEquals (aPair.body (), 1, StreamedRun.getStreamedRunCount () - nBefore);
        assertEquals (aPair.body (), sBuffered, sStreamed);
      }
    });
  }
}

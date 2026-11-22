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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.phoxtrot.cli.CliRunner.CliResult;

/**
 * Test class for the commands <code>xslt check</code> and <code>xslt run</code>, mostly with the
 * files of <code>examples/basic</code>.
 *
 * @author Philip Helger
 */
public final class XsltCommandTest
{
  private static final String BASIC = "../examples/basic/";

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  @NonNull
  private static String _output (@NonNull final CliResult aResult)
  {
    return aResult.out () + aResult.err ();
  }

  @NonNull
  private Path _write (@NonNull final String sRelPath, @NonNull final String sContent) throws IOException
  {
    final Path ret = m_aTempDir.getRoot ().toPath ().resolve (sRelPath);
    Files.createDirectories (ret.getParent ());
    Files.writeString (ret, sContent, StandardCharsets.UTF_8);
    return ret;
  }

  @Test
  public void testCheck ()
  {
    final CliResult aResult = CliRunner.run ("xslt",
                                             "check",
                                             BASIC + "invoice-to-html.xsl",
                                             BASIC + "streamable.xsl",
                                             BASIC + "split.xsl");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out ().contains ("3 stylesheet(s) checked without errors"));
  }

  @Test
  public void testCheckStaticError ()
  {
    final CliResult aResult = CliRunner.run ("xslt", "check", BASIC + "broken.xsl");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    // Module, line and column of the instruction, then the code
    assertTrue (aResult.err (), aResult.err ().contains ("broken.xsl:9:"));
    assertTrue (aResult.err (), aResult.err ().contains ("XPST0008"));
  }

  @Test
  public void testCheckNotWellFormed () throws IOException
  {
    final Path aFile = _write ("bad.xsl", "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>");
    final CliResult aResult = CliRunner.run ("xslt", "check", aFile.toString ());
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("XTSE0010"));
  }

  @Test
  public void testCheckStreamable ()
  {
    CliResult aResult = CliRunner.run ("xslt", "check", "--streamable", BASIC + "streamable.xsl");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());

    aResult = CliRunner.run ("xslt", "check", "--streamable", BASIC + "not-streamable.xsl");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("XTSE3430"));

    // Without the claim the stylesheet is fine
    aResult = CliRunner.run ("xslt", "check", BASIC + "not-streamable.xsl");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
  }

  @Test
  public void testRunToStdOut ()
  {
    final CliResult aResult = CliRunner.run ("xslt",
                                             "run",
                                             "-q",
                                             "-s",
                                             BASIC + "invoice-to-html.xsl",
                                             "-p",
                                             "title=Rechnung",
                                             BASIC + "invoice.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out (), aResult.out ().contains ("<title>Rechnung INV-1</title>"));
    assertTrue (aResult.out (), aResult.out ().contains ("126.00"));
    // The xsl:message goes to the error output, also in quiet mode
    assertTrue (aResult.err (), aResult.err ().contains ("[xsl:message]"));
    assertTrue (aResult.err (), aResult.err ().contains ("Rendering invoice INV-1 with 3 line(s)"));
  }

  @Test
  public void testRunToFile () throws IOException
  {
    final File aOut = new File (m_aTempDir.getRoot (), "sub/out.html");
    final CliResult aResult = CliRunner.run ("xslt",
                                             "run",
                                             "-s",
                                             BASIC + "invoice-to-html.xsl",
                                             "-o",
                                             aOut.getPath (),
                                             BASIC + "invoice.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (Files.readString (aOut.toPath (), StandardCharsets.UTF_8).contains ("<h1>Invoice INV-1 for ACME Corp.</h1>"));
  }

  @Test
  public void testRunDynamicError ()
  {
    final CliResult aResult = CliRunner.run ("xslt",
                                             "run",
                                             "-q",
                                             "-s",
                                             BASIC + "invoice-to-html.xsl",
                                             BASIC + "invoice-invalid.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    // The failed xsl:assert is the error of the run, printed once
    assertTrue (aResult.err (), aResult.err ().contains ("XTMM9001: The total 125.00 is not the sum of the lines"));
    assertFalse (aResult.err (), aResult.err ().contains ("[xsl:assert]"));
  }

  @Test
  public void testRunDynamicErrorDeletesOutput () throws IOException
  {
    final Path aOut = _write ("out.html", "old content");
    final CliResult aResult = CliRunner.run ("xslt",
                                             "run",
                                             "-q",
                                             "-s",
                                             BASIC + "invoice-to-html.xsl",
                                             "-o",
                                             aOut.toString (),
                                             BASIC + "invoice-invalid.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertFalse (Files.exists (aOut));
  }

  @Test
  public void testRunXParamAndInitialTemplate () throws IOException
  {
    final Path aXsl = _write ("it.xsl",
                              "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:xs='http://www.w3.org/2001/XMLSchema'>" +
                                        "<xsl:output method='text'/>" +
                                        "<xsl:param name='codes' as='xs:string*'/>" +
                                        "<xsl:param name='n' as='xs:integer'/>" +
                                        "<xsl:template name='xsl:initial-template'><xsl:value-of select='count($codes) * $n'/></xsl:template>" +
                                        "<xsl:template name='other'>other</xsl:template>" +
                                        "</xsl:stylesheet>");
    CliResult aResult = CliRunner.run ("xslt",
                                       "run",
                                       "-q",
                                       "-s",
                                       aXsl.toString (),
                                       "--initial-template",
                                       "--xparam",
                                       "codes=('A','B','C')",
                                       "-p",
                                       "n=5");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("15", aResult.out ());

    aResult = CliRunner.run ("xslt", "run", "-q", "-s", aXsl.toString (), "--initial-template=other", "-p", "n=1");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("other", aResult.out ());

    // Neither source nor initial template
    aResult = CliRunner.run ("xslt", "run", "-s", aXsl.toString ());
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_USAGE, aResult.exitCode ());
  }

  @Test
  public void testRunStreamed ()
  {
    final CliResult aResult = CliRunner.run ("xslt",
                                             "run",
                                             "-q",
                                             "--streamed",
                                             "-s",
                                             BASIC + "streamable.xsl",
                                             BASIC + "invoice.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out (), aResult.out ().contains ("<line name=\"Bolt\" amount=\"6\"/>"));
  }

  @Test
  public void testRunResultDocuments () throws IOException
  {
    // Rejected without --result-documents
    CliResult aResult = CliRunner.run ("xslt", "run", "-q", "-s", BASIC + "split.xsl", BASIC + "invoice.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("XTSE0010"));

    final Path aDir = m_aTempDir.getRoot ().toPath ().resolve ("lines");
    aResult = CliRunner.run ("xslt",
                             "run",
                             "-q",
                             "-s",
                             BASIC + "split.xsl",
                             "--result-documents",
                             aDir.toString (),
                             BASIC + "invoice.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out (), aResult.out ().contains ("<lines count=\"3\"/>"));
    assertTrue (Files.readString (aDir.resolve ("line-2.xml"), StandardCharsets.UTF_8).contains (">Gadget</line>"));
  }

  @Test
  public void testRunResultDocumentOutside () throws IOException
  {
    final Path aXsl = _write ("escape.xsl",
                              "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                                            "<xsl:template name='xsl:initial-template'><xsl:result-document href='../escaped.xml'><a/></xsl:result-document></xsl:template>" +
                                            "</xsl:stylesheet>");
    final Path aDir = m_aTempDir.getRoot ().toPath ().resolve ("out");
    final CliResult aResult = CliRunner.run ("xslt",
                                             "run",
                                             "-q",
                                             "-s",
                                             aXsl.toString (),
                                             "--initial-template",
                                             "--result-documents",
                                             aDir.toString ());
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_ERROR, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("outside of the folder"));
    assertFalse (Files.exists (m_aTempDir.getRoot ().toPath ().resolve ("escaped.xml")));
  }

  @Test
  public void testFileAccess () throws IOException
  {
    _write ("lib/lib.xsl",
            "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                           "<xsl:template name='xsl:initial-template'>from lib</xsl:template>" +
                           "</xsl:stylesheet>");
    final Path aMain = _write ("main/main.xsl",
                               "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                                                "<xsl:output method='text'/>" +
                                                "<xsl:include href='../lib/lib.xsl'/>" +
                                                "</xsl:stylesheet>");
    // The folder of the included module is not allowed
    CliResult aResult = CliRunner.run ("xslt", "run", "-q", "-s", aMain.toString (), "--initial-template");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("is refused"));

    aResult = CliRunner.run ("xslt",
                             "run",
                             "-q",
                             "-s",
                             aMain.toString (),
                             "--initial-template",
                             "--resource-dir",
                             m_aTempDir.getRoot ().toPath ().resolve ("lib").toString ());
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("from lib", aResult.out ());

    aResult = CliRunner.run ("xslt", "run", "-q", "-s", aMain.toString (), "--initial-template", "--allow-file-access");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("from lib", aResult.out ());
  }

  @Test
  public void testDocumentAccessAtRunTime () throws IOException
  {
    _write ("other/data.xml", "<data>secret</data>");
    final Path aXsl = _write ("main/doc.xsl",
                              "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                                         "<xsl:output method='text'/>" +
                                         "<xsl:template name='xsl:initial-template'><xsl:value-of select=\"doc('../other/data.xml')\"/></xsl:template>" +
                                         "</xsl:stylesheet>");
    CliResult aResult = CliRunner.run ("xslt", "run", "-q", "-s", aXsl.toString (), "--initial-template");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("FODC0002"));

    aResult = CliRunner.run ("xslt", "run", "-q", "-s", aXsl.toString (), "--initial-template", "--allow-file-access");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("secret", aResult.out ());
  }

  @Test
  public void testTrace ()
  {
    final CliResult aResult = CliRunner.run ("xslt",
                                             "run",
                                             "-q",
                                             "--trace",
                                             "-s",
                                             BASIC + "invoice-to-html.xsl",
                                             BASIC + "invoice.xml");
    assertEquals (_output (aResult), PhoxtrotCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("[trace] template match=\"/inv:Invoice\" for element inv:Invoice"));
    assertTrue (aResult.err (), aResult.err ().contains ("function f:amount#1"));
  }
}

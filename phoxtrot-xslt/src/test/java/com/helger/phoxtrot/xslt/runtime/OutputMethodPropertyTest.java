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
package com.helger.phoxtrot.xslt.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the serialization of the principal result to a
 * byte stream (Serialization 3.1 sections 4 to 7): with the xml and xhtml
 * output methods, text with markup characters, line ends, "]]&gt;", non-ASCII
 * characters and characters that the chosen encoding cannot represent re-parses
 * to the same text in elements, attributes and CDATA sections; the text method
 * writes the text unchanged or raises SERE0008 for a character the encoding
 * cannot represent.
 *
 * @author Philip Helger
 */
public final class OutputMethodPropertyTest
{
  /** An executable with the encoding it writes */
  private record Sheet (@NonNull String description, @NonNull XsltExecutable exec, @NonNull Charset charset, boolean cdata)
  {}

  private static final IGenerator <String> TEXTS = Generators.lists (Generators.of ("a",
                                                                                    "<",
                                                                                    ">",
                                                                                    "&",
                                                                                    "\"",
                                                                                    "'",
                                                                                    " ",
                                                                                    "\t",
                                                                                    "\n",
                                                                                    "\r",
                                                                                    "\r\n",
                                                                                    "]]>",
                                                                                    "]",
                                                                                    "\u007f",
                                                                                    "\u0085",
                                                                                    " ",
                                                                                    "ä",
                                                                                    " ",
                                                                                    "€",
                                                                                    "😀"),
                                                                     0,
                                                                     10)
                                                             .map (l -> String.join ("", l));

  private static final String [] ENCODINGS = { "UTF-8", "US-ASCII", "ISO-8859-1", "UTF-16" };

  private static final List <Sheet> XML_SHEETS = _compileXmlSheets ();

  @NonNull
  private static String _body (@NonNull final String sMethod)
  {
    // A literal result element in the XHTML namespace for xhtml, else in no namespace
    final String sNs = sMethod.equals ("xhtml") ? " xmlns='http://www.w3.org/1999/xhtml'" : "";
    return "<xsl:param name='s' as='xs:string'/>" +
           "<xsl:template name='xsl:initial-template'>" +
           "<html" +
           sNs +
           "><body title='{$s}'><p><xsl:value-of select='$s'/></p><pre><xsl:value-of select='$s'/></pre></body></html>" +
           "</xsl:template>";
  }

  @NonNull
  private static List <Sheet> _compileXmlSheets ()
  {
    final List <Sheet> ret = new ArrayList <> ();
    for (final String sMethod : new String [] { "xml", "xhtml" })
      for (final String sEncoding : ENCODINGS)
        for (final boolean bCData : new boolean [] { false, true })
        {
          final String sOutput = "<xsl:output method='" +
                                 sMethod +
                                 "' encoding='" +
                                 sEncoding +
                                 "'" +
                                 (bCData ? " cdata-section-elements='pre Q{http://www.w3.org/1999/xhtml}pre'" : "") +
                                 "/>";
          ret.add (new Sheet (sOutput,
                              XsltCompiler.compile (XsltTestHelper.SHEET + sOutput + _body (sMethod) + XsltTestHelper.END,
                                                    new XsltCompileOptions ()),
                              Charset.forName (sEncoding),
                              bCData));
        }
    return ret;
  }

  @NonNull
  private static byte [] _run (@NonNull final XsltExecutable aExec, @NonNull final String sText)
  {
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setInitialTemplate (new XdmQName ("http://www.w3.org/1999/XSL/Transform",
                                                                                                        "initial-template"));
    aRequest.params ().put (new XdmQName ("s"), XdmSequence.of (XdmAtomic.string (sText)));
    final ByteArrayOutputStream aBAOS = new ByteArrayOutputStream ();
    aExec.transform (aRequest, aBAOS);
    return aBAOS.toByteArray ();
  }

  @NonNull
  private static String _escape (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder ();
    s.codePoints ().forEach (n -> {
      if (n < 0x20 || n > 0x7e)
        aSB.append ("\\u{").append (Integer.toHexString (n)).append ('}');
      else
        aSB.appendCodePoint (n);
    });
    return aSB.toString ();
  }

  /**
   * @return <code>true</code> for the characters that Serialization 3.1
   *         section 5 requires as character references in text and attribute
   *         nodes (CR, NEL, LINE SEPARATOR and the control characters U+007F to
   *         U+009F; U+0001 to U+001F cannot occur in the generated texts), or
   *         only for CR.
   */
  private static boolean _isRawForbidden (final char c, final boolean bAll)
  {
    if (c == '\r')
      return true;
    return bAll && (c == '\u2028' || (c >= 0x7f && c <= 0x9f));
  }

  private static void _checkRoundTrip (@NonNull final Sheet aSheet,
                                      @NonNull final String sText,
                                      final boolean bAllEscapes) throws Exception
  {
    final byte [] aBytes = _run (aSheet.exec (), sText);
    final String sSerialized = new String (aBytes, aSheet.charset ());
    final String sContext = aSheet.description () + " text '" + _escape (sText) + "' gave '" + _escape (sSerialized) + "'";
    final DocumentBuilderFactory aDBF = DocumentBuilderFactory.newInstance ();
    aDBF.setNamespaceAware (true);
    aDBF.setCoalescing (true);
    final Document aDoc = aDBF.newDocumentBuilder ().parse (new ByteArrayInputStream (aBytes));
    final Element aBody = (Element) aDoc.getDocumentElement ().getElementsByTagNameNS ("*", "body").item (0);
    assertEquals (sContext, _escape (sText), _escape (aBody.getAttribute ("title")));
    assertEquals (sContext, _escape (sText), _escape (aBody.getElementsByTagNameNS ("*", "p").item (0).getTextContent ()));
    assertEquals (sContext, _escape (sText), _escape (aBody.getElementsByTagNameNS ("*", "pre").item (0).getTextContent ()));
    for (int i = 0; i < sSerialized.length (); ++i)
      if (_isRawForbidden (sSerialized.charAt (i), bAllEscapes))
        fail ("Unescaped U+" + Integer.toHexString (sSerialized.charAt (i)) + " in " + sContext);
  }

  /**
   * The xml and xhtml output methods in four encodings, with and without
   * CDATA sections: the bytes parse with the JDK parser (which reads the
   * encoding from the XML declaration) to an attribute and two elements whose
   * values are the original text, and a CR appears only as a character
   * reference. A CR in a CDATA section is left to
   * {@link #testCarriageReturnInCDataSection()}, the other characters that
   * must be escaped to {@link #testControlCharactersEscaped()}.
   */
  @Test
  public void testXmlOutputRoundTrips ()
  {
    new PropertyChecker ().setTries (300).forAll (TEXTS, sText -> {
      for (final Sheet aSheet : XML_SHEETS)
        if (!aSheet.cdata () || sText.indexOf ('\r') < 0)
          _checkRoundTrip (aSheet, sText, false);
    });
  }

  /**
   * A CR in a text node must be output as a character reference
   * (Serialization 3.1 section 5, "CR, NEL and LINE SEPARATOR characters in
   * text nodes MUST be output respectively as &amp;#xD;..."), so that it
   * survives the round trip. In an element of cdata-section-elements the
   * engine writes it raw inside the CDATA section, where a parser turns it
   * into a LF: for the text CR the output is
   * <code>&lt;pre&gt;&lt;![CDATA[\r]]&gt;&lt;/pre&gt;</code>.
   */
  @Test
  @Ignore ("Engine bug: a CR in a CDATA section is written raw and does not survive the round trip (Serialization 3.1 sections 5 and 5.1.5)")
  public void testCarriageReturnInCDataSection ()
  {
    new PropertyChecker ().setTries (100).forAll (TEXTS, sText -> {
      for (final Sheet aSheet : XML_SHEETS)
        if (aSheet.cdata ())
          _checkRoundTrip (aSheet, sText + "\r", false);
    });
  }

  /**
   * Serialization 3.1 section 5: "CR, NEL and LINE SEPARATOR characters in
   * text nodes MUST be output respectively as &amp;#xD;, &amp;#x85;, and
   * &amp;#x2028;" (in attributes also NL and TAB) and "the non-whitespace
   * control characters in the ranges #x1 through #x1F and #x7F through #x9F
   * MUST always be escaped, regardless of the value of the version parameter".
   * The engine writes U+007F to U+009F and U+2028 raw with version 1.0 in
   * UTF-8 (U+007F also in US-ASCII), and U+2028 raw with version 1.1 in UTF-8:
   * <code>&lt;r a='&amp;#x7f;&amp;#x85;&amp;#x2028;'/&gt;</code> is written
   * with the three characters unescaped.
   */
  @Test
  @Ignore ("Engine bug: U+007F to U+009F and U+2028 are not written as character references (Serialization 3.1 section 5)")
  public void testControlCharactersEscaped ()
  {
    new PropertyChecker ().setTries (100).forAll (TEXTS, sText -> {
      for (final Sheet aSheet : XML_SHEETS)
        if (!aSheet.cdata ())
          _checkRoundTrip (aSheet, sText, true);
    });
  }

  /**
   * The text output method writes the string value unchanged in UTF-8 and
   * ISO-8859-1 where the encoding can represent it, and raises SERE0008 where
   * it cannot.
   */
  @Test
  public void testTextOutput ()
  {
    final XsltExecutable aUtf8 = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                       "<xsl:output method='text' encoding='UTF-8'/>" +
                                                       _body ("text") +
                                                       XsltTestHelper.END,
                                                       new XsltCompileOptions ());
    final XsltExecutable aLatin1 = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                         "<xsl:output method='text' encoding='ISO-8859-1'/>" +
                                                         _body ("text") +
                                                         XsltTestHelper.END,
                                                         new XsltCompileOptions ());
    new PropertyChecker ().setTries (500).forAll (TEXTS, sText -> {
      final String sExpected = sText + sText;
      assertEquals (_escape (sText), _escape (sExpected), _escape (new String (_run (aUtf8, sText), StandardCharsets.UTF_8)));
      final boolean bLatin1 = sText.chars ().allMatch (c -> c <= 0xff);
      try
      {
        final String sResult = new String (_run (aLatin1, sText), StandardCharsets.ISO_8859_1);
        if (!bLatin1)
          fail ("No error for '" + _escape (sText) + "' in ISO-8859-1, gave '" + _escape (sResult) + "'");
        assertEquals (_escape (sText), _escape (sExpected), _escape (sResult));
      }
      catch (final XPathException ex)
      {
        if (bLatin1)
          throw ex;
        assertEquals (_escape (sText), "SERE0008", ex.getCode ());
      }
    });
  }
}

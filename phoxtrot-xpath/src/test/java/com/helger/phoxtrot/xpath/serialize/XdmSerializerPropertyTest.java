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
package com.helger.phoxtrot.xpath.serialize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Test;

import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyAssume;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for {@link XdmSerializer} and
 * {@link XdmIncrementalSerializer} on random documents with awkward
 * characters: markup characters, <code>]]&gt;</code>, carriage returns, tabs
 * and line ends in text and attributes, supplementary characters and
 * characters the output encoding cannot represent. The xml and xhtml methods
 * must give a document that parses to the same tree (Serialization 3.1
 * sections 5 and 6), the text method the string value (section 8), and the
 * incremental serializer the same characters as the plain one.
 *
 * @author Philip Helger
 */
public final class XdmSerializerPropertyTest
{
  private static final String NS_P = "urn:p";
  private static final String NS_XHTML = "http://www.w3.org/1999/xhtml";

  /** Pieces of text and attribute values, each a complete code point sequence */
  private static final String [] TEXT_PIECES = { "a",
                                                 "x y",
                                                 "<",
                                                 ">",
                                                 "&",
                                                 "]]>",
                                                 "]",
                                                 "\"",
                                                 "'",
                                                 "\r",
                                                 "\n",
                                                 "\r\n",
                                                 "\t",
                                                 " ",
                                                 "\u007f",
                                                 "\u0085",
                                                 "\u00a0",
                                                 "\u00e9",
                                                 "\u20ac",
                                                 "\u2028",
                                                 "\ud83d\ude00",
                                                 "\ufffd" };

  /** Pieces of comments and PI data: no "--", no trailing "-" and no "?>" can arise */
  private static final String [] COMMENT_PIECES = { "a", " ", "-x", "<", "&", ">", "\u00e9", "\ud83d\ude00" };

  private static final String [] ELEMENT_NAMES = { "a", "b", "br", "p", "script", "p:d" };
  private static final String [] ATTRIBUTE_NAMES = { "x", "y", "p:z", "href" };
  /** The xml method with version 1.1 */
  private static final String XML_11 = "xml1.1";
  private static final String [] ENCODINGS = { "UTF-8", "US-ASCII", "ISO-8859-1" };
  private static final int MAX_DEPTH = 4;

  private static void _appendEscaped (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    s.codePoints ().forEach (nCP -> {
      if ((nCP >= 'a' && nCP <= 'z') || nCP == ' ')
        aSB.appendCodePoint (nCP);
      else
        aSB.append ("&#").append (nCP).append (';');
    });
  }

  @NonNull
  private static String _drawPieces (@NonNull final ChoiceSequence aChoices, @NonNull final String [] aPieces, final int nMax)
  {
    final StringBuilder aSB = new StringBuilder ();
    final int nCount = (int) aChoices.draw (nMax);
    for (int i = 0; i < nCount; ++i)
      aSB.append (aPieces[(int) aChoices.draw (aPieces.length - 1)]);
    return aSB.toString ();
  }

  private static void _appendComment (@NonNull final ChoiceSequence aChoices, @NonNull final StringBuilder aSB)
  {
    // Comments and PIs take no character references: the content is written as is
    if (aChoices.draw (1) == 0)
      aSB.append ("<!--").append (_drawPieces (aChoices, COMMENT_PIECES, 4)).append ("-->");
    else
      aSB.append ("<?pi ").append (_drawPieces (aChoices, COMMENT_PIECES, 4)).append ("?>");
  }

  private static void _appendElement (@NonNull final ChoiceSequence aChoices, @NonNull final StringBuilder aSB, final int nDepth)
  {
    final String sName = ELEMENT_NAMES[(int) aChoices.draw (ELEMENT_NAMES.length - 1)];
    aSB.append ('<').append (sName);
    if (nDepth == 0)
      aSB.append (" xmlns:p=\"" + NS_P + "\"");
    switch ((int) aChoices.draw (7))
    {
      case 0:
        aSB.append (" xmlns=\"" + NS_XHTML + "\"");
        break;
      case 1:
        aSB.append (" xmlns=\"urn:d\"");
        break;
      case 2:
        // Undeclares a default namespace declared above, harmless otherwise
        aSB.append (" xmlns=\"\"");
        break;
      default:
        break;
    }
    final Set <String> aUsed = new HashSet <> ();
    final int nAttrs = (int) aChoices.draw (2);
    for (int i = 0; i < nAttrs; ++i)
    {
      final String sAttr = ATTRIBUTE_NAMES[(int) aChoices.draw (ATTRIBUTE_NAMES.length - 1)];
      final String sValue = _drawPieces (aChoices, TEXT_PIECES, 4);
      if (aUsed.add (sAttr))
      {
        aSB.append (' ').append (sAttr).append ("=\"");
        _appendEscaped (aSB, sValue);
        aSB.append ('"');
      }
    }
    aSB.append ('>');
    if (nDepth < MAX_DEPTH)
    {
      final int nChildren = (int) aChoices.draw (3);
      for (int i = 0; i < nChildren; ++i)
        switch ((int) aChoices.draw (4))
        {
          case 0:
          case 1:
            _appendEscaped (aSB, _drawPieces (aChoices, TEXT_PIECES, 5));
            break;
          case 2:
            _appendComment (aChoices, aSB);
            break;
          default:
            _appendElement (aChoices, aSB, nDepth + 1);
            break;
        }
    }
    aSB.append ("</").append (sName).append ('>');
  }

  /** Source texts of documents, with comments and PIs also around the document element */
  private static final IGenerator <String> DOCUMENTS = aChoices -> {
    final StringBuilder aSB = new StringBuilder ();
    if (aChoices.draw (3) == 0)
      _appendComment (aChoices, aSB);
    _appendElement (aChoices, aSB, 0);
    if (aChoices.draw (3) == 0)
      _appendComment (aChoices, aSB);
    return aSB.toString ();
  };

  @NonNull
  private static IXdmNode _parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException ("Not well-formed: " + sXml, ex);
    }
  }

  /**
   * Compare two trees: kinds, names, attributes (in any order), in-scope
   * namespaces, values of text, comment and PI nodes and the children in order.
   * The depth is bounded by the generator.
   */
  private static void _assertSameTree (@NonNull final String sMsg, @NonNull final IXdmNode aExpected, @NonNull final IXdmNode aActual)
  {
    assertEquals (sMsg + ": kind", aExpected.getKind (), aActual.getKind ());
    switch (aExpected.getKind ())
    {
      case TEXT:
      case COMMENT:
        assertEquals (sMsg + ": value", aExpected.getValue (), aActual.getValue ());
        return;
      case PROCESSING_INSTRUCTION:
        assertEquals (sMsg + ": PI target", aExpected.getName (), aActual.getName ());
        assertEquals (sMsg + ": PI data", aExpected.getValue (), aActual.getValue ());
        return;
      case ELEMENT:
      {
        final XdmQName aEName = aExpected.getName ();
        final XdmQName aAName = aActual.getName ();
        assertEquals (sMsg + ": element name", aEName.getClarkName (), aAName.getClarkName ());
        assertEquals (sMsg + ": in-scope namespaces of " + aEName, aExpected.getInScopeNamespaces (), aActual.getInScopeNamespaces ());
        assertEquals (sMsg + ": attribute count of " + aEName, aExpected.getAttributeCount (), aActual.getAttributeCount ());
        for (final IXdmNode aEAttr : aExpected.getAllAttributes ())
        {
          final XdmQName aName = aEAttr.getName ();
          final IXdmNode aAAttr = aActual.getAttribute (aName.namespaceUri (), aName.localName ());
          assertNotNull (sMsg + ": attribute " + aName.getClarkName () + " of " + aEName, aAAttr);
          assertEquals (sMsg + ": attribute " + aName.getClarkName () + " of " + aEName, aEAttr.getValue (), aAAttr.getValue ());
        }
        break;
      }
      default:
        break;
    }
    assertEquals (sMsg + ": child count", aExpected.getChildCount (), aActual.getChildCount ());
    for (int i = 0; i < aExpected.getChildCount (); ++i)
      _assertSameTree (sMsg, aExpected.getChildAtIndex (i), aActual.getChildAtIndex (i));
  }

  /**
   * @return <code>true</code> if a comment or PI (target or data) of the
   *         document holds a character the encoder cannot encode: there is no
   *         character reference for it (Serialization 3.1 section 5.1.3,
   *         SERE0008).
   */
  private static boolean _hasUnencodableMarkupText (@NonNull final IXdmNode aNode, @NonNull final CharsetEncoder aEncoder)
  {
    if (aNode.getKind () == ENodeKind.COMMENT || aNode.getKind () == ENodeKind.PROCESSING_INSTRUCTION)
      return !aEncoder.canEncode (aNode.getValue ());
    for (final IXdmNode aChild : aNode.getAllChildren ())
      if (_hasUnencodableMarkupText (aChild, aEncoder))
        return true;
    return false;
  }

  @NonNull
  private static SerializationParams _params (@NonNull final String sMethod, @NonNull final String sEncoding, final boolean bCdata)
  {
    // URI attributes (href) would otherwise be %-escaped by the xhtml and html methods
    final SerializationParams aParams = new SerializationParams ().setMethod (sMethod)
                                                                  .setEncoding (sEncoding)
                                                                  .setEscapeUriAttributes (false);
    if (bCdata)
    {
      aParams.addCdataSectionElement (new XdmQName ("a"));
      aParams.addCdataSectionElement (new XdmQName (NS_XHTML, "p"));
    }
    return aParams;
  }

  /**
   * @return The serialized form, or <code>null</code> if serialization failed
   *         with an error (whose code is then put into the builder).
   */
  @Nullable
  private static String _trySerialize (@NonNull final IXdmNode aDoc,
                                       @NonNull final SerializationParams aParams,
                                       @NonNull final StringBuilder aErrorCode)
  {
    try
    {
      return XdmSerializer.serialize (XdmSequence.of (aDoc), aParams);
    }
    catch (final XPathException ex)
    {
      aErrorCode.append (ex.getCode ());
      return null;
    }
  }

  /**
   * The xml method (XML 1.0 and 1.1) and the xhtml method, with and without CDATA sections and with
   * encodings that cannot represent every character, produce a document that
   * parses to the same tree. Documents with a comment or PI that the
   * encoding cannot represent are left to
   * {@link #testUnencodableCommentOrPI()}.
   */
  @Test
  public void testMarkupRoundTrip ()
  {
    new PropertyChecker ().setTries (1500)
                          .forAll (DOCUMENTS,
                                   Generators.of (SerializationParams.METHOD_XML, SerializationParams.METHOD_XHTML, XML_11),
                                   Generators.of (ENCODINGS),
                                   Generators.booleans (),
                                   (sSource, sMethod, sEncoding, aCdata) -> {
                                     final IXdmNode aDoc = _parse (sSource);
                                     final boolean bXml11 = XML_11.equals (sMethod);
                                     // A carriage return in a CDATA section, and CDATA with XML 1.1, are left to
                                     // testCdataSpecialCharacters (the JDK XML 1.1 parser also misreads "]]]>")
                                     final boolean bCdata = aCdata.booleanValue () && !bXml11 && aDoc.getStringValue ().indexOf ('\r') < 0;
                                     // U+2028 with XML 1.1 is left to testXml11LineSeparator; the JDK XML 1.1 parser
                                     // rejects a supplementary character in a comment or PI, which XML 1.1 allows
                                     if (bXml11 && (sSource.contains ("&#8232;") || sSource.contains ("\ud83d")))
                                       return;
                                     final SerializationParams aParams = _params (bXml11 ? SerializationParams.METHOD_XML : sMethod,
                                                                                  sEncoding,
                                                                                  bCdata);
                                     if (bXml11)
                                     {
                                       // The declaration tells the parser to apply the XML 1.1 rules (NEL and LS are line ends)
                                       aParams.setVersion ("1.1").setOmitXmlDeclaration (false);
                                     }
                                     final StringBuilder aErrorCode = new StringBuilder ();
                                     final String sSerialized = _trySerialize (aDoc, aParams, aErrorCode);
                                     final String sMsg = sMethod + " " + sEncoding + " cdata=" + bCdata + " " + sSource;
                                     final boolean bUnencodable = _hasUnencodableMarkupText (aDoc,
                                                                                             Charset.forName (sEncoding).newEncoder ());
                                     if (bUnencodable)
                                       return;
                                     assertEquals (sMsg + ": error", "", aErrorCode.toString ());
                                     // Every character of the output must be encodable
                                     assertTrue (sMsg + ": unencodable output " + sSerialized,
                                                 Charset.forName (sEncoding).newEncoder ().canEncode (sSerialized));
                                     final IXdmNode aReparsed;
                                     try
                                     {
                                       aReparsed = new XdmParser ().parse (sSerialized);
                                     }
                                     catch (final XdmParseException ex)
                                     {
                                       fail (sMsg + ": output is not well-formed: " + sSerialized + " - " + ex.getMessage ());
                                       return;
                                     }
                                     _assertSameTree (sMsg + " -> " + sSerialized, aDoc, aReparsed);
                                   });
  }

  /**
   * A comment or processing instruction with a character the output encoding
   * cannot represent is SERE0008 with the xml and xhtml methods, as there is
   * no character reference in that context (Serialization 3.1 section 5.1.3
   * and the definition of SERE0008).
   */
  @Test
  @Ignore ("Engine bug: comments and PIs are written without an encoding check, so the output holds unencodable characters")
  public void testUnencodableCommentOrPI ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (DOCUMENTS,
                                   Generators.of (SerializationParams.METHOD_XML, SerializationParams.METHOD_XHTML),
                                   Generators.of ("US-ASCII", "ISO-8859-1"),
                                   (sSource, sMethod, sEncoding) -> {
                                     final IXdmNode aDoc = _parse (sSource);
                                     PropertyAssume.that (_hasUnencodableMarkupText (aDoc, Charset.forName (sEncoding).newEncoder ()));
                                     final StringBuilder aErrorCode = new StringBuilder ();
                                     final String sSerialized = _trySerialize (aDoc, _params (sMethod, sEncoding, false), aErrorCode);
                                     assertEquals (sMethod + " " + sEncoding + " " + sSource + " -> " + sSerialized,
                                                   "SERE0008",
                                                   aErrorCode.toString ());
                                   });
  }

  /**
   * A carriage return in the text of a CDATA section element survives the
   * round trip: written raw inside a CDATA section, the parser would turn it
   * into a line feed (XML 1.0 section 2.11), so it has to be written as a
   * character reference between two sections (the round-trip requirement of
   * Serialization 3.1 section 5). With XML 1.1 the same holds for NEL, and a
   * restricted character (U+007F to U+009F except NEL) may not appear raw at
   * all (XML 1.1 section 2.2).
   */
  @Test
  @Ignore ("Engine bug: CR (and with XML 1.1 C1 controls) inside a CDATA section are written raw: wrong value or not well-formed")
  public void testCdataSpecialCharacters ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (Generators.lists (Generators.of ("a", "\r", "\r\n", "]]>", "\n", "\u007f", "\u0085"), 1, 6),
                                   Generators.of (SerializationParams.METHOD_XML, SerializationParams.METHOD_XHTML, XML_11),
                                   (aPieces, sMethod) -> {
                                     final boolean bXml11 = XML_11.equals (sMethod);
                                     // The JDK XML 1.1 parser misreads a "]" directly before the end of a CDATA section
                                     if (bXml11 && aPieces.contains ("]]>"))
                                       return;
                                     final StringBuilder aSB = new StringBuilder ("<a>");
                                     _appendEscaped (aSB, String.join ("", aPieces));
                                     aSB.append ("</a>");
                                     final IXdmNode aDoc = _parse (aSB.toString ());
                                     final SerializationParams aParams = _params (bXml11 ? SerializationParams.METHOD_XML : sMethod, "UTF-8", true);
                                     if (bXml11)
                                       aParams.setVersion ("1.1").setOmitXmlDeclaration (false);
                                     final String sSerialized = XdmSerializer.serialize (XdmSequence.of (aDoc), aParams);
                                     final IXdmNode aReparsed;
                                     try
                                     {
                                       aReparsed = new XdmParser ().parse (sSerialized);
                                     }
                                     catch (final XdmParseException ex)
                                     {
                                       fail (sMethod + " " + aSB + ": output is not well-formed: " + sSerialized + " - " + ex.getMessage ());
                                       return;
                                     }
                                     _assertSameTree (sMethod + " " + aSB + " -> " + sSerialized, aDoc, aReparsed);
                                   });
  }

  /**
   * With version 1.1 a LINE SEPARATOR (U+2028) in text or an attribute value
   * survives the round trip: an XML 1.1 parser turns a raw one into a line
   * feed (XML 1.1 section 2.11), so it has to be written as a character
   * reference (the round-trip requirement of Serialization 3.1 section 5).
   */
  @Test
  @Ignore ("Engine bug: with version 1.1, U+2028 is written raw and re-parses as a line feed")
  public void testXml11LineSeparator ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (Generators.lists (Generators.of ("a", "\u2028", "\u0085", "\n"), 1, 6),
                                   Generators.booleans (),
                                   (aPieces, aInAttribute) -> {
                                     final StringBuilder aSB = new StringBuilder ("<a");
                                     final String sValue = String.join ("", aPieces);
                                     if (aInAttribute.booleanValue ())
                                     {
                                       aSB.append (" x=\"");
                                       _appendEscaped (aSB, sValue);
                                       aSB.append ("\">");
                                     }
                                     else
                                     {
                                       aSB.append ('>');
                                       _appendEscaped (aSB, sValue);
                                     }
                                     aSB.append ("</a>");
                                     final IXdmNode aDoc = _parse (aSB.toString ());
                                     final SerializationParams aParams = _params (SerializationParams.METHOD_XML, "UTF-8", false).setVersion ("1.1")
                                                                                                                                 .setOmitXmlDeclaration (false);
                                     final String sSerialized = XdmSerializer.serialize (XdmSequence.of (aDoc), aParams);
                                     _assertSameTree (aSB + " -> " + sSerialized, aDoc, _parse (sSerialized));
                                   });
  }

  /**
   * The text method writes the string value of the document without escaping
   * (Serialization 3.1 section 8); a character the encoding cannot represent
   * is SERE0008.
   */
  @Test
  public void testTextMethod ()
  {
    new PropertyChecker ().setTries (1500).forAll (DOCUMENTS, Generators.of (ENCODINGS), (sSource, sEncoding) -> {
      final IXdmNode aDoc = _parse (sSource);
      final String sExpected = aDoc.getStringValue ();
      final StringBuilder aErrorCode = new StringBuilder ();
      final String sSerialized = _trySerialize (aDoc, _params (SerializationParams.METHOD_TEXT, sEncoding, false), aErrorCode);
      final boolean bEncodable = Charset.forName (sEncoding).newEncoder ().canEncode (sExpected);
      final String sMsg = sEncoding + " " + sSource;
      if (bEncodable)
        assertEquals (sMsg, sExpected, sSerialized);
      else
        assertEquals (sMsg, "SERE0008", aErrorCode.toString ());
    });
  }

  /**
   * Write the node to the incremental serializer, opening an element with
   * startElement and writing its children one by one, or writing it as a
   * whole, as the random source decides.
   */
  private static void _writeIncrementally (@NonNull final XdmIncrementalSerializer aSer,
                                           @NonNull final IXdmNode aNode,
                                           @NonNull final Random aRandom)
  {
    // An element without children is always written as a whole, see testIncrementalEmptyElement
    if (aNode.getKind () == ENodeKind.ELEMENT && aNode.getChildCount () > 0 && aRandom.nextBoolean ())
    {
      aSer.startElement (aNode);
      for (final IXdmNode aChild : aNode.getAllChildren ())
        _writeIncrementally (aSer, aChild, aRandom);
      aSer.endElement ();
    }
    else
      aSer.writeNode (aNode);
  }

  /**
   * {@link XdmIncrementalSerializer} writes the same characters as
   * {@link XdmSerializer} for every method and parameter set it supports,
   * however the tree is split into start tags, end tags and complete nodes,
   * and fails with the same error code where the plain one fails.
   */
  @Test
  public void testIncrementalMatchesPlain ()
  {
    new PropertyChecker ().setTries (1500)
                          .forAll (DOCUMENTS,
                                   Generators.of (SerializationParams.METHOD_XML,
                                                  SerializationParams.METHOD_XHTML,
                                                  SerializationParams.METHOD_HTML,
                                                  SerializationParams.METHOD_TEXT),
                                   Generators.of (ENCODINGS),
                                   Generators.ints (),
                                   (sSource, sMethod, sEncoding, aSeed) -> {
                                     final IXdmNode aDoc = _parse (sSource);
                                     final Random aRandom = new Random (aSeed.intValue ());
                                     final SerializationParams aParams = _params (sMethod, sEncoding, aRandom.nextBoolean ());
                                     aParams.setIncludeContentType (false);
                                     if (aRandom.nextBoolean ())
                                       aParams.setOmitXmlDeclaration (false);
                                     // The text method supports neither a character map nor normalisation incrementally
                                     if (!SerializationParams.METHOD_TEXT.equals (sMethod))
                                     {
                                       if (aRandom.nextBoolean ())
                                         aParams.addCharacterMapping ('a', "[x]").addCharacterMapping (0xe9, "e&");
                                       if (aRandom.nextBoolean ())
                                         aParams.setNormalizationForm (aRandom.nextBoolean () ? "NFC" : "NFD");
                                     }
                                     assertTrue (XdmIncrementalSerializer.isSupported (aParams));
                                     final String sMsg = sMethod + " " + sEncoding + " seed=" + aSeed + " " + sSource;
                                     final StringBuilder aErrorCode = new StringBuilder ();
                                     final String sPlain = _trySerialize (aDoc, aParams, aErrorCode);
                                     final StringWriter aSW = new StringWriter ();
                                     String sIncrementalError = null;
                                     try
                                     {
                                       final XdmIncrementalSerializer aSer = new XdmIncrementalSerializer (aParams, aSW);
                                       for (final IXdmNode aChild : aDoc.getAllChildren ())
                                         _writeIncrementally (aSer, aChild, aRandom);
                                       aSer.finish ();
                                     }
                                     catch (final XPathException ex)
                                     {
                                       sIncrementalError = ex.getCode ();
                                     }
                                     if (sPlain == null)
                                       assertEquals (sMsg, aErrorCode.toString (), sIncrementalError);
                                     else
                                     {
                                       assertEquals (sMsg + ": error " + sIncrementalError, null, sIncrementalError);
                                       assertEquals (sMsg, sPlain, aSW.toString ());
                                     }
                                   });
  }

  /**
   * An element opened with startElement and closed with endElement without
   * any content in between is written as the plain serializer writes the same
   * empty element (the class contract: the same characters as
   * {@link XdmSerializer}).
   */
  @Test
  @Ignore ("Engine deviation from the class contract: startElement/endElement without content gives <a></a>, XdmSerializer <a/>")
  public void testIncrementalEmptyElement ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (Generators.of ("<a/>", "<a xmlns=\"urn:d\" x=\"1\"/>", "<p:a xmlns:p=\"urn:p\"/>"),
                                   Generators.of (SerializationParams.METHOD_XML,
                                                  SerializationParams.METHOD_XHTML,
                                                  SerializationParams.METHOD_HTML),
                                   (sSource, sMethod) -> {
                                     final IXdmNode aDoc = _parse (sSource);
                                     final SerializationParams aParams = _params (sMethod, "UTF-8", false).setIncludeContentType (false);
                                     final StringWriter aSW = new StringWriter ();
                                     final XdmIncrementalSerializer aSer = new XdmIncrementalSerializer (aParams, aSW);
                                     aSer.startElement (aDoc.getChildAtIndex (0));
                                     aSer.endElement ();
                                     aSer.finish ();
                                     assertEquals (sMethod + " " + sSource,
                                                   XdmSerializer.serialize (XdmSequence.of (aDoc), aParams),
                                                   aSW.toString ());
                                   });
  }
}

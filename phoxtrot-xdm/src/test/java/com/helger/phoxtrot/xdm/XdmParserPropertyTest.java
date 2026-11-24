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
package com.helger.phoxtrot.xdm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.XdmDocumentGenerator.GenDocument;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyAssume;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for {@link XdmParser}, the numbering {@link XdmTreeBuilder} gives while it
 * builds, the name indexes of {@link XdmDocumentNode} and {@link XdmStreamReader}, on documents of
 * {@link XdmDocumentGenerator}.
 *
 * @author Philip Helger
 */
public final class XdmParserPropertyTest
{
  /** Code points inserted into documents: markup, line ends, invalid characters and name characters */
  private static final int [] INSERTED_CHARS = { '<',
                                                 '>',
                                                 '&',
                                                 ';',
                                                 '"',
                                                 ':',
                                                 '\r',
                                                 0,
                                                 0x1f,
                                                 0xb7,
                                                 0x300,
                                                 0x37f,
                                                 0x2070,
                                                 0x10000,
                                                 0x1f600,
                                                 0xeffff,
                                                 0xfffe,
                                                 0xd800 };

  private static final Pattern ENCODING_DECLARATION = Pattern.compile ("^<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']*)[\"']");

  @NonNull
  private static InputSource _bytes (final byte @NonNull [] aBytes)
  {
    return new InputSource (new ByteArrayInputStream (aBytes));
  }

  /**
   * @return The nodes of the tree in document order as {@link IXdmNode#assignDocumentOrder()}
   *         defines it: every node, then its attributes, then its children.
   */
  @NonNull
  private static ICommonsList <IXdmNode> _getNodesInDocumentOrder (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      ret.add (aCur);
      for (int i = 0; i < aCur.getAttributeCount (); ++i)
        ret.add (aCur.getAttributeAtIndex (i));
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * Check that the numbering of the tree is the reference numbering: the position in document
   * order, and as end order the position of the last node of the subtree.
   */
  public static void assertReferenceNumbering (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <IXdmNode> aNodes = _getNodesInDocumentOrder (aRoot);
    final Map <IXdmNode, Integer> aEnd = new IdentityHashMap <> ();
    // Backwards, so that the end of the last child is known before its parent
    for (int i = aNodes.size () - 1; i >= 0; --i)
    {
      final IXdmNode aCur = aNodes.get (i);
      int nEnd = i;
      if (aCur.getChildCount () > 0)
        nEnd = aEnd.get (aCur.getChildAtIndex (aCur.getChildCount () - 1)).intValue ();
      else
        if (aCur.getAttributeCount () > 0)
          nEnd = i + aCur.getAttributeCount ();
      aEnd.put (aCur, Integer.valueOf (nEnd));
    }
    for (int i = 0; i < aNodes.size (); ++i)
    {
      final IXdmNode aCur = aNodes.get (i);
      assertEquals (aCur.toString (), i, aCur.getOrder ());
      assertEquals (aCur.toString (), aEnd.get (aCur).intValue (), aCur.getEndOrder ());
    }
  }

  /** @return Order and end order of every node, one line each. */
  @NonNull
  private static ICommonsList <String> _getNumbering (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final IXdmNode aNode : _getNodesInDocumentOrder (aRoot))
      ret.add (aNode.getKind () + " " + aNode.getOrder () + "-" + aNode.getEndOrder ());
    return ret;
  }

  /**
   * Parsing the text or its UTF-8 bytes gives exactly the tree of the generated document.
   */
  @Test
  public void testParsedTreeMatchesDocument ()
  {
    new PropertyChecker ().setTries (300).forAll (XdmDocumentGenerator.documents (), aDoc -> {
      final String sXml = aDoc.getAsXml ();
      final ICommonsList <String> aExpected = aDoc.getExpectedDescription ();
      assertEquals (aExpected, XdmDocumentGenerator.describe (new XdmParser ().parse (sXml)));
      assertEquals (aExpected,
                    XdmDocumentGenerator.describe (new XdmParser ().parse (_bytes (sXml.getBytes (StandardCharsets.UTF_8)))));
    });
  }

  /**
   * The numbering given while building is the document order with consistent end orders, a second
   * numbering by {@link IXdmNode#assignDocumentOrder()} gives the same numbers, and the recorded
   * maximum element depth is the one of the document until that renumbering.
   */
  @Test
  public void testNumberingAsBuilt ()
  {
    new PropertyChecker ().setTries (300).forAll (XdmDocumentGenerator.documents (), aDoc -> {
      final XdmDocumentNode aParsed = (XdmDocumentNode) new XdmParser ().parse (aDoc.getAsXml ());
      assertEquals (aDoc.getMaxElementDepth (), aParsed.getMaxElementDepth ());
      assertReferenceNumbering (aParsed);
      final ICommonsList <String> aBuilt = _getNumbering (aParsed);
      aParsed.assignDocumentOrder ();
      assertEquals (aBuilt, _getNumbering (aParsed));
      assertEquals (-1, aParsed.getMaxElementDepth ());
    });
  }

  private static void _assertSameNodes (@NonNull final List <IXdmNode> aExpected, @NonNull final List <IXdmNode> aActual)
  {
    assertEquals (aExpected.size (), aActual.size ());
    for (int i = 0; i < aExpected.size (); ++i)
      assertSame (aExpected.get (i), aActual.get (i));
  }

  /**
   * Check the element, attribute and child element indexes of a document against a walk over the
   * tree, for all names of the document and one absent name.
   *
   * @param bChildrenFirst
   *        <code>true</code> to ask for the child elements before the element index is built,
   *        <code>false</code> to build it first.
   */
  public static void assertNameIndexes (@NonNull final XdmDocumentNode aDoc, final boolean bChildrenFirst)
  {
    final ICommonsList <IXdmNode> aNodes = _getNodesInDocumentOrder (aDoc);
    final Set <XdmQName> aElementNames = new LinkedHashSet <> ();
    final Set <XdmQName> aAttrNames = new LinkedHashSet <> ();
    final ICommonsList <IXdmNode> aParents = new CommonsArrayList <> ();
    for (final IXdmNode aNode : aNodes)
      if (aNode.isElement ())
      {
        aElementNames.add (aNode.getName ());
        aParents.add (aNode);
      }
      else
        if (aNode.isAttribute ())
          aAttrNames.add (aNode.getName ());
    aParents.add (aDoc);
    final XdmQName aAbsent = new XdmQName ("urn:absent", "absent");
    aElementNames.add (aAbsent);
    aAttrNames.add (aAbsent);

    final Runnable aCheckChildren = () -> {
      for (final IXdmNode aParent : aParents)
        for (final XdmQName aName : aElementNames)
        {
          final ICommonsList <IXdmNode> aExpected = new CommonsArrayList <> ();
          for (int i = 0; i < aParent.getChildCount (); ++i)
            if (aParent.getChildAtIndex (i).isElement () && aParent.getChildAtIndex (i).getName ().equals (aName))
              aExpected.add (aParent.getChildAtIndex (i));
          final List <IXdmNode> aActual = aDoc.getChildElementsByName (aParent, aName);
          assertNotNull (aActual);
          _assertSameNodes (aExpected, aActual);
        }
    };
    if (bChildrenFirst)
      aCheckChildren.run ();
    for (final XdmQName aName : aElementNames)
    {
      final ICommonsList <IXdmNode> aExpected = aNodes.getAll (x -> x.isElement () && x.getName ().equals (aName));
      final List <IXdmNode> aActual = aDoc.getElementsByName (aName);
      assertNotNull (aActual);
      _assertSameNodes (aExpected, aActual);
    }
    for (final XdmQName aName : aAttrNames)
    {
      final ICommonsList <IXdmNode> aExpected = aNodes.getAll (x -> x.isAttribute () && x.getName ().equals (aName));
      final List <IXdmNode> aActual = aDoc.getAttributesByName (aName);
      assertNotNull (aActual);
      _assertSameNodes (aExpected, aActual);
    }
    if (!bChildrenFirst)
      aCheckChildren.run ();
  }

  /**
   * The element, attribute and child element name indexes contain exactly the nodes a walk over the
   * tree finds, in document order, whether the child lists are asked for before or after the
   * element index is built.
   */
  @Test
  public void testNameIndexes ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (XdmDocumentGenerator.documents (), Generators.booleans (), (aDoc, aChildrenFirst) -> {
                            final XdmDocumentNode aParsed = (XdmDocumentNode) new XdmParser ().parse (aDoc.getAsXml ());
                            assertNameIndexes (aParsed, aChildrenFirst.booleanValue ());
                          });
  }

  @NonNull
  private static IXdmNode _streamAll (final byte @NonNull [] aBytes, final boolean bReadAll) throws XdmParseException
  {
    try (final XdmStreamReader aReader = XdmStreamReader.open (_bytes (aBytes)))
    {
      if (bReadAll)
        return aReader.readAll ();
      while (aReader.next () != XdmStreamReader.EEvent.END)
      {
        // Keep everything
      }
      return aReader.getDocument ();
    }
  }

  /**
   * {@link XdmStreamReader} that drops nothing builds the tree of {@link XdmParser}, numbered the
   * same way, whether read event by event or with {@link XdmStreamReader#readAll()}.
   */
  @Test
  public void testStreamReaderMatchesParser ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (XdmDocumentGenerator.documents (), Generators.booleans (), (aDoc, aReadAll) -> {
                            final byte [] aBytes = aDoc.getAsXml ().getBytes (StandardCharsets.UTF_8);
                            final IXdmNode aParsed = new XdmParser ().parse (_bytes (aBytes));
                            final IXdmNode aStreamed = _streamAll (aBytes, aReadAll.booleanValue ());
                            assertEquals (aDoc.getExpectedDescription (), XdmDocumentGenerator.describe (aStreamed));
                            assertEquals (_getNumbering (aParsed), _getNumbering (aStreamed));
                            assertReferenceNumbering (aStreamed);
                          });
  }

  @NonNull
  private static IGenerator <byte []> _mutatedDocuments ()
  {
    return Generators.mutatedByteArrays (XdmDocumentGenerator.documents ()
                                                             .map (x -> x.getAsXml ().getBytes (StandardCharsets.UTF_8)));
  }

  /**
   * @return <code>true</code> if the document has an XML declaration with an encoding name other
   *         than UTF-8.
   */
  private static boolean _isOtherEncodingDeclared (final byte @NonNull [] aBytes)
  {
    final Matcher aMatcher = ENCODING_DECLARATION.matcher (new String (aBytes, StandardCharsets.ISO_8859_1));
    return aMatcher.find () && !"UTF-8".equalsIgnoreCase (aMatcher.group (1));
  }

  /**
   * Damaged documents (bytes replaced, inserted, deleted, truncated) are either read or refused with
   * {@link XdmParseException} by {@link XdmParser} and {@link XdmStreamReader}, never with another
   * exception. Both accept the same documents, with the same tree, unless the stream reader refuses
   * one as unsupported. Documents that declare another encoding than UTF-8 are left out of that
   * comparison, see {@link #testStreamReaderAcceptsEncodingNamesOfParser()}.
   */
  @Test
  public void testMutatedBytes ()
  {
    new PropertyChecker ().setTries (2000).forAll (_mutatedDocuments (), aBytes -> {
      IXdmNode aParsed = null;
      try
      {
        aParsed = new XdmParser ().parse (_bytes (aBytes));
      }
      catch (final XdmParseException ex)
      {
        // Refused as documented
      }
      IXdmNode aStreamed = null;
      boolean bUnsupported = false;
      try
      {
        aStreamed = _streamAll (aBytes, false);
      }
      catch (final XdmStreamUnsupportedException ex)
      {
        bUnsupported = true;
      }
      catch (final XdmParseException ex)
      {
        // Refused as documented
      }
      if (!bUnsupported)
      {
        assertEquals ("Read by the parser: " + (aParsed != null) + ", by the stream reader: " + (aStreamed != null),
                      Boolean.valueOf (aParsed != null),
                      Boolean.valueOf (aStreamed != null));
        if (aParsed != null)
          assertEquals (XdmDocumentGenerator.describe (aParsed), XdmDocumentGenerator.describe (aStreamed));
      }
    });
  }

  /**
   * A document with an XML declaration that names its encoding with a name {@link XdmParser}
   * accepts is also read by {@link XdmStreamReader}, to the same tree.
   */
  @Test
  public void testStreamReaderAcceptsEncodingNamesOfParser ()
  {
    new PropertyChecker ().setTries (50)
                          .forAll (XdmDocumentGenerator.documents (),
                                   Generators.of ("UTF-8", "utf-8", "UTF8", "utf8", "Cp1252", "ISO8859_1", "US-ASCII"),
                                   (aDoc, sEncoding) -> {
                                     final String sXml = aDoc.getAsXml ();
                                     final Charset aCharset = Charset.forName (sEncoding);
                                     // Only documents whose characters the encoding can write
                                     final StringBuilder aSB = new StringBuilder ("<?xml version=\"1.0\" encoding=\"" + sEncoding + "\"?>");
                                     final String sBody = sXml.startsWith ("<?xml") ? sXml.substring (sXml.indexOf ("?>") + 2) : sXml;
                                     PropertyAssume.that (aCharset.newEncoder ().canEncode (sBody));
                                     aSB.append (sBody);
                                     final byte [] aBytes = aSB.toString ().getBytes (aCharset);
                                     final IXdmNode aParsed = new XdmParser ().parse (_bytes (aBytes));
                                     assertEquals (XdmDocumentGenerator.describe (aParsed),
                                                   XdmDocumentGenerator.describe (_streamAll (aBytes, false)));
                                   });
  }

  /**
   * @return A generator of documents as text with one to three code points of
   *         {@link #INSERTED_CHARS} inserted at random positions.
   */
  @NonNull
  private static IGenerator <String> _documentsWithInsertedChars ()
  {
    final IGenerator <GenDocument> aDocs = XdmDocumentGenerator.documents ();
    return aChoices -> {
      final StringBuilder aSB = new StringBuilder (aDocs.generate (aChoices).getAsXml ());
      final int nInserts = 1 + (int) aChoices.draw (2);
      for (int i = 0; i < nInserts; ++i)
      {
        final int nPos = (int) aChoices.draw (aSB.length ());
        final int nChar = INSERTED_CHARS[(int) aChoices.draw (INSERTED_CHARS.length - 1)];
        aSB.insert (nPos, new StringBuilder ().appendCodePoint (nChar));
      }
      return aSB.toString ();
    };
  }

  /**
   * Text with inserted characters (also XML 1.0 5th edition name characters the JDK parser rejects,
   * which the error explanation of {@link XdmParser} looks for) is read or refused with
   * {@link XdmParseException}, never with another exception.
   */
  @Test
  public void testInsertedCharacters ()
  {
    new PropertyChecker ().setTries (1000).forAll (_documentsWithInsertedChars (), sXml -> {
      try
      {
        final IXdmNode aDoc = new XdmParser ().parse (sXml);
        assertTrue (aDoc.isDocument ());
      }
      catch (final XdmParseException ex)
      {
        assertNotNull (ex.getMessage ());
      }
    });
  }
}

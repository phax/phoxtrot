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

import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.IGenerator;

/**
 * Generator of well-formed, namespace well-formed XML documents for the property tests, together
 * with the tree a namespace aware parser must build from them. Documents have elements with
 * prefixed and unprefixed names, namespace declarations (also undeclaring the default namespace),
 * attributes, text with markup characters, line ends and non-BMP characters (written as text or
 * CDATA section), comments and processing instructions, also before and after the document
 * element. Nesting is limited to {@link #MAX_DEPTH} levels. Every choice 0 gives the simplest
 * variant, so failures shrink towards <code>&lt;a/&gt;</code>.
 *
 * @author Philip Helger
 */
public final class XdmDocumentGenerator
{
  /** The maximum number of nested element levels, the document element counting as one */
  public static final int MAX_DEPTH = 5;

  private static final String [] LOCAL_NAMES = { "a", "b", "x-y", "_z", "n.1", "été" };
  private static final String [] PREFIXES = { "p", "q", "r" };
  private static final String [] URIS = { "urn:a", "urn:b", "http://example.org/ns?x=1&y=\"2\"" };
  private static final String [] PI_TARGETS = { "pi", "t-1", "é" };
  /** Text characters: markup characters, line ends, tab, non-ASCII and a non-BMP character */
  private static final int [] TEXT_CHARS = { 'a', ' ', '<', '>', '&', '"', '\'', '\t', '\n', '\r', ']', 0xe4, 0x20ac, 0x1f600 };
  /** Comment and processing instruction characters: no '-', '?' or CR */
  private static final int [] MISC_CHARS = { 'a', ' ', '<', '>', '&', '"', '\'', '\t', '\n', ']', 0xe4, 0x1f600 };

  /** A node of the generated document. */
  public sealed interface IGenNode permits GenElement, GenText, GenComment, GenPI
  {}

  /** A generated attribute: name and raw value. */
  public record GenAttr (String prefix, String namespaceUri, String localName, String value)
  {}

  /**
   * A generated element: name, the namespace declarations of its start tag (prefix to URI, in the
   * written order), attributes and children.
   */
  public record GenElement (String prefix,
                            String namespaceUri,
                            String localName,
                            ICommonsList <String []> namespaceDeclarations,
                            ICommonsList <GenAttr> attributes,
                            ICommonsList <IGenNode> children) implements IGenNode
  {}

  /** Text, written as CDATA section if <code>cdata</code> is set. */
  public record GenText (String text, boolean cdata) implements IGenNode
  {}

  /** A comment. */
  public record GenComment (String text) implements IGenNode
  {}

  /** A processing instruction; the data never starts with whitespace. */
  public record GenPI (String target, String data) implements IGenNode
  {}

  /** A generated document: comments and PIs before and after the document element. */
  public record GenDocument (boolean xmlDeclaration,
                             ICommonsList <IGenNode> prolog,
                             GenElement root,
                             ICommonsList <IGenNode> epilog)
  {
    @Override
    public String toString ()
    {
      return getAsXml ();
    }

    /**
     * @return The document as XML text.
     */
    @NonNull
    public String getAsXml ()
    {
      final StringBuilder aSB = new StringBuilder ();
      if (xmlDeclaration)
        aSB.append ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      for (final IGenNode aNode : prolog)
      {
        _write (aNode, aSB);
        aSB.append ('\n');
      }
      _write (root, aSB);
      for (final IGenNode aNode : epilog)
      {
        aSB.append ('\n');
        _write (aNode, aSB);
      }
      return aSB.toString ();
    }

    /**
     * @return The number of nested element levels, the document element counting as one.
     */
    public int getMaxElementDepth ()
    {
      int ret = 0;
      final ArrayDeque <Object []> aStack = new ArrayDeque <> ();
      aStack.push (new Object [] { root, Integer.valueOf (1) });
      while (!aStack.isEmpty ())
      {
        final Object [] aCur = aStack.pop ();
        final int nDepth = ((Integer) aCur[1]).intValue ();
        ret = Math.max (ret, nDepth);
        for (final IGenNode aChild : ((GenElement) aCur[0]).children ())
          if (aChild instanceof final GenElement aElement)
            aStack.push (new Object [] { aElement, Integer.valueOf (nDepth + 1) });
      }
      return ret;
    }

    /**
     * @return The description of the tree a parser must build, in the format of
     *         {@link XdmDocumentGenerator#describe(IXdmNode)}: adjacent text and CDATA sections
     *         merged, empty text left out.
     */
    @NonNull
    public ICommonsList <String> getExpectedDescription ()
    {
      final ICommonsList <String> ret = new CommonsArrayList <> ();
      ret.add ("D");
      for (final IGenNode aNode : prolog)
        _describe (aNode, ret);
      _describe (root, ret);
      for (final IGenNode aNode : epilog)
        _describe (aNode, ret);
      return ret;
    }
  }

  /** Marks the end of an element in the expected description */
  private static final GenComment END_MARKER = new GenComment (null);

  /** Marks the end of an element in the description of a parsed tree, compared by identity */
  private static final IXdmNode END_NODE = IXdmNode.createComment ("end");

  private XdmDocumentGenerator ()
  {}

  private static void _escapeText (@NonNull final String sText, @NonNull final StringBuilder aSB)
  {
    for (final char c : sText.toCharArray ())
      switch (c)
      {
        case '&' -> aSB.append ("&amp;");
        case '<' -> aSB.append ("&lt;");
        // Needed for "]]>"
        case '>' -> aSB.append ("&gt;");
        // A literal CR would be read as LF
        case '\r' -> aSB.append ("&#13;");
        default -> aSB.append (c);
      }
  }

  private static void _escapeAttr (@NonNull final String sText, @NonNull final StringBuilder aSB)
  {
    for (final char c : sText.toCharArray ())
      switch (c)
      {
        case '&' -> aSB.append ("&amp;");
        case '<' -> aSB.append ("&lt;");
        case '"' -> aSB.append ("&quot;");
        // Attribute value normalisation would turn these into spaces
        case '\t' -> aSB.append ("&#9;");
        case '\n' -> aSB.append ("&#10;");
        case '\r' -> aSB.append ("&#13;");
        default -> aSB.append (c);
      }
  }

  private static void _writeName (@NonNull final String sPrefix,
                                  @NonNull final String sLocalName,
                                  @NonNull final StringBuilder aSB)
  {
    if (!sPrefix.isEmpty ())
      aSB.append (sPrefix).append (':');
    aSB.append (sLocalName);
  }

  /** Write a node and its subtree, iteratively. */
  private static void _write (@NonNull final IGenNode aStart, @NonNull final StringBuilder aSB)
  {
    // An entry is a node to write, or a String end tag
    final ArrayDeque <Object> aStack = new ArrayDeque <> ();
    aStack.push (aStart);
    while (!aStack.isEmpty ())
    {
      final Object aCur = aStack.pop ();
      switch (aCur)
      {
        case final String sEndTag -> aSB.append (sEndTag);
        case final GenText aText ->
        {
          if (aText.cdata ())
            aSB.append ("<![CDATA[").append (aText.text ()).append ("]]>");
          else
            _escapeText (aText.text (), aSB);
        }
        case final GenComment aComment -> aSB.append ("<!--").append (aComment.text ()).append ("-->");
        case final GenPI aPI ->
        {
          aSB.append ("<?").append (aPI.target ());
          if (!aPI.data ().isEmpty ())
            aSB.append (' ').append (aPI.data ());
          aSB.append ("?>");
        }
        case final GenElement aElement ->
        {
          aSB.append ('<');
          _writeName (aElement.prefix (), aElement.localName (), aSB);
          for (final String [] aDecl : aElement.namespaceDeclarations ())
          {
            aSB.append (aDecl[0].isEmpty () ? " xmlns" : " xmlns:" + aDecl[0]).append ("=\"");
            _escapeAttr (aDecl[1], aSB);
            aSB.append ('"');
          }
          for (final GenAttr aAttr : aElement.attributes ())
          {
            aSB.append (' ');
            _writeName (aAttr.prefix (), aAttr.localName (), aSB);
            aSB.append ("=\"");
            _escapeAttr (aAttr.value (), aSB);
            aSB.append ('"');
          }
          if (aElement.children ().isEmpty ())
            aSB.append ("/>");
          else
          {
            aSB.append ('>');
            final StringBuilder aEnd = new StringBuilder ("</");
            _writeName (aElement.prefix (), aElement.localName (), aEnd);
            aStack.push (aEnd.append ('>').toString ());
            for (int i = aElement.children ().size () - 1; i >= 0; --i)
              aStack.push (aElement.children ().get (i));
          }
        }
        default -> throw new IllegalStateException ("Unexpected entry " + aCur);
      }
    }
  }

  @NonNull
  private static String _describeElement (@NonNull final String sNamespaceUri,
                                          @NonNull final String sLocalName,
                                          @NonNull final String sPrefix,
                                          @NonNull final Map <String, String> aDecls,
                                          @NonNull final Map <String, String> aAttrs)
  {
    return "E {" + sNamespaceUri + "}" + sLocalName + " pfx=" + sPrefix + " ns=" + aDecls + " @" + aAttrs;
  }

  /** Describe a generated node and its subtree, iteratively, merging adjacent text. */
  private static void _describe (@NonNull final IGenNode aStart, @NonNull final ICommonsList <String> aTarget)
  {
    final ArrayDeque <IGenNode> aStack = new ArrayDeque <> ();
    aStack.push (aStart);
    // Text pending to be merged with following text
    final StringBuilder aText = new StringBuilder ();
    while (!aStack.isEmpty ())
    {
      final IGenNode aCur = aStack.pop ();
      if (aCur instanceof final GenText aGenText)
      {
        aText.append (aGenText.text ());
        continue;
      }
      if (!aText.isEmpty ())
      {
        aTarget.add ("T " + aText);
        aText.setLength (0);
      }
      if (aCur == END_MARKER)
      {
        aTarget.add ("/");
        continue;
      }
      switch (aCur)
      {
        case final GenComment aComment -> aTarget.add ("C " + aComment.text ());
        case final GenPI aPI -> aTarget.add ("P " + aPI.target () + " " + aPI.data ());
        case final GenElement aElement ->
        {
          final Map <String, String> aDecls = new TreeMap <> ();
          for (final String [] aDecl : aElement.namespaceDeclarations ())
            aDecls.put (aDecl[0], aDecl[1]);
          final Map <String, String> aAttrs = new TreeMap <> ();
          for (final GenAttr aAttr : aElement.attributes ())
            aAttrs.put ("{" + aAttr.namespaceUri () + "}" + aAttr.localName () + "|" + aAttr.prefix (), aAttr.value ());
          aTarget.add (_describeElement (aElement.namespaceUri (),
                                         aElement.localName (),
                                         aElement.prefix (),
                                         aDecls,
                                         aAttrs));
          // The end marker keeps text of different parents apart
          aStack.push (END_MARKER);
          for (int i = aElement.children ().size () - 1; i >= 0; --i)
            aStack.push (aElement.children ().get (i));
        }
        default -> throw new IllegalStateException ("Unexpected node " + aCur);
      }
    }
    if (!aText.isEmpty ())
      aTarget.add ("T " + aText);
  }

  /**
   * Describe a parsed tree in the format of {@link GenDocument#getExpectedDescription()}: one line
   * per node in document order with kind, name, prefix, declared namespaces and attributes (both
   * sorted) and value.
   *
   * @param aDoc
   *        The document node. May not be <code>null</code>.
   * @return The description. Never <code>null</code>.
   */
  @NonNull
  public static ICommonsList <String> describe (@NonNull final IXdmNode aDoc)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aDoc);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aCur == END_NODE)
      {
        // The end of an element, see the expected description
        ret.add ("/");
        continue;
      }
      switch (aCur.getKind ())
      {
        case DOCUMENT -> ret.add ("D");
        case ELEMENT ->
        {
          final Map <String, String> aAttrs = new TreeMap <> ();
          for (int i = 0; i < aCur.getAttributeCount (); ++i)
          {
            final IXdmNode aAttr = aCur.getAttributeAtIndex (i);
            final XdmQName aName = aAttr.getName ();
            aAttrs.put ("{" + aName.namespaceUri () + "}" + aName.localName () + "|" + aName.prefix (), aAttr.getValue ());
          }
          final XdmQName aName = aCur.getName ();
          ret.add (_describeElement (aName.namespaceUri (),
                                     aName.localName (),
                                     aName.prefix (),
                                     new TreeMap <> (aCur.getDeclaredNamespaces ()),
                                     aAttrs));
          aStack.push (END_NODE);
        }
        case TEXT -> ret.add ("T " + aCur.getValue ());
        case COMMENT -> ret.add ("C " + aCur.getValue ());
        case PROCESSING_INSTRUCTION -> ret.add ("P " + aCur.getName ().localName () + " " + aCur.getValue ());
        default -> ret.add ("? " + aCur);
      }
      if (aCur.isDocument () || aCur.isElement ())
        for (int i = aCur.getChildCount () - 1; i >= 0; --i)
          aStack.push (aCur.getChildAtIndex (i));
    }
    return ret;
  }

  private static int _draw (@NonNull final ChoiceSequence aChoices, final int nMaxInclusive)
  {
    return (int) aChoices.draw (nMaxInclusive);
  }

  @NonNull
  private static String _drawString (@NonNull final ChoiceSequence aChoices,
                                     final int @NonNull [] aAlphabet,
                                     final int nMaxLength)
  {
    final StringBuilder aSB = new StringBuilder ();
    final int nLength = _draw (aChoices, nMaxLength);
    for (int i = 0; i < nLength; ++i)
      aSB.appendCodePoint (aAlphabet[_draw (aChoices, aAlphabet.length - 1)]);
    return aSB.toString ();
  }

  @NonNull
  private static IGenNode _drawMisc (@NonNull final ChoiceSequence aChoices)
  {
    if (_draw (aChoices, 1) == 0)
      return new GenComment (_drawString (aChoices, MISC_CHARS, 8));
    final String sTarget = PI_TARGETS[_draw (aChoices, PI_TARGETS.length - 1)];
    // The parser drops the whitespace between target and data
    final String sData = _drawString (aChoices, MISC_CHARS, 8).replaceFirst ("^[ \t\n]+", "");
    return new GenPI (sTarget, sData);
  }

  @NonNull
  private static ICommonsList <IGenNode> _drawMiscList (@NonNull final ChoiceSequence aChoices)
  {
    final ICommonsList <IGenNode> ret = new CommonsArrayList <> ();
    final int nCount = _draw (aChoices, 2);
    for (int i = 0; i < nCount; ++i)
      ret.add (_drawMisc (aChoices));
    return ret;
  }

  @NonNull
  private static GenText _drawText (@NonNull final ChoiceSequence aChoices)
  {
    final String sText = _drawString (aChoices, TEXT_CHARS, 10);
    // A CDATA section can hold neither "]]>" nor a CR that survives reading
    final boolean bCData = _draw (aChoices, 3) == 3 && !sText.contains ("]]>") && sText.indexOf ('\r') < 0;
    return new GenText (sText, bCData);
  }

  /**
   * Draw an element without children.
   *
   * @param aInScope
   *        The namespaces in scope at the parent, prefix to URI; updated with the declarations of
   *        the new element.
   */
  @NonNull
  private static GenElement _drawElementShell (@NonNull final ChoiceSequence aChoices,
                                               @NonNull final Map <String, String> aInScope)
  {
    final ICommonsList <String []> aDecls = new CommonsArrayList <> ();
    final int nDecls = _draw (aChoices, 2);
    for (int i = 0; i < nDecls; ++i)
    {
      final int nPrefix = _draw (aChoices, PREFIXES.length);
      // Index 0 is the default namespace, which may also be undeclared
      final String sPrefix = nPrefix == 0 ? "" : PREFIXES[nPrefix - 1];
      final int nUri = _draw (aChoices, URIS.length);
      final String sUri;
      if (nUri == URIS.length)
        sUri = sPrefix.isEmpty () ? "" : URIS[0];
      else
        sUri = URIS[nUri];
      if (aDecls.containsAny (x -> x[0].equals (sPrefix)))
        continue;
      aDecls.add (new String [] { sPrefix, sUri });
      aInScope.put (sPrefix, sUri);
    }

    final ICommonsList <String> aPrefixes = new CommonsArrayList <> ();
    for (final Map.Entry <String, String> aEntry : aInScope.entrySet ())
      if (!aEntry.getKey ().isEmpty () && !aEntry.getValue ().isEmpty ())
        aPrefixes.add (aEntry.getKey ());

    final int nElementPrefix = _draw (aChoices, aPrefixes.size ());
    final String sPrefix = nElementPrefix == 0 ? "" : aPrefixes.get (nElementPrefix - 1);
    final String sNamespaceUri = aInScope.getOrDefault (sPrefix, "");
    final String sLocalName = LOCAL_NAMES[_draw (aChoices, LOCAL_NAMES.length - 1)];

    final ICommonsList <GenAttr> aAttrs = new CommonsArrayList <> ();
    final int nAttrs = _draw (aChoices, 3);
    for (int i = 0; i < nAttrs; ++i)
    {
      final String sAttrLocalName = LOCAL_NAMES[_draw (aChoices, LOCAL_NAMES.length - 1)];
      // 0: no namespace, then the prefixes in scope, last xml:lang
      final int nAttrPrefix = _draw (aChoices, aPrefixes.size () + 1);
      final String sAttrPrefix;
      final String sAttrNamespaceUri;
      final String sAttrName;
      if (nAttrPrefix == 0)
      {
        sAttrPrefix = "";
        sAttrNamespaceUri = "";
        sAttrName = sAttrLocalName;
      }
      else
        if (nAttrPrefix <= aPrefixes.size ())
        {
          sAttrPrefix = aPrefixes.get (nAttrPrefix - 1);
          sAttrNamespaceUri = aInScope.get (sAttrPrefix);
          sAttrName = sAttrLocalName;
        }
        else
        {
          sAttrPrefix = "xml";
          sAttrNamespaceUri = XdmConstants.NS_XML;
          sAttrName = "lang";
        }
      final String sValue = _drawString (aChoices, TEXT_CHARS, 6);
      // Expanded names must be unique (also over different prefixes of the same URI)
      if (aAttrs.containsAny (x -> x.namespaceUri ().equals (sAttrNamespaceUri) && x.localName ().equals (sAttrName)))
        continue;
      aAttrs.add (new GenAttr (sAttrPrefix, sAttrNamespaceUri, sAttrName, sValue));
    }
    return new GenElement (sPrefix, sNamespaceUri, sLocalName, aDecls, aAttrs, new CommonsArrayList <> ());
  }

  /** Draw the document element with its subtree, iteratively. */
  @NonNull
  private static GenElement _drawTree (@NonNull final ChoiceSequence aChoices)
  {
    final Map <String, String> aRootScope = new TreeMap <> ();
    final GenElement aRoot = _drawElementShell (aChoices, aRootScope);
    // Entries: element, its in-scope namespaces, its depth
    final ArrayDeque <Object []> aStack = new ArrayDeque <> ();
    aStack.push (new Object [] { aRoot, aRootScope, Integer.valueOf (1) });
    while (!aStack.isEmpty ())
    {
      final Object [] aCur = aStack.pop ();
      final GenElement aElement = (GenElement) aCur[0];
      @SuppressWarnings ("unchecked")
      final Map <String, String> aInScope = (Map <String, String>) aCur[1];
      final int nDepth = ((Integer) aCur[2]).intValue ();
      if (nDepth >= MAX_DEPTH)
        continue;
      final int nChildren = _draw (aChoices, 4);
      for (int i = 0; i < nChildren; ++i)
      {
        // 0: text, 1: element, 2: comment or PI
        switch (_draw (aChoices, 2))
        {
          case 0 -> aElement.children ().add (_drawText (aChoices));
          case 1 ->
          {
            final Map <String, String> aChildScope = new TreeMap <> (aInScope);
            final GenElement aChild = _drawElementShell (aChoices, aChildScope);
            aElement.children ().add (aChild);
            aStack.push (new Object [] { aChild, aChildScope, Integer.valueOf (nDepth + 1) });
          }
          default -> aElement.children ().add (_drawMisc (aChoices));
        }
      }
    }
    return aRoot;
  }

  /**
   * @return A generator of well-formed documents. Never <code>null</code>.
   */
  @NonNull
  public static IGenerator <GenDocument> documents ()
  {
    return aChoices -> {
      final boolean bXmlDecl = _draw (aChoices, 1) == 1;
      final ICommonsList <IGenNode> aProlog = _drawMiscList (aChoices);
      final GenElement aRoot = _drawTree (aChoices);
      final ICommonsList <IGenNode> aEpilog = _drawMiscList (aChoices);
      return new GenDocument (bXmlDecl, aProlog, aRoot, aEpilog);
    };
  }
}

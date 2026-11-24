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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.io.nonblocking.NonBlockingStringReader;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;

/**
 * Generator of small random XML documents for property tests: elements in no
 * namespace, a default namespace and two prefixed namespaces, attributes,
 * text with markup and non-ASCII characters, comments and processing
 * instructions, also before and after the document element. A document knows
 * its XML text ({@link Doc#toString()}) and a canonical form that the JDK DOM
 * parser serves as the independent reference for ({@link #getCanonical(Node)}).
 *
 * @author Philip Helger
 */
public final class RandomXmlDocument
{
  private static final String NS_XMLNS = "http://www.w3.org/2000/xmlns/";

  /** One namespace binding of the pool: prefix ("" for the default namespace) and URI */
  public record Ns (@NonNull String prefix, @NonNull String uri)
  {}

  private static final Ns [] NAMESPACES = { new Ns ("", ""),
                                            new Ns ("", "urn:d"),
                                            new Ns ("p", "urn:p"),
                                            new Ns ("q", "urn:q") };

  /** Characters with a meaning in XML, line ends, tabs and non-ASCII letters */
  private static final String TEXT_CHARS = "<>&\"' \t\n\r]\u00e4\u20ac\u0085\u2028ab";

  /** Text of valid XML characters, possibly empty */
  public static final IGenerator <String> TEXT = Generators.oneOf (Generators.strings (TEXT_CHARS, 0, 12),
                                                                   Generators.unicodeStrings (0, 8)
                                                                             .filter (s -> s.codePoints ()
                                                                                            .allMatch (RandomXmlDocument::_isValidXmlChar)));

  private static final IGenerator <Ns> ELEMENT_NS = Generators.of (NAMESPACES);
  private static final IGenerator <Ns> ATTRIBUTE_NS = Generators.of (NAMESPACES[0], NAMESPACES[2], NAMESPACES[3]);
  private static final IGenerator <String> LOCAL_NAMES = Generators.of ("a", "b", "c", "d\u00e9");
  private static final IGenerator <Comment> COMMENTS = Generators.strings ("ab -<&\u00e4", 0, 8)
                                                                 .filter (s -> !s.contains ("--") && !s.endsWith ("-"))
                                                                 .map (Comment::new);
  private static final IGenerator <PI> PIS = Generators.strings ("ab ?>&<", 0, 8)
                                                       .filter (s -> !s.contains ("?>") && (s.isEmpty () || s.charAt (0) != ' '))
                                                       .flatMap (s -> Generators.of ("pi", "x-y").map (t -> new PI (t, s)));
  private static final IGenerator <XNode> MISC = Generators.oneOf (COMMENTS, PIS);

  /** The element names of {@link #simpleDocuments(int, int)} */
  public static final String [] SIMPLE_NAMES = { "a", "b", "c" };
  /** The attribute names of {@link #simpleDocuments(int, int)} */
  public static final String [] SIMPLE_ATTRIBUTES = { "k", "m" };
  /** The attribute values of {@link #simpleDocuments(int, int)} */
  public static final String [] SIMPLE_VALUES = { "1", "2", "01", "a", "b c", "" };

  /** Small documents of up to four levels of elements */
  public static final IGenerator <Doc> DOCUMENTS = aChoices -> {
    final List <XNode> aProlog = new ArrayList <> ();
    final int nProlog = (int) aChoices.draw (2);
    for (int i = 0; i < nProlog; ++i)
      aProlog.add (MISC.generate (aChoices));
    final Elem aRoot = _generateElement (aChoices, 3);
    final List <XNode> aEpilog = new ArrayList <> ();
    final int nEpilog = (int) aChoices.draw (2);
    for (int i = 0; i < nEpilog; ++i)
      aEpilog.add (MISC.generate (aChoices));
    return new Doc (aProlog, aRoot, aEpilog);
  };

  /** A node of the model. */
  public sealed interface XNode permits Elem, Text, Comment, PI
  {
    void appendXml (@NonNull StringBuilder aSB);

    void appendCanonical (@NonNull StringBuilder aSB);
  }

  /** An attribute of the model. */
  public record XAttr (@NonNull Ns ns, @NonNull String local, @NonNull String value)
  {
    @NonNull
    String getQName ()
    {
      return ns.prefix ().isEmpty () ? local : ns.prefix () + ":" + local;
    }
  }

  private static void _escapeText (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      switch (c)
      {
        case '<' -> aSB.append ("&lt;");
        case '>' -> aSB.append ("&gt;");
        case '&' -> aSB.append ("&amp;");
        case '\r' -> aSB.append ("&#13;");
        default -> aSB.append (c);
      }
    }
  }

  private static void _escapeAttr (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      switch (c)
      {
        case '<' -> aSB.append ("&lt;");
        case '&' -> aSB.append ("&amp;");
        case '"' -> aSB.append ("&quot;");
        case '\t' -> aSB.append ("&#9;");
        case '\n' -> aSB.append ("&#10;");
        case '\r' -> aSB.append ("&#13;");
        default -> aSB.append (c);
      }
    }
  }

  private static void _appendQuoted (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    // Code points, so that a difference in surrogates is visible
    aSB.append ('"');
    s.codePoints ().forEach (n -> {
      if (n < 0x20 || n > 0x7e || n == '"' || n == '\\')
        aSB.append ("\\u{").append (Integer.toHexString (n)).append ('}');
      else
        aSB.appendCodePoint (n);
    });
    aSB.append ('"');
  }

  private static void _appendElementCanonical (@NonNull final StringBuilder aSB,
                                               @NonNull final String sName,
                                               @NonNull final Map <String, String> aAttrs)
  {
    aSB.append ('E').append (sName).append ('[');
    for (final Map.Entry <String, String> aEntry : aAttrs.entrySet ())
    {
      aSB.append (aEntry.getKey ()).append ('=');
      _appendQuoted (aSB, aEntry.getValue ());
    }
    aSB.append ("](");
  }

  @NonNull
  private static String _nullToEmpty (@Nullable final String s)
  {
    return s == null ? "" : s;
  }

  /** An element of the model. */
  public record Elem (@NonNull Ns ns, @NonNull String local, @NonNull List <XAttr> attrs, @NonNull List <XNode> children)
                     implements XNode
  {
    @NonNull
    String getQName ()
    {
      return ns.prefix ().isEmpty () ? local : ns.prefix () + ":" + local;
    }

    public void appendXml (@NonNull final StringBuilder aSB)
    {
      aSB.append ('<').append (getQName ());
      // Every element declares the namespaces it uses itself
      if (ns.prefix ().isEmpty ())
        aSB.append (" xmlns=\"").append (ns.uri ()).append ('"');
      else
        aSB.append (" xmlns:").append (ns.prefix ()).append ("=\"").append (ns.uri ()).append ('"');
      final List <String> aDeclared = new ArrayList <> ();
      aDeclared.add (ns.prefix ());
      for (final XAttr aAttr : attrs)
      {
        final String sPrefix = aAttr.ns ().prefix ();
        if (!sPrefix.isEmpty () && !aDeclared.contains (sPrefix))
        {
          aDeclared.add (sPrefix);
          aSB.append (" xmlns:").append (sPrefix).append ("=\"").append (aAttr.ns ().uri ()).append ('"');
        }
      }
      for (final XAttr aAttr : attrs)
      {
        aSB.append (' ').append (aAttr.getQName ()).append ("=\"");
        _escapeAttr (aSB, aAttr.value ());
        aSB.append ('"');
      }
      if (children.isEmpty ())
        aSB.append ("/>");
      else
      {
        aSB.append ('>');
        for (final XNode aChild : children)
          aChild.appendXml (aSB);
        aSB.append ("</").append (getQName ()).append ('>');
      }
    }

    public void appendCanonical (@NonNull final StringBuilder aSB)
    {
      final Map <String, String> aSorted = new TreeMap <> ();
      for (final XAttr aAttr : attrs)
        aSorted.put ("{" + aAttr.ns ().uri () + "}" + aAttr.local (), aAttr.value ());
      _appendElementCanonical (aSB, "{" + ns.uri () + "}" + local, aSorted);
      for (final XNode aChild : children)
        aChild.appendCanonical (aSB);
      aSB.append (')');
    }
  }

  /** A text node of the model, never empty. */
  public record Text (@NonNull String text) implements XNode
  {
    public void appendXml (@NonNull final StringBuilder aSB)
    {
      _escapeText (aSB, text);
    }

    public void appendCanonical (@NonNull final StringBuilder aSB)
    {
      aSB.append ('T');
      _appendQuoted (aSB, text);
    }
  }

  /** A comment of the model. */
  public record Comment (@NonNull String text) implements XNode
  {
    public void appendXml (@NonNull final StringBuilder aSB)
    {
      aSB.append ("<!--").append (text).append ("-->");
    }

    public void appendCanonical (@NonNull final StringBuilder aSB)
    {
      aSB.append ('C');
      _appendQuoted (aSB, text);
    }
  }

  /** A processing instruction of the model. */
  public record PI (@NonNull String target, @NonNull String data) implements XNode
  {
    public void appendXml (@NonNull final StringBuilder aSB)
    {
      aSB.append ("<?").append (target);
      if (!data.isEmpty ())
        aSB.append (' ').append (data);
      aSB.append ("?>");
    }

    public void appendCanonical (@NonNull final StringBuilder aSB)
    {
      aSB.append ("P").append (target);
      _appendQuoted (aSB, data);
    }
  }

  /** A document: prolog nodes, the document element and epilog nodes. */
  public record Doc (@NonNull List <XNode> prolog, @NonNull Elem root, @NonNull List <XNode> epilog)
  {
    /**
     * @return The canonical form of the document, comparable with
     *         {@link RandomXmlDocument#getCanonical(Node)}.
     */
    @NonNull
    public String getCanonical ()
    {
      final StringBuilder aSB = new StringBuilder ("D(");
      for (final XNode aNode : prolog)
        aNode.appendCanonical (aSB);
      root.appendCanonical (aSB);
      for (final XNode aNode : epilog)
        aNode.appendCanonical (aSB);
      return aSB.append (')').toString ();
    }

    /** @return The XML text of the document. */
    @Override
    @NonNull
    public String toString ()
    {
      final StringBuilder aSB = new StringBuilder ();
      for (final XNode aNode : prolog)
        aNode.appendXml (aSB);
      root.appendXml (aSB);
      for (final XNode aNode : epilog)
        aNode.appendXml (aSB);
      return aSB.toString ();
    }
  }

  private static boolean _isValidXmlChar (final int n)
  {
    return n == 0x9 ||
           n == 0xa ||
           n == 0xd ||
           (n >= 0x20 && n <= 0xd7ff) ||
           (n >= 0xe000 && n <= 0xfffd) ||
           (n >= 0x10000 && n <= 0x10ffff);
  }

  @PresentForCodeCoverage
  private static final RandomXmlDocument INSTANCE = new RandomXmlDocument ();

  private RandomXmlDocument ()
  {}

  @NonNull
  private static List <XAttr> _generateAttributes (@NonNull final ChoiceSequence aChoices)
  {
    final List <XAttr> ret = new ArrayList <> ();
    final int nCount = (int) aChoices.draw (3);
    for (int i = 0; i < nCount; ++i)
    {
      final Ns aNs = ATTRIBUTE_NS.generate (aChoices);
      final String sLocal = LOCAL_NAMES.generate (aChoices);
      final String sValue = TEXT.generate (aChoices);
      // Attribute names must be unique by expanded name
      boolean bDuplicate = false;
      for (final XAttr aOther : ret)
        if (aOther.ns ().uri ().equals (aNs.uri ()) && aOther.local ().equals (sLocal))
          bDuplicate = true;
      if (!bDuplicate)
        ret.add (new XAttr (aNs, sLocal, sValue));
    }
    return ret;
  }

  @NonNull
  private static Elem _generateElement (@NonNull final ChoiceSequence aChoices, final int nDepth)
  {
    final Ns aNs = ELEMENT_NS.generate (aChoices);
    final String sLocal = LOCAL_NAMES.generate (aChoices);
    final List <XAttr> aAttrs = _generateAttributes (aChoices);
    final List <XNode> aChildren = new ArrayList <> ();
    final int nCount = nDepth <= 0 ? 0 : (int) aChoices.draw (4);
    for (int i = 0; i < nCount; ++i)
    {
      final XNode aChild = switch ((int) aChoices.draw (3))
      {
        case 0 -> new Text (TEXT.generate (aChoices));
        case 1 -> _generateElement (aChoices, nDepth - 1);
        default -> MISC.generate (aChoices);
      };
      // Adjacent text is one text node, empty text none
      if (aChild instanceof final Text aText)
      {
        if (aText.text ().isEmpty ())
          continue;
        if (!aChildren.isEmpty () && aChildren.getLast () instanceof final Text aPrev)
        {
          aChildren.set (aChildren.size () - 1, new Text (aPrev.text () + aText.text ()));
          continue;
        }
      }
      aChildren.add (aChild);
    }
    return new Elem (aNs, sLocal, aAttrs, aChildren);
  }

  private static void _appendCanonicalRecursive (@NonNull final StringBuilder aSB, @NonNull final Node aNode)
  {
    switch (aNode.getNodeType ())
    {
      case Node.DOCUMENT_NODE:
        aSB.append ("D(");
        for (Node aChild = aNode.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
          _appendCanonicalRecursive (aSB, aChild);
        aSB.append (')');
        break;
      case Node.ELEMENT_NODE:
      {
        final Map <String, String> aSorted = new TreeMap <> ();
        final NamedNodeMap aAttrs = aNode.getAttributes ();
        for (int i = 0; i < aAttrs.getLength (); ++i)
        {
          final Attr aAttr = (Attr) aAttrs.item (i);
          if (!NS_XMLNS.equals (aAttr.getNamespaceURI ()))
            aSorted.put ("{" + _nullToEmpty (aAttr.getNamespaceURI ()) + "}" + aAttr.getLocalName (), aAttr.getValue ());
        }
        _appendElementCanonical (aSB, "{" + _nullToEmpty (aNode.getNamespaceURI ()) + "}" + aNode.getLocalName (), aSorted);
        for (Node aChild = aNode.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
          _appendCanonicalRecursive (aSB, aChild);
        aSB.append (')');
        break;
      }
      case Node.TEXT_NODE:
      case Node.CDATA_SECTION_NODE:
        aSB.append ('T');
        _appendQuoted (aSB, aNode.getNodeValue ());
        break;
      case Node.COMMENT_NODE:
        aSB.append ('C');
        _appendQuoted (aSB, aNode.getNodeValue ());
        break;
      case Node.PROCESSING_INSTRUCTION_NODE:
        aSB.append ('P').append (aNode.getNodeName ());
        _appendQuoted (aSB, aNode.getNodeValue ());
        break;
      default:
        aSB.append ("?").append (aNode.getNodeType ());
    }
  }

  /**
   * Parse XML with the JDK DOM parser (namespace aware, adjacent text and
   * CDATA sections merged).
   *
   * @param sXml
   *        The XML text.
   * @return The document node.
   * @throws Exception
   *         If the text is not well-formed.
   */
  @NonNull
  public static Node parseWithJdk (@NonNull final String sXml) throws Exception
  {
    final DocumentBuilderFactory aDBF = DocumentBuilderFactory.newInstance ();
    aDBF.setNamespaceAware (true);
    aDBF.setCoalescing (true);
    final Node ret = aDBF.newDocumentBuilder ().parse (new InputSource (new NonBlockingStringReader (sXml)));
    ret.normalize ();
    return ret;
  }

  /**
   * @param aNode
   *        A DOM node.
   * @return The canonical form of the node, comparable with
   *         {@link Doc#getCanonical()}: expanded names, attributes sorted,
   *         namespace declarations ignored.
   */
  @NonNull
  public static String getCanonical (@NonNull final Node aNode)
  {
    final StringBuilder aSB = new StringBuilder ();
    _appendCanonicalRecursive (aSB, aNode);
    return aSB.toString ();
  }

  private static void _appendSimpleElementRecursive (@NonNull final ChoiceSequence aChoices,
                                                     @NonNull final StringBuilder aSB,
                                                     final int nDepth,
                                                     final int nMaxChildren)
  {
    final String sName = SIMPLE_NAMES[(int) aChoices.draw (SIMPLE_NAMES.length - 1)];
    aSB.append ('<').append (sName);
    // Each attribute present or absent
    for (final String sAttr : SIMPLE_ATTRIBUTES)
      if (aChoices.draw (1) == 1)
        aSB.append (' ').append (sAttr).append ("='").append (SIMPLE_VALUES[(int) aChoices.draw (SIMPLE_VALUES.length - 1)]).append ('\'');
    aSB.append ('>');
    final int nCount = nDepth <= 0 ? 0 : (int) aChoices.draw (nMaxChildren);
    for (int i = 0; i < nCount; ++i)
      if (aChoices.draw (4) == 0)
        aSB.append ('t');
      else
        _appendSimpleElementRecursive (aChoices, aSB, nDepth - 1, Math.max (1, nMaxChildren / 4));
    aSB.append ("</").append (sName).append ('>');
  }

  /**
   * Documents for tests of matching, numbering and keys: a document element
   * <code>r</code> with elements <code>a</code>, <code>b</code> and
   * <code>c</code> in no namespace below it, each with optional attributes
   * <code>k</code> and <code>m</code> (values of {@link #SIMPLE_VALUES}), and
   * text nodes <code>t</code>.
   *
   * @param nMaxDepth
   *        The maximum number of element levels below the document element.
   * @param nMaxChildren
   *        The maximum number of children of the document element; each level
   *        below has a quarter of that, at least one.
   * @return The generator of the XML texts. Never <code>null</code>.
   */
  @NonNull
  public static IGenerator <String> simpleDocuments (final int nMaxDepth, final int nMaxChildren)
  {
    return aChoices -> {
      final StringBuilder aSB = new StringBuilder ("<r>");
      final int nCount = (int) aChoices.draw (nMaxChildren);
      for (int i = 0; i < nCount; ++i)
        _appendSimpleElementRecursive (aChoices, aSB, nMaxDepth - 1, Math.max (1, nMaxChildren / 4));
      return aSB.append ("</r>").toString ();
    };
  }
}

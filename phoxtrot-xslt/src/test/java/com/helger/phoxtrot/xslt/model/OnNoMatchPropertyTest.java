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
package com.helger.phoxtrot.xslt.model;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the built-in template rules of the
 * <code>on-no-match</code> values of xsl:mode ({@link EOnNoMatch}, XSLT 3.0
 * section 6.7) on generated documents ({@link RandomXmlDocument}), together
 * with a user rule for the elements named <code>b</code>: the result equals the
 * result of the rules of the specification applied in Java to the JDK DOM,
 * including the parameters (tunnel and non-tunnel) that the built-in rules pass
 * on.
 *
 * @author Philip Helger
 */
public final class OnNoMatchPropertyTest
{
  /** The tested values of on-no-match ("fail" has no result) */
  private enum EMode
  {
    SHALLOW_COPY ("shallow-copy"),
    DEEP_COPY ("deep-copy"),
    TEXT_ONLY_COPY ("text-only-copy"),
    SHALLOW_SKIP ("shallow-skip"),
    DEEP_SKIP ("deep-skip");

    private final String m_sAttrValue;

    EMode (@NonNull final String sAttrValue)
    {
      m_sAttrValue = sAttrValue;
    }
  }

  private static final String NONE = "none";

  private static final Map <EMode, XsltExecutable> SHEETS = _compileAll ();

  @NonNull
  private static Map <EMode, XsltExecutable> _compileAll ()
  {
    final Map <EMode, XsltExecutable> ret = new EnumMap <> (EMode.class);
    for (final EMode eMode : EMode.values ())
      ret.put (eMode,
               XsltCompiler.compile (XsltTestHelper.SHEET +
                                     "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
                                     "<xsl:mode name='m' on-no-match='" +
                                     eMode.m_sAttrValue +
                                     "'/>" +
                                     "<xsl:template match='/'><w>" +
                                     "<out><xsl:apply-templates select='/' mode='m'>" +
                                     "<xsl:with-param name='p' select=\"'P'\"/><xsl:with-param name='t' select=\"'T'\" tunnel='yes'/>" +
                                     "</xsl:apply-templates></out>" +
                                     "<out><xsl:apply-templates select='/node()' mode='m'>" +
                                     "<xsl:with-param name='p' select=\"'P'\"/><xsl:with-param name='t' select=\"'T'\" tunnel='yes'/>" +
                                     "</xsl:apply-templates></out>" +
                                     "</w></xsl:template>" +
                                     "<xsl:template match='*:b' mode='m'>" +
                                     "<xsl:param name='p' select=\"'" +
                                     NONE +
                                     "'\"/><xsl:param name='t' select=\"'" +
                                     NONE +
                                     "'\" tunnel='yes'/>" +
                                     "<hit p='{$p}' t='{$t}' n='{name()}'>" +
                                     "<xsl:apply-templates select='@*' mode='#current'><xsl:sort select='name()'/></xsl:apply-templates>" +
                                     "<xsl:apply-templates mode='#current'/>" +
                                     "</hit></xsl:template>" +
                                     XsltTestHelper.END,
                                     new XsltCompileOptions ()));
    return ret;
  }

  /** The attributes of an element (none for a document node) without the namespace declarations, sorted by name() */
  @NonNull
  private static List <Attr> _getSortedAttributes (@NonNull final Node aElement)
  {
    final List <Attr> ret = new ArrayList <> ();
    final NamedNodeMap aAttrs = aElement.getAttributes ();
    // A document node has none
    if (aAttrs == null)
      return ret;
    for (int i = 0; i < aAttrs.getLength (); ++i)
    {
      final Attr aAttr = (Attr) aAttrs.item (i);
      if (!"http://www.w3.org/2000/xmlns/".equals (aAttr.getNamespaceURI ()))
        ret.add (aAttr);
    }
    ret.sort ( (a, b) -> a.getNodeName ().compareTo (b.getNodeName ()));
    return ret;
  }

  private static void _applyToChildren (@NonNull final EMode eMode,
                                        @NonNull final Node aNode,
                                        @NonNull final Node aTarget,
                                        @NonNull final String sParam,
                                        @NonNull final String sTunnel)
  {
    for (Node aChild = aNode.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
      _applyRecursive (eMode, aChild, aTarget, sParam, sTunnel);
  }

  private static void _applyToAttributes (@NonNull final EMode eMode,
                                          @NonNull final Node aNode,
                                          @NonNull final Node aTarget,
                                          @NonNull final String sParam,
                                          @NonNull final String sTunnel)
  {
    for (final Attr aAttr : _getSortedAttributes (aNode))
      _applyRecursive (eMode, aAttr, aTarget, sParam, sTunnel);
  }

  /**
   * Apply the template rules of mode <code>m</code> to one node, appending
   * the result to the target.
   */
  private static void _applyRecursive (@NonNull final EMode eMode,
                                       @NonNull final Node aNode,
                                       @NonNull final Node aTarget,
                                       @NonNull final String sParam,
                                       @NonNull final String sTunnel)
  {
    final Document aOwner = aTarget.getOwnerDocument ();
    if (aNode.getNodeType () == Node.ELEMENT_NODE && "b".equals (aNode.getLocalName ()))
    {
      // The user rule: no non-tunnel parameter is passed on
      final Element aHit = aOwner.createElementNS (null, "hit");
      aHit.setAttributeNS (null, "p", sParam);
      aHit.setAttributeNS (null, "t", sTunnel);
      aHit.setAttributeNS (null, "n", aNode.getNodeName ());
      aTarget.appendChild (aHit);
      _applyToAttributes (eMode, aNode, aHit, NONE, sTunnel);
      _applyToChildren (eMode, aNode, aHit, NONE, sTunnel);
      return;
    }
    final short nType = aNode.getNodeType ();
    switch (eMode)
    {
      case SHALLOW_COPY:
        switch (nType)
        {
          case Node.DOCUMENT_NODE -> _applyToChildren (eMode, aNode, aTarget, sParam, sTunnel);
          case Node.ELEMENT_NODE ->
          {
            final Element aCopy = aOwner.createElementNS (aNode.getNamespaceURI (), aNode.getNodeName ());
            aTarget.appendChild (aCopy);
            _applyToAttributes (eMode, aNode, aCopy, sParam, sTunnel);
            _applyToChildren (eMode, aNode, aCopy, sParam, sTunnel);
          }
          case Node.ATTRIBUTE_NODE -> ((Element) aTarget).setAttributeNS (aNode.getNamespaceURI (),
                                                                          aNode.getNodeName (),
                                                                          aNode.getNodeValue ());
          default -> aTarget.appendChild (aOwner.importNode (aNode, false));
        }
        break;
      case DEEP_COPY:
        if (nType == Node.DOCUMENT_NODE)
        {
          for (Node aChild = aNode.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
            aTarget.appendChild (aOwner.importNode (aChild, true));
        }
        else
          if (nType == Node.ATTRIBUTE_NODE)
            ((Element) aTarget).setAttributeNS (aNode.getNamespaceURI (), aNode.getNodeName (), aNode.getNodeValue ());
          else
            aTarget.appendChild (aOwner.importNode (aNode, true));
        break;
      case TEXT_ONLY_COPY:
        switch (nType)
        {
          case Node.DOCUMENT_NODE, Node.ELEMENT_NODE -> _applyToChildren (eMode, aNode, aTarget, sParam, sTunnel);
          case Node.TEXT_NODE, Node.CDATA_SECTION_NODE, Node.ATTRIBUTE_NODE -> aTarget.appendChild (aOwner.createTextNode (aNode.getNodeValue ()));
          default ->
          {
            // comments and processing instructions give nothing
          }
        }
        break;
      case SHALLOW_SKIP:
        if (nType == Node.DOCUMENT_NODE || nType == Node.ELEMENT_NODE)
        {
          _applyToAttributes (eMode, aNode, aTarget, sParam, sTunnel);
          _applyToChildren (eMode, aNode, aTarget, sParam, sTunnel);
        }
        break;
      case DEEP_SKIP:
        if (nType == Node.DOCUMENT_NODE)
          _applyToChildren (eMode, aNode, aTarget, sParam, sTunnel);
        break;
    }
  }

  /** The canonical form of the expected result of both apply-templates of the initial template */
  @NonNull
  private static String _expected (@NonNull final EMode eMode, @NonNull final Node aSource) throws Exception
  {
    final DocumentBuilderFactory aDBF = DocumentBuilderFactory.newInstance ();
    aDBF.setNamespaceAware (true);
    final Document aResult = aDBF.newDocumentBuilder ().newDocument ();
    final Element aW = aResult.createElementNS (null, "w");
    aResult.appendChild (aW);
    final Element aOut1 = aResult.createElementNS (null, "out");
    aW.appendChild (aOut1);
    _applyRecursive (eMode, aSource, aOut1, "P", "T");
    final Element aOut2 = aResult.createElementNS (null, "out");
    aW.appendChild (aOut2);
    for (Node aChild = aSource.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
      _applyRecursive (eMode, aChild, aOut2, "P", "T");
    // Adjacent text nodes are one, empty ones none
    aResult.normalize ();
    return RandomXmlDocument.getCanonical (aResult);
  }

  @Nullable
  private static String _run (@NonNull final EMode eMode, @NonNull final String sSource)
  {
    return SHEETS.get (eMode)
                 .transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                 .principalSerialized ();
  }

  /**
   * Every on-no-match value except "fail" gives the result of its built-in
   * rules of section 6.7 combined with the user rule, for the document node
   * and for its children as the selected nodes.
   */
  @Test
  public void testBuiltInRulesMatchSpecification ()
  {
    new PropertyChecker ().setTries (500).forAll (RandomXmlDocument.DOCUMENTS, aDoc -> {
      final String sSource = aDoc.toString ();
      final Node aSourceDom = RandomXmlDocument.parseWithJdk (sSource);
      for (final EMode eMode : EMode.values ())
      {
        final String sResult = _run (eMode, sSource);
        assertEquals (eMode + " on " + sSource + " gave " + sResult,
                      _expected (eMode, aSourceDom),
                      RandomXmlDocument.getCanonical (RandomXmlDocument.parseWithJdk (sResult)));
      }
    });
  }
}

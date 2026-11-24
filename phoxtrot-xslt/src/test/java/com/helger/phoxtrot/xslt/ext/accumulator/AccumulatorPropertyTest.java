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
package com.helger.phoxtrot.xslt.ext.accumulator;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for accumulators ({@link AccumulatorExtension}, XSLT
 * 3.0 section 18.2) on buffered documents against the formal model of section
 * 18.2.4 computed in Java: a traversal with a start and an end event per node,
 * the last matching rule in document order of the event's phase applied, the
 * pre-descent value after the start event and the post-descent value after the
 * end event of a node.
 *
 * @author Philip Helger
 */
public final class AccumulatorPropertyTest
{
  /** The values of all accumulators at one point of the traversal */
  private static final class State
  {
    private int m_nCountA;
    private int m_nDepth;
    private final List <String> m_aPath = new ArrayList <> ();
    private int m_nTextLength;
    private int m_nKSum;
    private String m_sKind = "-";

    @NonNull
    String getAsString ()
    {
      return m_nCountA + "/" + m_nDepth + "/" + String.join (".", m_aPath) + "/" + m_nTextLength + "/" + m_nKSum + "/" + m_sKind;
    }
  }

  private static final String ACCUMULATORS = "<xsl:accumulator name='countA' initial-value='0' as='xs:integer'>" +
                                             "<xsl:accumulator-rule match='a' select='$value + 1'/></xsl:accumulator>" +
                                             "<xsl:accumulator name='depth' initial-value='0' as='xs:integer'>" +
                                             "<xsl:accumulator-rule match='*' phase='start' select='$value + 1'/>" +
                                             "<xsl:accumulator-rule match='*' phase='end' select='$value - 1'/></xsl:accumulator>" +
                                             "<xsl:accumulator name='path' initial-value='()' as='xs:string*'>" +
                                             "<xsl:accumulator-rule match='*' select='$value, name()'/>" +
                                             "<xsl:accumulator-rule match='*' phase='end' select='$value[position() lt last()]'/></xsl:accumulator>" +
                                             "<xsl:accumulator name='textLength' initial-value='0' as='xs:integer'>" +
                                             "<xsl:accumulator-rule match='text()' select='$value + string-length()'/></xsl:accumulator>" +
                                             "<xsl:accumulator name='kSum' initial-value='0' as='xs:integer'>" +
                                             "<xsl:accumulator-rule match='*[@k]' phase='end'>" +
                                             "<xsl:sequence select='$value + (if (@k castable as xs:integer) then xs:integer(@k) else 0)'/>" +
                                             "</xsl:accumulator-rule></xsl:accumulator>" +
                                             "<xsl:accumulator name='kind' initial-value=\"'-'\" as='xs:string'>" +
                                             "<xsl:accumulator-rule match='b' select=\"'B'\"/>" +
                                             "<xsl:accumulator-rule match='*' select=\"'E'\"/>" +
                                             "<xsl:accumulator-rule match='c' select=\"'C'\"/>" +
                                             "<xsl:accumulator-rule match='text()' select=\"'T'\"/>" +
                                             "<xsl:accumulator-rule match='c' phase='end' select=\"$value || 'c'\"/>" +
                                             "</xsl:accumulator>";

  private static final String [] NAMES = { "countA", "depth", "path", "textLength", "kSum", "kind" };

  private static final XsltExecutable SHEET = _compile ();

  @NonNull
  private static XsltExecutable _compile ()
  {
    final StringBuilder aBefore = new StringBuilder ();
    final StringBuilder aAfter = new StringBuilder ();
    for (final String sName : NAMES)
    {
      if (aBefore.length () > 0)
      {
        aBefore.append (", ");
        aAfter.append (", ");
      }
      aBefore.append ("string-join(accumulator-before('").append (sName).append ("'), '.')");
      aAfter.append ("string-join(accumulator-after('").append (sName).append ("'), '.')");
    }
    return XsltCompiler.compile (XsltTestHelper.SHEET +
                                 ACCUMULATORS +
                                 "<xsl:mode use-accumulators='#all'/>" +
                                 "<xsl:output method='text'/>" +
                                 "<xsl:template match='/'>" +
                                 "<xsl:for-each select='descendant-or-self::node()'>" +
                                 "<xsl:value-of select=\"if (self::text()) then '#t' else if (self::*) then name() else '/'\"/>:" +
                                 "<xsl:value-of select=\"" +
                                 aBefore +
                                 "\" separator='/'/>|<xsl:value-of select=\"" +
                                 aAfter +
                                 "\" separator='/'/>;" +
                                 "</xsl:for-each>" +
                                 "</xsl:template>" +
                                 XsltTestHelper.END,
                                 new XsltCompileOptions ());
  }

  private static int _getKValue (@NonNull final Element aElement)
  {
    try
    {
      return Integer.parseInt (aElement.getAttribute ("k"));
    }
    catch (final NumberFormatException ex)
    {
      return 0;
    }
  }

  private static void _startEvent (@NonNull final State aState, @NonNull final Node aNode)
  {
    if (aNode.getNodeType () == Node.ELEMENT_NODE)
    {
      final String sName = aNode.getNodeName ();
      if (sName.equals ("a"))
        aState.m_nCountA++;
      aState.m_nDepth++;
      aState.m_aPath.add (sName);
      // The last matching rule in document order wins
      aState.m_sKind = sName.equals ("c") ? "C" : "E";
    }
    else
      if (aNode.getNodeType () == Node.TEXT_NODE)
      {
        aState.m_nTextLength += aNode.getNodeValue ().codePointCount (0, aNode.getNodeValue ().length ());
        aState.m_sKind = "T";
      }
  }

  private static void _endEvent (@NonNull final State aState, @NonNull final Node aNode)
  {
    if (aNode.getNodeType () == Node.ELEMENT_NODE)
    {
      aState.m_nDepth--;
      aState.m_aPath.removeLast ();
      final Element aElement = (Element) aNode;
      if (aElement.hasAttribute ("k"))
        aState.m_nKSum += _getKValue (aElement);
      if (aNode.getNodeName ().equals ("c"))
        aState.m_sKind = aState.m_sKind + "c";
    }
  }

  /** Walk the tree in traversal order, recording the pre-descent and post-descent values of each node in document order. */
  private static void _traverseRecursive (@NonNull final Node aNode,
                                          @NonNull final State aState,
                                          @NonNull final List <String> aLines)
  {
    _startEvent (aState, aNode);
    final int nIndex = aLines.size ();
    final String sLabel = switch (aNode.getNodeType ())
    {
      case Node.TEXT_NODE -> "#t";
      case Node.ELEMENT_NODE -> aNode.getNodeName ();
      default -> "/";
    };
    aLines.add (sLabel + ":" + aState.getAsString () + "|");
    for (Node aChild = aNode.getFirstChild (); aChild != null; aChild = aChild.getNextSibling ())
      _traverseRecursive (aChild, aState, aLines);
    _endEvent (aState, aNode);
    aLines.set (nIndex, aLines.get (nIndex) + aState.getAsString () + ";");
  }

  /**
   * The pre-descent and post-descent values of six accumulators (start and
   * end rules, a sequence type, text nodes, a rule with a sequence
   * constructor, several matching rules) at every node equal the formal model
   * computed in Java.
   */
  @Test
  public void testAccumulatorsMatchFormalModel ()
  {
    new PropertyChecker ().setTries (1000).forAll (RandomXmlDocument.simpleDocuments (4, 8), sSource -> {
      final List <String> aLines = new ArrayList <> ();
      _traverseRecursive (RandomXmlDocument.parseWithJdk (sSource), new State (), aLines);
      final String sResult = SHEET.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                                  .principalSerialized ();
      assertEquals (sSource, String.join ("", aLines), sResult);
    });
  }
}

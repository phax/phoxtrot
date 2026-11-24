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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the laws of the {@link IXdmNode} API on documents of
 * {@link XdmDocumentGenerator}: {@link IXdmNode#compareDocumentOrder(IXdmNode, IXdmNode)} is the
 * total order of the XDM (an element, then its namespace nodes, then its attributes, then its
 * children; nodes of different trees all in one direction), the namespace queries agree with a
 * reference computed from the declarations of the element and its ancestors, and the navigation
 * and string value accessors agree with each other.
 *
 * @author Philip Helger
 */
public final class XdmNodeApiPropertyTest
{
  /** Prefixes to look up: those of the generator, the reserved ones and an unused one */
  private static final String [] PREFIXES = { "", "p", "q", "r", "xml", "xmlns", "s" };

  /**
   * @return All nodes of the tree in document order: each element followed by its namespace nodes
   *         (in the order of {@link IXdmNode#getNamespaceNodes()}), its attributes and its
   *         children.
   */
  @NonNull
  private static ICommonsList <IXdmNode> _getAllNodesInOrder (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      ret.add (aCur);
      ret.addAll (aCur.getNamespaceNodes ());
      ret.addAll (aCur.getAllAttributes ());
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return ret;
  }

  private static boolean _isNamespace (@NonNull final IXdmNode aNode)
  {
    return aNode.getKind () == ENodeKind.NAMESPACE;
  }

  /**
   * compareDocumentOrder agrees with the reference order on every pair of nodes of a tree
   * (namespace nodes of one element only need to be ordered consistently among themselves), and
   * orders all nodes of one tree before or after all nodes of another.
   */
  @Test
  public void testDocumentOrderIsTotal ()
  {
    new PropertyChecker ().setTries (150).forAll (XdmDocumentGenerator.documents (), XdmDocumentGenerator.documents (), (aGen1, aGen2) -> {
      final IXdmNode aDoc1 = new XdmParser ().parse (aGen1.getAsXml ());
      final IXdmNode aDoc2 = new XdmParser ().parse (aGen2.getAsXml ());
      final ICommonsList <IXdmNode> aNodes = _getAllNodesInOrder (aDoc1);
      for (int i = 0; i < aNodes.size (); ++i)
        for (int j = 0; j < aNodes.size (); ++j)
        {
          final IXdmNode aLeft = aNodes.get (i);
          final IXdmNode aRight = aNodes.get (j);
          final int nCmp = Integer.signum (IXdmNode.compareDocumentOrder (aLeft, aRight));
          final String sMsg = i + ":" + aLeft + " vs " + j + ":" + aRight;
          assertEquals (sMsg, -nCmp, Integer.signum (IXdmNode.compareDocumentOrder (aRight, aLeft)));
          if (_isNamespace (aLeft) && _isNamespace (aRight) && aLeft.getParent () == aRight.getParent ())
            assertEquals (sMsg, Boolean.valueOf (i == j), Boolean.valueOf (nCmp == 0));
          else
            assertEquals (sMsg, Integer.signum (Integer.compare (i, j)), nCmp);
        }
      final int nCross = Integer.signum (IXdmNode.compareDocumentOrder (aDoc1, aDoc2));
      assertTrue (nCross != 0);
      for (final IXdmNode aNode1 : aNodes)
        for (final IXdmNode aNode2 : _getAllNodesInOrder (aDoc2))
          assertEquals (aNode1 + " vs " + aNode2, nCross, Integer.signum (IXdmNode.compareDocumentOrder (aNode1, aNode2)));
    });
  }

  /**
   * @return The reference in-scope namespaces of an element: the declarations of the element and
   *         its ancestors, the closest winning, an undeclared default namespace left out, and the
   *         <code>xml</code> prefix.
   */
  @NonNull
  private static Map <String, String> _referenceInScope (@NonNull final IXdmNode aElement)
  {
    final Map <String, String> aCollected = new LinkedHashMap <> ();
    for (IXdmNode aCur = aElement; aCur != null && aCur.isElement (); aCur = aCur.getParent ())
      for (final Map.Entry <String, String> aEntry : aCur.getDeclaredNamespaces ().entrySet ())
        aCollected.putIfAbsent (aEntry.getKey (), aEntry.getValue ());
    final Map <String, String> ret = new LinkedHashMap <> ();
    for (final Map.Entry <String, String> aEntry : aCollected.entrySet ())
      if (!aEntry.getValue ().isEmpty ())
        ret.put (aEntry.getKey (), aEntry.getValue ());
    ret.put ("xml", XdmConstants.NS_XML);
    return ret;
  }

  /**
   * @return The concatenated text descendants of a node, walked iteratively.
   */
  @NonNull
  private static String _referenceStringValue (@NonNull final IXdmNode aNode)
  {
    final StringBuilder aSB = new StringBuilder ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aNode);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aCur.isText ())
        aSB.append (aCur.getValue ());
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return aSB.toString ();
  }

  /**
   * The namespace, attribute, navigation and string value accessors of every element agree with
   * each other and with references computed from the declarations and children.
   */
  @Test
  public void testAccessorsAgree ()
  {
    new PropertyChecker ().setTries (300).forAll (XdmDocumentGenerator.documents (), aGenDoc -> {
      final IXdmNode aDoc = new XdmParser ().parse (aGenDoc.getAsXml ());
      assertEquals (_referenceStringValue (aDoc), aDoc.getStringValue ());
      for (final IXdmNode aNode : _getAllNodesInOrder (aDoc))
      {
        final String sMsg = aNode.toString ();
        assertSame (sMsg, aDoc, aNode.getRoot ());
        assertSame (sMsg, aDoc, aNode.getOwnerDocument ());
        for (int i = 0; i < aNode.getChildCount (); ++i)
        {
          assertSame (sMsg, aNode, aNode.getChildAtIndex (i).getParent ());
          assertSame (sMsg, aNode.getChildAtIndex (i), aNode.getAllChildren ().get (i));
        }
        if (!aNode.isElement ())
          continue;
        assertEquals (sMsg, _referenceStringValue (aNode), aNode.getStringValue ());

        // Declarations: the three accessors agree
        final Map <String, String> aDeclared = aNode.getDeclaredNamespaces ();
        assertEquals (sMsg, Boolean.valueOf (!aDeclared.isEmpty ()), Boolean.valueOf (aNode.hasDeclaredNamespaces ()));
        assertEquals (sMsg, aDeclared, aNode.declaredNamespaces ());
        for (final String sPrefix : PREFIXES)
          assertEquals (sMsg + " " + sPrefix, aDeclared.get (sPrefix), aNode.getDeclaredNamespaceUri (sPrefix));

        // In-scope namespaces, lookups and namespace nodes
        final Map <String, String> aInScope = aNode.getInScopeNamespaces ();
        assertEquals (sMsg, _referenceInScope (aNode), aInScope);
        for (final String sPrefix : PREFIXES)
          assertEquals (sMsg + " " + sPrefix, aInScope.get (sPrefix), aNode.lookupNamespaceUri (sPrefix));
        final ICommonsList <IXdmNode> aNamespaceNodes = aNode.getNamespaceNodes ();
        assertEquals (sMsg, aInScope.size (), aNamespaceNodes.size ());
        final ICommonsList <IXdmNode> aAgain = aNode.getNamespaceNodes ();
        for (int i = 0; i < aNamespaceNodes.size (); ++i)
        {
          final IXdmNode aNamespace = aNamespaceNodes.get (i);
          assertSame (sMsg, aNamespace, aAgain.get (i));
          assertSame (sMsg, aNode, aNamespace.getParent ());
          // The namespace node of the default namespace has no name
          final String sPrefix = aNamespace.getName () == null ? "" : aNamespace.getName ().localName ();
          assertEquals (sMsg, aInScope.get (sPrefix), aNamespace.getValue ());
        }

        // Attributes are found by their expanded name
        for (int i = 0; i < aNode.getAttributeCount (); ++i)
        {
          final IXdmNode aAttr = aNode.getAttributeAtIndex (i);
          final XdmQName aName = aAttr.getName ();
          assertSame (sMsg, aNode, aAttr.getParent ());
          assertSame (sMsg, aAttr, aNode.getAttribute (aName.namespaceUri (), aName.localName ()));
          if (!aName.hasNamespace ())
            assertSame (sMsg, aAttr, aNode.getAttribute (null, aName.localName ()));
          assertEquals (sMsg, aAttr.getValue (), aAttr.getStringValue ());
        }
        assertNull (sMsg, aNode.getAttribute ("urn:none", "a"));
      }
    });
  }
}

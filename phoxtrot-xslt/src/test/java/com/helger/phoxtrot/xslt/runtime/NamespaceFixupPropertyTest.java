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

import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for {@link NamespaceFixup#finish(IXdmNode)}: fixing the namespaces in the
 * numbering walk must give the same declarations and the same document order as the separate
 * fixup walk followed by the numbering, for trees with namespaced elements and attributes, own
 * declarations and undeclarations of the default namespace, rooted in a document or in a
 * parentless element.
 *
 * @author Philip Helger
 */
public final class NamespaceFixupPropertyTest
{
  private static final String [] LOCAL_NAMES = { "a", "b" };
  private static final String [] URIS = { "", "urn:a", "urn:b" };
  private static final String [] PREFIXES = { "", "p", "q" };
  private static final int MAX_DEPTH = 5;

  private static int _draw (@NonNull final ChoiceSequence aChoices, final int nMaxInclusive)
  {
    return (int) aChoices.draw (nMaxInclusive);
  }

  @NonNull
  private static XdmQName _drawName (@NonNull final ChoiceSequence aChoices)
  {
    final String sLocalName = LOCAL_NAMES[_draw (aChoices, LOCAL_NAMES.length - 1)];
    final String sUri = URIS[_draw (aChoices, URIS.length - 1)];
    final String sPrefix = sUri.isEmpty () ? "" : PREFIXES[_draw (aChoices, PREFIXES.length - 1)];
    return new XdmQName (sUri, sLocalName, sPrefix);
  }

  /** Draw an element with declarations and attributes, without children. */
  @NonNull
  private static IXdmNode _drawElement (@NonNull final ChoiceSequence aChoices)
  {
    final IXdmNode ret = IXdmNode.createElement (_drawName (aChoices));
    final int nDecls = _draw (aChoices, 2);
    for (int i = 0; i < nDecls; ++i)
    {
      final String sPrefix = PREFIXES[_draw (aChoices, PREFIXES.length - 1)];
      final String sUri = URIS[_draw (aChoices, URIS.length - 1)];
      ret.declareNamespace (sPrefix, sUri);
    }
    final int nAttrs = _draw (aChoices, 3);
    for (int i = 0; i < nAttrs; ++i)
    {
      final XdmQName aName = _draw (aChoices, 5) == 5 ? new XdmQName (XdmConstants.NS_XML, "lang", "xml")
                                                      : _drawName (aChoices);
      if (ret.getAttribute (aName.namespaceUri (), aName.localName ()) == null)
        ret.addAttribute (IXdmNode.createAttribute (aName, "v" + i));
    }
    return ret;
  }

  /** Draw a tree, rooted in a document or a parentless element, iteratively. */
  @NonNull
  private static IXdmNode _drawTree (@NonNull final ChoiceSequence aChoices)
  {
    final IXdmNode aRoot;
    final IXdmNode aTop;
    if (_draw (aChoices, 1) == 0)
    {
      aRoot = IXdmNode.createDocument ();
      aTop = _drawElement (aChoices);
      aRoot.appendChild (aTop);
    }
    else
    {
      aRoot = _drawElement (aChoices);
      aTop = aRoot;
    }
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    final ArrayDeque <Integer> aDepths = new ArrayDeque <> ();
    aStack.push (aTop);
    aDepths.push (Integer.valueOf (1));
    while (!aStack.isEmpty ())
    {
      final IXdmNode aElement = aStack.pop ();
      final int nDepth = aDepths.pop ().intValue ();
      if (nDepth >= MAX_DEPTH)
        continue;
      final int nChildren = _draw (aChoices, 3);
      for (int i = 0; i < nChildren; ++i)
        if (_draw (aChoices, 3) == 0)
          aElement.appendChild (IXdmNode.createText ("t" + i));
        else
        {
          final IXdmNode aChild = _drawElement (aChoices);
          aElement.appendChild (aChild);
          aStack.push (aChild);
          aDepths.push (Integer.valueOf (nDepth + 1));
        }
    }
    return aRoot;
  }

  /** @return A copy of a tree drawn by {@link #_drawTree(ChoiceSequence)}, iteratively. */
  @NonNull
  private static IXdmNode _copy (@NonNull final IXdmNode aRoot)
  {
    final ArrayDeque <IXdmNode []> aStack = new ArrayDeque <> ();
    final IXdmNode ret = aRoot.isDocument () ? IXdmNode.createDocument () : null;
    IXdmNode aCopyRoot = ret;
    aStack.push (new IXdmNode [] { aRoot, null });
    while (!aStack.isEmpty ())
    {
      final IXdmNode [] aCur = aStack.pop ();
      final IXdmNode aNode = aCur[0];
      final IXdmNode aCopy;
      if (aNode.isDocument ())
        aCopy = ret;
      else
        if (aNode.isText ())
          aCopy = IXdmNode.createText (aNode.getValue ());
        else
        {
          aCopy = IXdmNode.createElement (aNode.getName ());
          aNode.getDeclaredNamespaces ().forEach (aCopy::declareNamespace);
          for (int i = 0; i < aNode.getAttributeCount (); ++i)
          {
            final IXdmNode aAttr = aNode.getAttributeAtIndex (i);
            aCopy.addAttribute (IXdmNode.createAttribute (aAttr.getName (), aAttr.getValue ()));
          }
        }
      if (aCur[1] != null)
        aCur[1].appendChild (aCopy);
      else
        aCopyRoot = aCopy;
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (new IXdmNode [] { aNode.getChildAtIndex (i), aCopy });
    }
    return aCopyRoot;
  }

  /**
   * @return One line per node in document order: its order and end order, and for an element its
   *         name and its own declarations.
   */
  @NonNull
  private static ICommonsList <String> _describe (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aNode = aStack.pop ();
      String sLine = aNode.getOrder () + "-" + aNode.getEndOrder ();
      if (aNode.isElement ())
        sLine += " " + aNode.getName ().prefix () + ":" + aNode.getName () + " " + aNode.getDeclaredNamespaces ();
      ret.add (sLine);
      for (int i = 0; i < aNode.getAttributeCount (); ++i)
        ret.add ("@" + aNode.getAttributeAtIndex (i).getOrder ());
      for (int i = aNode.getChildCount () - 1; i >= 0; --i)
        aStack.push (aNode.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * The fixup in the numbering walk ({@link NamespaceFixup#finish(IXdmNode)}) gives the same
   * declarations and numbers as {@link NamespaceFixup#fixNamespaces(IXdmNode)} followed by
   * {@link IXdmNode#assignDocumentOrder()}.
   */
  @Test
  public void testFixupInNumberingWalk ()
  {
    new PropertyChecker ().setTries (2000).forAll (NamespaceFixupPropertyTest::_drawTree, aRoot -> {
      final IXdmNode aExpected = _copy (aRoot);
      NamespaceFixup.fixNamespaces (aExpected);
      aExpected.assignDocumentOrder ();
      NamespaceFixup.finish (aRoot);
      assertEquals (_describe (aExpected), _describe (aRoot));
    });
  }
}

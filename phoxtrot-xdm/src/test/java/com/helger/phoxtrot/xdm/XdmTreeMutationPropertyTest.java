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
import static org.junit.Assert.fail;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for structural changes of a parsed tree ({@link IXdmNode#appendChild(IXdmNode)},
 * {@link IXdmNode#removeChild(IXdmNode)}, {@link IXdmNode#addAttribute(IXdmNode)}) interleaved
 * with the queries that depend on the numbering: document order, the name indexes of
 * {@link XdmDocumentNode} and the maximum element depth. A mirror of the child lists serves as
 * model.
 *
 * @author Philip Helger
 */
public final class XdmTreeMutationPropertyTest
{
  private static final String [] NAMES = { "a", "b", "x-y" };

  /** Kinds of operations */
  private static final int OP_APPEND_ELEMENT = 0;
  private static final int OP_APPEND_TEXT = 1;
  private static final int OP_APPEND_COMMENT = 2;
  private static final int OP_REMOVE = 3;
  private static final int OP_ADD_ATTRIBUTE = 4;
  private static final int OP_MOVE = 5;
  private static final int OP_COMPARE_ORDER = 6;
  private static final int OP_CHECK_INDEXES = 7;
  private static final int OP_RENUMBER = 8;

  /**
   * One operation: the kind and two selectors, taken modulo the number of candidates at the time
   * the operation is applied.
   */
  public record Op (int kind, int first, int second)
  {}

  private static final IGenerator <Op> OPS = aChoices -> new Op ((int) aChoices.draw (OP_RENUMBER),
                                                                 (int) aChoices.draw (1000),
                                                                 (int) aChoices.draw (1000));

  /** @return All elements of the tree in document order. */
  @NonNull
  private static ICommonsList <IXdmNode> _getElements (@NonNull final IXdmNode aRoot)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aCur.isElement ())
        ret.add (aCur);
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return ret;
  }

  /** @return All nodes of the tree in document order, attributes after their element. */
  @NonNull
  private static ICommonsList <IXdmNode> _getNodes (@NonNull final IXdmNode aRoot)
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

  /** Fill the mirror with the child lists of the document and all its elements. */
  private static void _fillMirror (@NonNull final IXdmNode aRoot, @NonNull final Map <IXdmNode, ICommonsList <IXdmNode>> aMirror)
  {
    final ICommonsList <IXdmNode> aParents = _getElements (aRoot);
    aParents.add (aRoot);
    for (final IXdmNode aParent : aParents)
      aMirror.put (aParent, new CommonsArrayList <> (aParent.getAllChildren ()));
  }

  /** Check that the tree reachable from the document has exactly the children of the mirror. */
  private static void _assertMirror (@NonNull final IXdmNode aDoc, @NonNull final Map <IXdmNode, ICommonsList <IXdmNode>> aMirror)
  {
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aDoc);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      final ICommonsList <IXdmNode> aExpected = aMirror.get (aCur);
      assertEquals (aExpected.size (), aCur.getChildCount ());
      for (int i = 0; i < aExpected.size (); ++i)
      {
        final IXdmNode aChild = aCur.getChildAtIndex (i);
        assertSame (aExpected.get (i), aChild);
        assertSame (aCur, aChild.getParent ());
        assertSame (aDoc, aChild.getRoot ());
        if (aChild.isElement ())
          aStack.push (aChild);
      }
    }
  }

  /**
   * Check the name indexes: each is either absent (the tree is not numbered) or equal to a walk
   * over the tree.
   */
  private static void _assertIndexesUpToDate (@NonNull final XdmDocumentNode aDoc)
  {
    final ICommonsList <IXdmNode> aNodes = _getNodes (aDoc);
    for (final String sName : NAMES)
    {
      final XdmQName aName = new XdmQName (sName);
      final List <IXdmNode> aElements = aDoc.getElementsByName (aName);
      if (aElements != null)
        assertEquals (aNodes.getAll (x -> x.isElement () && x.getName ().equals (aName)), aElements);
      final List <IXdmNode> aAttrs = aDoc.getAttributesByName (aName);
      if (aAttrs != null)
        assertEquals (aNodes.getAll (x -> x.isAttribute () && x.getName ().equals (aName)), aAttrs);
      for (final IXdmNode aParent : aNodes.getAll (IXdmNode::isElement))
      {
        final List <IXdmNode> aChildren = aDoc.getChildElementsByName (aParent, aName);
        if (aChildren != null)
          assertEquals (aParent.getAllChildren ().getAll (x -> x.isElement () && x.getName ().equals (aName)),
                        aChildren);
      }
    }
  }

  /**
   * Apply an operation to the tree and the mirror.
   *
   * @return <code>true</code> if the structure of the tree changed.
   */
  private static boolean _apply (@NonNull final XdmDocumentNode aDoc,
                              @NonNull final Map <IXdmNode, ICommonsList <IXdmNode>> aMirror,
                              @NonNull final Op aOp)
  {
    final ICommonsList <IXdmNode> aElements = _getElements (aDoc);
    final IXdmNode aTarget = aElements.get (aOp.first () % aElements.size ());
    final String sName = NAMES[aOp.second () % NAMES.length];
    switch (aOp.kind ())
    {
      case OP_APPEND_ELEMENT:
      {
        final IXdmNode aNew = IXdmNode.createElement (new XdmQName (sName));
        aTarget.appendChild (aNew);
        aMirror.get (aTarget).add (aNew);
        aMirror.put (aNew, new CommonsArrayList <> ());
        return true;
      }
      case OP_APPEND_TEXT:
      case OP_APPEND_COMMENT:
      {
        final IXdmNode aNew = aOp.kind () == OP_APPEND_TEXT ? IXdmNode.createText (sName) : IXdmNode.createComment (sName);
        aTarget.appendChild (aNew);
        aMirror.get (aTarget).add (aNew);
        return true;
      }
      case OP_REMOVE:
      case OP_MOVE:
      {
        if (aTarget.getChildCount () == 0)
          return false;
        final IXdmNode aChild = aTarget.getChildAtIndex (aOp.second () % aTarget.getChildCount ());
        aTarget.removeChild (aChild);
        aMirror.get (aTarget).remove (aChild);
        assertNull (aChild.getParent ());
        assertSame (aChild, aChild.getRoot ());
        if (aOp.kind () == OP_MOVE)
        {
          // The removed subtree is no longer reachable, so it cannot become its own descendant
          final ICommonsList <IXdmNode> aRemaining = _getElements (aDoc);
          final IXdmNode aNewParent = aRemaining.get (aOp.first () % aRemaining.size ());
          aNewParent.appendChild (aChild);
          aMirror.get (aNewParent).add (aChild);
        }
        return true;
      }
      case OP_ADD_ATTRIBUTE:
        if (aTarget.getAttribute (null, sName) != null)
          return false;
        aTarget.addAttribute (IXdmNode.createAttribute (new XdmQName (sName), "v"));
        return true;
      case OP_COMPARE_ORDER:
      {
        final ICommonsList <IXdmNode> aNodes = _getNodes (aDoc);
        final int nLeft = aOp.first () % aNodes.size ();
        final int nRight = aOp.second () % aNodes.size ();
        assertEquals (Integer.signum (Integer.compare (nLeft, nRight)),
                      Integer.signum (IXdmNode.compareDocumentOrder (aNodes.get (nLeft), aNodes.get (nRight))));
        return false;
      }
      case OP_CHECK_INDEXES:
        _assertIndexesUpToDate (aDoc);
        return false;
      default:
        aDoc.assignDocumentOrder ();
        XdmParserPropertyTest.assertReferenceNumbering (aDoc);
        return false;
    }
  }

  /**
   * After any sequence of appends, removals, moves and added attributes, interleaved with document
   * order comparisons, name index lookups and renumbering: the children are those of the model, a
   * removed node is a parentless root, document order comparisons follow the current tree, name
   * indexes are never stale, the recorded maximum element depth is gone after the first change, and
   * a final numbering is the reference numbering with name indexes equal to a walk.
   */
  @Test
  public void testChangesKeepQueriesConsistent ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (XdmDocumentGenerator.documents (),
                                   Generators.lists (OPS, 1, 25),
                                   Generators.booleans (),
                                   (aGenDoc, aOps, aChildrenFirst) -> {
                                     final XdmDocumentNode aDoc = (XdmDocumentNode) new XdmParser ().parse (aGenDoc.getAsXml ());
                                     final Map <IXdmNode, ICommonsList <IXdmNode>> aMirror = new IdentityHashMap <> ();
                                     _fillMirror (aDoc, aMirror);
                                     // Use the indexes of the numbering as built
                                     _assertIndexesUpToDate (aDoc);
                                     boolean bChanged = false;
                                     for (final Op aOp : aOps)
                                     {
                                       if (_apply (aDoc, aMirror, aOp))
                                         bChanged = true;
                                       _assertMirror (aDoc, aMirror);
                                       if (bChanged)
                                         assertEquals (-1, aDoc.getMaxElementDepth ());
                                     }
                                     aDoc.assignDocumentOrder ();
                                     XdmParserPropertyTest.assertReferenceNumbering (aDoc);
                                     XdmParserPropertyTest.assertNameIndexes (aDoc, aChildrenFirst.booleanValue ());
                                   });
  }

  /**
   * Appending a parentless node to itself or to one of its descendants is refused with
   * {@link IllegalArgumentException} and changes nothing: it would make the node its own ancestor,
   * and every walk up to the root or through the tree would loop forever.
   */
  @Test
  public void testAppendAncestorIsRefused ()
  {
    new PropertyChecker ().setTries (100)
                          .forAll (XdmDocumentGenerator.documents (), Generators.ints (0, 1000), (aGenDoc, aSelector) -> {
                            final IXdmNode aDoc = new XdmParser ().parse (aGenDoc.getAsXml ());
                            final ICommonsList <IXdmNode> aElements = _getElements (aDoc);
                            final IXdmNode aRoot = aElements.getFirstOrNull ();
                            aDoc.removeChild (aRoot);
                            // The new parent is the detached element itself or one of its descendants
                            final IXdmNode aNewParent = aElements.get (aSelector.intValue () % aElements.size ());
                            final int nChildren = aNewParent.getChildCount ();
                            try
                            {
                              aNewParent.appendChild (aRoot);
                              fail ("Appending " + aRoot + " below itself was accepted");
                            }
                            catch (final IllegalArgumentException ex)
                            {
                              // Expected
                            }
                            assertNull (aRoot.getParent ());
                            assertEquals (nChildren, aNewParent.getChildCount ());
                          });
  }
}

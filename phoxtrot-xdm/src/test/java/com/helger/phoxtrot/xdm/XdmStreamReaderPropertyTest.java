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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.XdmStreamReader.EEvent;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for {@link XdmStreamReader} with {@link XdmStreamReader#drop(IXdmNode)} and
 * {@link XdmStreamReader#skipSubtree()}: a simulation of the same decisions on the tree of
 * {@link XdmParser} serves as model.
 *
 * @author Philip Helger
 */
public final class XdmStreamReaderPropertyTest
{
  /** An entry of the simulated event walk over a parsed tree. */
  private record SimEntry (IXdmNode node, boolean exit, int depth)
  {}

  private static boolean _isSet (final long nMask, final int nEventIndex)
  {
    return ((nMask >>> (nEventIndex & 63)) & 1) != 0;
  }

  @NonNull
  private static String _describeEvent (@NonNull final EEvent eEvent,
                                        @NonNull final IXdmNode aNode,
                                        final int nDepth)
  {
    final StringBuilder aSB = new StringBuilder ().append (eEvent).append (' ').append (nDepth).append (' ');
    aSB.append (aNode.getKind ()).append (' ');
    if (aNode.getName () != null)
      aSB.append (aNode.getName ().getEQName ()).append (' ');
    if (eEvent == EEvent.LEAF)
      aSB.append ('[').append (aNode.getValue ()).append (']');
    if (eEvent == EEvent.ENTER)
      for (int i = 0; i < aNode.getAttributeCount (); ++i)
        aSB.append ('@').append (aNode.getAttributeAtIndex (i).getName ().getEQName ()).append ('=').append (aNode.getAttributeAtIndex (i).getValue ());
    return aSB.toString ();
  }

  /**
   * Describe the tree without the excluded nodes and their subtrees, one line per node in document
   * order, optionally with the order numbers.
   */
  @NonNull
  private static ICommonsList <String> _describeRemaining (@NonNull final IXdmNode aDoc,
                                                           @NonNull final Set <IXdmNode> aExcluded,
                                                           final boolean bWithOrder)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aDoc);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aExcluded.contains (aCur))
        continue;
      ret.add (aCur.getKind () +
               " " +
               (aCur.getName () == null ? "" : aCur.getName ().getEQName ()) +
               " [" +
               aCur.getValue () +
               "]" +
               (bWithOrder ? " " + aCur.getOrder () : ""));
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.push (aCur.getChildAtIndex (i));
    }
    return ret;
  }

  /**
   * Simulate the events of a stream reader on a parsed tree, with the same drop and skip decisions.
   *
   * @param aEvents
   *        Filled with the expected event descriptions.
   * @param aExcluded
   *        Filled with the nodes that are dropped or skipped.
   */
  private static void _simulate (@NonNull final IXdmNode aDoc,
                                 final long nDropMask,
                                 final long nSkipMask,
                                 @NonNull final ICommonsList <String> aEvents,
                                 @NonNull final Set <IXdmNode> aExcluded)
  {
    final ArrayDeque <SimEntry> aStack = new ArrayDeque <> ();
    for (int i = aDoc.getChildCount () - 1; i >= 0; --i)
      aStack.push (new SimEntry (aDoc.getChildAtIndex (i), false, 1));
    int nEvent = 0;
    while (!aStack.isEmpty ())
    {
      final SimEntry aCur = aStack.pop ();
      final IXdmNode aNode = aCur.node ();
      if (aCur.exit ())
      {
        aEvents.add (_describeEvent (EEvent.EXIT, aNode, aCur.depth ()));
        if (_isSet (nDropMask, nEvent++))
          aExcluded.add (aNode);
      }
      else
        if (aNode.isElement ())
        {
          aEvents.add (_describeEvent (EEvent.ENTER, aNode, aCur.depth ()));
          if (_isSet (nSkipMask, nEvent++))
            aExcluded.add (aNode);
          else
          {
            aStack.push (new SimEntry (aNode, true, aCur.depth ()));
            for (int i = aNode.getChildCount () - 1; i >= 0; --i)
              aStack.push (new SimEntry (aNode.getChildAtIndex (i), false, aCur.depth () + 1));
          }
        }
        else
        {
          aEvents.add (_describeEvent (EEvent.LEAF, aNode, aCur.depth ()));
          if (_isSet (nDropMask, nEvent++))
            aExcluded.add (aNode);
        }
    }
  }

  /**
   * A stream reader that drops completed nodes and skips elements at random returns the events of
   * the parsed tree without the skipped subtrees, creates nodes in increasing document order, and
   * ends with the parsed tree minus the dropped and skipped nodes; without skipping, every node has
   * the order number of the parsed tree.
   */
  @Test
  public void testDropAndSkip ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (XdmDocumentGenerator.documents (),
                                   Generators.longs (),
                                   Generators.longs (),
                                   Generators.booleans (),
                                   (aGenDoc, aDropMask, aSkipMask, aNoSkip) -> {
                                     final byte [] aBytes = aGenDoc.getAsXml ().getBytes (StandardCharsets.UTF_8);
                                     // Skip about one element in four
                                     final long nSkipMask = aNoSkip.booleanValue () ? 0
                                                                                    : aSkipMask.longValue () &
                                                                                      Long.rotateLeft (aSkipMask.longValue (), 17);
                                     final IXdmNode aParsed = new XdmParser ().parse (new InputSource (new ByteArrayInputStream (aBytes)));
                                     final ICommonsList <String> aExpectedEvents = new CommonsArrayList <> ();
                                     final Set <IXdmNode> aExcluded = Collections.newSetFromMap (new IdentityHashMap <> ());
                                     _simulate (aParsed, aDropMask.longValue (), nSkipMask, aExpectedEvents, aExcluded);

                                     final ICommonsList <String> aEvents = new CommonsArrayList <> ();
                                     try (final XdmStreamReader aReader = XdmStreamReader.open (new InputSource (new ByteArrayInputStream (aBytes))))
                                     {
                                       int nEvent = 0;
                                       int nLastOrder = 0;
                                       EEvent eEvent;
                                       while ((eEvent = aReader.next ()) != EEvent.END)
                                       {
                                         final IXdmNode aNode = aReader.getNode ();
                                         aEvents.add (_describeEvent (eEvent, aNode, aReader.getDepth ()));
                                         final boolean bDecision = _isSet (eEvent == EEvent.ENTER ? nSkipMask
                                                                                                                  : aDropMask.longValue (),
                                                                           nEvent++);
                                         if (eEvent == EEvent.EXIT)
                                           assertTrue (aNode.getEndOrder () >= aNode.getOrder ());
                                         else
                                         {
                                           // Created in document order
                                           assertTrue (aNode.getOrder () > nLastOrder);
                                           nLastOrder = aNode.getOrder ();
                                           if (aNode.getAttributeCount () > 0)
                                             nLastOrder = aNode.getAttributeAtIndex (aNode.getAttributeCount () - 1).getOrder ();
                                         }
                                         if (bDecision)
                                         {
                                           if (eEvent == EEvent.ENTER)
                                             aReader.skipSubtree ();
                                           else
                                             aReader.drop (aNode);
                                           assertNull (aNode.getParent ());
                                         }
                                       }
                                       assertEquals (aExpectedEvents, aEvents);
                                       assertEquals (_describeRemaining (aParsed, aExcluded, nSkipMask == 0),
                                                     _describeRemaining (aReader.getDocument (),
                                                                         Collections.emptySet (),
                                                                         nSkipMask == 0));
                                     }
                                   });
  }
}

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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.unittest.property.Generators;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based test for the numbering of trees without grandchildren (a root with leaf
 * children, as the temporary tree of an XSLT variable whose content is text), which
 * {@link IXdmNode#assignDocumentOrder()} numbers without its general walk. The same tree with a
 * nested element appended takes the general walk; both must give the reference numbering, and the
 * nodes before the appended element the same numbers.
 *
 * @author Philip Helger
 */
public final class XdmFlatTreeNumberingPropertyTest
{
  private static final int ROOT_DOCUMENT = 0;
  private static final int ROOT_ELEMENT = 1;
  private static final int ROOT_TEXT = 2;

  @NonNull
  private static IXdmNode _createLeaf (final int nKind, final int nIndex)
  {
    return switch (nKind)
    {
      case 0 -> IXdmNode.createText ("t" + nIndex);
      case 1 -> IXdmNode.createComment ("c" + nIndex);
      default -> IXdmNode.createProcessingInstruction ("p", "d" + nIndex);
    };
  }

  @NonNull
  private static IXdmNode _createTree (final int nRootKind, final int nAttributes, @NonNull final List <Integer> aLeaves)
  {
    if (nRootKind == ROOT_TEXT)
      return IXdmNode.createText ("root");
    final IXdmNode aRoot = nRootKind == ROOT_DOCUMENT ? IXdmNode.createDocument () : IXdmNode.createElement (new XdmQName ("r"));
    if (nRootKind == ROOT_ELEMENT)
      for (int i = 0; i < nAttributes; ++i)
        aRoot.addAttribute (IXdmNode.createAttribute (new XdmQName ("a" + i), "v"));
    for (int i = 0; i < aLeaves.size (); ++i)
      aRoot.appendChild (_createLeaf (aLeaves.get (i).intValue (), i));
    return aRoot;
  }

  @Test
  public void testFlatTreeNumbering ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (Generators.ints (ROOT_DOCUMENT, ROOT_TEXT),
                                   Generators.ints (0, 3),
                                   Generators.lists (Generators.ints (0, 2), 0, 8),
                                   (aRootKind, aAttributes, aLeaves) -> {
                                     final int nRootKind = aRootKind.intValue ();
                                     final IXdmNode aFlat = _createTree (nRootKind, aAttributes.intValue (), aLeaves);
                                     aFlat.assignDocumentOrder ();
                                     XdmParserPropertyTest.assertReferenceNumbering (aFlat);
                                     // Numbered again after a change of a leaf list: still the reference
                                     if (nRootKind != ROOT_TEXT)
                                     {
                                       aFlat.appendChild (IXdmNode.createComment ("late"));
                                       aFlat.assignDocumentOrder ();
                                       XdmParserPropertyTest.assertReferenceNumbering (aFlat);
                                     }
                                     // The form that defeats the flat numbering: a nested element appended
                                     if (nRootKind != ROOT_TEXT)
                                     {
                                       final IXdmNode aGeneral = _createTree (nRootKind, aAttributes.intValue (), aLeaves);
                                       aGeneral.appendChild (IXdmNode.createComment ("late"));
                                       final IXdmNode aNested = IXdmNode.createElement (new XdmQName ("z"));
                                       aNested.appendChild (IXdmNode.createElement (new XdmQName ("z")));
                                       aGeneral.appendChild (aNested);
                                       aGeneral.assignDocumentOrder ();
                                       XdmParserPropertyTest.assertReferenceNumbering (aGeneral);
                                       assertEquals (aFlat.getOrder (), aGeneral.getOrder ());
                                       for (int i = 0; i < aFlat.getAttributeCount (); ++i)
                                         assertEquals (aFlat.getAttributeAtIndex (i).getOrder (),
                                                       aGeneral.getAttributeAtIndex (i).getOrder ());
                                       for (int i = 0; i < aFlat.getChildCount (); ++i)
                                         assertEquals (aFlat.getChildAtIndex (i).getOrder (), aGeneral.getChildAtIndex (i).getOrder ());
                                     }
                                   });
  }
}

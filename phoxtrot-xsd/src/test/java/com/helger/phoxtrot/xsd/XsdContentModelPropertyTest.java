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
package com.helger.phoxtrot.xsd;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for content models (XML Schema 1.1 Part 1, sections 3.8 and 3.9, Element
 * Sequence Valid): generated models of nested sequence and choice groups with element particles
 * and occurrence ranges are translated into a Java regular expression over the child element
 * names; for every model that compiles (the Unique Particle Attribution constraint refuses some),
 * an instance is valid exactly when the regular expression matches the sequence of its children,
 * under both versions, which must also agree on whether the model compiles.
 *
 * @author Philip Helger
 */
public final class XsdContentModelPropertyTest
{
  private static final String [] NAMES = { "a", "b", "c" };
  private static final int MAX_DEPTH = 3;

  /**
   * A generated model: the particle as schema text and as Java regular expression over the names.
   */
  private record Model (@NonNull String xsd, @NonNull String java)
  {}

  /**
   * A generated model with the child name sequences of the instances to test.
   */
  private record ModelCase (@NonNull Model model, @NonNull List <String> children)
  {}

  private static int _draw (@NonNull final ChoiceSequence aChoices, final int nMaxInclusive)
  {
    return (int) aChoices.draw (nMaxInclusive);
  }

  /**
   * @return The occurrence attributes and the Java quantifier of a drawn occurrence range.
   */
  @NonNull
  private static String [] _drawOccurs (@NonNull final ChoiceSequence aChoices)
  {
    final int nMin = _draw (aChoices, 2);
    final int nMax = _draw (aChoices, 3);
    if (nMax == 0)
    {
      // Unbounded
      return new String [] { " minOccurs=\"" + nMin + "\" maxOccurs=\"unbounded\"", "{" + nMin + ",}" };
    }
    final int nUpper = Math.max (nMin, nMax);
    return new String [] { " minOccurs=\"" + nMin + "\" maxOccurs=\"" + nUpper + "\"", "{" + nMin + "," + nUpper + "}" };
  }

  @NonNull
  private static Model _drawParticle (@NonNull final ChoiceSequence aChoices, final int nDepth)
  {
    final String [] aOccurs = _drawOccurs (aChoices);
    if (nDepth >= MAX_DEPTH || _draw (aChoices, 2) == 0)
    {
      final String sName = NAMES[_draw (aChoices, NAMES.length - 1)];
      return new Model ("<xs:element name=\"" + sName + "\" type=\"xs:string\"" + aOccurs[0] + "/>",
                        "(?:" + sName + ",)" + aOccurs[1]);
    }
    final boolean bChoice = _draw (aChoices, 1) == 1;
    final String sCompositor = bChoice ? "choice" : "sequence";
    final StringBuilder aXsd = new StringBuilder ("<xs:").append (sCompositor).append (aOccurs[0]).append ('>');
    final StringBuilder aJava = new StringBuilder ("(?:");
    final int nChildren = 1 + _draw (aChoices, 2);
    for (int i = 0; i < nChildren; ++i)
    {
      final Model aChild = _drawParticle (aChoices, nDepth + 1);
      aXsd.append (aChild.xsd ());
      if (bChoice && i > 0)
        aJava.append ('|');
      aJava.append (aChild.java ());
    }
    aXsd.append ("</xs:").append (sCompositor).append ('>');
    aJava.append (')').append (aOccurs[1]);
    return new Model (aXsd.toString (), aJava.toString ());
  }

  /**
   * @return Child name sequences (each name followed by a comma) built from the names while the
   *         Java expression may still match, or random ones.
   */
  @NonNull
  private static IGenerator <String> _children (@NonNull final Pattern aPattern)
  {
    final IGenerator <String> aGuided = aChoices -> {
      final StringBuilder aSB = new StringBuilder ();
      final int nLength = _draw (aChoices, 6);
      for (int i = 0; i < nLength; ++i)
      {
        final StringBuilder aCandidates = new StringBuilder ();
        for (final String sName : NAMES)
        {
          final Matcher aMatcher = aPattern.matcher (aSB + sName + ",");
          if (aMatcher.matches () || aMatcher.hitEnd ())
            aCandidates.append (sName);
        }
        if (aCandidates.isEmpty ())
          break;
        aSB.append (aCandidates.charAt (_draw (aChoices, aCandidates.length () - 1))).append (',');
      }
      return aSB.toString ();
    };
    final IGenerator <String> aRandom = Generators.lists (Generators.of (NAMES), 0, 6).map (aNames -> {
      final StringBuilder aSB = new StringBuilder ();
      for (final String sName : aNames)
        aSB.append (sName).append (',');
      return aSB.toString ();
    });
    return Generators.oneOf (aGuided, aRandom);
  }

  @NonNull
  private static String _instance (@NonNull final String sChildren)
  {
    final StringBuilder aSB = new StringBuilder ("<r>");
    for (final String sName : sChildren.split (","))
      if (!sName.isEmpty ())
        aSB.append ('<').append (sName).append ("/>");
    return aSB.append ("</r>").toString ();
  }

  /**
   * Model property: an instance is valid exactly when the sequence of its child names matches the
   * regular expression of the content model.
   */
  @Test
  public void testContentModelMatchesRegex ()
  {
    final IGenerator <Model> aModels = aChoices -> {
      // The content model of the complex type is a group
      final Model aRoot = _drawParticle (aChoices, 1);
      if (aRoot.xsd ().startsWith ("<xs:element"))
        return new Model ("<xs:sequence>" + aRoot.xsd () + "</xs:sequence>", aRoot.java ());
      return aRoot;
    };
    new PropertyChecker ().setTries (400)
                          .forAll (aModels.flatMap (aModel -> Generators.lists (_children (Pattern.compile (aModel.java ())),
                                                                                1,
                                                                                8)
                                                                        .map (aChildren -> new ModelCase (aModel, aChildren))),
                                   aCase -> {
                                     final Model aModel = aCase.model ();
                                     final String sSchema = XsdPropertyTestHelper.SCHEMA_START +
                                                            "<xs:element name=\"r\"><xs:complexType>" +
                                                            aModel.xsd () +
                                                            "</xs:complexType></xs:element>" +
                                                            XsdPropertyTestHelper.SCHEMA_END;
                                     final XsdSchema [] aSchemas = XsdPropertyTestHelper.compileBoth (sSchema);
                                     if (aSchemas == null)
                                       return;
                                     final Pattern aPattern = Pattern.compile (aModel.java ());
                                     for (final String sChildren : aCase.children ())
                                     {
                                       final String sInstance = _instance (sChildren);
                                       final boolean bExpected = aPattern.matcher (sChildren).matches ();
                                       assertEquals (aModel.xsd () + " " + sInstance + " (1.0)",
                                                     Boolean.valueOf (bExpected),
                                                     Boolean.valueOf (XsdPropertyTestHelper.isValidInstance (aSchemas[0],
                                                                                                             sInstance)));
                                       assertEquals (aModel.xsd () + " " + sInstance + " (1.1)",
                                                     Boolean.valueOf (bExpected),
                                                     Boolean.valueOf (XsdPropertyTestHelper.isValidInstance (aSchemas[1],
                                                                                                             sInstance)));
                                     }
                                   });
  }
}

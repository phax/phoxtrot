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
package com.helger.phoxtrot.xslt.ext.number;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the counting of xsl:number (XSLT 3.0 section 12.3):
 * counting from the name index of the document
 * ({@link NumberInstruction#getNameIndexCountCount()}, for count and from
 * patterns that name their elements, and parents with many children) must give
 * the numbers of the walk over the tree, which the same patterns written with a
 * wildcard and a <code>local-name()</code> predicate force.
 *
 * @author Philip Helger
 */
public final class NumberInstructionPropertyTest
{
  /** A count or from pattern with names, and the same pattern without */
  private record PatternPair (@NonNull String named, @NonNull String unnamed)
  {}

  private static final IGenerator <String> NAMES = Generators.of (RandomXmlDocument.SIMPLE_NAMES);

  private static final IGenerator <PatternPair> PATTERNS = aChoices -> {
    final String sN = NAMES.generate (aChoices);
    final String sM = NAMES.generate (aChoices);
    final String sUN = _unnamed (sN);
    final String sUM = _unnamed (sM);
    return switch ((int) aChoices.draw (4))
    {
      case 1 -> new PatternPair (sN + " | " + sM, sUN + " | " + sUM);
      case 2 -> new PatternPair (sN + "[@k]", sUN + "[@k]");
      case 3 -> new PatternPair (sM + "/" + sN, sUM + "/" + sUN);
      case 4 -> new PatternPair (sN + "[@m = '1']", sUN + "[@m = '1']");
      default -> new PatternPair (sN, sUN);
    };
  };

  @NonNull
  private static String _unnamed (@NonNull final String sName)
  {
    return "*[local-name() = '" + sName + "']";
  }

  @NonNull
  private static String _numberAll (@NonNull final String sAttrs)
  {
    return "<xsl:for-each select='//* | //@k'><xsl:number " + sAttrs + " format='1.1'/>|</xsl:for-each>";
  }

  @NonNull
  private static String _sheet (@NonNull final String sLevel,
                                @NonNull final PatternPair aCount,
                                @NonNull final PatternPair aFrom,
                                final boolean bWithFrom,
                                final boolean bNamed)
  {
    final StringBuilder aSB = new StringBuilder (XsltTestHelper.SHEET);
    aSB.append ("<xsl:output method='text'/><xsl:template match='/'>");
    String sAttrs = "level='" + sLevel + "' count=\"" + (bNamed ? aCount.named () : aCount.unnamed ()) + "\"";
    if (bWithFrom)
      sAttrs += " from=\"" + (bNamed ? aFrom.named () : aFrom.unnamed ()) + "\"";
    aSB.append (_numberAll (sAttrs));
    // The default count pattern of an element
    for (final String sName : RandomXmlDocument.SIMPLE_NAMES)
    {
      aSB.append ("<xsl:for-each select='//").append (sName).append ("'><xsl:number level='").append (sLevel).append ('\'');
      if (!bNamed)
        aSB.append (" count=\"").append (_unnamed (sName)).append ('"');
      aSB.append (" format='1.1'/>|</xsl:for-each>");
    }
    aSB.append ("</xsl:template>").append (XsltTestHelper.END);
    return aSB.toString ();
  }

  @NonNull
  private static String _run (@NonNull final XsltExecutable aExec, @NonNull final String sSource)
  {
    return aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                .principalSerialized ();
  }

  /**
   * For levels single, multiple and any, with and without a from pattern,
   * every element and attribute of a generated document gets the same number
   * from the name index as from the walk over the tree. Over all tries the
   * name index must have been used.
   */
  @Test
  public void testNameIndexCountsLikeTheWalk ()
  {
    final long nBefore = NumberInstruction.getNameIndexCountCount ();
    new PropertyChecker ().setTries (600)
                          .forAll (Generators.of ("single", "multiple", "any"),
                                   PATTERNS,
                                   PATTERNS.flatMap (p -> Generators.booleans ().map (b -> b.booleanValue () ? p : null)),
                                   RandomXmlDocument.simpleDocuments (2, 60),
                                   (sLevel, aCount, aFrom, sSource) -> {
                                     final boolean bWithFrom = aFrom != null;
                                     final PatternPair aRealFrom = bWithFrom ? aFrom : aCount;
                                     final XsltExecutable aNamed = XsltCompiler.compile (_sheet (sLevel,
                                                                                                 aCount,
                                                                                                 aRealFrom,
                                                                                                 bWithFrom,
                                                                                                 true),
                                                                                         new XsltCompileOptions ());
                                     final XsltExecutable aUnnamed = XsltCompiler.compile (_sheet (sLevel,
                                                                                                   aCount,
                                                                                                   aRealFrom,
                                                                                                   bWithFrom,
                                                                                                   false),
                                                                                           new XsltCompileOptions ());
                                     assertEquals (_run (aUnnamed, sSource), _run (aNamed, sSource));
                                   });
    assertTrue (NumberInstruction.getNameIndexCountCount () > nBefore);
  }

  @NonNull
  private static String _memoSheet (@NonNull final String sLevel, @NonNull final String sCount, final boolean bDefeat)
  {
    final StringBuilder aSB = new StringBuilder (XsltTestHelper.SHEET);
    aSB.append ("<xsl:output method='text'/><xsl:template match='/'><xsl:variable name='t' select='true()'/>");
    aSB.append (_numberAll ("level='" + sLevel + "' count=\"" + (bDefeat ? "(" + sCount + ")[$t]" : sCount) + "\""));
    aSB.append ("</xsl:template>").append (XsltTestHelper.END);
    return aSB.toString ();
  }

  /**
   * A count pattern without variables has its match results kept for the run
   * (per parent with many children for single and multiple, per name list of
   * the index for any); it gives the numbers of the same pattern with a
   * reference to a local variable, whose results are not kept.
   */
  @Test
  public void testKeptMatchesCountLikeEveryTest ()
  {
    new PropertyChecker ().setTries (600)
                          .forAll (Generators.of ("single", "multiple", "any"),
                                   PATTERNS,
                                   Generators.booleans (),
                                   RandomXmlDocument.simpleDocuments (2, 60),
                                   (sLevel, aCount, aNamed, sSource) -> {
                                     final String sCount = aNamed.booleanValue () ? aCount.named () : aCount.unnamed ();
                                     final XsltExecutable aKept = XsltCompiler.compile (_memoSheet (sLevel, sCount, false),
                                                                                        new XsltCompileOptions ());
                                     final XsltExecutable aEvery = XsltCompiler.compile (_memoSheet (sLevel, sCount, true),
                                                                                         new XsltCompileOptions ());
                                     assertEquals (_run (aEvery, sSource), _run (aKept, sSource));
                                   });
  }
}

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
package com.helger.phoxtrot.xslt.compile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for matching template rule patterns: a pattern matched
 * bottom-up (<code>PatternMatcher.DirectPath</code>, for paths of child and
 * attribute steps without positional predicates), the same pattern with a
 * predicate <code>[position() ge 1]</code> on every step, which leaves the
 * meaning unchanged but needs the generic path search, and the definition of
 * XSLT 3.0 section 5.5.3 (a node matches if it is in the result of
 * <code>root(.)//(P)</code>) must agree for every node.
 *
 * @author Philip Helger
 */
public final class CompiledPatternPropertyTest
{
  /** A pattern as written, and with a positional predicate on each step */
  private record PatternPair (@NonNull String pattern, @NonNull String searched)
  {}

  /** Name tests without and with a namespace prefix, and with wildcards */
  private static final IGenerator <String> PREFIXES = Generators.of ("", "p:", "q:", "d:", "*:");
  private static final IGenerator <String> ELEMENT_NAMES = Generators.oneOf (Generators.of ("a", "b", "c", "r", "d\u00e9")
                                                                                       .flatMap (n -> PREFIXES.map (p -> p + n)),
                                                                             Generators.of ("p:*", "d:*"));
  private static final IGenerator <String> ATTRIBUTE_NAMES = Generators.oneOf (Generators.of ("k", "m", "a", "b")
                                                                                         .flatMap (n -> PREFIXES.map (p -> p + n)),
                                                                               Generators.of ("p:*", "q:*"));
  private static final IGenerator <String> VALUES = Generators.of (RandomXmlDocument.SIMPLE_VALUES);
  private static final String FORCE_SEARCH = "[position() ge 1]";

  private static final IGenerator <String> PREDICATES = aChoices -> switch ((int) aChoices.draw (6))
  {
    case 0 -> "[@" + ATTRIBUTE_NAMES.generate (aChoices) + "]";
    case 1 -> "[@" + ATTRIBUTE_NAMES.generate (aChoices) + " = '" + VALUES.generate (aChoices) + "']";
    case 2 -> "[" + ELEMENT_NAMES.generate (aChoices) + "]";
    case 3 -> "[not(" + ELEMENT_NAMES.generate (aChoices) + ")]";
    case 4 -> "[1]";
    case 5 -> "[2]";
    default -> "[text()]";
  };

  @NonNull
  private static String _predicates (@NonNull final ChoiceSequence aChoices)
  {
    final StringBuilder aSB = new StringBuilder ();
    final int nCount = (int) aChoices.draw (2);
    for (int i = 0; i < nCount; ++i)
      aSB.append (PREDICATES.generate (aChoices));
    return aSB.toString ();
  }

  @NonNull
  private static PatternPair _path (@NonNull final ChoiceSequence aChoices)
  {
    final StringBuilder aPattern = new StringBuilder ();
    final StringBuilder aSearched = new StringBuilder ();
    final String sStart = switch ((int) aChoices.draw (2))
    {
      case 1 -> "/";
      case 2 -> "//";
      default -> "";
    };
    aPattern.append (sStart);
    aSearched.append (sStart);
    final int nSteps = 1 + (int) aChoices.draw (2);
    for (int i = 0; i < nSteps; ++i)
    {
      final boolean bLast = i == nSteps - 1;
      if (i > 0)
      {
        final String sSep = aChoices.draw (3) == 0 ? "//" : "/";
        aPattern.append (sSep);
        aSearched.append (sSep);
      }
      final String sTest = switch ((int) aChoices.draw (bLast ? 5 : 2))
      {
        case 1 -> "*";
        case 2 -> "node()";
        case 3 -> "@" + ATTRIBUTE_NAMES.generate (aChoices);
        case 4 -> "text()";
        case 5 -> "@*";
        default -> ELEMENT_NAMES.generate (aChoices);
      };
      final String sPredicates = _predicates (aChoices);
      aPattern.append (sTest).append (sPredicates);
      aSearched.append (sTest).append (sPredicates).append (FORCE_SEARCH);
    }
    return new PatternPair (aPattern.toString (), aSearched.toString ());
  }

  /** Paths and unions of two paths */
  private static final IGenerator <PatternPair> PATTERNS = aChoices -> {
    final PatternPair aFirst = _path (aChoices);
    if (aChoices.draw (3) != 0)
      return aFirst;
    final PatternPair aSecond = _path (aChoices);
    return new PatternPair (aFirst.pattern () + " | " + aSecond.pattern (), aFirst.searched () + " | " + aSecond.searched ());
  };

  /** Documents without and with namespaces */
  private static final IGenerator <String> DOCUMENTS = Generators.oneOf (RandomXmlDocument.simpleDocuments (3, 6),
                                                                         RandomXmlDocument.DOCUMENTS.map (Object::toString));

  @NonNull
  private static String _sheet (@NonNull final PatternPair aPattern)
  {
    return XsltTestHelper.SHEET.replace ("<xsl:stylesheet ", "<xsl:stylesheet xmlns:p='urn:p' xmlns:q='urn:q' xmlns:d='urn:d' ") +
           "<xsl:output method='text'/>" +
           "<xsl:template match='/'>" +
           "<xsl:for-each select='//node() | //@*'>" +
           "<xsl:variable name='n' select='.'/>" +
           "<xsl:apply-templates select='.' mode='direct'/>" +
           "<xsl:apply-templates select='.' mode='search'/>" +
           "<xsl:value-of select=\"if (exists(root(.)//(" +
           aPattern.pattern () +
           ")[. is $n])) then 1 else 0\"/>|" +
           "</xsl:for-each>" +
           "</xsl:template>" +
           "<xsl:template match=\"" +
           aPattern.pattern () +
           "\" mode='direct'>1</xsl:template>" +
           "<xsl:template match='node() | @*' mode='direct' priority='-9'>0</xsl:template>" +
           "<xsl:template match=\"" +
           aPattern.searched () +
           "\" mode='search'>1</xsl:template>" +
           "<xsl:template match='node() | @*' mode='search' priority='-9'>0</xsl:template>" +
           XsltTestHelper.END;
  }

  /**
   * For every node of a generated document, the pattern matched directly, the
   * pattern matched by the path search and the expression
   * <code>root(.)//(P)</code> agree.
   */
  @Test
  public void testMatchingAgreesWithDefinition ()
  {
    new PropertyChecker ().setTries (2000).forAll (PATTERNS, DOCUMENTS, (aPattern, sSource) -> {
      final String sResult = XsltCompiler.compile (_sheet (aPattern), new XsltCompileOptions ())
                                         .transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                                         .principalSerialized ();
      for (final String sNode : sResult.split ("\\|"))
      {
        assertEquals (sResult, 3, sNode.length ());
        assertTrue (sResult, sNode.charAt (0) == sNode.charAt (1) && sNode.charAt (1) == sNode.charAt (2));
      }
    });
  }
}

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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the rule index of a mode
 * (<code>Mode.getNamedRuleIndexes</code>, used by <code>Engine._findRule</code>):
 * the rule chosen for a node, and the chain of xsl:next-match, must not depend on
 * whether a pattern names the elements or attributes it matches. Every pattern
 * is also written with name tests replaced by a wildcard and a
 * <code>local-name()</code> predicate, which has no final names and goes to
 * the unnamed rules of the index. Names are tested with and without namespaces
 * and with namespace and local name wildcards.
 *
 * @author Philip Helger
 */
public final class RuleIndexPropertyTest
{
  /** A template rule: the pattern in both forms, its priority and whether it calls xsl:next-match */
  private record Rule (@NonNull String pattern, @NonNull String unnamedPattern, @NonNull String priority, boolean nextMatch)
  {}

  /** A name test as written, and the predicate on the name that is equivalent to it */
  private record NameTest (@NonNull String test, @NonNull String predicate)
  {
    @NonNull
    String unnamedElement ()
    {
      return "*[" + predicate + "]";
    }

    @NonNull
    String unnamedAttribute ()
    {
      return "@*[" + predicate + "]";
    }
  }

  private static final String [] [] NAMESPACES = { { "", "" }, { "p", "urn:p" }, { "q", "urn:q" }, { "d", "urn:d" } };
  private static final IGenerator <String> PRIORITIES = Generators.of ("0", "-1", "0.5", "1");

  @NonNull
  private static IGenerator <NameTest> _nameTests (@NonNull final String... aLocalNames)
  {
    final IGenerator <String> aLocal = Generators.of (aLocalNames);
    return aChoices -> {
      final String sLocal = aLocal.generate (aChoices);
      final int nNs = (int) aChoices.draw (NAMESPACES.length);
      if (nNs == NAMESPACES.length)
        return new NameTest ("*:" + sLocal, "local-name() = '" + sLocal + "'");
      final String [] aNs = NAMESPACES[nNs];
      if (nNs > 0 && aChoices.draw (4) == 0)
        return new NameTest (aNs[0] + ":*", "namespace-uri() = '" + aNs[1] + "'");
      return new NameTest (aNs[0].isEmpty () ? sLocal : aNs[0] + ":" + sLocal,
                           "local-name() = '" + sLocal + "' and namespace-uri() = '" + aNs[1] + "'");
    };
  }

  private static final IGenerator <NameTest> ELEMENT_NAMES = _nameTests ("a", "b", "c", "r", "d\u00e9");
  private static final IGenerator <NameTest> ATTRIBUTE_NAMES = _nameTests ("k", "m", "a", "b");

  private static final IGenerator <Rule> RULES = aChoices -> {
    final NameTest aN = ELEMENT_NAMES.generate (aChoices);
    final NameTest aM = ELEMENT_NAMES.generate (aChoices);
    final NameTest aA = ATTRIBUTE_NAMES.generate (aChoices);
    final String sN = aN.test ();
    final String sM = aM.test ();
    final String sA = aA.test ();
    final String sV = Generators.of (RandomXmlDocument.SIMPLE_VALUES).generate (aChoices);
    final String sUN = aN.unnamedElement ();
    final String sUM = aM.unnamedElement ();
    final String sUA = aA.unnamedAttribute ();
    final String [] aPair = switch ((int) aChoices.draw (12))
    {
      case 0 -> new String [] { sN, sUN };
      case 1 -> new String [] { "@" + sA, sUA };
      case 2 -> new String [] { sM + "/" + sN, sUM + "/" + sUN };
      case 3 -> new String [] { sN + "[@" + sA + "]", sUN + "[@" + sA + "]" };
      case 4 -> new String [] { sN + " | " + sM, sUN + " | " + sUM };
      case 5 -> new String [] { "*", "*" };
      case 6 -> new String [] { "node()", "node()" };
      case 7 -> new String [] { "@*", "@*" };
      case 8 -> new String [] { "text()", "text()" };
      case 9 -> new String [] { sN + "[1]", sUN + "[1]" };
      case 10 -> new String [] { sN + "[@" + sA + " = '" + sV + "']", sUN + "[@" + sA + " = '" + sV + "']" };
      case 11 -> new String [] { "//" + sN, "//" + sUN };
      default -> new String [] { sN + "/@" + sA, sUN + "/" + sUA };
    };
    return new Rule (aPair[0], aPair[1], PRIORITIES.generate (aChoices), aChoices.draw (1) == 1);
  };

  /** Documents without and with namespaces */
  private static final IGenerator <String> DOCUMENTS = Generators.oneOf (RandomXmlDocument.simpleDocuments (3, 6),
                                                                         RandomXmlDocument.DOCUMENTS.map (Object::toString));

  @NonNull
  private static String _sheet (@NonNull final List <Rule> aRules, final boolean bUnnamed)
  {
    final StringBuilder aSB = new StringBuilder (XsltTestHelper.SHEET.replace ("<xsl:stylesheet ",
                                                                               "<xsl:stylesheet xmlns:p='urn:p' xmlns:q='urn:q' xmlns:d='urn:d' "));
    aSB.append ("<xsl:output method='text'/><xsl:mode on-no-match='deep-skip'/>")
       .append ("<xsl:template match='/' priority='100'>")
       .append ("<xsl:for-each select='//node() | //@*'><xsl:apply-templates select='.'/>|</xsl:for-each>")
       .append ("</xsl:template>");
    for (int i = 0; i < aRules.size (); ++i)
    {
      final Rule aRule = aRules.get (i);
      aSB.append ("<xsl:template match=\"")
         .append (bUnnamed ? aRule.unnamedPattern () : aRule.pattern ())
         .append ("\" priority='")
         .append (aRule.priority ())
         .append ("'>")
         .append (i)
         .append (';');
      if (aRule.nextMatch ())
        aSB.append ("<xsl:next-match/>");
      aSB.append ("</xsl:template>");
    }
    return aSB.append (XsltTestHelper.END).toString ();
  }

  @NonNull
  private static String _run (@NonNull final String sSheet, @NonNull final String sSource)
  {
    return XsltCompiler.compile (sSheet, new XsltCompileOptions ())
                       .transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                       .principalSerialized ();
  }

  /**
   * For every node of a generated document, the same rules are chosen (the
   * first one and those reached by xsl:next-match) whether the patterns name
   * their elements and attributes or not.
   */
  @Test
  public void testIndexedRulesMatchUnnamedRules ()
  {
    new PropertyChecker ().setTries (1000)
                          .forAll (Generators.lists (RULES, 1, 12),
                                   DOCUMENTS,
                                   (aRules, sSource) -> assertEquals (_run (_sheet (aRules, true), sSource),
                                                                      _run (_sheet (aRules, false), sSource)));
  }
}

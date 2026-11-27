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
 * Property based tests for <code>fn:key</code> (XSLT 3.0 section 20.2): the key
 * index, built from the name index of the document where every match pattern
 * names its elements ({@link Engine#getKeyIndexFromNamesCount()}) or by a walk
 * over the tree otherwise, must find exactly the nodes of the equivalent path
 * expression, also restricted to a subtree by the third argument.
 *
 * @author Philip Helger
 */
public final class KeyIndexPropertyTest
{
  /** A match pattern with names, the same without names, and the equivalent predicate */
  private record KeyMatch (@NonNull String named, @NonNull String unnamed, @NonNull String predicate)
  {}

  private static final IGenerator <String> NAMES = Generators.of (RandomXmlDocument.SIMPLE_NAMES);

  private static final IGenerator <KeyMatch> MATCHES = aChoices -> {
    final String sN = NAMES.generate (aChoices);
    final String sM = NAMES.generate (aChoices);
    final String sUN = "*[local-name() = '" + sN + "']";
    final String sUM = "*[local-name() = '" + sM + "']";
    return switch ((int) aChoices.draw (3))
    {
      case 1 -> new KeyMatch (sN + " | " + sM, sUN + " | " + sUM, "self::" + sN + " or self::" + sM);
      case 2 -> new KeyMatch (sN + "[@m]", sUN + "[@m]", "self::" + sN + " and @m");
      case 3 -> new KeyMatch (sM + "/" + sN, sUM + "/" + sUN, "self::" + sN + " and parent::" + sM);
      default -> new KeyMatch (sN, sUN, "self::" + sN);
    };
  };

  /** Single values of <code>@k</code> and the tokens of <code>@m</code> */
  private static final IGenerator <String> USES = Generators.of ("@k", "tokenize(@m)");

  @NonNull
  private static String _sheet (@NonNull final KeyMatch aMatch, @NonNull final String sUse)
  {
    final String sValues = "('1', '2', '01', 'a', 'b', 'c', 'b c', '')";
    return XsltTestHelper.SHEET +
           "<xsl:output method='text'/>" +
           "<xsl:key name='named' match=\"" +
           aMatch.named () +
           "\" use='" +
           sUse +
           "'/>" +
           "<xsl:key name='unnamed' match=\"" +
           aMatch.unnamed () +
           "\" use='" +
           sUse +
           "'/>" +
           "<xsl:template match='/'>" +
           "<xsl:for-each select='/, //*'>" +
           "<xsl:variable name='top' select='.'/>" +
           "<xsl:for-each select=\"" +
           sValues +
           "\">" +
           "<xsl:variable name='v' select='.'/>" +
           "<xsl:value-of select=\"key('named', $v, $top) ! generate-id()\"/>/" +
           "<xsl:value-of select=\"key('unnamed', $v, $top) ! generate-id()\"/>/" +
           "<xsl:value-of select=\"$top/descendant-or-self::*[" +
           aMatch.predicate () +
           "][" +
           sUse +
           " = $v] ! generate-id()\"/>/" +
           "<xsl:value-of select=\"key('named', ($v, '1', $v), $top) ! generate-id()\"/>/" +
           "<xsl:value-of select=\"$top/descendant-or-self::*[" +
           aMatch.predicate () +
           "][" +
           sUse +
           " = ($v, '1')] ! generate-id()\"/>|" +
           "</xsl:for-each>" +
           "</xsl:for-each>" +
           "</xsl:template>" +
           XsltTestHelper.END;
  }

  /**
   * For every value and every subtree of a generated document, the key with a
   * named match pattern, the same key without names and the path expression
   * find the same nodes in the same order; so do the key with several values
   * (one of them twice) and its path expression. Over all tries the name index must
   * have been used.
   */
  @Test
  public void testKeyMatchesPathExpression ()
  {
    final long nBefore = Engine.getKeyIndexFromNamesCount ();
    new PropertyChecker ().setTries (1000)
                          .forAll (MATCHES, USES, RandomXmlDocument.simpleDocuments (3, 8), (aMatch, sUse, sSource) -> {
                            final XsltExecutable aExec = XsltCompiler.compile (_sheet (aMatch, sUse), new XsltCompileOptions ());
                            final String sResult = aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                                                        .principalSerialized ();
                            for (final String sCase : sResult.split ("\\|"))
                            {
                              final String [] aParts = sCase.split ("/", -1);
                              assertEquals (sResult, 5, aParts.length);
                              assertEquals (sResult, aParts[2], aParts[0]);
                              assertEquals (sResult, aParts[2], aParts[1]);
                              assertEquals (sResult, aParts[4], aParts[3]);
                            }
                          });
    assertTrue (Engine.getKeyIndexFromNamesCount () > nBefore);
  }
}

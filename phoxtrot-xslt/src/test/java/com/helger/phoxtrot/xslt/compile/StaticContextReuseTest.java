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

import static com.helger.phoxtrot.xslt.XsltTestHelper.END;
import static com.helger.phoxtrot.xslt.XsltTestHelper.SHEET;
import static com.helger.phoxtrot.xslt.XsltTestHelper.errorCode;
import static com.helger.phoxtrot.xslt.XsltTestHelper.text;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test that expressions and patterns with different static contexts evaluated
 * one after the other in the same context each see their own static context,
 * although equivalent ones share the dynamic context
 * ({@link com.helger.phoxtrot.xslt.runtime.XsltContext#getDynamicContextFor(com.helger.phoxtrot.xpath.context.StaticContext)}).
 *
 * @author Philip Helger
 */
public final class StaticContextReuseTest
{
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";
  private static final String SP = "<xsl:text> </xsl:text>";

  @Test
  public void testNamespacesAlternate ()
  {
    // The cast resolves the prefix with the static context at run time
    final String sE = "<xsl:value-of select=\"namespace-uri-from-QName(xs:QName('p:x'))\" xmlns:p='urn:";
    assertEquals ("urn:a urn:b urn:a urn:b",
                  text (IT + sE + "a'/>" + SP + sE + "b'/>" + SP + sE + "a'/>" + SP + sE + "b'/>" + TE, null));
  }

  @Test
  public void testXPathDefaultNamespaceAlternates ()
  {
    // An unprefixed name cast to xs:QName takes the default element namespace
    final String sE = "<xsl:value-of select=\"namespace-uri-from-QName(xs:QName('x')) || '.'\"";
    assertEquals (". urn:a. . urn:a.",
                  text (IT +
                        sE +
                        "/>" +
                        SP +
                        sE +
                        " xpath-default-namespace='urn:a'/>" +
                        SP +
                        sE +
                        "/>" +
                        SP +
                        sE +
                        " xpath-default-namespace='urn:a'/>" +
                        TE,
                        null));
  }

  @Test
  public void testBackwardsCompatibleElementsAlternate ()
  {
    // With XSLT 1.0 behaviour the strings are compared as numbers, with 3.0
    // behaviour as strings
    final String sE = "<xsl:value-of select=\"'10' &lt; '9'\"";
    assertEquals ("false true false true",
                  text (IT +
                        sE +
                        " version='1.0'/>" +
                        SP +
                        sE +
                        "/>" +
                        SP +
                        sE +
                        " version='1.0'/>" +
                        SP +
                        sE +
                        "/>" +
                        TE,
                        null));
  }

  @Test
  public void testDefaultCollationAlternates ()
  {
    final String sCI = "default-collation='http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive'";
    assertEquals ("false true false true",
                  text (IT +
                        "<xsl:value-of select=\"'a' = 'A'\"/>" +
                        SP +
                        "<xsl:value-of select=\"'a' = 'A'\" " +
                        sCI +
                        "/>" +
                        SP +
                        "<xsl:value-of select=\"'a' = 'A'\"/>" +
                        SP +
                        "<xsl:value-of select=\"'a' = 'A'\" " +
                        sCI +
                        "/>" +
                        TE,
                        null));
  }

  @Test
  public void testBaseUriAlternates ()
  {
    assertEquals ("http://a/ http://b/ http://a/",
                  text (IT +
                        "<xsl:value-of select='static-base-uri()' xml:base='http://a/'/>" +
                        SP +
                        "<xsl:value-of select='static-base-uri()' xml:base='http://b/'/>" +
                        SP +
                        "<xsl:value-of select='static-base-uri()' xml:base='http://a/'/>" +
                        TE,
                        null));
  }

  @Test
  public void testPatternsAlternate ()
  {
    // The predicates resolve the prefix at run time: only the pattern with p
    // bound to urn:a matches, although the one with urn:b is tried first
    final String sP = "<xsl:template match=\"x[namespace-uri-from-QName(xs:QName('p:y')) = 'urn:a']\" xmlns:p='urn:";
    assertEquals ("AAA",
                  text ("<xsl:template match='/'><xsl:apply-templates select='r/x'/></xsl:template>" +
                        sP +
                        "a' priority='2'>A</xsl:template>" +
                        sP +
                        "b' priority='3'>B</xsl:template>" +
                        "<xsl:template match='x'>N</xsl:template>",
                        "<r><x/><x/><x/></r>"));
  }

  @Test
  public void testInheritedByCallsAndVariables ()
  {
    // The context of a template body, of a function body and of a variable body starts with the
    // static context of the caller's last expression; their own expressions still resolve the
    // prefix with their own static context
    final String sNS = "namespace-uri-from-QName(xs:QName('p:x'))";
    assertEquals ("urn:a urn:a urn:b urn:c urn:d",
                  text (IT +
                        "<xsl:value-of select=\"" +
                        sNS +
                        "\" xmlns:p='urn:a'/>" +
                        SP +
                        "<xsl:call-template name='t' xmlns:p='urn:a'><xsl:with-param name='v' select=\"" +
                        sNS +
                        "\"/></xsl:call-template>" +
                        TE +
                        "<xsl:template name='t' xmlns:p='urn:b' xmlns:f='urn:f'><xsl:param name='v'/>" +
                        "<xsl:value-of select=\"$v || ' ' || " +
                        sNS +
                        "\"/>" +
                        SP +
                        "<xsl:variable name='w'><xsl:value-of select=\"" +
                        sNS +
                        "\" xmlns:p='urn:c'/></xsl:variable><xsl:value-of select='$w'/>" +
                        SP +
                        "<xsl:value-of select='f:g()'/></xsl:template>" +
                        "<xsl:function name='f:g' xmlns:f='urn:f' xmlns:p='urn:d'><xsl:sequence select=\"" +
                        sNS +
                        "\"/></xsl:function>",
                        null));
  }

  @Test
  public void testDeclaredTypeAfterBackwardsCompatibleExpression ()
  {
    // The parameter value is computed by an expression with XSLT 1.0 behaviour, whose static
    // context the context of the template body starts with; the declared type is still converted
    // with the rules of the instructions (no XPath 1.0 conversion of the string to xs:double)
    final String sCall = "<xsl:call-template name='t'><xsl:with-param name='v' select=\"'5'\" version='1.0'/>" +
                         "</xsl:call-template>";
    assertEquals ("XTTE0590",
                  errorCode (SHEET +
                             "<xsl:output method='text'/>" +
                             IT +
                             sCall +
                             TE +
                             "<xsl:template name='t'><xsl:param name='v' as='xs:double'/><xsl:value-of select='$v + 1'/>" +
                             "</xsl:template>" +
                             END,
                             null));
    // The same value from an expression without XSLT 1.0 behaviour
    assertEquals ("XTTE0590",
                  errorCode (SHEET +
                             "<xsl:output method='text'/>" +
                             IT +
                             sCall.replace (" version='1.0'", "") +
                             TE +
                             "<xsl:template name='t'><xsl:param name='v' as='xs:double'/><xsl:value-of select='$v + 1'/>" +
                             "</xsl:template>" +
                             END,
                             null));
  }
}

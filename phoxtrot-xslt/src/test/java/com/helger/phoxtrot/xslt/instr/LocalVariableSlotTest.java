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
package com.helger.phoxtrot.xslt.instr;

import static com.helger.phoxtrot.xslt.XsltTestHelper.END;
import static com.helger.phoxtrot.xslt.XsltTestHelper.transform;
import static com.helger.phoxtrot.xslt.XsltTestHelper.text;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test the binding of local variables and parameters by slot in the frame of their body (see
 * {@link LocalVariable} and {@link com.helger.phoxtrot.xpath.context.VariableFrame}): the cases
 * where a frame changed in place could show a wrong binding.
 *
 * @author Philip Helger
 */
public final class LocalVariableSlotTest
{
  private static final String IT = "<xsl:template name='xsl:initial-template'>";
  private static final String TE = "</xsl:template>";
  private static final String SHEET_F = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                                        " xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:f='urn:f'>" +
                                        "<xsl:output method='text'/>";

  @Test
  public void testShadowingInNestedScope ()
  {
    // The inner $x shadows the outer one only within xsl:if
    assertEquals ("2 1",
                  text (IT +
                        "<xsl:variable name='x' select='1'/>" +
                        "<xsl:if test='true()'><xsl:variable name='x' select='2'/><xsl:value-of select='$x'/></xsl:if>" +
                        "<xsl:text> </xsl:text><xsl:value-of select='$x'/>" +
                        TE,
                        null));
  }

  @Test
  public void testRebindingInLoop ()
  {
    assertEquals ("10 20 30 ",
                  text (IT +
                        "<xsl:variable name='z' select='0'/>" +
                        "<xsl:for-each select='1 to 3'><xsl:variable name='v' select='. * 10 + $z'/>" +
                        "<xsl:value-of select='$v'/><xsl:text> </xsl:text></xsl:for-each>" +
                        TE,
                        null));
  }

  @Test
  public void testClosureEscapingLoopIteration ()
  {
    // $b is lazy and not evaluated before the function is called after the loop: it must still
    // see the $a of its own iteration. $z makes the iterations share the frame of the content
    assertEquals ("10 20 30",
                  text (IT +
                        "<xsl:variable name='fs' as='function(*)*'>" +
                        "<xsl:variable name='z' select='0'/>" +
                        "<xsl:for-each select='1 to 3'>" +
                        "<xsl:variable name='a' select='.'/>" +
                        "<xsl:variable name='b' select='$a * 10 + $z'/>" +
                        "<xsl:sequence select='function () { $b }'/>" +
                        "</xsl:for-each>" +
                        "</xsl:variable>" +
                        "<xsl:value-of select='$fs ! .()'/>" +
                        TE,
                        null));
  }

  @Test
  public void testLazyContentWithLocalsEvaluatedAfterSiblings ()
  {
    // The content of $a has its own local variable and is evaluated only after $b is bound
    assertEquals ("b in b",
                  text (IT +
                        "<xsl:variable name='a'><xsl:variable name='i' select='\"in\"'/><xsl:value-of select='$i'/></xsl:variable>" +
                        "<xsl:variable name='b' select='\"b\"'/>" +
                        "<xsl:value-of select='$b, string($a), $b'/>" +
                        TE,
                        null));
  }

  @Test
  public void testLazyContentShadowingOuterVariable ()
  {
    assertEquals ("inner outer",
                  text (IT +
                        "<xsl:variable name='x' select='\"outer\"'/>" +
                        "<xsl:variable name='a'><xsl:variable name='x' select='\"inner\"'/><xsl:value-of select='$x'/></xsl:variable>" +
                        "<xsl:value-of select='string($a), $x'/>" +
                        TE,
                        null));
  }

  @Test
  public void testXPathBindingsShadowFrameVariables ()
  {
    assertEquals ("5 6 1 7 1 true 1",
                  text (IT +
                        "<xsl:variable name='x' select='1'/>" +
                        "<xsl:value-of select='(for $x in (5, 6) return $x), $x, (let $x := 7 return $x), $x," +
                        " (some $x in (1, 2) satisfies $x = 2), $x'/>" +
                        TE,
                        null));
    assertEquals ("9 1",
                  text (IT +
                        "<xsl:variable name='x' select='1'/>" +
                        "<xsl:value-of select='function ($x) { $x } (9), $x'/>" +
                        TE,
                        null));
  }

  @Test
  public void testMemoisedCallWithFrameVariableInLoop ()
  {
    // A call whose value is kept per variable binding must be evaluated again for the new binding
    // of every iteration; $z makes the iterations share one frame
    assertEquals ("A A|AA AA|AAA AAA|",
                  text (IT +
                        "<xsl:variable name='z' select='\"\"'/>" +
                        "<xsl:for-each select='1 to 3'>" +
                        "<xsl:variable name='s' select='string-join((1 to .) ! \"a\")'/>" +
                        "<xsl:value-of select='for $i in 1 to 2 return upper-case(concat($s, $z))'/>|" +
                        "</xsl:for-each>" +
                        TE,
                        null));
  }

  @Test
  public void testRecursiveTemplateAndFunctionParameters ()
  {
    assertEquals ("3 2 1 0|120",
                  transform (SHEET_F +
                             "<xsl:function name='f:fact' as='xs:integer'><xsl:param name='n' as='xs:integer'/>" +
                             "<xsl:variable name='m' select='$n - 1'/>" +
                             "<xsl:sequence select='if ($n le 1) then 1 else $n * f:fact($m)'/></xsl:function>" +
                             "<xsl:template name='count'><xsl:param name='n'/>" +
                             "<xsl:value-of select='$n'/>" +
                             "<xsl:if test='$n gt 0'><xsl:text> </xsl:text>" +
                             "<xsl:call-template name='count'><xsl:with-param name='n' select='$n - 1'/></xsl:call-template>" +
                             "</xsl:if>" +
                             "<xsl:value-of select='$n'/>" +
                             "</xsl:template>" +
                             IT +
                             "<xsl:variable name='r'><xsl:call-template name='count'><xsl:with-param name='n' select='3'/></xsl:call-template></xsl:variable>" +
                             "<xsl:value-of select='substring($r, 1, 7)'/>|<xsl:value-of select='f:fact(5)'/>" +
                             TE +
                             END,
                             null,
                             null));
  }

  @Test
  public void testParameterKeepsValueAfterRecursiveCall ()
  {
    // Every call has its own frame: the caller's $n is unchanged after the recursive call returns
    assertEquals ("0 1 2 3 ",
                  text ("<xsl:template name='count'><xsl:param name='n'/>" +
                        "<xsl:if test='$n gt 0'>" +
                        "<xsl:call-template name='count'><xsl:with-param name='n' select='$n - 1'/></xsl:call-template>" +
                        "</xsl:if>" +
                        "<xsl:value-of select='$n'/><xsl:text> </xsl:text>" +
                        "</xsl:template>" +
                        IT +
                        "<xsl:call-template name='count'><xsl:with-param name='n' select='3'/></xsl:call-template>" +
                        TE,
                        null));
  }

  @Test
  public void testErrorVariablesInLoop ()
  {
    assertEquals ("1:FOAR0001 2:FOAR0001 ",
                  text (IT +
                        "<xsl:variable name='z' select='0'/>" +
                        "<xsl:for-each select='1 to 2'><xsl:variable name='i' select='. + $z'/>" +
                        "<xsl:try><xsl:sequence select='$i div 0'/>" +
                        "<xsl:catch><xsl:value-of select='$i'/>:<xsl:value-of select='local-name-from-QName($err:code)'" +
                        " xmlns:err='http://www.w3.org/2005/xqt-errors'/><xsl:text> </xsl:text></xsl:catch>" +
                        "</xsl:try></xsl:for-each>" +
                        TE,
                        null));
  }

  @Test
  public void testIterateParameters ()
  {
    assertEquals ("1 3 6 10 |10",
                  text (IT +
                        "<xsl:iterate select='1 to 4'><xsl:param name='sum' select='0'/>" +
                        "<xsl:variable name='next' select='$sum + .'/>" +
                        "<xsl:on-completion><xsl:text>|</xsl:text><xsl:value-of select='$sum'/></xsl:on-completion>" +
                        "<xsl:value-of select='$next'/><xsl:text> </xsl:text>" +
                        "<xsl:next-iteration><xsl:with-param name='sum' select='$next'/></xsl:next-iteration>" +
                        "</xsl:iterate>" +
                        TE,
                        null));
  }

  @Test
  public void testLocalVariableInGroupingPattern ()
  {
    assertEquals ("2 1 2 ",
                  text ("<xsl:template match='r'><xsl:variable name='n' select='\"p\"'/>" +
                        "<xsl:for-each-group select='*' group-ending-with='*[name() = $n]'>" +
                        "<xsl:value-of select='count(current-group())'/><xsl:text> </xsl:text>" +
                        "</xsl:for-each-group></xsl:template>",
                        "<r><h/><p/><p/><h/><p/></r>"));
  }

  @Test
  public void testExpressionReenteredByRecursion ()
  {
    // The same expression is evaluated again by the recursive call while its loop runs: every
    // evaluation has its own frame for $i
    assertEquals ("21",
                  transform (SHEET_F +
                             "<xsl:function name='f:s' as='xs:integer'><xsl:param name='n' as='xs:integer'/>" +
                             "<xsl:sequence select='sum (for $i in 1 to 2 return if ($n = 0) then 0 else f:s($n - 1) + $i)'/>" +
                             "</xsl:function>" +
                             IT +
                             "<xsl:value-of select='f:s(3)'/>" +
                             TE +
                             END,
                             null,
                             null));
  }
}

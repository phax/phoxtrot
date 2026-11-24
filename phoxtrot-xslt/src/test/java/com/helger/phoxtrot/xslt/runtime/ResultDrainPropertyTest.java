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

import java.io.StringWriter;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for {@link ResultDrain}: a generated stylesheet that
 * constructs elements, attributes, namespaces, text, atomic values, comments,
 * processing instructions and copies in any order writes the same characters
 * to a writer, where the result is written while it is built, as the string
 * result; where the run fails, both fail with the same error code.
 *
 * @author Philip Helger
 */
public final class ResultDrainPropertyTest
{
  private static final String SOURCE = "<r xmlns:s='urn:s'><a k='1'>t<s:b/></a><a k='2'/><!--c--></r>";

  private static final IGenerator <String> OUTPUTS = Generators.of ("<xsl:output method='xml'/>",
                                                                    "<xsl:output method='xml' indent='yes'/>",
                                                                    "<xsl:output method='xml' omit-xml-declaration='yes' cdata-section-elements='e'/>",
                                                                    "<xsl:output method='html'/>",
                                                                    "<xsl:output method='xhtml' indent='yes'/>",
                                                                    "<xsl:output method='text'/>");

  /** Instructions without content */
  private static final IGenerator <String> LEAVES = Generators.of ("<xsl:attribute name='a'>v&amp;&lt;</xsl:attribute>",
                                                                   "<xsl:attribute name='p:a' namespace='urn:p2'>w</xsl:attribute>",
                                                                   "<xsl:attribute name='a'>again</xsl:attribute>",
                                                                   "<xsl:namespace name='z' select=\"'urn:z'\"/>",
                                                                   "<xsl:namespace name='p' select=\"'urn:p3'\"/>",
                                                                   "<xsl:text>t &amp; &lt;x&gt; ]]&gt;</xsl:text>",
                                                                   "<xsl:text>\n  </xsl:text>",
                                                                   "<xsl:value-of select='1 to 3'/>",
                                                                   "<xsl:sequence select=\"1, 'a', 2.5\"/>",
                                                                   "<xsl:sequence select='()'/>",
                                                                   "<xsl:comment>c-d</xsl:comment>",
                                                                   "<xsl:processing-instruction name='pi'>x y</xsl:processing-instruction>",
                                                                   "<xsl:copy-of select='//a[1]'/>",
                                                                   "<xsl:copy-of select='//a/@k'/>",
                                                                   "<xsl:copy-of select='/r/node()'/>",
                                                                   "<xsl:sequence select='/r/a[2]'/>",
                                                                   "<xsl:copy-of select='/'/>",
                                                                   "<br/>",
                                                                   "<xsl:document><d/></xsl:document>");

  @NonNull
  private static String _instructionRecursive (@NonNull final ChoiceSequence aChoices, final int nDepth)
  {
    final int nKind = nDepth <= 0 ? 0 : (int) aChoices.draw (8);
    if (nKind <= 2)
      return LEAVES.generate (aChoices);
    final StringBuilder aContent = new StringBuilder ();
    final int nCount = (int) aChoices.draw (3);
    for (int i = 0; i < nCount; ++i)
      aContent.append (_instructionRecursive (aChoices, nDepth - 1));
    return switch (nKind)
    {
      case 3 -> "<e xmlns:p='urn:p'>" + aContent + "</e>";
      case 4 -> "<xsl:element name='q:x' namespace='urn:q'>" + aContent + "</xsl:element>";
      case 5 -> "<xsl:element name='y' inherit-namespaces='no'>" + aContent + "</xsl:element>";
      case 6 -> "<xsl:for-each select='1 to 2'>" + aContent + "</xsl:for-each>";
      case 7 -> "<xsl:for-each select='/r/a'><xsl:copy>" + aContent + "</xsl:copy></xsl:for-each>";
      default -> "<xsl:if test='true()'>" + aContent + "</xsl:if>";
    };
  }

  private static final IGenerator <String> BODIES = aChoices -> {
    final StringBuilder aSB = new StringBuilder ();
    final int nCount = 1 + (int) aChoices.draw (2);
    for (int i = 0; i < nCount; ++i)
      aSB.append (_instructionRecursive (aChoices, 4));
    return aSB.toString ();
  };

  @NonNull
  private static XsltTransformRequest _request ()
  {
    return new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (SOURCE));
  }

  @NonNull
  private static String _toString (@NonNull final XsltExecutable aExec)
  {
    try
    {
      return aExec.transform (_request ()).principalSerialized ();
    }
    catch (final XPathException ex)
    {
      return "error " + ex.getCode ();
    }
  }

  @NonNull
  private static String _toWriter (@NonNull final XsltExecutable aExec)
  {
    final StringWriter aSW = new StringWriter ();
    try
    {
      aExec.transform (_request (), aSW);
    }
    catch (final XPathException ex)
    {
      return "error " + ex.getCode ();
    }
    return aSW.toString ();
  }

  /**
   * The characters written to a writer are those of the string result, and an
   * error is raised with the same code by both.
   */
  @Test
  public void testWriterMatchesStringResult ()
  {
    new PropertyChecker ().setTries (2000).forAll (OUTPUTS, BODIES, (sOutput, sBody) -> {
      final String sSheet = XsltTestHelper.SHEET +
                            sOutput +
                            "<xsl:template match='/'>" +
                            sBody +
                            "</xsl:template>" +
                            XsltTestHelper.END;
      final XsltExecutable aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
      assertEquals (sSheet, _toString (aExec), _toWriter (aExec));
    });
  }
}

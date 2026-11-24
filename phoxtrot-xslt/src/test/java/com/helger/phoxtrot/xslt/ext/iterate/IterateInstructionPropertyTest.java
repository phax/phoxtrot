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
package com.helger.phoxtrot.xslt.ext.iterate;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for xsl:iterate ({@link IterateInstruction}, XSLT 3.0
 * section 7.2) against the same loop written in Java: parameters that
 * xsl:next-iteration does not set keep their value, xsl:break ends the
 * iteration with its own result and suppresses xsl:on-completion, a body
 * without xsl:next-iteration keeps all parameters, and position() and last()
 * refer to the input sequence.
 *
 * @author Philip Helger
 */
public final class IterateInstructionPropertyTest
{
  private static final IGenerator <List <Integer>> VALUES = Generators.lists (Generators.ints (-5, 9), 0, 15);

  /**
   * For each item: break when the running sum would exceed $limit; for a
   * multiple of 3 no xsl:next-iteration at all; for a new maximum both
   * parameters; otherwise only the sum.
   */
  private static final XsltExecutable SHEET = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                    "<xsl:output method='text'/>" +
                                                                    "<xsl:param name='limit' as='xs:integer'/>" +
                                                                    "<xsl:template match='/'>" +
                                                                    "<xsl:iterate select='r/i'>" +
                                                                    "<xsl:param name='sum' select='0' as='xs:integer'/>" +
                                                                    "<xsl:param name='max' select='-100' as='xs:integer'/>" +
                                                                    "<xsl:on-completion><xsl:value-of select=\"'end', $sum, $max\" separator=':'/></xsl:on-completion>" +
                                                                    "<xsl:variable name='n' select='xs:integer(@v)'/>" +
                                                                    "<xsl:value-of select='position(), last()' separator='/'/>" +
                                                                    "<xsl:text>,</xsl:text>" +
                                                                    "<xsl:choose>" +
                                                                    "<xsl:when test='$sum + $n gt $limit'><xsl:break select=\"'break', position(), $sum, $max\"/></xsl:when>" +
                                                                    "<xsl:when test='$n mod 3 = 0'/>" +
                                                                    "<xsl:when test='$n gt $max'><xsl:next-iteration><xsl:with-param name='sum' select='$sum + $n'/>" +
                                                                    "<xsl:with-param name='max' select='$n'/></xsl:next-iteration></xsl:when>" +
                                                                    "<xsl:otherwise><xsl:next-iteration><xsl:with-param name='sum' select='$sum + $n'/></xsl:next-iteration></xsl:otherwise>" +
                                                                    "</xsl:choose>" +
                                                                    "</xsl:iterate>" +
                                                                    "</xsl:template>" +
                                                                    XsltTestHelper.END,
                                                                    new XsltCompileOptions ());

  @NonNull
  private static String _expected (@NonNull final List <Integer> aValues, final int nLimit)
  {
    final StringBuilder aSB = new StringBuilder ();
    int nSum = 0;
    int nMax = -100;
    final int nLast = aValues.size ();
    for (int i = 0; i < nLast; ++i)
    {
      final int n = aValues.get (i).intValue ();
      aSB.append (i + 1).append ('/').append (nLast).append (',');
      if (nSum + n > nLimit)
      {
        // xsl:break: adjacent atomic values of its result are joined by a space
        return aSB.append ("break ").append (i + 1).append (' ').append (nSum).append (' ').append (nMax).toString ();
      }
      if (n % 3 == 0)
        continue;
      if (n > nMax)
        nMax = n;
      nSum += n;
    }
    return aSB.append ("end:").append (nSum).append (':').append (nMax).toString ();
  }

  /**
   * The output of the iteration equals the Java loop for random values and
   * limits.
   */
  @Test
  public void testIterateMatchesJava ()
  {
    new PropertyChecker ().setTries (1000).forAll (VALUES, Generators.ints (-3, 40), (aValues, aLimit) -> {
      final StringBuilder aSource = new StringBuilder ("<r>");
      for (final Integer aValue : aValues)
        aSource.append ("<i v='").append (aValue.intValue ()).append ("'/>");
      aSource.append ("</r>");
      final XsltTransformRequest aRequest = new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (aSource.toString ()));
      aRequest.params ().put (new XdmQName ("limit"), XdmSequence.of (XdmAtomic.integer (aLimit.intValue ())));
      assertEquals (aSource + " limit " + aLimit,
                    _expected (aValues, aLimit.intValue ()),
                    SHEET.transform (aRequest).principalSerialized ());
    });
  }
}

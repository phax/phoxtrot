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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.BeforeClass;
import org.junit.Test;

import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltTestHelper;

/**
 * Functions registered by the user through
 * {@link XsltExtensionRegistry#registerFunctions(java.util.function.Consumer)}
 * are visible in static expressions (<code>use-when</code>, static variables),
 * as at run time; the run-time XSLT functions and those of the default
 * extensions stay unavailable there (XSLT 3.0 section 9.7).
 *
 * @author Philip Helger
 */
public final class UserFunctionsInStaticExpressionsTest
{
  private static final String NS = "urn:phoxtrot:test:user-functions-static";
  private static final String HEAD = "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'" +
                                     " xmlns:p='" +
                                     NS +
                                     "'><xsl:output method='text'/>";

  @NonNull
  private static XdmSequence _twice (@NonNull final List <XdmSequence> aArgs)
  {
    final long nValue = ((XdmAtomic) aArgs.get (0).get (0)).getBigInteger ().longValue ();
    return XdmSequence.of (XdmAtomic.integer (nValue * 2));
  }

  @BeforeClass
  public static void registerFunction ()
  {
    // The registry is shared by the JVM: the function lives in a namespace of this test only
    XsltExtensionRegistry.getInstance ()
                         .registerFunctions (aLib -> aLib.register (NS,
                                                                    "twice",
                                                                    "(xs:integer) as xs:integer",
                                                                    (aCtx, aArgs) -> _twice (aArgs)));
  }

  @NonNull
  private static String _run (@NonNull final String sBody)
  {
    return XsltTestHelper.transform (HEAD + sBody + "</xsl:stylesheet>", null, null);
  }

  @Test
  public void testUseWhen ()
  {
    final String sTemplate = "<xsl:template name='xsl:initial-template'>" +
                             "<xsl:value-of select='$v'/>" +
                             "</xsl:template>";
    assertEquals ("yes",
                  _run ("<xsl:variable name='v' select='\"yes\"' use-when=\"function-available('p:twice', 1)\"/>" +
                        "<xsl:variable name='v' select='\"no\"' use-when=\"not(function-available('p:twice', 1))\"/>" +
                        sTemplate));
    assertEquals ("yes",
                  _run ("<xsl:variable name='v' select='\"yes\"' use-when=\"function-available('p:twice')\"/>" +
                        "<xsl:variable name='v' select='\"no\"' use-when=\"not(function-available('p:twice'))\"/>" +
                        sTemplate));
    // Wrong arity and unknown functions stay unavailable
    assertEquals ("no",
                  _run ("<xsl:variable name='v' select='\"yes\"' use-when=\"function-available('p:twice', 2)\"/>" +
                        "<xsl:variable name='v' select='\"no\"' use-when=\"not(function-available('p:twice', 2))\"/>" +
                        sTemplate));
    assertEquals ("no",
                  _run ("<xsl:variable name='v' select='\"yes\"' use-when=\"function-available('p:unknown')\"/>" +
                        "<xsl:variable name='v' select='\"no\"' use-when=\"not(function-available('p:unknown'))\"/>" +
                        sTemplate));
  }

  @Test
  public void testStaticVariable ()
  {
    assertEquals ("true 42",
                  _run ("<xsl:variable name='a' static='yes' select=\"function-available('p:twice', 1)\"/>" +
                        "<xsl:variable name='b' static='yes' select='p:twice(21)'/>" +
                        "<xsl:template name='xsl:initial-template'>" +
                        "<xsl:value-of select='$a, $b'/>" +
                        "</xsl:template>"));
  }

  @Test
  public void testBuiltinRuntimeFunctionsStayUnavailable ()
  {
    assertEquals ("false false false true",
                  _run ("<xsl:variable name='a' static='yes' select=\"function-available('current')\"/>" +
                        "<xsl:variable name='b' static='yes' select=\"function-available('key', 2)\"/>" +
                        "<xsl:variable name='c' static='yes' select=\"function-available('current-group')\"/>" +
                        "<xsl:variable name='d' static='yes' select=\"function-available('system-property')\"/>" +
                        "<xsl:template name='xsl:initial-template'>" +
                        "<xsl:value-of select='$a, $b, $c, $d'/>" +
                        "</xsl:template>"));
  }
}

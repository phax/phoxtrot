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
import static org.junit.Assert.assertNotEquals;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xsd.XsdTestSupport.Compiled;
import com.helger.phoxtrot.xsd.XsdTestSupport.EVerdict;

/**
 * Shared helpers of the property tests that compile a simple type or a whole schema under XSD 1.0
 * and 1.1 and check values or instance documents against it.
 *
 * @author Philip Helger
 */
final class XsdPropertyTestHelper
{
  /** The schema start tag with the XSD namespace bound to <code>xs</code> */
  static final String SCHEMA_START = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">";
  /** The schema end tag */
  static final String SCHEMA_END = "</xs:schema>";

  @PresentForCodeCoverage
  private static final XsdPropertyTestHelper INSTANCE = new XsdPropertyTestHelper ();

  private XsdPropertyTestHelper ()
  {}

  /**
   * Escape a string for an XML attribute value or text, keeping whitespace characters as character
   * references so that neither attribute value normalisation nor line end handling changes them.
   *
   * @param s
   *        The raw string. May not be <code>null</code>.
   * @return The escaped string. Never <code>null</code>.
   */
  @NonNull
  static String escape (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder (s.length () + 8);
    for (final char c : s.toCharArray ())
      switch (c)
      {
        case '&' -> aSB.append ("&amp;");
        case '<' -> aSB.append ("&lt;");
        case '>' -> aSB.append ("&gt;");
        case '"' -> aSB.append ("&quot;");
        case '\t' -> aSB.append ("&#9;");
        case '\n' -> aSB.append ("&#10;");
        case '\r' -> aSB.append ("&#13;");
        default -> aSB.append (c);
      }
    return aSB.toString ();
  }

  /**
   * Compile a schema under one version.
   *
   * @return The compiled schema or <code>null</code> if it is invalid. An unsupported construct
   *         fails the test.
   */
  @Nullable
  static XsdSchema compile (@NonNull final String sSchema, @NonNull final EXsdVersion eVersion)
  {
    final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, eVersion);
    assertNotEquals (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
    return aCompiled.verdict () == EVerdict.VALID ? aCompiled.schema () : null;
  }

  /**
   * Compile a schema under both versions, which must give the same verdict.
   *
   * @return The schema of each version (1.0 first), or <code>null</code> if it is invalid.
   */
  @Nullable
  static XsdSchema @Nullable [] compileBoth (@NonNull final String sSchema)
  {
    final XsdSchema aSchema10 = compile (sSchema, EXsdVersion.V10);
    final XsdSchema aSchema11 = compile (sSchema, EXsdVersion.V11);
    assertEquals (sSchema + " (1.0 vs 1.1)", Boolean.valueOf (aSchema10 != null), Boolean.valueOf (aSchema11 != null));
    if (aSchema10 == null)
      return null;
    return new XsdSchema [] { aSchema10, aSchema11 };
  }

  /**
   * Compile the simple type <code>t</code> restricting <code>xs:sBase</code> with the passed facets
   * under one version.
   *
   * @return The compiled type, <code>null</code> if the schema is invalid.
   */
  @Nullable
  static XsdSimpleType compileRestriction (@NonNull final String sBase,
                                           @NonNull final String sFacets,
                                           @NonNull final EXsdVersion eVersion)
  {
    final String sSchema = SCHEMA_START +
                           "<xs:simpleType name=\"t\"><xs:restriction base=\"xs:" +
                           sBase +
                           "\">" +
                           sFacets +
                           "</xs:restriction></xs:simpleType>" +
                           SCHEMA_END;
    final XsdSchema aSchema = compile (sSchema, eVersion);
    return aSchema == null ? null : (XsdSimpleType) aSchema.m_aTypes.get (new XsdName ("", "t"));
  }

  /**
   * @return The simple type <code>t</code> of a compiled schema. Never <code>null</code>.
   */
  @NonNull
  static XsdSimpleType getType (@NonNull final XsdSchema aSchema)
  {
    return (XsdSimpleType) aSchema.m_aTypes.get (new XsdName ("", "t"));
  }

  /**
   * @return <code>true</code> if the raw value is valid for the type; only the documented
   *         {@link XsdInvalidException} counts as invalid, anything else fails the test.
   */
  static boolean isValidValue (@NonNull final XsdSimpleType aType,
                               @NonNull final String sRaw,
                               @NonNull final EXsdVersion eVersion)
  {
    try
    {
      XsdSimpleTypeValidator.validate (aType, sRaw, eVersion);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  /**
   * @return <code>true</code> if the instance is valid. An unsupported construct fails the test.
   */
  static boolean isValidInstance (@NonNull final XsdSchema aSchema, @NonNull final String sInstance)
  {
    final EVerdict eVerdict = XsdTestSupport.validate (aSchema, sInstance);
    assertNotEquals (sInstance, EVerdict.UNSUPPORTED, eVerdict);
    return eVerdict == EVerdict.VALID;
  }
}

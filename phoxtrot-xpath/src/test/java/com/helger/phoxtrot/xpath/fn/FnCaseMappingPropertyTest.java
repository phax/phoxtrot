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
package com.helger.phoxtrot.xpath.fn;

import static org.junit.Assert.assertEquals;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for <code>fn:upper-case</code>,
 * <code>fn:lower-case</code> (F&amp;O 3.1 sections 5.4.7 and 5.4.8: the
 * full, language independent Unicode case mappings) and
 * <code>fn:normalize-unicode</code> (section 5.4.6) with the case mappings of
 * {@link String} under {@link Locale#ROOT} and {@link Normalizer} as the
 * reference. The JDK of the build uses the same Unicode version as the
 * engine's tables.
 *
 * @author Philip Helger
 */
public final class FnCaseMappingPropertyTest
{
  private static final XdmQName VAR_A = new XdmQName ("a");
  private static final XdmQName VAR_B = new XdmQName ("b");

  private static final String [] SPECIAL_CHARS = { "a",
                                                   "Z",
                                                   "\u00df",
                                                   "\u0130",
                                                   "\u0131",
                                                   "\u01c5",
                                                   "\u1e9e",
                                                   "\ufb01",
                                                   "\u0149",
                                                   "\u1f80",
                                                   "\u00e9",
                                                   "e\u0301",
                                                   "\u212b",
                                                   "\uff21",
                                                   "\ud801\udc00",
                                                   "\ud801\udc28",
                                                   "\ud83d\ude00",
                                                   "\u0345",
                                                   "\u2126",
                                                   "\u00b5",
                                                   "\u03c2",
                                                   " " };

  private static boolean _isXmlChar (final int nCodePoint)
  {
    return nCodePoint == 0x9 ||
           nCodePoint == 0xa ||
           nCodePoint == 0xd ||
           (nCodePoint >= 0x20 && nCodePoint <= 0xd7ff) ||
           (nCodePoint >= 0xe000 && nCodePoint <= 0xfffd) ||
           (nCodePoint >= 0x10000 && nCodePoint <= 0x10ffff);
  }

  /**
   * Strings of XML characters without U+03A3 (capital sigma): the JDK applies
   * the context dependent Final_Sigma rule when lower-casing it, which is a
   * point the specification leaves open.
   */
  private static final IGenerator <String> STRINGS = Generators.oneOf (Generators.lists (Generators.of (SPECIAL_CHARS), 0, 8)
                                                                                 .map (x -> String.join ("", x)),
                                                                       Generators.unicodeStrings (0, 12))
                                                               .filter (s -> s.codePoints ()
                                                                              .allMatch (c -> _isXmlChar (c) && c != 0x3a3));

  private final Map <String, CompiledXPath> m_aCache = new HashMap <> ();
  private final StaticContext m_aStaticCtx = new StaticContext ();

  public FnCaseMappingPropertyTest ()
  {
    m_aStaticCtx.declareVariable (VAR_A);
    m_aStaticCtx.declareVariable (VAR_B);
  }

  @NonNull
  private String _evalString (@NonNull final String sExpr, @NonNull final String sA, @NonNull final String sB)
  {
    final CompiledXPath aXPath = m_aCache.computeIfAbsent (sExpr, x -> XPathCompiler.compile (x, m_aStaticCtx));
    final DynamicContext aCtx = aXPath.createDynamicContext ()
                                      .withVariable (VAR_A, XdmSequence.of (XdmAtomic.string (sA)))
                                      .withVariable (VAR_B, XdmSequence.of (XdmAtomic.string (sB)));
    final XdmSequence aResult = aXPath.evaluate (aCtx);
    assertEquals (sExpr, 1, aResult.size ());
    return ((XdmAtomic) aResult.get (0)).getString ();
  }

  @NonNull
  private static String _codepoints (@NonNull final String s)
  {
    final StringBuilder aSB = new StringBuilder ();
    s.codePoints ().forEach (c -> aSB.append (Integer.toHexString (c)).append (' '));
    return aSB.toString ();
  }

  /**
   * <code>fn:upper-case</code> and <code>fn:lower-case</code> apply the full
   * Unicode case mappings without language specific rules, as
   * {@link String#toUpperCase(Locale)} with {@link Locale#ROOT} does.
   */
  @Test
  public void testCaseMapping ()
  {
    new PropertyChecker ().setTries (3000).forAll (STRINGS, s -> {
      final String sMsg = _codepoints (s);
      assertEquals (sMsg, _codepoints (s.toUpperCase (Locale.ROOT)), _codepoints (_evalString ("upper-case($a)", s, "")));
      assertEquals (sMsg, _codepoints (s.toLowerCase (Locale.ROOT)), _codepoints (_evalString ("lower-case($a)", s, "")));
    });
  }

  /**
   * <code>fn:normalize-unicode</code> gives the four normalisation forms as
   * {@link Normalizer} does; the form name is matched case-insensitively
   * after removing leading and trailing spaces, and the empty form returns
   * the input unchanged (F&amp;O 3.1 section 5.4.6).
   */
  @Test
  public void testNormalizeUnicode ()
  {
    new PropertyChecker ().setTries (2000).forAll (STRINGS, Generators.of (Normalizer.Form.values ()), Generators.booleans (), (s, eForm, aDecorate) -> {
      final String sMsg = _codepoints (s) + eForm;
      final String sFormName = aDecorate.booleanValue () ? " " + eForm.name ().toLowerCase (Locale.ROOT) + " " : eForm.name ();
      assertEquals (sMsg, _codepoints (Normalizer.normalize (s, eForm)), _codepoints (_evalString ("normalize-unicode($a, $b)", s, sFormName)));
      assertEquals (sMsg, _codepoints (s), _codepoints (_evalString ("normalize-unicode($a, $b)", s, "")));
    });
  }
}

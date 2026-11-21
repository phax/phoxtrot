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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.phoxtrot.xpath.context.EXsdVersion;

/**
 * Test class for class {@link XsdPatternMatcher}.
 *
 * @author Philip Helger
 */
public final class XsdPatternMatcherTest
{
  @Test
  public void testBasic ()
  {
    final XsdPatternMatcher aMatcher = XsdPatternMatcher.compile ("[A-Z]{2}[0-9]+", EXsdVersion.V10);
    assertTrue (aMatcher.matches ("DE123"));
    // Anchored at both ends
    assertFalse (aMatcher.matches ("xDE123"));
    assertFalse (aMatcher.matches ("DE123x"));
    assertFalse (aMatcher.matches ("D1"));

    // XSD specifics: no anchors (^ and $ are literals), class subtraction, name characters
    assertTrue (XsdPatternMatcher.compile ("^a$", EXsdVersion.V10).matches ("^a$"));
    assertTrue (XsdPatternMatcher.compile ("[a-z-[aeiou]]+", EXsdVersion.V10).matches ("bcd"));
    assertFalse (XsdPatternMatcher.compile ("[a-z-[aeiou]]+", EXsdVersion.V10).matches ("bad"));
    assertTrue (XsdPatternMatcher.compile ("\\i\\c*", EXsdVersion.V10).matches ("_a1"));
  }

  @Test (expected = XsdInvalidException.class)
  public void testInvalidGrammar ()
  {
    // Lazy quantifiers are not XSD
    XsdPatternMatcher.compile ("a+?", EXsdVersion.V10);
  }
}

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

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xpath.context.EXsdVersion;

/**
 * A matcher for the regular expression of an XSD <code>pattern</code> facet, without a schema:
 * the same grammar check and matching as the validator, anchored at both ends as XSD requires.
 * Thread-safe.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdPatternMatcher
{
  private final XsdPattern m_aPattern;

  private XsdPatternMatcher (@NonNull final XsdPattern aPattern)
  {
    m_aPattern = aPattern;
  }

  /**
   * Check whether a value matches the pattern.
   *
   * @param sValue
   *        The value. No whitespace normalisation is applied.
   * @return <code>true</code> if the whole value matches the pattern.
   * @throws XsdUnsupportedException
   *         if the regular expression is too complex for the value
   */
  public boolean matches (@NonNull final String sValue)
  {
    ValueEnforcer.notNull (sValue, "Value");
    return m_aPattern.matches (sValue);
  }

  /**
   * Compile an XSD regular expression.
   *
   * @param sPattern
   *        The regular expression as in a <code>pattern</code> facet. May not be
   *        <code>null</code>.
   * @param eVersion
   *        The XSD version whose grammar applies. May not be <code>null</code>.
   * @return The matcher. Never <code>null</code>.
   * @throws XsdInvalidException
   *         if the pattern is not a valid XSD regular expression
   * @throws XsdUnsupportedException
   *         if the engine cannot execute the pattern
   */
  @NonNull
  public static XsdPatternMatcher compile (@NonNull final String sPattern, @NonNull final EXsdVersion eVersion)
  {
    ValueEnforcer.notNull (sPattern, "Pattern");
    ValueEnforcer.notNull (eVersion, "Version");
    XsdPattern.validateSyntax (sPattern, eVersion);
    return new XsdPatternMatcher (XsdPattern.compile (sPattern, eVersion));
  }
}

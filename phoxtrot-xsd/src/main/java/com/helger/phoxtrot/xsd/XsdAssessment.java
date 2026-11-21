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

import com.helger.collection.commons.ICommonsList;

/**
 * The result of {@link XsdSchema#assessText(String, XsdValidationOptions)}:
 * the validation result plus the governing type of every element and attribute
 * that was assessed.
 *
 * @param result
 *        The validation result, the same as
 *        {@link XsdSchema#validateText(String, XsdValidationOptions)} returns.
 * @param nodeTypes
 *        The governing types in document order, attributes after their
 *        element. Complete for a valid instance; for an invalid one only the
 *        nodes assessed before the validation stopped (with the default
 *        options it continues after errors, so usually most nodes).
 *        Attributes without a declaration (e.g. matched by a lax wildcard) and
 *        <code>xsi:</code> attributes have no entry.
 * @author Philip Helger
 */
public record XsdAssessment (@NonNull XsdValidationResult result, @NonNull ICommonsList <XsdNodeType> nodeTypes)
{}

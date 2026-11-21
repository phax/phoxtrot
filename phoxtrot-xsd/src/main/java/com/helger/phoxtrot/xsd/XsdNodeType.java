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
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;

/**
 * The governing type definition an element or attribute of an instance was
 * assessed against, with <code>xsi:type</code>, conditional type assignment,
 * substitution groups and wildcards already resolved.
 * <p>
 * The node is identified independently of any tree implementation, so that a
 * caller can find it in its own parse of the same instance: by the 0-based
 * positions among the <em>element</em> children from the document node down
 * (the document element is always <code>[0]</code>), plus the expanded name
 * for an attribute. Element-only positions do not depend on how a parser
 * splits or merges text, comments and processing instructions.
 *
 * @param elementPath
 *        The 0-based element positions from the document node to the element
 *        (or to the element carrying the attribute). Never empty.
 * @param attribute
 *        The expanded name of the attribute, <code>null</code> for an element.
 * @param type
 *        The governing type definition.
 * @author Philip Helger
 */
public record XsdNodeType (@NonNull ICommonsList <Integer> elementPath,
                           @Nullable XsdName attribute,
                           @NonNull IXsdTypeDefinitionView type)
{
  /**
   * @return <code>true</code> if the node is an attribute.
   */
  public boolean isAttribute ()
  {
    return attribute != null;
  }
}

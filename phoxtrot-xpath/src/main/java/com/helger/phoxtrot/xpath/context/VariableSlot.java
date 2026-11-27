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
package com.helger.phoxtrot.xpath.context;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.XdmQName;

/**
 * The slot of one variable declaration in a {@link VariableFrameLayout}. A static context that
 * declares a variable with a slot ({@link StaticContext#declareFrameVariable(VariableSlot)}) lets
 * the compiled references read it from the {@link VariableFrame} of the dynamic context instead of
 * looking it up by name.
 *
 * @param layout
 *        The layout the slot belongs to.
 * @param index
 *        The 0-based index in the frame.
 * @param name
 *        The variable name.
 * @author Philip Helger
 */
@Immutable
public record VariableSlot (@NonNull VariableFrameLayout layout, @Nonnegative int index, @NonNull XdmQName name)
{
  @Override
  public String toString ()
  {
    return "VariableSlot[$" + name.getEQName () + "@" + index + "]";
  }
}

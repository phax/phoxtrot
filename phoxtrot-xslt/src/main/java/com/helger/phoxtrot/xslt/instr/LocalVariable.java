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
package com.helger.phoxtrot.xslt.instr;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.VariableFrame;
import com.helger.phoxtrot.xpath.context.VariableSlot;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.model.VariableDef;
import com.helger.phoxtrot.xslt.runtime.LazyVariableException;
import com.helger.phoxtrot.xslt.runtime.TryScope;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.tree.AbstractXslNode;

/**
 * A local xsl:variable: binds its value for the following siblings. The value
 * is evaluated on first use, in the context of the declaration, and only then
 * (as by Saxon; XPath 3.1 section 2.3.4 leaves the order of evaluation open):
 * a variable no path uses raises no error. The required type ("as") is checked
 * at that point as well, and an error is located at the xsl:variable element
 * unless it has a location of its own.
 *
 * @author Philip Helger
 */
@Immutable
public final class LocalVariable implements IBindingInstruction
{
  /**
   * The value of one binding, evaluated on first use in the context of the
   * declaration with the frame it is read from.
   */
  @NotThreadSafe
  private static final class LazyValue extends VariableFrame.AbstractLazyValue
  {
    private final LocalVariable m_aOwner;
    // The context of the declaration; set right after binding, released after
    // the evaluation
    private XsltContext m_aCtx;

    LazyValue (@NonNull final LocalVariable aOwner)
    {
      m_aOwner = aOwner;
    }

    @Override
    @NonNull
    protected XdmSequence compute (@NonNull final VariableFrame aHolder)
    {
      final XsltContext aCtx = m_aCtx.withFrame (aHolder);
      try
      {
        return m_aOwner.m_aDef.evaluate (aCtx);
      }
      catch (final XPathException ex)
      {
        throw LazyVariableException.of (ex, m_aOwner.m_aDef.getElement (), aCtx.get (TryScope.KEY));
      }
    }

    @Override
    protected void onComputed ()
    {
      m_aCtx = null;
    }
  }

  private final VariableDef m_aDef;
  private final VariableSlot m_aSlot;

  /**
   * Create a local variable instruction.
   *
   * @param aDef
   *        The definition.
   * @param aSlot
   *        The slot of the declaration in the frame of its body.
   */
  public LocalVariable (@NonNull final VariableDef aDef, @NonNull final VariableSlot aSlot)
  {
    m_aDef = aDef;
    m_aSlot = aSlot;
  }

  /**
   * Get the definition of the variable.
   *
   * @return The definition. Never <code>null</code>.
   */
  @NonNull
  public VariableDef getDefinition ()
  {
    return m_aDef;
  }

  /**
   * Bind the variable lazily in its slot: the value is evaluated in the
   * context of the declaration (with the variables bound before it, not at the
   * first use) when it is first needed. The innermost xsl:try scope of the
   * declaration context is used when the evaluation fails later, so that the
   * dynamic error is wrapped in a {@link LazyVariableException} that carries
   * this scope and is located at the xsl:variable element if it has no
   * location of its own.
   *
   * @param aCtx
   *        The context at the declaration.
   * @return The context for the following siblings, with the variable bound;
   *         the same context unless the variable is the first binding of its
   *         body.
   */
  @NonNull
  public XsltContext bind (@NonNull final XsltContext aCtx)
  {
    final LazyValue aValue = new LazyValue (this);
    final XsltContext ret = aCtx.withLazySlotValue (m_aSlot, aValue);
    aValue.m_aCtx = ret;
    return ret;
  }

  @Nullable
  public AbstractXslNode getLocation ()
  {
    return m_aDef.getElement ();
  }
}

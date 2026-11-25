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
package com.helger.phoxtrot.xpath.eval;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.VariableFrame;
import com.helger.phoxtrot.xpath.context.VariableFrameLayout;
import com.helger.phoxtrot.xpath.context.VariableSlot;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * The function item of an inline function expression: a closure over the
 * variables in scope where it was created. The focus is absent in its body
 * (XPath 3.1 section 3.1.7).
 *
 * @author Philip Helger
 */
@Immutable
public final class InlineFunctionItem extends AbstractFunctionItem
{
  /**
   * The compiled body of an inline function expression with its frame layout.
   *
   * @param expr
   *        The compiled body.
   * @param layout
   *        The layout of the frame of a call: the parameters and the variables the body binds.
   * @param paramSlots
   *        The slots of the parameters, in order.
   * @param captureNames
   *        The variables of the enclosing expression that the body references, bound by name in
   *        the closure.
   * @param captureSlots
   *        Their slots in the frame of the enclosing expression (or inline function body).
   */
  @Immutable
  record Body (@NonNull CompiledExpr expr,
               @NonNull VariableFrameLayout layout,
               @NonNull VariableSlot [] paramSlots,
               @NonNull XdmQName [] captureNames,
               @NonNull VariableSlot [] captureSlots)
  {}

  private final IExpr.InlineFunctionExpr m_aExpr;
  private final Body m_aBody;
  private final ICommonsList <SeqType> m_aParamTypes;
  private final SeqType m_aReturnType;
  private final DynamicContext m_aClosure;

  /**
   * @param aExpr
   *        The inline function expression.
   * @param aBody
   *        The compiled body of the expression.
   * @param aCtx
   *        The context at creation; its variables are captured.
   */
  InlineFunctionItem (final IExpr.@NonNull InlineFunctionExpr aExpr,
                      @NonNull final Body aBody,
                      @NonNull final DynamicContext aCtx)
  {
    m_aExpr = aExpr;
    m_aBody = aBody;
    m_aParamTypes = new CommonsArrayList <> ();
    for (final IExpr.Param aParam : aExpr.params ())
      m_aParamTypes.add (aParam.type () != null ? aParam.type () : SeqType.ANY);
    m_aReturnType = aExpr.returnType () != null ? aExpr.returnType () : SeqType.ANY;
    // The frame of the host variables is changed in place while their scope runs; the function
    // keeps the bindings of its creation, also beyond that scope
    // The frame of the enclosing expression is changed in place too: the variables the body
    // references are bound by name with their current values, and the frame is not kept
    // (withFrame drops it)
    final VariableFrame aFrame = aCtx.getFrame ();
    final VariableFrame aLocalFrame = aCtx.getLocalFrame ();
    DynamicContext aClosure = aCtx.withFocus (null, 0, 0);
    if (aFrame != null || aLocalFrame != null)
      aClosure = aClosure.withFrame (aFrame == null ? null : aFrame.getSnapshot ());
    if (aLocalFrame != null)
    {
      final XdmQName [] aNames = aBody.captureNames ();
      final VariableSlot [] aSlots = aBody.captureSlots ();
      for (int i = 0; i < aNames.length; ++i)
        aClosure = aClosure.withVariable (aNames[i], aLocalFrame.get (aSlots[i].index ()));
    }
    m_aClosure = aClosure;
  }

  @Nullable
  public XdmQName getName ()
  {
    return null;
  }

  public int getArity ()
  {
    return m_aParamTypes.size ();
  }

  @NonNull
  public List <SeqType> getParamTypes ()
  {
    return m_aParamTypes;
  }

  @NonNull
  public SeqType getReturnType ()
  {
    return m_aReturnType;
  }

  /**
   * Evaluate the body in the captured closure (without focus), in a new frame with each parameter
   * bound to its argument. The caller's context is ignored.
   */
  @NonNull
  public XdmSequence call (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final DynamicContext aBodyCtx = m_aClosure.withNewLocalFrame (m_aBody.layout ());
    final VariableFrame aFrame = aBodyCtx.getLocalFrame ();
    final VariableSlot [] aParamSlots = m_aBody.paramSlots ();
    for (int i = 0; i < aParamSlots.length; ++i)
      aFrame.set (aParamSlots[i], aArgs.get (i));
    return m_aBody.expr ().evaluate (aBodyCtx);
  }

  @Override
  public String toString ()
  {
    return "function#" + getArity ();
  }
}

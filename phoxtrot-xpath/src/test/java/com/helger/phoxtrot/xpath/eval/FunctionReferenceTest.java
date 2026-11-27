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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.EOccurrence;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FnAccessor;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test class for the kept values of named function references and of maps built from literals
 * and function references: the item of a function that captures nothing and such a map are
 * returned again for the same resolved functions, a focus-dependent function and any other map
 * are created per evaluation, and a change of the function library is seen.
 *
 * @author Philip Helger
 */
public final class FunctionReferenceTest
{
  private static final String NS_F = "urn:f";
  private static final XdmQName F_X = new XdmQName (NS_F, "x", "f");
  private static final SeqType INTEGER_ONE = SeqType.of (ItemType.atomic (EAtomicType.INTEGER), EOccurrence.ONE);

  private static final IXdmNode DOC_NODE;

  static
  {
    try
    {
      DOC_NODE = new XdmParser ().parse ("<r><a/><b/><c/></r>");
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  @NonNull
  private static DynamicContext _ctx (@NonNull final CompiledXPath aCompiled)
  {
    return aCompiled.createDynamicContext ().withFocus (DOC_NODE, 1, 1);
  }

  @NonNull
  private static String _joined (@NonNull final XdmSequence aSeq)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aSeq)
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (FnAccessor.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  @NonNull
  private static String _evalOrError (@NonNull final String sExpr)
  {
    try
    {
      final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, new StaticContext ());
      return _joined (aCompiled.evaluate (_ctx (aCompiled)));
    }
    catch (final XPathException ex)
    {
      return "error " + ex.getCode ();
    }
  }

  @Test
  public void testFunctionItemKept ()
  {
    final CompiledXPath aCompiled = XPathCompiler.compile ("abs#1", new StaticContext ());
    final XdmSequence aFirst = aCompiled.evaluate (_ctx (aCompiled));
    assertSame (aFirst, aCompiled.evaluate (_ctx (aCompiled)));
    assertEquals ("3",
                  _joined (XPathEvaluator.callFunction (_ctx (aCompiled),
                                                        aFirst.getFirst (),
                                                        List.of (XdmSequence.of (XdmAtomic.integer (-3))))));
    // Called in a loop, the kept item gives the same results as the static call
    assertEquals (_evalOrError ("for $i in (-2 to 2) return abs($i)"),
                  _evalOrError ("for $i in (-2 to 2) return abs#1($i)"));
  }

  @Test
  public void testFocusDependentNotKept ()
  {
    final CompiledXPath aCompiled = XPathCompiler.compile ("name#0", new StaticContext ());
    assertNotSame (aCompiled.evaluate (_ctx (aCompiled)).getFirst (), aCompiled.evaluate (_ctx (aCompiled)).getFirst ());
    // Each item captures the focus of its reference
    assertEquals ("a b c", _evalOrError ("/r/* ! name#0()"));
    assertEquals ("a b c", _evalOrError ("/r/* ! map { 'n' : name#0 }('n')()"));
    assertEquals ("a b c", _evalOrError ("/r/* ! map { 'n' : name#0, 'm' : abs#1 }('n')()"));
    // The form of XPathEvaluatorPropertyTest.testConstantMaps
    assertEquals ("a,2.5 b,2.5 c,2.5",
                  _evalOrError ("/r/* ! (let $m := map { 'a' : abs#1, 1 : name#0 } return string-join(" +
                                "for $k in sort(map:keys($m), (), function ($x) { string($x) || ($x instance of xs:integer) }) " +
                                "return let $v := $m($k) return string(if ($v instance of function(*)) " +
                                "then (if (function-arity($v) eq 0) then $v() else $v(-2.5)) else $v), ','))"));
  }

  @Test
  public void testConstantMapKept ()
  {
    for (final String sExpr : new String [] { "map { 'a' : abs#1, 'b' : 1, 3 : string#1 }",
                                              "map { 'a' : 1 }",
                                              "map { }" })
    {
      final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, new StaticContext ());
      final XdmSequence aFirst = aCompiled.evaluate (_ctx (aCompiled));
      assertSame (sExpr, aFirst, aCompiled.evaluate (_ctx (aCompiled)));
    }
    // A key that is no literal or a value that is neither a literal nor a function reference:
    // built per evaluation
    for (final String sExpr : new String [] { "map { string('a') : abs#1 }", "map { 'a' : abs#1, 'b' : (1, 2) }" })
    {
      final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, new StaticContext ());
      assertNotSame (sExpr, aCompiled.evaluate (_ctx (aCompiled)), aCompiled.evaluate (_ctx (aCompiled)));
    }
    // The kept map in a loop: the same results as the map built each time
    assertEquals (_evalOrError ("for $k in ('a', 'b', 'c') return map { string('a') : abs#1, 'b' : round#1 }($k)(-2.5)"),
                  _evalOrError ("for $k in ('a', 'b', 'c') return map { 'a' : abs#1, 'b' : round#1 }($k)(-2.5)"));
  }

  @Test
  public void testDuplicateKeyRaisedEveryTime ()
  {
    final CompiledXPath aCompiled;
    try
    {
      aCompiled = XPathCompiler.compile ("map { 'a' : abs#1, 'a' : 2 }", new StaticContext ());
    }
    catch (final XPathException ex)
    {
      // Raised statically: nothing is evaluated
      assertEquals ("XQDY0137", ex.getCode ());
      return;
    }
    for (int i = 0; i < 2; ++i)
      try
      {
        aCompiled.evaluate (_ctx (aCompiled));
        fail ();
      }
      catch (final XPathException ex)
      {
        assertEquals ("XQDY0137", ex.getCode ());
      }
  }

  @Test
  public void testLibraryChangeSeen ()
  {
    final FunctionLibrary aLib = FunctionLibrary.getDefault ().createChild ();
    aLib.register (F_X, List.of (INTEGER_ONE), INTEGER_ONE, (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.integer (1)));
    final StaticContext aSC = new StaticContext ().setFunctionLibrary (aLib);
    aSC.declareNamespace ("f", NS_F);
    final CompiledXPath aRef = XPathCompiler.compile ("f:x#1(0)", aSC);
    final CompiledXPath aMap = XPathCompiler.compile ("map { 'k' : f:x#1 }('k')(0)", aSC);
    assertEquals ("1", _joined (aRef.evaluate (_ctx (aRef))));
    assertEquals ("1", _joined (aMap.evaluate (_ctx (aMap))));
    // The registration replaces the function: the references resolve to the new one
    aLib.register (F_X, List.of (INTEGER_ONE), INTEGER_ONE, (aCtx, aArgs) -> XdmSequence.of (XdmAtomic.integer (2)));
    assertEquals ("2", _joined (aRef.evaluate (_ctx (aRef))));
    assertEquals ("2", _joined (aMap.evaluate (_ctx (aMap))));
  }
}

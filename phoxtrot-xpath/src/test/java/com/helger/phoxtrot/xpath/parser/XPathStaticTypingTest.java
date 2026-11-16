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
package com.helger.phoxtrot.xpath.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.StaticContext;

/**
 * Test class for {@link XPathStaticTyping}.
 *
 * @author Philip Helger
 */
public final class XPathStaticTypingTest
{
  /** Always true at run time, but not statically. */
  private static final String COND = "current-dateTime() eq dateTime(current-date(), current-time())";

  @NonNull
  private static StaticContext _ctx (final boolean bStaticTyping)
  {
    return new StaticContext ().declareVariable (new XdmQName ("x")).setStaticTyping (bStaticTyping);
  }

  @NonNull
  private static String _infer (@NonNull final String sExpr)
  {
    final StaticContext aCtx = _ctx (true);
    return XPathStaticTyping.checkTypes (XPathParser.parse (sExpr, aCtx), aCtx).toString ();
  }

  private static void _expectTypeError (@NonNull final String... aExprs)
  {
    for (final String sExpr : aExprs)
    {
      try
      {
        XPathParser.parse (sExpr, _ctx (true));
        fail ("Expected XPTY0004 for: " + sExpr);
      }
      catch (final XPathException ex)
      {
        assertEquals (sExpr + " -> " + ex.getMessage (), "XPTY0004", ex.getCode ());
      }
      // Without the feature the same expression compiles
      assertNotNull (XPathParser.parse (sExpr, _ctx (false)));
    }
  }

  private static void _expectFine (@NonNull final String... aExprs)
  {
    for (final String sExpr : aExprs)
      try
      {
        assertNotNull (sExpr, XPathParser.parse (sExpr, _ctx (true)));
      }
      catch (final XPathException ex)
      {
        fail (sExpr + " -> " + ex.getMessage ());
      }
  }

  @Test
  public void testInference ()
  {
    assertEquals ("xs:integer", _infer ("1"));
    assertEquals ("empty-sequence()", _infer ("()"));
    assertEquals ("(xs:integer | xs:string)+", _infer ("(1, 'a')"));
    assertEquals ("xs:integer?", _infer ("if (" + COND + ") then 1 else ()"));
    assertEquals ("(xs:string | xs:integer)", _infer ("if (" + COND + ") then 'a' else 1"));
    // An alternative that another one subsumes is dropped
    assertEquals ("xs:decimal", _infer ("if (" + COND + ") then 1 else 1.5"));
    assertEquals ("xs:integer*", _infer ("1 to 3"));
    assertEquals ("xs:double", _infer ("1 + 2e0"));
    assertEquals ("xs:decimal", _infer ("1 div 2"));
    assertEquals ("xs:integer", _infer ("1 idiv 2"));
    assertEquals ("xs:boolean", _infer ("1 = (1, 2)"));
    assertEquals ("xs:string", _infer ("'a' || 1"));
    assertEquals ("xs:string+", _infer ("for $x in (1, 2) return string($x)"));
    assertEquals ("xs:string*", _infer ("for $x in (1, 2)[. gt 1] return string($x)"));
    assertEquals ("xs:integer+", _infer ("let $x := (1, 2) return $x"));
    // A variable of the static context without a declared type
    assertEquals ("item()*", _infer ("$x"));
    assertEquals ("item()", _infer ("."));
    assertEquals ("element()*", _infer ("a/b"));
    assertEquals ("attribute()*", _infer ("@a"));
    // A numeric predicate keeps at most one item
    assertEquals ("element()?", _infer ("(a/b)[1]"));
    assertEquals ("function(item()*) as xs:boolean", _infer ("exists#1"));
    assertEquals ("function(item()*) as xs:boolean?", _infer ("function ($a) { if ($a) then true() else () }"));
    assertEquals ("function(xs:string?) as xs:integer", _infer ("string-length(?)"));
  }

  @Test
  public void testEmptyType ()
  {
    for (final String sExpr : new String [] { "()[1]",
                                               "if (" + COND + ") then () else ()",
                                               "for $a in () return 1",
                                               "count(() ! 1)" })
    {
      try
      {
        XPathParser.parse (sExpr, _ctx (true));
        fail ("Expected XPST0005 for: " + sExpr);
      }
      catch (final XPathException ex)
      {
        assertEquals (sExpr + " -> " + ex.getMessage (), "XPST0005", ex.getCode ());
      }
      assertNotNull (XPathParser.parse (sExpr, _ctx (false)));
    }
    // The exempt forms
    _expectFine ("()", "(())", "data(())", "count(())", "if (" + COND + ") then () else 1");
  }

  @Test
  public void testDefaultOff ()
  {
    // The static checks only run with the feature
    assertNotNull (XPathParser.parse ("string-length(1)", _ctx (false)));
  }

  @Test
  public void testArguments ()
  {
    _expectTypeError ("string-length(1)",
                      "function-arity(if (" + COND + ") then dateTime#2 else 1)",
                      // The empty branch violates the occurrence
                      "function-arity(if (" + COND + ") then dateTime#2 else ())",
                      "has-children(if (" + COND + ") then . else 1)",
                      "unparsed-text('a.txt', if (" + COND + ") then 'utf-8' else 1)",
                      "filter((), true())",
                      // An undeclared variable is item()*
                      "string-length($x)",
                      // Atomising a function item fails
                      "abs(exists#1)");
    _expectFine ("string-length('abc')",
                 "string-length(())",
                 "abs(1 + 2)",
                 "concat('a', 1, ())",
                 "function-arity(if (" + COND + ") then dateTime#2 else concat#3)",
                 // Promotion and xs:untypedAtomic
                 "math:sqrt(4)",
                 "math:sqrt(xs:float(4))",
                 "starts-with(xs:anyURI('urn:a'), 'urn')",
                 "math:sqrt(xs:untypedAtomic('4'))",
                 // Nodes of untyped data atomise to xs:untypedAtomic
                 "string-length((a/b)[1])",
                 "count(a/b)",
                 "fn:for-each-pair((1, 2), (3, 4), function ($a, $b) { $a + $b })");
  }

  @Test
  public void testFunctionTypes ()
  {
    _expectTypeError ("filter(1 to 10, function ($a) { if ($a eq 100) then () else true() })",
                      "filter(1 to 10, function ($a) { if ($a eq 100) then 0 else true() })",
                      "filter(1 to 10, function ($a) { if ($a eq 100) then (true(), false()) else true() })",
                      // The parameter type is narrower than the required one
                      "filter(('1', '2'), function ($a as xs:integer) as xs:boolean { $a eq 100 })",
                      "filter((), if (" + COND + ") then exists#1 else 1)");
    _expectFine ("filter(1 to 10, function ($a) { $a mod 2 = 0 })",
                 "filter(1 to 10, exists#1)",
                 "filter(1 to 10, function ($a as item()) as xs:boolean { true() })",
                 "sort((3, 1, 2), (), function ($a) { -$a })");
  }

  @Test
  public void testContextArgument ()
  {
    // The zero-argument form checks the context item
    _expectTypeError ("(if (" + COND + ") then . else 1) ! has-children()", "1 ! name()");
    _expectFine ("a ! has-children()", "a/name()", "(1, 2) ! string()");
  }
}

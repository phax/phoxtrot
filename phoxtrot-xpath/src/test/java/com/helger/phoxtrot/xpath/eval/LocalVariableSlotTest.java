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

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Test the variables an expression binds itself (<code>for</code>, <code>let</code>, quantifiers,
 * inline function parameters), which are bound by slot in a frame that is changed in place: the
 * cases where that could show a wrong binding.
 *
 * @author Philip Helger
 */
public final class LocalVariableSlotTest
{
  @NonNull
  private static String _eval (@NonNull final String sExpr)
  {
    final StaticContext aSC = new StaticContext ().declareVariable (new XdmQName ("ext"));
    final DynamicContext aDC = new DynamicContext (aSC).withVariable (new XdmQName ("ext"), XdmSequence.of (XdmAtomic.integer (5)));
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : XPathCompiler.compile (sExpr, aSC).evaluate (aDC))
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      aSB.append (Atomizer.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  @Test
  public void testShadowing ()
  {
    assertEquals ("3 4 3 4", _eval ("for $a in (1, 2) return for $a in (3, 4) return $a"));
    assertEquals ("11 12 21 22", _eval ("for $a in (1, 2) return (for $b in (1, 2) return $a * 10 + $b)"));
    assertEquals ("2 1", _eval ("let $x := 1 return (let $x := 2 return $x, $x)"));
    assertEquals ("7 5", _eval ("(let $ext := 7 return $ext), $ext"));
    assertEquals ("3", _eval ("let $x := 1 return function ($x) { $x } (2) + $x"));
    assertEquals ("2", _eval ("let $a := 1, $b := $a + 1 return $b"));
  }

  @Test
  public void testQuantified ()
  {
    assertEquals ("true", _eval ("some $x in (1, 2), $y in (3, 4) satisfies $x + $y = 6"));
    assertEquals ("false", _eval ("every $x in (1, 2), $y in (3, 4) satisfies $x + $y lt 6"));
    assertEquals ("true", _eval ("every $x in (1, 2) satisfies some $x in ($x, 9) satisfies $x = 9"));
    assertEquals ("1 2", _eval ("for $i in (1, 2, 3) return $i[some $j in (1, 2) satisfies $j = $i]"));
  }

  @Test
  public void testClosuresKeepTheirIteration ()
  {
    assertEquals ("10 20 30", _eval ("let $fs := for $i in 1 to 3 return function () { $i * 10 } return $fs ! .()"));
    assertEquals ("111 112", _eval ("for $a in (1, 2) return function ($b) { function ($c) { $a + $b + $c } } (10) (100)"));
    // A variable of a function body captured by a function created in it
    assertEquals ("9", _eval ("function ($x) { let $y := $x * 2 return function () { $y + $x } } (3) ()"));
    // Two levels out, through the closure of the enclosing function
    assertEquals ("1 2", _eval ("for $a in (1, 2) return function () { function () { $a } } () ()"));
    assertEquals ("3 2 1", _eval ("let $neg := -1 return sort ((3, 1, 2), (), function ($v) { $v * $neg })"));
  }

  @Test
  public void testRecursiveFunctionItems ()
  {
    // Every call has its own frame: the outer call's $n and $i are unchanged after the inner one
    assertEquals ("10", _eval ("let $f := function ($f, $n) { if ($n = 0) then 0 else $n + $f ($f, $n - 1) } return $f ($f, 4)"));
    assertEquals ("21",
                  _eval ("let $s := function ($s, $n) { sum (for $i in 1 to 2 return if ($n = 0) then 0 else $s ($s, $n - 1) + $i) }" +
                         " return $s ($s, 3)"));
  }

  @Test
  public void testMemoisedCallPerBinding ()
  {
    assertEquals ("a aa aaa", _eval ("for $i in 1 to 3 return string-join ((1 to $i) ! 'a')"));
    assertEquals ("A A AA AA", _eval ("for $s in ('a', 'aa') return for $j in 1 to 2 return upper-case ($s)"));
  }
}

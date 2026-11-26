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

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.base.wrapper.Wrapper;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.XPathPropertyTestHelper;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the optimised evaluation paths of
 * {@link XPathEvaluator} (early exit, the name index, step unions,
 * focus-independent predicates, name predicates and loop-invariant calls).
 * Each optimised expression is compared on random documents with an
 * equivalent expression that is written so that the optimisation does not
 * apply: both must raise the same error or return the same items.
 *
 * @author Philip Helger
 */
public final class XPathEvaluatorPropertyTest
{
  /** Relative and absolute paths, with and without predicates */
  private static final String [] PATHS = { "//a",
                                           "//b",
                                           "//p:a",
                                           "//d:a",
                                           "//zz",
                                           "//*",
                                           "//a/b",
                                           "//a//c",
                                           "/*/a",
                                           "a",
                                           "*",
                                           ".//b",
                                           "//@x",
                                           "//@p:x",
                                           "//a/@y",
                                           "//text()",
                                           "//comment()",
                                           "//processing-instruction()",
                                           "//a[2]",
                                           "//b[@x]",
                                           "//*[@y = '1']",
                                           "(//a)[1]",
                                           "//a[last()]",
                                           "//c/..",
                                           "//a/ancestor::b",
                                           "descendant::b[1]",
                                           "//b/following-sibling::a",
                                           "//a/preceding::c",
                                           "//node()",
                                           "child::node()",
                                           "//b[a]/c",
                                           "//a[b][2]",
                                           "//zz/a",
                                           "(//zz | //a)/b",
                                           // Depth-first existence: child, attribute and self steps after the first
                                           "/*/a/b",
                                           "/*/a/b/@x",
                                           "a/b/c",
                                           "a/b[2]/c",
                                           "a/b[last()]/c",
                                           "*/*[@x]/c",
                                           "//a/b/c",
                                           "//a/b[1]/@y",
                                           "//b/self::*/a[last()]/*",
                                           "a/@x",
                                           "ancestor::*/b/a" };

  /** Element names as name test and as the QName constructor of the reference */
  private static final String [] [] ELEMENT_NAMES = { { "a", "QName('', 'a')" },
                                                      { "b", "QName('', 'b')" },
                                                      { "c", "QName('', 'c')" },
                                                      { "p:a", "QName('urn:p', 'a')" },
                                                      { "p:b", "QName('urn:p', 'b')" },
                                                      { "d:a", "QName('urn:d', 'a')" },
                                                      { "zz", "QName('', 'zz')" },
                                                      { "p:zz", "QName('urn:p', 'zz')" } };

  private static final String [] [] ATTRIBUTE_NAMES = { { "x", "QName('', 'x')" },
                                                        { "y", "QName('', 'y')" },
                                                        { "p:x", "QName('urn:p', 'x')" },
                                                        { "zz", "QName('', 'zz')" } };

  /** The keys of the maps of {@link #testConstantMaps()}: strings and integers */
  private static final String [] MAP_KEYS = { "'a'", "'b'", "'c'", "1", "2" };

  /**
   * The values of the maps of {@link #testConstantMaps()}: function references (focus-dependent
   * ones too) and literals
   */
  private static final String [] MAP_VALUES = { "abs#1", "round#1", "string#1", "name#0", "position#0", "last#0", "1", "'v'" };

  /**
   * Pairs of an expression using the name index and a reference without name
   * tests; <code>{N}</code>/<code>{M}</code> are element name tests,
   * <code>{QN}</code>/<code>{QM}</code> their QNames, <code>{A}</code> an
   * attribute name test and <code>{QA}</code> its QName.
   */
  private static final String [] [] NAME_INDEX_PAIRS = { { "//{N}", "root(.)/descendant::*[node-name(.) eq {QN}]" },
                                                         { ".//{N}", "descendant::*[node-name(.) eq {QN}]" },
                                                         { "descendant::{N}", "descendant::*[node-name(.) eq {QN}]" },
                                                         { "{N}", "*[node-name(.) eq {QN}]" },
                                                         { "{N}[2]", "*[node-name(.) eq {QN}][2]" },
                                                         { "//{N}[1]",
                                                           "root(.)/descendant-or-self::node()/*[node-name(.) eq {QN}][1]" },
                                                         { "//{N}/{M}",
                                                           "root(.)/descendant::*[node-name(.) eq {QN}]/*[node-name(.) eq {QM}]" },
                                                         { "//{N}//{M}",
                                                           "root(.)/descendant::*[node-name(.) eq {QN}]/descendant::*[node-name(.) eq {QM}]" },
                                                         { "count(//{N})", "count(root(.)/descendant::*[node-name(.) eq {QN}])" },
                                                         { "//@{A}", "root(.)/descendant::*/@*[node-name(.) eq {QA}]" },
                                                         { ".//@{A}", "descendant-or-self::*/@*[node-name(.) eq {QA}]" },
                                                         { "@{A}", "@*[node-name(.) eq {QA}]" },
                                                         { "//{N}/@{A}",
                                                           "root(.)/descendant::*[node-name(.) eq {QN}]/@*[node-name(.) eq {QA}]" },
                                                         { "//{N} | //{M}",
                                                           "root(.)/descendant::*[node-name(.) eq {QN} or node-name(.) eq {QM}]" },
                                                         { "(//{N})[last()]", "(root(.)/descendant::*[node-name(.) eq {QN}])[last()]" },
                                                         { "exists(//{N}/{M})",
                                                           "count(root(.)/descendant::*[node-name(.) eq {QN}]/*[node-name(.) eq {QM}]) gt 0" } };

  /** Pairs of a step union and the equivalent expression without union */
  private static final String [] [] STEP_UNION_PAIRS = { { "@*|node()", "(@*, node())" },
                                                         { "node()|@*", "(@*, node())" },
                                                         { "*|comment()", "node()[self::* or self::comment()]" },
                                                         { "text()|*", "node()[self::text() or self::*]" },
                                                         { "@x|@y", "@*[node-name(.) = (QName('', 'x'), QName('', 'y'))]" },
                                                         { "a|@*", "(@*, *[node-name(.) eq QName('', 'a')])" },
                                                         { "p:a|b|processing-instruction()",
                                                           "node()[node-name(.) = (QName('urn:p', 'a'), QName('', 'b')) or self::processing-instruction()]" },
                                                         { "root(.)/descendant-or-self::node()/(@*|node())",
                                                           "(root(.)/descendant-or-self::node()/@*, root(.)/descendant-or-self::node()/node())/." },
                                                         { "count(//(@*|node()))", "count(//@*) + count(//node())" } };

  /** Sequences a predicate is applied to */
  private static final String [] FILTERED = { "//*",
                                              "//a",
                                              "child::node()",
                                              "//@*",
                                              "(1 to 7)",
                                              "('a', 'b', 'c')",
                                              "//text()",
                                              "descendant::*",
                                              "(//b, //a)" };

  /** Predicates that are focus-independent; only <code>$v</code> and <code>$w</code> vary */
  private static final String [] FOCUS_INDEPENDENT_PREDICATES = { "$v",
                                                                  "$v + 0",
                                                                  "abs($v)",
                                                                  "$v[1]",
                                                                  "floor($v)",
                                                                  "($v, 1)[1]",
                                                                  "string-length($w)",
                                                                  "count($w)",
                                                                  "exists($w)",
                                                                  "$v = 2",
                                                                  "$w",
                                                                  "not($v)",
                                                                  "round($v) - 1" };

  /** Values for <code>$v</code> and <code>$w</code> */
  private static final IGenerator <XdmSequence> VALUES = Generators.oneOf (Generators.ints (-1, 8)
                                                                                     .map (x -> XdmSequence.of (XdmAtomic.integer (x.longValue ()))),
                                                                           Generators.of (XdmSequence.EMPTY,
                                                                                          XdmSequence.of (XdmAtomic.decimal (new BigDecimal ("2.0"))),
                                                                                          XdmSequence.of (XdmAtomic.decimal (new BigDecimal ("1.5"))),
                                                                                          XdmSequence.of (XdmAtomic.dbl (2.0)),
                                                                                          XdmSequence.of (XdmAtomic.dbl (Double.NaN)),
                                                                                          XdmSequence.of (XdmAtomic.dbl (Double.POSITIVE_INFINITY)),
                                                                                          XdmSequence.of (XdmAtomic.flt (3.0f)),
                                                                                          XdmSequence.of (XdmAtomic.string ("")),
                                                                                          XdmSequence.of (XdmAtomic.string ("ab")),
                                                                                          XdmSequence.of (XdmAtomic.untyped ("2")),
                                                                                          XdmSequence.of (XdmAtomic.bool (true)),
                                                                                          XdmSequence.of (XdmAtomic.bool (false)),
                                                                                          XdmSequence.of (XdmAtomic.integer (1),
                                                                                                          XdmAtomic.integer (2)),
                                                                                          XdmSequence.of (XdmAtomic.string ("a"),
                                                                                                          XdmAtomic.integer (3))));

  /**
   * Name predicates as written (with <code>{V}</code> for the compared value)
   * and the same predicate with the name accessors replaced by expressions
   * over <code>node-name(.)</code> of the same type.
   */
  private static final String [] NAME_PREDICATES = { "name() = {V}",
                                                     "name() eq {V}",
                                                     "{V} = name()",
                                                     "local-name() = {V}",
                                                     "local-name() eq {V}",
                                                     "namespace-uri() = {V}",
                                                     "namespace-uri() eq {V}",
                                                     "local-name() = {V} and namespace-uri() = 'urn:p'",
                                                     "name() = {V} and local-name() = 'a'",
                                                     "name() != {V}" };

  private static final String [] NAME_VALUES = { "'a'",
                                                 "'p:a'",
                                                 "'x'",
                                                 "'pi'",
                                                 "'urn:p'",
                                                 "'urn:d'",
                                                 "''",
                                                 "xs:untypedAtomic('a')",
                                                 "xs:untypedAtomic('urn:p')",
                                                 "xs:anyURI('urn:p')",
                                                 "('a', 'b')",
                                                 "('urn:p', '')",
                                                 "()",
                                                 "1",
                                                 "$v",
                                                 "$w" };

  /** Values for <code>$v</code> and <code>$w</code> in name predicates */
  private static final IGenerator <XdmSequence> NAME_VARIABLE_VALUES = Generators.of (XdmSequence.EMPTY,
                                                                                      XdmSequence.of (XdmAtomic.string ("a")),
                                                                                      XdmSequence.of (XdmAtomic.string ("p:b")),
                                                                                      XdmSequence.of (XdmAtomic.untyped ("b")),
                                                                                      XdmSequence.of (XdmAtomic.anyUri ("urn:p")),
                                                                                      XdmSequence.of (XdmAtomic.string ("a"),
                                                                                                      XdmAtomic.string ("c")),
                                                                                      XdmSequence.of (XdmAtomic.integer (1)));

  private static final String [] NAME_CANDIDATES = { "//node()", "//*", "child::node()", "//@*", "(//*, //@*)" };

  /**
   * Expressions with built-in calls in loops that the run memo keeps
   * (<code>CallMemo</code>), each with a reference whose arguments are passed
   * through an inline function, which makes the calls ineligible.
   */
  private static final String [] [] LOOP_INVARIANT_PAIRS = { { "for $x in //* return count($x//a)",
                                                               "for $x in //* return count((function ($q) { $q })($x//a))" },
                                                             { "for $x in //* return string-join($x/@* ! string(.), ',')",
                                                               "for $x in //* return string-join((function ($q) { $q })($x/@* ! string(.)), ',')" },
                                                             { "for $x in (0, 1, 2, 3) return count(//*[count(*) ge $x])",
                                                               "for $x in (0, 1, 2, 3) return count((function ($q) { $q })(//*[count(*) ge $x]))" },
                                                             { "for $x in //* return for $y in $x/* return count($y/*) + count($x/*)",
                                                               "for $x in //* return for $y in $x/* return count((function ($q) { $q })($y/*)) + count((function ($q) { $q })($x/*))" },
                                                             { "some $x in //* satisfies count($x/*) eq 2",
                                                               "some $x in //* satisfies count((function ($q) { $q })($x/*)) eq 2" },
                                                             { "for $x in //* return (count($x/*), count($x/*))",
                                                               "for $x in //* return (count((function ($q) { $q })($x/*)), count((function ($q) { $q })($x/*)))" },
                                                             { "for $x in //* return for $x in ($x, $x/*) return count($x/*)",
                                                               "for $x in //* return for $x in ($x, $x/*) return count((function ($q) { $q })($x/*))" },
                                                             { "for $x in //* return let $x := $x/.. return count($x/*)",
                                                               "for $x in //* return let $x := $x/.. return count((function ($q) { $q })($x/*))" },
                                                             { "for $x in //*, $y in $x/* return count($y/..//*)",
                                                               "for $x in //*, $y in $x/* return count((function ($q) { $q })($y/..//*))" },
                                                             { "for $x in //* return count(.//a)",
                                                               "for $x in //* return count((function ($q) { $q })(.//a))" },
                                                             { "//*/count(.//b)", "//*/count((function ($q) { $q })(.//b))" },
                                                             { "for $x in //* return $x/string-length(string(.))",
                                                               "for $x in //* return $x/string-length((function ($q) { $q })(string(.)))" },
                                                             { "for $x in //*, $i in (1, 2) return count($x/node()[$i])",
                                                               "for $x in //*, $i in (1, 2) return count((function ($q) { $q })($x/node()[$i]))" },
                                                             { "every $x in //* satisfies every $y in $x/* satisfies count($y//*) lt count($x//*)",
                                                               "every $x in //* satisfies every $y in $x/* satisfies count((function ($q) { $q })($y//*)) lt count((function ($q) { $q })($x//*))" },
                                                             { "for $x in //* return count($x/*) + count($v)",
                                                               "for $x in //* return count((function ($q) { $q })($x/*)) + count((function ($q) { $q })($v))" } };

  private static final String [] RANDOM_LEAVES = { "1",
                                                   "2.5",
                                                   "'a'",
                                                   "''",
                                                   "true()",
                                                   "()",
                                                   "xs:double('NaN')",
                                                   "xs:untypedAtomic('1')",
                                                   ".",
                                                   "$v",
                                                   "map { 'a' : 1 }",
                                                   "[1, 2]" };
  private static final String [] RANDOM_FUNCTIONS = { "count",
                                                      "exists",
                                                      "empty",
                                                      "not",
                                                      "boolean",
                                                      "string",
                                                      "data",
                                                      "distinct-values",
                                                      "reverse",
                                                      "head",
                                                      "tail",
                                                      "sum",
                                                      "name",
                                                      "number",
                                                      "normalize-space",
                                                      "zero-or-one",
                                                      "exactly-one",
                                                      "root" };
  private static final String [] RANDOM_OPERATORS = { " = ",
                                                      " != ",
                                                      " < ",
                                                      " eq ",
                                                      " + ",
                                                      " - ",
                                                      " and ",
                                                      " or ",
                                                      " | ",
                                                      " except ",
                                                      ", ",
                                                      " ! ",
                                                      "/",
                                                      " is ",
                                                      " << " };
  private static final String [] RANDOM_PREDICATES = { "[1]", "[last()]", "[2]", "[. = 'a']", "[@x]", "[b]", "[position() lt 3]" };

  private static void _appendRandomExprRecursive (@NonNull final ChoiceSequence aChoices,
                                                  @NonNull final StringBuilder aSB,
                                                  final int nDepth)
  {
    // The depth limit keeps the expressions small
    final int nKind = nDepth >= 3 ? (int) aChoices.draw (1) : (int) aChoices.draw (7);
    switch (nKind)
    {
      case 0:
        aSB.append (PATHS[(int) aChoices.draw (PATHS.length - 1)]);
        break;
      case 1:
        aSB.append (RANDOM_LEAVES[(int) aChoices.draw (RANDOM_LEAVES.length - 1)]);
        break;
      case 2:
        aSB.append (RANDOM_FUNCTIONS[(int) aChoices.draw (RANDOM_FUNCTIONS.length - 1)]).append ('(');
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (')');
        break;
      case 3:
        // Both operands in parentheses, so that "/" is always followed by a step
        aSB.append ("((");
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (')').append (RANDOM_OPERATORS[(int) aChoices.draw (RANDOM_OPERATORS.length - 1)]).append ('(');
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append ("))");
        break;
      case 4:
        aSB.append ('(');
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (')').append (RANDOM_PREDICATES[(int) aChoices.draw (RANDOM_PREDICATES.length - 1)]);
        break;
      case 5:
        aSB.append ("(if (");
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (") then ");
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (" else ");
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (')');
        break;
      case 6:
        aSB.append ("(for $x in ");
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (" return ($x, ");
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append ("))");
        break;
      default:
        aSB.append ("(some $x in ");
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (" satisfies ");
        _appendRandomExprRecursive (aChoices, aSB, nDepth + 1);
        aSB.append (')');
        break;
    }
  }

  /** Random expressions over paths, literals, functions, operators, predicates and clauses */
  private static final IGenerator <String> RANDOM_EXPRESSIONS = aChoices -> {
    final StringBuilder aSB = new StringBuilder ();
    _appendRandomExprRecursive (aChoices, aSB, 0);
    return aSB.toString ();
  };

  private final XPathPropertyTestHelper m_aHelper = new XPathPropertyTestHelper ();

  /**
   * @return The document or, depending on the selector, one of its elements
   *         as context item.
   */
  @NonNull
  private IXdmItem _getContextItem (@NonNull final IXdmNode aDoc, final int nSelector)
  {
    final XdmSequence aElements = m_aHelper.eval ("//*", aDoc);
    if (nSelector == 0)
      return aDoc;
    return aElements.get ((nSelector - 1) % aElements.size ());
  }

  @NonNull
  private static String _fill (@NonNull final String sTemplate,
                               final String @NonNull [] aN,
                               final String @NonNull [] aM,
                               final String @NonNull [] aA)
  {
    return sTemplate.replace ("{QN}", aN[1])
                    .replace ("{QM}", aM[1])
                    .replace ("{QA}", aA[1])
                    .replace ("{N}", aN[0])
                    .replace ("{M}", aM[0])
                    .replace ("{A}", aA[0]);
  }

  /**
   * Existence and the effective boolean value of a path (early exit, also from
   * the name index) agree with a test of its full count: <code>exists</code>,
   * <code>empty</code>, <code>boolean</code>, <code>not</code>, conditions,
   * quantifiers, predicates and
   * {@link CompiledXPath#evaluateEffectiveBooleanValue}.
   */
  @Test
  public void testExistenceMatchesCount ()
  {
    new PropertyChecker ().setTries (150)
                          .forAll (XPathPropertyTestHelper.DOCUMENTS,
                                   Generators.of (PATHS),
                                   Generators.ints (0, 20),
                                   (sXml, sPath, aSelector) -> {
                                     final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
                                     final IXdmItem aCtx = _getContextItem (aDoc, aSelector.intValue ());
                                     final String sCount = "count(" + sPath + ")";
                                     m_aHelper.assertEquivalent ("exists(" + sPath + ")", sCount + " gt 0", aCtx);
                                     m_aHelper.assertEquivalent ("empty(" + sPath + ")", sCount + " eq 0", aCtx);
                                     m_aHelper.assertEquivalent ("boolean(" + sPath + ")", sCount + " gt 0", aCtx);
                                     m_aHelper.assertEquivalent ("not(" + sPath + ")", sCount + " eq 0", aCtx);
                                     m_aHelper.assertEquivalent ("if (" + sPath + ") then 1 else 0",
                                                                 "if (" + sCount + " gt 0) then 1 else 0",
                                                                 aCtx);
                                     m_aHelper.assertEquivalent ("some $n in " + sPath + " satisfies true()",
                                                                 sCount + " gt 0",
                                                                 aCtx);
                                     m_aHelper.assertEquivalent ("(" + sPath + ") and true()", sCount + " gt 0", aCtx);
                                     m_aHelper.assertEquivalent ("//*[" + sPath + "]", "//*[" + sCount + " gt 0]", aCtx);

                                     final CompiledXPath aXPath = m_aHelper.compile (sPath);
                                     final boolean bExpected = ((XdmAtomic) m_aHelper.eval (sCount + " gt 0", aCtx).get (0)).getBoolean ();
                                     assertEquals (sPath,
                                                   Boolean.valueOf (bExpected),
                                                   Boolean.valueOf (aXPath.evaluateEffectiveBooleanValue (aXPath.createDynamicContext ()
                                                                                                                .withFocus (aCtx,
                                                                                                                            1,
                                                                                                                            1))));
                                   });
  }

  /**
   * Named child, descendant and attribute steps (answered from the name
   * indexes of the document) select the same nodes in the same order as a
   * wildcard step filtered by <code>node-name</code>, also for names the
   * document does not contain.
   */
  @Test
  public void testNameIndexMatchesWildcard ()
  {
    new PropertyChecker ().setTries (150)
                          .forAll (XPathPropertyTestHelper.DOCUMENTS,
                                   Generators.of (NAME_INDEX_PAIRS),
                                   Generators.lists (Generators.ints (0, 7), 3, 3),
                                   Generators.ints (0, 20),
                                   (sXml, aPair, aNames, aSelector) -> {
                                     final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
                                     final IXdmItem aCtx = _getContextItem (aDoc, aSelector.intValue ());
                                     final String [] aN = ELEMENT_NAMES[aNames.get (0).intValue ()];
                                     final String [] aM = ELEMENT_NAMES[aNames.get (1).intValue ()];
                                     final String [] aA = ATTRIBUTE_NAMES[aNames.get (2).intValue () % ATTRIBUTE_NAMES.length];
                                     m_aHelper.assertEquivalent (_fill (aPair[0], aN, aM, aA),
                                                                 _fill (aPair[1], aN, aM, aA),
                                                                 aCtx);
                                   });
  }

  /**
   * A union of predicate-free attribute and child steps (evaluated in one pass
   * per axis) returns the nodes of both steps in document order without
   * duplicates.
   */
  @Test
  public void testStepUnionMatchesSequence ()
  {
    new PropertyChecker ().setTries (150)
                          .forAll (XPathPropertyTestHelper.DOCUMENTS,
                                   Generators.of (STEP_UNION_PAIRS),
                                   Generators.ints (0, 20),
                                   (sXml, aPair, aSelector) -> {
                                     final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
                                     final IXdmItem aCtx = _getContextItem (aDoc, aSelector.intValue ());
                                     m_aHelper.assertEquivalent (aPair[0], aPair[1], aCtx);
                                   });
  }

  /**
   * A focus-independent predicate (evaluated once per step) selects the same
   * items as the predicate evaluated per item with the rule of XPath 3.1
   * section 3.3.2: a single numeric value is compared with the position,
   * anything else gives its effective boolean value. Errors must be the same.
   */
  @Test
  public void testFocusIndependentPredicates ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (XPathPropertyTestHelper.DOCUMENTS,
                                   Generators.lists (Generators.ints (0, 100), 2, 2),
                                   VALUES,
                                   VALUES,
                                   (sXml, aChoice, aV, aW) -> {
                                     final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
                                     final String sSeq = FILTERED[aChoice.get (0).intValue () % FILTERED.length];
                                     final String sPred = FOCUS_INDEPENDENT_PREDICATES[aChoice.get (1).intValue () %
                                                                                       FOCUS_INDEPENDENT_PREDICATES.length];
                                     final String sReference = "(let $q := " +
                                                               sPred +
                                                               " return if ($q instance of xs:numeric) then position() eq $q else boolean($q))";
                                     m_aHelper.assertEquivalent (sSeq + "[" + sPred + "]",
                                                                 sSeq + "[" + sReference + "]",
                                                                 aDoc,
                                                                 aV,
                                                                 aW);
                                     m_aHelper.assertEquivalent ("count(" + sSeq + "[" + sPred + "][1])",
                                                                 "count(" + sSeq + "[" + sReference + "][position() eq 1])",
                                                                 aDoc,
                                                                 aV,
                                                                 aW);
                                     m_aHelper.assertEquivalent ("exists(" + sSeq + "[" + sPred + "])",
                                                                 "count(" + sSeq + "[" + sReference + "]) gt 0",
                                                                 aDoc,
                                                                 aV,
                                                                 aW);
                                   });
  }

  /**
   * A predicate comparing <code>name()</code>, <code>local-name()</code> or
   * <code>namespace-uri()</code> with a value (tested as strings) selects the
   * same nodes as the comparison with expressions of the same type over
   * <code>node-name(.)</code>, and raises the same errors.
   */
  @Test
  public void testNamePredicates ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (XPathPropertyTestHelper.DOCUMENTS,
                                   Generators.lists (Generators.ints (0, 100), 3, 3),
                                   NAME_VARIABLE_VALUES,
                                   NAME_VARIABLE_VALUES,
                                   (sXml, aChoice, aV, aW) -> {
                                     final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
                                     final String sCandidates = NAME_CANDIDATES[aChoice.get (0).intValue () % NAME_CANDIDATES.length];
                                     final String sPred = NAME_PREDICATES[aChoice.get (1).intValue () % NAME_PREDICATES.length].replace ("{V}",
                                                                                                                                         NAME_VALUES[aChoice.get (2)
                                                                                                                                                            .intValue () %
                                                                                                                                                     NAME_VALUES.length]);
                                     final String sReference = sPred.replace ("namespace-uri()",
                                                                              "(namespace-uri-from-QName(node-name(.)), xs:anyURI(''))[1]")
                                                                    .replace ("local-name()",
                                                                              "string(local-name-from-QName(node-name(.)))")
                                                                    .replace ("name()", "string(node-name(.))");
                                     m_aHelper.assertEquivalent (sCandidates + "[" + sPred + "]",
                                                                 sCandidates + "[" + sReference + "]",
                                                                 aDoc,
                                                                 aV,
                                                                 aW);
                                   });
  }

  /**
   * Built-in calls in loops that the run memo keeps return the same values as
   * the same calls made ineligible for the memo, also with shadowed variables,
   * nested loops and calls that read the focus.
   */
  @Test
  public void testLoopInvariantCalls ()
  {
    new PropertyChecker ().setTries (150)
                          .forAll (XPathPropertyTestHelper.DOCUMENTS,
                                   Generators.of (LOOP_INVARIANT_PAIRS),
                                   VALUES,
                                   (sXml, aPair, aV) -> {
                                     final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
                                     m_aHelper.assertEquivalent (aPair[0], aPair[1], aDoc, aV, XdmSequence.EMPTY);
                                   });
  }

  /**
   * A map of literal keys and literal or function reference values, which is
   * kept for the resolved functions, gives the same entries in every
   * evaluation as the same map with keys that are no literals (built each
   * time), also with focus-dependent functions, which capture the focus of
   * each evaluation, and with duplicate keys.
   */
  @Test
  public void testConstantMaps ()
  {
    // Each entry is a pair of indices into MAP_KEYS and MAP_VALUES
    final IGenerator <List <List <Integer>>> aEntries = Generators.lists (Generators.lists (Generators.ints (0, 39), 2, 2),
                                                                         0,
                                                                         5);
    final String sTemplate = "//* ! (let $m := {MAP} return string-join(" +
                             "for $k in sort(map:keys($m), (), function ($x) { string($x) || ($x instance of xs:integer) }) " +
                             "return let $v := $m($k) return string(if ($v instance of function(*)) " +
                             "then (if (function-arity($v) eq 0) then $v() else $v(-2.5)) else $v), ','))";
    new PropertyChecker ().setTries (300)
                          .forAll (XPathPropertyTestHelper.DOCUMENTS, aEntries, (sXml, aEntryList) -> {
                            final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
                            final StringBuilder aMap = new StringBuilder ("map { ");
                            final StringBuilder aRef = new StringBuilder ("map { ");
                            for (int i = 0; i < aEntryList.size (); ++i)
                            {
                              final String sKey = MAP_KEYS[aEntryList.get (i).get (0).intValue () % MAP_KEYS.length];
                              final String sValue = MAP_VALUES[aEntryList.get (i).get (1).intValue () % MAP_VALUES.length];
                              if (i > 0)
                              {
                                aMap.append (", ");
                                aRef.append (", ");
                              }
                              aMap.append (sKey).append (" : ").append (sValue);
                              // data() makes the key no literal, so the map is built each time
                              aRef.append ("data(").append (sKey).append (") : ").append (sValue);
                            }
                            aMap.append (" }");
                            aRef.append (" }");
                            m_aHelper.assertEquivalent (sTemplate.replace ("{MAP}", aMap),
                                                        sTemplate.replace ("{MAP}", aRef),
                                                        aDoc,
                                                        XdmSequence.EMPTY,
                                                        XdmSequence.EMPTY);
                          });
  }

  /**
   * A general comparison with a range expression operand (answered from the
   * bounds without building the range) agrees with the existential comparison
   * against the members of the range, for integer, decimal, double and float
   * values of any magnitude and all operators, in both operand orders.
   */
  @Test
  public void testRangeComparison ()
  {
    // Both bounds share a base, so that the reference only builds small ranges
    final IGenerator <String> aBases = Generators.of ("0", "9007199254740992", "170141183460469231731687303715884105728");
    final IGenerator <String> aRanges = Generators.oneOf (aBases.flatMap (sBase -> Generators.lists (Generators.ints (-5, 5), 2, 2)
                                                                                          .map (x -> "((" +
                                                                                                     sBase +
                                                                                                     " + " +
                                                                                                     x.get (0) +
                                                                                                     ") to (" +
                                                                                                     sBase +
                                                                                                     " + " +
                                                                                                     x.get (1) +
                                                                                                     "))")),
                                                          Generators.of ("(() to 3)", "(1 to ())", "(xs:untypedAtomic('1') to 3)"));
    final IGenerator <String> aValues = Generators.oneOf (Generators.ints (-6, 6).map (x -> x.toString ()),
                                                          Generators.ints (-6, 6).map (x -> x + ".5"),
                                                          Generators.ints (-6, 6).map (x -> x + ".0"),
                                                          Generators.ints (-6, 6).map (x -> "xs:double(" + x + ")"),
                                                          Generators.ints (-6, 6).map (x -> "xs:float(" + x + ".5)"),
                                                          Generators.ints (-6, 6).map (x -> "(9007199254740992 + " + x + ")"),
                                                          Generators.ints (-6, 6).map (x -> "xs:double(9007199254740992 + " + x + ")"),
                                                          Generators.ints (-6, 6).map (x -> "xs:float(16777216 + " + x + ")"),
                                                          Generators.ints (-6, 6)
                                                                    .map (x -> "(170141183460469231731687303715884105728 + " +
                                                                               x +
                                                                               ")"),
                                                          Generators.ints (-6, 6)
                                                                    .map (x -> "xs:double(170141183460469231731687303715884105728 + " +
                                                                               x +
                                                                               ")"),
                                                          Generators.of ("xs:double('NaN')",
                                                                         "xs:double('INF')",
                                                                         "xs:double('-INF')",
                                                                         "xs:double('-0')",
                                                                         "xs:untypedAtomic('2')",
                                                                         "xs:untypedAtomic('2.5')",
                                                                         "()",
                                                                         "(1, 100)",
                                                                         "(-100, 2.5)"));
    final IGenerator <String> aOperators = Generators.of ("=", "!=", "<", "<=", ">", ">=");
    new PropertyChecker ().setTries (2000)
                          .forAll (aRanges,
                                   aValues,
                                   aOperators,
                                   (sRange, sValue, sOp) -> {
                                     m_aHelper.assertEquivalent ("(" + sValue + ") " + sOp + " " + sRange,
                                                                 "some $a in (" +
                                                                                                           sValue +
                                                                                                           "), $b in " +
                                                                                                           sRange +
                                                                                                           " satisfies $a " +
                                                                                                           sOp +
                                                                                                           " $b",
                                                                 null,
                                                                 XdmSequence.EMPTY,
                                                                 XdmSequence.EMPTY);
                                     m_aHelper.assertEquivalent (sRange + " " + sOp + " (" + sValue + ")",
                                                                 "some $b in " + sRange + ", $a in (" + sValue + ") satisfies $b " + sOp + " $a",
                                                                 null,
                                                                 XdmSequence.EMPTY,
                                                                 XdmSequence.EMPTY);
                                   });
  }

  /**
   * @return The effective boolean value of a sequence (XPath 3.1 section
   *         2.4.3), or <code>null</code> where it is the error FORG0006.
   */
  private static Boolean _getEbv (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return Boolean.FALSE;
    final IXdmItem aFirst = aSeq.get (0);
    if (aFirst instanceof IXdmNode)
      return Boolean.TRUE;
    if (aSeq.size () > 1 || !(aFirst instanceof final XdmAtomic aAtomic))
      return null;
    final EAtomicType eType = aAtomic.getType ();
    if (eType == EAtomicType.BOOLEAN)
      return Boolean.valueOf (aAtomic.getBoolean ());
    if (eType.isNumeric ())
    {
      final double d = aAtomic.getAsDouble ();
      return Boolean.valueOf (d != 0 && !Double.isNaN (d));
    }
    if (eType.isDerivedFrom (EAtomicType.STRING) || eType == EAtomicType.UNTYPED_ATOMIC || eType == EAtomicType.ANY_URI)
      return Boolean.valueOf (!aAtomic.getString ().isEmpty ());
    return null;
  }

  @Nullable
  private static String _getErrorCode (@NonNull final Runnable aRunnable)
  {
    try
    {
      aRunnable.run ();
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  /**
   * For random expressions, an evaluation stopped early never contradicts the
   * complete one: if the complete evaluation succeeds, the effective boolean
   * value from {@link CompiledXPath#evaluateEffectiveBooleanValue} is the one
   * of the complete result (or FORG0006 exactly where that has none), and
   * <code>exists</code>, <code>empty</code> and <code>boolean</code> agree
   * with <code>count</code>. Only {@link XPathException}s may be raised.
   */
  @Test
  public void testRandomExpressionsEarlyExitConsistent ()
  {
    new PropertyChecker ().setTries (1500)
                          .forAll (XPathPropertyTestHelper.DOCUMENTS,
                                   RANDOM_EXPRESSIONS,
                                   Generators.ints (0, 20),
                                   VALUES,
                                   (sXml, sExpr, aSelector, aV) -> {
                                     final IXdmNode aDoc = XPathPropertyTestHelper.parse (sXml);
                                     final IXdmItem aCtx = _getContextItem (aDoc, aSelector.intValue ());
                                     final Wrapper <XdmSequence> aFull = new Wrapper <> ();
                                     final String sFullError = _getErrorCode ( () -> aFull.set (m_aHelper.eval (sExpr,
                                                                                                                aCtx,
                                                                                                                aV,
                                                                                                                XdmSequence.EMPTY)));
                                     final Wrapper <Boolean> aEbv = new Wrapper <> ();
                                     final String sEbvError = _getErrorCode ( () -> {
                                       final CompiledXPath aXPath = m_aHelper.compile (sExpr);
                                       final DynamicContext aDynCtx = aXPath.createDynamicContext ()
                                                                            .withVariable (XPathPropertyTestHelper.VAR_V, aV)
                                                                            .withVariable (XPathPropertyTestHelper.VAR_W, XdmSequence.EMPTY)
                                                                            .withFocus (aCtx, 1, 1);
                                       aEbv.set (Boolean.valueOf (aXPath.evaluateEffectiveBooleanValue (aDynCtx)));
                                     });
                                     if (sFullError != null)
                                       return;
                                     final Boolean aExpected = _getEbv (aFull.get ());
                                     if (aExpected == null)
                                       assertEquals (sExpr, "FORG0006", sEbvError);
                                     else
                                     {
                                       assertEquals (sExpr, null, sEbvError);
                                       assertEquals (sExpr, aExpected, aEbv.get ());
                                     }
                                     final String sCount = "count(" + sExpr + ")";
                                     m_aHelper.assertEquivalent ("exists(" + sExpr + ")", sCount + " gt 0", aCtx, aV, XdmSequence.EMPTY);
                                     m_aHelper.assertEquivalent ("empty(" + sExpr + ")", sCount + " eq 0", aCtx, aV, XdmSequence.EMPTY);
                                     if (aExpected != null)
                                       assertEquals (sExpr,
                                                     aExpected,
                                                     Boolean.valueOf (((XdmAtomic) m_aHelper.eval ("boolean(" + sExpr + ")",
                                                                                                   aCtx,
                                                                                                   aV,
                                                                                                   XdmSequence.EMPTY)
                                                                                         .get (0)).getBoolean ()));
                                   });
  }
}

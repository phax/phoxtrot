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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmDocumentNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.EAxis;
import com.helger.phoxtrot.xpath.ast.EBinaryOp;
import com.helger.phoxtrot.xpath.ast.EComparisonOp;
import com.helger.phoxtrot.xpath.ast.ENodeTestKind;
import com.helger.phoxtrot.xpath.ast.ExprWalker;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.collation.CollationRegistry;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.context.VariableFrame;
import com.helger.phoxtrot.xpath.context.VariableFrameLayout;
import com.helger.phoxtrot.xpath.context.VariableSlot;
import com.helger.phoxtrot.xpath.fn.FnAccessor;
import com.helger.phoxtrot.xpath.fn.FnBoolean;
import com.helger.phoxtrot.xpath.fn.FnContext;
import com.helger.phoxtrot.xpath.fn.FnSequence;
import com.helger.phoxtrot.xpath.fn.FunctionCallSite;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary.FunctionDef;
import com.helger.phoxtrot.xpath.fn.IXPathFunction;
import com.helger.phoxtrot.xpath.ops.AtomicCaster;
import com.helger.phoxtrot.xpath.ops.Atomizer;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.IXdmFunction;
import com.helger.phoxtrot.xpath.value.XdmArray;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmMap;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Evaluator for {@link IExpr} (a port of go-xslt's <code>eval.go</code> and <code>axes.go</code>):
 * an expression is compiled once into a tree of {@link AbstractCompiledExpr} nodes
 * ({@link #compile(IExpr)}), one node class per expression kind holding its compiled children, and
 * evaluated through them. Owner: evaluator workstream.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathEvaluator
{
  /**
   * The largest integer range (<code>a to b</code>) that is materialised; longer ranges are an
   * implementation limit (XPDY0130), as in go-xslt.
   */
  public static final long MAX_RANGE_SIZE = 10_000_000L;

  private static final XdmSequence TRUE_SEQ = XdmSequence.of (XdmAtomic.TRUE);
  private static final XdmSequence FALSE_SEQ = XdmSequence.of (XdmAtomic.FALSE);
  // Results of _evalFocusIndependentPredicate besides a 1-based position
  private static final int SELECT_ALL = 0;
  private static final int SELECT_NONE = -1;
  /** The name accessors of a name predicate (see _getNameComparisons) */
  private static final Set <String> NAME_ACCESSORS = Set.of ("name", "local-name", "namespace-uri");
  /** The maximum nesting of <code>and</code> in a name predicate */
  private static final int MAX_NAME_COMPARISONS = 8;
  private static final AbstractCompiledExpr [] NO_EXPRS = new AbstractCompiledExpr [0];

  @PresentForCodeCoverage
  private static final XPathEvaluator INSTANCE = new XPathEvaluator ();

  private XPathEvaluator ()
  {}

  @NonNull
  private static XdmSequence _bool (final boolean b)
  {
    return b ? TRUE_SEQ : FALSE_SEQ;
  }

  private static boolean _ebv (@NonNull final XdmSequence aSeq)
  {
    return Atomizer.getEffectiveBooleanValue (aSeq);
  }

  /**
   * @return The context item of the focus, never <code>null</code>.
   * @throws XPathException
   *         XPDY0002 if the context item is absent
   */
  @NonNull
  private static IXdmItem _getContextItem (@NonNull final DynamicContext aCtx)
  {
    final IXdmItem ret = aCtx.getContextItem ();
    if (ret == null)
      throw XPathException.noContext ("The context item is absent");
    return ret;
  }

  /**
   * @return The string value of an atomic value as <code>fn:string</code> gives it: the string
   *         itself for string-like values, the canonical lexical form otherwise.
   */
  @NonNull
  private static String _getStringValue (@NonNull final XdmAtomic aValue)
  {
    return aValue.isStringLike () ? aValue.getString () : aValue.getLexical ();
  }

  /**
   * @return <code>true</code> if the numeric value equals the 1-based position exactly.
   */
  private static boolean _isPosition (@NonNull final XdmAtomic aValue, final int nPosition)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBigInt)
      return aBigInt.bitLength () < 32 && aBigInt.intValue () == nPosition;
    if (aPayload instanceof final BigDecimal aBigDec)
      return aBigDec.compareTo (BigDecimal.valueOf (nPosition)) == 0;
    return aValue.getAsDouble () == nPosition;
  }

  /**
   * @return The position a numeric predicate value selects (the one position
   *         {@link #_isPosition(XdmAtomic, int)} accepts), or a value below 1 if it selects none:
   *         -1 for a fractional value, NaN, or a value outside the int range (or, for xs:double
   *         and xs:float, below 1); an xs:integer or xs:decimal value of 0 or below within the int
   *         range is returned as it is.
   */
  private static int _getSelectedPosition (@NonNull final XdmAtomic aValue)
  {
    final Object aPayload = aValue.getValue ();
    if (aPayload instanceof final BigInteger aBigInt)
      return aBigInt.bitLength () < 32 ? aBigInt.intValue () : -1;
    if (aPayload instanceof final BigDecimal aBigDec)
    {
      final BigDecimal aStripped = aBigDec.stripTrailingZeros ();
      if (aStripped.scale () > 0 ||
          aStripped.compareTo (BigDecimal.valueOf (Integer.MAX_VALUE)) > 0 ||
          aStripped.compareTo (BigDecimal.valueOf (Integer.MIN_VALUE)) < 0)
        return -1;
      return aStripped.intValue ();
    }
    final double d = aValue.getAsDouble ();
    if (d != Math.rint (d) || d < 1 || d > Integer.MAX_VALUE)
      return -1;
    return (int) d;
  }

  /**
   * Evaluate a predicate for one item: a single number is compared with the context position,
   * anything else gives its effective boolean value. A predicate that can only be a node
   * sequence is evaluated with early exit ({@link AbstractCompiledExpr#tryEarlyEbv(DynamicContext, boolean)}).
   */
  private static boolean _isPredicateTrue (@NonNull final AbstractCompiledExpr aPredicate,
                                           @NonNull final IXdmItem aItem,
                                           final int nPosition,
                                           final int nSize,
                                           @NonNull final DynamicContext aCtx)
  {
    final DynamicContext aFocus = aCtx.withFocus (aItem, nPosition, nSize);
    // Optimisation (early exit): a path predicate stops at its first node
    final Boolean aEarly = aPredicate.tryEarlyEbv (aFocus, true);
    if (aEarly != null)
      return aEarly.booleanValue ();
    final XdmSequence aValue = aPredicate.evaluate (aFocus);
    if (aValue.isSingleton () && aValue.get (0) instanceof final XdmAtomic aAtomic && aAtomic.isNumeric ())
      return _isPosition (aAtomic, nPosition);
    return _ebv (aValue);
  }

  /**
   * @return The implementation a static call resolves to in the function library of the static
   *         context, or <code>null</code> if it does not resolve.
   */
  @Nullable
  private static IXPathFunction _getImplementation (final IExpr.@NonNull FunctionCallExpr aCall,
                                                    @NonNull final DynamicContext aCtx)
  {
    final FunctionDef aDef = aCall.site ()
                                  .getFunction (aCtx.getStaticContext ().getFunctionLibrary (),
                                                aCall.name (),
                                                aCall.args ().size ());
    return aDef == null ? null : aDef.getImpl ();
  }

  /**
   * @return <code>true</code> if a static call resolves, in the function library of the static
   *         context, to the implementation of the shared default library, so that a host function
   *         registered under a built-in name is never taken for the built-in one.
   */
  private static boolean _isDefaultImplementation (final IExpr.@NonNull FunctionCallExpr aCall,
                                                   @NonNull final DynamicContext aCtx)
  {
    final IXPathFunction aImpl = _getImplementation (aCall, aCtx);
    if (aImpl == null)
      return false;
    final FunctionDef aDefault = FunctionLibrary.getDefault ().getFunction (aCall.name (), aCall.args ().size ());
    return aDefault != null && aDefault.getImpl () == aImpl;
  }

  /**
   * Is a predicate's value the same for every candidate, so that one evaluation decides for all
   * of them? See {@link ExprWalker#isFocusIndependent(IExpr, java.util.function.Predicate)};
   * every static function call in it must also resolve, in the function library of the static
   * context, to the implementation of the shared default library, so that a host function
   * registered under a built-in name is never assumed to be focus-free. A literal is left to the
   * literal shortcut of the callers.
   */
  private static boolean _isFocusIndependentPredicate (@NonNull final AbstractCompiledExpr aPredicate,
                                                       @NonNull final DynamicContext aCtx)
  {
    final IExpr aExpr = aPredicate.getExpression ();
    if (aExpr instanceof IExpr.LiteralExpr || aExpr instanceof IExpr.PathExpr || aExpr instanceof IExpr.ContextItemExpr)
      return false;
    return ExprWalker.isFocusIndependent (aExpr, aCall -> _isDefaultImplementation (aCall, aCtx));
  }

  /**
   * One comparison of a name predicate (see
   * {@link #_getNameComparisons(AbstractCompiledExpr, DynamicContext)}): a name accessor of the candidate
   * compared for equality with an expression that has the same value for every candidate. The
   * strings of that value are computed on first use, so within one predicate application.
   */
  @NotThreadSafe
  private static final class NameComparison
  {
    private final AbstractCompiledExpr m_aComparison;
    // Local name of fn:name, fn:local-name or fn:namespace-uri
    private final String m_sAccessor;
    private final AbstractCompiledExpr m_aValue;
    private final boolean m_bValueComparison;
    private boolean m_bEvaluated;
    // Null if the value needs the generic comparison
    private String [] m_aStrings;

    NameComparison (@NonNull final ComparisonNode aComparison,
                    @NonNull final String sAccessor,
                    @NonNull final AbstractCompiledExpr aValue)
    {
      m_aComparison = aComparison;
      m_sAccessor = sAccessor;
      m_aValue = aValue;
      m_bValueComparison = aComparison.m_eOp == EComparisonOp.VALUE_EQ;
    }
  }

  /**
   * @return The local name of the accessor if the expression is a static call of
   *         <code>fn:name()</code>, <code>fn:local-name()</code> or <code>fn:namespace-uri()</code>
   *         without argument (the context node) that resolves to the built-in implementation,
   *         otherwise <code>null</code>.
   */
  @Nullable
  private static String _getNameAccessor (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    if (!(aExpr instanceof final IExpr.FunctionCallExpr aCall) ||
        !aCall.args ().isEmpty () ||
        !XdmConstants.NS_FN.equals (aCall.name ().namespaceUri ()))
      return null;
    final String sLocalName = aCall.name ().localName ();
    if (!NAME_ACCESSORS.contains (sLocalName) || !_isDefaultImplementation (aCall, aCtx))
      return null;
    return sLocalName;
  }

  /**
   * Has an expression the same value for every candidate of a predicate? True for
   * <code>current()</code> (the XSLT implementation {@link FnContext#XSLT_CURRENT}, whose item
   * the focus of the predicate does not change), for a built-in name accessor applied to it
   * (<code>name(current())</code>) and for whatever {@link ExprWalker#isFocusIndependent} accepts
   * with built-in functions only (<code>'x'</code>, <code>$name</code>).
   */
  private static boolean _isCandidateInvariant (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    IExpr aInner = aExpr;
    if (aExpr instanceof final IExpr.FunctionCallExpr aCall &&
        aCall.args ().size () == 1 &&
        XdmConstants.NS_FN.equals (aCall.name ().namespaceUri ()) &&
        NAME_ACCESSORS.contains (aCall.name ().localName ()) &&
        _isDefaultImplementation (aCall, aCtx))
      aInner = aCall.args ().get (0);
    if (aInner instanceof final IExpr.FunctionCallExpr aCall &&
        aCall.args ().isEmpty () &&
        _getImplementation (aCall, aCtx) == FnContext.XSLT_CURRENT)
      return true;
    return ExprWalker.isFocusIndependent (aExpr, aCall -> _isDefaultImplementation (aCall, aCtx));
  }

  /**
   * Collect the comparisons of a name predicate, an <code>and</code> of comparisons (depth-limited).
   *
   * @return <code>false</code> if the expression has another shape.
   */
  private static boolean _collectNameComparisons (@NonNull final AbstractCompiledExpr aExpr,
                                                  @NonNull final DynamicContext aCtx,
                                                  final int nDepth,
                                                  @NonNull final ICommonsList <NameComparison> aTarget)
  {
    if (aExpr instanceof final BinaryNode aAnd && aAnd.m_eOp == EBinaryOp.AND)
      return nDepth < MAX_NAME_COMPARISONS &&
             _collectNameComparisons (aAnd.m_aLeft, aCtx, nDepth + 1, aTarget) &&
             _collectNameComparisons (aAnd.m_aRight, aCtx, nDepth + 1, aTarget);
    if (!(aExpr instanceof final ComparisonNode aComp) ||
        (aComp.m_eOp != EComparisonOp.GENERAL_EQ && aComp.m_eOp != EComparisonOp.VALUE_EQ))
      return false;
    String sAccessor = _getNameAccessor (aComp.m_aLeft.getExpression (), aCtx);
    AbstractCompiledExpr aValue = aComp.m_aRight;
    if (sAccessor == null)
    {
      sAccessor = _getNameAccessor (aComp.m_aRight.getExpression (), aCtx);
      aValue = aComp.m_aLeft;
    }
    if (sAccessor == null || !_isCandidateInvariant (aValue.getExpression (), aCtx))
      return false;
    aTarget.add (new NameComparison (aComp, sAccessor, aValue));
    return true;
  }

  /**
   * Recognise a name predicate: <code>name() = name(current())</code>,
   * <code>local-name() = local-name(current()) and namespace-uri() = namespace-uri(current())</code>,
   * <code>local-name() eq 'x'</code> and the like, i.e. an <code>and</code> of <code>=</code> or
   * <code>eq</code> comparisons, each of a name accessor of the context node with an expression of
   * the same value for every candidate (see {@link #_isCandidateInvariant(IExpr, DynamicContext)}),
   * under the codepoint collation. Such a predicate is tested per candidate by
   * {@link #_isNamePredicateTrue(ICommonsList, IXdmItem, int, int, DynamicContext)}.
   *
   * @return The comparisons in the order of the expression, or <code>null</code> for any other
   *         predicate.
   */
  @Nullable
  private static ICommonsList <NameComparison> _getNameComparisons (@NonNull final AbstractCompiledExpr aPredicate,
                                                                   @NonNull final DynamicContext aCtx)
  {
    if (!(aPredicate instanceof ComparisonNode) && !(aPredicate instanceof BinaryNode))
      return null;
    if (XPathOperators.getDefaultCollation (aCtx) != null)
      return null;
    final ICommonsList <NameComparison> ret = new CommonsArrayList <> ();
    return _collectNameComparisons (aPredicate, aCtx, 0, ret) ? ret : null;
  }

  /**
   * @return The strings an equality with the value matches, or <code>null</code> if the
   *         comparison must be evaluated generically: an atom that is not string-like, an
   *         untypedAtomic compared with <code>namespace-uri()</code> (cast to xs:anyURI) or more
   *         than one atom for <code>eq</code> (an error).
   */
  @Nullable
  private static String [] _getComparedStrings (@NonNull final NameComparison aComp, @NonNull final XdmSequence aValue)
  {
    final XdmSequence aAtoms = XPathOperators.atomize (aValue);
    if (aComp.m_bValueComparison && aAtoms.size () > 1)
      return null;
    final String [] ret = new String [aAtoms.size ()];
    for (int i = 0; i < ret.length; ++i)
    {
      if (!(aAtoms.get (i) instanceof final XdmAtomic aAtomic) || !aAtomic.isStringLike ())
        return null;
      if (aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC && aComp.m_sAccessor.equals ("namespace-uri"))
        return null;
      ret[i] = aAtomic.getString ();
    }
    return ret;
  }

  /**
   * Test a name predicate (see {@link #_getNameComparisons(AbstractCompiledExpr, DynamicContext)}) for one
   * candidate, as the generic evaluation would: the comparisons left to right, each stopping the
   * test when false. The compared value of a comparison is evaluated once, when the first
   * candidate reaches it; the name of the candidate is then compared directly. A candidate that is
   * no node and a value that is no string go through the generic evaluation of the comparison.
   */
  private static boolean _isNamePredicateTrue (@NonNull final ICommonsList <NameComparison> aComparisons,
                                               @NonNull final IXdmItem aItem,
                                               final int nPosition,
                                               final int nSize,
                                               @NonNull final DynamicContext aCtx)
  {
    for (final NameComparison aComp : aComparisons)
    {
      if (!aComp.m_bEvaluated)
      {
        aComp.m_aStrings = _getComparedStrings (aComp, aComp.m_aValue.evaluate (aCtx.withFocus (aItem, nPosition, nSize)));
        aComp.m_bEvaluated = true;
      }
      final boolean bTrue;
      if (aComp.m_aStrings == null || !(aItem instanceof final IXdmNode aNode))
        bTrue = aComp.m_aComparison.getEbv (aCtx.withFocus (aItem, nPosition, nSize));
      else
      {
        final String sName = switch (aComp.m_sAccessor)
        {
          case "name" -> FnAccessor.getName (aNode);
          case "local-name" -> FnAccessor.getLocalName (aNode);
          default -> FnAccessor.getNamespaceUri (aNode);
        };
        boolean bFound = false;
        for (final String sValue : aComp.m_aStrings)
          if (sValue.equals (sName))
          {
            bFound = true;
            break;
          }
        bTrue = bFound;
      }
      if (!bTrue)
        return false;
    }
    return true;
  }

  /**
   * Evaluate a focus-independent predicate (see
   * {@link #_isFocusIndependentPredicate(AbstractCompiledExpr, DynamicContext)}) once, with the focus of
   * the given candidate, exactly as
   * {@link #_isPredicateTrue(AbstractCompiledExpr, IXdmItem, int, int, DynamicContext)} evaluates it, and
   * say which candidates it keeps: a single number selects the one position it equals, anything
   * else all candidates or none by its effective boolean value.
   *
   * @return The 1-based position the predicate selects, {@link #SELECT_ALL} or
   *         {@link #SELECT_NONE}.
   */
  private static int _evalFocusIndependentPredicate (@NonNull final AbstractCompiledExpr aPredicate,
                                                     @NonNull final IXdmItem aItem,
                                                     final int nPosition,
                                                     final int nSize,
                                                     @NonNull final DynamicContext aCtx)
  {
    final DynamicContext aFocus = aCtx.withFocus (aItem, nPosition, nSize);
    // Optimisation (early exit): a path predicate stops at its first node
    final Boolean aEarly = aPredicate.tryEarlyEbv (aFocus, true);
    if (aEarly != null)
      return aEarly.booleanValue () ? SELECT_ALL : SELECT_NONE;
    final XdmSequence aValue = aPredicate.evaluate (aFocus);
    if (aValue.isSingleton () && aValue.get (0) instanceof final XdmAtomic aAtomic && aAtomic.isNumeric ())
    {
      final int nPos = _getSelectedPosition (aAtomic);
      return nPos >= 1 ? nPos : SELECT_NONE;
    }
    return _ebv (aValue) ? SELECT_ALL : SELECT_NONE;
  }

  /**
   * @return The numeric literal value of a predicate, or <code>null</code> if the predicate is no
   *         numeric literal.
   */
  @Nullable
  private static XdmAtomic _getNumericLiteral (@NonNull final AbstractCompiledExpr aPredicate)
  {
    if (aPredicate.getExpression () instanceof final IExpr.LiteralExpr aLiteral && aLiteral.value ().isNumeric ())
      return aLiteral.value ();
    return null;
  }

  /**
   * Find the first item of a sequence (in its current order) for which a predicate is true, with
   * the context positions and size of the whole sequence.
   *
   * @param nFrom
   *        The 0-based index to start the search at.
   * @return The 0-based index of the item, or -1 if no item from <code>nFrom</code> on passes.
   */
  private static int _findPassing (@NonNull final XdmSequence aSeq,
                                   @NonNull final AbstractCompiledExpr aPredicate,
                                   final int nFrom,
                                   @NonNull final DynamicContext aCtx)
  {
    final int nSize = aSeq.size ();
    final XdmAtomic aLiteral = _getNumericLiteral (aPredicate);
    if (aLiteral != null)
    {
      final int nPos = _getSelectedPosition (aLiteral);
      return nPos > nFrom && nPos <= nSize ? nPos - 1 : -1;
    }
    if (nSize - nFrom >= 2 && _isFocusIndependentPredicate (aPredicate, aCtx))
    {
      aCtx.checkInterrupt ();
      final int nSelected = _evalFocusIndependentPredicate (aPredicate, aSeq.get (nFrom), nFrom + 1, nSize, aCtx);
      if (nSelected == SELECT_ALL)
        return nFrom;
      return nSelected > nFrom && nSelected <= nSize ? nSelected - 1 : -1;
    }
    final ICommonsList <NameComparison> aNameComparisons = _getNameComparisons (aPredicate, aCtx);
    if (aNameComparisons != null)
    {
      // Optimisation: a name predicate evaluates its compared values once
      for (int i = nFrom; i < nSize; ++i)
      {
        aCtx.checkInterrupt ();
        if (_isNamePredicateTrue (aNameComparisons, aSeq.get (i), i + 1, nSize, aCtx))
          return i;
      }
      return -1;
    }
    for (int i = nFrom; i < nSize; ++i)
    {
      aCtx.checkInterrupt ();
      if (_isPredicateTrue (aPredicate, aSeq.get (i), i + 1, nSize, aCtx))
        return i;
    }
    return -1;
  }

  /**
   * Apply one predicate to a sequence in its current order (axis order for an axis step).
   */
  @NonNull
  private static XdmSequence _applyPredicate (@NonNull final XdmSequence aSeq,
                                              @NonNull final AbstractCompiledExpr aPredicate,
                                              @NonNull final DynamicContext aCtx)
  {
    final int nSize = aSeq.size ();
    if (nSize == 0)
      return aSeq;
    final XdmAtomic aLiteral = _getNumericLiteral (aPredicate);
    if (aLiteral != null)
    {
      // A numeric literal selects one position without evaluating per item
      final int nPos = _getSelectedPosition (aLiteral);
      return nPos >= 1 && nPos <= nSize ? XdmSequence.of (aSeq.get (nPos - 1)) : XdmSequence.EMPTY;
    }
    if (nSize >= 2 && _isFocusIndependentPredicate (aPredicate, aCtx))
    {
      // One evaluation decides for all candidates
      aCtx.checkInterrupt ();
      final int nSelected = _evalFocusIndependentPredicate (aPredicate, aSeq.get (0), 1, nSize, aCtx);
      if (nSelected == SELECT_ALL)
        return aSeq;
      return nSelected >= 1 && nSelected <= nSize ? XdmSequence.of (aSeq.get (nSelected - 1)) : XdmSequence.EMPTY;
    }
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    final ICommonsList <NameComparison> aNameComparisons = _getNameComparisons (aPredicate, aCtx);
    if (aNameComparisons != null)
    {
      // Optimisation: a name predicate evaluates its compared values once
      for (int i = 0; i < nSize; ++i)
      {
        aCtx.checkInterrupt ();
        final IXdmItem aItem = aSeq.get (i);
        if (_isNamePredicateTrue (aNameComparisons, aItem, i + 1, nSize, aCtx))
          aBuilder.add (aItem);
      }
      return aBuilder.build ();
    }
    for (int i = 0; i < nSize; ++i)
    {
      aCtx.checkInterrupt ();
      final IXdmItem aItem = aSeq.get (i);
      if (_isPredicateTrue (aPredicate, aItem, i + 1, nSize, aCtx))
        aBuilder.add (aItem);
    }
    return aBuilder.build ();
  }

  /**
   * Apply the predicates one after the other (XPath 3.1 sections 3.2.1 and 3.3.3), each to the
   * complete result of the one before, so that the positions of each predicate count within that
   * result.
   *
   * @return The items that pass all predicates in their current order; the input itself without
   *         predicates.
   */
  @NonNull
  private static XdmSequence _applyPredicates (@NonNull final XdmSequence aSeq,
                                               @NonNull final AbstractCompiledExpr [] aPredicates,
                                               @NonNull final DynamicContext aCtx)
  {
    XdmSequence ret = aSeq;
    for (final AbstractCompiledExpr aPredicate : aPredicates)
      ret = _applyPredicate (ret, aPredicate, aCtx);
    return ret;
  }

  /**
   * Apply all predicates but the last one completely (the positions of a predicate depend on the
   * full result of the one before).
   */
  @NonNull
  private static XdmSequence _applyPredicatesButLast (@NonNull final XdmSequence aSeq,
                                                      @NonNull final AbstractCompiledExpr [] aPredicates,
                                                      @NonNull final DynamicContext aCtx)
  {
    XdmSequence ret = aSeq;
    for (int i = 0; i < aPredicates.length - 1; ++i)
      ret = _applyPredicate (ret, aPredicates[i], aCtx);
    return ret;
  }

  /**
   * Early exit: would the predicates keep at least one item? The last predicate stops at the first
   * item that passes.
   */
  private static boolean _hasPassingItem (@NonNull final XdmSequence aSeq,
                                          @NonNull final AbstractCompiledExpr [] aPredicates,
                                          @NonNull final DynamicContext aCtx)
  {
    if (aPredicates.length == 0)
      return !aSeq.isEmpty ();
    final XdmSequence aCandidates = _applyPredicatesButLast (aSeq, aPredicates, aCtx);
    return _findPassing (aCandidates, aPredicates[aPredicates.length - 1], 0, aCtx) >= 0;
  }

  /**
   * @return The element name of a descendant or descendant-or-self step with an
   *         exact name test, which the name index of a document can answer;
   *         <code>null</code> for any other step.
   */
  @Nullable
  private static XdmQName _getExactName (@Nullable final NodeTest aTest)
  {
    if (aTest == null ||
        aTest.kind () != ENodeTestKind.NAME ||
        aTest.anyName () ||
        aTest.anyNamespace () ||
        aTest.anyLocalName ())
      return null;
    return aTest.name ();
  }

  /**
   * @return The exact element name of a <code>descendant</code> or
   *         <code>descendant-or-self</code> step, which the name index of a document can answer;
   *         <code>null</code> for any other axis or a test that is no exact name test.
   */
  @Nullable
  private static XdmQName _getIndexableName (final IExpr.@NonNull StepExpr aStep)
  {
    final EAxis eAxis = aStep.axis ();
    if (eAxis != EAxis.DESCENDANT && eAxis != EAxis.DESCENDANT_OR_SELF)
      return null;
    return _getExactName (aStep.test ());
  }

  /**
   * One step of a path, compiled: the step with its compiled predicates and postfix expression,
   * the compiled fused step of a <code>//</code>, and what the step's shape decides once.
   */
  @Immutable
  private static final class CompiledStep
  {
    private final IExpr.StepExpr m_aStep;
    private final AbstractCompiledExpr [] m_aPredicates;
    // Null for an axis step
    private final AbstractCompiledExpr m_aPostfix;
    // Null if there is none
    private final CompiledStep m_aFused;
    // The exact name of a descendant(-or-self) step, null otherwise
    private final XdmQName m_aIndexName;
    // The exact name of a child step, null otherwise
    private final XdmQName m_aChildName;
    // All predicates select the same nodes whatever the context position is
    private final boolean m_bFocusFreePredicates;

    CompiledStep (@NonNull final ExprCompiler aC, final IExpr.@NonNull StepExpr aStep)
    {
      m_aStep = aStep;
      m_aPredicates = aC.compileAll (aStep.predicates ());
      m_aPostfix = aStep.postfix () == null ? null : aC.compile (aStep.postfix ());
      m_aFused = aStep.fused () == null ? null : new CompiledStep (aC, aStep.fused ());
      m_aIndexName = _getIndexableName (aStep);
      m_aChildName = aStep.axis () == EAxis.CHILD ? _getExactName (aStep.test ()) : null;
      boolean bFocusFree = true;
      for (final IExpr aPredicate : aStep.predicates ())
        if (!ExprWalker.isFocusFreeBoolean (aPredicate))
        {
          bFocusFree = false;
          break;
        }
      m_bFocusFreePredicates = bFocusFree;
    }
  }

  /**
   * @return The sequence of a list of a document's name index, created once per run and list (by
   *         identity, see {@link AxisNavigator#getNamedIndexList(EAxis, IXdmNode, XdmQName)}).
   */
  @NonNull
  private static XdmSequence _getIndexListSequence (@NonNull final List <IXdmNode> aIndexList, @NonNull final DynamicContext aCtx)
  {
    if (aIndexList.isEmpty ())
      return XdmSequence.EMPTY;
    final Map <Object, Object> aMemo = aCtx.getRunMemo ();
    if (aMemo.get (aIndexList) instanceof final XdmSequence aKnown)
      return aKnown;
    final XdmSequence ret = XdmSequence.of (aIndexList);
    aMemo.put (aIndexList, ret);
    return ret;
  }

  /**
   * The nodes of the axis of an axis step from one context node that pass its node test.
   *
   * @return The nodes in axis order (reverse document order for a reverse axis).
   */
  @NonNull
  private static XdmSequence _collectAxisCandidates (@NonNull final CompiledStep aStep,
                                                     @NonNull final IXdmNode aNode,
                                                     @NonNull final DynamicContext aCtx)
  {
    final EAxis eAxis = aStep.m_aStep.axis ();
    final NodeTest aTest = aStep.m_aStep.test ();
    final XdmQName aIndexName = aStep.m_aIndexName;
    final XdmQName aChildName = aStep.m_aChildName;
    if (aChildName != null || aIndexName != null)
    {
      // Optimisation: a list of the name index that is the whole result becomes a sequence once
      // per run instead of being copied on every step (e.g. //name from the document node)
      final List <IXdmNode> aIndexList = AxisNavigator.getNamedIndexList (eAxis, aNode, aChildName != null ? aChildName : aIndexName);
      if (aIndexList != null)
        return _getIndexListSequence (aIndexList, aCtx);
    }
    // Filled in place, without the copy of XdmSequence.of (List)
    final XdmSequence.ListBuilder <IXdmNode> aNodes = new XdmSequence.ListBuilder <> ();
    // Optimisation for the common exact name steps: child elements by name, descendants from the
    // document's name index; any other step walks the axis
    if (aChildName != null)
      AxisNavigator.collectNamedChildren (aNode, aChildName, aNodes);
    else
      if (aIndexName == null || !AxisNavigator.collectNamedDescendants (eAxis, List.of (aNode), aIndexName, aNodes))
      {
        final ENodeKind ePrincipal = AxisNavigator.getPrincipalNodeKind (eAxis);
        AxisNavigator.collect (eAxis,
                               aNode,
                               aTest == null ? null : x -> NodeTestMatcher.matches (aTest, x, ePrincipal),
                               aNodes);
      }
    return aNodes.build ();
  }

  /**
   * One axis step from one context node.
   *
   * @return The selected nodes in document order.
   */
  @NonNull
  private static XdmSequence _evalAxisStep (@NonNull final CompiledStep aStep,
                                            @NonNull final IXdmNode aNode,
                                            @NonNull final DynamicContext aCtx)
  {
    XdmSequence ret = _applyPredicates (_collectAxisCandidates (aStep, aNode, aCtx), aStep.m_aPredicates, aCtx);
    if (aStep.m_aStep.axis ().isReverse () && ret.size () > 1)
    {
      // Positions were counted in reverse document order; the step result is
      // in document order
      final ICommonsList <IXdmItem> aList = ret.getAsList ();
      Collections.reverse (aList);
      ret = XdmSequence.of (aList);
    }
    return ret;
  }

  /**
   * @param sCode
   *        The error code if the item is not a node: XPTY0019 for the result of a previous step,
   *        XPTY0020 for the context item of the first step.
   * @return The item as node.
   * @throws XPathException
   *         with <code>sCode</code> if the item is not a node
   */
  @NonNull
  private static IXdmNode _getStepContextNode (@NonNull final IXdmItem aItem, @NonNull final String sCode)
  {
    if (aItem instanceof final IXdmNode aNode)
      return aNode;
    throw XPathException.of (sCode, "The context item of a path step is not a node");
  }

  /**
   * @return <code>true</code> if all items are nodes (the result needs a document order sort).
   * @throws XPathException
   *         XPTY0018 for a mix of nodes and other items in the last step.
   */
  private static boolean _isAllNodes (@NonNull final XdmSequence aSeq, final boolean bLastStep)
  {
    int nNodes = 0;
    for (final IXdmItem aItem : aSeq)
      if (aItem instanceof IXdmNode)
        nNodes++;
    if (nNodes > 0 && nNodes < aSeq.size () && bLastStep)
      throw XPathException.of ("XPTY0018",
                               "The result of the last step of a path expression mixes nodes and non-node items");
    return nNodes == aSeq.size ();
  }

  /**
   * The candidates of a descendant step with an exact name test from many
   * context nodes at once, through the name index of their document. Only for
   * predicates that select the same nodes whatever the context position is,
   * since the positions count among all selected nodes and not per context
   * node.
   *
   * @return The candidates in document order, before the predicates, or
   *         <code>null</code> if the index cannot be used.
   */
  @Nullable
  private static XdmSequence _getIndexedCandidates (@NonNull final CompiledStep aStep,
                                                    @NonNull final XdmSequence aCurrent,
                                                    @NonNull final DynamicContext aCtx)
  {
    final XdmQName aIndexName = aStep.m_aIndexName;
    if (aIndexName == null || !aStep.m_bFocusFreePredicates)
      return null;
    final List <IXdmNode> aContextNodes = new ArrayList <> (aCurrent.size ());
    for (final IXdmItem aItem : aCurrent)
    {
      if (!(aItem instanceof final IXdmNode aNode))
        return null;
      aContextNodes.add (aNode);
    }
    aCtx.checkInterrupt ();
    final XdmSequence.ListBuilder <IXdmNode> aNodes = new XdmSequence.ListBuilder <> ();
    if (!AxisNavigator.collectNamedDescendants (aStep.m_aStep.axis (), aContextNodes, aIndexName, aNodes))
      return null;
    return aNodes.build ();
  }

  /**
   * @return <code>true</code> for the step <code>descendant-or-self::node()</code>
   *         without predicates, as <code>//</code> abbreviates it.
   */
  private static boolean _isDescendantOrSelfNode (final IExpr.@NonNull StepExpr aStep)
  {
    return aStep.postfix () == null &&
           aStep.axis () == EAxis.DESCENDANT_OR_SELF &&
           aStep.test () != null &&
           aStep.test ().kind () == ENodeTestKind.ANY_NODE &&
           aStep.predicates ().isEmpty ();
  }

  /**
   * The steps <code>descendant-or-self::node()/attribute::N</code> (as in
   * <code>//@N</code>) with an exact name N from many context nodes at once,
   * through the attribute index of their document. Only for predicates that
   * select the same nodes whatever the context position is, as for
   * {@link #_getIndexedCandidates(CompiledStep, XdmSequence, DynamicContext)}.
   *
   * @return The selected attributes in document order, or <code>null</code> if
   *         the index cannot be used.
   */
  @Nullable
  private static XdmSequence _applySubtreeAttributeStep (@NonNull final CompiledStep aAttributeStep,
                                                         @NonNull final XdmSequence aCurrent,
                                                         @NonNull final DynamicContext aCtx)
  {
    final IExpr.StepExpr aStep = aAttributeStep.m_aStep;
    if (aStep.postfix () != null || aStep.axis () != EAxis.ATTRIBUTE)
      return null;
    final XdmQName aName = _getExactName (aStep.test ());
    if (aName == null || !aAttributeStep.m_bFocusFreePredicates)
      return null;
    final List <IXdmNode> aContextNodes = new ArrayList <> (aCurrent.size ());
    for (final IXdmItem aItem : aCurrent)
    {
      if (!(aItem instanceof final IXdmNode aNode))
        return null;
      aContextNodes.add (aNode);
    }
    aCtx.checkInterrupt ();
    final XdmSequence.ListBuilder <IXdmNode> aNodes = new XdmSequence.ListBuilder <> ();
    if (!AxisNavigator.collectNamedSubtreeAttributes (aContextNodes, aName, aNodes))
      return null;
    return _applyPredicates (aNodes.build (), aAttributeStep.m_aPredicates, aCtx);
  }

  /** Apply a step (not the first of a relative path) to every item of the previous step. */
  @NonNull
  private static XdmSequence _applyStep (@NonNull final CompiledStep aStep,
                                         @NonNull final XdmSequence aCurrent,
                                         @NonNull final DynamicContext aCtx,
                                         final boolean bLastStep)
  {
    final int nSize = aCurrent.size ();
    if (nSize == 0)
      return aCurrent;
    if (aStep.m_aPostfix == null)
    {
      if (nSize == 1)
        return _evalAxisStep (aStep, _getStepContextNode (aCurrent.get (0), "XPTY0019"), aCtx);
      final XdmSequence aIndexed = _getIndexedCandidates (aStep, aCurrent, aCtx);
      if (aIndexed != null)
        return _applyPredicates (aIndexed, aStep.m_aPredicates, aCtx);
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final IXdmItem aItem : aCurrent)
      {
        aCtx.checkInterrupt ();
        aBuilder.addAll (_evalAxisStep (aStep, _getStepContextNode (aItem, "XPTY0019"), aCtx));
      }
      return NodeSorter.sortAndDeduplicate (aBuilder.build ());
    }

    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    for (int i = 0; i < nSize; ++i)
    {
      aCtx.checkInterrupt ();
      final IXdmItem aItem = aCurrent.get (i);
      _getStepContextNode (aItem, "XPTY0019");
      final DynamicContext aSub = aCtx.withFocus (aItem, i + 1, nSize);
      aBuilder.addAll (_applyPredicates (aStep.m_aPostfix.evaluate (aSub), aStep.m_aPredicates, aSub));
    }
    final XdmSequence ret = aBuilder.build ();
    if (_isAllNodes (ret, bLastStep))
      return NodeSorter.sortAndDeduplicate (ret);
    return ret;
  }

  /**
   * @param nEnd
   *        The number of steps to look at, from the first one on.
   * @return <code>true</code> if all these steps are axis steps without predicates. From a node
   *         such a step raises no error and selects nodes of the same tree only.
   */
  private static boolean _areErrorFreeSteps (@NonNull final List <IExpr.StepExpr> aSteps, final int nEnd)
  {
    for (int i = 0; i < nEnd; ++i)
    {
      final IExpr.StepExpr aStep = aSteps.get (i);
      if (aStep.postfix () != null || !aStep.predicates ().isEmpty ())
        return false;
    }
    return true;
  }

  /**
   * Is the expression of a shape that raises no error if the context item is a node whose tree
   * has a document node as root, and that then selects only nodes of that tree? These are paths
   * (relative or absolute) of axis steps without predicates, possibly starting with such an
   * expression, unions of them, and one of them in parentheses. The recursion follows the
   * expression nesting, not the input.
   */
  private static boolean _isErrorFreeNodeExpr (@NonNull final IExpr aExpr)
  {
    return switch (aExpr)
    {
      case final IExpr.PathExpr e -> (e.start () == null || _isErrorFreeNodeExpr (e.start ())) &&
                                     _areErrorFreeSteps (e.steps (), e.steps ().size ());
      case final IExpr.SetExpr e -> e.op () == IExpr.SetExpr.ESetOp.UNION &&
                                    _isErrorFreeNodeExpr (e.left ()) &&
                                    _isErrorFreeNodeExpr (e.right ());
      case final IExpr.SequenceExpr e -> e.items ().size () == 1 && _isErrorFreeNodeExpr (e.items ().get (0));
      default -> false;
    };
  }

  /**
   * The part of {@link #_isEmptyByNameIndex(IExpr.PathExpr, IExpr.StepExpr, int, DynamicContext)}
   * that depends only on the expression: the element name whose absence from the document makes
   * the path empty.
   *
   * @param aLast
   *        The last step (the fused step for <code>//name</code>).
   * @param nPrefix
   *        The number of steps before the last one.
   * @return The element name, or <code>null</code> if the path does not qualify.
   */
  @Nullable
  private static XdmQName _getEmptyByIndexName (final IExpr.@NonNull PathExpr aExpr,
                                                final IExpr.@NonNull StepExpr aLast,
                                                final int nPrefix)
  {
    if (aLast.postfix () != null || (aExpr.start () == null && nPrefix == 0))
      return null;
    final EAxis eAxis = aLast.axis ();
    if (eAxis != EAxis.CHILD && eAxis != EAxis.DESCENDANT && eAxis != EAxis.DESCENDANT_OR_SELF)
      return null;
    final XdmQName aName = _getExactName (aLast.test ());
    if (aName == null)
      return null;
    if ((aExpr.start () != null && !_isErrorFreeNodeExpr (aExpr.start ())) || !_areErrorFreeSteps (aExpr.steps (), nPrefix))
      return null;
    return aName;
  }

  /**
   * The run-time part of the name index check: is the element name absent from the document of
   * the context item?
   *
   * @param aName
   *        The name from {@link #_getEmptyByIndexName(IExpr.PathExpr, IExpr.StepExpr, int)}, may
   *        be <code>null</code>.
   * @param bBuildIndex
   *        <code>true</code> to build the element index of the document if it is not there (for a
   *        last descendant step, whose evaluation builds it anyway), <code>false</code> to build it
   *        only once the check was asked often enough to pay for it (for a last child step, which
   *        does not need it: in a single pass over the document building it would cost more than
   *        the shortcut saves), see {@link XdmDocumentNode#getElementsByNameWhenRepeated(XdmQName)}.
   */
  private static boolean _isNameAbsent (@Nullable final XdmQName aName,
                                        final boolean bBuildIndex,
                                        @NonNull final DynamicContext aCtx)
  {
    if (aName == null)
      return false;
    if (!(aCtx.getContextItem () instanceof final IXdmNode aNode) || !(aNode.getRoot () instanceof final XdmDocumentNode aDoc))
      return false;
    // null if the tree is not numbered, the document is streamed or (without bBuildIndex) the
    // index is not built yet
    final List <IXdmNode> aElements = bBuildIndex ? aDoc.getElementsByName (aName)
                                                  : aDoc.getElementsByNameWhenRepeated (aName);
    return aElements != null && aElements.isEmpty ();
  }

  /**
   * Can a path be answered as empty from the element index of the document, without evaluating
   * the steps before its last one? That is the case if the last step is a child, descendant or
   * descendant-or-self step with an exact element name that does not occur in the document. To
   * skip no error, only prefixes that cannot raise one qualify: a start expression and the steps
   * before the last as in {@link #_isErrorFreeNodeExpr(IExpr)}, from a context item that is a node
   * whose root is a document node (anything else falls back to the evaluation, which raises
   * XPDY0002, XPTY0020 or XPDY0050 as before). Paths without anything before the last step are
   * left to the evaluation, which is as cheap. A tree that is not numbered or a streamed document
   * has no index and is never answered here.
   *
   * @param aLast
   *        The last step (the fused step for <code>//name</code>).
   * @param nPrefix
   *        The number of steps before the last one.
   * @return <code>true</code> if the path is known to be empty.
   */
  private static boolean _isEmptyByNameIndex (final IExpr.@NonNull PathExpr aExpr,
                                               final IExpr.@NonNull StepExpr aLast,
                                               final int nPrefix,
                                               @NonNull final DynamicContext aCtx)
  {
    return _isNameAbsent (_getEmptyByIndexName (aExpr, aLast, nPrefix), aLast.axis () != EAxis.CHILD, aCtx);
  }

  /**
   * The check of {@link #_isEmptyByNameIndex(IExpr.PathExpr, IExpr.StepExpr, int, DynamicContext)}
   * for a whole path, as the evaluation of the path applies it: the last step is the fused step
   * for <code>//name</code> if there is one.
   *
   * @param aExpr
   *        The path.
   * @param aCtx
   *        The dynamic context with the context item the path is evaluated from.
   * @return <code>true</code> if the path is known to be empty without evaluating it,
   *         <code>false</code> if that cannot be decided (the path may still be empty).
   */
  static boolean isEmptyByNameIndex (final IExpr.@NonNull PathExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    final List <IExpr.StepExpr> aSteps = aExpr.steps ();
    final int nSteps = aSteps.size ();
    if (nSteps == 0)
      return false;
    final boolean bFused = nSteps >= 2 && aSteps.get (nSteps - 2).fused () != null;
    final IExpr.StepExpr aLast = bFused ? aSteps.get (nSteps - 2).fused () : aSteps.get (nSteps - 1);
    return _isEmptyByNameIndex (aExpr, aLast, bFused ? nSteps - 2 : nSteps - 1, aCtx);
  }

  /**
   * Early exit: does an axis step select at least one node from the context items? Every context
   * item is checked to be a node first, as in the full evaluation; then the context nodes are
   * tried one by one until one of them selects a node. Document order and duplicates do not
   * matter for the answer.
   *
   * @param sCode
   *        The error code for a context item that is not a node.
   */
  private static boolean _existsInStep (@NonNull final CompiledStep aStep,
                                        @NonNull final XdmSequence aCurrent,
                                        @NonNull final DynamicContext aCtx,
                                        @NonNull final String sCode)
  {
    final int nSize = aCurrent.size ();
    if (nSize == 0)
      return false;
    for (final IXdmItem aItem : aCurrent)
      _getStepContextNode (aItem, sCode);
    if (nSize > 1)
    {
      final XdmSequence aIndexed = _getIndexedCandidates (aStep, aCurrent, aCtx);
      if (aIndexed != null)
        return _hasPassingItem (aIndexed, aStep.m_aPredicates, aCtx);
    }
    for (final IXdmItem aItem : aCurrent)
    {
      aCtx.checkInterrupt ();
      if (_hasPassingItem (_collectAxisCandidates (aStep, (IXdmNode) aItem, aCtx), aStep.m_aPredicates, aCtx))
        return true;
    }
    return false;
  }

  /**
   * The steps before the last one of a path whose existence can be answered depth-first (see
   * <code>PathNode._existsDepthFirst</code>): a path without start expression, without a filter
   * expression as step and without an unfused <code>descendant-or-self::node()</code> (which keeps
   * the shortcut through the attribute index), whose steps after the first one take the child,
   * attribute or self axis. Those axes give distinct nodes for distinct context nodes, so the walk
   * visits every node of the full evaluation once; the first step has one context node.
   *
   * @param aSteps
   *        The compiled steps of the path.
   * @param nPrefix
   *        The number of steps before the last one (a fused pair counted as two).
   * @return The steps to walk, a fused pair replaced by its fused step, or <code>null</code> if the
   *         path does not qualify.
   */
  @Nullable
  private static CompiledStep [] _getDepthFirstSteps (@NonNull final CompiledStep [] aSteps, final int nPrefix)
  {
    if (nPrefix == 0)
      return null;
    final List <CompiledStep> ret = new ArrayList <> (nPrefix);
    for (int i = 0; i < nPrefix; ++i)
    {
      CompiledStep aStep = aSteps[i];
      if (aStep.m_aPostfix != null)
        return null;
      if (aStep.m_aFused != null)
      {
        aStep = aStep.m_aFused;
        i++;
      }
      else
        if (_isDescendantOrSelfNode (aStep.m_aStep))
          return null;
      if (!ret.isEmpty ())
      {
        final EAxis eAxis = aStep.m_aStep.axis ();
        if (eAxis != EAxis.CHILD && eAxis != EAxis.ATTRIBUTE && eAxis != EAxis.SELF)
          return null;
      }
      ret.add (aStep);
    }
    return ret.toArray (new CompiledStep [0]);
  }

  /**
   * A path expression (XPath 3.1 section 3.3) with its compiled start expression and steps, and
   * what its shape decides once: the last step (the fused step for <code>//name</code>), the
   * element name whose absence from the document makes the path empty, and whether it is a
   * <code>//@name</code> path.
   */
  @Immutable
  private static final class PathNode extends AbstractCompiledExpr
  {
    private final IExpr.PathExpr m_aPath;
    // Null for a pure location path
    private final AbstractCompiledExpr m_aStart;
    private final CompiledStep [] m_aSteps;
    // Null for a path without steps
    private final CompiledStep m_aLast;
    // The number of steps before the last one
    private final int m_nPrefix;
    // Null if the name index cannot answer the path
    private final XdmQName m_aEmptyByIndexName;
    // True if the check may build the element index (a last descendant step, which uses it anyway)
    private final boolean m_bEmptyByIndexBuild;
    // "//@name": the full evaluation answers it from the attribute index
    private final boolean m_bSubtreeAttributes;
    // The steps before the last one if the existence is answered depth-first, null otherwise
    private final CompiledStep [] m_aDepthFirstSteps;

    PathNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull PathExpr aPath)
    {
      super (aPath);
      m_aPath = aPath;
      m_aStart = aPath.start () == null ? null : aC.compile (aPath.start ());
      final List <IExpr.StepExpr> aSteps = aPath.steps ();
      final int nSteps = aSteps.size ();
      m_aSteps = new CompiledStep [nSteps];
      for (int i = 0; i < nSteps; ++i)
        m_aSteps[i] = new CompiledStep (aC, aSteps.get (i));
      if (nSteps == 0)
      {
        m_aLast = null;
        m_nPrefix = 0;
        m_aEmptyByIndexName = null;
        m_bEmptyByIndexBuild = false;
        m_bSubtreeAttributes = false;
        m_aDepthFirstSteps = null;
      }
      else
      {
        // The fused "//name" step replaces the last two steps
        final boolean bFused = nSteps >= 2 && m_aSteps[nSteps - 2].m_aFused != null;
        m_aLast = bFused ? m_aSteps[nSteps - 2].m_aFused : m_aSteps[nSteps - 1];
        m_nPrefix = bFused ? nSteps - 2 : nSteps - 1;
        m_aEmptyByIndexName = _getEmptyByIndexName (aPath, m_aLast.m_aStep, m_nPrefix);
        m_bEmptyByIndexBuild = m_aLast.m_aStep.axis () != EAxis.CHILD;
        m_bSubtreeAttributes = m_nPrefix == nSteps - 1 &&
                               nSteps >= 2 &&
                               _isDescendantOrSelfNode (aSteps.get (nSteps - 2)) &&
                               m_aLast.m_aStep.axis () == EAxis.ATTRIBUTE &&
                               _getExactName (m_aLast.m_aStep.test ()) != null;
        m_aDepthFirstSteps = m_aStart == null && m_aLast.m_aPostfix == null ? _getDepthFirstSteps (m_aSteps, m_nPrefix) : null;
      }
    }

    /**
     * Evaluate the path up to a step.
     *
     * @param nEnd
     *        The index of the first step not evaluated; the number of steps for the whole path. A
     *        fused pair of steps (<code>//name</code>) must not be split.
     * @return The result of the steps before <code>nEnd</code>; for a relative path without a
     *         start expression and <code>nEnd</code> 0 the context item.
     */
    @NonNull
    private XdmSequence _evalPrefix (@NonNull final DynamicContext aCtx, final int nEnd)
    {
      final int nSteps = m_aSteps.length;
      XdmSequence aCurrent;
      int nFirst = 0;
      if (m_aStart != null)
        aCurrent = m_aStart.evaluate (aCtx);
      else
        if (m_aPath.absolute ())
        {
          final IXdmItem aItem = _getContextItem (aCtx);
          if (!(aItem instanceof final IXdmNode aNode))
            throw XPathException.of ("XPTY0020", "The context item for '/' is not a node");
          final IXdmNode aRoot = aNode.getRoot ();
          if (!aRoot.isDocument ())
            throw XPathException.of ("XPDY0050",
                                     "The root of the tree containing the context item is not a document node");
          aCurrent = XdmSequence.of (aRoot);
        }
        else
        {
          if (nSteps == 0 || nEnd == 0)
            return XdmSequence.of (_getContextItem (aCtx));
          final CompiledStep aFirst = m_aSteps[0];
          if (aFirst.m_aPostfix != null)
          {
            // A filter expression as first step keeps its own order
            aCurrent = _applyPredicates (aFirst.m_aPostfix.evaluate (aCtx), aFirst.m_aPredicates, aCtx);
          }
          else
          {
            final IXdmItem aItem = _getContextItem (aCtx);
            if (!(aItem instanceof final IXdmNode aNode))
              throw XPathException.of ("XPTY0020", "The context item for an axis step is not a node");
            aCurrent = _evalAxisStep (aFirst, aNode, aCtx);
          }
          nFirst = 1;
        }
      for (int i = nFirst; i < nEnd; ++i)
      {
        CompiledStep aStep = m_aSteps[i];
        // Only within the prefix asked for: the attribute step may be the one
        // the caller evaluates itself (_existsInPath)
        if (i + 1 < nEnd && aStep.m_aFused == null && _isDescendantOrSelfNode (aStep.m_aStep))
        {
          // Optimisation for "//@name": the attributes from the index in place of all nodes of
          // the subtrees and the attribute step after them
          final XdmSequence aAttributes = _applySubtreeAttributeStep (m_aSteps[i + 1], aCurrent, aCtx);
          if (aAttributes != null)
          {
            aCurrent = aAttributes;
            i++;
            continue;
          }
        }
        if (aStep.m_aFused != null)
        {
          // Optimisation for "//name": one descendant step in place of descendant-or-self::node()
          // and the child step after it
          aStep = aStep.m_aFused;
          i++;
        }
        aCurrent = _applyStep (aStep, aCurrent, aCtx, i == nSteps - 1);
      }
      return aCurrent;
    }

    /**
     * Early exit for a path whose last step is an axis step: its value is a node sequence, so its
     * effective boolean value and its existence are the same question. The steps before the last
     * one are walked depth-first for a path of {@link #m_aDepthFirstSteps}, and evaluated
     * completely otherwise, unless the element index of the document shows that the last step
     * selects nothing.
     *
     * @return <code>null</code> if the last step is not an axis step (the caller then evaluates
     *         the path completely).
     */
    /**
     * The start node of a path without start expression: the root of the context node for an
     * absolute path, the context node otherwise, with the errors of {@link #_evalPrefix(DynamicContext, int)}.
     */
    @NonNull
    private IXdmNode _getStartNode (@NonNull final DynamicContext aCtx)
    {
      final IXdmItem aItem = _getContextItem (aCtx);
      if (m_aPath.absolute ())
      {
        if (!(aItem instanceof final IXdmNode aNode))
          throw XPathException.of ("XPTY0020", "The context item for '/' is not a node");
        final IXdmNode aRoot = aNode.getRoot ();
        if (!aRoot.isDocument ())
          throw XPathException.of ("XPDY0050", "The root of the tree containing the context item is not a document node");
        return aRoot;
      }
      if (!(aItem instanceof final IXdmNode aNode))
        throw XPathException.of ("XPTY0020", "The context item for an axis step is not a node");
      return aNode;
    }

    /**
     * @return <code>true</code> if the depth-first walk tests the predicates of the step on each
     *         node when it visits it instead of filtering the whole step result first: predicates
     *         that are boolean and select the same nodes whatever the context position is.
     */
    private static boolean _isFilteredLazily (@NonNull final CompiledStep aStep)
    {
      return aStep.m_aPredicates.length > 0 && aStep.m_bFocusFreePredicates;
    }

    /**
     * @return <code>true</code> if the node passes all the predicates, which are boolean and read
     *         neither the context position nor the size (see {@link #_isFilteredLazily(CompiledStep)}).
     */
    private static boolean _passesFocusFreePredicates (@NonNull final AbstractCompiledExpr [] aPredicates,
                                                       @NonNull final IXdmNode aNode,
                                                       final int nPosition,
                                                       final int nSize,
                                                       @NonNull final DynamicContext aCtx)
    {
      for (final AbstractCompiledExpr aPredicate : aPredicates)
        if (!_isPredicateTrue (aPredicate, aNode, nPosition, nSize, aCtx))
          return false;
      return true;
    }

    /**
     * @return The nodes of one level of the depth-first walk: the step from the node, its
     *         predicates applied, or, for a step filtered lazily, its candidates in axis order
     *         (the order does not matter for the existence).
     */
    @NonNull
    private static XdmSequence _getDepthFirstLevel (@NonNull final CompiledStep aStep,
                                                    @NonNull final IXdmNode aNode,
                                                    @NonNull final DynamicContext aCtx)
    {
      // Optimisation: a boolean predicate such as [@cat = 'C'] is tested only on the candidates
      // the walk reaches before it finds a node
      if (_isFilteredLazily (aStep))
        return _collectAxisCandidates (aStep, aNode, aCtx);
      return _evalAxisStep (aStep, aNode, aCtx);
    }

    /**
     * Early exit for a path of {@link #m_aDepthFirstSteps}: the steps are walked depth-first from
     * the start node, each step from one context node at a time (with its predicates, positions
     * counted per context node as in the full evaluation), and the last step is tested for every
     * node of the step before it until one selects a node. The intermediate results are neither
     * sorted nor deduplicated, which the steps allowed here do not need, and boolean predicates
     * that do not read the context position are tested on a node when it is visited. An error that a later
     * context node would raise is not raised once a node is found (XPath 3.1 section 2.3.4). The
     * walk is iterative, with one level per step of the expression.
     *
     * @return <code>true</code> if the path selects at least one node.
     */
    private boolean _existsDepthFirst (@NonNull final DynamicContext aCtx)
    {
      final CompiledStep [] aSteps = m_aDepthFirstSteps;
      final int nLevels = aSteps.length;
      final XdmSequence [] aLevelNodes = new XdmSequence [nLevels];
      final int [] aLevelPos = new int [nLevels];
      aLevelNodes[0] = _getDepthFirstLevel (aSteps[0], _getStartNode (aCtx), aCtx);
      int nLevel = 0;
      while (nLevel >= 0)
      {
        final XdmSequence aNodes = aLevelNodes[nLevel];
        final int nPos = aLevelPos[nLevel];
        if (nPos >= aNodes.size ())
        {
          // This level is done: continue with the next node of the level above
          nLevel--;
          continue;
        }
        aLevelPos[nLevel] = nPos + 1;
        final IXdmNode aNode = (IXdmNode) aNodes.get (nPos);
        aCtx.checkInterrupt ();
        if (_isFilteredLazily (aSteps[nLevel]) &&
            !_passesFocusFreePredicates (aSteps[nLevel].m_aPredicates, aNode, nPos + 1, aNodes.size (), aCtx))
          continue;
        if (nLevel == nLevels - 1)
        {
          if (_hasPassingItem (_collectAxisCandidates (m_aLast, aNode, aCtx), m_aLast.m_aPredicates, aCtx))
            return true;
        }
        else
        {
          nLevel++;
          aLevelNodes[nLevel] = _getDepthFirstLevel (aSteps[nLevel], aNode, aCtx);
          aLevelPos[nLevel] = 0;
        }
      }
      return false;
    }

    @Nullable
    private Boolean _existsInPath (@NonNull final DynamicContext aCtx)
    {
      if (m_aLast == null || m_aLast.m_aPostfix != null)
        return null;
      // Optimisation: empty by the document's name index, without evaluating the prefix
      if (_isNameAbsent (m_aEmptyByIndexName, m_bEmptyByIndexBuild, aCtx))
        return Boolean.FALSE;
      // "//@name": the full evaluation answers it from the attribute index,
      // which is cheaper than visiting every node of the subtrees here
      if (m_bSubtreeAttributes)
        return null;
      // Optimisation: a path of child steps (as /a/b/c/@d in a per-line assertion) stops at the
      // first node of the last step instead of evaluating all steps before it completely
      if (m_aDepthFirstSteps != null)
        return Boolean.valueOf (_existsDepthFirst (aCtx));
      final XdmSequence aCurrent = _evalPrefix (aCtx, m_nPrefix);
      final boolean bFromContextItem = m_aStart == null && !m_aPath.absolute () && m_nPrefix == 0;
      return Boolean.valueOf (_existsInStep (m_aLast, aCurrent, aCtx, bFromContextItem ? "XPTY0020" : "XPTY0019"));
    }

    /**
     * Evaluate the whole path with all its steps. A path that selects elements by a name the
     * document does not contain, after a prefix that cannot raise an error, is empty without
     * evaluating the prefix.
     *
     * @return The result of the last step: nodes in document order without duplicates, or, for a
     *         last step that is a postfix expression yielding no nodes, its items in their order.
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      // Optimisation: a last element name the document does not contain makes the path empty
      // without evaluating its prefix
      if (m_aLast != null && _isNameAbsent (m_aEmptyByIndexName, m_bEmptyByIndexBuild, aCtx))
        return XdmSequence.EMPTY;
      return _evalPrefix (aCtx, m_aSteps.length);
    }

    @Override
    @Nullable
    Boolean tryEarlyEbv (@NonNull final DynamicContext aCtx, final boolean bPredicate)
    {
      return _existsInPath (aCtx);
    }

    @Override
    boolean exists (@NonNull final DynamicContext aCtx)
    {
      final Boolean aEarly = _existsInPath (aCtx);
      if (aEarly != null)
        return aEarly.booleanValue ();
      return !evaluate (aCtx).isEmpty ();
    }
  }

  /** A primary expression followed by predicates (XPath 3.1 section 3.2.1). */
  @Immutable
  private static final class FilterNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr m_aPrimary;
    private final AbstractCompiledExpr [] m_aPredicates;

    FilterNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull FilterExpr aExpr)
    {
      super (aExpr);
      m_aPrimary = aC.compile (aExpr.primary ());
      m_aPredicates = aC.compileAll (aExpr.predicates ());
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return _applyPredicates (m_aPrimary.evaluate (aCtx), m_aPredicates, aCtx);
    }

    /**
     * Early exit for the effective boolean value: the last predicate stops at the first item that
     * passes if that is a node; after an atomic value or a function a second passing item is
     * looked for, which makes the value undefined (FORG0006). Not for a predicate, whose value
     * could be a number.
     */
    @Override
    @Nullable
    Boolean tryEarlyEbv (@NonNull final DynamicContext aCtx, final boolean bPredicate)
    {
      if (bPredicate)
        return null;
      final XdmSequence aPrimary = m_aPrimary.evaluate (aCtx);
      if (m_aPredicates.length == 0)
        return Boolean.valueOf (_ebv (aPrimary));
      final XdmSequence aCandidates = _applyPredicatesButLast (aPrimary, m_aPredicates, aCtx);
      final AbstractCompiledExpr aLast = m_aPredicates[m_aPredicates.length - 1];
      final int nFirst = _findPassing (aCandidates, aLast, 0, aCtx);
      if (nFirst < 0)
        return Boolean.FALSE;
      final IXdmItem aFirst = aCandidates.get (nFirst);
      if (aFirst instanceof IXdmNode)
        return Boolean.TRUE;
      final int nSecond = _findPassing (aCandidates, aLast, nFirst + 1, aCtx);
      if (nSecond < 0)
        return Boolean.valueOf (_ebv (XdmSequence.of (aFirst)));
      return Boolean.valueOf (_ebv (XdmSequence.of (aFirst, aCandidates.get (nSecond))));
    }

    @Override
    boolean exists (@NonNull final DynamicContext aCtx)
    {
      return _hasPassingItem (m_aPrimary.evaluate (aCtx), m_aPredicates, aCtx);
    }
  }

  /**
   * The comma operator and parenthesised expressions (XPath 3.1 sections 3.1.3 and 3.4.1): the
   * values of the items are concatenated in order; a single item is evaluated directly, also with
   * early exit.
   */
  @Immutable
  private static final class SequenceNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr [] m_aItems;

    SequenceNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull SequenceExpr aExpr)
    {
      super (aExpr);
      m_aItems = aC.compileAll (aExpr.items ());
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      if (m_aItems.length == 1)
        return m_aItems[0].evaluate (aCtx);
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final AbstractCompiledExpr aItem : m_aItems)
        aBuilder.addAll (aItem.evaluate (aCtx));
      return aBuilder.build ();
    }

    @Override
    @Nullable
    Boolean tryEarlyEbv (@NonNull final DynamicContext aCtx, final boolean bPredicate)
    {
      return m_aItems.length == 1 ? m_aItems[0].tryEarlyEbv (aCtx, bPredicate) : null;
    }

    @Override
    boolean exists (@NonNull final DynamicContext aCtx)
    {
      if (m_aItems.length == 1)
        return m_aItems[0].exists (aCtx);
      return super.exists (aCtx);
    }
  }

  /** A literal (XPath 3.1 section 3.1.1): its sequence, built once by the parser. */
  @Immutable
  private static final class LiteralNode extends AbstractCompiledExpr
  {
    private final XdmSequence m_aValue;

    LiteralNode (final IExpr.@NonNull LiteralExpr aExpr)
    {
      super (aExpr);
      m_aValue = aExpr.sequence ();
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return m_aValue;
    }
  }

  /** A variable reference (XPath 3.1 section 3.1.2): the value bound in the dynamic context. */
  @Immutable
  private static final class VarRefNode extends AbstractCompiledExpr
  {
    private final XdmQName m_aName;

    VarRefNode (final IExpr.@NonNull VarRefExpr aExpr)
    {
      super (aExpr);
      m_aName = aExpr.name ();
    }

    /**
     * @throws XPathException
     *         XPDY0002 if no value is bound to the name
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final XdmSequence ret = aCtx.getVariable (m_aName);
      if (ret == null)
        throw XPathException.noContext ("No value is bound to the variable $" + m_aName.getEQName ());
      return ret;
    }
  }

  /** The context item expression <code>.</code> (XPath 3.1 section 3.1.4). */
  @Immutable
  private static final class ContextItemNode extends AbstractCompiledExpr
  {
    ContextItemNode (final IExpr.@NonNull ContextItemExpr aExpr)
    {
      super (aExpr);
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return XdmSequence.of (_getContextItem (aCtx));
    }
  }

  /** An argument placeholder outside a function call, which is an error when evaluated. */
  @Immutable
  private static final class PlaceholderNode extends AbstractCompiledExpr
  {
    PlaceholderNode (final IExpr.@NonNull PlaceholderExpr aExpr)
    {
      super (aExpr);
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      throw XPathException.syntax ("An argument placeholder is only allowed in a function call");
    }
  }

  /**
   * @return <code>true</code> if one of the compiled arguments is an argument placeholder
   *         (<code>?</code>), which makes the call a partial function application (XPath 3.1
   *         section 3.1.5.1).
   */
  private static boolean _hasPlaceholder (@NonNull final AbstractCompiledExpr [] aArgs)
  {
    for (final AbstractCompiledExpr aArg : aArgs)
      if (aArg instanceof PlaceholderNode)
        return true;
    return false;
  }

  /**
   * Partial application: evaluate and convert the bound arguments now.
   */
  @NonNull
  private static XdmSequence _partialApply (@NonNull final IXdmFunction aFunc,
                                            @NonNull final AbstractCompiledExpr [] aArgs,
                                            @NonNull final DynamicContext aCtx)
  {
    final List <SeqType> aParamTypes = aFunc.getParamTypes ();
    final XdmSequence [] aBound = new XdmSequence [aArgs.length];
    for (int i = 0; i < aBound.length; ++i)
    {
      final AbstractCompiledExpr aArg = aArgs[i];
      if (!(aArg instanceof PlaceholderNode))
        aBound[i] = XPathOperators.convertArgument (aArg.evaluate (aCtx), aParamTypes.get (i), aCtx, aFunc.getName (), i);
    }
    return XdmSequence.of (new PartialFunctionItem (aFunc, aBound));
  }

  /**
   * A static call of fn:exists, fn:empty, fn:boolean or fn:not (recognised by the implementation
   * the name resolves to): only the existence or the effective boolean value of the argument
   * matters, so it is evaluated with early exit. The parameter type <code>item()*</code> needs no
   * conversion.
   *
   * @return <code>null</code> for any other function.
   */
  @Nullable
  private static XdmSequence _tryExistenceCall (@NonNull final FunctionDef aDef,
                                                @NonNull final AbstractCompiledExpr aArg,
                                                @NonNull final DynamicContext aCtx)
  {
    final IXPathFunction aImpl = aDef.getImpl ();
    if (aImpl == FnSequence.EXISTS)
      return _bool (aArg.exists (aCtx));
    if (aImpl == FnSequence.EMPTY)
      return _bool (!aArg.exists (aCtx));
    if (aImpl == FnBoolean.BOOLEAN)
      return _bool (aArg.getEbv (aCtx));
    if (aImpl == FnBoolean.NOT)
      return _bool (!aArg.getEbv (aCtx));
    return null;
  }

  /**
   * The literal arguments of a static call converted to the parameter types of the resolved
   * definition, from the call site if it holds them for this definition and compatibility mode,
   * otherwise converted now and stored there. A literal whose conversion fails gets no entry, so
   * that the call converts it again and raises the error at the time it always did. The conversion
   * of a literal depends only on its value, the parameter type and the XPath 1.0 compatibility
   * mode, and has no side effects.
   *
   * @return <code>null</code> if no argument is a literal.
   */
  private static FunctionCallSite.@Nullable ConvertedLiterals _getConvertedLiterals (final IExpr.@NonNull FunctionCallExpr aExpr,
                                                                                    @NonNull final FunctionDef aDef,
                                                                                    @NonNull final DynamicContext aCtx)
  {
    final FunctionCallSite aSite = aExpr.site ();
    final List <IExpr> aArgExprs = aExpr.args ();
    if (!aSite.hasLiteralArgument (aArgExprs))
      return null;
    final boolean bCompat = aCtx.getStaticContext ().isXPath10Compatibility ();
    FunctionCallSite.ConvertedLiterals ret = aSite.getConvertedLiterals (aDef, bCompat);
    if (ret == null)
    {
      final List <SeqType> aParamTypes = aDef.getParamTypes ();
      final XdmSequence [] aValues = new XdmSequence [aArgExprs.size ()];
      for (int i = 0; i < aValues.length; ++i)
        if (aArgExprs.get (i) instanceof final IExpr.LiteralExpr aLiteral)
          try
          {
            aValues[i] = XPathOperators.convertArgument (aLiteral.sequence (),
                                                         aParamTypes.get (i),
                                                         aCtx,
                                                         aDef.getName (),
                                                         i);
          }
          catch (final XPathException ex)
          {
            // Converted and raised again by the call itself
          }
      ret = new FunctionCallSite.ConvertedLiterals (aDef, bCompat, aValues);
      aSite.setConvertedLiterals (ret);
    }
    return ret;
  }

  /**
   * The argument list of a static call for the implementation, which only reads it: the shared
   * empty list, an immutable list of the one or two values without an array (the most frequent
   * arities), otherwise a fixed-size list over the array, which nobody else holds.
   */
  @NonNull
  private static List <XdmSequence> _getArgumentList (@NonNull final XdmSequence [] aArgs)
  {
    return switch (aArgs.length)
    {
      case 0 -> List.of ();
      case 1 -> List.of (aArgs[0]);
      case 2 -> List.of (aArgs[0], aArgs[1]);
      default -> Arrays.asList (aArgs);
    };
  }

  /**
   * A static function call (XPath 3.1 section 3.1.5), memoised by {@link CallMemo} where its
   * value is determined by the context item and its variables (loop-invariant hoisting).
   */
  @Immutable
  private static final class FunctionCallNode extends AbstractCompiledExpr
  {
    private final IExpr.FunctionCallExpr m_aCall;
    private final AbstractCompiledExpr [] m_aArgs;
    // Kept, so that the memo creates no function objects per evaluation
    private final Function <FunctionLibrary, Object> m_aPlanCreator;
    private final Function <DynamicContext, XdmSequence> m_aDirect;
    // The variables of the call bound by slot (host or expression frame), for the tokens of the
    // memo; null for none
    private final Map <XdmQName, VariableSlot> m_aFrameVariables;

    FunctionCallNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull FunctionCallExpr aCall)
    {
      super (aCall);
      m_aCall = aCall;
      m_aArgs = aC.compileAll (aCall.args ());
      m_aPlanCreator = CallMemo.createPlanCreator (aCall);
      m_aDirect = this::_evalDirect;
      Map <XdmQName, VariableSlot> aFrameVariables = null;
      for (final XdmQName aName : ExprWalker.getAllVariableReferences (aCall))
      {
        final VariableSlot aSlot = aC.getTokenSlot (aName);
        if (aSlot != null)
        {
          if (aFrameVariables == null)
            aFrameVariables = new HashMap <> ();
          aFrameVariables.put (aName, aSlot);
        }
      }
      m_aFrameVariables = aFrameVariables;
    }

    /**
     * The call without the memo. The function is resolved by name and arity in the function
     * library of the static context, through the resolution cache of the call site (see
     * {@link FunctionCallSite}). With an argument placeholder the result is a partially applied
     * {@link BuiltInFunctionItem}. A one-argument call of <code>fn:exists</code>,
     * <code>fn:empty</code>, <code>fn:boolean</code> or <code>fn:not</code> is answered with early
     * exit. Otherwise every argument is evaluated, converted to its parameter type with the
     * function conversion rules (including the XPath 1.0 compatibility conversions) and the
     * implementation is invoked; a literal argument is taken converted from the call site (see
     * {@link #_getConvertedLiterals(IExpr.FunctionCallExpr, FunctionDef, DynamicContext)}).
     *
     * @throws XPathException
     *         XPST0017 (from the function lookup) if no function of that name and arity exists;
     *         XPTY0004 or another conversion error for an argument that does not match its type
     */
    @NonNull
    private XdmSequence _evalDirect (@NonNull final DynamicContext aCtx)
    {
      final AbstractCompiledExpr [] aArgExprs = m_aArgs;
      final FunctionDef aDef = XPathOperators.getFunctionDef (aCtx, m_aCall.site (), m_aCall.name (), aArgExprs.length);
      if (m_aCall.site ().hasPlaceholder (m_aCall.args ()))
        return _partialApply (new BuiltInFunctionItem (aDef, aCtx), aArgExprs, aCtx);
      if (aArgExprs.length == 1)
      {
        // Optimisation (early exit) for exists, empty, boolean and not: only existence matters
        final XdmSequence aEarly = _tryExistenceCall (aDef, aArgExprs[0], aCtx);
        if (aEarly != null)
          return aEarly;
      }

      final List <SeqType> aParamTypes = aDef.getParamTypes ();
      final FunctionCallSite.ConvertedLiterals aLiterals = _getConvertedLiterals (m_aCall, aDef, aCtx);
      final XdmSequence [] aArgs = new XdmSequence [aArgExprs.length];
      for (int i = 0; i < aArgs.length; ++i)
      {
        final XdmSequence aLiteral = aLiterals == null ? null : aLiterals.get (i);
        if (aLiteral != null)
          aArgs[i] = aLiteral;
        else
        {
          // XPath 1.0 compatibility rules are applied inside convert
          final XdmSequence aValue = aArgExprs[i].evaluate (aCtx);
          aArgs[i] = XPathOperators.convertArgument (aValue, aParamTypes.get (i), aCtx, aDef.getName (), i);
        }
      }
      return XPathOperators.invokeBuiltIn (aDef, aCtx, _getArgumentList (aArgs));
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final CallMemo.Plan aPlan = CallMemo.getApplicablePlan (m_aCall, m_aPlanCreator, aCtx);
      if (aPlan == null)
        return _evalDirect (aCtx);
      return CallMemo.evaluate (aPlan, m_aCall, m_aFrameVariables, aCtx, m_aDirect);
    }
  }

  /**
   * A dynamic function call (XPath 3.1 section 3.2.2): the base expression must give exactly one
   * function item (a map or an array counts as a function of arity one) whose arity is the number
   * of arguments. With an argument placeholder the function is partially applied; otherwise the
   * arguments are evaluated and passed to
   * {@link XPathEvaluator#callFunction(DynamicContext, IXdmItem, List)}, which converts them.
   */
  @Immutable
  private static final class DynamicCallNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr m_aBase;
    private final AbstractCompiledExpr [] m_aArgs;

    DynamicCallNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull DynamicCallExpr aExpr)
    {
      super (aExpr);
      m_aBase = aC.compile (aExpr.base ());
      m_aArgs = aC.compileAll (aExpr.args ());
    }

    /**
     * @throws XPathException
     *         XPTY0004 if the base is not a single item, not a function item, or of another arity
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final XdmSequence aBase = m_aBase.evaluate (aCtx);
      if (!aBase.isSingleton ())
        throw XPathException.type ("The target of a dynamic function call must be a single item, but it has " +
                                   aBase.size () +
                                   " items");
      final IXdmItem aFuncItem = aBase.get (0);
      final int nArity = getFunctionArity (aFuncItem);
      if (nArity < 0)
        throw XPathException.type ("The target of a dynamic function call is not a function");
      if (nArity != m_aArgs.length)
        throw XPathException.type ("A function of arity " + nArity + " is called with " + m_aArgs.length + " arguments");
      if (_hasPlaceholder (m_aArgs))
        return _partialApply (asFunction (aFuncItem), m_aArgs, aCtx);
      // Optimisation: a map or array lookup takes its single argument without an argument list
      if (aFuncItem instanceof XdmMap || aFuncItem instanceof XdmArray)
        return callFunction (aCtx, aFuncItem, List.of (m_aArgs[0].evaluate (aCtx)));

      final ICommonsList <XdmSequence> aArgs = new CommonsArrayList <> (m_aArgs.length);
      for (final AbstractCompiledExpr aArgExpr : m_aArgs)
        aArgs.add (aArgExpr.evaluate (aCtx));
      return callFunction (aCtx, aFuncItem, aArgs);
    }
  }

  /**
   * A named function reference <code>name#arity</code> (XPath 3.1 section 3.1.6): a
   * {@link BuiltInFunctionItem} for the function of that name and arity. A focus-dependent
   * function (such as <code>fn:name#0</code>) captures the current dynamic context, so that it is
   * evaluated later with the focus of the reference.
   * <p>
   * The function is resolved through the resolution cache of the call site of the expression (see
   * {@link FunctionCallSite}), and the item of a function that is not focus-dependent is kept for
   * its definition: such an item captures nothing of the evaluation, and function items have no
   * identity, so every evaluation may return the same one.
   */
  @ThreadSafe
  private static final class NamedFunctionRefNode extends AbstractCompiledExpr
  {
    private final XdmQName m_aName;
    private final int m_nArity;
    private final FunctionCallSite m_aSite;
    // The sequence of the item of the last resolved definition that is not focus-dependent; an immutable value
    // published through the volatile field, concurrent evaluations may each store one
    private volatile XdmSequence m_aItem;

    NamedFunctionRefNode (final IExpr.@NonNull NamedFunctionRefExpr aExpr)
    {
      super (aExpr);
      m_aName = aExpr.name ();
      m_nArity = aExpr.arity ();
      m_aSite = aExpr.site ();
    }

    /**
     * @param aCtx
     *        The dynamic context.
     * @return The function definition the reference resolves to in the function library of the
     *         static context.
     * @throws XPathException
     *         XPST0017 if no function of that name and arity exists
     */
    @NonNull
    FunctionDef getFunctionDef (@NonNull final DynamicContext aCtx)
    {
      return XPathOperators.getFunctionDef (aCtx, m_aSite, m_aName, m_nArity);
    }

    /**
     * @param aDef
     *        The resolved definition, from {@link #getFunctionDef(DynamicContext)}.
     * @param aCtx
     *        The dynamic context, captured for a focus-dependent function.
     * @return The single function item of the definition.
     */
    @NonNull
    XdmSequence getFunctionItem (@NonNull final FunctionDef aDef, @NonNull final DynamicContext aCtx)
    {
      if (aDef.isFocusDependent ())
        return XdmSequence.of (new BuiltInFunctionItem (aDef, aCtx));
      final XdmSequence aKept = m_aItem;
      // Optimisation: the item of the same definition captures nothing, so it is reused
      if (aKept != null && ((BuiltInFunctionItem) aKept.getFirst ()).getFunctionDef () == aDef)
        return aKept;
      final XdmSequence ret = XdmSequence.of (new BuiltInFunctionItem (aDef, null));
      m_aItem = ret;
      return ret;
    }

    /**
     * @throws XPathException
     *         XPST0017 (from the function lookup) if no function of that name and arity exists
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return getFunctionItem (getFunctionDef (aCtx), aCtx);
    }
  }

  /** An inline function expression (XPath 3.1 section 3.1.7) with its compiled body. */
  @Immutable
  private static final class InlineFunctionNode extends AbstractCompiledExpr
  {
    private final IExpr.InlineFunctionExpr m_aFunction;
    private final InlineFunctionItem.Body m_aBody;

    InlineFunctionNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull InlineFunctionExpr aExpr)
    {
      super (aExpr);
      m_aFunction = aExpr;
      // The body has its own frame per call; the parameters are its first slots
      final FunctionBoundary aBoundary = aC.enterFunction ();
      final List <IExpr.Param> aParams = aExpr.params ();
      final VariableSlot [] aParamSlots = new VariableSlot [aParams.size ()];
      for (int i = 0; i < aParamSlots.length; ++i)
        aParamSlots[i] = aC.bindLocal (aParams.get (i).name ());
      final AbstractCompiledExpr aBody = aC.compile (aExpr.body ());
      aC.exitFunction (aBoundary);
      final int nCaptures = aBoundary.m_aCaptures.size ();
      final XdmQName [] aCaptureNames = aBoundary.m_aCaptures.keySet ().toArray (new XdmQName [nCaptures]);
      final VariableSlot [] aCaptureSlots = aBoundary.m_aCaptures.values ().toArray (new VariableSlot [nCaptures]);
      m_aBody = new InlineFunctionItem.Body (aBody, aBoundary.m_aLayout, aParamSlots, aCaptureNames, aCaptureSlots);
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return XdmSequence.of (new InlineFunctionItem (m_aFunction, m_aBody, aCtx));
    }
  }

  /**
   * One operand of a range expression <code>a to b</code> (XPath 3.1 section 3.4.1): atomised,
   * an <code>xs:untypedAtomic</code> value cast to <code>xs:integer</code>, and then required to
   * be of an integer type. The operand is converted like an argument of type
   * <code>xs:integer?</code>, so in XPath 1.0 compatibility mode only its first item counts
   * (section 3.1.5.2).
   *
   * @return The integer value, or <code>null</code> for the empty sequence.
   * @throws XPathException
   *         XPTY0004 for more than one item or a value that is no integer (<code>1.0</code> and
   *         <code>1e0</code> included); FORG0001 for an untyped value that is no valid integer
   */
  @Nullable
  private static BigInteger _getRangeOperand (@NonNull final XdmSequence aSeq, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aOperand = aSeq.size () > 1 && aCtx.getStaticContext ().isXPath10Compatibility () ? XdmSequence.of (aSeq.getFirst ())
                                                                                                         : aSeq;
    XdmAtomic aValue = XPathOperators.atomizeOptional (aOperand, "operand of 'to'");
    if (aValue == null)
      return null;
    if (aValue.getType () == EAtomicType.UNTYPED_ATOMIC)
      aValue = AtomicCaster.castFromString (aValue.getString (), EAtomicType.INTEGER, aCtx.getStaticContext ());
    if (!aValue.getType ().isIntegerFamily ())
      throw XPathException.type ("The operands of 'to' must be integers, not " + aValue.getType ().getQualifiedName ());
    return aValue.getBigInteger ();
  }

  /**
   * The range expression <code>a to b</code> (XPath 3.1 section 3.4.1), materialised as a
   * sequence of <code>xs:integer</code> values in ascending order. An empty operand or
   * <code>a &gt; b</code> gives the empty sequence. Interrupts are checked every 4096 items.
   */
  @Immutable
  private static final class RangeNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr m_aFrom;
    private final AbstractCompiledExpr m_aTo;

    RangeNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull RangeExpr aExpr)
    {
      super (aExpr);
      m_aFrom = aC.compile (aExpr.from ());
      m_aTo = aC.compile (aExpr.to ());
    }

    /**
     * @throws XPathException
     *         XPDY0130 if the range has more than {@link #MAX_RANGE_SIZE} items; the operand errors
     *         of {@link XPathEvaluator#_getRangeOperand(XdmSequence, DynamicContext)}
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final XdmSequence aFromSeq = m_aFrom.evaluate (aCtx);
      final XdmSequence aToSeq = m_aTo.evaluate (aCtx);
      final BigInteger aFrom = _getRangeOperand (aFromSeq, aCtx);
      final BigInteger aTo = _getRangeOperand (aToSeq, aCtx);
      if (aFrom == null || aTo == null || aFrom.compareTo (aTo) > 0)
        return XdmSequence.EMPTY;
      final BigInteger aSpan = aTo.subtract (aFrom);
      if (aSpan.compareTo (BigInteger.valueOf (MAX_RANGE_SIZE)) >= 0)
        throw XPathException.of ("XPDY0130", "The integer range " + aFrom + " to " + aTo + " is too large");
      final int nCount = aSpan.intValue () + 1;
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      if (aFrom.bitLength () < 63 && aTo.bitLength () < 63)
      {
        final long nFrom = aFrom.longValue ();
        for (int i = 0; i < nCount; ++i)
        {
          if ((i & 4095) == 0)
            aCtx.checkInterrupt ();
          aBuilder.add (XdmAtomic.integer (nFrom + i));
        }
      }
      else
        for (int i = 0; i < nCount; ++i)
        {
          if ((i & 4095) == 0)
            aCtx.checkInterrupt ();
          aBuilder.add (XdmAtomic.integer (aFrom.add (BigInteger.valueOf (i))));
        }
      return aBuilder.build ();
    }
  }

  /**
   * @return The general comparison operator with the operands swapped (<code>&lt;</code> becomes
   *         <code>&gt;</code>, <code>&lt;=</code> becomes <code>&gt;=</code> and vice versa);
   *         <code>=</code>, <code>!=</code> and every other operator are returned unchanged.
   */
  @NonNull
  private static EComparisonOp _swapGeneralOp (@NonNull final EComparisonOp eOp)
  {
    return switch (eOp)
    {
      case GENERAL_LT -> EComparisonOp.GENERAL_GT;
      case GENERAL_GT -> EComparisonOp.GENERAL_LT;
      case GENERAL_LE -> EComparisonOp.GENERAL_GE;
      case GENERAL_GE -> EComparisonOp.GENERAL_LE;
      default -> eOp;
    };
  }

  /**
   * @return <code>true</code> if the xs:integer or xs:decimal value compares true under the
   *         operator with some integer of the range. Both are compared exactly, at any magnitude.
   */
  private static boolean _isRangeHitExact (@NonNull final EComparisonOp eOp,
                                           @NonNull final XdmAtomic aValue,
                                           @NonNull final BigInteger aFrom,
                                           @NonNull final BigInteger aTo)
  {
    final BigDecimal aV = aValue.getValue () instanceof final BigInteger aBigInt ? new BigDecimal (aBigInt)
                                                                                 : aValue.getBigDecimal ();
    final BigDecimal aLo = new BigDecimal (aFrom);
    final BigDecimal aHi = new BigDecimal (aTo);
    return switch (eOp)
    {
      case GENERAL_EQ -> (aV.signum () == 0 || aV.stripTrailingZeros ().scale () <= 0) &&
                         aV.compareTo (aLo) >= 0 &&
                         aV.compareTo (aHi) <= 0;
      case GENERAL_NE -> aFrom.compareTo (aTo) != 0 || aV.compareTo (aLo) != 0;
      case GENERAL_LT -> aV.compareTo (aHi) < 0;
      case GENERAL_LE -> aV.compareTo (aHi) <= 0;
      case GENERAL_GT -> aV.compareTo (aLo) > 0;
      default -> aV.compareTo (aLo) >= 0;
    };
  }

  /**
   * {@link #_isRangeHitExact(EComparisonOp, XdmAtomic, BigInteger, BigInteger)} for an xs:double or
   * xs:float value, to whose type every range member is promoted first. Promotion is monotonic, so
   * the promoted bounds settle each operator; for "!=" some member differs from the value unless
   * both promoted bounds (and so every member between them) equal it.
   */
  private static boolean _isRangeHitFloat (@NonNull final EComparisonOp eOp,
                                           @NonNull final XdmAtomic aValue,
                                           @NonNull final BigInteger aFrom,
                                           @NonNull final BigInteger aTo)
  {
    final double d = aValue.getAsDouble ();
    if (Double.isNaN (d))
      return eOp == EComparisonOp.GENERAL_NE;
    final boolean bFloat = aValue.getType () == EAtomicType.FLOAT;
    final double dLo = bFloat ? aFrom.floatValue () : aFrom.doubleValue ();
    final double dHi = bFloat ? aTo.floatValue () : aTo.doubleValue ();
    return switch (eOp)
    {
      case GENERAL_EQ -> d >= dLo && d <= dHi && d == Math.rint (d);
      case GENERAL_NE -> dLo != d || dHi != d;
      case GENERAL_LT -> d < dHi;
      case GENERAL_LE -> d <= dHi;
      case GENERAL_GT -> d > dLo;
      default -> d >= dLo;
    };
  }

  /**
   * A general comparison one of whose operands is a range expression, without materialising the
   * range (<code>$c = (32 to 55295)</code> is a bounds test; RangeExpr-409c compares against a
   * range of 10^13 integers). The existential semantics are exact: for a numeric v and the integers
   * from..to, "v = range" holds iff v is an integer in [from, to], "v != range" iff some member
   * differs from v, and the orderings compare v against the nearest bound. An empty range makes
   * every comparison false.
   *
   * @param eOp
   *        The operator, oriented so that the other operand is on the left.
   * @param aOtherAtoms
   *        The atomised other operand.
   * @return <code>null</code> if the fast path does not apply (the other operand holds anything but
   *         numeric or untyped atomic values); the caller then takes the ordinary path.
   */
  @Nullable
  private static XdmSequence _evalRangeComparison (@NonNull final EComparisonOp eOp,
                                                   @NonNull final RangeNode aRange,
                                                   @NonNull final XdmSequence aOtherAtoms,
                                                   @NonNull final DynamicContext aCtx)
  {
    final ICommonsList <XdmAtomic> aValues = new CommonsArrayList <> (aOtherAtoms.size ());
    for (final IXdmItem aItem : aOtherAtoms)
    {
      final XdmAtomic aAtomic = (XdmAtomic) aItem;
      if (aAtomic.isNumeric ())
        aValues.add (aAtomic);
      else
        if (aAtomic.getType () == EAtomicType.UNTYPED_ATOMIC)
          aValues.add (XPathOperators.untypedToDouble (aAtomic, aCtx));
        else
          return null;
    }
    final BigInteger aFrom = _getRangeOperand (aRange.m_aFrom.evaluate (aCtx), aCtx);
    final BigInteger aTo = _getRangeOperand (aRange.m_aTo.evaluate (aCtx), aCtx);
    if (aFrom == null || aTo == null || aFrom.compareTo (aTo) > 0 || aValues.isEmpty ())
      return FALSE_SEQ;
    for (final XdmAtomic aValue : aValues)
    {
      final Object aPayload = aValue.getValue ();
      final boolean bHit;
      if (aPayload instanceof BigInteger || aPayload instanceof BigDecimal)
        bHit = _isRangeHitExact (eOp, aValue, aFrom, aTo);
      else
        bHit = _isRangeHitFloat (eOp, aValue, aFrom, aTo);
      if (bHit)
        return TRUE_SEQ;
    }
    return FALSE_SEQ;
  }

  /**
   * @return The call if a compiled expression is a static call of <code>fn:generate-id</code> with
   *         zero or one argument and without placeholder, otherwise <code>null</code>. Whether it
   *         resolves to the built-in implementation is decided at run time.
   */
  @Nullable
  private static FunctionCallNode _getGenerateIdCall (@NonNull final AbstractCompiledExpr aExpr)
  {
    if (aExpr instanceof final FunctionCallNode aCall &&
        aCall.m_aArgs.length <= 1 &&
        "generate-id".equals (aCall.m_aCall.name ().localName ()) &&
        XdmConstants.NS_FN.equals (aCall.m_aCall.name ().namespaceUri ()) &&
        !aCall.m_aCall.site ().hasPlaceholder (aCall.m_aCall.args ()))
      return aCall;
    return null;
  }

  /**
   * @return The definition a <code>fn:generate-id</code> call (see
   *         {@link #_getGenerateIdCall(AbstractCompiledExpr)}) resolves to in the function library of
   *         the static context if it is the built-in implementation ({@link FnAccessor#GENERATE_ID}),
   *         otherwise <code>null</code>, so that a host function of that name is called as written.
   */
  @Nullable
  private static FunctionDef _getBuiltInGenerateId (@NonNull final FunctionCallNode aCall, @NonNull final DynamicContext aCtx)
  {
    final FunctionDef aDef = aCall.m_aCall.site ()
                                          .getFunction (aCtx.getStaticContext ().getFunctionLibrary (),
                                                        aCall.m_aCall.name (),
                                                        aCall.m_aArgs.length);
    return aDef != null && aDef.getImpl () == FnAccessor.GENERATE_ID ? aDef : null;
  }

  /**
   * The node a built-in <code>fn:generate-id</code> call would give the identifier of, with the
   * errors of the call: the context item for the zero-argument form (XPDY0002 if absent, XPTY0004
   * if it is no node), otherwise the argument converted to <code>node()?</code>.
   *
   * @return <code>null</code> for an empty argument.
   */
  @Nullable
  private static IXdmNode _getGenerateIdNode (@NonNull final FunctionCallNode aCall,
                                              @NonNull final FunctionDef aDef,
                                              @NonNull final DynamicContext aCtx)
  {
    if (aCall.m_aArgs.length == 0)
    {
      final IXdmItem aItem = aCtx.getContextItem ();
      if (aItem == null)
        throw XPathException.noContext ("The context item is absent");
      if (aItem instanceof final IXdmNode aNode)
        return aNode;
      throw XPathException.type ("The context item is not a node");
    }
    final XdmSequence aArg = XPathOperators.convertArgument (aCall.m_aArgs[0].evaluate (aCtx),
                                                             aDef.getParamTypes ().get (0),
                                                             aCtx,
                                                             aDef.getName (),
                                                             0);
    return aArg.isEmpty () ? null : (IXdmNode) aArg.get (0);
  }

  /**
   * A value, general or node comparison (XPath 3.1 section 3.7). A general comparison with a range
   * expression as one operand is first tried without materialising the range
   * ({@link XPathEvaluator#_evalRangeComparison(EComparisonOp, RangeNode, XdmSequence, DynamicContext)});
   * not in XPath 1.0 compatibility mode, which has its own general comparison rules. Everything
   * else goes to
   * {@link XPathOperators#compare(EComparisonOp, XdmSequence, XdmSequence, DynamicContext)}. The
   * left operand is evaluated before the right one.
   * <p>
   * An equality or inequality (<code>=</code>, <code>!=</code>, <code>eq</code>, <code>ne</code>)
   * of two calls of the built-in <code>fn:generate-id</code> under the codepoint collation (the
   * Muenchian grouping of XSLT 1.0, <code>generate-id() = generate-id(key('k', @a)[1])</code>)
   * compares the nodes: the identifiers are equal exactly for the same node or two empty
   * arguments, so no identifier is created.
   */
  @Immutable
  private static final class ComparisonNode extends AbstractCompiledExpr
  {
    private final EComparisonOp m_eOp;
    private final AbstractCompiledExpr m_aLeft;
    private final AbstractCompiledExpr m_aRight;
    // Both operands as fn:generate-id calls for an (in)equality, otherwise null
    private final FunctionCallNode m_aLeftGenerateId;
    private final FunctionCallNode m_aRightGenerateId;

    ComparisonNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull ComparisonExpr aExpr)
    {
      super (aExpr);
      m_eOp = aExpr.op ();
      m_aLeft = aC.compile (aExpr.left ());
      m_aRight = aC.compile (aExpr.right ());
      final boolean bEquality = m_eOp == EComparisonOp.GENERAL_EQ ||
                                m_eOp == EComparisonOp.GENERAL_NE ||
                                m_eOp == EComparisonOp.VALUE_EQ ||
                                m_eOp == EComparisonOp.VALUE_NE;
      final FunctionCallNode aLeftCall = bEquality ? _getGenerateIdCall (m_aLeft) : null;
      final FunctionCallNode aRightCall = aLeftCall != null ? _getGenerateIdCall (m_aRight) : null;
      m_aLeftGenerateId = aRightCall != null ? aLeftCall : null;
      m_aRightGenerateId = aRightCall;
    }

    /**
     * Compare two built-in <code>fn:generate-id</code> calls by their nodes (see the class
     * description), the left argument evaluated first, as the calls would be.
     *
     * @return <code>null</code> if a call does not resolve to the built-in implementation or the
     *         default collation is not the codepoint collation; the caller then evaluates the
     *         comparison as written.
     */
    @Nullable
    private XdmSequence _tryGenerateIdComparison (@NonNull final DynamicContext aCtx)
    {
      // Another collation might take different identifiers for equal
      if (!CollationRegistry.CODEPOINT_URI.equals (aCtx.getStaticContext ().getDefaultCollation ()))
        return null;
      final FunctionDef aLeftDef = _getBuiltInGenerateId (m_aLeftGenerateId, aCtx);
      if (aLeftDef == null)
        return null;
      final FunctionDef aRightDef = _getBuiltInGenerateId (m_aRightGenerateId, aCtx);
      if (aRightDef == null)
        return null;
      final IXdmNode aLeft = _getGenerateIdNode (m_aLeftGenerateId, aLeftDef, aCtx);
      final IXdmNode aRight = _getGenerateIdNode (m_aRightGenerateId, aRightDef, aCtx);
      // Nodes have identity semantics, as in the identifier table of fn:generate-id
      final boolean bSame = aLeft == aRight;
      final boolean bEqual = m_eOp == EComparisonOp.GENERAL_EQ || m_eOp == EComparisonOp.VALUE_EQ;
      return _bool (bSame == bEqual);
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      if (m_aLeftGenerateId != null)
      {
        // Optimisation: two fn:generate-id calls are compared by their nodes, without creating the
        // identifiers (a run cache and a weak map lookup and a string per call)
        final XdmSequence aFast = _tryGenerateIdComparison (aCtx);
        if (aFast != null)
          return aFast;
      }
      // XPath 1.0 compatibility mode has its own general comparison rules
      if (m_eOp.isGeneral () && !aCtx.getStaticContext ().isXPath10Compatibility ())
      {
        if (m_aRight instanceof final RangeNode aRange)
        {
          final XdmSequence aLeft = XPathOperators.atomize (m_aLeft.evaluate (aCtx));
          final XdmSequence aFast = _evalRangeComparison (m_eOp, aRange, aLeft, aCtx);
          if (aFast != null)
            return aFast;
          return _bool (XPathOperators.generalCompareAtoms (m_eOp, aLeft, XPathOperators.atomize (aRange.evaluate (aCtx)), aCtx));
        }
        if (m_aLeft instanceof final RangeNode aRange)
        {
          final XdmSequence aRight = XPathOperators.atomize (m_aRight.evaluate (aCtx));
          final XdmSequence aFast = _evalRangeComparison (_swapGeneralOp (m_eOp), aRange, aRight, aCtx);
          if (aFast != null)
            return aFast;
          return _bool (XPathOperators.generalCompareAtoms (m_eOp, XPathOperators.atomize (aRange.evaluate (aCtx)), aRight, aCtx));
        }
      }
      final XdmSequence aLeft = m_aLeft.evaluate (aCtx);
      final XdmSequence aRight = m_aRight.evaluate (aCtx);
      return XPathOperators.compare (m_eOp, aLeft, aRight, aCtx);
    }
  }

  /**
   * A logical (XPath 3.1 section 3.8) or arithmetic (section 3.5) expression. <code>and</code>
   * and <code>or</code> take the effective boolean value of the operands left to right, each with
   * early exit, and do not evaluate the right operand once the left one decides the result. An
   * arithmetic operator evaluates both operands and applies
   * {@link XPathOperators#arithmetic(EBinaryOp, XdmSequence, XdmSequence, DynamicContext)}.
   */
  @Immutable
  private static final class BinaryNode extends AbstractCompiledExpr
  {
    private final EBinaryOp m_eOp;
    private final AbstractCompiledExpr m_aLeft;
    private final AbstractCompiledExpr m_aRight;

    BinaryNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull BinaryExpr aExpr)
    {
      super (aExpr);
      m_eOp = aExpr.op ();
      m_aLeft = aC.compile (aExpr.left ());
      m_aRight = aC.compile (aExpr.right ());
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final EBinaryOp eOp = m_eOp;
      if (eOp == EBinaryOp.AND)
        return _bool (m_aLeft.getEbv (aCtx) && m_aRight.getEbv (aCtx));
      if (eOp == EBinaryOp.OR)
        return _bool (m_aLeft.getEbv (aCtx) || m_aRight.getEbv (aCtx));

      final XdmSequence aLeft = m_aLeft.evaluate (aCtx);
      final XdmSequence aRight = m_aRight.evaluate (aCtx);
      return XPathOperators.arithmetic (eOp, aLeft, aRight, aCtx);
    }
  }

  /** The unary arithmetic operators (XPath 3.1 section 3.5). */
  @Immutable
  private static final class UnaryNode extends AbstractCompiledExpr
  {
    private final boolean m_bNegate;
    private final AbstractCompiledExpr m_aOperand;

    UnaryNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull UnaryExpr aExpr)
    {
      super (aExpr);
      m_bNegate = aExpr.negate ();
      m_aOperand = aC.compile (aExpr.operand ());
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return XPathOperators.unaryArithmetic (m_bNegate, m_aOperand.evaluate (aCtx), aCtx);
    }
  }

  /**
   * The string concatenation <code>a || b || ...</code> (XPath 3.1 section 3.6): each operand is
   * atomised, an empty operand counts as the zero-length string, and the string values are joined.
   */
  @Immutable
  private static final class StringConcatNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr [] m_aParts;

    StringConcatNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull StringConcatExpr aExpr)
    {
      super (aExpr);
      m_aParts = aC.compileAll (aExpr.parts ());
    }

    /**
     * @return A single <code>xs:string</code>.
     * @throws XPathException
     *         XPTY0004 if an operand atomises to more than one item; FOTY0013 for a map or function
     *         item
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final StringBuilder aSB = new StringBuilder ();
      for (final AbstractCompiledExpr aPart : m_aParts)
      {
        final XdmAtomic aValue = XPathOperators.atomizeOptional (aPart.evaluate (aCtx), "operand of '||'");
        if (aValue != null)
          aSB.append (_getStringValue (aValue));
      }
      return XdmSequence.of (XdmAtomic.string (aSB.toString ()));
    }
  }

  /**
   * @param eOp
   *        The operator, for the message.
   * @return The sequence itself if all items are nodes.
   * @throws XPathException
   *         XPTY0004 for any item that is not a node
   */
  @NonNull
  private static XdmSequence _checkNodes (@NonNull final XdmSequence aSeq, final IExpr.SetExpr.@NonNull ESetOp eOp)
  {
    for (final IXdmItem aItem : aSeq)
      if (!(aItem instanceof IXdmNode))
        throw XPathException.type ("The operands of '" + eOp.name ().toLowerCase (Locale.ROOT) + "' must be nodes");
    return aSeq;
  }

  /**
   * @return <code>true</code> if the node passes one of the tests.
   */
  private static boolean _matchesAny (@NonNull final List <NodeTest> aTests,
                                      @NonNull final IXdmNode aNode,
                                      @NonNull final ENodeKind ePrincipal)
  {
    for (final NodeTest aTest : aTests)
      if (NodeTestMatcher.matches (aTest, aNode, ePrincipal))
        return true;
    return false;
  }

  /**
   * A union of attribute and child steps without predicates (see {@link IExpr.StepUnion}) from the
   * context node: the attributes that pass one of the attribute tests, then the children that pass
   * one of the child tests. Same result and errors as evaluating the operands.
   *
   * @return The selected nodes in document order without duplicates.
   * @throws XPathException
   *         XPDY0002 if the context item is absent, XPTY0020 if it is not a node
   */
  @NonNull
  private static XdmSequence _evalStepUnion (final IExpr.@NonNull StepUnion aUnion, @NonNull final DynamicContext aCtx)
  {
    final IXdmItem aItem = _getContextItem (aCtx);
    if (!(aItem instanceof final IXdmNode aNode))
      throw XPathException.of ("XPTY0020", "The context item for an axis step is not a node");
    final XdmSequence.ListBuilder <IXdmNode> aNodes = new XdmSequence.ListBuilder <> ();
    final List <NodeTest> aAttributeTests = aUnion.attributeTests ();
    if (!aAttributeTests.isEmpty ())
    {
      final int nCount = aNode.getAttributeCount ();
      for (int i = 0; i < nCount; ++i)
      {
        final IXdmNode aAttr = aNode.getAttributeAtIndex (i);
        if (_matchesAny (aAttributeTests, aAttr, ENodeKind.ATTRIBUTE))
          aNodes.add (aAttr);
      }
    }
    final List <NodeTest> aChildTests = aUnion.childTests ();
    if (!aChildTests.isEmpty ())
    {
      final int nCount = aNode.getChildCount ();
      for (int i = 0; i < nCount; ++i)
      {
        final IXdmNode aChild = aNode.getChildAtIndex (i);
        if (_matchesAny (aChildTests, aChild, ENodeKind.ELEMENT))
          aNodes.add (aChild);
      }
    }
    return aNodes.build ();
  }

  /**
   * <code>union</code>, <code>intersect</code> and <code>except</code> (XPath 3.1 section 3.4.2)
   * on node identity: both operands must be node sequences, the result is in document order
   * without duplicates.
   */
  @Immutable
  private static final class SetNode extends AbstractCompiledExpr
  {
    private final IExpr.SetExpr.ESetOp m_eOp;
    private final AbstractCompiledExpr m_aLeft;
    private final AbstractCompiledExpr m_aRight;
    // Null if the set expression is no union of single steps
    private final IExpr.StepUnion m_aStepUnion;

    SetNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull SetExpr aExpr)
    {
      super (aExpr);
      m_eOp = aExpr.op ();
      m_aLeft = aC.compile (aExpr.left ());
      m_aRight = aC.compile (aExpr.right ());
      m_aStepUnion = aExpr.stepUnion ();
    }

    /**
     * @throws XPathException
     *         XPTY0004 if an operand holds an item that is not a node
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      // Optimisation for unions of single steps such as "@*|*|comment()": one scan per axis, no sort
      if (m_aStepUnion != null)
        return _evalStepUnion (m_aStepUnion, aCtx);
      final IExpr.SetExpr.ESetOp eOp = m_eOp;
      final XdmSequence aLeft = _checkNodes (m_aLeft.evaluate (aCtx), eOp);
      final XdmSequence aRight = _checkNodes (m_aRight.evaluate (aCtx), eOp);
      if (eOp == IExpr.SetExpr.ESetOp.UNION)
        return NodeSorter.sortAndDeduplicate (XdmSequence.concat (aLeft, aRight));

      final Set <IXdmItem> aRightSet = Collections.newSetFromMap (new IdentityHashMap <> ());
      for (final IXdmItem aItem : aRight)
        aRightSet.add (aItem);
      final boolean bIntersect = eOp == IExpr.SetExpr.ESetOp.INTERSECT;
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final IXdmItem aItem : aLeft)
        if (aRightSet.contains (aItem) == bIntersect)
          aBuilder.add (aItem);
      return NodeSorter.sortAndDeduplicate (aBuilder.build ());
    }
  }

  /** The conditional expression (XPath 3.1 section 3.12). */
  @Immutable
  private static final class IfNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr m_aCondition;
    private final AbstractCompiledExpr m_aThen;
    private final AbstractCompiledExpr m_aElse;

    IfNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull IfExpr aExpr)
    {
      super (aExpr);
      m_aCondition = aC.compile (aExpr.condition ());
      m_aThen = aC.compile (aExpr.thenExpr ());
      m_aElse = aC.compile (aExpr.elseExpr ());
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return m_aCondition.getEbv (aCtx) ? m_aThen.evaluate (aCtx) : m_aElse.evaluate (aCtx);
    }
  }

  /**
   * One binding of a <code>for</code>, <code>let</code>, <code>some</code> or <code>every</code>
   * expression, with its compiled expression.
   *
   * @param name
   *        The variable name.
   * @param type
   *        The declared type, <code>null</code> if none.
   * @param expr
   *        The compiled binding expression.
   * @param slot
   *        The slot of the variable in the frame of the expression.
   */
  @Immutable
  private record CompiledBinding (@NonNull XdmQName name,
                                  @Nullable SeqType type,
                                  @NonNull AbstractCompiledExpr expr,
                                  @NonNull VariableSlot slot)
  {}

  /**
   * @return The frame of the expression's own variables in the context; the frame of the layout of
   *         the slot.
   * @throws IllegalStateException
   *         if the context has no frame of that layout (the evaluation did not start at the root of
   *         the compiled expression)
   */
  @NonNull
  private static VariableFrame _getLocalFrame (@NonNull final DynamicContext aCtx, @NonNull final VariableSlot aSlot)
  {
    final VariableFrame ret = aCtx.getLocalFrame ();
    if (ret == null || ret.getLayout () != aSlot.layout ())
      throw new IllegalStateException ("No frame for the variable $" + aSlot.name ().getEQName ());
    return ret;
  }

  /**
   * @return The value to bind to a variable: unchanged without a declared type, otherwise
   *         converted to the declared type with the function conversion rules.
   * @throws XPathException
   *         XPTY0004 or another conversion error if the value does not match the declared type
   */
  @NonNull
  private static XdmSequence _bindingValue (@NonNull final CompiledBinding aBinding,
                                            @NonNull final XdmSequence aValue,
                                            @NonNull final DynamicContext aCtx)
  {
    if (aBinding.type () == null)
      return aValue;
    return SequenceTypeMatcher.convert (aValue, aBinding.type (), aCtx, "XPTY0004");
  }

  /** The <code>for</code> expression (XPath 3.1 section 3.9). */
  @Immutable
  private static final class ForNode extends AbstractCompiledExpr
  {
    private final CompiledBinding [] m_aBindings;
    private final AbstractCompiledExpr m_aBody;

    ForNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull ForExpr aExpr)
    {
      super (aExpr);
      m_aBindings = aC.compileBindings (aExpr.bindings ());
      m_aBody = aC.compile (aExpr.body ());
      aC.unbind (m_aBindings.length);
    }

    /**
     * The iteration from the binding at <code>nIndex</code> on: the binding sequence is evaluated
     * in the context of the outer variables, and for each of its items in order the variable is
     * bound to that item and the next binding (finally the return expression) is evaluated. The
     * recursion depth is the number of bindings in the expression.
     *
     * @param nIndex
     *        The 0-based index of the binding to iterate; the number of bindings evaluates the
     *        body.
     * @param aOut
     *        Receives the results of the return expression in iteration order.
     */
    private void _evalFrom (final int nIndex, @NonNull final DynamicContext aCtx, final XdmSequence.@NonNull Builder aOut)
    {
      if (nIndex == m_aBindings.length)
      {
        aOut.addAll (m_aBody.evaluate (aCtx));
        return;
      }
      final CompiledBinding aBinding = m_aBindings[nIndex];
      final XdmSequence aSeq = aBinding.expr ().evaluate (aCtx);
      final VariableFrame aFrame = _getLocalFrame (aCtx, aBinding.slot ());
      for (final IXdmItem aItem : aSeq)
      {
        aCtx.checkInterrupt ();
        // The slot is written in place: no context per iteration
        aFrame.set (aBinding.slot (), _bindingValue (aBinding, XdmSequence.of (aItem), aCtx));
        _evalFrom (nIndex + 1, aCtx, aOut);
      }
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      // The focus of the loop for the memo of calls that read it (CallMemo)
      final IXdmItem aOuterLoopFocus = aCtx.setLoopFocus (aCtx.getContextItem ());
      try
      {
        _evalFrom (0, aCtx, aBuilder);
      }
      finally
      {
        aCtx.setLoopFocus (aOuterLoopFocus);
      }
      return aBuilder.build ();
    }
  }

  /**
   * The <code>let</code> expression (XPath 3.1 section 3.10): the bindings are evaluated eagerly
   * in order, each seeing the variables bound before it, then the return expression is evaluated
   * with all of them.
   */
  @Immutable
  private static final class LetNode extends AbstractCompiledExpr
  {
    private final CompiledBinding [] m_aBindings;
    private final AbstractCompiledExpr m_aBody;

    LetNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull LetExpr aExpr)
    {
      super (aExpr);
      m_aBindings = aC.compileBindings (aExpr.bindings ());
      m_aBody = aC.compile (aExpr.body ());
      aC.unbind (m_aBindings.length);
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      for (final CompiledBinding aBinding : m_aBindings)
      {
        final XdmSequence aValue = _bindingValue (aBinding, aBinding.expr ().evaluate (aCtx), aCtx);
        _getLocalFrame (aCtx, aBinding.slot ()).set (aBinding.slot (), aValue);
      }
      return m_aBody.evaluate (aCtx);
    }
  }

  /**
   * The quantified expression <code>some</code>/<code>every</code> (XPath 3.1 section 3.13), over
   * all combinations of the binding items in order.
   */
  @Immutable
  private static final class QuantifiedNode extends AbstractCompiledExpr
  {
    private final boolean m_bEvery;
    private final CompiledBinding [] m_aBindings;
    private final AbstractCompiledExpr m_aSatisfies;

    QuantifiedNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull QuantifiedExpr aExpr)
    {
      super (aExpr);
      m_bEvery = aExpr.every ();
      m_aBindings = aC.compileBindings (aExpr.bindings ());
      m_aSatisfies = aC.compile (aExpr.satisfies ());
      aC.unbind (m_aBindings.length);
    }

    /**
     * The combinations from the binding at <code>nIndex</code> on. <code>some</code> stops at the
     * first combination whose test is true, <code>every</code> at the first one whose test is
     * false, so later combinations and their errors are not evaluated.
     *
     * @return For <code>some</code> <code>false</code>, for <code>every</code> <code>true</code>
     *         if no combination decides it (also for an empty binding sequence).
     */
    private boolean _evalFrom (final int nIndex, @NonNull final DynamicContext aCtx)
    {
      if (nIndex == m_aBindings.length)
        return m_aSatisfies.getEbv (aCtx);
      final CompiledBinding aBinding = m_aBindings[nIndex];
      final boolean bEvery = m_bEvery;
      final XdmSequence aSeq = aBinding.expr ().evaluate (aCtx);
      final VariableFrame aFrame = _getLocalFrame (aCtx, aBinding.slot ());
      for (final IXdmItem aItem : aSeq)
      {
        aCtx.checkInterrupt ();
        // The slot is written in place: no context per combination
        aFrame.set (aBinding.slot (), _bindingValue (aBinding, XdmSequence.of (aItem), aCtx));
        final boolean b = _evalFrom (nIndex + 1, aCtx);
        if (bEvery && !b)
          return false;
        if (!bEvery && b)
          return true;
      }
      return bEvery;
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      // The focus of the loop for the memo of calls that read it (CallMemo)
      final IXdmItem aOuterLoopFocus = aCtx.setLoopFocus (aCtx.getContextItem ());
      try
      {
        return _bool (_evalFrom (0, aCtx));
      }
      finally
      {
        aCtx.setLoopFocus (aOuterLoopFocus);
      }
    }
  }

  /**
   * The simple map operator <code>a ! b ! ...</code> (XPath 3.1 section 3.15): each step is
   * evaluated once per item of the result before it, with that item, its position and the size
   * as focus, and the results are concatenated in order. Unlike a path, nothing is sorted or
   * deduplicated and atomic values are allowed.
   */
  @Immutable
  private static final class SimpleMapNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr [] m_aSteps;

    SimpleMapNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull SimpleMapExpr aExpr)
    {
      super (aExpr);
      m_aSteps = aC.compileAll (aExpr.steps ());
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      XdmSequence aCur = m_aSteps[0].evaluate (aCtx);
      for (int nStep = 1; nStep < m_aSteps.length; ++nStep)
      {
        final AbstractCompiledExpr aStep = m_aSteps[nStep];
        final int nSize = aCur.size ();
        final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
        for (int i = 0; i < nSize; ++i)
        {
          aCtx.checkInterrupt ();
          aBuilder.addAll (aStep.evaluate (aCtx.withFocus (aCur.get (i), i + 1, nSize)));
        }
        aCur = aBuilder.build ();
      }
      return aCur;
    }
  }

  /** <code>instance of</code> (XPath 3.1 section 3.14.1). */
  @Immutable
  private static final class InstanceOfNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr m_aOperand;
    private final SeqType m_aType;

    InstanceOfNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull InstanceOfExpr aExpr)
    {
      super (aExpr);
      m_aOperand = aC.compile (aExpr.operand ());
      m_aType = aExpr.type ();
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return _bool (SequenceTypeMatcher.matches (m_aOperand.evaluate (aCtx), m_aType));
    }
  }

  /**
   * <code>treat as</code> (XPath 3.1 section 3.14.5): the value is returned unchanged if it
   * matches the sequence type, without any conversion.
   */
  @Immutable
  private static final class TreatNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr m_aOperand;
    private final SeqType m_aType;

    TreatNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull TreatExpr aExpr)
    {
      super (aExpr);
      m_aOperand = aC.compile (aExpr.operand ());
      m_aType = aExpr.type ();
    }

    /**
     * @throws XPathException
     *         XPDY0050 if the value does not match the type
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final XdmSequence aValue = m_aOperand.evaluate (aCtx);
      if (!SequenceTypeMatcher.matches (aValue, m_aType))
        throw XPathException.of ("XPDY0050", "The value does not match the type " + SequenceTypeMatcher.getAsString (m_aType));
      return aValue;
    }
  }

  /**
   * <code>cast as</code> (XPath 3.1 section 3.14.2) or <code>castable as</code> (section 3.14.3),
   * delegated to {@link XPathOperators}: the cast value, or for <code>castable as</code> a single
   * boolean.
   */
  @Immutable
  private static final class CastNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr m_aOperand;
    private final SeqType m_aType;
    private final boolean m_bCastable;

    CastNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull CastExpr aExpr)
    {
      super (aExpr);
      m_aOperand = aC.compile (aExpr.operand ());
      m_aType = aExpr.type ();
      m_bCastable = aExpr.castable ();
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final XdmSequence aValue = m_aOperand.evaluate (aCtx);
      if (m_bCastable)
        return _bool (XPathOperators.castable (aValue, m_aType, aCtx));
      return XPathOperators.cast (aValue, m_aType, aCtx);
    }
  }

  /**
   * The map constructor <code>map { k : v, ... }</code> (XPath 3.1 section 3.11.1.1): the entries
   * are evaluated in order, each key atomised to exactly one atomic value.
   * <p>
   * A map whose keys are literals and whose values are literals or named function references
   * (<code>map { 'UP' : rm:up#2, ... }</code>, a dispatch table) has the same value for the same
   * resolved functions, so its last value is kept with the definitions the references resolved to
   * and returned while every reference resolves to the same definition again. A focus-dependent
   * function or a failed construction keeps nothing.
   */
  @ThreadSafe
  private static final class MapConstructorNode extends AbstractCompiledExpr
  {
    /** A built map with the definitions its function references resolved to, in value order. */
    @Immutable
    private record ConstantMap (@NonNull FunctionDef [] defs, @NonNull XdmSequence map)
    {}

    private final AbstractCompiledExpr [] m_aKeys;
    private final AbstractCompiledExpr [] m_aValues;
    // The function references among the values, in value order; null if the map is not built only from literals
    // and function references
    private final NamedFunctionRefNode [] m_aConstantRefs;
    private volatile ConstantMap m_aConstant;

    MapConstructorNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull MapConstructorExpr aExpr)
    {
      super (aExpr);
      m_aKeys = aC.compileAll (aExpr.keys ());
      m_aValues = aC.compileAll (aExpr.values ());
      m_aConstantRefs = _getConstantRefs (m_aKeys, m_aValues);
    }

    /**
     * @return The function references among the values if every key is a literal and every value a
     *         literal or a named function reference, otherwise <code>null</code>.
     */
    @Nullable
    private static NamedFunctionRefNode [] _getConstantRefs (@NonNull final AbstractCompiledExpr [] aKeys,
                                                             @NonNull final AbstractCompiledExpr [] aValues)
    {
      for (final AbstractCompiledExpr aKey : aKeys)
        if (!(aKey instanceof LiteralNode))
          return null;
      final List <NamedFunctionRefNode> aRefs = new ArrayList <> ();
      for (final AbstractCompiledExpr aValue : aValues)
      {
        if (aValue instanceof final NamedFunctionRefNode aRef)
          aRefs.add (aRef);
        else
          if (!(aValue instanceof LiteralNode))
            return null;
      }
      return aRefs.toArray (new NamedFunctionRefNode [0]);
    }

    /**
     * @return The definitions the function references resolve to now, or <code>null</code> if one of
     *         them is focus-dependent (its item captures the context).
     * @throws XPathException
     *         XPST0017 if a reference names no function
     */
    @Nullable
    private FunctionDef [] _resolveConstantRefs (@NonNull final DynamicContext aCtx)
    {
      final FunctionDef [] ret = new FunctionDef [m_aConstantRefs.length];
      for (int i = 0; i < ret.length; ++i)
      {
        final FunctionDef aDef = m_aConstantRefs[i].getFunctionDef (aCtx);
        if (aDef.isFocusDependent ())
          return null;
        ret[i] = aDef;
      }
      return ret;
    }

    /**
     * @return <code>true</code> if every function reference resolves to the definition it resolved
     *         to when the kept map was built (by identity; none of those is focus-dependent).
     * @throws XPathException
     *         XPST0017 if a reference names no function
     */
    private boolean _isKeptFor (@NonNull final ConstantMap aKept, @NonNull final DynamicContext aCtx)
    {
      final FunctionDef [] aKeptDefs = aKept.defs ();
      for (int i = 0; i < aKeptDefs.length; ++i)
        if (m_aConstantRefs[i].getFunctionDef (aCtx) != aKeptDefs[i])
          return false;
      return true;
    }

    /**
     * Build the map: the entries in order, each key atomised to exactly one atomic value.
     *
     * @throws XPathException
     *         XPTY0004 for a key that is not exactly one atomic value; XQDY0137 for a duplicate key
     */
    @NonNull
    private XdmSequence _build (@NonNull final DynamicContext aCtx)
    {
      final XdmMap.Builder aMap = new XdmMap.Builder (m_aKeys.length);
      for (int i = 0; i < m_aKeys.length; ++i)
      {
        final XdmSequence aKeySeq = XPathOperators.atomize (m_aKeys[i].evaluate (aCtx));
        if (!aKeySeq.isSingleton ())
          throw XPathException.type ("A map key must be a single atomic value, but it has " + aKeySeq.size () + " items");
        final XdmAtomic aKey = (XdmAtomic) aKeySeq.get (0);
        final XdmSequence aValue = m_aValues[i].evaluate (aCtx);
        if (!aMap.putIfAbsent (aKey, aValue))
          throw XPathException.of ("XQDY0137", "Duplicate key in a map constructor");
      }
      return XdmSequence.of (aMap.build ());
    }

    /**
     * @return A single map.
     * @throws XPathException
     *         XPTY0004 if a key is not exactly one atomic value after atomisation; XQDY0137 if two
     *         keys are the same key (<code>op:same-key</code>); XPST0017 if a function reference
     *         names no function
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      if (m_aConstantRefs == null)
        return _build (aCtx);
      // Optimisation: the map of the same resolved functions is the same value, so it is not built again
      final ConstantMap aKept = m_aConstant;
      if (aKept != null && _isKeptFor (aKept, aCtx))
        return aKept.map ();
      final FunctionDef [] aDefs = _resolveConstantRefs (aCtx);
      final XdmSequence ret = _build (aCtx);
      if (aDefs != null)
        m_aConstant = new ConstantMap (aDefs, ret);
      return ret;
    }
  }

  /**
   * The array constructors (XPath 3.1 section 3.11.2): the square form <code>[a, b]</code> makes
   * the value of each expression one member; the curly form <code>array { a, b }</code> makes each
   * item of the concatenated values one member.
   */
  @Immutable
  private static final class ArrayConstructorNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr [] m_aItems;
    private final boolean m_bCurly;

    ArrayConstructorNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull ArrayConstructorExpr aExpr)
    {
      super (aExpr);
      m_aItems = aC.compileAll (aExpr.items ());
      m_bCurly = aExpr.curly ();
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final ICommonsList <XdmSequence> aMembers = new CommonsArrayList <> ();
      for (final AbstractCompiledExpr aItemExpr : m_aItems)
      {
        final XdmSequence aValue = aItemExpr.evaluate (aCtx);
        if (m_bCurly)
        {
          for (final IXdmItem aItem : aValue)
            aMembers.add (XdmSequence.of (aItem));
        }
        else
          aMembers.add (aValue);
      }
      return XdmSequence.of (XdmArray.of (aMembers));
    }
  }

  /**
   * The member of an array at a 1-based position given as an atomic key, as an array lookup or a
   * call of an array as function does it. An <code>xs:untypedAtomic</code> key is cast to
   * <code>xs:integer</code> first.
   *
   * @return The member (a sequence of any length).
   * @throws XPathException
   *         XPTY0004 if the key is not of an integer type; FORG0001 for an untyped key that is no
   *         valid integer; FOAY0001 if the position is less than 1 or greater than the size
   */
  @NonNull
  private static XdmSequence _getArrayMember (@NonNull final XdmArray aArray,
                                              @NonNull final XdmAtomic aKey,
                                              @NonNull final DynamicContext aCtx)
  {
    XdmAtomic aIndex = aKey;
    if (aIndex.getType () == EAtomicType.UNTYPED_ATOMIC)
      aIndex = AtomicCaster.castFromString (aIndex.getString (), EAtomicType.INTEGER, aCtx.getStaticContext ());
    if (!aIndex.getType ().isIntegerFamily ())
      throw XPathException.type ("An array index must be an xs:integer, not " + aIndex.getType ().getQualifiedName ());
    final BigInteger aBigIndex = aIndex.getBigInteger ();
    if (aBigIndex.signum () <= 0 || aBigIndex.compareTo (BigInteger.valueOf (aArray.size ())) > 0)
      throw XPathException.of ("FOAY0001", "Array index " + aBigIndex + " is out of bounds (1.." + aArray.size () + ")");
    return aArray.get (aBigIndex.intValue () - 1);
  }

  /**
   * The lookup operator (XPath 3.1 section 3.11.3): postfix <code>E?K</code> with a base
   * expression, unary <code>?K</code> on the context item without one. The key specifier is
   * atomised once; a wildcard (<code>?*</code>, no key expression) selects all values of a map (in
   * the map's iteration order) or all members of an array (in order). For each base item in order,
   * each key is looked up: a key absent from a map contributes nothing, an array is indexed with
   * {@link XPathEvaluator#_getArrayMember(XdmArray, XdmAtomic, DynamicContext)}. The results are
   * concatenated.
   */
  @Immutable
  private static final class LookupNode extends AbstractCompiledExpr
  {
    // Null for a unary lookup
    private final AbstractCompiledExpr m_aBase;
    // Null for a wildcard
    private final AbstractCompiledExpr m_aKey;

    LookupNode (@NonNull final ExprCompiler aC, final IExpr.@NonNull LookupExpr aExpr)
    {
      super (aExpr);
      m_aBase = aExpr.base () == null ? null : aC.compile (aExpr.base ());
      m_aKey = aExpr.key () == null ? null : aC.compile (aExpr.key ());
    }

    /**
     * @throws XPathException
     *         XPTY0004 if a base item is neither a map nor an array; XPDY0002 for a unary lookup
     *         without context item; the array index errors
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final XdmSequence aBase = m_aBase == null ? XdmSequence.of (_getContextItem (aCtx)) : m_aBase.evaluate (aCtx);
      final XdmSequence aKeys = m_aKey == null ? null : XPathOperators.atomize (m_aKey.evaluate (aCtx));
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final IXdmItem aItem : aBase)
      {
        if (aItem instanceof final XdmMap aMap)
        {
          if (aKeys == null)
          {
            for (final XdmMap.Entry aEntry : aMap.getAllEntries ())
              aBuilder.addAll (aEntry.value ());
          }
          else
            for (final IXdmItem aKey : aKeys)
            {
              final XdmSequence aValue = aMap.get ((XdmAtomic) aKey);
              if (aValue != null)
                aBuilder.addAll (aValue);
            }
        }
        else
          if (aItem instanceof final XdmArray aArray)
          {
            if (aKeys == null)
            {
              for (int i = 0; i < aArray.size (); ++i)
                aBuilder.addAll (aArray.get (i));
            }
            else
              for (final IXdmItem aKey : aKeys)
                aBuilder.addAll (_getArrayMember (aArray, (XdmAtomic) aKey, aCtx));
          }
          else
            throw XPathException.type ("The lookup operator requires a map or an array");
      }
      return aBuilder.build ();
    }
  }

  /**
   * A variable reference to a variable the host binds by slot (see
   * {@link StaticContext#declareFrameVariable(VariableSlot)}): the value is read from the frame of
   * the dynamic context, without a lookup by name.
   */
  @Immutable
  private static final class FrameVarRefNode extends AbstractCompiledExpr
  {
    private final VariableSlot m_aSlot;

    FrameVarRefNode (final IExpr.@NonNull VarRefExpr aExpr, @NonNull final VariableSlot aSlot)
    {
      super (aExpr);
      m_aSlot = aSlot;
    }

    /**
     * @throws XPathException
     *         XPDY0002 if the context has no frame of the layout of the slot (the variable is not
     *         bound)
     */
    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      final VariableFrame aFrame = aCtx.getFrame ();
      if (aFrame == null || aFrame.getLayout () != m_aSlot.layout ())
        throw XPathException.noContext ("No value is bound to the variable $" + m_aSlot.name ().getEQName ());
      return aFrame.get (m_aSlot.index ());
    }
  }

  /**
   * A variable reference to a variable the expression binds itself (<code>for</code>,
   * <code>let</code>, quantifiers, inline function parameters): the value is read from the frame of
   * the expression (see {@link DynamicContext#getLocalFrame()}), without a lookup by name.
   */
  @Immutable
  private static final class LocalVarRefNode extends AbstractCompiledExpr
  {
    private final VariableSlot m_aSlot;

    LocalVarRefNode (final IExpr.@NonNull VarRefExpr aExpr, @NonNull final VariableSlot aSlot)
    {
      super (aExpr);
      m_aSlot = aSlot;
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return _getLocalFrame (aCtx, m_aSlot).get (m_aSlot.index ());
    }
  }

  /**
   * The root of a compiled expression that binds variables itself: every evaluation gets a new
   * frame for them, so that an evaluation that starts again before the first one ended (a
   * recursive function call that evaluates the same expression) does not see or change its
   * bindings.
   */
  @Immutable
  private static final class LocalFrameNode extends AbstractCompiledExpr
  {
    private final AbstractCompiledExpr m_aRoot;
    private final VariableFrameLayout m_aLayout;

    LocalFrameNode (@NonNull final AbstractCompiledExpr aRoot, @NonNull final VariableFrameLayout aLayout)
    {
      super (aRoot.getExpression ());
      m_aRoot = aRoot;
      m_aLayout = aLayout;
    }

    @NonNull
    private DynamicContext _withFrame (@NonNull final DynamicContext aCtx)
    {
      return aCtx.withNewLocalFrame (m_aLayout);
    }

    @Override
    @NonNull
    XdmSequence evaluate (@NonNull final DynamicContext aCtx)
    {
      return m_aRoot.evaluate (_withFrame (aCtx));
    }

    @Override
    @Nullable
    Boolean tryEarlyEbv (@NonNull final DynamicContext aCtx, final boolean bPredicate)
    {
      final DynamicContext aFrameCtx = _withFrame (aCtx);
      final Boolean ret = m_aRoot.tryEarlyEbv (aFrameCtx, bPredicate);
      if (ret != null || bPredicate)
        return ret;
      // The complete evaluation with the same frame, instead of a second one from getEbv
      return Boolean.valueOf (Atomizer.getEffectiveBooleanValue (m_aRoot.evaluate (aFrameCtx)));
    }

    @Override
    boolean exists (@NonNull final DynamicContext aCtx)
    {
      return m_aRoot.exists (_withFrame (aCtx));
    }
  }

  /** A variable the expression binds itself, in the scope of the compilation. */
  @Immutable
  private record LocalBinding (@NonNull XdmQName name, @NonNull VariableSlot slot)
  {}

  /**
   * The start of the body of an inline function in the scope of the compilation: the body has its
   * own layout, and the variables of the enclosing expression it references are captured by name
   * when the function item is created.
   */
  @NotThreadSafe
  private static final class FunctionBoundary
  {
    // The layout of the enclosing scope, restored at the end of the body; null if none yet
    private final VariableFrameLayout m_aOuterLayout;
    private final VariableFrameLayout m_aLayout = new VariableFrameLayout ();
    // The variables of the directly enclosing scope that the body references, by name
    private final Map <XdmQName, VariableSlot> m_aCaptures = new LinkedHashMap <> ();

    FunctionBoundary (@Nullable final VariableFrameLayout aOuterLayout)
    {
      m_aOuterLayout = aOuterLayout;
    }
  }

  /**
   * The state of one compilation: the static context whose frame variables the references read by
   * slot (if any), and the variables the expression itself binds around the part being compiled
   * (<code>for</code>, <code>let</code>, quantifiers, inline function parameters), each with its
   * slot in the frame of the expression or of the inline function body around it. They shadow the
   * variables of the static context.
   */
  @NotThreadSafe
  private static final class ExprCompiler
  {
    private final StaticContext m_aStatic;
    // LocalBinding and FunctionBoundary entries around the current position, innermost last
    private final ICommonsList <Object> m_aScope = new CommonsArrayList <> ();
    // The layout of the current scope (expression or inline function body); null until the first
    // binding of the expression
    private VariableFrameLayout m_aLayout;

    ExprCompiler (@Nullable final StaticContext aStatic)
    {
      m_aStatic = aStatic;
    }

    /** @return The layout of the expression's own variables, <code>null</code> for none. */
    @Nullable
    VariableFrameLayout getRootLayout ()
    {
      return m_aLayout;
    }

    /**
     * Bring a variable the expression binds into scope.
     *
     * @return Its slot in the frame of the current scope.
     */
    @NonNull
    VariableSlot bindLocal (@NonNull final XdmQName aName)
    {
      if (m_aLayout == null)
        m_aLayout = new VariableFrameLayout ();
      final VariableSlot ret = m_aLayout.allocateSlot (aName);
      m_aScope.add (new LocalBinding (aName, ret));
      return ret;
    }

    void unbind (final int nCount)
    {
      for (int i = 0; i < nCount; ++i)
        m_aScope.removeLast ();
    }

    @NonNull
    FunctionBoundary enterFunction ()
    {
      final FunctionBoundary ret = new FunctionBoundary (m_aLayout);
      m_aScope.add (ret);
      m_aLayout = ret.m_aLayout;
      return ret;
    }

    void exitFunction (@NonNull final FunctionBoundary aBoundary)
    {
      // Removes the bindings of the body (the parameters) and the boundary itself
      Object aRemoved;
      do
      {
        aRemoved = m_aScope.removeLast ();
      } while (aRemoved != aBoundary);
      m_aLayout = aBoundary.m_aOuterLayout;
    }

    /**
     * Resolve a reference against the variables the expression binds.
     *
     * @param bCapture
     *        <code>true</code> to record a variable of an enclosing scope that an inline function
     *        body references as a capture of the outermost body crossed
     * @return The slot to read from the frame of the current scope, <code>null</code> if the name
     *         is not bound by the expression (or only outside an inline function body around the
     *         position, where it is captured by name)
     */
    @Nullable
    private VariableSlot _resolveLocal (@NonNull final XdmQName aName, final boolean bCapture)
    {
      FunctionBoundary aCrossed = null;
      for (int i = m_aScope.size () - 1; i >= 0; --i)
      {
        final Object aEntry = m_aScope.get (i);
        if (aEntry instanceof final FunctionBoundary aBoundary)
          aCrossed = aBoundary;
        else
          if (aEntry instanceof final LocalBinding aBinding && aBinding.name ().equals (aName))
          {
            if (aCrossed == null)
              return aBinding.slot ();
            if (bCapture)
              aCrossed.m_aCaptures.putIfAbsent (aName, aBinding.slot ());
            return null;
          }
      }
      return null;
    }

    private boolean _isBoundByExpression (@NonNull final XdmQName aName)
    {
      for (final Object aEntry : m_aScope)
        if (aEntry instanceof final LocalBinding aBinding && aBinding.name ().equals (aName))
          return true;
      return false;
    }

    /**
     * @return The slot a reference to the name reads from the frame of the host at the current
     *         position, or <code>null</code> if the expression binds the name itself or the static
     *         context has no slot for it.
     */
    @Nullable
    VariableSlot getFrameVariable (@NonNull final XdmQName aName)
    {
      if (m_aStatic == null || _isBoundByExpression (aName))
        return null;
      return m_aStatic.getFrameVariable (aName);
    }

    /**
     * @return The slot whose binding identifies the value of the name at the current position for
     *         the memo ({@link CallMemo}): of the expression's frame or of the host frame;
     *         <code>null</code> if the name is bound by name.
     */
    @Nullable
    VariableSlot getTokenSlot (@NonNull final XdmQName aName)
    {
      if (_isBoundByExpression (aName))
        return _resolveLocal (aName, false);
      return getFrameVariable (aName);
    }

    @NonNull
    AbstractCompiledExpr [] compileAll (@NonNull final List <IExpr> aExprs)
    {
      if (aExprs.isEmpty ())
        return NO_EXPRS;
      final AbstractCompiledExpr [] ret = new AbstractCompiledExpr [aExprs.size ()];
      for (int i = 0; i < ret.length; ++i)
        ret[i] = compile (aExprs.get (i));
      return ret;
    }

    /**
     * Compile the bindings of a <code>for</code>, <code>let</code> or quantified expression, each
     * seeing the ones before it and getting its own slot. The names stay bound for the body; the
     * caller unbinds them afterwards.
     */
    @NonNull
    CompiledBinding [] compileBindings (@NonNull final List <IExpr.Binding> aBindings)
    {
      final CompiledBinding [] ret = new CompiledBinding [aBindings.size ()];
      for (int i = 0; i < ret.length; ++i)
      {
        final IExpr.Binding aBinding = aBindings.get (i);
        final AbstractCompiledExpr aExpr = compile (aBinding.expr ());
        ret[i] = new CompiledBinding (aBinding.name (), aBinding.type (), aExpr, bindLocal (aBinding.name ()));
      }
      return ret;
    }

    @NonNull
    private AbstractCompiledExpr _compileVarRef (final IExpr.@NonNull VarRefExpr aExpr)
    {
      final XdmQName aName = aExpr.name ();
      if (_isBoundByExpression (aName))
      {
        final VariableSlot aLocal = _resolveLocal (aName, true);
        // Captured by an inline function body: bound by name in its closure
        return aLocal != null ? new LocalVarRefNode (aExpr, aLocal) : new VarRefNode (aExpr);
      }
      final VariableSlot aSlot = getFrameVariable (aName);
      return aSlot != null ? new FrameVarRefNode (aExpr, aSlot) : new VarRefNode (aExpr);
    }

    @NonNull
    AbstractCompiledExpr compile (@NonNull final IExpr aExpr)
    {
      return switch (aExpr)
      {
        case final IExpr.LiteralExpr e -> new LiteralNode (e);
        case final IExpr.VarRefExpr e -> _compileVarRef (e);
        case final IExpr.ContextItemExpr e -> new ContextItemNode (e);
        case final IExpr.FunctionCallExpr e -> new FunctionCallNode (this, e);
        case final IExpr.PlaceholderExpr e -> new PlaceholderNode (e);
        case final IExpr.NamedFunctionRefExpr e -> new NamedFunctionRefNode (e);
        case final IExpr.InlineFunctionExpr e -> new InlineFunctionNode (this, e);
        case final IExpr.DynamicCallExpr e -> new DynamicCallNode (this, e);
        case final IExpr.SequenceExpr e -> new SequenceNode (this, e);
        case final IExpr.BinaryExpr e -> new BinaryNode (this, e);
        case final IExpr.UnaryExpr e -> new UnaryNode (this, e);
        case final IExpr.ComparisonExpr e -> new ComparisonNode (this, e);
        case final IExpr.SetExpr e -> new SetNode (this, e);
        case final IExpr.RangeExpr e -> new RangeNode (this, e);
        case final IExpr.StringConcatExpr e -> new StringConcatNode (this, e);
        case final IExpr.IfExpr e -> new IfNode (this, e);
        case final IExpr.ForExpr e -> new ForNode (this, e);
        case final IExpr.LetExpr e -> new LetNode (this, e);
        case final IExpr.QuantifiedExpr e -> new QuantifiedNode (this, e);
        case final IExpr.SimpleMapExpr e -> new SimpleMapNode (this, e);
        case final IExpr.PathExpr e -> new PathNode (this, e);
        case final IExpr.FilterExpr e -> new FilterNode (this, e);
        case final IExpr.InstanceOfExpr e -> new InstanceOfNode (this, e);
        case final IExpr.TreatExpr e -> new TreatNode (this, e);
        case final IExpr.CastExpr e -> new CastNode (this, e);
        case final IExpr.MapConstructorExpr e -> new MapConstructorNode (this, e);
        case final IExpr.ArrayConstructorExpr e -> new ArrayConstructorNode (this, e);
        case final IExpr.LookupExpr e -> new LookupNode (this, e);
      };
    }
  }

  /**
   * Compile with the recursion of the expression nesting; a nesting the Java stack of the calling
   * thread cannot take is XPDY0130, as in the evaluation.
   */
  @NonNull
  private static AbstractCompiledExpr _compile (@NonNull final ExprCompiler aCompiler, @NonNull final IExpr aExpr)
  {
    try
    {
      final AbstractCompiledExpr ret = aCompiler.compile (aExpr);
      final VariableFrameLayout aLayout = aCompiler.getRootLayout ();
      // Only an expression that binds variables itself needs a frame per evaluation
      return aLayout != null && aLayout.getSize () > 0 ? new LocalFrameNode (ret, aLayout) : ret;
    }
    catch (final StackOverflowError ex)
    {
      throw new XPathException ("XPDY0130", "The expression is nested too deeply", ex);
    }
  }

  /**
   * Compile an expression into a tree of evaluation nodes, one per expression node. The result
   * keeps no state of a run: compile once and evaluate as often as needed, also concurrently.
   * The variables the expression binds itself (<code>for</code>, <code>let</code>, quantifiers,
   * inline function parameters) are read by slot from a frame created per evaluation (inline
   * function bodies: per call); other variable references are looked up by name.
   *
   * @param aExpr
   *        Parsed expression.
   * @return The compiled expression. Never <code>null</code>.
   * @throws XPathException
   *         XPDY0130 if the expression is nested too deeply for the stack of the calling thread
   */
  @NonNull
  public static AbstractCompiledExpr compile (@NonNull final IExpr aExpr)
  {
    return _compile (new ExprCompiler (null), aExpr);
  }

  /**
   * Compile an expression as {@link #compile(IExpr)} does, reading the variables that the static
   * context declares with a slot ({@link StaticContext#declareFrameVariable(VariableSlot)}) from
   * the {@link VariableFrame} of the dynamic context, unless the expression binds the name itself.
   * The static context must be the one the expression was parsed with.
   *
   * @param aExpr
   *        Parsed expression.
   * @param aStatic
   *        The static context of the expression.
   * @return The compiled expression. Never <code>null</code>.
   */
  @NonNull
  public static AbstractCompiledExpr compile (@NonNull final IExpr aExpr, @NonNull final StaticContext aStatic)
  {
    return _compile (new ExprCompiler (aStatic), aExpr);
  }

  /**
   * @param aExpr
   *        Compiled expression.
   * @param aCtx
   *        Dynamic context.
   * @return The result sequence; node sequences produced by path expressions are in document order
   *         without duplicates.
   * @throws com.helger.phoxtrot.xpath.XPathException
   *         for dynamic and type errors.
   */
  @NonNull
  public static XdmSequence evaluate (@NonNull final AbstractCompiledExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    try
    {
      return aExpr.evaluate (aCtx);
    }
    catch (final StackOverflowError ex)
    {
      throw new XPathException ("XPDY0130", "The evaluation is nested too deeply", ex);
    }
  }

  /**
   * The effective boolean value of an expression (XPath 3.1 section 2.4.3), for hosts that need
   * one (<code>xsl:if</code>, <code>xsl:when</code>, assertions). Paths and filter expressions stop
   * at the first decisive item (section 2.3.4), so a dynamic error in a part that does not change
   * the result is not raised; other expressions are evaluated completely.
   *
   * @param aExpr
   *        Compiled expression.
   * @param aCtx
   *        Dynamic context.
   * @return The effective boolean value.
   * @throws com.helger.phoxtrot.xpath.XPathException
   *         for dynamic and type errors, FORG0006 if the effective boolean value is not defined.
   */
  public static boolean evaluateEffectiveBooleanValue (@NonNull final AbstractCompiledExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    try
    {
      return aExpr.getEbv (aCtx);
    }
    catch (final StackOverflowError ex)
    {
      throw new XPathException ("XPDY0130", "The evaluation is nested too deeply", ex);
    }
  }

  /**
   * Compile and evaluate an expression once. Callers that evaluate an expression more than once
   * compile it with {@link #compile(IExpr)} and use
   * {@link #evaluate(AbstractCompiledExpr, DynamicContext)}.
   *
   * @param aExpr
   *        Parsed expression.
   * @param aCtx
   *        Dynamic context.
   * @return The result sequence.
   * @throws com.helger.phoxtrot.xpath.XPathException
   *         for dynamic and type errors.
   */
  @NonNull
  public static XdmSequence evaluate (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    return evaluate (compile (aExpr), aCtx);
  }

  /**
   * Compile an expression and take its effective boolean value once (see
   * {@link #evaluateEffectiveBooleanValue(AbstractCompiledExpr, DynamicContext)}).
   *
   * @param aExpr
   *        Parsed expression.
   * @param aCtx
   *        Dynamic context.
   * @return The effective boolean value.
   * @throws com.helger.phoxtrot.xpath.XPathException
   *         for dynamic and type errors, FORG0006 if the effective boolean value is not defined.
   */
  public static boolean evaluateEffectiveBooleanValue (@NonNull final IExpr aExpr, @NonNull final DynamicContext aCtx)
  {
    return evaluateEffectiveBooleanValue (compile (aExpr), aCtx);
  }

  /**
   * @param aItem
   *        Any item.
   * @return The arity if the item is a function item (1 for maps and arrays), -1 otherwise.
   */
  public static int getFunctionArity (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmFunction aFunc)
      return aFunc.getArity ();
    if (aItem instanceof XdmMap || aItem instanceof XdmArray)
      return 1;
    return -1;
  }

  /**
   * @param aItem
   *        A function, map or array.
   * @return The item as {@link IXdmFunction}; maps and arrays are wrapped.
   * @throws XPathException
   *         XPTY0004 for other items.
   */
  @NonNull
  public static IXdmFunction asFunction (@NonNull final IXdmItem aItem)
  {
    if (aItem instanceof final IXdmFunction aFunc)
      return aFunc;
    if (aItem instanceof XdmMap || aItem instanceof XdmArray)
      return new MapArrayFunctionItem (aItem);
    throw XPathException.type ("The item is not a function");
  }

  /**
   * Call a function item dynamically: check the arity, apply the function conversion rules to the
   * arguments, invoke the function within
   * {@link DynamicContext#enterCall()}/{@link DynamicContext#leaveCall()}, and convert the result
   * to the declared return type. Maps and arrays are functions of arity one. This is the entry
   * point for higher-order functions in other packages.
   *
   * @param aCtx
   *        The caller's dynamic context.
   * @param aFuncItem
   *        A function, map or array.
   * @param aArgs
   *        The arguments.
   * @return The result.
   * @throws XPathException
   *         XPTY0004 for a non-function or an arity mismatch, FOAY0001 for an array index out of
   *         bounds.
   */
  @NonNull
  public static XdmSequence callFunction (@NonNull final DynamicContext aCtx,
                                          @NonNull final IXdmItem aFuncItem,
                                          @NonNull final List <XdmSequence> aArgs)
  {
    final int nArity = getFunctionArity (aFuncItem);
    if (nArity < 0)
      throw XPathException.type ("The item is not a function");
    if (aArgs.size () != nArity)
      throw XPathException.type ("A function of arity " + nArity + " is called with " + aArgs.size () + " arguments");

    if (aFuncItem instanceof final XdmMap aMap)
    {
      final XdmSequence aKey = SequenceTypeMatcher.convert (aArgs.get (0),
                                                            SequenceTypeMatcher.ANY_ATOMIC_ONE,
                                                            aCtx,
                                                            "XPTY0004");
      final XdmSequence ret = aMap.get ((XdmAtomic) aKey.get (0));
      return ret == null ? XdmSequence.EMPTY : ret;
    }
    if (aFuncItem instanceof final XdmArray aArray)
    {
      final XdmSequence aIndex = SequenceTypeMatcher.convert (aArgs.get (0),
                                                              SequenceTypeMatcher.INTEGER_ONE,
                                                              aCtx,
                                                              "XPTY0004");
      return _getArrayMember (aArray, (XdmAtomic) aIndex.get (0), aCtx);
    }

    final IXdmFunction aFunc = (IXdmFunction) aFuncItem;
    final List <SeqType> aParamTypes = aFunc.getParamTypes ();
    final ICommonsList <XdmSequence> aConverted = new CommonsArrayList <> (nArity);
    for (int i = 0; i < nArity; ++i)
      aConverted.add (XPathOperators.convertArgument (aArgs.get (i), aParamTypes.get (i), aCtx, aFunc.getName (), i));
    aCtx.checkInterrupt ();
    final XdmSequence aResult;
    aCtx.enterCall ();
    try
    {
      aResult = aFunc.call (aCtx, aConverted);
    }
    finally
    {
      aCtx.leaveCall ();
    }
    if (aFunc instanceof final AbstractFunctionItem aOwn && aOwn.isResultTrusted ())
      return aResult;
    return SequenceTypeMatcher.convert (aResult, aFunc.getReturnType (), aCtx, "XPTY0004");
  }
}

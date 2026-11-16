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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.EAxis;
import com.helger.phoxtrot.xpath.ast.EBinaryOp;
import com.helger.phoxtrot.xpath.ast.ENodeTestKind;
import com.helger.phoxtrot.xpath.ast.EOccurrence;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.eval.SequenceTypeMatcher;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.value.EAtomicType;

/**
 * Type inference and type checks of XPath 3.1's optional Static Typing Feature
 * (section 4.1). With the feature, a processor must raise a type error during
 * static analysis "if the inferred static type of an expression is not subsumed
 * by the required type of the context where the expression is used" (section
 * 2.2.3.1); the inference rules are implementation-dependent in XPath 3.1.
 * <p>
 * The inferred type of an expression ({@link StaticType}) keeps a short list of
 * alternative item types and an occurrence, so that the branches of an
 * <code>if</code> keep their own types. Every expression kind gets a type; a
 * construct that is not reasoned about gets <code>item()*</code>, which is
 * pessimistic, as the feature requires. The checks are the required types of
 * the arguments of static function calls after the function conversion rules
 * (section 3.1.5.2): atomisation where an atomic type is required, the
 * conversion of <code>xs:untypedAtomic</code>, numeric and URI promotion,
 * function coercion checked on the parameter and return types (an inline
 * function contributes the return type inferred from its body), and the
 * subtype judgement of section 2.5.6 otherwise.
 * The zero-argument forms that take the context item as their argument
 * (<code>fn:has-children()</code> and others) check it against the parameter
 * of the one-argument form.
 * <p>
 * The context item has the static type the static context declares
 * ({@link StaticContext#setContextItemStaticType(ItemType)}), else
 * <code>item()</code>; a variable the type it was declared with
 * ({@link StaticContext#declareVariable(XdmQName, SeqType)}), else
 * <code>item()*</code>. A node atomises to
 * <code>xs:untypedAtomic</code> unless the static context has in-scope schema
 * definitions or the node test names a type; then to
 * <code>xs:anyAtomicType</code>. The traversal follows the nesting of the AST,
 * which the parser limits to {@link XPathParserImpl#MAX_DEPTH} levels, and
 * runs inside the parser's stack guard.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class XPathStaticTyping
{
  /**
   * The occurrence "more than one". Occurrences are kept as a minimum (0 or 1)
   * and a maximum (0, 1 or this).
   */
  private static final int MANY = 2;

  /**
   * At most this many alternatives are kept; beyond that they collapse into
   * their common supertype.
   */
  private static final int MAX_ALTERNATIVES = 8;

  /**
   * The functions whose zero-argument form is the one-argument form applied to
   * the context item (F&amp;O 3.1), by local name in the <code>fn</code>
   * namespace.
   */
  private static final Set <String> CONTEXT_ARGUMENT_FUNCTIONS = Set.of ("base-uri",
                                                                         "data",
                                                                         "document-uri",
                                                                         "generate-id",
                                                                         "has-children",
                                                                         "local-name",
                                                                         "name",
                                                                         "namespace-uri",
                                                                         "nilled",
                                                                         "node-name",
                                                                         "number",
                                                                         "path",
                                                                         "root",
                                                                         "string");

  /**
   * An inferred static type: alternative item types and an occurrence. No
   * alternatives and a maximum of 0 is <code>empty-sequence()</code>.
   *
   * @param items
   *        The alternative item types, without one that another one subsumes.
   * @param min
   *        The minimum number of items, 0 or 1.
   * @param max
   *        The maximum number of items, 0, 1 or {@link #MANY}.
   */
  record StaticType (@NonNull List <ItemType> items, int min, int max)
  {
    static final StaticType EMPTY = new StaticType (List.of (), 0, 0);
    static final StaticType ANY = new StaticType (List.of (ItemType.ITEM), 0, MANY);

    /**
     * @return The type of exactly one item of the given item type.
     */
    @NonNull
    static StaticType one (@NonNull final ItemType aItem)
    {
      return new StaticType (List.of (aItem), 1, 1);
    }

    /**
     * @return The type of the declared sequence type.
     */
    @NonNull
    static StaticType of (@NonNull final SeqType aType)
    {
      if (aType.empty ())
        return EMPTY;
      final EOccurrence eOcc = aType.occurrence ();
      return new StaticType (List.of (aType.itemType ()),
                             eOcc.allowsEmpty () ? 0 : 1,
                             eOcc.allowsMany () ? MANY : 1);
    }

    /**
     * @return <code>true</code> for <code>empty-sequence()</code>.
     */
    boolean isEmpty ()
    {
      return max == 0;
    }

    /**
     * @return The same item types with another occurrence; the empty type if
     *         the maximum is 0.
     */
    @NonNull
    StaticType withOccurrence (final int nMin, final int nMax)
    {
      if (nMax == 0 || items.isEmpty ())
        return EMPTY;
      return new StaticType (items, nMin, nMax);
    }

    @Override
    public String toString ()
    {
      if (isEmpty ())
        return "empty-sequence()";
      final StringBuilder aSB = new StringBuilder ();
      if (items.size () > 1)
        aSB.append ('(');
      for (int i = 0; i < items.size (); ++i)
      {
        if (i > 0)
          aSB.append (" | ");
        aSB.append (SequenceTypeMatcher.getItemTypeAsString (items.get (i)));
      }
      if (items.size () > 1)
        aSB.append (')');
      if (min == 0 && max == 1)
        aSB.append ('?');
      else
        if (min == 0)
          aSB.append ('*');
        else
          if (max == MANY)
            aSB.append ('+');
      return aSB.toString ();
    }
  }

  /**
   * The variables bound in the expression and the static type of the focus.
   *
   * @param variables
   *        The static types of the variables bound in the expression.
   * @param focus
   *        The static type of the context item, <code>null</code> for the one
   *        of the static context.
   */
  private record Scope (@NonNull ICommonsMap <XdmQName, StaticType> variables, @Nullable StaticType focus)
  {
    @NonNull
    Scope withFocus (@NonNull final StaticType aFocus)
    {
      return new Scope (variables, aFocus);
    }

    @NonNull
    Scope withVariable (@NonNull final XdmQName aName, @NonNull final StaticType aType)
    {
      final ICommonsMap <XdmQName, StaticType> aVars = variables.getClone ();
      aVars.put (aName, aType);
      return new Scope (aVars, focus);
    }
  }

  private static final ItemType BOOLEAN = ItemType.atomic (EAtomicType.BOOLEAN);
  private static final ItemType STRING = ItemType.atomic (EAtomicType.STRING);
  private static final ItemType INTEGER = ItemType.atomic (EAtomicType.INTEGER);
  private static final SeqType INTEGER_OPTIONAL = SeqType.of (INTEGER, EOccurrence.ZERO_OR_ONE);
  private static final ItemType ANY_ATOMIC = ItemType.atomic (EAtomicType.ANY_ATOMIC_TYPE);
  private static final ItemType UNTYPED_ATOMIC = ItemType.atomic (EAtomicType.UNTYPED_ATOMIC);
  private static final ItemType ANY_NODE = ItemType.node (NodeTest.ANY_NODE);
  private static final ItemType ANY_FUNCTION = new ItemType (ItemType.Kind.FUNCTION,
                                                             null,
                                                             null,
                                                             true,
                                                             null,
                                                             null,
                                                             null,
                                                             null);
  private static final ItemType ANY_MAP = new ItemType (ItemType.Kind.MAP, null, null, true, null, null, null, null);
  private static final ItemType ANY_ARRAY = new ItemType (ItemType.Kind.ARRAY, null, null, true, null, null, null, null);

  private final StaticContext m_aStaticCtx;
  private final FunctionLibrary m_aFunctions;

  private XPathStaticTyping (@NonNull final StaticContext aStaticCtx)
  {
    m_aStaticCtx = aStaticCtx;
    m_aFunctions = aStaticCtx.getFunctionLibrary ();
  }

  /**
   * @return The most specific built-in atomic type both types derive from; the
   *         numeric pseudo type for two numeric types without a common numeric
   *         ancestor.
   */
  @NonNull
  private static EAtomicType _getCommonAtomicType (@NonNull final EAtomicType eA, @NonNull final EAtomicType eB)
  {
    for (EAtomicType e = eA; e != null; e = e.getParent ())
      if (eB.isDerivedFrom (e))
        return e;
    if (eA.isNumeric () && eB.isNumeric ())
      return EAtomicType.NUMERIC;
    return EAtomicType.ANY_ATOMIC_TYPE;
  }

  /**
   * @return The most specific item type of the ones this implementation can
   *         express that every alternative is a subtype of.
   */
  @NonNull
  private static ItemType _getCommonItemType (@NonNull final List <ItemType> aItems)
  {
    if (aItems.size () == 1)
      return aItems.get (0);
    ItemType.Kind eKind = null;
    for (final ItemType aItem : aItems)
      if (eKind == null)
        eKind = aItem.kind ();
      else
        if (eKind != aItem.kind ())
          return ItemType.ITEM;
    switch (eKind)
    {
      case ATOMIC:
      {
        EAtomicType eCommon = null;
        for (final ItemType aItem : aItems)
        {
          if (aItem.schemaType () != null)
            return ANY_ATOMIC;
          eCommon = eCommon == null ? aItem.atomicType () : _getCommonAtomicType (eCommon, aItem.atomicType ());
        }
        return ItemType.atomic (eCommon);
      }
      case NODE:
        return ANY_NODE;
      case FUNCTION:
        return ANY_FUNCTION;
      case MAP:
        return ANY_MAP;
      case ARRAY:
        return ANY_ARRAY;
      default:
        return ItemType.ITEM;
    }
  }

  /**
   * @return The sequence type that every value of the static type matches.
   */
  @NonNull
  static SeqType toSeqType (@NonNull final StaticType aType)
  {
    if (aType.isEmpty ())
      return SeqType.EMPTY_SEQUENCE;
    final EOccurrence eOcc;
    if (aType.min () == 0)
      eOcc = aType.max () == 1 ? EOccurrence.ZERO_OR_ONE : EOccurrence.ZERO_OR_MORE;
    else
      eOcc = aType.max () == 1 ? EOccurrence.ONE : EOccurrence.ONE_OR_MORE;
    return SeqType.of (_getCommonItemType (aType.items ()), eOcc);
  }

  /**
   * Add an alternative to a list, leaving out an alternative that another one
   * subsumes, and collapsing the list into its common supertype once it grows
   * beyond {@link #MAX_ALTERNATIVES}.
   */
  private static void _addAlternative (@NonNull final List <ItemType> aTarget, @NonNull final ItemType aItem)
  {
    for (final ItemType aExisting : aTarget)
      if (SequenceTypeMatcher.isItemSubtype (aItem, aExisting))
        return;
    aTarget.removeIf (aExisting -> SequenceTypeMatcher.isItemSubtype (aExisting, aItem));
    aTarget.add (aItem);
    if (aTarget.size () > MAX_ALTERNATIVES)
    {
      final ItemType aCommon = _getCommonItemType (new ArrayList <> (aTarget));
      aTarget.clear ();
      aTarget.add (aCommon);
    }
  }

  @NonNull
  private static List <ItemType> _mergeItems (@NonNull final List <ItemType> aA, @NonNull final List <ItemType> aB)
  {
    // Common case: the same alternatives, nothing to merge
    if (aA.equals (aB) || aB.isEmpty ())
      return aA;
    if (aA.isEmpty ())
      return aB;
    final List <ItemType> ret = new ArrayList <> (aA);
    for (final ItemType aItem : aB)
      _addAlternative (ret, aItem);
    return List.copyOf (ret);
  }

  /**
   * @return The type of a value that is one of the two (the branches of an
   *         <code>if</code>).
   */
  @NonNull
  static StaticType union (@NonNull final StaticType aA, @NonNull final StaticType aB)
  {
    return new StaticType (_mergeItems (aA.items (), aB.items ()),
                           Math.min (aA.min (), aB.min ()),
                           Math.max (aA.max (), aB.max ()));
  }

  /**
   * @return The type of the concatenation of the two (the comma operator).
   */
  @NonNull
  static StaticType concat (@NonNull final StaticType aA, @NonNull final StaticType aB)
  {
    if (aA.isEmpty ())
      return aB;
    if (aB.isEmpty ())
      return aA;
    return new StaticType (_mergeItems (aA.items (), aB.items ()),
                           Math.min (1, aA.min () + aB.min ()),
                           Math.min (MANY, aA.max () + aB.max ()));
  }

  /**
   * @return The type of evaluating the inner expression once for every item of
   *         the outer one and concatenating the results (<code>for</code>,
   *         <code>!</code>, path steps).
   */
  @NonNull
  static StaticType repeat (@NonNull final StaticType aOuter, @NonNull final StaticType aInner)
  {
    if (aOuter.isEmpty () || aInner.isEmpty ())
      return StaticType.EMPTY;
    return aInner.withOccurrence (aOuter.min () * aInner.min (), Math.min (MANY, aOuter.max () * aInner.max ()));
  }

  /**
   * @return The type of one item of the given type: the context item of the
   *         right-hand side of <code>!</code> or of a path step, or the
   *         variable of <code>for</code>.
   */
  @NonNull
  private static StaticType _eachItem (@NonNull final StaticType aType)
  {
    return aType.isEmpty () ? StaticType.EMPTY : new StaticType (aType.items (), 1, 1);
  }

  /**
   * The result of atomising a value of the given type (XPath 3.1 section
   * 2.4.2).
   *
   * @return The atomised type, or <code>null</code> if atomisation always fails
   *         (a function item or a map).
   */
  @Nullable
  private StaticType _atomize (@NonNull final StaticType aType)
  {
    final List <ItemType> aItems = new ArrayList <> ();
    int nMin = aType.min ();
    int nMax = aType.max ();
    for (final ItemType aItem : aType.items ())
      switch (aItem.kind ())
      {
        case ATOMIC:
          _addAlternative (aItems, aItem);
          break;
        case NODE:
        {
          final NodeTest aTest = aItem.nodeTest ();
          final ENodeTestKind eKind = aTest.kind ();
          if (eKind == ENodeTestKind.COMMENT ||
              eKind == ENodeTestKind.PROCESSING_INSTRUCTION ||
              eKind == ENodeTestKind.NAMESPACE_NODE)
            _addAlternative (aItems, STRING);
          else
            if (aTest.typeName () != null ||
                eKind == ENodeTestKind.SCHEMA_ELEMENT ||
                eKind == ENodeTestKind.SCHEMA_ATTRIBUTE ||
                m_aStaticCtx.getSchemaSet () != null)
            {
              // A typed node may atomise to anything, also to several values
              _addAlternative (aItems, ANY_ATOMIC);
              nMin = 0;
              nMax = MANY;
            }
            else
              _addAlternative (aItems, UNTYPED_ATOMIC);
          break;
        }
        case FUNCTION:
        case MAP:
          return null;
        default:
          // item() and arrays: any number of atomic values
          _addAlternative (aItems, ANY_ATOMIC);
          nMin = 0;
          nMax = MANY;
          break;
      }
    if (aItems.isEmpty () || nMax == 0)
      return StaticType.EMPTY;
    return new StaticType (List.copyOf (aItems), nMin, nMax);
  }

  /**
   * The function conversion rules (XPath 3.1 section 3.1.5.2) for one atomic
   * alternative against a required atomic type.
   *
   * @return <code>true</code> if the value is accepted or converted.
   */
  private static boolean _isAtomicAccepted (@NonNull final ItemType aAtomic, @NonNull final ItemType aRequired)
  {
    if (SequenceTypeMatcher.isItemSubtype (aAtomic, aRequired))
      return true;
    final EAtomicType eActual = aAtomic.atomicType ();
    final EAtomicType eRequired = aRequired.atomicType ();
    // xs:untypedAtomic is cast to the required type
    if (eActual == EAtomicType.UNTYPED_ATOMIC)
      return true;
    if (aAtomic.schemaType () != null || aRequired.schemaType () != null)
      return false;
    // Numeric and URI promotion (XPath 3.1 section B.1)
    if (eRequired == EAtomicType.DOUBLE)
      return eActual.isDerivedFrom (EAtomicType.FLOAT) || eActual.isDerivedFrom (EAtomicType.DECIMAL);
    if (eRequired == EAtomicType.FLOAT)
      return eActual.isDerivedFrom (EAtomicType.DECIMAL);
    if (eRequired == EAtomicType.STRING)
      return eActual.isDerivedFrom (EAtomicType.ANY_URI);
    return false;
  }

  /**
   * Whether every value of the inferred type is accepted by the function
   * conversion rules for the required type (XPath 3.1 section 3.1.5.2).
   *
   * @return <code>true</code> if the inferred type is subsumed by the required
   *         type after the conversions.
   */
  private boolean _isAccepted (@NonNull final StaticType aActual, @NonNull final SeqType aRequired)
  {
    if (aRequired.empty ())
      return aActual.isEmpty ();
    final ItemType aReqItem = aRequired.itemType ();
    final EOccurrence eReqOcc = aRequired.occurrence ();
    StaticType aConverted = aActual;
    final boolean bAtomic = aReqItem.kind () == ItemType.Kind.ATOMIC;
    if (bAtomic)
    {
      aConverted = _atomize (aActual);
      if (aConverted == null)
        return false;
    }
    if (aConverted.min () == 0 && !eReqOcc.allowsEmpty () && !aConverted.isEmpty ())
      return false;
    if (aConverted.isEmpty ())
      return eReqOcc.allowsEmpty ();
    if (aConverted.max () == MANY && !eReqOcc.allowsMany ())
      return false;
    for (final ItemType aItem : aConverted.items ())
    {
      final boolean bAccepted;
      if (bAtomic)
        bAccepted = _isAtomicAccepted (aItem, aReqItem);
      else
        if (aItem.kind () == ItemType.Kind.FUNCTION && aReqItem.kind () == ItemType.Kind.FUNCTION)
          bAccepted = _isFunctionAccepted (aItem, aReqItem);
        else
          bAccepted = SequenceTypeMatcher.isItemSubtype (aItem, aReqItem);
      if (!bAccepted)
        return false;
    }
    return true;
  }

  /**
   * Function coercion (XPath 3.1 section 3.1.5.3) for a function item against
   * a required function type: the coerced function converts the arguments it
   * receives (values of the required parameter types) to the parameter types
   * of the supplied function, and its result to the required return type,
   * with the function conversion rules. It is accepted statically if both
   * conversions are.
   *
   * @return <code>true</code> if the supplied function type is accepted.
   */
  private boolean _isFunctionAccepted (@NonNull final ItemType aSupplied, @NonNull final ItemType aRequired)
  {
    if (aRequired.wildcard () || aRequired.returnType () == null)
      return true;
    // Pessimistic: a function of unknown signature
    if (aSupplied.wildcard () || aSupplied.returnType () == null)
      return false;
    final List <SeqType> aParamsS = aSupplied.paramTypes () == null ? List.of () : aSupplied.paramTypes ();
    final List <SeqType> aParamsR = aRequired.paramTypes () == null ? List.of () : aRequired.paramTypes ();
    if (aParamsS.size () != aParamsR.size ())
      return false;
    for (int i = 0; i < aParamsS.size (); ++i)
      if (!_isAccepted (StaticType.of (aParamsR.get (i)), aParamsS.get (i)))
        return false;
    return _isAccepted (StaticType.of (aSupplied.returnType ()), aRequired.returnType ());
  }

  /**
   * @throws XPathException
   *         XPTY0004 if the inferred type of an argument is not accepted by
   *         the parameter type.
   */
  private void _checkArgument (@NonNull final XdmQName aFunction,
                               final int nIndex,
                               @NonNull final StaticType aActual,
                               @NonNull final SeqType aRequired)
  {
    if (!_isAccepted (aActual, aRequired))
      throw XPathException.of ("XPTY0004",
                               "The inferred static type " +
                                                aActual +
                                                " of argument " +
                                                (nIndex + 1) +
                                                " of " +
                                                aFunction.getEQName () +
                                                " is not subsumed by the required type " +
                                                SequenceTypeMatcher.getAsString (aRequired));
  }

  /**
   * @return A type error with the code XPTY0004 or another one, naming the
   *         inferred type.
   */
  @NonNull
  private static XPathException _typeError (@NonNull final String sCode,
                                            @NonNull final StaticType aActual,
                                            @NonNull final String sWhat)
  {
    return XPathException.of (sCode, "The inferred static type " + aActual + " of " + sWhat);
  }

  /**
   * @return <code>true</code> if every alternative is a node type (also for
   *         the empty type).
   */
  private static boolean _isNodes (@NonNull final StaticType aType)
  {
    for (final ItemType aItem : aType.items ())
      if (aItem.kind () != ItemType.Kind.NODE)
        return false;
    return true;
  }

  /**
   * @return <code>true</code> if an atomic value of the type can be an operand
   *         of an arithmetic operator (numeric, date, time and duration types,
   *         and the types that may hold one).
   */
  private static boolean _isArithmeticAtomic (@NonNull final ItemType aAtomic, final boolean bUnary)
  {
    if (aAtomic.schemaType () != null)
      return true;
    final EAtomicType e = aAtomic.atomicType ();
    if (e == EAtomicType.ANY_ATOMIC_TYPE || e == EAtomicType.UNTYPED_ATOMIC || e == EAtomicType.NUMERIC || e.isNumeric ())
      return true;
    if (bUnary)
      return false;
    return e.isDurationFamily () ||
           e.isDerivedFrom (EAtomicType.DATE_TIME) ||
           e == EAtomicType.DATE ||
           e == EAtomicType.TIME;
  }

  /**
   * The operand of an arithmetic operator is atomised and must be at most one
   * value of a type the operator mapping has an entry for (XPath 3.1 section
   * 3.5).
   *
   * @throws XPathException
   *         XPTY0004 otherwise
   */
  private void _checkArithmeticOperand (@NonNull final StaticType aOperand, final boolean bUnary)
  {
    final StaticType aAtomized = _atomize (aOperand);
    if (aAtomized == null || aAtomized.max () == MANY)
      throw _typeError ("XPTY0004", aOperand, "an arithmetic operand allows more than one atomic value");
    for (final ItemType aItem : aAtomized.items ())
      if (!_isArithmeticAtomic (aItem, bUnary))
        throw _typeError ("XPTY0004", aOperand, "an arithmetic operand is not numeric, date, time or duration");
  }

  /**
   * The operands of a value comparison are atomised and must be at most one
   * value, the operands of a node comparison at most one node (XPath 3.1
   * sections 3.7.1 and 3.7.3).
   *
   * @throws XPathException
   *         XPTY0004 otherwise
   */
  private void _checkComparisonOperand (final IExpr.@NonNull ComparisonExpr aComparison,
                                        @NonNull final StaticType aOperand)
  {
    if (aComparison.op ().isValue ())
    {
      final StaticType aAtomized = _atomize (aOperand);
      if (aAtomized == null || aAtomized.max () == MANY)
        throw _typeError ("XPTY0004", aOperand, "a value comparison operand allows more than one atomic value");
    }
    else
      if (aComparison.op ().isNode () && (!_isNodes (aOperand) || aOperand.max () == MANY))
        throw _typeError ("XPTY0004", aOperand, "a node comparison operand is not node()?");
  }

  /**
   * The operand of <code>cast as</code> is atomised and must be one value, or
   * none if the target type has the occurrence <code>?</code> (XPath 3.1
   * section 3.14.2).
   *
   * @throws XPathException
   *         XPTY0004 otherwise
   */
  private void _checkCastOperand (final IExpr.@NonNull CastExpr aCast, @NonNull final StaticType aOperand)
  {
    final StaticType aAtomized = _atomize (aOperand);
    if (aAtomized == null || aAtomized.max () == MANY)
      throw _typeError ("XPTY0004", aOperand, "the cast operand allows more than one atomic value");
    if (aAtomized.min () == 0 && !aCast.type ().occurrence ().allowsEmpty ())
      throw _typeError ("XPTY0004", aOperand, "the cast operand allows the empty sequence");
  }

  @NonNull
  private static ItemType _getFunctionType (@NonNull final List <SeqType> aParams, @NonNull final SeqType aReturn)
  {
    return new ItemType (ItemType.Kind.FUNCTION, null, null, false, List.copyOf (aParams), aReturn, null, null);
  }

  /**
   * @return The item type an axis step with the node test selects.
   */
  @NonNull
  private static ItemType _getStepItemType (@NonNull final EAxis eAxis, @NonNull final NodeTest aTest)
  {
    switch (aTest.kind ())
    {
      case NAME:
        if (eAxis == EAxis.ATTRIBUTE)
          return ItemType.node (NodeTest.ofKind (ENodeTestKind.ATTRIBUTE));
        if (eAxis == EAxis.NAMESPACE)
          return ItemType.node (NodeTest.ofKind (ENodeTestKind.NAMESPACE_NODE));
        return ItemType.node (NodeTest.ofKind (ENodeTestKind.ELEMENT));
      case ANY_NODE:
        if (eAxis == EAxis.ATTRIBUTE)
          return ItemType.node (NodeTest.ofKind (ENodeTestKind.ATTRIBUTE));
        return ANY_NODE;
      default:
        return ItemType.node (aTest);
    }
  }

  /**
   * @return The numeric type of an arithmetic operand after atomisation, or
   *         <code>null</code> if it is not known to be numeric.
   */
  @Nullable
  private EAtomicType _getNumericOperandType (@NonNull final StaticType aOperand)
  {
    final StaticType aAtomized = _atomize (aOperand);
    if (aAtomized == null || aAtomized.isEmpty ())
      return null;
    EAtomicType ret = null;
    for (final ItemType aItem : aAtomized.items ())
    {
      if (aItem.schemaType () != null)
        return null;
      EAtomicType e = aItem.atomicType ();
      // xs:untypedAtomic is cast to xs:double for arithmetic
      if (e == EAtomicType.UNTYPED_ATOMIC)
        e = EAtomicType.DOUBLE;
      if (!e.isNumeric ())
        return null;
      ret = ret == null ? e : _getCommonAtomicType (ret, e);
    }
    return ret;
  }

  /**
   * The result type of an arithmetic operator (XPath 3.1 section 3.5 and
   * appendix B.2): for numeric operands the promoted type, for anything else
   * <code>xs:anyAtomicType</code>.
   */
  @NonNull
  private StaticType _inferArithmetic (final IExpr.@NonNull BinaryExpr aExpr,
                                       @NonNull final StaticType aLeft,
                                       @NonNull final StaticType aRight)
  {
    final int nMin = Math.min (aLeft.min (), aRight.min ());
    final EAtomicType eLeft = _getNumericOperandType (aLeft);
    final EAtomicType eRight = _getNumericOperandType (aRight);
    if (eLeft == null || eRight == null)
      return new StaticType (List.of (ANY_ATOMIC), 0, 1);
    final EAtomicType eResult;
    if (aExpr.op () == EBinaryOp.INTEGER_DIVIDE)
      eResult = EAtomicType.INTEGER;
    else
      if (eLeft.isDerivedFrom (EAtomicType.DOUBLE) || eRight.isDerivedFrom (EAtomicType.DOUBLE))
        eResult = EAtomicType.DOUBLE;
      else
        if (eLeft.isDerivedFrom (EAtomicType.FLOAT) || eRight.isDerivedFrom (EAtomicType.FLOAT))
          eResult = EAtomicType.FLOAT;
        else
          if (eLeft == EAtomicType.NUMERIC || eRight == EAtomicType.NUMERIC)
            eResult = EAtomicType.NUMERIC;
          else
            if (aExpr.op () == EBinaryOp.DIVIDE ||
                !eLeft.isIntegerFamily () ||
                !eRight.isIntegerFamily ())
              eResult = EAtomicType.DECIMAL;
            else
              eResult = EAtomicType.INTEGER;
    return new StaticType (List.of (ItemType.atomic (eResult)), nMin, 1);
  }

  /**
   * @return The static type of a variable reference: bound in the expression,
   *         else declared with its type in the static context, else
   *         <code>item()*</code>.
   */
  @NonNull
  private StaticType _inferVariable (@NonNull final XdmQName aName, @NonNull final Scope aScope)
  {
    final StaticType ret = aScope.variables ().get (aName);
    if (ret != null)
      return ret;
    final SeqType aDeclared = m_aStaticCtx.getVariableType (aName);
    return aDeclared != null ? StaticType.of (aDeclared) : StaticType.ANY;
  }

  /**
   * @return The static type of the context item: the focus of the scope, else
   *         the context item static type of the static context, else
   *         <code>item()</code>.
   */
  @NonNull
  private StaticType _getFocus (@NonNull final Scope aScope)
  {
    if (aScope.focus () != null)
      return aScope.focus ();
    final ItemType aDeclared = m_aStaticCtx.getContextItemStaticType ();
    return StaticType.one (aDeclared != null ? aDeclared : ItemType.ITEM);
  }

  /**
   * Infer the arguments of a static function call, check them against the
   * parameter types and return the result type: the declared return type, or
   * for a partial application the function type of the remaining parameters.
   */
  @NonNull
  private StaticType _inferFunctionCall (final IExpr.@NonNull FunctionCallExpr aCall, @NonNull final Scope aScope)
  {
    final List <IExpr> aArgs = aCall.args ();
    final FunctionLibrary.FunctionDef aDef = m_aFunctions.getFunction (aCall.name (), aArgs.size ());
    final List <SeqType> aParams = aDef != null ? aDef.getParamTypes () : null;
    final boolean bParamsKnown = aParams != null && aParams.size () == aArgs.size ();
    final List <SeqType> aRemaining = new ArrayList <> ();
    for (int i = 0; i < aArgs.size (); ++i)
    {
      final IExpr aArg = aArgs.get (i);
      if (aArg instanceof IExpr.PlaceholderExpr)
      {
        aRemaining.add (bParamsKnown ? aParams.get (i) : SeqType.ANY);
        continue;
      }
      final StaticType aArgType = _infer (aArg, aScope);
      if (bParamsKnown && !m_aStaticCtx.isXPath10Compatibility ())
        _checkArgument (aCall.name (), i, aArgType, aParams.get (i));
    }
    // The zero-argument forms that take the context item as their argument
    if (aArgs.isEmpty () &&
        XdmConstants.NS_FN.equals (aCall.name ().namespaceUri ()) &&
        CONTEXT_ARGUMENT_FUNCTIONS.contains (aCall.name ().localName ()))
    {
      final FunctionLibrary.FunctionDef aOneArg = m_aFunctions.getFunction (aCall.name (), 1);
      if (aOneArg != null && aOneArg.getParamTypes ().size () == 1)
        _checkArgument (aCall.name (), 0, _getFocus (aScope), aOneArg.getParamTypes ().get (0));
    }
    final SeqType aReturn = aDef != null ? aDef.getReturnType () : SeqType.ANY;
    if (aRemaining.size () > 0)
      return StaticType.one (_getFunctionType (aRemaining, aReturn));
    return StaticType.of (aReturn);
  }

  /**
   * @return The function type of a named function reference, or
   *         <code>function(*)</code> if the function is not known statically.
   */
  @NonNull
  private StaticType _inferNamedFunctionRef (final IExpr.@NonNull NamedFunctionRefExpr aRef)
  {
    final FunctionLibrary.FunctionDef aDef = m_aFunctions.getFunction (aRef.name (), aRef.arity ());
    if (aDef == null || aDef.getParamTypes ().size () != aRef.arity ())
      return StaticType.one (ANY_FUNCTION);
    return StaticType.one (_getFunctionType (aDef.getParamTypes (), aDef.getReturnType ()));
  }

  /**
   * @return The function type of an inline function: the declared parameter
   *         types (<code>item()*</code> if undeclared) and the declared return
   *         type, or the type inferred from the body. The body has no focus.
   */
  @NonNull
  private StaticType _inferInlineFunction (final IExpr.@NonNull InlineFunctionExpr aFunc, @NonNull final Scope aScope)
  {
    final List <SeqType> aParams = new ArrayList <> ();
    Scope aBodyScope = new Scope (aScope.variables (), StaticType.one (ItemType.ITEM));
    for (final IExpr.Param aParam : aFunc.params ())
    {
      final SeqType aType = aParam.type () != null ? aParam.type () : SeqType.ANY;
      aParams.add (aType);
      aBodyScope = aBodyScope.withVariable (aParam.name (), StaticType.of (aType));
    }
    final StaticType aBody = _infer (aFunc.body (), aBodyScope);
    if (aFunc.returnType () != null && !_isAccepted (aBody, aFunc.returnType ()))
      throw _typeError ("XPTY0004",
                        aBody,
                        "the function body is not subsumed by the declared return type " +
                               SequenceTypeMatcher.getAsString (aFunc.returnType ()));
    final SeqType aReturn = aFunc.returnType () != null ? aFunc.returnType () : toSeqType (aBody);
    return StaticType.one (_getFunctionType (aParams, aReturn));
  }

  /**
   * @return The result type of a dynamic function call: the return type of a
   *         single function type, the value type of a map or array type, else
   *         <code>item()*</code>.
   */
  @NonNull
  private StaticType _inferDynamicCall (final IExpr.@NonNull DynamicCallExpr aCall, @NonNull final Scope aScope)
  {
    final StaticType aBase = _infer (aCall.base (), aScope);
    // The base must be exactly one function, map or array
    if (aBase.min () == 0 || aBase.max () == MANY)
      throw _typeError ("XPTY0004", aBase, "the function of a dynamic call is not exactly one item");
    for (final ItemType aBaseItem : aBase.items ())
      if (aBaseItem.kind () != ItemType.Kind.FUNCTION &&
          aBaseItem.kind () != ItemType.Kind.MAP &&
          aBaseItem.kind () != ItemType.Kind.ARRAY)
        throw _typeError ("XPTY0004", aBase, "the function of a dynamic call is not function(*)");
    final ItemType aItem = aBase.items ().size () == 1 ? aBase.items ().get (0) : null;
    // Every alternative of a known signature must take this many arguments
    for (final ItemType aBaseItem : aBase.items ())
      if (aBaseItem.kind () == ItemType.Kind.FUNCTION &&
          !aBaseItem.wildcard () &&
          aBaseItem.paramTypes () != null &&
          aBaseItem.paramTypes ().size () != aCall.args ().size ())
        throw _typeError ("XPTY0004",
                          aBase,
                          "the function of a dynamic call does not take " + aCall.args ().size () + " arguments");
    final List <SeqType> aParams = aItem != null &&
                                   aItem.kind () == ItemType.Kind.FUNCTION &&
                                   !aItem.wildcard () &&
                                   aItem.paramTypes () != null &&
                                   aItem.paramTypes ().size () == aCall.args ().size () ? aItem.paramTypes () : null;
    boolean bPartial = false;
    for (int i = 0; i < aCall.args ().size (); ++i)
    {
      final IExpr aArg = aCall.args ().get (i);
      if (aArg instanceof IExpr.PlaceholderExpr)
        bPartial = true;
      else
      {
        final StaticType aArgType = _infer (aArg, aScope);
        if (aParams != null && !_isAccepted (aArgType, aParams.get (i)))
          throw _typeError ("XPTY0004",
                            aArgType,
                            "argument " +
                                      (i + 1) +
                                      " of a dynamic call is not subsumed by the required type " +
                                      SequenceTypeMatcher.getAsString (aParams.get (i)));
      }
    }
    if (bPartial)
      return StaticType.one (ANY_FUNCTION);
    if (aItem == null)
      return StaticType.ANY;
    switch (aItem.kind ())
    {
      case FUNCTION:
        return aItem.wildcard () || aItem.returnType () == null ? StaticType.ANY : StaticType.of (aItem.returnType ());
      case MAP:
        // An absent key gives the empty sequence
        if (aItem.wildcard () || aItem.valueType () == null)
          return StaticType.ANY;
        final StaticType aValue = StaticType.of (aItem.valueType ());
        return aValue.withOccurrence (0, aValue.max ());
      case ARRAY:
        return aItem.wildcard () || aItem.valueType () == null ? StaticType.ANY : StaticType.of (aItem.valueType ());
      default:
        return StaticType.ANY;
    }
  }

  /**
   * Infer the predicates of a step or filter expression with the item type of
   * the filtered sequence as focus. A filtered sequence may lose all items; a
   * numeric literal predicate keeps at most one.
   */
  @NonNull
  private StaticType _inferPredicates (@NonNull final StaticType aInput,
                                       @NonNull final List <IExpr> aPredicates,
                                       @NonNull final Scope aScope)
  {
    if (aPredicates.isEmpty ())
      return aInput;
    final Scope aPredScope = aScope.withFocus (_eachItem (aInput));
    int nMax = aInput.max ();
    for (final IExpr aPred : aPredicates)
    {
      _infer (aPred, aPredScope);
      if (aPred instanceof final IExpr.LiteralExpr aLiteral && aLiteral.value ().getType ().isNumeric ())
        nMax = Math.min (nMax, 1);
    }
    return aInput.withOccurrence (0, nMax);
  }

  /**
   * @return The type of a path: the start (the root of the context node for an
   *         absolute path, else the context item), then every step evaluated
   *         once per item of the previous one.
   */
  @NonNull
  private StaticType _inferPath (final IExpr.@NonNull PathExpr aPath, @NonNull final Scope aScope)
  {
    StaticType aCurrent;
    // Whether the input of the next step is the context item (XPTY0020) or the
    // result of an expression (XPTY0019)
    boolean bFromContext = false;
    if (aPath.start () != null)
      aCurrent = _infer (aPath.start (), aScope);
    else
    {
      final StaticType aFocus = _getFocus (aScope);
      if (aPath.absolute ())
      {
        if (!_isNodes (aFocus))
          throw _typeError ("XPTY0020", aFocus, "the context item of a path from the root is not a node");
        aCurrent = StaticType.one (ItemType.node (NodeTest.ofKind (ENodeTestKind.DOCUMENT)));
      }
      else
      {
        aCurrent = aFocus;
        bFromContext = true;
      }
    }
    for (final IExpr.StepExpr aStep : aPath.steps ())
    {
      if (bFromContext)
      {
        // Only an axis step needs a node as context item
        if (aStep.postfix () == null && !_isNodes (aCurrent))
          throw _typeError ("XPTY0020", aCurrent, "the context item of an axis step is not a node");
        bFromContext = false;
      }
      else
        if (!_isNodes (aCurrent))
          throw _typeError ("XPTY0019", aCurrent, "the left-hand side of / is not a sequence of nodes");
      final Scope aStepScope = aScope.withFocus (_eachItem (aCurrent));
      StaticType aStepType;
      if (aStep.postfix () != null)
        aStepType = _infer (aStep.postfix (), aStepScope);
      else
        aStepType = new StaticType (List.of (_getStepItemType (aStep.axis (), aStep.test ())), 0, MANY);
      aStepType = _inferPredicates (aStepType, aStep.predicates (), aScope);
      aCurrent = repeat (aCurrent, aStepType);
    }
    return aCurrent;
  }

  /**
   * Bind the variables of <code>for</code>, <code>let</code> or a quantified
   * expression one after the other and infer the body.
   *
   * @param bEachItem
   *        <code>true</code> if every variable is bound to one item of its
   *        sequence (<code>for</code>, quantified), <code>false</code> for the
   *        whole sequence (<code>let</code>).
   * @return The type of the body, repeated for every item of the bound
   *         sequences if <code>bEachItem</code>.
   */
  @NonNull
  private StaticType _inferBindings (@NonNull final List <IExpr.Binding> aBindings,
                                     @NonNull final IExpr aBody,
                                     final boolean bEachItem,
                                     @NonNull final Scope aScope)
  {
    Scope aInner = aScope;
    StaticType aOuter = StaticType.one (ItemType.ITEM);
    for (final IExpr.Binding aBinding : aBindings)
    {
      final StaticType aSeq = _infer (aBinding.expr (), aInner);
      if (bEachItem)
      {
        aOuter = repeat (aOuter, aSeq.withOccurrence (aSeq.min (), aSeq.max ()));
        aInner = aInner.withVariable (aBinding.name (), _eachItem (aSeq));
      }
      else
        aInner = aInner.withVariable (aBinding.name (), aSeq);
    }
    final StaticType aBodyType = _infer (aBody, aInner);
    return bEachItem ? repeat (aOuter, aBodyType) : aBodyType;
  }

  /**
   * Infer the static type of an expression and check the required types of the
   * function calls in it; {@link #_infer(IExpr, Scope)} adds the check of the
   * empty type.
   *
   * @throws XPathException
   *         XPTY0004 for an argument whose inferred type is not accepted
   */
  @NonNull
  private StaticType _inferExpr (@NonNull final IExpr aExpr, @NonNull final Scope aScope)
  {
    switch (aExpr)
    {
      case final IExpr.LiteralExpr aLiteral:
        return StaticType.one (ItemType.atomic (aLiteral.value ().getType ()));
      case final IExpr.VarRefExpr aVar:
        return _inferVariable (aVar.name (), aScope);
      case final IExpr.ContextItemExpr aContext:
        return _getFocus (aScope);
      case final IExpr.FunctionCallExpr aCall:
        return _inferFunctionCall (aCall, aScope);
      case final IExpr.PlaceholderExpr aPlaceholder:
        return StaticType.ANY;
      case final IExpr.NamedFunctionRefExpr aRef:
        return _inferNamedFunctionRef (aRef);
      case final IExpr.InlineFunctionExpr aFunc:
        return _inferInlineFunction (aFunc, aScope);
      case final IExpr.DynamicCallExpr aCall:
        return _inferDynamicCall (aCall, aScope);
      case final IExpr.SequenceExpr aSeq:
      {
        StaticType ret = StaticType.EMPTY;
        for (final IExpr aItem : aSeq.items ())
          ret = concat (ret, _infer (aItem, aScope));
        return ret;
      }
      case final IExpr.BinaryExpr aBinary:
      {
        final StaticType aLeft = _infer (aBinary.left (), aScope);
        final StaticType aRight = _infer (aBinary.right (), aScope);
        if (aBinary.op () == EBinaryOp.AND || aBinary.op () == EBinaryOp.OR)
          return StaticType.one (BOOLEAN);
        _checkArithmeticOperand (aLeft, false);
        _checkArithmeticOperand (aRight, false);
        return _inferArithmetic (aBinary, aLeft, aRight);
      }
      case final IExpr.UnaryExpr aUnary:
      {
        final StaticType aOperand = _infer (aUnary.operand (), aScope);
        _checkArithmeticOperand (aOperand, true);
        final EAtomicType eType = _getNumericOperandType (aOperand);
        return new StaticType (List.of (eType != null ? ItemType.atomic (eType) : ANY_ATOMIC), 0, 1);
      }
      case final IExpr.ComparisonExpr aComparison:
      {
        final StaticType aLeft = _infer (aComparison.left (), aScope);
        final StaticType aRight = _infer (aComparison.right (), aScope);
        _checkComparisonOperand (aComparison, aLeft);
        _checkComparisonOperand (aComparison, aRight);
        // A general comparison is always true or false, the others are empty
        // for an empty operand
        if (aComparison.op ().isGeneral () || (aLeft.min () == 1 && aRight.min () == 1))
          return StaticType.one (BOOLEAN);
        return new StaticType (List.of (BOOLEAN), 0, 1);
      }
      case final IExpr.SetExpr aSet:
      {
        final StaticType aLeft = _infer (aSet.left (), aScope);
        final StaticType aRight = _infer (aSet.right (), aScope);
        final StaticType aNodes = aSet.op () == IExpr.SetExpr.ESetOp.UNION ? concat (aLeft, aRight) : aLeft;
        return aNodes.withOccurrence (0, MANY);
      }
      case final IExpr.RangeExpr aRange:
      {
        for (final IExpr aOperand : new IExpr [] { aRange.from (), aRange.to () })
        {
          final StaticType aType = _infer (aOperand, aScope);
          if (!_isAccepted (aType, INTEGER_OPTIONAL))
            throw _typeError ("XPTY0004", aType, "a range operand is not subsumed by xs:integer?");
        }
        return new StaticType (List.of (INTEGER), 0, MANY);
      }
      case final IExpr.StringConcatExpr aConcat:
        for (final IExpr aPart : aConcat.parts ())
          _infer (aPart, aScope);
        return StaticType.one (STRING);
      case final IExpr.IfExpr aIf:
        _infer (aIf.condition (), aScope);
        return union (_infer (aIf.thenExpr (), aScope), _infer (aIf.elseExpr (), aScope));
      case final IExpr.ForExpr aFor:
        return _inferBindings (aFor.bindings (), aFor.body (), true, aScope);
      case final IExpr.LetExpr aLet:
        return _inferBindings (aLet.bindings (), aLet.body (), false, aScope);
      case final IExpr.QuantifiedExpr aQuantified:
        _inferBindings (aQuantified.bindings (), aQuantified.satisfies (), true, aScope);
        return StaticType.one (BOOLEAN);
      case final IExpr.SimpleMapExpr aMap:
      {
        StaticType ret = null;
        for (final IExpr aStep : aMap.steps ())
          ret = ret == null ? _infer (aStep, aScope) : repeat (ret, _infer (aStep, aScope.withFocus (_eachItem (ret))));
        return ret;
      }
      case final IExpr.PathExpr aPath:
        return _inferPath (aPath, aScope);
      case final IExpr.FilterExpr aFilter:
        return _inferPredicates (_infer (aFilter.primary (), aScope), aFilter.predicates (), aScope);
      case final IExpr.InstanceOfExpr aInstance:
        _infer (aInstance.operand (), aScope);
        return StaticType.one (BOOLEAN);
      case final IExpr.TreatExpr aTreat:
        _infer (aTreat.operand (), aScope);
        return StaticType.of (aTreat.type ());
      case final IExpr.CastExpr aCast:
      {
        final StaticType aOperand = _infer (aCast.operand (), aScope);
        if (aCast.castable ())
          return StaticType.one (BOOLEAN);
        _checkCastOperand (aCast, aOperand);
        return StaticType.of (aCast.type ());
      }
      case final IExpr.MapConstructorExpr aMapCtor:
        for (final IExpr aKey : aMapCtor.keys ())
          _infer (aKey, aScope);
        for (final IExpr aValue : aMapCtor.values ())
          _infer (aValue, aScope);
        return StaticType.one (ANY_MAP);
      case final IExpr.ArrayConstructorExpr aArrayCtor:
        for (final IExpr aItem : aArrayCtor.items ())
          _infer (aItem, aScope);
        return StaticType.one (ANY_ARRAY);
      case final IExpr.LookupExpr aLookup:
      {
        final StaticType aBase = aLookup.base () != null ? _infer (aLookup.base (), aScope) : _getFocus (aScope);
        for (final ItemType aItem : aBase.items ())
          if (aItem.kind () != ItemType.Kind.MAP && aItem.kind () != ItemType.Kind.ARRAY)
            throw _typeError ("XPTY0004", aBase, "the left-hand side of a lookup is not a map or an array");
        if (aLookup.key () != null)
          _infer (aLookup.key (), aScope);
        return StaticType.ANY;
      }
    }
  }

  /**
   * @return <code>true</code> for the expressions XPST0005 exempts: the empty
   *         sequence <code>()</code> (also nested in parentheses) and
   *         <code>data(())</code>.
   */
  private static boolean _isExemptFromEmpty (@NonNull final IExpr aExpr)
  {
    if (aExpr instanceof final IExpr.SequenceExpr aSeq)
    {
      for (final IExpr aItem : aSeq.items ())
        if (!_isExemptFromEmpty (aItem))
          return false;
      return true;
    }
    return aExpr instanceof final IExpr.FunctionCallExpr aCall &&
           aCall.args ().size () == 1 &&
           XdmConstants.NS_FN.equals (aCall.name ().namespaceUri ()) &&
           "data".equals (aCall.name ().localName ()) &&
           aCall.args ().get (0) instanceof final IExpr.SequenceExpr aArg &&
           aArg.items ().isEmpty ();
  }

  /**
   * Infer the static type of an expression and check it.
   *
   * @throws XPathException
   *         XPTY0004 for an argument whose inferred type is not accepted,
   *         XPST0005 if the inferred type is <code>empty-sequence()</code> and
   *         the expression is neither <code>()</code> nor
   *         <code>data(())</code> (XPath 3.1 section 2.2.3.1).
   */
  @NonNull
  private StaticType _infer (@NonNull final IExpr aExpr, @NonNull final Scope aScope)
  {
    final StaticType ret = _inferExpr (aExpr, aScope);
    if (ret.isEmpty () && !_isExemptFromEmpty (aExpr))
      throw XPathException.of ("XPST0005", "The inferred static type of the expression is empty-sequence()");
    return ret;
  }

  /**
   * Infer the static type of a parsed expression and check the required types
   * of the static function calls in it.
   *
   * @param aRoot
   *        The parsed expression.
   * @param aStaticCtx
   *        The static context it was parsed with.
   * @return The inferred static type.
   * @throws XPathException
   *         XPTY0004 for an argument whose inferred static type is not
   *         accepted by its parameter type, XPST0005 for an expression whose
   *         inferred static type is <code>empty-sequence()</code>.
   */
  @NonNull
  static StaticType checkTypes (@NonNull final IExpr aRoot, @NonNull final StaticContext aStaticCtx)
  {
    return new XPathStaticTyping (aStaticCtx)._infer (aRoot, new Scope (new CommonsHashMap <> (), null));
  }
}

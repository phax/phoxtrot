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
package com.helger.phoxtrot.xslt.model;

import java.util.Arrays;
import java.util.Comparator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.pattern.PatternFinalNames;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * A mode: its template rules, ordered best first (import precedence, then
 * priority, then declaration order), and the properties of its xsl:mode
 * declaration. Filled by the compiler and immutable afterwards.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class Mode
{
  /** The name used for the unnamed mode. */
  public static final XdmQName UNNAMED = new XdmQName (XsltNames.NS_XSL, "unnamed");
  /** The minimum number of rules for which a mode gets a rule index. */
  public static final int MIN_INDEXED_RULES = 2;
  private static final int [] NO_RULES = new int [0];

  private final XdmQName m_aName;
  private EOnNoMatch m_eOnNoMatch = EOnNoMatch.TEXT_ONLY_COPY;
  private boolean m_bFailOnMultipleMatch;
  private boolean m_bWarningOnNoMatch;
  private boolean m_bWarningOnMultipleMatch;
  private String m_sVisibility;
  private String m_sTyped;
  private boolean m_bStreamable;
  private XslElement m_aDeclaration;
  private final ICommonsList <TemplateRule> m_aRules = new CommonsArrayList <> ();
  // The rule index (see getNamedRuleIndexes), null if the mode has none
  private ICommonsMap <XdmQName, int []> m_aElementRules;
  private ICommonsMap <XdmQName, int []> m_aAttributeRules;
  private int [] m_aUnnamedRules;

  /**
   * Create a mode without rules and with the default properties.
   *
   * @param aName
   *        The mode name, {@link #UNNAMED} for the unnamed mode. May not be
   *        <code>null</code>.
   */
  public Mode (@NonNull final XdmQName aName)
  {
    m_aName = aName;
  }

  /**
   * Get the mode name.
   *
   * @return The mode name, {@link #UNNAMED} for the unnamed mode.
   */
  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  /**
   * Check whether this is the unnamed mode.
   *
   * @return <code>true</code> if this is the unnamed mode.
   */
  public boolean isUnnamed ()
  {
    return UNNAMED.equals (m_aName);
  }

  /**
   * Get the on-no-match behaviour of the mode.
   *
   * @return The built-in template rules used when no rule matches (XSLT 3.0
   *         section 6.7); text-only-copy unless the xsl:mode declaration says
   *         otherwise.
   */
  @NonNull
  public EOnNoMatch getOnNoMatch ()
  {
    return m_eOnNoMatch;
  }

  /**
   * Set the on-no-match behaviour of the mode.
   *
   * @param eOnNoMatch
   *        The built-in template rules to use. May not be <code>null</code>.
   */
  public void setOnNoMatch (@NonNull final EOnNoMatch eOnNoMatch)
  {
    m_eOnNoMatch = eOnNoMatch;
  }

  /**
   * Check whether the mode fails on multiple matches.
   *
   * @return <code>true</code> for on-multiple-match="fail": the engine raises
   *         XTDE0540 when several template rules of the same import
   *         precedence and priority match an item.
   */
  public boolean isFailOnMultipleMatch ()
  {
    return m_bFailOnMultipleMatch;
  }

  /**
   * Set whether the mode fails on multiple matches.
   *
   * @param b
   *        <code>true</code> for on-multiple-match="fail".
   */
  public void setFailOnMultipleMatch (final boolean b)
  {
    m_bFailOnMultipleMatch = b;
  }

  /**
   * Get the declared warning-on-no-match value.
   *
   * @return The declared warning-on-no-match value. It is recorded only; the
   *         engine does not read it.
   */
  public boolean isWarningOnNoMatch ()
  {
    return m_bWarningOnNoMatch;
  }

  /**
   * Set the declared warning-on-no-match value.
   *
   * @param b
   *        The declared value.
   */
  public void setWarningOnNoMatch (final boolean b)
  {
    m_bWarningOnNoMatch = b;
  }

  /**
   * Get the declared warning-on-multiple-match value.
   *
   * @return The declared warning-on-multiple-match value. It is recorded
   *         only; the engine does not read it.
   */
  public boolean isWarningOnMultipleMatch ()
  {
    return m_bWarningOnMultipleMatch;
  }

  /**
   * Set the declared warning-on-multiple-match value.
   *
   * @param b
   *        The declared value.
   */
  public void setWarningOnMultipleMatch (final boolean b)
  {
    m_bWarningOnMultipleMatch = b;
  }

  /**
   * Get the declared visibility of the mode.
   *
   * @return The declared visibility or <code>null</code>.
   */
  @Nullable
  public String getVisibility ()
  {
    return m_sVisibility;
  }

  /**
   * Set the declared visibility of the mode.
   *
   * @param s
   *        The declared visibility. May be <code>null</code>.
   */
  public void setVisibility (@Nullable final String s)
  {
    m_sVisibility = s;
  }

  /**
   * Get the declared value of the typed attribute.
   *
   * @return The declared value of the typed attribute (yes, no, strict, lax,
   *         unspecified) or <code>null</code>.
   */
  @Nullable
  public String getTyped ()
  {
    return m_sTyped;
  }

  /**
   * Set the declared value of the typed attribute.
   *
   * @param s
   *        The declared value. May be <code>null</code>.
   */
  public void setTyped (@Nullable final String s)
  {
    m_sTyped = s;
  }

  /**
   * Check whether the mode is declared streamable.
   *
   * @return <code>true</code> if the mode is declared
   *         <code>streamable="yes"</code> (XSLT 3.0 section 6.6.1).
   */
  public boolean isStreamable ()
  {
    return m_bStreamable;
  }

  /**
   * Set whether the mode is declared streamable.
   *
   * @param bStreamable
   *        <code>true</code> for <code>streamable="yes"</code>.
   */
  public void setStreamable (final boolean bStreamable)
  {
    m_bStreamable = bStreamable;
  }

  /**
   * Get the xsl:mode declaration of the mode.
   *
   * @return The (highest precedence) xsl:mode declaration or
   *         <code>null</code>.
   */
  @Nullable
  public XslElement getDeclaration ()
  {
    return m_aDeclaration;
  }

  /**
   * Set the xsl:mode declaration of the mode.
   *
   * @param aDeclaration
   *        The (highest precedence) xsl:mode declaration. May be
   *        <code>null</code>.
   */
  public void setDeclaration (@Nullable final XslElement aDeclaration)
  {
    m_aDeclaration = aDeclaration;
  }

  /**
   * @return The array with the value appended (a new array).
   */
  @NonNull
  private static int [] _append (@Nullable final int [] aArray, final int nValue)
  {
    if (aArray == null)
      return new int [] { nValue };
    final int [] ret = Arrays.copyOf (aArray, aArray.length + 1);
    ret[aArray.length] = nValue;
    return ret;
  }

  /**
   * Build the rule index from the sorted rules: the positions of the rules
   * whose pattern names the elements or attributes it can match
   * ({@link com.helger.phoxtrot.xpath.pattern.XsltPattern#getFinalNames()}),
   * by these names, and the positions of all other rules. Every array is in
   * ascending order. A mode with fewer than {@link #MIN_INDEXED_RULES} rules
   * gets no index.
   */
  private void _buildRuleIndex ()
  {
    // Optimisation: the rule positions by the final names of their patterns, so that the rule
    // search tests only the rules that can match a node of that name
    m_aElementRules = null;
    m_aAttributeRules = null;
    m_aUnnamedRules = null;
    if (m_aRules.size () < MIN_INDEXED_RULES)
      return;
    final ICommonsMap <XdmQName, int []> aElementRules = new CommonsHashMap <> ();
    final ICommonsMap <XdmQName, int []> aAttributeRules = new CommonsHashMap <> ();
    int [] aUnnamedRules = NO_RULES;
    for (int i = 0; i < m_aRules.size (); ++i)
    {
      final PatternFinalNames aNames = m_aRules.get (i).getPattern ().pattern ().getFinalNames ();
      if (aNames == null)
        aUnnamedRules = _append (aUnnamedRules, i);
      else
      {
        // The name lists are distinct, so a rule is added once per name
        for (final XdmQName aName : aNames.elementNames ())
          aElementRules.put (aName, _append (aElementRules.get (aName), i));
        for (final XdmQName aName : aNames.attributeNames ())
          aAttributeRules.put (aName, _append (aAttributeRules.get (aName), i));
      }
    }
    m_aElementRules = aElementRules;
    m_aAttributeRules = aAttributeRules;
    m_aUnnamedRules = aUnnamedRules;
  }

  /**
   * Append a template rule. The order is irrelevant until
   * {@link #sortRules()} runs.
   *
   * @param aRule
   *        The rule to add.
   */
  public void addRule (@NonNull final TemplateRule aRule)
  {
    m_aRules.add (aRule);
  }

  /**
   * Sort the rules best first: descending by import precedence, then by
   * priority, then by declaration order. The first rule in this order whose
   * pattern matches is the one conflict resolution (XSLT 3.0 section 6.4)
   * selects, the later declared rule winning among equals, and the rules
   * after it are the candidates for xsl:next-match. Called once at the end of
   * compilation.
   */
  public void sortRules ()
  {
    m_aRules.sort (Comparator.comparingInt (TemplateRule::getPrecedence)
                             .thenComparingDouble (TemplateRule::getPriority)
                             .thenComparingInt (TemplateRule::getOrder)
                             .reversed ());
    _buildRuleIndex ();
  }

  /**
   * Get the template rules of the mode.
   *
   * @return The rules, best first (do not modify).
   */
  @NonNull
  public ICommonsList <TemplateRule> rules ()
  {
    return m_aRules;
  }

  /**
   * The rules of the index whose pattern names the item: for an element or
   * attribute the positions (in {@link #rules()}) of the rules whose pattern
   * can match only elements or attributes of the given names and names that
   * of the item; empty for any other item. Together with
   * {@link #getUnnamedRuleIndexes()} they are all rules that can match the
   * item.
   *
   * @param aItem
   *        The item to find rules for.
   * @return The positions in ascending order (do not modify), or
   *         <code>null</code> if the mode has no rule index: then every rule
   *         must be tested.
   */
  @Nullable
  public int [] getNamedRuleIndexes (@NonNull final IXdmItem aItem)
  {
    if (m_aUnnamedRules == null)
      return null;
    if (aItem instanceof final IXdmNode aNode)
    {
      final ICommonsMap <XdmQName, int []> aMap = aNode.isElement () ? m_aElementRules
                                                                    : aNode.isAttribute () ? m_aAttributeRules : null;
      if (aMap != null)
      {
        final int [] ret = aMap.get (aNode.getName ());
        if (ret != null)
          return ret;
      }
    }
    return NO_RULES;
  }

  /**
   * Get the rules of the index whose pattern names no element or attribute.
   *
   * @return The positions (in {@link #rules()}) of the rules whose pattern
   *         names no elements or attributes, in ascending order (do not
   *         modify); <code>null</code> if the mode has no rule index (see
   *         {@link #getNamedRuleIndexes(IXdmItem)}).
   */
  @Nullable
  public int [] getUnnamedRuleIndexes ()
  {
    return m_aUnnamedRules;
  }

  @Override
  public String toString ()
  {
    return isUnnamed () ? "#unnamed" : m_aName.getEQName ();
  }
}

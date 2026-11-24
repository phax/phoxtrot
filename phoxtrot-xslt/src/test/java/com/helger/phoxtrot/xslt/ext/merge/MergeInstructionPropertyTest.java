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
package com.helger.phoxtrot.xslt.ext.merge;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for xsl:merge ({@link MergeInstruction}, XSLT 3.0
 * section 15) against grouping the concatenated inputs in Java: one merge
 * group per distinct composite key in key order, the items of a group ordered
 * by merge source and then by input order, ascending and descending, with
 * sorted inputs and with sort-before-merge.
 *
 * @author Philip Helger
 */
public final class MergeInstructionPropertyTest
{
  /** One input item: numeric key, string key and its source position */
  private record Entry (int key, @NonNull String sub, int pos)
  {}

  private static final String [] SOURCES = { "A", "B", "C" };

  private static final IGenerator <List <Entry>> ENTRIES = Generators.lists (Generators.ints (0, 5)
                                                                                       .flatMap (k -> Generators.of ("a", "b", "B")
                                                                                                                .map (s -> new Entry (k.intValue (),
                                                                                                                                      s,
                                                                                                                                      0))),
                                                                             0,
                                                                             8);

  private static final Comparator <Entry> ASCENDING = Comparator.comparingInt (Entry::key).thenComparing (Entry::sub);

  private static final XsltExecutable SORTED_ASCENDING = _compile ("ascending", false);
  private static final XsltExecutable SORTED_DESCENDING = _compile ("descending", false);
  private static final XsltExecutable UNSORTED_ASCENDING = _compile ("ascending", true);

  @NonNull
  private static XsltExecutable _compile (@NonNull final String sOrder, final boolean bSortBeforeMerge)
  {
    final StringBuilder aSB = new StringBuilder (XsltTestHelper.SHEET).append ("<xsl:output method='text'/>")
                                                                     .append ("<xsl:template match='/'><xsl:merge>");
    for (final String sSource : SOURCES)
      aSB.append ("<xsl:merge-source name='")
         .append (sSource)
         .append ("' select=\"r/s[@n = '")
         .append (sSource)
         .append ("']/e\" sort-before-merge='")
         .append (bSortBeforeMerge ? "yes" : "no")
         .append ("'><xsl:merge-key select='xs:integer(@k)' order='")
         .append (sOrder)
         .append ("'/><xsl:merge-key select='string(@s)' order='")
         .append (sOrder)
         .append ("' collation='http://www.w3.org/2005/xpath-functions/collation/codepoint'/></xsl:merge-source>");
    aSB.append ("<xsl:merge-action>")
       .append ("<xsl:value-of select='current-merge-key()' separator='-'/>:")
       .append ("<xsl:value-of select=\"current-merge-group() ! (@id)\" separator=' '/>")
       .append ("<xsl:value-of select=\"'', count(current-merge-group('A')), count(current-merge-group('B')), count(current-merge-group('C'))\" separator='/'/>")
       .append (";</xsl:merge-action></xsl:merge></xsl:template>")
       .append (XsltTestHelper.END);
    return XsltCompiler.compile (aSB.toString (), new XsltCompileOptions ());
  }

  /** The source document with one element per input, items in the given order */
  @NonNull
  private static String _source (@NonNull final List <List <Entry>> aInputs)
  {
    final StringBuilder aSB = new StringBuilder ("<r>");
    for (int i = 0; i < SOURCES.length; ++i)
    {
      aSB.append ("<s n='").append (SOURCES[i]).append ("'>");
      for (final Entry aEntry : aInputs.get (i))
        aSB.append ("<e k='")
           .append (aEntry.key ())
           .append ("' s='")
           .append (aEntry.sub ())
           .append ("' id='")
           .append (SOURCES[i])
           .append (aEntry.pos ())
           .append ("'/>");
      aSB.append ("</s>");
    }
    return aSB.append ("</r>").toString ();
  }

  @NonNull
  private static List <Entry> _numbered (@NonNull final List <Entry> aEntries)
  {
    final List <Entry> ret = new ArrayList <> ();
    for (int i = 0; i < aEntries.size (); ++i)
      ret.add (new Entry (aEntries.get (i).key (), aEntries.get (i).sub (), i));
    return ret;
  }

  /**
   * The expected output: the groups by composite key in key order, each with
   * the IDs by source and then by input order and the count per source.
   */
  @NonNull
  private static String _expected (@NonNull final List <List <Entry>> aInputs, final boolean bDescending)
  {
    final Map <Entry, List <String> []> aGroups = new TreeMap <> (bDescending ? ASCENDING.reversed () : ASCENDING);
    for (int i = 0; i < SOURCES.length; ++i)
      for (final Entry aEntry : aInputs.get (i))
      {
        @SuppressWarnings ("unchecked")
        final List <String> [] aPerSource = aGroups.computeIfAbsent (aEntry,
                                                                     x -> new List [] { new ArrayList <> (),
                                                                                        new ArrayList <> (),
                                                                                        new ArrayList <> () });
        aPerSource[i].add (SOURCES[i] + aEntry.pos ());
      }
    final StringBuilder aSB = new StringBuilder ();
    for (final Map.Entry <Entry, List <String> []> aGroup : aGroups.entrySet ())
    {
      aSB.append (aGroup.getKey ().key ()).append ('-').append (aGroup.getKey ().sub ()).append (':');
      final List <String> aAll = new ArrayList <> ();
      for (final List <String> aPerSource : aGroup.getValue ())
        aAll.addAll (aPerSource);
      aSB.append (String.join (" ", aAll));
      for (final List <String> aPerSource : aGroup.getValue ())
        aSB.append ('/').append (aPerSource.size ());
      aSB.append (';');
    }
    return aSB.toString ();
  }

  /**
   * Merging inputs that are sorted by the merge keys gives the groups of the
   * Java grouping, ascending and descending.
   */
  @Test
  public void testMergeOfSortedInputs ()
  {
    new PropertyChecker ().setTries (1000)
                          .forAll (ENTRIES, ENTRIES, ENTRIES, Generators.booleans (), (aA, aB, aC, aDescending) -> {
                            final boolean bDescending = aDescending.booleanValue ();
                            final List <List <Entry>> aInputs = new ArrayList <> ();
                            for (final List <Entry> aEntries : List.of (aA, aB, aC))
                            {
                              // Sort first (stable), then number in input order
                              final List <Entry> aSorted = new ArrayList <> (aEntries);
                              aSorted.sort (bDescending ? ASCENDING.reversed () : ASCENDING);
                              aInputs.add (_numbered (aSorted));
                            }
                            final String sSource = _source (aInputs);
                            final XsltExecutable aExec = bDescending ? SORTED_DESCENDING : SORTED_ASCENDING;
                            assertEquals (sSource + (bDescending ? " descending" : " ascending"),
                                          _expected (aInputs, bDescending),
                                          aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                                               .principalSerialized ());
                          });
  }

  /**
   * With sort-before-merge="yes" unsorted inputs are sorted stably first, so
   * the items of a group stay in input order within their source.
   */
  @Test
  public void testMergeWithSortBeforeMerge ()
  {
    new PropertyChecker ().setTries (1000).forAll (ENTRIES, ENTRIES, ENTRIES, (aA, aB, aC) -> {
      final List <List <Entry>> aInputs = List.of (_numbered (aA), _numbered (aB), _numbered (aC));
      final String sSource = _source (aInputs);
      assertEquals (sSource,
                    _expected (aInputs, false),
                    UNSORTED_ASCENDING.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (sSource)))
                                      .principalSerialized ());
    });
  }
}

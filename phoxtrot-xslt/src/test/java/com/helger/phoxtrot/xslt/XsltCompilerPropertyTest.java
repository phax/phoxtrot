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
package com.helger.phoxtrot.xslt;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.phoxtrot.xpath.XPathException;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Robustness of {@link XsltCompiler} and of running what it compiles: valid
 * stylesheets mutated by inserting, replacing, deleting and duplicating
 * pieces either compile or fail with an {@link XPathException} carrying an
 * error code; a compiled one either runs or fails the same way. No other
 * exception (such as a NullPointerException, an
 * ArrayIndexOutOfBoundsException or a ClassCastException) may escape.
 *
 * @author Philip Helger
 */
public final class XsltCompilerPropertyTest
{
  private static final String SOURCE = "<r><a k='1' m='x y'>t<b/>2</a><b k='2'><a k='1'/></b><!--c--><?pi d?><c>3</c></r>";

  /** Valid stylesheets using a broad range of instructions, declarations and expressions */
  private static final String [] SHEETS = { XsltTestHelper.SHEET +
                                            "<xsl:output method='xml' indent='yes'/>" +
                                            "<xsl:mode on-no-match='shallow-copy'/>" +
                                            "<xsl:template match='a[@k]'><A n='{count(preceding::a)}'><xsl:apply-templates select='@*|node()'/></A></xsl:template>" +
                                            "<xsl:template match='comment()'/>" +
                                            XsltTestHelper.END,
                                            XsltTestHelper.SHEET +
                                            "<xsl:output method='text'/>" +
                                            "<xsl:key name='k' match='a|b' use='@k'/>" +
                                            "<xsl:variable name='v' select='(1 to 5) ! (. * 2)'/>" +
                                            "<xsl:function name='f:sq' xmlns:f='urn:f' as='xs:integer'><xsl:param name='n' as='xs:integer'/><xsl:sequence select='$n * $n'/></xsl:function>" +
                                            "<xsl:template match='/' xmlns:f='urn:f'>" +
                                            "<xsl:value-of select='sum($v), f:sq(3), count(key(\"k\", \"1\"))' separator=','/>" +
                                            "<xsl:for-each select='//a'><xsl:sort select='@k' data-type='number' order='descending'/><xsl:number level='any' format='i'/>;</xsl:for-each>" +
                                            "</xsl:template>" +
                                            XsltTestHelper.END,
                                            XsltTestHelper.SHEET +
                                            "<xsl:output method='xml'/>" +
                                            "<xsl:template match='/'><out>" +
                                            "<xsl:for-each-group select='//*' group-by='local-name()'><g n='{current-grouping-key()}' c='{count(current-group())}'/></xsl:for-each-group>" +
                                            "<xsl:iterate select='//a'><xsl:param name='s' select='0'/><xsl:on-completion><s v='{$s}'/></xsl:on-completion>" +
                                            "<xsl:next-iteration><xsl:with-param name='s' select='$s + 1'/></xsl:next-iteration></xsl:iterate>" +
                                            "<xsl:analyze-string select='string(.)' regex='[0-9]+'><xsl:matching-substring><n><xsl:value-of select='.'/></n></xsl:matching-substring>" +
                                            "<xsl:non-matching-substring><xsl:value-of select='upper-case(.)'/></xsl:non-matching-substring></xsl:analyze-string>" +
                                            "</out></xsl:template>" +
                                            XsltTestHelper.END,
                                            XsltTestHelper.SHEET +
                                            "<xsl:output method='json'/>" +
                                            "<xsl:param name='p' as='xs:string' select=\"'x'\"/>" +
                                            "<xsl:template match='/'>" +
                                            "<xsl:try><xsl:sequence select=\"map { 'a': array { 1, 2, $p }, 'b': (//a)[1]/@k => string() }\"/>" +
                                            "<xsl:catch xmlns:err='http://www.w3.org/2005/xqt-errors'><xsl:sequence select=\"map { 'err': $err:code => string() }\"/></xsl:catch></xsl:try>" +
                                            "</xsl:template>" +
                                            XsltTestHelper.END,
                                            XsltTestHelper.SHEET +
                                            "<xsl:output method='html'/>" +
                                            "<xsl:accumulator name='cnt' initial-value='0'><xsl:accumulator-rule match='a' select='$value + 1'/></xsl:accumulator>" +
                                            "<xsl:mode use-accumulators='cnt'/>" +
                                            "<xsl:template match='/'><html><body><xsl:apply-templates select='//a'/>" +
                                            "<xsl:choose><xsl:when test='count(//b) gt 1'><p>many</p></xsl:when><xsl:otherwise><p>few</p></xsl:otherwise></xsl:choose>" +
                                            "</body></html></xsl:template>" +
                                            "<xsl:template match='a'><xsl:element name='h{accumulator-after(\"cnt\")}'><xsl:attribute name='id' select='generate-id()'/>" +
                                            "<xsl:copy-of select='@*'/><xsl:comment>x</xsl:comment><xsl:processing-instruction name='p'>q</xsl:processing-instruction>" +
                                            "</xsl:element><xsl:next-match/></xsl:template>" +
                                            XsltTestHelper.END };

  /** Pieces with a meaning in XML, XPath or XSLT */
  private static final IGenerator <String> PIECES = Generators.of ("<",
                                                                   ">",
                                                                   "'",
                                                                   "\"",
                                                                   "/",
                                                                   "{",
                                                                   "}",
                                                                   "(",
                                                                   ")",
                                                                   "[",
                                                                   "]",
                                                                   "@",
                                                                   "$",
                                                                   ":",
                                                                   "=",
                                                                   ",",
                                                                   "*",
                                                                   "|",
                                                                   "..",
                                                                   "!",
                                                                   "#",
                                                                   "?",
                                                                   "0",
                                                                   "-1",
                                                                   " ",
                                                                   "xsl:",
                                                                   "&amp;",
                                                                   "<!--",
                                                                   "]]>",
                                                                   "\u00e4",
                                                                   "\ud83d\ude00",
                                                                   "<xsl:sequence select='.'/>",
                                                                   "<xsl:value-of/>",
                                                                   "</xsl:template>",
                                                                   " mode='m'",
                                                                   " as='xs:integer'",
                                                                   "current-group()",
                                                                   "position()",
                                                                   "map{}",
                                                                   "function($x){$x}");

  @NonNull
  private static String _mutate (@NonNull final ChoiceSequence aChoices,
                                 @NonNull final String sIn,
                                 @NonNull final IGenerator <String> aPieces)
  {
    final int nLen = sIn.length ();
    final int nPos = (int) aChoices.draw (nLen);
    final int nSpan = Math.min (nLen - nPos, 1 + (int) aChoices.draw (15));
    return switch ((int) aChoices.draw (4))
    {
      case 0 -> sIn.substring (0, nPos) + aPieces.generate (aChoices) + sIn.substring (nPos);
      case 1 -> sIn.substring (0, nPos) + aPieces.generate (aChoices) + sIn.substring (Math.min (nLen, nPos + nSpan));
      case 2 -> sIn.substring (0, nPos) + sIn.substring (Math.min (nLen, nPos + nSpan));
      case 3 -> sIn.substring (0, nPos) + sIn.substring (nPos, nPos + nSpan) + sIn.substring (nPos);
      default -> sIn.substring (0, nPos);
    };
  }

  /**
   * Pieces with a meaning in XPath or XSLT that keep an attribute value
   * well-formed
   */
  private static final IGenerator <String> EXPRESSION_PIECES = Generators.of ("/",
                                                                              "//",
                                                                              "{",
                                                                              "}",
                                                                              "{{",
                                                                              "(",
                                                                              ")",
                                                                              "()",
                                                                              "[",
                                                                              "]",
                                                                              "[1]",
                                                                              "@",
                                                                              "$",
                                                                              "$v",
                                                                              ":",
                                                                              "=",
                                                                              ",",
                                                                              "*",
                                                                              "|",
                                                                              ".",
                                                                              "..",
                                                                              "!",
                                                                              "#",
                                                                              "?",
                                                                              "0",
                                                                              "-1",
                                                                              "1e300",
                                                                              " ",
                                                                              "&apos;",
                                                                              "&quot;",
                                                                              "&lt;",
                                                                              " and ",
                                                                              " to ",
                                                                              " instance of xs:integer",
                                                                              " cast as xs:date",
                                                                              "=>",
                                                                              "\u00e4",
                                                                              "current()",
                                                                              "current-group()",
                                                                              "position()",
                                                                              "last()",
                                                                              "map{}",
                                                                              "array{1}",
                                                                              "?*",
                                                                              "function($x){$x}",
                                                                              "#1",
                                                                              "xs:integer",
                                                                              "node()",
                                                                              "text()",
                                                                              "ancestor::",
                                                                              "following-sibling::*");

  /** The values of attributes, the expressions, patterns and AVTs of a stylesheet */
  private static final Pattern ATTRIBUTE_VALUE = Pattern.compile ("='([^']*)'|=\"([^\"]*)\"");

  /** A valid stylesheet with one to three mutations of its text */
  private static final IGenerator <String> MUTATED_SHEETS = aChoices -> {
    String ret = SHEETS[(int) aChoices.draw (SHEETS.length - 1)];
    final int nMutations = 1 + (int) aChoices.draw (2);
    for (int i = 0; i < nMutations; ++i)
      ret = _mutate (aChoices, ret, PIECES);
    return ret;
  };

  /** A valid stylesheet with one to three mutations inside attribute values */
  private static final IGenerator <String> MUTATED_EXPRESSIONS = aChoices -> {
    String ret = SHEETS[(int) aChoices.draw (SHEETS.length - 1)];
    final int nMutations = 1 + (int) aChoices.draw (2);
    for (int i = 0; i < nMutations; ++i)
    {
      final List <int []> aSpans = new ArrayList <> ();
      final Matcher aMatcher = ATTRIBUTE_VALUE.matcher (ret);
      while (aMatcher.find ())
      {
        final int nGroup = aMatcher.group (1) != null ? 1 : 2;
        aSpans.add (new int [] { aMatcher.start (nGroup), aMatcher.end (nGroup) });
      }
      final int [] aSpan = aSpans.get ((int) aChoices.draw (aSpans.size () - 1));
      ret = ret.substring (0, aSpan[0]) +
            _mutate (aChoices, ret.substring (aSpan[0], aSpan[1]), EXPRESSION_PIECES) +
            ret.substring (aSpan[1]);
    }
    return ret;
  };

  /** The unmutated stylesheets compile and run. */
  @Test
  public void testPoolIsValid ()
  {
    for (final String sSheet : SHEETS)
      assertNotNull (XsltTestHelper.transform (sSheet, SOURCE, null));
  }

  /**
   * A mutated stylesheet compiles or fails with an error code, and a compiled
   * one runs or fails with an error code. Runs are bounded by a step limit and
   * a deadline.
   */
  @Test
  public void testMutatedStylesheetsFailCleanly ()
  {
    new PropertyChecker ().setTries (5000).forAll (Generators.oneOf (MUTATED_SHEETS, MUTATED_EXPRESSIONS), sSheet -> {
      final XsltExecutable aExec;
      try
      {
        aExec = XsltCompiler.compile (sSheet, new XsltCompileOptions ());
      }
      catch (final XPathException ex)
      {
        assertNotNull (ex.getCode ());
        return;
      }
      final long nDeadline = System.nanoTime () + 2_000_000_000L;
      try
      {
        aExec.transform (new XsltTransformRequest ().setGlobalContextItem (XsltTestHelper.parse (SOURCE))
                                                    .setMaxSteps (100_000)
                                                    .setInterrupt ( () -> System.nanoTime () > nDeadline))
             .principalSerialized ();
      }
      catch (final XPathException ex)
      {
        assertNotNull (ex.getCode ());
      }
    });
  }
}

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
package com.helger.phoxtrot.xslt.instr;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.RandomXmlDocument;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltCompiler;
import com.helger.phoxtrot.xslt.XsltExecutable;
import com.helger.phoxtrot.xslt.XsltTestHelper;
import com.helger.phoxtrot.xslt.XsltTransformRequest;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the construction of simple content ({@link SimpleContent},
 * XSLT 3.0 section 5.7.2) from random sequences of strings and text nodes,
 * zero-length ones included: zero-length text nodes are discarded, adjacent
 * text nodes merged, and the strings joined with the separator of the
 * instruction. Checked for xsl:value-of (with select and an explicit or the
 * default separator, and with a sequence constructor), text and attribute
 * value templates, xsl:attribute, xsl:comment and
 * xsl:processing-instruction (leading spaces removed).
 *
 * @author Philip Helger
 */
public final class SimpleContentPropertyTest
{
  /** One item: a string or a text node with this value */
  private record Item (boolean textNode, @NonNull String value)
  {}

  private static final IGenerator <List <Item>> ITEMS = Generators.lists (Generators.booleans ()
                                                                                    .flatMap (b -> Generators.of ("",
                                                                                                                  "a",
                                                                                                                  " ",
                                                                                                                  "b c")
                                                                                                             .map (s -> new Item (b.booleanValue (),
                                                                                                                                  s))),
                                                                          0,
                                                                          6);

  private static final XsltExecutable SHEET = XsltCompiler.compile (XsltTestHelper.SHEET.replace ("<xsl:stylesheet ",
                                                                                                  "<xsl:stylesheet xmlns:f='urn:f' exclude-result-prefixes='f xs' ") +
                                                                    "<xsl:output method='xml' omit-xml-declaration='yes'/>" +
                                                                    "<xsl:param name='spec' as='xs:string*'/>" +
                                                                    "<xsl:function name='f:text' as='text()'><xsl:param name='s' as='xs:string'/>" +
                                                                    "<xsl:value-of select='$s'/></xsl:function>" +
                                                                    "<xsl:variable name='seq' select=\"$spec ! (if (starts-with(., 't')) then f:text(substring(., 2)) else substring(., 2))\"/>" +
                                                                    "<xsl:template name='xsl:initial-template'><r>" +
                                                                    "<v1><xsl:value-of select='$seq' separator='-'/></v1>" +
                                                                    "<v2><xsl:value-of select='$seq'/></v2>" +
                                                                    "<v3><xsl:value-of><xsl:sequence select='$seq'/></xsl:value-of></v3>" +
                                                                    "<v4 xsl:expand-text='yes'>{$seq}</v4>" +
                                                                    "<v5 a='{$seq}'/>" +
                                                                    "<v6><xsl:attribute name='a' select='$seq' separator='-'/></v6>" +
                                                                    "<v7><xsl:comment select='$seq'/></v7>" +
                                                                    "<v8><xsl:processing-instruction name='pi' select='$seq'/></v8>" +
                                                                    "</r></xsl:template>" +
                                                                    XsltTestHelper.END,
                                                                    new XsltCompileOptions ());

  /** The rules of section 5.7.2 in Java */
  @NonNull
  private static String _simpleContent (@NonNull final List <Item> aItems, @NonNull final String sSeparator)
  {
    final List <String> aStrings = new ArrayList <> ();
    boolean bLastWasText = false;
    for (final Item aItem : aItems)
    {
      if (aItem.textNode ())
      {
        // Zero-length text nodes are discarded, adjacent ones merged
        if (aItem.value ().isEmpty ())
          continue;
        if (bLastWasText)
          aStrings.set (aStrings.size () - 1, aStrings.getLast () + aItem.value ());
        else
          aStrings.add (aItem.value ());
        bLastWasText = true;
      }
      else
      {
        aStrings.add (aItem.value ());
        bLastWasText = false;
      }
    }
    return String.join (sSeparator, aStrings);
  }

  @NonNull
  private static String _getChildText (@NonNull final Node aRoot, @NonNull final String sName)
  {
    return ((Element) aRoot).getElementsByTagName (sName).item (0).getTextContent ();
  }

  @NonNull
  private static String _getChildNodeValue (@NonNull final Node aRoot, @NonNull final String sName)
  {
    final Node aChild = ((Element) aRoot).getElementsByTagName (sName).item (0).getFirstChild ();
    return aChild == null ? "" : aChild.getNodeValue ();
  }

  /**
   * Every instruction gives the string of the rules of section 5.7.2 with its
   * separator.
   */
  @Test
  public void testSimpleContentMatchesSpecification ()
  {
    new PropertyChecker ().setTries (1000).forAll (ITEMS, aItems -> {
      final List <IXdmItem> aSpec = new ArrayList <> ();
      for (final Item aItem : aItems)
        aSpec.add (XdmAtomic.string ((aItem.textNode () ? "t" : "s") + aItem.value ()));
      final XsltTransformRequest aRequest = new XsltTransformRequest ().setInitialTemplate (new XdmQName ("http://www.w3.org/1999/XSL/Transform",
                                                                                                          "initial-template"));
      aRequest.params ().put (new XdmQName ("spec"), XdmSequence.of (aSpec));
      final String sResult = SHEET.transform (aRequest).principalSerialized ();
      final Node aRoot = RandomXmlDocument.parseWithJdk (sResult).getFirstChild ();
      final String sContext = aItems + " gave " + sResult;
      final String sSpace = _simpleContent (aItems, " ");
      assertEquals (sContext, _simpleContent (aItems, "-"), _getChildText (aRoot, "v1"));
      assertEquals (sContext, sSpace, _getChildText (aRoot, "v2"));
      assertEquals (sContext, _simpleContent (aItems, ""), _getChildText (aRoot, "v3"));
      assertEquals (sContext, sSpace, _getChildText (aRoot, "v4"));
      assertEquals (sContext, sSpace, ((Element) ((Element) aRoot).getElementsByTagName ("v5").item (0)).getAttribute ("a"));
      assertEquals (sContext, _simpleContent (aItems, "-"), ((Element) ((Element) aRoot).getElementsByTagName ("v6").item (0)).getAttribute ("a"));
      assertEquals (sContext, sSpace, _getChildNodeValue (aRoot, "v7"));
      assertEquals (sContext, sSpace.stripLeading (), _getChildNodeValue (aRoot, "v8"));
    });
  }
}

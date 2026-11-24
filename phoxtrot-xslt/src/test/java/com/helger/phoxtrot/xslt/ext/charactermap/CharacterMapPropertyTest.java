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
package com.helger.phoxtrot.xslt.ext.charactermap;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
 * Property based tests for character maps ({@link CharacterMapExtension},
 * XSLT 3.0 section 26.1, Serialization 3.1 section 11) on random text: every
 * mapped character is replaced exactly once (a mapping that swaps two
 * characters is not applied again to its own result), the strings of the map
 * are written without escaping, a map's own mapping wins over the one of a map
 * it uses, and the unmapped characters are escaped as usual.
 *
 * @author Philip Helger
 */
public final class CharacterMapPropertyTest
{
  /** The effective mapping of the map "outer" */
  private static final Map <Character, String> MAPPING = Map.of (Character.valueOf ('a'),
                                                                  "b",
                                                                  Character.valueOf ('b'),
                                                                  "a",
                                                                  Character.valueOf ('#'),
                                                                  "<i/>",
                                                                  Character.valueOf ('€'),
                                                                  "",
                                                                  Character.valueOf ('ä'),
                                                                  "&#228;");

  private static final IGenerator <String> TEXTS = Generators.lists (Generators.of ("a",
                                                                                    "b",
                                                                                    "x",
                                                                                    "#",
                                                                                    "€",
                                                                                    "ä",
                                                                                    "<",
                                                                                    "&",
                                                                                    ">",
                                                                                    "]]>",
                                                                                    "\r",
                                                                                    "\n",
                                                                                    " ",
                                                                                    "😀"),
                                                                     0,
                                                                     12)
                                                             .map (l -> String.join ("", l));

  /**
   * The map "inner" maps a, "#" and the euro sign; "outer" uses it and adds b,
   * the a-umlaut, and its own mapping of a, which wins over inner's.
   */
  private static final String MAPS = "<xsl:character-map name='inner'>" +
                                     "<xsl:output-character character='a' string='[inner]'/>" +
                                     "<xsl:output-character character='#' string='&lt;i/&gt;'/>" +
                                     "<xsl:output-character character='&#x20ac;' string=''/>" +
                                     "</xsl:character-map>" +
                                     "<xsl:character-map name='outer' use-character-maps='inner'>" +
                                     "<xsl:output-character character='a' string='b'/>" +
                                     "<xsl:output-character character='b' string='a'/>" +
                                     "<xsl:output-character character='&#xe4;' string='&amp;#228;'/>" +
                                     "</xsl:character-map>" +
                                     "<xsl:param name='s' as='xs:string'/>";

  private static final XsltExecutable TEXT = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                   MAPS +
                                                                   "<xsl:output method='text' use-character-maps='outer'/>" +
                                                                   "<xsl:template name='xsl:initial-template'><xsl:value-of select='$s'/></xsl:template>" +
                                                                   XsltTestHelper.END,
                                                                   new XsltCompileOptions ());

  private static final XsltExecutable XML = XsltCompiler.compile (XsltTestHelper.SHEET +
                                                                  MAPS +
                                                                  "<xsl:output method='xml' omit-xml-declaration='yes' use-character-maps='outer'/>" +
                                                                  "<xsl:template name='xsl:initial-template'><p><xsl:value-of select='$s'/></p></xsl:template>" +
                                                                  XsltTestHelper.END,
                                                                  new XsltCompileOptions ());

  @NonNull
  private static String _run (@NonNull final XsltExecutable aExec, @NonNull final String sText)
  {
    final XsltTransformRequest aRequest = new XsltTransformRequest ().setInitialTemplate (new XdmQName ("http://www.w3.org/1999/XSL/Transform",
                                                                                                        "initial-template"));
    aRequest.params ().put (new XdmQName ("s"), XdmSequence.of (XdmAtomic.string (sText)));
    return aExec.transform (aRequest).principalSerialized ();
  }

  /**
   * The text output method writes every character of the text through the
   * map exactly once.
   */
  @Test
  public void testTextMethodMapsOnce ()
  {
    new PropertyChecker ().setTries (1000).forAll (TEXTS, sText -> {
      final StringBuilder aExpected = new StringBuilder ();
      for (int i = 0; i < sText.length (); ++i)
      {
        final String sMapped = MAPPING.get (Character.valueOf (sText.charAt (i)));
        if (sMapped != null)
          aExpected.append (sMapped);
        else
          aExpected.append (sText.charAt (i));
      }
      assertEquals (sText, aExpected.toString (), _run (TEXT, sText));
    });
  }

  /**
   * The xml output method writes the strings of the map unescaped (the
   * mapped "#" becomes an element, the mapped a-umlaut a character
   * reference) and escapes every other character, so the result parses to
   * the expected tree.
   */
  @Test
  public void testXmlMethodParsesToMappedTree ()
  {
    new PropertyChecker ().setTries (1000).forAll (TEXTS, sText -> {
      final DocumentBuilderFactory aDBF = DocumentBuilderFactory.newInstance ();
      aDBF.setNamespaceAware (true);
      final Document aExpected = aDBF.newDocumentBuilder ().newDocument ();
      final Element aP = aExpected.createElementNS (null, "p");
      aExpected.appendChild (aP);
      final StringBuilder aText = new StringBuilder ();
      for (int i = 0; i < sText.length (); ++i)
      {
        final char c = sText.charAt (i);
        if (c == '#')
        {
          aP.appendChild (aExpected.createTextNode (aText.toString ()));
          aText.setLength (0);
          aP.appendChild (aExpected.createElementNS (null, "i"));
        }
        else
          if (c == 'ä')
            aText.append (c);
          else
          {
            final String sMapped = MAPPING.get (Character.valueOf (c));
            aText.append (sMapped != null ? sMapped : String.valueOf (c));
          }
      }
      aP.appendChild (aExpected.createTextNode (aText.toString ()));
      aExpected.normalize ();
      final String sResult = _run (XML, sText);
      final Node aActual = RandomXmlDocument.parseWithJdk (sResult);
      assertEquals (sText + " gave " + sResult, RandomXmlDocument.getCanonical (aExpected), RandomXmlDocument.getCanonical (aActual));
    });
  }
}

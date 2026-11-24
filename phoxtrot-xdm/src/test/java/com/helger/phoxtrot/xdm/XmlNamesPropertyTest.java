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
package com.helger.phoxtrot.xdm;

import static org.junit.Assert.assertEquals;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.string.StringHex;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the string checks of {@link XmlNames} (the per character predicates are
 * checked exhaustively in {@link XmlNamesTest}): on strings of UTF-16 units around the range
 * boundaries of the Name and NCName productions of XML 1.0 fifth edition and Namespaces in XML,
 * including surrogate pairs, lone and reversed surrogates and colons, <code>isName</code> and
 * <code>isNCName</code> agree with a direct evaluation of the productions over the code points.
 *
 * @author Philip Helger
 */
public final class XmlNamesPropertyTest
{
  /**
   * UTF-16 units: ASCII name and non-name characters, the colon, the boundaries of the NameStartChar
   * and NameChar ranges and both kinds of surrogates
   */
  private static final char [] UNITS = { 'a',
                                         'Z',
                                         '_',
                                         ':',
                                         '-',
                                         '.',
                                         '0',
                                         ' ',
                                         '!',
                                         0xB7,
                                         0xBF,
                                         0xC0,
                                         0xD6,
                                         0xD7,
                                         0xF7,
                                         0x2FF,
                                         0x300,
                                         0x36F,
                                         0x370,
                                         0x37E,
                                         0x37F,
                                         0x200B,
                                         0x200C,
                                         0x200E,
                                         0x203F,
                                         0x2041,
                                         0x2070,
                                         0x218F,
                                         0x2190,
                                         0x2FF0,
                                         0x3000,
                                         0x3001,
                                         0xD7FF,
                                         0xD800,
                                         0xDB7F,
                                         0xDB80,
                                         0xDBFF,
                                         0xDC00,
                                         0xDFFF,
                                         0xE000,
                                         0xF8FF,
                                         0xF900,
                                         0xFDD0,
                                         0xFDF0,
                                         0xFFFD,
                                         0xFFFE };

  private static boolean _isNameStartChar (final int c)
  {
    return c == ':' ||
           (c >= 'A' && c <= 'Z') ||
           c == '_' ||
           (c >= 'a' && c <= 'z') ||
           (c >= 0xC0 && c <= 0xD6) ||
           (c >= 0xD8 && c <= 0xF6) ||
           (c >= 0xF8 && c <= 0x2FF) ||
           (c >= 0x370 && c <= 0x37D) ||
           (c >= 0x37F && c <= 0x1FFF) ||
           (c >= 0x200C && c <= 0x200D) ||
           (c >= 0x2070 && c <= 0x218F) ||
           (c >= 0x2C00 && c <= 0x2FEF) ||
           (c >= 0x3001 && c <= 0xD7FF) ||
           (c >= 0xF900 && c <= 0xFDCF) ||
           (c >= 0xFDF0 && c <= 0xFFFD) ||
           (c >= 0x10000 && c <= 0xEFFFF);
  }

  private static boolean _isNameChar (final int c)
  {
    return _isNameStartChar (c) ||
           c == '-' ||
           c == '.' ||
           (c >= '0' && c <= '9') ||
           c == 0xB7 ||
           (c >= 0x300 && c <= 0x36F) ||
           (c >= 0x203F && c <= 0x2040);
  }

  /**
   * @return <code>true</code> if the code points of the string match <code>Name</code> (or, without
   *         colons, <code>NCName</code>). A lone surrogate is a code point of its own and matches
   *         nothing.
   */
  private static boolean _isName (@NonNull final String s, final boolean bNC)
  {
    if (s.isEmpty ())
      return false;
    final int [] aCodePoints = s.codePoints ().toArray ();
    for (int i = 0; i < aCodePoints.length; ++i)
    {
      final int c = aCodePoints[i];
      if (bNC && c == ':')
        return false;
      if (i == 0 ? !_isNameStartChar (c) : !_isNameChar (c))
        return false;
    }
    return true;
  }

  @NonNull
  private static IGenerator <String> _strings ()
  {
    return Generators.lists (Generators.ints (0, UNITS.length - 1), 0, 6).map (aIndexes -> {
      final StringBuilder aSB = new StringBuilder ();
      for (final Integer aIndex : aIndexes)
        aSB.append (UNITS[aIndex.intValue ()]);
      return aSB.toString ();
    });
  }

  /**
   * isName and isNCName agree with the productions on strings of boundary units, also with
   * surrogate pairs and lone surrogates.
   */
  @Test
  public void testNameStrings ()
  {
    new PropertyChecker ().setTries (2000).forAll (_strings (), s -> {
      final StringBuilder aHex = new StringBuilder ();
      for (final char c : s.toCharArray ())
        aHex.append (StringHex.getHexStringLeadingZero (c, 4)).append (' ');
      assertEquals ("Name " + aHex, Boolean.valueOf (_isName (s, false)), Boolean.valueOf (XmlNames.isName (s)));
      assertEquals ("NCName " + aHex, Boolean.valueOf (_isName (s, true)), Boolean.valueOf (XmlNames.isNCName (s)));
    });
  }
}

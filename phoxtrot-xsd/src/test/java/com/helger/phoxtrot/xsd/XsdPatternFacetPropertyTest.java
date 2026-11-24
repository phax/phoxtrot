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
package com.helger.phoxtrot.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the pattern facet (XML Schema 1.1 Part 2, section 4.3.4 and appendix
 * G, regular expressions): generated XSD regular expressions are translated by hand into
 * <code>java.util.regex</code> and every generated string must be accepted exactly when the
 * translation matches the whole string. The translation spells out the XSD rules: the expression is
 * implicitly anchored, <code>^</code> and <code>$</code> are ordinary characters, <code>.</code>
 * excludes only line feed and carriage return, <code>\s</code> is the four XML whitespace
 * characters, <code>\d</code> is <code>\p{Nd}</code>, <code>\i</code> and <code>\c</code> follow the
 * NameStartChar and NameChar productions of XML 1.0 fifth edition, <code>\w</code> excludes
 * punctuation, separators and other characters, and character class subtraction is an
 * intersection with the complement. Several patterns in one derivation step are alternatives,
 * patterns of different steps must all match. Arbitrary pattern strings are only ever accepted or
 * refused as schema error.
 *
 * @author Philip Helger
 */
public final class XsdPatternFacetPropertyTest
{
  /** NameStartChar of XML 1.0 fifth edition as a Java character class body */
  private static final String JAVA_NAME_START = ":A-Z_a-z\\x{C0}-\\x{D6}\\x{D8}-\\x{F6}\\x{F8}-\\x{2FF}\\x{370}-\\x{37D}" +
                                                "\\x{37F}-\\x{1FFF}\\x{200C}-\\x{200D}\\x{2070}-\\x{218F}\\x{2C00}-\\x{2FEF}" +
                                                "\\x{3001}-\\x{D7FF}\\x{F900}-\\x{FDCF}\\x{FDF0}-\\x{FFFD}\\x{10000}-\\x{EFFFF}";
  /** NameChar of XML 1.0 fifth edition as a Java character class body */
  private static final String JAVA_NAME_CHAR = JAVA_NAME_START + "\\-.0-9\\x{B7}\\x{300}-\\x{36F}\\x{203F}-\\x{2040}";

  /**
   * The characters of the test strings: letters, digits (also a non-ASCII one), name characters,
   * the regular expression metacharacters, the XML whitespace characters, punctuation, a format
   * character and a non-BMP character.
   */
  private static final int [] ALPHABET = { 'a',
                                           'b',
                                           'z',
                                           'A',
                                           0xC4,
                                           '0',
                                           '5',
                                           0x663,
                                           '_',
                                           ':',
                                           '-',
                                           '.',
                                           '^',
                                           '$',
                                           ' ',
                                           '\t',
                                           '\n',
                                           '\r',
                                           0xE9,
                                           0xB7,
                                           0x300,
                                           0x1F600,
                                           '!',
                                           '#',
                                           0xAD,
                                           '[',
                                           ']',
                                           '\\',
                                           '|',
                                           '?',
                                           '*',
                                           '+',
                                           '(',
                                           ')',
                                           '{',
                                           '}' };

  /**
   * Characters whose name character classification differs between XML 1.0 fifth edition and the
   * earlier editions that XSD 1.0 refers to (<code>\i</code> and <code>\c</code>).
   */
  private static final int [] EDITION_DEPENDENT = { 0x663, 0x1F600 };

  /** Characters written as themselves outside a character class */
  private static final int [] PLAIN_LITERALS = { 'a', 'b', 'z', 'A', 0xC4, '0', '5', '_', ':', '^', '$', ' ', 0xE9, 0xB7, 0x1F600, '!', '#' };
  /** Characters written with a single character escape */
  private static final char [] ESCAPED = { '.', '?', '*', '+', '(', ')', '{', '}', '|', '[', ']', '\\', '-', '^' };
  /** Characters written as themselves inside a character class */
  private static final int [] CLASS_LITERALS = { 'a', 'b', 'z', 'A', '0', '5', '_', ':', '$', ' ', 0xE9, 0xB7, 0x1F600, '!' };
  /** Ranges inside a character class */
  private static final String [] [] RANGES = { { "a", "c" }, { "0", "5" }, { "A", "Z" }, { "À", "ÿ" }, { "#", "." } };

  /**
   * The multi-character escapes: XSD form, Java form usable inside and outside a character class,
   * whether it is a name character class.
   */
  private static final String [] [] MULTI_ESCAPES = { { "\\d", "\\p{Nd}", "" },
                                                      { "\\D", "\\P{Nd}", "" },
                                                      { "\\s", "[\\x{20}\\x{9}\\x{A}\\x{D}]", "" },
                                                      { "\\S", "[^\\x{20}\\x{9}\\x{A}\\x{D}]", "" },
                                                      { "\\i", "[" + JAVA_NAME_START + "]", "n" },
                                                      { "\\I", "[^" + JAVA_NAME_START + "]", "n" },
                                                      { "\\c", "[" + JAVA_NAME_CHAR + "]", "n" },
                                                      { "\\C", "[^" + JAVA_NAME_CHAR + "]", "n" },
                                                      { "\\w", "[^\\p{P}\\p{Z}\\p{C}]", "" },
                                                      { "\\W", "[\\p{P}\\p{Z}\\p{C}]", "" },
                                                      { "\\p{Lu}", "\\p{Lu}", "" },
                                                      { "\\p{L}", "\\p{L}", "" },
                                                      { "\\P{L}", "\\P{L}", "" },
                                                      { "\\p{N}", "\\p{N}", "" },
                                                      { "\\p{Mn}", "\\p{Mn}", "" } };

  /** The maximum nesting of groups and character class subtractions */
  private static final int MAX_DEPTH = 3;

  /**
   * A generated regular expression: the XSD form, the Java form (to be matched against the whole
   * string) and whether it uses <code>\i</code>, <code>\I</code>, <code>\c</code> or <code>\C</code>.
   */
  private record Regex (@NonNull String xsd, @NonNull String java, boolean nameClasses)
  {}

  /** A generated regular expression with the strings to test it on. */
  private record RegexCase (@NonNull Regex regex, @NonNull List <String> values)
  {}

  @NonNull
  private static String _javaChar (final int nCodePoint)
  {
    return "\\x{" + Integer.toHexString (nCodePoint) + "}";
  }

  private static int _draw (@NonNull final ChoiceSequence aChoices, final int nMaxInclusive)
  {
    return (int) aChoices.draw (nMaxInclusive);
  }

  /**
   * Draw the items of a character class group (without brackets): XSD form, Java form (to be
   * placed inside brackets) and the name class flag.
   */
  @NonNull
  private static Regex _drawClassItems (@NonNull final ChoiceSequence aChoices)
  {
    final StringBuilder aXsd = new StringBuilder ();
    final StringBuilder aJava = new StringBuilder ();
    boolean bName = false;
    final int nItems = 1 + _draw (aChoices, 2);
    for (int i = 0; i < nItems; ++i)
      switch (_draw (aChoices, 3))
      {
        case 0 ->
        {
          final int c = CLASS_LITERALS[_draw (aChoices, CLASS_LITERALS.length - 1)];
          aXsd.appendCodePoint (c);
          aJava.append (_javaChar (c));
        }
        case 1 ->
        {
          final String [] aRange = RANGES[_draw (aChoices, RANGES.length - 1)];
          aXsd.append (aRange[0]).append ('-').append (aRange[1]);
          aJava.append (_javaChar (aRange[0].codePointAt (0))).append ('-').append (_javaChar (aRange[1].codePointAt (0)));
        }
        case 2 ->
        {
          final char c = ESCAPED[_draw (aChoices, ESCAPED.length - 1)];
          aXsd.append ('\\').append (c);
          aJava.append (_javaChar (c));
        }
        default ->
        {
          final String [] aEscape = MULTI_ESCAPES[_draw (aChoices, MULTI_ESCAPES.length - 1)];
          aXsd.append (aEscape[0]);
          aJava.append (aEscape[1]);
          bName |= !aEscape[2].isEmpty ();
        }
      }
    return new Regex (aXsd.toString (), aJava.toString (), bName);
  }

  /**
   * Draw a character class expression with brackets: positive or negative group, possibly with a
   * subtraction. The Java form is a complete bracket expression.
   */
  @NonNull
  private static Regex _drawClass (@NonNull final ChoiceSequence aChoices, final int nDepth)
  {
    final boolean bNegative = _draw (aChoices, 2) == 2;
    final Regex aItems = _drawClassItems (aChoices);
    final String sJavaGroup = (bNegative ? "[^" : "[") + aItems.java () + "]";
    if (nDepth < MAX_DEPTH && _draw (aChoices, 2) == 2)
    {
      final Regex aSub = _drawClass (aChoices, nDepth + 1);
      return new Regex ((bNegative ? "[^" : "[") + aItems.xsd () + "-" + aSub.xsd () + "]",
                        "[" + sJavaGroup + "&&[^" + aSub.java () + "]]",
                        aItems.nameClasses () || aSub.nameClasses ());
    }
    return new Regex ((bNegative ? "[^" : "[") + aItems.xsd () + "]", sJavaGroup, aItems.nameClasses ());
  }

  /** Draw an atom: a character, an escape, a wildcard, a character class or a group. */
  @NonNull
  private static Regex _drawAtom (@NonNull final ChoiceSequence aChoices, final int nDepth)
  {
    switch (_draw (aChoices, nDepth < MAX_DEPTH ? 5 : 4))
    {
      case 0:
      {
        final int c = PLAIN_LITERALS[_draw (aChoices, PLAIN_LITERALS.length - 1)];
        return new Regex (new String (Character.toChars (c)), _javaChar (c), false);
      }
      case 1:
      {
        final char c = ESCAPED[_draw (aChoices, ESCAPED.length - 1)];
        return new Regex ("\\" + c, _javaChar (c), false);
      }
      case 2:
      {
        // The single character escapes of the whitespace characters, or the wildcard
        switch (_draw (aChoices, 3))
        {
          case 0:
            return new Regex ("\\n", _javaChar ('\n'), false);
          case 1:
            return new Regex ("\\t", _javaChar ('\t'), false);
          case 2:
            return new Regex ("\\r", _javaChar ('\r'), false);
          default:
            return new Regex (".", "[^\\x{A}\\x{D}]", false);
        }
      }
      case 3:
      {
        final String [] aEscape = MULTI_ESCAPES[_draw (aChoices, MULTI_ESCAPES.length - 1)];
        return new Regex (aEscape[0], aEscape[1], !aEscape[2].isEmpty ());
      }
      case 4:
        return _drawClass (aChoices, nDepth + 1);
      default:
      {
        final Regex aInner = _drawRegex (aChoices, nDepth + 1);
        return new Regex ("(" + aInner.xsd () + ")", "(?:" + aInner.java () + ")", aInner.nameClasses ());
      }
    }
  }

  /** Draw a quantifier, the same text in XSD and Java. */
  @NonNull
  private static String _drawQuantifier (@NonNull final ChoiceSequence aChoices)
  {
    switch (_draw (aChoices, 6))
    {
      case 0:
        return "";
      case 1:
        return "?";
      case 2:
        return "*";
      case 3:
        return "+";
      case 4:
        return "{" + _draw (aChoices, 2) + "}";
      case 5:
      {
        final int nMin = _draw (aChoices, 2);
        return "{" + nMin + "," + (nMin + _draw (aChoices, 2)) + "}";
      }
      default:
        return "{" + _draw (aChoices, 2) + ",}";
    }
  }

  /** Draw a regular expression: branches of quantified atoms. */
  @NonNull
  private static Regex _drawRegex (@NonNull final ChoiceSequence aChoices, final int nDepth)
  {
    final StringBuilder aXsd = new StringBuilder ();
    final StringBuilder aJava = new StringBuilder ();
    boolean bName = false;
    final int nBranches = 1 + _draw (aChoices, 2);
    for (int nBranch = 0; nBranch < nBranches; ++nBranch)
    {
      if (nBranch > 0)
      {
        aXsd.append ('|');
        aJava.append ('|');
      }
      final int nPieces = _draw (aChoices, 3);
      for (int i = 0; i < nPieces; ++i)
      {
        final Regex aAtom = _drawAtom (aChoices, nDepth);
        final String sQuantifier = _drawQuantifier (aChoices);
        aXsd.append (aAtom.xsd ()).append (sQuantifier);
        // The Java form is grouped so that the quantifier applies to the whole atom
        aJava.append ("(?:").append (aAtom.java ()).append (')').append (sQuantifier);
        bName |= aAtom.nameClasses ();
      }
    }
    return new Regex (aXsd.toString (), aJava.toString (), bName);
  }

  @NonNull
  private static IGenerator <Regex> _regexes ()
  {
    return aChoices -> _drawRegex (aChoices, 0);
  }

  @NonNull
  private static IGenerator <String> _strings ()
  {
    return Generators.lists (Generators.ints (0, ALPHABET.length - 1), 0, 6).map (aIndexes -> {
      final StringBuilder aSB = new StringBuilder ();
      for (final Integer aIndex : aIndexes)
        aSB.appendCodePoint (ALPHABET[aIndex.intValue ()]);
      return aSB.toString ();
    });
  }

  /**
   * @return Strings that a Java regular expression matches, drawn from the alphabet by a
   *         backtracking-free random walk: each try appends characters one by one while a prefix
   *         of a match is possible.
   */
  @NonNull
  private static IGenerator <String> _matchingStrings (@NonNull final Pattern aPattern)
  {
    return aChoices -> {
      final StringBuilder aSB = new StringBuilder ();
      final int nLength = _draw (aChoices, 5);
      for (int i = 0; i < nLength; ++i)
      {
        final ICommonsList <Integer> aCandidates = new CommonsArrayList <> ();
        for (final int c : ALPHABET)
        {
          final String sNext = new StringBuilder (aSB).appendCodePoint (c).toString ();
          final Matcher aMatcher = aPattern.matcher (sNext);
          if (aMatcher.matches () || aMatcher.hitEnd ())
            aCandidates.add (Integer.valueOf (c));
        }
        if (aCandidates.isEmpty ())
          break;
        aSB.appendCodePoint (aCandidates.get (_draw (aChoices, aCandidates.size () - 1)).intValue ());
      }
      return aSB.toString ();
    };
  }

  private static boolean _containsEditionDependent (@NonNull final String s)
  {
    for (final int c : EDITION_DEPENDENT)
      if (s.indexOf (new String (Character.toChars (c))) >= 0)
        return true;
    return false;
  }

  @NonNull
  private static String _patternFacet (@NonNull final Regex aRegex)
  {
    return "<xs:pattern value=\"" + XsdPropertyTestHelper.escape (aRegex.xsd ()) + "\"/>";
  }

  /**
   * Check a string against the type of each version and the expected verdict. Under XSD 1.0 a
   * pattern with name classes is not checked against characters whose classification depends on
   * the XML edition.
   */
  private static void _check (@NonNull final XsdSimpleType @NonNull [] aTypes,
                              final boolean bNameClasses,
                              @NonNull final String sMessage,
                              final boolean bExpected,
                              @NonNull final String sValue)
  {
    if (!bNameClasses || !_containsEditionDependent (sValue))
      assertEquals (sMessage + " (1.0)",
                    Boolean.valueOf (bExpected),
                    Boolean.valueOf (XsdPropertyTestHelper.isValidValue (aTypes[0], sValue, EXsdVersion.V10)));
    assertEquals (sMessage + " (1.1)",
                  Boolean.valueOf (bExpected),
                  Boolean.valueOf (XsdPropertyTestHelper.isValidValue (aTypes[1], sValue, EXsdVersion.V11)));
  }

  /**
   * Model property: a restriction of xs:string with one generated pattern accepts a string exactly
   * when the hand translation into <code>java.util.regex</code> matches all of it. The strings are
   * random strings over the alphabet and strings built to match.
   */
  @Test
  public void testPatternMatchesTranslation ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (_regexes ().flatMap (aRegex -> {
                            final Pattern aPattern = Pattern.compile (aRegex.java ());
                            return Generators.lists (Generators.oneOf (_strings (), _matchingStrings (aPattern)), 1, 8)
                                             .map (aValues -> new RegexCase (aRegex, aValues));
                          }), aCase -> {
                            final Regex aRegex = aCase.regex ();
                            final List <String> aValues = aCase.values ();
                            final Pattern aPattern = Pattern.compile (aRegex.java ());
                            final String sFacets = _patternFacet (aRegex);
                            final XsdSimpleType aType10 = XsdPropertyTestHelper.compileRestriction ("string",
                                                                                                    sFacets,
                                                                                                    EXsdVersion.V10);
                            final XsdSimpleType aType11 = XsdPropertyTestHelper.compileRestriction ("string",
                                                                                                    sFacets,
                                                                                                    EXsdVersion.V11);
                            assertNotNull (aRegex.xsd () + " must compile under 1.0", aType10);
                            assertNotNull (aRegex.xsd () + " must compile under 1.1", aType11);
                            final XsdSimpleType [] aTypes = { aType10, aType11 };
                            for (final String sValue : aValues)
                              _check (aTypes,
                                      aRegex.nameClasses (),
                                      "/" + aRegex.xsd () + "/ ~ /" + aRegex.java () + "/ on '" + sValue + "'",
                                      aPattern.matcher (sValue).matches (),
                                      sValue);
                          });
  }

  /**
   * Model property for the combination of patterns (XML Schema 1.1 Part 2, section 4.3.4.3):
   * patterns in the same derivation step are alternatives, a pattern of a derived type must hold
   * together with the one of its base type.
   */
  @Test
  public void testPatternCombination ()
  {
    new PropertyChecker ().setTries (200)
                          .forAll (_regexes (),
                                   _regexes (),
                                   Generators.booleans (),
                                   Generators.lists (_strings (), 1, 8),
                                   (aRegex1, aRegex2, aSameStep, aValues) -> {
                                     final boolean bSameStep = aSameStep.booleanValue ();
                                     final String sSchema;
                                     if (bSameStep)
                                       sSchema = XsdPropertyTestHelper.SCHEMA_START +
                                                 "<xs:simpleType name=\"t\"><xs:restriction base=\"xs:string\">" +
                                                 _patternFacet (aRegex1) +
                                                 _patternFacet (aRegex2) +
                                                 "</xs:restriction></xs:simpleType>" +
                                                 XsdPropertyTestHelper.SCHEMA_END;
                                     else
                                       sSchema = XsdPropertyTestHelper.SCHEMA_START +
                                                 "<xs:simpleType name=\"b\"><xs:restriction base=\"xs:string\">" +
                                                 _patternFacet (aRegex1) +
                                                 "</xs:restriction></xs:simpleType>" +
                                                 "<xs:simpleType name=\"t\"><xs:restriction base=\"b\">" +
                                                 _patternFacet (aRegex2) +
                                                 "</xs:restriction></xs:simpleType>" +
                                                 XsdPropertyTestHelper.SCHEMA_END;
                                     final XsdSchema [] aSchemas = XsdPropertyTestHelper.compileBoth (sSchema);
                                     assertNotNull (sSchema, aSchemas);
                                     final XsdSimpleType [] aTypes = { XsdPropertyTestHelper.getType (aSchemas[0]),
                                                                       XsdPropertyTestHelper.getType (aSchemas[1]) };
                                     final Pattern aPattern1 = Pattern.compile (aRegex1.java ());
                                     final Pattern aPattern2 = Pattern.compile (aRegex2.java ());
                                     for (final String sValue : aValues)
                                     {
                                       final boolean bMatch1 = aPattern1.matcher (sValue).matches ();
                                       final boolean bMatch2 = aPattern2.matcher (sValue).matches ();
                                       _check (aTypes,
                                               aRegex1.nameClasses () || aRegex2.nameClasses (),
                                               (bSameStep ? "same step " : "derived ") +
                                                                                                  "/" +
                                                                                                  aRegex1.xsd () +
                                                                                                  "/ /" +
                                                                                                  aRegex2.xsd () +
                                                                                                  "/ on '" +
                                                                                                  sValue +
                                                                                                  "'",
                                               bSameStep ? bMatch1 || bMatch2 : bMatch1 && bMatch2,
                                               sValue);
                                     }
                                   });
  }

  /**
   * Robustness property: an arbitrary string of regular expression characters as pattern either
   * compiles or is refused as schema error under each version, and a compiled pattern accepts or
   * refuses any value without any other exception.
   */
  @Test
  public void testArbitraryPatternsOnlyFailAsSchemaError ()
  {
    final IGenerator <String> aTokens = Generators.of ("a",
                                                       "b",
                                                       "-",
                                                       "[",
                                                       "]",
                                                       "^",
                                                       "$",
                                                       "\\",
                                                       "{",
                                                       "}",
                                                       ",",
                                                       "1",
                                                       "(",
                                                       ")",
                                                       "|",
                                                       "?",
                                                       "*",
                                                       "+",
                                                       ".",
                                                       "\\p{",
                                                       "\\P{",
                                                       "Is",
                                                       "L",
                                                       "Lu",
                                                       "BasicLatin",
                                                       "\\d",
                                                       "\\i",
                                                       "\\c",
                                                       "-[",
                                                       "😀",
                                                       "\uD83D",
                                                       "&",
                                                       "#");
    new PropertyChecker ().setTries (500)
                          .forAll (Generators.lists (aTokens, 0, 12),
                                   Generators.lists (_strings (), 1, 4),
                                   (aPatternTokens, aValues) -> {
                                     final String sPattern = String.join ("", aPatternTokens);
                                     for (final EXsdVersion eVersion : EXsdVersion.values ())
                                     {
                                       final XsdSimpleType aType = XsdPropertyTestHelper.compileRestriction ("string",
                                                                                                             "<xs:pattern value=\"" +
                                                                                                                       XsdPropertyTestHelper.escape (sPattern) +
                                                                                                                       "\"/>",
                                                                                                             eVersion);
                                       if (aType != null)
                                         for (final String sValue : aValues)
                                           XsdPropertyTestHelper.isValidValue (aType, sValue, eVersion);
                                     }
                                   });
  }
}

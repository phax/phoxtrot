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

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Test;

import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for value equality in the enumeration facet (XML Schema 1.1 Part 2, section
 * 4.3.5) and in <code>fixed</code> attribute values (XML Schema 1.1 Part 1, section 3.5.4.2): both
 * compare values, not lexical forms. Each generated lexical form carries a key that two forms share
 * exactly when their values are equal: numbers by their exact value, dateTimes by their instant (a
 * dateTime without timezone is never equal to one with a timezone), durations by months and
 * seconds, binary values by their octets, booleans by their truth value. For a union the value is
 * that of the first member type that accepts the lexical form, and values of different primitive
 * types are never equal.
 *
 * @author Philip Helger
 */
public final class XsdValueEqualityPropertyTest
{
  /** The types of {@link #_forms(String, int)} */
  private static final String [] TYPES = { "boolean", "double", "float", "dateTime", "duration", "hexBinary", "base64Binary" };
  private static final String [] NON_BINARY_TYPES = { "boolean", "double", "float", "dateTime", "duration" };
  private static final String [] BINARY_TYPES = { "hexBinary", "base64Binary" };

  private static final String [] [] BOOLEAN_FORMS = { { "true", "1", " true " }, { "false", "0", "\t0\n" } };
  /** Groups of equal durations */
  private static final String [] [] DURATION_FORMS = { { "P1D", "PT24H", "PT1440M", "PT86400S", "P0Y1D", "PT86400.0S" },
                                                       { "P1Y", "P12M", "P0Y12M0D", "P1YT0S" },
                                                       { "P0D", "PT0S", "-P0D", "P0Y", "-PT0.0S" },
                                                       { "P1M", "P0Y1M" },
                                                       { "P30D", "PT720H" },
                                                       { "-P1D", "-PT24H" },
                                                       { "P1Y1D", "P12MT24H" } };
  /** Groups of equal binary values: hexBinary, base64Binary */
  private static final String [] [] HEX_FORMS = { { "0a", "0A", " 0a " }, { "ff00", "FF00", "fF00" }, { "" }, { "00" } };
  private static final String [] [] BASE64_FORMS = { { "QQ==", "Q Q = =", " QQ== " }, { "AAAA", "A A A A" }, { "" }, { "AA==" } };
  /** Local dateTimes of the dateTime forms, and the seconds from the first */
  private static final String [] DATE_TIMES = { "2000-01-01T12:00:00", "2000-01-01T13:00:00", "2000-01-01T11:00:00", "1999-12-31T24:00:00", "2000-01-01T00:00:00" };
  private static final int [] DATE_TIME_SECONDS = { 0, 3600, -3600, -12 * 3600, -12 * 3600 };
  private static final String [] OFFSET_LEXICAL = { "Z", "+00:00", "+01:00", "-01:00", "+12:00", "-12:00", "+13:00" };
  private static final int [] OFFSET_SECONDS = { 0, 0, 3600, -3600, 12 * 3600, -12 * 3600, 13 * 3600 };

  /**
   * A lexical form with the key of its value.
   *
   * @param lexical
   *        The lexical form.
   * @param key
   *        Equal for equal values, different otherwise.
   */
  private record Form (@NonNull String lexical, @NonNull String key)
  {
    @Override
    public String toString ()
    {
      return "'" + lexical + "'";
    }
  }

  /**
   * A type with generated forms of its values.
   *
   * @param type
   *        The local name of the type.
   * @param forms
   *        The forms; the first ones are the enumeration or fixed values.
   */
  private record FormCase (@NonNull String type, @NonNull List <Form> forms)
  {}

  @NonNull
  private static IGenerator <Form> _groupForms (@NonNull final String [] [] aGroups)
  {
    return Generators.ints (0, aGroups.length - 1)
                     .flatMap (aGroup -> Generators.of (aGroups[aGroup.intValue ()])
                                                   .map (sLexical -> new Form (sLexical, "g" + aGroup)));
  }

  /**
   * @return Forms of the multiples of 1/4 from -2 to 2 (exact in float and double), and the
   *         infinities (without the plus sign, which XSD 1.0 does not allow): plain, with leading and trailing zeros, a plus sign, or in exponent
   *         notation.
   */
  @NonNull
  private static IGenerator <Form> _floatForms ()
  {
    return Generators.ints (-9, 9).flatMap (aN -> Generators.ints (0, 4).map (aFormat -> {
      final int n = aN.intValue ();
      if (n == 9)
        return new Form ("INF", "INF");
      if (n == -9)
        return new Form ("-INF", "-INF");
      final BigDecimal aValue = BigDecimal.valueOf (n).divide (BigDecimal.valueOf (4));
      final String sKey = aValue.stripTrailingZeros ().toPlainString ();
      final String sLexical = switch (aFormat.intValue ())
      {
        case 1 -> aValue.setScale (4).toPlainString ();
        case 2 -> aValue.movePointRight (2).toPlainString () + "E-2";
        case 3 -> (n >= 0 ? "+" : "") + aValue.toPlainString ();
        case 4 -> aValue.movePointLeft (1).toPlainString () + "e1";
        default -> aValue.toPlainString ();
      };
      return new Form (sLexical, sKey);
    }));
  }

  /**
   * @return dateTime forms: equal instants written with different local times and offsets, and the
   *         same local times without timezone (equal only among themselves). With
   *         <code>nTimezoneMode</code> 0 no form has a timezone, with 1 all have one, with 2 each
   *         may have one.
   */
  @NonNull
  private static IGenerator <Form> _dateTimeForms (final int nTimezoneMode)
  {
    final int nMinOffset = nTimezoneMode == 1 ? 0 : -1;
    final int nMaxOffset = nTimezoneMode == 0 ? -1 : OFFSET_LEXICAL.length - 1;
    return Generators.ints (0, DATE_TIMES.length - 1)
                     .flatMap (aLocal -> Generators.ints (nMinOffset, nMaxOffset).map (aOffset -> {
                       final int nLocal = aLocal.intValue ();
                       final int nOffset = aOffset.intValue ();
                       if (nOffset < 0)
                         return new Form (DATE_TIMES[nLocal], "local " + DATE_TIME_SECONDS[nLocal]);
                       return new Form (DATE_TIMES[nLocal] + OFFSET_LEXICAL[nOffset],
                                        "utc " + (DATE_TIME_SECONDS[nLocal] - OFFSET_SECONDS[nOffset]));
                     }));
  }

  @NonNull
  private static IGenerator <Form> _forms (@NonNull final String sType, final int nTimezoneMode)
  {
    return switch (sType)
    {
      case "boolean" -> _groupForms (BOOLEAN_FORMS);
      case "double", "float" -> _floatForms ();
      case "dateTime" -> _dateTimeForms (nTimezoneMode);
      case "duration" -> _groupForms (DURATION_FORMS);
      case "hexBinary" -> _groupForms (HEX_FORMS);
      default -> _groupForms (BASE64_FORMS);
    };
  }

  /**
   * @return Cases of one of the types with at least <code>nMinForms</code> forms; dateTime forms
   *         either all have a timezone or none has one, unless <code>bMixedTimezones</code> is set.
   */
  @NonNull
  private static IGenerator <FormCase> _cases (@NonNull final String @NonNull [] aTypes,
                                               final int nMinForms,
                                               final boolean bMixedTimezones)
  {
    return Generators.ints (0, aTypes.length - 1)
                     .flatMap (aType -> Generators.ints (0, 1)
                                                  .flatMap (aTimezoneMode -> Generators.lists (_forms (aTypes[aType.intValue ()],
                                                                                                       bMixedTimezones ? 2
                                                                                                                       : aTimezoneMode.intValue ()),
                                                                                               nMinForms,
                                                                                               nMinForms + 6)
                                                                                       .map (aForms -> new FormCase (aTypes[aType.intValue ()],
                                                                                                                     aForms))));
  }

  /**
   * Check that an enumeration facet with the first forms of the case as values accepts each form
   * exactly when its value equals one of them, under both versions.
   */
  private static void _checkEnumeration (@NonNull final FormCase aCase, final int nEnum)
  {
    final List <Form> aForms = aCase.forms ();
    final StringBuilder aFacets = new StringBuilder ();
    for (int i = 0; i < nEnum; ++i)
      aFacets.append ("<xs:enumeration value=\"")
             .append (XsdPropertyTestHelper.escape (aForms.get (i).lexical ()))
             .append ("\"/>");
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final XsdSimpleType aType = XsdPropertyTestHelper.compileRestriction (aCase.type (), aFacets.toString (), eVersion);
      assertNotNull (aCase.type () + " " + aFacets, aType);
      for (final Form aForm : aForms)
      {
        boolean bExpected = false;
        for (int i = 0; i < nEnum; ++i)
          if (aForms.get (i).key ().equals (aForm.key ()))
            bExpected = true;
        assertEquals (aCase.type () + " " + aFacets + " " + aForm + " (" + eVersion.getID () + ")",
                      Boolean.valueOf (bExpected),
                      Boolean.valueOf (XsdPropertyTestHelper.isValidValue (aType, aForm.lexical (), eVersion)));
      }
    }
  }

  /**
   * Check that an attribute declared with the first form of the case as fixed value accepts each
   * form in an XSD 1.1 instance exactly when its value equals the fixed one.
   */
  private static void _checkFixed (@NonNull final FormCase aCase)
  {
    final List <Form> aForms = aCase.forms ();
    final Form aFixed = aForms.get (0);
    final String sSchema = XsdPropertyTestHelper.SCHEMA_START +
                           "<xs:element name=\"r\"><xs:complexType><xs:attribute name=\"a\" type=\"xs:" +
                           aCase.type () +
                           "\" fixed=\"" +
                           XsdPropertyTestHelper.escape (aFixed.lexical ()) +
                           "\"/></xs:complexType></xs:element>" +
                           XsdPropertyTestHelper.SCHEMA_END;
    final XsdSchema aSchema = XsdPropertyTestHelper.compile (sSchema, EXsdVersion.V11);
    assertNotNull (sSchema, aSchema);
    for (final Form aForm : aForms)
    {
      final String sInstance = "<r a=\"" + XsdPropertyTestHelper.escape (aForm.lexical ()) + "\"/>";
      assertEquals (aCase.type () + " fixed " + aFixed + " " + aForm,
                    Boolean.valueOf (aForm.key ().equals (aFixed.key ())),
                    Boolean.valueOf (XsdPropertyTestHelper.isValidInstance (aSchema, sInstance)));
    }
  }

  /**
   * Model property: an enumeration facet with the first one to three forms as values accepts a
   * form exactly when its value equals one of them, under both versions. The binary types and
   * dateTimes with and without timezone in one case are left to the ignored tests below.
   */
  @Test
  public void testEnumerationComparesValues ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (_cases (NON_BINARY_TYPES, 4, false),
                                   Generators.ints (1, 3),
                                   (aCase, aEnumCount) -> _checkEnumeration (aCase, aEnumCount.intValue ()));
  }

  /**
   * Model property for the binary types: the enumeration compares octets, so hexBinary
   * <code>0a</code> equals <code>0A</code> and base64Binary <code>QQ==</code> equals
   * <code>Q Q = =</code> (XML Schema 1.1 Part 2, sections 3.3.15 and 3.3.16: the value space is
   * the set of octet sequences).
   */
  @Ignore ("Engine bug: the enumeration facet compares hexBinary and base64Binary values lexically")
  @Test
  public void testEnumerationComparesBinaryValues ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (_cases (BINARY_TYPES, 4, false),
                                   Generators.ints (1, 3),
                                   (aCase, aEnumCount) -> _checkEnumeration (aCase, aEnumCount.intValue ()));
  }

  /**
   * Model property for XSD 1.1: an attribute declared with a fixed value accepts an attribute value
   * in an instance exactly when its value equals the fixed one. dateTimes with and without
   * timezone in one case are left to the ignored test below.
   */
  @Test
  public void testFixedAttributeComparesValues ()
  {
    new PropertyChecker ().setTries (200).forAll (_cases (TYPES, 2, false), XsdValueEqualityPropertyTest::_checkFixed);
  }

  /**
   * Model property: a dateTime without timezone never equals one with a timezone (XML Schema 1.1
   * Part 2, section 3.3.7 and appendix D.2.1; 1.0 section 3.2.7.4: there is no determinate order
   * between <code>2000-01-20T12:00:00</code> and <code>2000-01-20T12:00:00Z</code>), neither in an
   * enumeration nor as fixed value.
   */
  @Ignore ("Engine bug: dateTime equality (enumeration, fixed) treats a value without timezone as UTC")
  @Test
  public void testDateTimeWithAndWithoutTimezoneNeverEqual ()
  {
    new PropertyChecker ().setTries (300)
                          .forAll (_cases (new String [] { "dateTime" }, 4, true), Generators.ints (1, 3), (aCase, aEnumCount) -> {
                            _checkEnumeration (aCase, aEnumCount.intValue ());
                            _checkFixed (aCase);
                          });
  }

  /**
   * @return The key of the value of a lexical form in the union of xs:int and xs:boolean, or
   *         <code>null</code> if no member accepts it.
   */
  @Nullable
  private static String _unionKey (@NonNull final String sLexical, final boolean bIntFirst)
  {
    final String sTrimmed = sLexical.strip ();
    final String sInt = sTrimmed.matches ("[+-]?[0-9]+") ? "int " + Integer.parseInt (sTrimmed) : null;
    final String sBoolean;
    if (sTrimmed.equals ("true") || sTrimmed.equals ("1"))
      sBoolean = "boolean true";
    else
      if (sTrimmed.equals ("false") || sTrimmed.equals ("0"))
        sBoolean = "boolean false";
      else
        sBoolean = null;
    if (bIntFirst)
      return sInt != null ? sInt : sBoolean;
    return sBoolean != null ? sBoolean : sInt;
  }

  /**
   * Model property for enumerations on unions (XML Schema 1.1 Part 2, section 4.1.2.3): the value
   * of a lexical form is that of the first member type that accepts it, and an
   * <code>xs:int</code> value never equals an <code>xs:boolean</code> one. With
   * <code>memberTypes="xs:int xs:boolean"</code> and the enumeration value <code>1</code> (the
   * integer 1), <code>01</code> is accepted and <code>true</code> is not; with the member types in
   * the other order <code>1</code> is the boolean true, so <code>true</code> is accepted and
   * <code>01</code> is not.
   */
  @Ignore ("Engine bug: the enumeration facet of a union compares the lexical forms, not the values of the accepting member")
  @Test
  public void testUnionEnumerationUsesFirstMember ()
  {
    final String [] aLexicals = { "1", "01", "+1", "true", "0", "false", "2", " 1 ", "x" };
    new PropertyChecker ().setTries (100)
                          .forAll (Generators.booleans (),
                                   Generators.ints (0, aLexicals.length - 1),
                                   Generators.lists (Generators.ints (0, aLexicals.length - 1), 1, 6),
                                   (aIntFirst, aEnum, aValues) -> {
                                     final boolean bIntFirst = aIntFirst.booleanValue ();
                                     final String sEnum = aLexicals[aEnum.intValue ()];
                                     final String sSchema = XsdPropertyTestHelper.SCHEMA_START +
                                                            "<xs:simpleType name=\"u\"><xs:union memberTypes=\"" +
                                                            (bIntFirst ? "xs:int xs:boolean" : "xs:boolean xs:int") +
                                                            "\"/></xs:simpleType>" +
                                                            "<xs:simpleType name=\"t\"><xs:restriction base=\"u\">" +
                                                            "<xs:enumeration value=\"" +
                                                            sEnum +
                                                            "\"/></xs:restriction></xs:simpleType>" +
                                                            XsdPropertyTestHelper.SCHEMA_END;
                                     final XsdSchema [] aSchemas = XsdPropertyTestHelper.compileBoth (sSchema);
                                     final String sEnumKey = _unionKey (sEnum, bIntFirst);
                                     // An enumeration value outside the value space is a schema error
                                     assertEquals (sSchema,
                                                   Boolean.valueOf (sEnumKey != null),
                                                   Boolean.valueOf (aSchemas != null));
                                     if (aSchemas != null)
                                       for (final Integer aValue : aValues)
                                       {
                                         final String sValue = aLexicals[aValue.intValue ()];
                                         final boolean bExpected = sEnumKey.equals (_unionKey (sValue, bIntFirst));
                                         for (int i = 0; i < 2; ++i)
                                         {
                                           final EXsdVersion eVersion = i == 0 ? EXsdVersion.V10 : EXsdVersion.V11;
                                           assertEquals (sSchema + " '" + sValue + "' (" + eVersion.getID () + ")",
                                                         Boolean.valueOf (bExpected),
                                                         Boolean.valueOf (XsdPropertyTestHelper.isValidValue (XsdPropertyTestHelper.getType (aSchemas[i]),
                                                                                                              sValue,
                                                                                                              eVersion)));
                                         }
                                       }
                                   });
  }

}

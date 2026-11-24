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
package com.helger.phoxtrot.xpath.fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.unittest.property.ChoiceSequence;
import com.helger.unittest.property.Generators;
import com.helger.unittest.property.IGenerator;
import com.helger.unittest.property.PropertyChecker;

/**
 * Property based tests for the JSON functions of {@link FnJson} (F&amp;O 3.1
 * sections 17.5 and 17.6) and the json output method of the serializer
 * (Serialization 3.1 section 10). Random JSON texts are built together with an
 * XPath expression for the value <code>fn:parse-json</code> must return
 * (strings with escapes, invalid XML characters replaced by U+FFFD, numbers
 * as <code>xs:double</code>); the JSON round trips through
 * <code>fn:serialize</code> and through <code>fn:json-to-xml</code> /
 * <code>fn:xml-to-json</code> must keep the value; and arbitrary text fails
 * only with the documented error codes.
 *
 * @author Philip Helger
 */
public final class FnJsonPropertyTest
{
  private static final XdmQName VAR_V = new XdmQName ("v");
  private static final XdmQName VAR_W = new XdmQName ("w");
  private static final int MAX_DEPTH = 4;

  /**
   * A piece of a JSON string: how it is written in the JSON text and the
   * characters <code>fn:parse-json</code> must make of it.
   */
  @Immutable
  private record StringPiece (@NonNull String json, @NonNull String value)
  {}

  private static final StringPiece [] STRING_PIECES = { new StringPiece ("a", "a"),
                                                        new StringPiece (" ", " "),
                                                        new StringPiece ("\\\"", "\""),
                                                        new StringPiece ("\\\\", "\\"),
                                                        new StringPiece ("\\/", "/"),
                                                        new StringPiece ("/", "/"),
                                                        new StringPiece ("\\b", "\ufffd"),
                                                        new StringPiece ("\\f", "\ufffd"),
                                                        new StringPiece ("\\n", "\n"),
                                                        new StringPiece ("\\r", "\r"),
                                                        new StringPiece ("\\t", "\t"),
                                                        new StringPiece ("\\u0000", "\ufffd"),
                                                        new StringPiece ("\\u001F", "\ufffd"),
                                                        new StringPiece ("\\u0041", "A"),
                                                        new StringPiece ("\\u00e9", "\u00e9"),
                                                        new StringPiece ("\u00e9", "\u00e9"),
                                                        new StringPiece ("\u007f", "\u007f"),
                                                        new StringPiece ("\\uFFFF", "\ufffd"),
                                                        new StringPiece ("\\uFFFE", "\ufffd"),
                                                        new StringPiece ("\\uD83D\\uDE00", "\ud83d\ude00"),
                                                        new StringPiece ("\\ud83d\\ude00", "\ud83d\ude00"),
                                                        new StringPiece ("\ud83d\ude00", "\ud83d\ude00"),
                                                        new StringPiece ("\\uD800", "\ufffd"),
                                                        new StringPiece ("a\\uDFFF", "a\ufffd"),
                                                        new StringPiece ("\u2028", "\u2028"),
                                                        new StringPiece ("<&>", "<&>") };

  /** Number texts in the JSON grammar (RFC 7159 section 6) */
  private static final String [] NUMBER_TEXTS = { "0",
                                                  "-0",
                                                  "1",
                                                  "-17",
                                                  "1.5",
                                                  "-0.25",
                                                  "1e2",
                                                  "1E+2",
                                                  "2.5e-3",
                                                  "123456789012345678901234567890",
                                                  "1e400",
                                                  "-1e400",
                                                  "4.9e-324",
                                                  "1e-400",
                                                  "0.1",
                                                  "9007199254740993" };

  private static final String [] WHITESPACE = { "", "", " ", "\n", "\t", "\r\n " };

  /** A JSON text and an XPath expression for the value parse-json gives for it */
  @Immutable
  private record JsonCase (@NonNull String json, @NonNull String expected)
  {
    /**
     * @return <code>true</code> if a number is out of the range of
     *         <code>xs:double</code>: it parses as infinity, which the json
     *         output method cannot write (SERE0020).
     */
    boolean hasInfiniteNumber ()
    {
      return json.contains ("e400");
    }
  }

  private static void _appendWhitespace (@NonNull final ChoiceSequence aChoices, @NonNull final StringBuilder aSB)
  {
    aSB.append (WHITESPACE[(int) aChoices.draw (WHITESPACE.length - 1)]);
  }

  /** @return An XPath expression for the string, with every code point given by number. */
  @NonNull
  private static String _stringExpr (@NonNull final String s)
  {
    if (s.isEmpty ())
      return "''";
    final StringBuilder aSB = new StringBuilder ("codepoints-to-string((");
    final int [] aCPs = s.codePoints ().toArray ();
    for (int i = 0; i < aCPs.length; ++i)
    {
      if (i > 0)
        aSB.append (',');
      aSB.append (aCPs[i]);
    }
    return aSB.append ("))").toString ();
  }

  @NonNull
  private static String _doubleExpr (@NonNull final String sJsonNumber)
  {
    final double d = Double.parseDouble (sJsonNumber);
    if (Double.isInfinite (d))
      return d > 0 ? "xs:double('INF')" : "xs:double('-INF')";
    return "xs:double('" + Double.toString (d) + "')";
  }

  /** Draw a string: its JSON text and its value are appended to the two builders. */
  private static void _drawString (@NonNull final ChoiceSequence aChoices,
                                   @NonNull final StringBuilder aJson,
                                   @NonNull final StringBuilder aValue)
  {
    aJson.append ('"');
    final int nCount = (int) aChoices.draw (5);
    for (int i = 0; i < nCount; ++i)
    {
      final StringPiece aPiece = STRING_PIECES[(int) aChoices.draw (STRING_PIECES.length - 1)];
      aJson.append (aPiece.json ());
      aValue.append (aPiece.value ());
    }
    aJson.append ('"');
  }

  /** Append a random JSON value to the JSON text and its expected value to the expression. */
  private static void _appendValue (@NonNull final ChoiceSequence aChoices,
                                    @NonNull final StringBuilder aJson,
                                    @NonNull final StringBuilder aExpr,
                                    final int nDepth)
  {
    final int nKind = (int) aChoices.draw (nDepth >= MAX_DEPTH ? 3 : 5);
    switch (nKind)
    {
      case 0:
        switch ((int) aChoices.draw (2))
        {
          case 0:
            aJson.append ("null");
            aExpr.append ("()");
            break;
          case 1:
            aJson.append ("true");
            aExpr.append ("true()");
            break;
          default:
            aJson.append ("false");
            aExpr.append ("false()");
            break;
        }
        break;
      case 1:
      {
        String sNumber = NUMBER_TEXTS[(int) aChoices.draw (NUMBER_TEXTS.length - 1)];
        if (aChoices.draw (2) == 0)
        {
          // A double from random bits, written as Java does (a valid JSON number)
          final double d = Double.longBitsToDouble (aChoices.draw (Long.MAX_VALUE - 1));
          if (!Double.isNaN (d) && !Double.isInfinite (d))
            sNumber = (aChoices.draw (1) == 0 ? "-" : "") + Double.toString (d);
        }
        aJson.append (sNumber);
        aExpr.append (_doubleExpr (sNumber));
        break;
      }
      case 2:
      {
        final StringBuilder aValue = new StringBuilder ();
        _drawString (aChoices, aJson, aValue);
        aExpr.append (_stringExpr (aValue.toString ()));
        break;
      }
      case 3:
      {
        aJson.append ('[');
        aExpr.append ('[');
        final int nCount = (int) aChoices.draw (3);
        for (int i = 0; i < nCount; ++i)
        {
          if (i > 0)
          {
            aJson.append (',');
            aExpr.append (", ");
          }
          _appendWhitespace (aChoices, aJson);
          _appendValue (aChoices, aJson, aExpr, nDepth + 1);
          _appendWhitespace (aChoices, aJson);
        }
        aJson.append (']');
        aExpr.append (']');
        break;
      }
      default:
      {
        aJson.append ('{');
        aExpr.append ("map{");
        final int nCount = (int) aChoices.draw (3);
        // Keys must stay distinct after U+FFFD replacement
        final Set <String> aKeys = new HashSet <> ();
        boolean bFirst = true;
        for (int i = 0; i < nCount; ++i)
        {
          final StringBuilder aKeyJson = new StringBuilder ();
          final StringBuilder aKeyValue = new StringBuilder ();
          _drawString (aChoices, aKeyJson, aKeyValue);
          if (!aKeys.add (aKeyValue.toString ()))
            continue;
          if (!bFirst)
          {
            aJson.append (',');
            aExpr.append (", ");
          }
          bFirst = false;
          _appendWhitespace (aChoices, aJson);
          aJson.append (aKeyJson);
          _appendWhitespace (aChoices, aJson);
          aJson.append (':');
          _appendWhitespace (aChoices, aJson);
          aExpr.append (_stringExpr (aKeyValue.toString ())).append (": ");
          _appendValue (aChoices, aJson, aExpr, nDepth + 1);
          _appendWhitespace (aChoices, aJson);
        }
        aJson.append ('}');
        aExpr.append ('}');
        break;
      }
    }
  }

  private static final IGenerator <JsonCase> JSON_CASES = aChoices -> {
    final StringBuilder aJson = new StringBuilder ();
    final StringBuilder aExpr = new StringBuilder ();
    _appendWhitespace (aChoices, aJson);
    _appendValue (aChoices, aJson, aExpr, 0);
    _appendWhitespace (aChoices, aJson);
    return new JsonCase (aJson.toString (), aExpr.toString ());
  };

  private final Map <String, CompiledXPath> m_aCache = new HashMap <> ();
  private final StaticContext m_aStaticCtx = new StaticContext ();

  public FnJsonPropertyTest ()
  {
    m_aStaticCtx.declareVariable (VAR_V);
    m_aStaticCtx.declareVariable (VAR_W);
  }

  @NonNull
  private XdmSequence _eval (@NonNull final String sExpr, @NonNull final XdmSequence aV, @NonNull final XdmSequence aW)
  {
    final CompiledXPath aXPath = m_aCache.computeIfAbsent (sExpr, x -> XPathCompiler.compile (x, m_aStaticCtx));
    return aXPath.evaluate (aXPath.createDynamicContext ().withVariable (VAR_V, aV).withVariable (VAR_W, aW));
  }

  private boolean _evalBoolean (@NonNull final String sExpr, @NonNull final XdmSequence aV, @NonNull final XdmSequence aW)
  {
    final XdmSequence aResult = _eval (sExpr, aV, aW);
    assertEquals (sExpr, 1, aResult.size ());
    return ((XdmAtomic) aResult.get (0)).getBoolean ();
  }

  /** @return The expected value: the expression is evaluated without cache, as each one is used once. */
  @NonNull
  private XdmSequence _evalExpected (@NonNull final String sExpr)
  {
    final CompiledXPath aXPath = XPathCompiler.compile (sExpr, m_aStaticCtx);
    return aXPath.evaluate (aXPath.createDynamicContext ());
  }

  /**
   * <code>fn:parse-json</code> gives the value the JSON text describes: JSON
   * escapes are resolved, characters that are no XML characters (including
   * unpaired surrogates) become U+FFFD, numbers are <code>xs:double</code>
   * (out of range as infinity) and <code>null</code> the empty sequence
   * (F&amp;O 3.1 section 17.5.1).
   */
  @Test
  public void testParseJson ()
  {
    new PropertyChecker ().setTries (1500).forAll (JSON_CASES, aCase -> {
      final XdmSequence aExpected = _evalExpected (aCase.expected ());
      final XdmSequence aJson = XdmSequence.of (XdmAtomic.string (aCase.json ()));
      assertTrue (aCase.json () + " vs. " + aCase.expected (), _evalBoolean ("deep-equal($v, parse-json($w))", aExpected, aJson));
    });
  }

  /**
   * Serializing the value of <code>fn:parse-json</code> with the json method
   * gives a JSON text that parses to the same value (Serialization 3.1
   * section 10).
   */
  @Test
  public void testSerializeRoundTrip ()
  {
    new PropertyChecker ().setTries (1500).forAll (JSON_CASES, Generators.booleans (), (aCase, aIndent) -> {
      if (aCase.hasInfiniteNumber ())
        return;
      final XdmSequence aJson = XdmSequence.of (XdmAtomic.string (aCase.json ()));
      final XdmSequence aParsed = _eval ("parse-json($v)", aJson, XdmSequence.EMPTY);
      // The json method writes the empty sequence as null
      final XdmSequence aSerialized = _eval ("serialize($v, map{'method':'json', 'indent':$w})",
                                             aParsed,
                                             XdmSequence.of (XdmAtomic.bool (aIndent.booleanValue ())));
      final String sSerialized = ((XdmAtomic) aSerialized.get (0)).getString ();
      assertTrue (aCase.json () + " -> " + sSerialized,
                  _evalBoolean ("deep-equal($v, parse-json($w))", aParsed, XdmSequence.of (XdmAtomic.string (sSerialized))));
    });
  }

  /**
   * <code>fn:xml-to-json</code> of <code>fn:json-to-xml</code> gives a JSON
   * text with the same value as the original (F&amp;O 3.1 sections 17.5.3
   * and 17.5.4), and <code>fn:json-to-xml</code> represents the top level
   * value by the element the JSON kind asks for.
   */
  @Test
  public void testJsonToXmlRoundTrip ()
  {
    new PropertyChecker ().setTries (1500).forAll (JSON_CASES, aCase -> {
      // xml-to-json refuses a number element whose value is infinite as xs:double (FOJS0006)
      if (aCase.hasInfiniteNumber ())
        return;
      final XdmSequence aJson = XdmSequence.of (XdmAtomic.string (aCase.json ()));
      final XdmSequence aParsed = _eval ("parse-json($v)", aJson, XdmSequence.EMPTY);
      final XdmSequence aBack = _eval ("xml-to-json(json-to-xml($v))", aJson, XdmSequence.EMPTY);
      final String sBack = ((XdmAtomic) aBack.get (0)).getString ();
      assertTrue (aCase.json () + " -> " + sBack,
                  _evalBoolean ("deep-equal($v, parse-json($w))", aParsed, XdmSequence.of (XdmAtomic.string (sBack))));
      // The escaped form keeps every character, also those that are no XML characters
      final XdmSequence aEscaped = _eval ("xml-to-json(json-to-xml($v, map{'escape':true()}))", aJson, XdmSequence.EMPTY);
      final String sEscaped = ((XdmAtomic) aEscaped.get (0)).getString ();
      assertTrue (aCase.json () + " -> " + sEscaped,
                  _evalBoolean ("deep-equal($v, parse-json($w))", aParsed, XdmSequence.of (XdmAtomic.string (sEscaped))));
    });
  }

  /**
   * A string of XML characters (controls, C1 controls, line separators,
   * quotes, solidus, supplementary characters) serialized with the json
   * method, as a value and as a map key, parses back to the same string:
   * the serializer escapes what JSON requires (Serialization 3.1 section
   * 10.1, RFC 7159 section 7).
   */
  @Test
  public void testSerializeStrings ()
  {
    final IGenerator <String> aStrings = Generators.lists (Generators.of ("a",
                                                                          "\t",
                                                                          "\n",
                                                                          "\r",
                                                                          "\"",
                                                                          "\\",
                                                                          "/",
                                                                          "</",
                                                                          "\u007f",
                                                                          "\u0085",
                                                                          "\u009f",
                                                                          "\u2028",
                                                                          "\u2029",
                                                                          "\u00e9",
                                                                          "\ufffd",
                                                                          "\ud83d\ude00"),
                                                           0,
                                                           8)
                                                   .map (x -> String.join ("", x));
    new PropertyChecker ().setTries (1500).forAll (aStrings, Generators.booleans (), (sValue, aIndent) -> {
      final XdmSequence aV = XdmSequence.of (XdmAtomic.string (sValue));
      final XdmSequence aIndentValue = XdmSequence.of (XdmAtomic.bool (aIndent.booleanValue ()));
      final String sSerialized = ((XdmAtomic) _eval ("serialize(map{$v: [$v]}, map{'method':'json', 'indent':$w})", aV, aIndentValue).get (0))
                                                                                                                                      .getString ();
      final XdmSequence aBack = _eval ("let $m := parse-json($v) return (map:keys($m), $m?*?*)",
                                       XdmSequence.of (XdmAtomic.string (sSerialized)),
                                       XdmSequence.EMPTY);
      assertEquals (sSerialized, 2, aBack.size ());
      assertEquals (sSerialized, sValue, ((XdmAtomic) aBack.get (0)).getString ());
      assertEquals (sSerialized, sValue, ((XdmAtomic) aBack.get (1)).getString ());
    });
  }

  private static final String [] TOKENS = { "{",
                                            "}",
                                            "[",
                                            "]",
                                            ",",
                                            ":",
                                            "\"a\"",
                                            "\"",
                                            "\\",
                                            "\\u",
                                            "\\uD800",
                                            "1",
                                            "-",
                                            "+",
                                            ".",
                                            "e",
                                            "E",
                                            "0",
                                            "true",
                                            "nul",
                                            "null",
                                            " ",
                                            "\n",
                                            "\u0001",
                                            "\u00e9",
                                            "\ud83d\ude00",
                                            "\ud800",
                                            "//",
                                            "/*" };

  /** Random token soup and mutated valid JSON texts */
  private static final IGenerator <String> ARBITRARY_TEXTS = Generators.oneOf (Generators.lists (Generators.of (TOKENS), 0, 20)
                                                                                         .map (x -> String.join ("", x)),
                                                                               JSON_CASES.flatMap (aCase -> aChoices -> {
                                                                                 final StringBuilder aSB = new StringBuilder (aCase.json ());
                                                                                 final int nEdits = 1 + (int) aChoices.draw (2);
                                                                                 for (int i = 0; i < nEdits && aSB.length () > 0; ++i)
                                                                                 {
                                                                                   final int nPos = (int) aChoices.draw (aSB.length () - 1);
                                                                                   if (aChoices.draw (1) == 0)
                                                                                     aSB.deleteCharAt (nPos);
                                                                                   else
                                                                                     aSB.insert (nPos,
                                                                                                 TOKENS[(int) aChoices.draw (TOKENS.length -
                                                                                                                             1)]);
                                                                                 }
                                                                                 return aSB.toString ();
                                                                               }),
                                                                               Generators.unicodeStrings (0, 20));

  /** @return The error code, or <code>null</code> if the expression succeeded. */
  @Nullable
  private String _tryEval (@NonNull final String sExpr, @NonNull final String sText)
  {
    try
    {
      _eval (sExpr, XdmSequence.of (XdmAtomic.string (sText)), XdmSequence.EMPTY);
      return null;
    }
    catch (final XPathException ex)
    {
      return ex.getCode ();
    }
  }

  /**
   * On arbitrary input, <code>fn:parse-json</code> and
   * <code>fn:json-to-xml</code> either succeed or raise FOJS0001 (FOJS0003
   * for a duplicate key with json-to-xml, which rejects them by default), never
   * another error or exception; both functions agree on what is valid JSON.
   */
  @Test
  public void testArbitraryInput ()
  {
    new PropertyChecker ().setTries (3000).forAll (ARBITRARY_TEXTS, sText -> {
      final String sParseError = _tryEval ("parse-json($v)", sText);
      final String sXmlError = _tryEval ("json-to-xml($v)", sText);
      final String sMsg = "'" + sText + "': " + sParseError + " / " + sXmlError;
      if (sParseError != null && !"FOJS0001".equals (sParseError))
        fail (sMsg);
      if (sXmlError != null && !"FOJS0001".equals (sXmlError) && !"FOJS0003".equals (sXmlError))
        fail (sMsg);
      // json-to-xml additionally rejects duplicate keys; otherwise both agree
      if (!"FOJS0003".equals (sXmlError))
        assertEquals (sMsg, sParseError, sXmlError);
    });
  }

  /**
   * Deeply nested arrays and objects either parse or raise an error, never a
   * stack overflow or another Java exception.
   */
  @Test
  public void testDeepNesting ()
  {
    new PropertyChecker ().setTries (20).forAll (Generators.ints (1, 200_000), Generators.booleans (), (aDepth, aObject) -> {
      final int nDepth = aDepth.intValue ();
      final StringBuilder aSB = new StringBuilder (nDepth * 6 + 2);
      for (int i = 0; i < nDepth; ++i)
        aSB.append (aObject.booleanValue () ? "{\"a\":" : "[");
      aSB.append ('1');
      for (int i = 0; i < nDepth; ++i)
        aSB.append (aObject.booleanValue () ? '}' : ']');
      final String sText = aSB.toString ();
      final List <String> aErrors = new ArrayList <> ();
      for (final String sExpr : new String [] { "parse-json($v)", "json-to-xml($v)", "xml-to-json(json-to-xml($v))" })
      {
        final String sError = _tryEval (sExpr, sText);
        if (sError != null)
          aErrors.add (sExpr + ": " + sError);
      }
      // Errors other than a stack overflow (XPDY0130) are not expected for valid JSON
      for (final String sError : aErrors)
        assertTrue ("depth " + nDepth + ": " + sError, sError.endsWith ("XPDY0130"));
    });
  }
}

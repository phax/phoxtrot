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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.Random;

import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.xml.sax.InputSource;

/**
 * Test {@link BracketHoldBackReader} and the XML 1.1 documents the JDK parser misreads without it.
 *
 * @author Philip Helger
 */
public final class BracketHoldBackReaderTest
{
  private static final String PREFIX = "<?xml version='1.1'?><a>";
  /** The texts the JDK XML 1.1 scanner misreads with a ']' at the end of its buffer */
  private static final String [] TEXTS = { "a]a]]&gt;", "a]b]]c]", "]x]]x", "x]]]y", "a]" };

  @NonNull
  private static String _expected (final int nPad, @NonNull final String sText)
  {
    return "x".repeat (nPad) + sText.replace ("&gt;", ">");
  }

  @NonNull
  private static String _document (final int nPad, @NonNull final String sText)
  {
    return PREFIX + "x".repeat (nPad) + sText + "</a>";
  }

  private static void _forBufferBoundaries (@NonNull final IPadConsumer aConsumer) throws Exception
  {
    // The first buffer of the JDK parser has 64 characters, the following ones 8 KB
    for (final int [] aRange : new int [] [] { { 0, 120 }, { 8150, 8300 }, { 16340, 16480 } })
      for (int nPad = aRange[0]; nPad < aRange[1]; ++nPad)
        for (final String sText : TEXTS)
          aConsumer.accept (nPad, sText);
  }

  @FunctionalInterface
  private interface IPadConsumer
  {
    void accept (int nPad, @NonNull String sText) throws Exception;
  }

  @Test
  public void testXml11TextAtBufferBoundaries () throws Exception
  {
    final XdmParser aParser = new XdmParser ();
    _forBufferBoundaries ( (nPad, sText) -> {
      assertEquals (nPad + " " + sText, _expected (nPad, sText), aParser.parse (_document (nPad, sText)).getStringValue ());
      // A character stream of the caller
      assertEquals (nPad + " " + sText,
                    _expected (nPad, sText),
                    aParser.parse (new InputSource (new StringReader (_document (nPad, sText)))).getStringValue ());
    });
  }

  @Test
  public void testChunks () throws IOException
  {
    final Random aRandom = new Random (20261125L);
    for (int nTry = 0; nTry < 5000; ++nTry)
    {
      final StringBuilder aSrc = new StringBuilder ();
      final int nLength = aRandom.nextInt (60);
      for (int i = 0; i < nLength; ++i)
        aSrc.append (aRandom.nextInt (3) == 0 ? 'a' : ']');
      final String sSrc = aSrc.toString ();
      try (final BracketHoldBackReader aReader = new BracketHoldBackReader (new StringReader (sSrc)))
      {
        final StringBuilder aRead = new StringBuilder ();
        final char [] aBuf = new char [40];
        while (true)
        {
          final int nOfs = aRandom.nextInt (4);
          final int nLen = 1 + aRandom.nextInt (aBuf.length - nOfs);
          final int nRead = aReader.read (aBuf, nOfs, nLen);
          if (nRead < 0)
            break;
          assertTrue (sSrc, nRead > 0 && nRead <= nLen);
          aRead.append (aBuf, nOfs, nRead);
          // A chunk ends with ']' only at the end of the source or as a run of ']' filling the request
          if (aBuf[nOfs + nRead - 1] == ']' && aRead.length () < sSrc.length ())
          {
            assertEquals (sSrc + " at " + aRead.length (), nLen, nRead);
            for (int i = 0; i < nRead; ++i)
              assertEquals (sSrc, ']', aBuf[nOfs + i]);
          }
        }
        assertEquals (sSrc, aRead.toString ());
      }
    }
  }
}

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

import java.io.IOException;
import java.io.Reader;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;

/**
 * A reader that never ends a chunk with <code>]</code> while more characters follow: a run of
 * <code>]</code> at the end of a chunk is held back and returned at the start of the next one.
 * <p>
 * The XML 1.1 scanner of the JDK parser (JDK 17 to 27 at least; XML 1.0 is not affected) drops or
 * repeats characters of text when a <code>]</code> is the last character of its buffer when the
 * buffer is refilled (after the first 64 characters, then every 8 KB): <code>a]a]]&amp;gt;</code>
 * there is read as <code>a]a]a]]&gt;</code>. The buffer is filled with one read of the source, so
 * a source read through this reader never puts a <code>]</code> at its end. Only a run of
 * <code>]</code> longer than the requested length is returned as it is.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class BracketHoldBackReader extends Reader
{
  private final Reader m_aIn;
  /** The number of <code>]</code> held back from the previous read */
  private int m_nPending;
  private boolean m_bEOF;

  BracketHoldBackReader (@NonNull final Reader aIn)
  {
    ValueEnforcer.notNull (aIn, "Reader");
    m_aIn = aIn;
  }

  /**
   * Read the held back <code>]</code> and then what the source gives into the buffer, and hold back
   * the <code>]</code> at the end of the result again unless the source has ended.
   */
  @Override
  public int read (final char @NonNull [] aBuf, final int nOfs, final int nLen) throws IOException
  {
    ValueEnforcer.isArrayOfsLen (aBuf, nOfs, nLen);
    if (nLen == 0)
      return 0;
    while (true)
    {
      final int nFromPending = Math.min (m_nPending, nLen);
      for (int i = 0; i < nFromPending; ++i)
        aBuf[nOfs + i] = ']';
      m_nPending -= nFromPending;
      int nCount = nFromPending;
      if (nCount < nLen && !m_bEOF)
      {
        final int nRead = m_aIn.read (aBuf, nOfs + nCount, nLen - nCount);
        if (nRead < 0)
          m_bEOF = true;
        else
          nCount += nRead;
      }
      if (nCount == 0)
        return -1;
      // Nothing follows: no need to hold anything back
      if (m_bEOF)
        return nCount;
      int nKeep = nCount;
      while (nKeep > 0 && aBuf[nOfs + nKeep - 1] == ']')
        nKeep--;
      if (nKeep > 0)
      {
        m_nPending += nCount - nKeep;
        return nKeep;
      }
      // A run of ']' that fills the whole request cannot be held back
      if (nCount == nLen)
        return nCount;
      // Only ']' so far: hold them back and read what follows them
      m_nPending += nCount;
    }
  }

  @Override
  public void close () throws IOException
  {
    m_aIn.close ();
  }
}

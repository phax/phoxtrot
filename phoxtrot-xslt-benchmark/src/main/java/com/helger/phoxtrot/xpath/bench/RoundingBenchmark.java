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
package com.helger.phoxtrot.xpath.bench;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * The rounding workloads of {@link RoundingWorkload}: 21 roundings per invoice line with the XSLT
 * 3.0 functions and the XSLT 1.0 templates of the rounding stylesheets.
 *
 * @author Philip Helger
 */
@State (Scope.Benchmark)
@BenchmarkMode (Mode.AverageTime)
@OutputTimeUnit (TimeUnit.MILLISECONDS)
@Warmup (iterations = 3, time = 1)
@Measurement (iterations = 5, time = 1)
@Fork (value = 1, jvmArgsAppend = { "-Xmx3g", "-Xss8m" })
public class RoundingBenchmark
{
  @Param ({ "functions-3.0", "templates-1.0", "templates-1.0-as-3.0", "templates-1.0-jdk" })
  public String m_sCase;

  /** The number of invoice lines (21 roundings each). */
  @Param ({ "1000" })
  public int m_nSize;

  private IWorkload m_aWorkload;

  @Setup
  public void setup () throws Exception
  {
    m_aWorkload = EWorkloadGroup.ROUNDING.createVerified (m_sCase, m_nSize);
  }

  @Benchmark
  public Object run () throws Exception
  {
    return m_aWorkload.run ();
  }
}

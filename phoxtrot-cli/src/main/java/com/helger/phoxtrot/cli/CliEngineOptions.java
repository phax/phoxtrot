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
package com.helger.phoxtrot.cli;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.phoxtrot.xpath.EThreading;

import picocli.CommandLine.Option;

/**
 * The engine options shared by the commands that compile or evaluate.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class CliEngineOptions
{
  /** The thread compilation and evaluation run on. */
  @Option (names = "--threading",
           paramLabel = "MODE",
           defaultValue = "NEW_THREAD",
           description = "The thread compilation and evaluation run on: ${COMPLETION-CANDIDATES} (default ${DEFAULT-VALUE}). A new thread has a large stack for deep recursion.")
  EThreading m_eThreading;

  /** The step limit, 0 for none. */
  @Option (names = "--max-steps",
           paramLabel = "N",
           defaultValue = "0",
           description = "Stop an evaluation after that many loop iterations and function calls with XPDY0130 (default 0: no limit).")
  long m_nMaxSteps;

  /** Constructor. Called by picocli. */
  CliEngineOptions ()
  {}
}

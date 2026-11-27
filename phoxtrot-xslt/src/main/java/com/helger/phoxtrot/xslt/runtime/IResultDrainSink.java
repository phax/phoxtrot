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
package com.helger.phoxtrot.xslt.runtime;

import org.jspecify.annotations.NonNull;

import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * Where a {@link ResultDrain} writes the parts of the principal result that
 * can no longer change: a serializer or a SAX content handler. Serialization
 * errors are thrown as {@link com.helger.phoxtrot.xpath.XPathException}; the
 * drain records them and raises the first one at the end.
 *
 * @author Philip Helger
 */
interface IResultDrainSink
{
  /**
   * Write a complete node with its subtree.
   *
   * @param aNode
   *        The node.
   */
  void writeNode (@NonNull IXdmNode aNode);

  /**
   * Write the start tag of an element whose children follow as separate
   * parts.
   *
   * @param aElement
   *        The element, whose name, attributes and namespaces are final.
   */
  void startElement (@NonNull IXdmNode aElement);

  /** Write the end tag of the innermost started element. */
  void endElement ();

  /** Complete the result after its last part. */
  void finish ();

  /**
   * Pass on what was written so far, after an error.
   *
   * @throws java.io.UncheckedIOException
   *         if the target fails
   */
  void flush ();
}

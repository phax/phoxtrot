#!/bin/bash
#
# Copyright (C) 2026 Philip Helger (www.helger.com)
# philip[at]helger[dot]com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Checks, analyses and evaluates XPath expressions.

set -euo pipefail
. "$(cd "$(dirname "$0")" && pwd)/common.sh"

NS=(--ns inv=urn:example:invoice)

# Static errors: both files are fine, the last expression is incomplete (XPST0003 with its column) - exit code 1
phoxtrot xpath check "${NS[@]}" -f total-check.xpath -f cardinality.xpath 'count(//inv:Line'

# With static typing: total-check.xpath passes, cardinality.xpath fails with XPTY0004 - exit code 1
phoxtrot xpath check "${NS[@]}" --static-typing --context-type 'document-node()' -f total-check.xpath -f cardinality.xpath

# Streamability: the sum is consuming (streamable), the positional filter is free-ranging - exit code 1
phoxtrot xpath streamability "${NS[@]}" 'sum(inv:Line/@price)' 'inv:Line[last()]'
# but needs only the subtree of the context node: fine in burst mode
phoxtrot xpath streamability "${NS[@]}" --mode BURST 'inv:Line[last()]'

# Evaluation against a document: true() for the valid invoice, false() for the invalid one
phoxtrot xpath eval "${NS[@]}" -q -c invoice.xml -f total-check.xpath 'sum(//inv:Line/@qty)' '//inv:Line[1]'
phoxtrot xpath eval "${NS[@]}" -q -c invoice-invalid.xml -f total-check.xpath
phoxtrot xpath eval -q --method json 'map { "lines": array { 1 to 3 } }'

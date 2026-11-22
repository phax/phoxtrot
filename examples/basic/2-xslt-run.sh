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

# Runs stylesheets. The results are written to target/ (not under version control).

set -euo pipefail
. "$(cd "$(dirname "$0")" && pwd)/common.sh"

# HTML with a parameter; the xsl:message goes to the error output
phoxtrot xslt run -s invoice-to-html.xsl -p title=Rechnung -o target/invoice.html invoice.xml

# The xsl:assert fails for the invoice with the wrong total - exit code 1
phoxtrot xslt run -s invoice-to-html.xsl -o target/invoice-invalid.html invoice-invalid.xml

# The same run with a trace of the templates and functions
phoxtrot xslt run -q --trace -s invoice-to-html.xsl -o target/invoice.html invoice.xml

# The source is read as a stream
phoxtrot xslt run --streamed -s streamable.xsl invoice.xml

# xsl:result-document is rejected without --result-documents (XTSE0010) - exit code 1
phoxtrot xslt run -s split.xsl invoice.xml
# and writes target/lines/line-1.xml ... with it
phoxtrot xslt run -s split.xsl --result-documents target/lines -o target/lines/lines.xml invoice.xml

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

# Checks stylesheets without running them: static errors with module, line and column, and the streamability rules.

set -euo pipefail
. "$(cd "$(dirname "$0")" && pwd)/common.sh"

# OK, OK (with the included functions.xsl), and XPST0008 in broken.xsl - exit code 1
phoxtrot xslt check invoice-to-html.xsl split.xsl broken.xsl

# Claims streaming: streamable.xsl meets the rules, not-streamable.xsl fails with XTSE3430 - exit code 1
phoxtrot xslt check --streamable streamable.xsl not-streamable.xsl

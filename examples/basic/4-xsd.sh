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

# Checks the XML Schema and validates documents against it.

set -euo pipefail
. "$(cd "$(dirname "$0")" && pwd)/common.sh"

# xs:assert is XSD 1.1 only - exit code 1 with XSD 1.0, valid with 1.1
phoxtrot xsd check invoice.xsd
phoxtrot xsd check --xsd-version 1.1 invoice.xsd

# invoice.xml is valid; invoice-invalid.xml misses the buyer, has a quantity of 0 and a wrong total - exit code 1
phoxtrot xsd validate --xsd-version 1.1 --xsd invoice.xsd invoice.xml invoice-invalid.xml

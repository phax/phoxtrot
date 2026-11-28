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
#

# Settings shared by the example scripts: the CLI JAR (set CLI_JAR to use another one) and the function "phoxtrot"
# that runs it, printing the command line and the exit code.

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
VERSION="$(sed -n '/<parent>/,/<\/parent>/!{s/^  <version>\(.*\)<\/version>.*/\1/p;}' "$ROOT/pom.xml" | head -1)"

CLI_JAR="${CLI_JAR:-$ROOT/phoxtrot-cli/target/phoxtrot-cli-$VERSION-shaded.jar}"
if [ ! -f "$CLI_JAR" ]; then
  echo "The CLI JAR '$CLI_JAR' does not exist - run 'mvn install' in '$ROOT' or set CLI_JAR" >&2
  exit 3
fi

# The examples refer to their files relative to this folder
cd "$HERE"

phoxtrot ()
{
  echo
  echo "$ phoxtrot $*"
  local RC=0
  java -jar "$CLI_JAR" "$@" || RC=$?
  echo "(exit code $RC)"
}

<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright (C) 2026 Philip Helger (www.helger.com)
    philip[at]helger[dot]com

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

            http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<!-- Declared streamable but not guaranteed streamable: the average reads the lines twice (XTSE3430) -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:inv="urn:example:invoice"
                exclude-result-prefixes="inv">
  <xsl:mode streamable="yes"/>

  <xsl:template match="/">
    <average>
      <xsl:value-of select="sum(//inv:Line/@price) div count(//inv:Line)"/>
    </average>
  </xsl:template>
</xsl:stylesheet>

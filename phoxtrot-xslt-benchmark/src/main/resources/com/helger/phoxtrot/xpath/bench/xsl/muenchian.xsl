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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text"/>
  <xsl:key name="byCat" match="line" use="@cat"/>

  <xsl:template match="/">
    <xsl:for-each select="invoice/lines/line[generate-id() = generate-id(key('byCat', @cat)[1])]">
      <xsl:sort select="@cat"/>
      <xsl:value-of select="concat(@cat, '=', count(key('byCat', @cat)), ',')"/>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>

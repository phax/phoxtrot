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
<!-- Included by invoice-to-html.xsl -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:f="urn:example:functions"
                exclude-result-prefixes="xs f">
  <!-- The amount of a line: quantity times price -->
  <xsl:function name="f:amount" as="xs:decimal">
    <xsl:param name="line" as="element()"/>
    <xsl:sequence select="xs:decimal($line/@qty) * xs:decimal($line/@price)"/>
  </xsl:function>

  <!-- An amount with two decimals -->
  <xsl:function name="f:format" as="xs:string">
    <xsl:param name="amount" as="xs:decimal"/>
    <xsl:sequence select="format-number($amount, '#,##0.00')"/>
  </xsl:function>
</xsl:stylesheet>

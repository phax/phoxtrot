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
<!-- Renders the invoice as HTML; checks the total with xsl:assert -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:inv="urn:example:invoice"
                xmlns:f="urn:example:functions"
                exclude-result-prefixes="xs inv f">
  <xsl:include href="functions.xsl"/>
  <xsl:output method="html" html-version="5" indent="yes"/>

  <xsl:param name="title" as="xs:string" select="'Invoice'"/>

  <xsl:template match="/inv:Invoice">
    <xsl:message select="'Rendering invoice ' || @id || ' with ' || count(inv:Line) || ' line(s)'"/>
    <xsl:assert test="xs:decimal(inv:Total) = sum(inv:Line ! f:amount(.))"
                select="'The total ' || inv:Total || ' is not the sum of the lines'"/>
    <html>
      <head><title><xsl:value-of select="$title || ' ' || @id"/></title></head>
      <body>
        <h1><xsl:value-of select="$title || ' ' || @id || ' for ' || inv:Buyer"/></h1>
        <table>
          <tr><th>Item</th><th>Quantity</th><th>Price</th><th>Amount</th></tr>
          <xsl:apply-templates select="inv:Line"/>
          <tr><td colspan="3">Total</td><td><xsl:value-of select="f:format(xs:decimal(inv:Total))"/></td></tr>
        </table>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="inv:Line">
    <tr>
      <td><xsl:value-of select="."/></td>
      <td><xsl:value-of select="@qty"/></td>
      <td><xsl:value-of select="f:format(xs:decimal(@price))"/></td>
      <td><xsl:value-of select="f:format(f:amount(.))"/></td>
    </tr>
  </xsl:template>
</xsl:stylesheet>

<?xml version="1.0" encoding="UTF-8"?>
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

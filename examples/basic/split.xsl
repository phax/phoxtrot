<?xml version="1.0" encoding="UTF-8"?>
<!-- Writes one document per line with xsl:result-document; needs result-documents to be allowed -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:inv="urn:example:invoice"
                exclude-result-prefixes="inv">
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/inv:Invoice">
    <xsl:for-each select="inv:Line">
      <xsl:result-document href="line-{position()}.xml">
        <line invoice="{../@id}" qty="{@qty}" price="{@price}"><xsl:value-of select="."/></line>
      </xsl:result-document>
    </xsl:for-each>
    <lines count="{count(inv:Line)}"/>
  </xsl:template>
</xsl:stylesheet>

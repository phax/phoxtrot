<?xml version="1.0" encoding="UTF-8"?>
<!-- Guaranteed streamable: one downward pass over the lines -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:inv="urn:example:invoice"
                exclude-result-prefixes="inv">
  <xsl:mode streamable="yes"/>
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/">
    <summary>
      <xsl:for-each select="inv:Invoice/inv:Line">
        <line name="{.}" amount="{@qty * @price}"/>
      </xsl:for-each>
    </summary>
  </xsl:template>
</xsl:stylesheet>

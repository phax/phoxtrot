<?xml version="1.0" encoding="UTF-8"?>
<!-- Has a static error: the variable $currency is not declared (XPST0008) -->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:inv="urn:example:invoice"
                exclude-result-prefixes="inv">
  <xsl:template match="/inv:Invoice">
    <total>
      <xsl:value-of select="inv:Total || ' ' || $currency"/>
    </total>
  </xsl:template>
</xsl:stylesheet>

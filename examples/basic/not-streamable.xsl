<?xml version="1.0" encoding="UTF-8"?>
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

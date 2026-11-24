<?xml version="1.0" encoding="UTF-8"?>
<!--
  XSLT 3.0 implementations of the rounding modes of java.math.RoundingMode,
  with the semantics of java.math.BigDecimal.setScale(int, RoundingMode).

  All functions take
    $value as xs:decimal  - the number to round
    $scale as xs:integer  - number of fraction digits to keep
                            (negative values round to tens, hundreds, ...,
                             like a negative BigDecimal scale)
  and return an xs:decimal.

  Use xs:decimal and not xs:double: a double such as 2.675 is actually
  2.67499999... in binary, so HALF_* modes would give "wrong" results.

  Note: xs:decimal does not keep trailing zeros (2.50 becomes 2.5). Use
  format-number(..., '0.00') when a fixed number of digits must be shown.

  Usage:
    <xsl:import href="rounding-modes.xsl"/>
    xmlns:rm="urn:x-rounding-mode"
    rm:half-up(xs:decimal('2.345'), 2)          => 2.35
    rm:round(xs:decimal('2.345'), 2, 'HALF_UP') => 2.35
-->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:rm="urn:x-rounding-mode"
                exclude-result-prefixes="#all">

  <!-- ============================================================ -->
  <!-- Helpers                                                       -->
  <!-- ============================================================ -->

  <!-- 10^$scale as an exact xs:decimal (also for negative scales) -->
  <xsl:function name="rm:pow10" as="xs:decimal" visibility="private">
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:variable name="p" as="xs:decimal"
                  select="xs:decimal('1' || string-join((1 to abs($scale)) ! '0'))"/>
    <xsl:sequence select="if ($scale ge 0) then $p else 1 div $p"/>
  </xsl:function>

  <!-- Applies an integer rounding step to $value shifted by $scale digits -->
  <xsl:function name="rm:shift-apply" as="xs:decimal" visibility="private">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:param name="op" as="function(xs:decimal) as xs:decimal"/>
    <xsl:variable name="f" as="xs:decimal" select="rm:pow10($scale)"/>
    <xsl:sequence select="$op($value * $f) div $f"/>
  </xsl:function>

  <!-- Applies $op to the absolute value and restores the sign -->
  <xsl:function name="rm:signed" as="xs:decimal" visibility="private">
    <xsl:param name="x" as="xs:decimal"/>
    <xsl:param name="op" as="function(xs:decimal) as xs:decimal"/>
    <xsl:sequence select="if ($x lt 0) then -$op(abs($x)) else $op($x)"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- RoundingMode.UP: away from zero                               -->
  <!--   5.5 -> 6, 1.1 -> 2, -1.1 -> -2, -5.5 -> -6                 -->
  <!-- ============================================================ -->
  <xsl:function name="rm:up" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:sequence select="rm:shift-apply($value, $scale,
                            function($x) { rm:signed($x, ceiling#1) })"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- RoundingMode.DOWN: towards zero (truncate)                    -->
  <!--   5.5 -> 5, 1.9 -> 1, -1.9 -> -1, -5.5 -> -5                 -->
  <!-- ============================================================ -->
  <xsl:function name="rm:down" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:sequence select="rm:shift-apply($value, $scale,
                            function($x) { rm:signed($x, floor#1) })"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- RoundingMode.CEILING: towards positive infinity               -->
  <!--   5.5 -> 6, 1.1 -> 2, -1.1 -> -1, -5.5 -> -5                 -->
  <!-- ============================================================ -->
  <xsl:function name="rm:ceiling" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:sequence select="rm:shift-apply($value, $scale, ceiling#1)"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- RoundingMode.FLOOR: towards negative infinity                 -->
  <!--   5.5 -> 5, 1.9 -> 1, -1.1 -> -2, -5.5 -> -6                 -->
  <!-- ============================================================ -->
  <xsl:function name="rm:floor" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:sequence select="rm:shift-apply($value, $scale, floor#1)"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- RoundingMode.HALF_UP: nearest neighbour, ties away from zero  -->
  <!-- ("kaufmaennisches Runden", UStAE 14.5 Abs. 20)               -->
  <!--   5.5 -> 6, 2.5 -> 3, -2.5 -> -3, 1.4 -> 1                   -->
  <!-- Note: fn:round() is NOT the same - it rounds ties towards     -->
  <!-- positive infinity (round(-2.5) = -2).                         -->
  <!-- ============================================================ -->
  <xsl:function name="rm:half-up" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:sequence select="rm:shift-apply($value, $scale,
                            function($x) { rm:signed($x, function($a) { floor($a + 0.5) }) })"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- RoundingMode.HALF_DOWN: nearest neighbour, ties towards zero  -->
  <!--   5.5 -> 5, 2.5 -> 2, -2.5 -> -2, 1.6 -> 2                   -->
  <!-- ============================================================ -->
  <xsl:function name="rm:half-down" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:sequence select="rm:shift-apply($value, $scale,
                            function($x) { rm:signed($x, function($a) { ceiling($a - 0.5) }) })"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- RoundingMode.HALF_EVEN: nearest neighbour, ties to the even   -->
  <!-- neighbour ("banker's rounding")                               -->
  <!--   5.5 -> 6, 2.5 -> 2, -2.5 -> -2, 1.5 -> 2                   -->
  <!-- fn:round-half-to-even has exactly these semantics, incl.      -->
  <!-- negative precision.                                           -->
  <!-- ============================================================ -->
  <xsl:function name="rm:half-even" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:sequence select="round-half-to-even($value, $scale)"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- RoundingMode.UNNECESSARY: value must already have at most     -->
  <!-- $scale fraction digits, otherwise an error is raised (Java:   -->
  <!-- ArithmeticException "Rounding necessary").                    -->
  <!-- ============================================================ -->
  <xsl:function name="rm:unnecessary" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:variable name="truncated" as="xs:decimal" select="rm:down($value, $scale)"/>
    <xsl:if test="$truncated ne $value">
      <xsl:sequence select="error(xs:QName('rm:ROUNDING_NECESSARY'),
                              'Rounding necessary: ' || $value || ' has more than ' || $scale || ' fraction digit(s)')"/>
    </xsl:if>
    <xsl:sequence select="$value"/>
  </xsl:function>

  <!-- ============================================================ -->
  <!-- Generic entry point using the Java enum constant names        -->
  <!-- ('UP', 'DOWN', 'CEILING', 'FLOOR', 'HALF_UP', 'HALF_DOWN',    -->
  <!--  'HALF_EVEN', 'UNNECESSARY'), case-insensitive                -->
  <!-- ============================================================ -->
  <xsl:function name="rm:round" as="xs:decimal">
    <xsl:param name="value" as="xs:decimal"/>
    <xsl:param name="scale" as="xs:integer"/>
    <xsl:param name="mode" as="xs:string"/>
    <xsl:variable name="impl" as="map(xs:string, function(*))" select="map {
        'UP'          : rm:up#2,
        'DOWN'        : rm:down#2,
        'CEILING'     : rm:ceiling#2,
        'FLOOR'       : rm:floor#2,
        'HALF_UP'     : rm:half-up#2,
        'HALF_DOWN'   : rm:half-down#2,
        'HALF_EVEN'   : rm:half-even#2,
        'UNNECESSARY' : rm:unnecessary#2 }"/>
    <xsl:variable name="f" select="$impl(upper-case(normalize-space($mode)))"/>
    <xsl:sequence select="if (exists($f))
                          then $f($value, $scale)
                          else error(xs:QName('rm:UNKNOWN_MODE'), 'Unknown rounding mode: ' || $mode)"/>
  </xsl:function>

</xsl:stylesheet>

<?xml version="1.0" encoding="UTF-8"?>
<!--
  XSLT 1.0 implementations of the rounding modes of java.math.RoundingMode,
  with the semantics of java.math.BigDecimal.setScale(int, RoundingMode).
  The XSLT 1.0 counterpart of rounding-modes.xsl.

  XSLT 1.0 has neither functions nor xs:decimal, and its numbers are IEEE
  doubles (2.675 is 2.67499999... there). These named templates therefore
  work on the decimal string digit by digit and are exact for any number of
  digits. They use only XPath 1.0 functions and never compare a string with a
  number, so they give the same results on XSLT 2.0 and 3.0 processors, also
  with version="3.0".

  All public templates take
    $value - the number to round, as a string in xs:decimal notation:
             optional sign, digits, optional decimal point and digits
             ("-2.345", "+.5", "12."), no exponent. Pass a string: a number
             is converted with string() first, which is only exact if the
             double holds the value exactly.
    $scale - number of fraction digits to keep (negative values round to
             tens, hundreds, ..., like a negative BigDecimal scale)
  and output the rounded value in canonical xs:decimal form, like the
  XSLT 3.0 version: no trailing fraction zeros and no decimal point for an
  integer (2.50 becomes 2.5, 3.0 becomes 3). Use format-number(..., '0.00')
  when a fixed number of digits must be shown.

  XSLT 1.0 has no catchable errors, so an unknown mode, an invalid value or
  scale, and a necessary rounding under UNNECESSARY (Java: ArithmeticException
  "Rounding necessary") stop the transformation with
  xsl:message terminate="yes". The message starts with rm:UNKNOWN_MODE,
  rm:INVALID_VALUE, rm:INVALID_SCALE or rm:ROUNDING_NECESSARY.

  Usage:
    <xsl:import href="rounding-modes-xslt1.xsl"/>
    xmlns:rm="urn:x-rounding-mode"

    <xsl:call-template name="rm:half-up">
      <xsl:with-param name="value" select="'2.345'"/>
      <xsl:with-param name="scale" select="2"/>
    </xsl:call-template>                                 => 2.35

    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="'2.345'"/>
      <xsl:with-param name="scale" select="2"/>
      <xsl:with-param name="mode" select="'HALF_UP'"/>
    </xsl:call-template>                                 => 2.35

  To use the result as a string, call the template inside xsl:variable.
-->
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:rm="urn:x-rounding-mode"
                exclude-result-prefixes="rm">

  <!-- Internal: $count zeros (nothing for a count of zero or less) -->
  <xsl:template name="rm:zeros">
    <xsl:param name="count" select="0"/>
    <xsl:if test="$count &gt; 0">
      <xsl:value-of select="substring('00000000000000000000000000000000', 1, $count)"/>
      <xsl:if test="$count &gt; 32">
        <xsl:call-template name="rm:zeros">
          <xsl:with-param name="count" select="$count - 32"/>
        </xsl:call-template>
      </xsl:if>
    </xsl:if>
  </xsl:template>

  <!-- Internal: the digit string $digits plus one ('' counts as 0) -->
  <xsl:template name="rm:increment">
    <xsl:param name="digits"/>
    <xsl:variable name="len" select="string-length($digits)"/>
    <xsl:variable name="last" select="substring($digits, $len)"/>
    <xsl:choose>
      <xsl:when test="$len = 0">1</xsl:when>
      <xsl:when test="$last != '9'">
        <xsl:value-of select="concat(substring($digits, 1, $len - 1), translate($last, '012345678', '123456789'))"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="rm:increment">
          <xsl:with-param name="digits" select="substring($digits, 1, $len - 1)"/>
        </xsl:call-template>
        <xsl:text>0</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Internal: $digits without leading zeros ('' if all are zero) -->
  <xsl:template name="rm:strip-leading-zeros">
    <xsl:param name="digits"/>
    <xsl:choose>
      <xsl:when test="starts-with($digits, '0')">
        <xsl:call-template name="rm:strip-leading-zeros">
          <xsl:with-param name="digits" select="substring($digits, 2)"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$digits"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Internal: $digits without trailing zeros ('' if all are zero) -->
  <xsl:template name="rm:strip-trailing-zeros">
    <xsl:param name="digits"/>
    <xsl:choose>
      <xsl:when test="substring($digits, string-length($digits)) = '0'">
        <xsl:call-template name="rm:strip-trailing-zeros">
          <xsl:with-param name="digits" select="substring($digits, 1, string-length($digits) - 1)"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$digits"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!--
    Generic entry point using the Java enum constant names ('UP', 'DOWN',
    'CEILING', 'FLOOR', 'HALF_UP', 'HALF_DOWN', 'HALF_EVEN', 'UNNECESSARY'),
    case-insensitive and with surrounding whitespace ignored.

    The value is split into its digits and the position of the decimal point.
    The first (integer digits + $scale) digits are kept, the others dropped
    (with zeros added on either side where the scale reaches beyond the
    digits). The mode decides from the sign, the dropped digits and the last
    kept digit whether the kept digits move one unit away from zero.
  -->
  <xsl:template name="rm:round">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:param name="mode"/>
    <xsl:variable name="m" select="translate(normalize-space($mode), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/>
    <xsl:variable name="v" select="normalize-space($value)"/>
    <xsl:variable name="negative" select="starts-with($v, '-')"/>
    <xsl:variable name="unsigned" select="substring($v, 1 + number(starts-with($v, '-') or starts-with($v, '+')))"/>
    <xsl:variable name="int-part" select="substring-before(concat($unsigned, '.'), '.')"/>
    <xsl:variable name="frac-part" select="substring-after($unsigned, '.')"/>
    <xsl:variable name="digits" select="concat($int-part, $frac-part)"/>
    <xsl:variable name="s" select="number($scale)"/>
    <xsl:choose>
      <xsl:when test="contains($m, '|') or not(contains('|UP|DOWN|CEILING|FLOOR|HALF_UP|HALF_DOWN|HALF_EVEN|UNNECESSARY|', concat('|', $m, '|')))">
        <xsl:message terminate="yes">rm:UNKNOWN_MODE: Unknown rounding mode: <xsl:value-of select="$mode"/></xsl:message>
      </xsl:when>
      <xsl:when test="$digits = '' or translate($digits, '0123456789', '') != '' or contains($frac-part, '.')">
        <xsl:message terminate="yes">rm:INVALID_VALUE: Not a decimal number: <xsl:value-of select="$value"/></xsl:message>
      </xsl:when>
      <xsl:when test="not($s = floor($s))">
        <xsl:message terminate="yes">rm:INVALID_SCALE: Not an integer scale: <xsl:value-of select="$scale"/></xsl:message>
      </xsl:when>
      <xsl:otherwise>
        <!-- Number of leading digits that are kept; zero or less keeps none -->
        <xsl:variable name="keep" select="string-length($int-part) + $s"/>
        <xsl:variable name="padded">
          <xsl:value-of select="$digits"/>
          <xsl:call-template name="rm:zeros">
            <xsl:with-param name="count" select="$keep - string-length($digits)"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="leading">
          <xsl:call-template name="rm:zeros">
            <xsl:with-param name="count" select="-$keep"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="kept" select="substring($padded, 1, $keep)"/>
        <xsl:variable name="dropped" select="concat($leading, substring($padded, $keep + 1))"/>
        <xsl:variable name="inexact" select="translate($dropped, '0', '') != ''"/>
        <!-- Digits as numbers (an absent digit is 0): XSLT 2.0 and later refuse to compare a string with a number -->
        <xsl:variable name="first" select="number(concat('0', substring($dropped, 1, 1)))"/>
        <xsl:variable name="beyond-half" select="translate(substring($dropped, 2), '0', '') != ''"/>
        <xsl:variable name="odd" select="number(concat('0', substring($kept, string-length($kept)))) mod 2 = 1"/>
        <xsl:variable name="more-than-half" select="$first &gt; 5 or ($first = 5 and $beyond-half)"/>
        <!-- 'true' if the kept digits move one unit away from zero. One short
             expression per mode: the JDK processor refuses long XPath
             expressions by default (jdk.xml.xpathExprOpLimit) -->
        <xsl:variable name="away">
          <xsl:choose>
            <xsl:when test="$m = 'UP'">
              <xsl:value-of select="$inexact"/>
            </xsl:when>
            <xsl:when test="$m = 'CEILING'">
              <xsl:value-of select="$inexact and not($negative)"/>
            </xsl:when>
            <xsl:when test="$m = 'FLOOR'">
              <xsl:value-of select="$inexact and $negative"/>
            </xsl:when>
            <xsl:when test="$m = 'HALF_UP'">
              <xsl:value-of select="$first &gt;= 5"/>
            </xsl:when>
            <xsl:when test="$m = 'HALF_DOWN'">
              <xsl:value-of select="$more-than-half"/>
            </xsl:when>
            <xsl:when test="$m = 'HALF_EVEN'">
              <xsl:value-of select="$more-than-half or ($first = 5 and $odd)"/>
            </xsl:when>
            <xsl:otherwise>false</xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <xsl:if test="$m = 'UNNECESSARY' and $inexact">
          <xsl:message terminate="yes">rm:ROUNDING_NECESSARY: Rounding necessary: <xsl:value-of select="$value"/> has more than <xsl:value-of select="$scale"/> fraction digit(s)</xsl:message>
        </xsl:if>
        <!-- The digits of the result; a negative scale appends its zeros -->
        <xsl:variable name="magnitude">
          <xsl:choose>
            <xsl:when test="$away = 'true'">
              <xsl:call-template name="rm:increment">
                <xsl:with-param name="digits" select="$kept"/>
              </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="$kept"/>
            </xsl:otherwise>
          </xsl:choose>
          <xsl:call-template name="rm:zeros">
            <xsl:with-param name="count" select="-$s"/>
          </xsl:call-template>
        </xsl:variable>
        <!-- Number of fraction digits in $magnitude: the scale, or 0 for a negative one -->
        <xsl:variable name="fraction-digits" select="$s * number($s &gt; 0)"/>
        <!-- Leading zeros so that there is an integer digit before the point -->
        <xsl:variable name="all">
          <xsl:call-template name="rm:zeros">
            <xsl:with-param name="count" select="$fraction-digits"/>
          </xsl:call-template>
          <xsl:value-of select="$magnitude"/>
        </xsl:variable>
        <xsl:variable name="int-result">
          <xsl:call-template name="rm:strip-leading-zeros">
            <xsl:with-param name="digits" select="substring($all, 1, string-length($all) - $fraction-digits)"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="frac-result">
          <xsl:call-template name="rm:strip-trailing-zeros">
            <xsl:with-param name="digits" select="substring($all, string-length($all) - $fraction-digits + 1)"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:choose>
          <xsl:when test="$int-result = '' and $frac-result = ''">0</xsl:when>
          <xsl:otherwise>
            <xsl:if test="$negative">-</xsl:if>
            <xsl:choose>
              <xsl:when test="$int-result = ''">0</xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="$int-result"/>
              </xsl:otherwise>
            </xsl:choose>
            <xsl:if test="$frac-result != ''">
              <xsl:value-of select="concat('.', $frac-result)"/>
            </xsl:if>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- RoundingMode.UP: away from zero (5.5 -> 6, 1.1 -> 2, -1.1 -> -2, -5.5 -> -6) -->
  <xsl:template name="rm:up">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="$value"/>
      <xsl:with-param name="scale" select="$scale"/>
      <xsl:with-param name="mode" select="'UP'"/>
    </xsl:call-template>
  </xsl:template>

  <!-- RoundingMode.DOWN: towards zero, truncate (5.5 -> 5, 1.9 -> 1, -1.9 -> -1, -5.5 -> -5) -->
  <xsl:template name="rm:down">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="$value"/>
      <xsl:with-param name="scale" select="$scale"/>
      <xsl:with-param name="mode" select="'DOWN'"/>
    </xsl:call-template>
  </xsl:template>

  <!-- RoundingMode.CEILING: towards positive infinity (5.5 -> 6, 1.1 -> 2, -1.1 -> -1, -5.5 -> -5) -->
  <xsl:template name="rm:ceiling">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="$value"/>
      <xsl:with-param name="scale" select="$scale"/>
      <xsl:with-param name="mode" select="'CEILING'"/>
    </xsl:call-template>
  </xsl:template>

  <!-- RoundingMode.FLOOR: towards negative infinity (5.5 -> 5, 1.9 -> 1, -1.1 -> -2, -5.5 -> -6) -->
  <xsl:template name="rm:floor">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="$value"/>
      <xsl:with-param name="scale" select="$scale"/>
      <xsl:with-param name="mode" select="'FLOOR'"/>
    </xsl:call-template>
  </xsl:template>

  <!--
    RoundingMode.HALF_UP: nearest neighbour, ties away from zero
    ("kaufmaennisches Runden", UStAE 14.5 Abs. 20): 5.5 -> 6, 2.5 -> 3,
    -2.5 -> -3, 1.4 -> 1. Note: round() is NOT the same - it rounds ties
    towards positive infinity (round(-2.5) = -2) and works on doubles.
  -->
  <xsl:template name="rm:half-up">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="$value"/>
      <xsl:with-param name="scale" select="$scale"/>
      <xsl:with-param name="mode" select="'HALF_UP'"/>
    </xsl:call-template>
  </xsl:template>

  <!-- RoundingMode.HALF_DOWN: nearest neighbour, ties towards zero (5.5 -> 5, 2.5 -> 2, -2.5 -> -2, 1.6 -> 2) -->
  <xsl:template name="rm:half-down">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="$value"/>
      <xsl:with-param name="scale" select="$scale"/>
      <xsl:with-param name="mode" select="'HALF_DOWN'"/>
    </xsl:call-template>
  </xsl:template>

  <!--
    RoundingMode.HALF_EVEN: nearest neighbour, ties to the even neighbour
    ("banker's rounding"): 5.5 -> 6, 2.5 -> 2, -2.5 -> -2, 1.5 -> 2
  -->
  <xsl:template name="rm:half-even">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="$value"/>
      <xsl:with-param name="scale" select="$scale"/>
      <xsl:with-param name="mode" select="'HALF_EVEN'"/>
    </xsl:call-template>
  </xsl:template>

  <!--
    RoundingMode.UNNECESSARY: the value must already have at most $scale
    fraction digits; otherwise the transformation stops with
    rm:ROUNDING_NECESSARY (Java: ArithmeticException "Rounding necessary")
  -->
  <xsl:template name="rm:unnecessary">
    <xsl:param name="value"/>
    <xsl:param name="scale" select="0"/>
    <xsl:call-template name="rm:round">
      <xsl:with-param name="value" select="$value"/>
      <xsl:with-param name="scale" select="$scale"/>
      <xsl:with-param name="mode" select="'UNNECESSARY'"/>
    </xsl:call-template>
  </xsl:template>

</xsl:stylesheet>

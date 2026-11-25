# JDK bug reports: XML 1.1 scanner of the built-in JAXP parser

Two independent defects of the XML 1.1 scanner of the JDK's internal Xerces
(`com.sun.org.apache.xerces.internal.impl.XML11EntityScanner`). File them separately at
<https://bugreport.java.com/bugreport/> (component: `xml`, subcomponent: `jaxp`).

Both reproduce on Eclipse Adoptium 17.0.21.1, 21.0.13.1, 25.0.5.1 and 27.0.1.1 (macOS, arm64).
XML 1.0 documents are not affected. Apache Xerces-J 2.9.1 parses all cases correctly.

---

## Report 1: XML 1.1 parser silently drops or duplicates text characters when a `]` ends the scanner's buffer

### Synopsis

SAX/DOM parsing of an XML 1.1 document returns wrong character data when a `]` in element
content is the last character of the entity scanner's buffer: characters are repeated or the `]` is
lost. No error is reported.

### Description

When the document declares `version="1.1"`, text content containing `]` is corrupted if the `]`
falls on the last position of the scanner's character buffer before it is refilled: offset 63
(end of the initial 64 character buffer) and then every 8192 characters. Examples:

| Input text (offset of first char 59) | Reported characters |
|---|---|
| `a]a]]&gt;` | `a]a]]a>` (an extra `a`) |
| `]b` with the `]` at offset 8255 | `b` (the `]` is lost) |

The same documents with `version="1.0"` are read correctly. The result depends only on the
position of the `]` in the input, so the corruption appears at random in real documents. The data is
corrupted silently, with no error or warning.

It also happens with a byte stream (`InputSource` with an `InputStream`) and with the StAX
parser (`XMLInputFactory.createXMLStreamReader`).

### Steps to reproduce

Save as `Xml11BracketRepro.java` and run `java Xml11BracketRepro.java`.

```java
import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

public class Xml11BracketRepro
{
  static String textOf (final String xml) throws Exception
  {
    final StringBuilder sb = new StringBuilder ();
    SAXParserFactory.newInstance ().newSAXParser ().parse (new InputSource (new StringReader (xml)), new DefaultHandler ()
    {
      @Override
      public void characters (final char [] ch, final int start, final int length)
      {
        sb.append (ch, start, length);
      }
    });
    return sb.toString ();
  }

  public static void main (final String [] args) throws Exception
  {
    System.out.println (System.getProperty ("java.vendor") + " " + System.getProperty ("java.version"));
    final String pad = "x".repeat (35);
    for (final String version : new String [] { "1.0", "1.1" })
    {
      // The ']' characters fall on offsets 60, 62 and 63, at the end of the first 64 character buffer
      final String xml = "<?xml version=\"" + version + "\"?><a>" + pad + "a]a]]&gt;</a>";
      final String expected = pad + "a]a]]>";
      final String actual = textOf (xml);
      System.out.println ("XML " + version + ": " + (expected.equals (actual) ? "OK" : "WRONG") + "  expected '" +
                          expected.substring (35) + "', got '" + actual.substring (35) + "'");
    }
    // A single ']' at the end of the second buffer (offset 64 + 8192 - 1 = 8255)
    final String pad2 = "x".repeat (8255 - 24);
    final String actual2 = textOf ("<?xml version=\"1.1\"?><a>" + pad2 + "]b</a>");
    System.out.println ("XML 1.1, ']' at offset 8255: " + ((pad2 + "]b").equals (actual2) ? "OK" : "WRONG") + "  got '..." +
                        actual2.substring (actual2.length () - 3) + "'");
  }
}
```

### Expected result

```
XML 1.0: OK  expected 'a]a]]>', got 'a]a]]>'
XML 1.1: OK  expected 'a]a]]>', got 'a]a]]>'
XML 1.1, ']' at offset 8255: OK  got '...x]b'
```

### Actual result (identical on JDK 17, 21, 25 and 27)

```
XML 1.0: OK  expected 'a]a]]>', got 'a]a]]>'
XML 1.1: WRONG  expected 'a]a]]>', got 'a]a]]a>'
XML 1.1, ']' at offset 8255: WRONG  got '...xxb'
```

With Apache Xerces-J 2.9.1 on the class path
(`-Djavax.xml.parsers.SAXParserFactory=org.apache.xerces.jaxp.SAXParserFactoryImpl`) all three
lines are `OK`.

### Workaround

Feed the parser through a `Reader` that never returns a chunk ending in `]` unless the input ends
there: hold the trailing `]` characters back and return them at the start of the next `read`. This
prevents the corruption at every offset tested (0 to 25,000). It does not help for byte stream input,
which the parser decodes itself.

### Notes

The `]` handling of element content goes through `XMLDocumentFragmentScannerImpl.scanContent`,
which calls `scanChar` and `skipChar(']')` on the entity scanner. A difference between
`XML11EntityScanner.skipChar` and `XMLEntityScanner.skipChar` was noticed while reading the
sources: the 1.1 version declares a local `int offset` where the 1.0 version assigns the `offset`
field. This has not been verified as the cause.

---

## Report 2: XML 1.1 parser rejects a CDATA section whose content ends with `]`

### Synopsis

A well-formed XML 1.1 document with a CDATA section whose content ends with `]` (written as `]]]>`)
fails with "XML document structures must start and end within the same entity". The same document
as XML 1.0 is parsed correctly.

### Description

`<![CDATA[c]]]>` is a CDATA section with the content `c]` (XML 1.1 section 2.7: the section ends at
the first `]]>`, and the `]` before it is content). With `version="1.1"` the JDK parser raises a fatal
error instead. The failure does not depend on the position in the document.

### Steps to reproduce

Save as `Xml11CdataRepro.java` and run `java Xml11CdataRepro.java`.

```java
import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

public class Xml11CdataRepro
{
  public static void main (final String [] args) throws Exception
  {
    final SAXParserFactory f = SAXParserFactory.newInstance ();
    System.out.println (System.getProperty ("java.version") + " " + f.getClass ().getName ());
    for (final String version : new String [] { "1.0", "1.1" })
    {
      // The CDATA section contains "c]": the "]" is followed directly by the "]]>" delimiter
      final String xml = "<?xml version=\"" + version + "\"?><a><![CDATA[c]]]></a>";
      final StringBuilder sb = new StringBuilder ();
      try
      {
        f.newSAXParser ().parse (new InputSource (new StringReader (xml)), new DefaultHandler ()
        {
          @Override
          public void characters (final char [] ch, final int start, final int length)
          {
            sb.append (ch, start, length);
          }
        });
        System.out.println ("XML " + version + ": '" + sb + "'" + ("c]".equals (sb.toString ()) ? " OK" : " WRONG"));
      }
      catch (final Exception ex)
      {
        System.out.println ("XML " + version + ": " + ex);
      }
    }
  }
}
```

### Expected result

```
XML 1.0: 'c]' OK
XML 1.1: 'c]' OK
```

### Actual result (identical on JDK 17, 21 and 27)

```
XML 1.0: 'c]' OK
XML 1.1: org.xml.sax.SAXParseException; lineNumber: 1; columnNumber: 43; XML document structures must start and end within the same entity.
```

Stack trace (JDK 27.0.1.1, Xerces frames):

```
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLScanner.reportFatalError(XMLScanner.java:1465)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.endEntity(XMLDocumentFragmentScannerImpl.java:952)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl.endEntity(XMLDocumentScannerImpl.java:592)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLEntityManager.endEntity(XMLEntityManager.java:1560)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLEntityScanner.load(XMLEntityScanner.java:1720)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XML11EntityScanner.scanData(XML11EntityScanner.java:952)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanCDATASection(XMLDocumentFragmentScannerImpl.java:1638)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl$FragmentContentDriver.next(XMLDocumentFragmentScannerImpl.java:2958)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl.next(XMLDocumentScannerImpl.java:635)
	at java.xml/com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanDocument(XMLDocumentFragmentScannerImpl.java:551)
```

With Apache Xerces-J 2.9.1 the result is `XML 1.1: 'c]' OK`.

### Workaround

None on the parser side. A document can avoid the construct by writing the final `]` outside the CDATA
section (`<![CDATA[c]]>]`).

# Basic example: the command line client

A small invoice with stylesheets, XPath expressions and an XML Schema, to try every command of the phoxtrot command
line client (`phoxtrot-cli`). Only the CLI JAR is needed: build it with `mvn install` in the project root, or set
`CLI_JAR`. The scripts print each command line and its exit code; several commands fail on purpose (marked in the
scripts and below).

| File | Content |
|------|---------|
| `invoice.xml` | The sample invoice (namespace `urn:example:invoice`): three lines, the total matches |
| `invoice-invalid.xml` | An invoice without buyer, with a quantity of 0 and a wrong total |
| `invoice.xsd` | The XML Schema of the invoice; an `xs:assert` checks the total, so it needs XSD 1.1 |
| `invoice-to-html.xsl` | Renders the invoice as HTML; includes `functions.xsl`, has a parameter `title`, an `xsl:message` and an `xsl:assert` on the total |
| `functions.xsl` | The stylesheet functions `f:amount` and `f:format` |
| `broken.xsl` | A static error: an undeclared variable (XPST0008) |
| `streamable.xsl` | A streamable mode that is guaranteed streamable |
| `not-streamable.xsl` | A streamable mode that is not guaranteed streamable: it reads the lines twice (XTSE3430) |
| `split.xsl` | Writes one document per line with `xsl:result-document` |
| `total-check.xpath` | The total check as XPath expression; with `treat as` so that it passes static typing |
| `cardinality.xpath` | `upper-case` of all lines: compiles, but fails static typing (XPTY0004) and fails when evaluated |
| `common.sh` | The settings shared by the scripts |

| Script | Shows |
|--------|-------|
| `1-xslt-check.sh` | `xslt check`: the static error in `broken.xsl` with line and column; `--streamable` accepts `streamable.xsl` and rejects `not-streamable.xsl` |
| `2-xslt-run.sh` | `xslt run`: HTML with a parameter, the failing assertion for `invoice-invalid.xml`, `--trace`, `--streamed`, and `split.xsl` rejected without and run with `--result-documents`. Results go to `target/` (not under version control) |
| `3-xpath.sh` | `xpath check` with a syntax error, `--static-typing`, `xpath streamability` in streamed and burst mode, `xpath eval` against both invoices |
| `4-xsd.sh` | `xsd check` with XSD 1.0 (fails: `xs:assert`) and 1.1, `xsd validate` of both invoices |

With Docker, run the commands of the scripts in this folder, e.g.

```
docker run --rm -v "$PWD:/data" phelger/phoxtrot xslt check --streamable streamable.xsl not-streamable.xsl
```

Ideas to play with: declare `$currency` in `broken.xsl` as `xsl:param` and set it with `-p currency=EUR`; make
`not-streamable.xsl` streamable (hint: `xsl:fork`, or compute the sum and the count in one `xsl:iterate`); remove the
`treat as` from `total-check.xpath` and see what static typing says.

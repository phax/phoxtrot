# QT3 (`qt3tests`, XPath 3.1 / F&O 3.1): failing cases

All 22,401 applicable cases pass. Measured offline, on JDK 21 and 25, with the native validator of
`phoxtrot-xsd` as the schema processor (the default of the harness, see below).

The cases that need the feature `fn-load-xquery-module` (the whole test set `fn-load-xquery-module` and
`fn-function-lookup-760`, 84 cases, 71 of them not already excluded by their spec) are skipped
(`feature-not-claimed:fn-load-xquery-module`): loading XQuery library modules needs an XQuery processor, and
`fn:load-xquery-module` raises FOQM0006 as F&O 3.1 specifies for that case. The cases that test the absence of the
feature (`satisfied="false"`) run. go-xslt counts the 34 of them that fail as accepted failures instead.

`fn:transform` is a real XSLT 3.0 processor (phoxtrot-xslt is on the class path of the harness), so the features
`fn-transform-XSLT` and `fn-transform-XSLT30` are claimed: the 4 cases that need their absence (`satisfied="false"`:
`fn-transform-901`, `fn-transform-902`, `fn-transform-err-14`, `fn-function-lookup-766b`) are skipped
(`feature-claimed:<feature>`). Until 2026-11-17 they ran; the three `fn-transform` cases were accepted failures, as in
go-xslt.

The harness runs offline, so the feature `remote_http` is not claimed: its 2 cases (`fn-unparsed-text-054`,
`fn-unparsed-text-054a`) are skipped (`feature-not-claimed:remote_http`). Until 2026-11-17 they ran: `054` passed
through its alternative result FOUT1170, `054a` (reading `https://timeanddate.com`, which by then answered every client
with 403) was an accepted failure, as in go-xslt. The stability of `fn:unparsed-text` they test is covered by
`FnResourceTest.testUnparsedTextStable`.

Every failing case is named here in backticks under a heading with its status and reason. `QT3IT` fails on any
failing case that is not named here, so a regression is loud and a newly accepted failure needs a written reason.
Remove an entry when its case passes. Status **Open** means winnable later; **Accepted** means a decision not to.

### Schema-validated sources with the JDK validator
`K2-DataFunc-6`, `fn-normalize-space-24`, `fn-string-length-23`, `json-to-xml-016`, `json-to-xml-017`,
`json-to-xml-037`, `json-to-xml-038`

**Status:** Accepted (only in a run with `-Dqt3.schemaProcessor=jdk`; they pass in the default run).
The harness validates sources with the native validator, on its test class path, and makes the schemas of the
environment the in-scope schema definitions of the test (`QT3SchemaCache`); the schema of the XML representation of
JSON (environment `json-ns`, which names only the namespace) is taken from the suite's copy. With
`-Dqt3.schemaProcessor=jdk` the JDK validator is used instead, as before: it gives type annotations, nilled, schema
IDs and typed values, but neither content types (element-only content atomizes to its string value instead of
FOTY0012: `K2-DataFunc-6`, `fn-normalize-space-24`, `fn-string-length-23`) nor element declarations (no
`schema-element(j:...)` and no `json-to-xml` with `validate=true`: `json-to-xml-016`, `json-to-xml-017`,
`json-to-xml-037`, `json-to-xml-038`). Both runs are otherwise identical by name.


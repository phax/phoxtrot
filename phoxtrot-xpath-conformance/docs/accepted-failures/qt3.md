# QT3 (`qt3tests`, XPath 3.1 / F&O 3.1): failing cases

22,382 of 22,407 applicable cases pass, 25 fail. Measured offline, on JDK 21 and 25, with the native validator of
`phoxtrot-xsd` as the schema processor (the default of the harness, see below).

The cases that need the feature `fn-load-xquery-module` (the whole test set `fn-load-xquery-module` and
`fn-function-lookup-760`, 84 cases, 71 of them not already excluded by their spec) are skipped
(`feature-not-claimed:fn-load-xquery-module`): loading XQuery library modules needs an XQuery processor, and
`fn:load-xquery-module` raises FOQM0006 as F&O 3.1 specifies for that case. The cases that test the absence of the
feature (`satisfied="false"`) run. go-xslt counts the 34 of them that fail as accepted failures instead.

Every failing case is named here in backticks under a heading with its status and reason. `QT3IT` fails on any
failing case that is not named here, so a regression is loud and a newly accepted failure needs a written reason.
Remove an entry when its case passes. Status **Open** means winnable later; **Accepted** means a decision not to.

### fn:transform unavailable
`fn-transform-901`, `fn-transform-902`, `fn-transform-err-14`

**Status:** Accepted (decision).
They depend on the feature `fn-transform-XSLT` (901, 902) or `fn-transform-XSLT30` (err-14) being *not* satisfied and
expect `fn:transform` to report that no (XSLT 3.0) processor is available. With phoxtrot-xslt on the class path
`fn:transform` is a real XSLT 3.0 processor, which necessarily contradicts them. go-xslt accepts the same cases.

### XPath static typing
`fn-filter-007`, `fn-filter-016`, `fn-filter-019`, `fn-filter-022`, `fn-for-each-pair-032`,
`fn-function-arity-010`, `fn-function-arity-012`, `fn-function-lookup-713`, `fn-function-name-010`,
`fn-has-children-012`, `fn-has-children-013`, `fn-innermost-008`, `fn-innermost-010`, `fn-outermost-008`,
`fn-outermost-010`, `fn-unparsed-text-009`, `fn-unparsed-text-available-007`, `fn-unparsed-text-available-009`,
`fn-unparsed-text-available-011`, `fn-unparsed-text-lines-009`

**Status:** Accepted (decision).
They depend on the optional Static Typing Feature and expect XPTY0004 from static type inference over `if/then/else`
branch unions or inline function bodies. A processor that checks types at run time correctly raises nothing. Only the
narrow static axis-step analysis (`ST-Axes*`, XPST0005) is implemented, behind `StaticContext.setStaticTyping`.
go-xslt accepts the same 20 cases.

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

### External entity in fn:parse-xml
`parse-xml-010`

**Status:** Accepted (decision).
`fn:parse-xml` does not load external entities (`XdmParser` keeps them off by default). Loading them is
implementation-defined and a security boundary. go-xslt accepts the same case.

### Network access
`fn-unparsed-text-054a`

**Status:** Accepted.
It reads `https://timeanddate.com`. The harness runs offline, and that server now answers every client with 403, so
the case fails even with network access. go-xslt accepts the same case.

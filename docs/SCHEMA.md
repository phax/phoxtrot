# Schema processing

phoxtrot has a schema provider interface (`com.helger.phoxtrot.xdm.schema`, entry point `XdmSchemaSupport`) with two
implementations:

| Provider | Package | Needs | Gives |
|----------|---------|-------|-------|
| JDK validator | `com.helger.phoxtrot.xdm.jaxp` (`JdkSchemaProvider`) | nothing beyond the JDK | validation and type annotations only |
| Native validator | `com.helger.phoxtrot.xsd` (`XsdSchemaProvider`, module `phoxtrot-xsd`) | nothing beyond phoxtrot | validation plus the full component model; XSD 1.0 and 1.1, schemas are compiled as 1.1, see [PLAN.md](PLAN.md) "Native XSD validator" |

`XdmSchemaSupport` loads the native provider by name if `phoxtrot-xsd` is on the class path; otherwise schema
processing is unavailable. The JDK validator is never chosen automatically, only used explicitly. Apache Xerces-J
2.12.2 was a third provider up to 0.2.0 (see "Why Xerces-J was removed" below).

## What only the native validator gives

### What the JDK validator cannot tell

The JDK ships its own copy of Xerces inside the `java.xml` module (`com.sun.org.apache.xerces.internal.*`). That copy
builds the full schema component model internally, but the module does not export those packages, so phoxtrot cannot
reach it without `--add-exports`. What the public JAXP validation API (`javax.xml.validation`) exposes is only the
`org.w3c.dom.TypeInfo` of each validated element and attribute: the type name, its namespace, and a derivation check
against a named type (`isDerivedFrom`).

From that, `JdkTypeDefinition` can derive the type name, simple vs. complex, list types and the built-in atomic base,
which is enough for type annotations, typed values of simple types, `fn:nilled` and schema-based IDs. Everything that
needs the schema *components* is missing (verified in `JdkSchemaSet` and `JdkTypeDefinition`):

| Needed for | Component information | JDK validator |
|------------|-----------------------|---------------|
| FOTY0012 when atomizing element-only content; typed value of complex types with simple content | content type of a complex type | not available (`getContentType` throws) |
| `schema-element()`, `schema-attribute()`, `xsl:import-schema`, `fn:json-to-xml` with `validate=true` | element and attribute declarations by name, substitution groups | lookups return `null` |
| user-defined atomic, list and union types in XPath (casts, constructor functions, `instance of`), `[xsl:]type` | type definitions by name, base type, validating a single value against a type | lookups return `null`, `getBaseType` and `validate` throw |
| `[xsl:]validation` on constructed nodes | validating an element in place against a declaration or type | not available |

`IXdmTypeDefinition.hasComponentModel ()` is `false` for JDK types and `XdmSchemaType.getDefinition ()` returns `null`
for them; callers such as the atomizer then fall back to what the type annotation alone allows.

### Features that need the native validator

* **Schema-aware XSLT** (`XsltCompileOptions.setSchemaAware (true)`): `xsl:import-schema`, `[xsl:]validation`,
  `[xsl:]type`, typed modes, `schema-element()`. Claiming it without a schema processor throws an `IllegalStateException` when
  the stylesheet is compiled (`StylesheetCompiler`). Without the claim the processor is a basic XSLT 3.0 processor, as
  XSLT 3.0 section 26 allows.
* **User-defined types in XPath** and the element/attribute declaration tests listed above.

### Tests that need a schema processor

`phoxtrot-xsd` is in test scope of `phoxtrot-xslt`, `phoxtrot-xpath-conformance` and `phoxtrot-xslt-conformance`, and a
compile dependency of the (not deployed) `phoxtrot-xsd-conformance`:

* **QT3**: 7 cases pass only with a full schema processor (`K2-DataFunc-6`, `fn-normalize-space-24`, `fn-string-length-23` need the
  content type for FOTY0012; `json-to-xml-016/017/037/038` need element declarations for `schema-element(j:...)` and
  `validate=true`). `-Dqt3.schemaProcessor=jdk` runs QT3 on the JDK validator: 66 fails instead of 59, otherwise
  identical by name. See `phoxtrot-xpath-conformance/docs/accepted-failures/qt3.md`.
* **XSLT30 schema-aware run** (`-Dxslt30.schemaAware=true`): all 9,310 applicable cases run; 12 fail
  (`phoxtrot-xslt-conformance/docs/accepted-failures/xslt30-schema-aware.md`). The default run (8,647 cases) does not
  need it. `XSLT30SchemaUnawareCasesIT` checks the 31 cases that require a processor *without* schema-awareness in both
  claim states.
* **xsdtests** (`phoxtrot-xsd-conformance`): `XsdNativeIT` runs the native validator for XSD 1.0 and 1.1 and gates on
  `docs/accepted-failures/xsd-native.md` (go-xslt's five 1.0 and four 1.1 fails); `XsdBridgeDifferentialIT` checks
  that validating a tree in place gives the verdict of validating its text, for every XSD 1.0 and 1.1 instance.
  `-Dxsd.validator=xerces` (the default of `XsdIT`) is the Xerces-J 2.12.2 baseline, a differential reference only
  (`docs/accepted-failures/xsd.md`); Xerces-J is a dependency of this not deployed module only.
* **`NoSchemaProcessorTest`**: a second surefire execution of `phoxtrot-xslt` without `phoxtrot-xsd` on the class path
  checks that the basic processor and the JDK validator work without it.

Xerces-J is otherwise only in test scope of `phoxtrot-xdm`, for the DOM bridge tests: the JDK DOM leaves entity
reference nodes empty.

## Why Xerces-J was removed

Xerces-J was the quickest way to get schema-awareness. The decision of 2026-10-26 made the native validator, ported
from go-xslt, the default provider and kept Xerces-J as an optional fallback; on 2026-11-16 the fallback was removed
(the package `com.helger.phoxtrot.xdm.xerces`, the optional dependency and the system property
`xdm.schemaProvider`). It is not a good base:

* **Maintenance.** 2.12.2 is the latest release on Maven Central (metadata last updated 2022-01-27; the jar was built
  on 2022-01-21).
* **XSD 1.0 only.** The published artifact does not read XSD 1.1 schemas: the schema-aware XSLT30 cases with XSD 1.1
  schemas (`accumulator-073`, `catalog-009`, `merge-051`, `strip-space-009`, `validation-1301`) fail at the schema, and
  the whole XSD 1.1 part of xsdtests (41,553 cases) is unsupported. The native validator, like go-xslt's, passes 99.99%
  of both versions.
* **Conformance.** On xsdtests XSD 1.0, Xerces-J fails 256 of 39,401 cases, go-xslt's validator 5 (same
  denominator). Without the 92 offline cases, the rest are mainly Xerces-J deviations: regular expressions (26),
  pre-Fifth-Edition XML name characters (16), `minOccurs`/`maxOccurs` of 2^31 and above (15), `vc:minVersion` (12),
  the `--MM--` gMonth form (26), identity constraints, particle restriction and others. Details:
  `phoxtrot-xsd-conformance/docs/accepted-failures/xsd.md`.
* **It changes JAXP for the whole application.** The jar registers itself in `META-INF/services` for
  `SAXParserFactory`, `DocumentBuilderFactory`, `SchemaFactory`, `DatatypeFactory`, `XMLEventFactory`,
  `DOMImplementationSourceList` and `org.xml.sax.driver`. Its mere presence changes what every `newInstance ()` call
  in the application returns, not only in phoxtrot. phoxtrot itself switched its catalogue readers and several tests
  to `newDefaultInstance ()` so that they stay independent of it.
* **The JAXP API hides part of the model.** Through JAXP a compiled schema is "fully composed": instance
  `xsi:schemaLocation` hints are ignored (6 xsdtests cases), and a second import of an already known namespace is not
  followed (2 cases, or 2 others with `honour-all-schemaLocations`).
* **Resource limits.** Without secure processing, Xerces-J expands large occurrence ranges in content models until
  the heap is exhausted (`particlesZ036_*`); with it, content models are capped at 3,000 nodes, which makes 6 valid
  test cases fail. go-xslt uses a position-set matcher and a linear counting matcher that need neither.
* **API defects.** The DOM `TypeInfo.isDerivedFrom (null, ...)` of a Xerces-J complex type in no namespace returns
  `true` for an unrelated type and even for a type name that does not exist, while the XS API
  (`XSTypeDefinition.derivedFrom`) answers `false` for both (reproduced with 2.12.2 on 2026-10-26); the provider had
  to check derivation through the XS API only.
* **Integration cost.** Validating an `IXdmNode` tree meant replaying it as SAX events into a copy and transferring the
  annotations back, so every validated document was built twice. A validator working on `IXdmNode` directly (as go-xslt's
  `ValidateNode`) validates in place.

# phoxtrot

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger.phoxtrot/phoxtrot-parent-pom/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger.phoxtrot/phoxtrot-parent-pom/)
[![javadoc](https://javadoc.io/badge2/com.helger.phoxtrot/phoxtrot-xdm/javadoc.svg)](https://javadoc.io/doc/com.helger.phoxtrot/phoxtrot-xdm)

> If this project saved you some time or made your day a little easier, a star would mean a lot — it helps others find it too.
<!-- ph-badge-end -->

> **Experimental.** This project is at an early stage. APIs, module layout and behaviour may change without notice,
> also between releases. Do not use it in production yet.

A pure Java implementation of XPath 3.1 and XSLT 3.0, intended as an Apache 2.0 licensed alternative to Saxon.
XML parsing is left to the JDK; everything on top of it, including an XML Schema 1.0 validator, is implemented here.

Status: initially released.
* The native XML Schema validator passes 99.99% of the applicable W3C xsdtests cases for XSD 1.0 (39,396 of 39,401) and XSD 1.1 (41,596 of 41,600);
  failing cases and the reasons: [xsd-native.md](phoxtrot-xsd-conformance/docs/accepted-failures/xsd-native.md).
* The XPath 3.1 engine runs and passes **all** 22,401 applicable W3C QT3 test cases;
  skipped cases and the reasons: [qt3.md](phoxtrot-xpath-conformance/docs/accepted-failures/qt3.md).
* The XSLT 3.0 engine passes 99.9% of the applicable W3C xslt30-test cases as a basic processor (8,640 of 8,647);
  failing cases and the reasons: [xslt30.md](phoxtrot-xslt-conformance/docs/accepted-failures/xslt30.md).

Schema-awareness and streaming are available as opt-ins (streaming: the streamability analysis, streamed execution of guaranteed-streamable constructs and output written while it is built).
XSLT 1.0 stylesheets run with backwards compatible behaviour (claimed by default; 8,752 of 8,762 cases with the claim), with the EXSLT - Common functions `exsl:node-set` and `exsl:object-type`.
A JAXP `TransformerFactory`, a W3C DOM bridge and a command line client (also as Docker image) are included.
See [docs/PLAN.md](docs/PLAN.md) for the roadmap.

## Modules

| Module | Content | Status |
|--------|---------|--------|
| `phoxtrot-xdm` | XPath data model: node tree built from SAX, incremental StAX reader for streamed documents, names, document order, W3C DOM and SAX bridge | usable |
| `phoxtrot-xpath` | XPath 3.1: values, parser, evaluator, F&O 3.1 function library, regex, collations, serialisation | usable |
| `phoxtrot-xpath-conformance` | W3C QT3 test harness (not deployed) | usable |
| `phoxtrot-xslt` | XSLT 3.0: stylesheet compiler, modules, templates and modes, all instruction families, XSLT functions, packages, schema-awareness, streaming (analysis, streamed execution, output written while it is built), JAXP `TransformerFactory` | usable |
| `phoxtrot-xslt-conformance` | W3C xslt30-test harness (not deployed) | usable |
| `phoxtrot-xsd` | Native XML Schema 1.0 and 1.1 validator (port of go-xslt's `internal/xsd`): loader, include/import/redefine/override, schema-for-schemas checks, simple types and facets, content models with open content, instance validation with identity constraints, assertions and conditional type assignment, read-only component view, validation API that reports all errors; the default schema provider for XPath and XSLT (schemas compiled as XSD 1.1) | usable |
| `phoxtrot-cli` | Command line client: check and run stylesheets, check, analyse and evaluate XPath expressions, check XML Schemas and validate documents (executable JAR, not part of the BOM; Dockerfile) | usable |
| `phoxtrot-xsd-conformance` | W3C xsdtests (XML Schema) harness with a pluggable validator (not deployed) | usable |
| `phoxtrot-xslt-benchmark` | JMH benchmarks and profiling workloads for parsing, XPath, XSLT, XSD, streaming and compiling (standalone, not in the reactor, not deployed; see its `README.md` and `docs/benchmarks`) | usable |
| `phoxtrot-xdm-memory-benchmark` | Retained heap of the node tree compared with ph-xml MicroDOM and Saxon TinyTree (standalone, not deployed) | usable |

Requires Java 21. CI runs on Java 21 and 25.

## Maven usage

The deployed modules are `phoxtrot-xdm`, `phoxtrot-xpath`, `phoxtrot-xslt` and `phoxtrot-xsd`. For XSLT 3.0 add
`phoxtrot-xslt` (it brings `phoxtrot-xpath` and `phoxtrot-xdm`); for XPath 3.1 alone `phoxtrot-xpath`:

```xml
<dependency>
  <groupId>com.helger.phoxtrot</groupId>
  <artifactId>phoxtrot-xslt</artifactId>
  <version>0.3.0</version>
</dependency>
```

Add `phoxtrot-xsd` as well for schema-awareness (see below).

## Schema-awareness (optional dependency)

Schema-awareness (`XsltCompileOptions.setSchemaAware (true)`: `xsl:import-schema`, `[xsl:]validation`/`[xsl:]type`,
typed sources, `schema-element()` and user-defined types in XPath) needs the native validator (module `phoxtrot-xsd`)
on the class path. Without it the engine is a basic XSLT 3.0 processor. It needs no further dependencies; imported
schemas are compiled as XSD 1.1, so assertions, conditional type assignment, open content and `xs:override` work.

XML parsing stays on the JDK parser. The validator reads the documents an imported schema references
(`xs:include`, `xs:import`, `xs:redefine`, `xs:override`) only from local files, never from the network, and only while
`XsltCompileOptions.setReadLocalSchemaFiles` allows it (default `true`; the JAXP adapter derives it from
`ACCESS_EXTERNAL_STYLESHEET`). A `schema-location` itself is read through the resolver of the compile options if one
is set. `phoxtrot-xsd` can also be used on its own to validate documents (`XsdSchema.validate`, which reports all
errors), against XSD 1.0 or 1.1; the `xsi:schemaLocation` hints of an instance are only followed with
`XsdValidationOptions.setFollowSchemaLocationHints (true)`, because the instance then decides which local files are
read.

```xml
<dependency>
  <groupId>com.helger.phoxtrot</groupId>
  <artifactId>phoxtrot-xsd</artifactId>
  <version>0.3.0</version>
</dependency>
```

[docs/SCHEMA.md](docs/SCHEMA.md) explains how the validator is loaded and what the JDK validator cannot provide.

## XPath API

Plain XPath 3.1 without XSLT (e.g. a Schematron binding that evaluates rule by rule) uses `phoxtrot-xpath` directly:

```java
StaticContext aSC = new StaticContext ();
aSC.declareNamespace ("cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");
aSC.declareVariable (new XdmQName ("limit"));
SeqType aDouble = SeqType.of (ItemType.atomic (EAtomicType.DOUBLE), EOccurrence.ONE);
aSC.setFunctionLibrary (FunctionLibrary.getDefault ().createChild ()
                                       .register (new XdmQName ("urn:ext", "twice"), List.of (aDouble), aDouble,
                                                  (aCtx, aArgs) -> ...));
CompiledXPath aXPath = XPathCompiler.compile ("count(//cbc:ID) le $limit", aSC);  // immutable, thread-safe

DynamicContext aCtx = aXPath.createDynamicContext ().withFocus (aDocNode, 1, 1)
                            .withVariable (new XdmQName ("limit"), XdmSequence.of (XdmAtomic.integer (10)));
aCtx.setMaxSteps (1_000_000);                      // optional, XPDY0130 when exceeded
Boolean aValid = LargeStackRunner.run (() -> Boolean.valueOf (aXPath.evaluateEffectiveBooleanValue (aCtx)),
                                       "my-xpath", EThreading.THREAD_POOL);
```

* Source compatible within 0.x (changes only with a note in "News and noteworthy"): in `com.helger.phoxtrot.xpath`
  `XPathCompiler`, `CompiledXPath`, `XPathException`, `EThreading`, `LargeStackRunner`,
  `XPathStreamabilityAnalyzer`, `XPathStreamabilityResult`, `XPathStreamabilityReason`, `XPathStreamabilityBinding`,
  `EStreamabilityVerdict`, `EStreamabilityConstruct` and `EStreamContextMode`; `StaticContext`, `DynamicContext`, `IResourceResolver`, `ResourceUri`, `VariableFrame`, `VariableFrameLayout` and `VariableSlot` (`context`);
  `FunctionLibrary` and `IXPathFunction` (`fn`); `SeqType`, `ItemType` and `EOccurrence` (`ast`); `EPosture`, `ESweep`,
  `PostureSweep` and `EStreamFunctionCategory` (`stream`); the value types of `value`, `XPathOperators` (`eval`) and the node model
  of `phoxtrot-xdm`. Everything else in `ast` (expression nodes), `eval`, `parser`, `ops`, `pattern` and `stream` is
  internal.
* Extension functions: `FunctionLibrary.register` with the signature as text (`"(xs:string?) as xs:integer"`) or as
  `SeqType`s; the arguments are converted by the XPath 3.1 function conversion rules before the call (see
  `IXPathFunction`). A compiled stylesheet's library is frozen; register into a `createChild ()`.
* `CompiledXPath.getFreeVariables ()`: the variables of the static context an expression uses, to bind only these.
* Threads: a `CompiledXPath`, a compiled `XsltExecutable` and a compiled `XsdSchema` may be used by several threads at
  once if they are handed over safely (a `final` or `volatile` field, a concurrent collection, an executor); the
  `StaticContext` of a compiled expression must not change afterwards, and a `DynamicContext` (with the function items
  an evaluation returns) is for one thread at a time. A node tree that several threads read must be numbered first:
  parsed trees are, a tree built with the `IXdmNode.create*` factories or `appendChild` needs `assignDocumentOrder ()`
  on its root, because comparing nodes of an unnumbered tree numbers it without synchronisation.
* Limits: `DynamicContext.setMaxSteps` (loop iterations and function calls, unlimited by default), the function call
  depth (10,000) and `LargeStackRunner` for a thread with a large stack.
* Errors: `XPathException` with code, description and error object; a static error has its position in the
  expression (`getExpressionOffset`, `getExpressionLine`, `getExpressionColumn`). An error raised in an
  `xsl:function` is an `XsltException` naming the function (`getFunctionFrame`).
* Streamability: `XPathStreamabilityAnalyzer` tells whether an expression (`analyze`) or a match pattern
  (`analyzePattern`) can be evaluated while streaming, with the analysis of XSLT 3.0 section 19: posture and sweep, a
  verdict (motionless, consuming, free-ranging) and for a verdict other than motionless the construct that causes it
  (an axis step, a function, a variable, without position in the text). Options: the context node streamed, buffered
  with its whole tree, or in burst mode (`EStreamContextMode.BURST`: only its subtree is buffered, verdict `BURST` for
  an expression or pattern predicate that needs nothing outside it, e.g. one Schematron rule per element; the
  document element counts as an ancestor of a streamed context node, so `/x/@a` is motionless), variables bound to
  streamed nodes or (burst mode) to nodes outside the subtree or with a binding expression compiled once
  (`analyzeBinding`, `addBoundVariable`: analysed as a `let` around each expression), the streamability category of
  extension functions
  (undeclared ones are not streamable). `FunctionLibrary.createDefaultWithXsltSignatures ()` adds the signatures of
  the XSLT functions (`fn:key`, `fn:current`, `fn:document`, ...) for compiling expressions of an XSLT-based language
  without `phoxtrot-xslt`.
* Static typing (XPath 3.1 section 4.1, optional): `StaticContext.setStaticTyping (true)` infers the static type of the
  expression at compile time and raises type errors (XPTY0004, XPTY0019, XPTY0020) and XPST0005 where the inferred
  type is not accepted, before anything is evaluated. It is pessimistic by definition: declare the context item type
  (`setContextItemStaticType`) and the variable types (`declareVariable (name, SeqType)`), and use `treat as` where
  only you know more. Off by default; XSLT does not use it.
* Stylesheet functions without Java code: compile a module with the `xsl:function`s (and the keys and global variables
  they use) and compile the expressions against `XsltExecutable.createStaticContext ()`, evaluated with the dynamic
  context of `XsltExecutable.createEvaluation (request).createDynamicContext ()`.

## JAXP (`javax.xml.transform`)

`com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory` is a JAXP `TransformerFactory` for XSLT 3.0 (`Templates`
are thread-safe, a `Transformer` is for one thread at a time). It is deliberately not registered in
`META-INF/services`, which would replace the JDK transformer for every application on the class path; create it
directly or by name:

```java
TransformerFactory aTF = new PhoxtrotTransformerFactory ();
// or: TransformerFactory.newInstance ("com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory", aClassLoader)
aTF.setURIResolver (aURIResolver);      // xsl:include/import, document(), doc(), unparsed-text()
Templates aTemplates = aTF.newTemplates (new StreamSource (aXslFile));
Transformer aTransformer = aTemplates.newTransformer ();
aTransformer.setErrorListener (aErrorListener);  // xsl:message arrives as warning()
aTransformer.setParameter ("phase", "#ALL");
aTransformer.transform (new DOMSource (aDoc), new DOMResult ());
```

* Sources: `StreamSource`, `DOMSource`, `SAXSource` (with or without `XMLReader`) and `XdmSource` (a phoxtrot tree);
  results: `StreamResult` (writer, stream or `file:` system ID; written while it is built where the output
  definition allows it), `DOMResult` and `SAXResult`. `newTransformer ()` is the identity transformer.
* Parameters: `String` becomes `xs:untypedAtomic` (so `as="xs:integer"` accepts "42"), numbers, `Boolean`, `URI`,
  `QName`, DOM nodes, phoxtrot items and sequences, arrays and collections (as sequences). Names `{uri}local`.
* Output properties: the `OutputKeys` and the other XSLT 3.0 serialization attributes except `use-character-maps`
  override the unnamed `xsl:output` for a `StreamResult`; namespaced names are accepted without effect.
* Errors: compile errors are reported to `ErrorListener.fatalError` and thrown as `TransformerConfigurationException`,
  run errors as `TransformerException`; the message starts with the error code, the locator has module, line and
  column, the cause is the `XsltException`. `xsl:message` and failed `xsl:assert` go to `ErrorListener.warning` while
  the transformation runs, or to an `IXsltMessageListener` (`PhoxtrotTransformer.setMessageListener`, factory
  attribute `PhoxtrotTransformerFactory.ATTR_MESSAGE_LISTENER`).
* Tracing: an `IXsltTraceListener` (`PhoxtrotTransformer.setTraceListener`, factory attribute
  `PhoxtrotTransformerFactory.ATTR_TRACE_LISTENER`) follows the template rules, named templates, stylesheet functions
  and optionally instructions of each transformation; the same listener as `XsltTransformRequest.setTraceListener`.
* Current date-time: `PhoxtrotTransformer.setCurrentDateTime` fixes the value of `fn:current-dateTime()` (its offset
  also being the implicit timezone) for the transformations of a transformer, e.g. for reproducible output.
* Threads: compilations and transformations run on the thread the factory attribute
  `PhoxtrotTransformerFactory.ATTR_THREADING` chooses (an `EThreading` or its name; default `NEW_THREAD`, a new
  thread with a large stack per call; also `THREAD_POOL` and `CALLING_THREAD`), a transformer can override it with
  `PhoxtrotTransformer.setThreading`.
* The `URIResolver` is called as JAXP specifies: with the `href` as written and the base URI it is resolved against
  (the empty string if there is none, e.g. a stylesheet without system ID).
* `XMLConstants.FEATURE_SECURE_PROCESSING` is on by default (unlike the JDK transformer): only the `URIResolver` and
  the `EntityResolver` give access to other resources, and `xsl:result-document` with `href` does not compile.
  Setting it to `false` sets both access attributes below to `all`, setting it to `true` sets them to the empty
  string.
* What the `URIResolver` does not resolve is read directly if `XMLConstants.ACCESS_EXTERNAL_STYLESHEET` allows the
  scheme (default empty, so nothing; `all` without secure processing; a `jar:` URI needs its nested URI
  allowed too). A `URIResolver` that throws refuses the URI: `xsl:include`/`xsl:import` fail with XTSE0165,
  `document()`/`doc()` with FODC0002, `unparsed-text()`/`json-doc()` with FOUT1170, each with its message, and the
  `*-available` functions return `false`.
* External DTDs and entities are only read with the attribute `ATTR_LOAD_EXTERNAL_ENTITIES`, for stylesheets, modules,
  sources and documents alike: an `EntityResolver` (factory attribute `ATTR_ENTITY_RESOLVER`,
  `PhoxtrotTransformer.setEntityResolver`) is asked first, and the parser opens them itself only for the schemes of
  `XMLConstants.ACCESS_EXTERNAL_DTD` (default empty, `all` without `FEATURE_SECURE_PROCESSING`). Further attributes:
  `ATTR_SCHEMA_AWARE` (the `schema-location` of `xsl:import-schema` goes through the `URIResolver` and
  `ACCESS_EXTERNAL_STYLESHEET` like a module; the documents it references are read only if `file` is allowed),
  `ATTR_MAX_STEPS` and `ATTR_MAX_DEPTH` (the nesting depth of templates and functions, default 10,000,
  `XsltTransformRequest.UNLIMITED_DEPTH` for no limit but the stack; `PhoxtrotTransformer.setMaxDepth` per
  transformer).
* Not supported: `SAXTransformerFactory` (`TransformerHandler`, `TemplatesHandler`), StAX sources and results, and
  writing secondary results (`xsl:result-document`); these are available from `PhoxtrotTransformer.getLastResult ()`.
  With `FEATURE_SECURE_PROCESSING` a stylesheet with an `xsl:result-document` that has an `href` attribute does not
  compile (XTSE0010), as in Saxon (`XsltCompileOptions.setSecondaryResultsAllowed`).
  Initial templates, modes and functions need the phoxtrot API (`PhoxtrotTemplates.getExecutable ()`).

## W3C DOM bridge

`com.helger.phoxtrot.xdm.bridge` converts between W3C DOM and the phoxtrot tree without serialising:
`DomToXdm.convertDocument (aNode, sSystemId)` builds the tree of a DOM (namespace fixup for DOMs built with
`createElementNS`, DOM level 1 names, entity references expanded, ID attributes, unparsed entities, the document URI)
and `DomToXdm.convert` returns the counterpart of a given DOM node in it; `DomToXdm.convertWithMapping` also keeps
the way back from every XDM node to its DOM node (`DomNodeMapping.getDomNode`); `XdmToDom.toDocument`,
`toDocumentFragment` and `appendTo` write a tree, a subtree or a parentless node into a DOM with correct namespace
declarations; `XdmToSax.emit` writes a tree as SAX events. `XdmDomView` shows a tree as a read-only DOM without
copying it (`new XdmDomView (aDoc).getNode (aXdmNode)`, one DOM node per XDM node, `XdmDomView.getXdmNode` back), e.g.
to report a node an XPath expression matched to code that expects `org.w3c.dom` nodes.

## Command line client

`phoxtrot-cli` builds an executable JAR (`phoxtrot-cli-x.y.z-shaded.jar`, Java 21) for checking stylesheets,
expressions and schemas without writing Java code, e.g. in a build or CI step:

```
java -jar phoxtrot-cli-x.y.z-shaded.jar <group> <command> [options]
```

| Command | Description |
|---------|-------------|
| `xslt check STYLESHEET...` | Compile stylesheets and report their static errors with module, line and column, without running them. `--streamable` claims streaming and enforces the streamability rules (XTSE3430) |
| `xslt run -s STYLESHEET [SOURCE]` | Run a stylesheet and write the principal result to `-o FILE` (deleted again if the run fails) or the standard output. `-p NAME=VALUE` and `--xparam NAME=XPATH` set parameters, `--initial-template [NAME]` and `--initial-mode NAME` select the start, `--streamed` reads the source as a stream, `--result-documents DIR` allows `xsl:result-document` with `href` and writes its results to `DIR` (UTF-8), `--trace` and `--trace-instructions` print what is entered. `xsl:message` and the trace go to the error output |
| `xpath check XPATH... / -f FILE...` | Compile expressions and report their static errors with line and column. `--declare NAME[=TYPE]` declares variables, `--static-typing` with `--context-type ITEMTYPE` raises type errors at compile time, `--xslt-functions` knows the XSLT functions |
| `xpath streamability XPATH...` | The streamability verdict (XSLT 3.0 section 19) with posture, sweep and the construct that causes it; `--mode STREAMED\|BURST\|GROUNDED`, `--pattern` for match patterns, `--streamed-var NAME` |
| `xpath eval XPATH...` | Evaluate expressions against `-c FILE` (optional) and print each result; `--var NAME=VALUE`, `--xvar NAME=XPATH`, `--method adaptive\|xml\|json\|text`, `--indent` |
| `xsd check SCHEMA...` | Compile schema documents as one schema and report its error |
| `xsd validate --xsd SCHEMA FILE...` | Validate documents and report every error with line and column (`--max-errors N`); `xsi:schemaLocation` hints are not followed |
| `help [COMMAND]`, `generate-completion` | The help of a command; a bash/zsh completion script |

Shared options: `--ns PREFIX=URI` (XPath commands), `--xsd-version 1.0|1.1` (XSD commands, default 1.0),
`--schema-aware`, `--no-backwards-compatible` and `--static-param NAME=VALUE` (XSLT commands), `--threading MODE` and
`--max-steps N`, `-q`/`--quiet` and `-v`/`--verbose`. Names are `local`, `prefix:local` (with `--ns`; `xsl:` is
predefined for the XSLT commands), `Q{uri}local` or `{uri}local`; string values are `xs:untypedAtomic`, so they are
converted to the declared type.

Secure by default: only local files are read, never the network, without external entities, and only from the folders
of the files named on the command line (stylesheet, source, context document, schemas) and the `--resource-dir DIR`
folders. A refused access is reported as warning, and the engine then raises its error (XTSE0165 for a module, FODC0002
for a document). `--allow-file-access` allows any local file; only then are the schema documents that an
`xsl:import-schema` schema references read. `xsl:result-document` with `href` is rejected (XTSE0010) unless
`--result-documents` is given, and its results must stay inside that folder.

Exit codes: `0` success, `1` a check failed (static error, not streamable, invalid document) or a run raised an error,
`2` invalid command line, `3` technical error (I/O, unreadable source, unusable schema, no verdict of the validator).

`examples/basic` has an invoice with stylesheets, XPath files and an XSD 1.1 schema, and one script per command group
(see its `README.md`).

### Docker

`phoxtrot-cli/Dockerfile` builds an image with the client (Eclipse Temurin 25 JRE); the arguments of `docker run`
after the image name are passed to the client. Mount the files to work on to `/data`, the working directory of the
image:

```
docker run --rm -v "$PWD:/data" phelger/phoxtrot xslt check --streamable my.xsl
docker run --rm -v "$PWD:/data" phelger/phoxtrot xslt run -s to-html.xsl -o out.html invoice.xml
docker run --rm -v "$PWD:/data" phelger/phoxtrot xsd validate --xsd-version 1.1 --xsd invoice.xsd invoice.xml
```

Only files below the mounted folder are visible, so included modules and documents must be inside it too. The exit
code of the client is the exit code of `docker run`.

`docker/build.sh` builds the image:

| Call | Builds |
|------|--------|
| `docker/build.sh snapshot [--mvn]` | The current `-SNAPSHOT` version for this machine, loaded into the local Docker, tagged `<version>` and `snapshot`, smoke tested |
| `docker/build.sh snapshot --push [--mvn]` | The same for linux/amd64 and linux/arm64, pushed |
| `docker/build.sh release [--mvn]` | A released version for linux/amd64 and linux/arm64, pushed, tagged `<version>` and `latest` |

`--mvn` builds the shaded JAR first; without it, `phoxtrot-cli/target` must contain exactly the JAR of the version in
`pom.xml`. `IMAGE` overrides the image name `phelger/phoxtrot`. Pushing needs a `docker login` first.

## Implementation-dependent values

* `fn:generate-id` returns `d`, the number of the node's tree in the run, `n` and the number of the node in that tree,
  both counted in the order the identifiers are first requested (`d1n1`, `d1n2`, `d2n1`, ...). The values differ from
  Saxon's (which also differ between Saxon's tree models), so output that contains them, such as the
  `active-pattern/@id` of an SVRL created by the ISO Schematron skeleton from abstract patterns, differs between
  processors; compare such output with the generated IDs normalised.

## Origin and acknowledgements

This library is a port of [go-xslt](https://github.com/tim-riep/go-xslt) by Tim Riep (Apache 2.0), a from-scratch Go
implementation of XSLT 3.0, XPath 3.1 and XSD. Its architecture, its test strategy and large parts of its behaviour are the
basis of this project. See [NOTICE](NOTICE).

# News and noteworthy

v0.5.0 - work in progress
* New benchmark workload `rounding` (`phoxtrot-xslt-benchmark`) with the rounding stylesheets of the property tests
* XPath expressions are compiled once into a tree of evaluation nodes instead of being interpreted per evaluation
  (`XPathEvaluator.compile (IExpr)`, `compile (IExpr, StaticContext)`, new class `CompiledExpr`); `CompiledXPath`,
  stylesheet expressions and patterns keep the compiled form
* XSLT local variables and parameters are bound by slot in a variable frame per template or function call instead of
  by name along a chain of contexts (new public classes `VariableFrame`, `VariableFrameLayout`, `VariableSlot`;
  `DynamicContext.getFrame`/`withFrame`, `StaticContext.declareFrameVariable`/`getFrameVariable`)
* An XSLT expression or pattern evaluation creates no dynamic context of its own when its static context differs
  from the previous one only in the declared variables (new `StaticContext.internRunTimeIdentity` and
  `isRunTimeEquivalent`; `DynamicContext.withStaticContext` returns the context itself for an equivalent static
  context)
* The variables an XPath expression binds itself (`for`, `let`, `some`/`every`, inline function parameters) are
  bound by slot in a frame per evaluation instead of a new dynamic context per binding (new
  `DynamicContext.getLocalFrame`/`withNewLocalFrame`, `VariableFrame.isLocal`)
* `XdmParser` works around a bug of the XML 1.1 parser of the JDK (JDK 17 to 27, XML 1.0 is not affected), which drops
  or repeats characters of a text when a `]` is the last character of its buffer (after the first 64 characters, then
  every 8 KB; for example `a]a]]&gt;` read as `a]a]a]]>`): a source given as characters (a string, a character
  stream) is read through a reader that never ends a chunk with `]`. A source of bytes or a URI is decoded by the JDK
  parser itself and stays affected; a CDATA section ending in `]` (`]]]>`) is still a parse error under XML 1.1
* The function conversion of arguments checks a single item without an iterator, keeps unchanged function items
  without a sequence builder and, in XPath 1.0 compatibility mode, checks a value that already matches only once
* A named function reference (`f#2`) keeps the resolution of its name in the function library (new component `site`
  of `IExpr.NamedFunctionRefExpr`, as for static calls) and returns the same function item while it resolves to the
  same function, unless the function is focus-dependent; a map constructor with literal keys whose values are
  literals or named function references (a dispatch table) keeps its map while every reference resolves to the same
  function
* A template or stylesheet function call creates one dynamic context for its body, with the frame of its parameters,
  instead of one context per changed property (new `DynamicContext.forHostCall`)
* A tree without grandchildren (such as the temporary tree of an XSLT variable whose content is text) is numbered in
  document order and given its namespace fixup without the stacks of the general tree walk
* An XSLT template call, function call or variable body starts with the dynamic context of the caller's last
  expression, so its first expression creates no dynamic context of its own when its static context is equivalent at
  run time (`XsltContext.getDynamicContext` keeps returning the context with the static context of the instructions)

v0.4.0 - 2026-11-26
* Added property based tests (ph-commons `ph-unittest-property`) for the tree builders, the stream reader, tree
  changes and the DOM bridge, the XPath parser, operators, optimised evaluation paths, regex, string and format
  functions, maps and arrays, serialisation, JSON, date/time arithmetic, sequence functions, collations, binary casts
  and case mapping, the XSD lexical checks, built-in types, facets (patterns, temporal bounds, white space, lists,
  value equality), identity constraints and content models, the name checks, node accessors, the read-only DOM view
  and the namespace fixup, and the XSLT compiler, template rule and key indexes, patterns, `xsl:number`, sorting,
  grouping, `xsl:analyze-string`, `xsl:iterate`, `xsl:merge`, `on-no-match`, output methods, character maps,
  accumulators, computed names, simple content, streaming, the result drain and the JAXP adapter, and the
  `java.math.RoundingMode` modes as XSLT 3.0 functions and as XSLT 1.0 named templates (the latter with XSLT 1.0 and
  XSLT 3.0 behaviour and on the XSLT 1.0 processor of the JDK) against `BigDecimal.setScale`
* `IXdmNode.appendChild` refuses a node that is the new parent itself or one of its ancestors (it made the tree a
  cycle, so walks up to the root and the document order numbering never ended)
* `XdmStreamReader` reads documents whose encoding has a Java name (`UTF8`, `Cp1252`, `ISO8859_1`) as `XdmParser`
  does; the JDK StAX parser knows only the IANA names, so a streamed run failed on a source the buffered run read
* XSD 1.0: `+INF` for xs:float/xs:double and the year 0000 in the date family are refused wherever an atomic value is
  checked (list items, union members, facet values, default and fixed values), not only for the type of an element
  or attribute
* XSD value equality (enumeration, fixed values): an enumeration of a union compares the value of the first member
  that accepts each side (with `memberTypes="xs:int xs:boolean"` the enumeration value `1` admits `01`, not `true`),
  and an enumeration value outside the value space of the base union is a schema error; enumerations of
  xs:hexBinary and xs:base64Binary compare octets (`0a` equals `0A`); a date/time value without timezone never equals
  one with a timezone; a fixed value of a union equals the instance value when the values are equal, also if
  different members accept them
* Serialisation (xml and xhtml methods): CR, NEL, U+2028 and the control characters #x7F-#x9F (under version 1.1
  also #x1-#x1F) are written as character references in text and attribute values, also inside CDATA sections
  (which are closed for them, as for an unencodable character; a C0 control under version 1.0 is SERE0006 there as in
  text); a character the encoding cannot hold in a comment or processing instruction is SERE0008 (xml, xhtml and html
  methods, not the adaptive method); `XdmIncrementalSerializer` writes an element without content in the same form
  as `XdmSerializer` (`<a/>`, `<br>`, `<br />`)
* `xsl:analyze-string`: after a zero-length match the next character is a whole code point, so a character outside
  the BMP is no longer split into its surrogates
* `XdmDomView`: `isDefaultNamespace (null)` follows DOM Level 3 Core appendix B.3 (false for a node without an
  element that is unprefixed or declares the default namespace, for example a comment outside the document element)
* New module `phoxtrot-cli`: a command line client (executable JAR) with `xslt check` (static errors, `--streamable`),
  `xslt run`, `xpath check` (static errors, static typing), `xpath streamability`, `xpath eval`, `xsd check` and
  `xsd validate`. It reads only local files from the folders named on the command line unless `--allow-file-access`
  is given, and allows `xsl:result-document` with `href` only with `--result-documents`. With a Dockerfile
  (`phoxtrot-cli/Dockerfile`, `docker/build.sh`) and examples (`examples/basic`)
* XSD: the xs:base64Binary lexical check uses a lookup table and a tight loop for the leading run of base64
  characters instead of range comparisons, whose branches are mispredicted on base64 data: about 8 times faster,
  which halves the validation time of an e-invoice with a 200 KB attachment
* `XdmTreeBuilder` uses a new buffer for each text node that is merged from several character events: a reused
  buffer that once held a non-Latin-1 character stays UTF-16, so all later text was inflated and compressed again
* XSD: new `XsdSchema.assessText` validates instance text like `validateText` and also reports the governing type
  definition of every element and attribute (`XsdAssessment`, `XsdNodeType`), with `xsi:type`, conditional type
  assignment, substitution groups and wildcards resolved. Nodes are identified by their element positions, so a
  caller can map them to its own parse of the same text
* XSD: new `XsdSimpleTypeView.getEffectiveFacets` adds the facets XSD Datatypes defines for the built-in derived types
  (the bounds of `xs:byte`, `xs:int`, `xs:unsignedLong`, `xs:positiveInteger` etc. and `fractionDigits` 0 of
  `xs:integer`) to the declared ones; `getFacets` still reports only the declared facets
* XSD: new `XsdSimpleTypeView.getValueError`/`isValidValue` check a value against a simple type exactly as the validator
  does (whiteSpace, lexical space, all facets), and new `XsdPatternMatcher` matches the regular expression of a
  `pattern` facet without a schema
* XSD: `XsdNodeType` (of `XsdSchema.assessText`) also reports the element declaration an element was assessed against
* XSD: an undeclared `xml:*` attribute (`xml:lang`, `xml:space`, `xml:base`, `xml:id`) is now invalid under 1.0 and
  1.1 unless an attribute use (`ref="xml:lang"`) or a wildcard admits it (cvc-complex-type.3.2.2, on a simple-typed
  element cvc-type.3.1.1); before, it was accepted under 1.0 and on every simple-typed element. Only `xsi:type`,
  `xsi:nil`, `xsi:schemaLocation` and `xsi:noNamespaceSchemaLocation` are exempt, so `xsi:foo` is rejected too
* XSLT: functions registered with `XsltExtensionRegistry.registerFunctions` are now also visible in static expressions
  (`use-when`, static variables and parameters, shadow attributes), so `function-available` answers there as at run
  time; the run-time XSLT functions (`current()`, `key()`, `current-group()` etc.) stay unavailable there
* The classes and records of the engine modules (enums, interfaces and exceptions excepted) carry a concurrency
  annotation (`@Immutable`, `@NotThreadSafe` or `@ThreadSafe`); `FnTransform` is now `@ThreadSafe` instead of
  `@Immutable`
* XSLT: the lazily built in-scope namespaces of a stylesheet element (`xsl:evaluate`,
  `XsltExecutable.createStaticContext`) are published safely, so concurrent transformations of one compiled stylesheet
  no longer race on them
* `FunctionLibrary.register` with an implementation for dynamic calls registers the complete definition under the
  write lock; before, the dynamic implementation was set afterwards without the lock and the frozen check
* XSLT API change: `OutputDefinition.ATTRIBUTES` is an unmodifiable `List`, `XsltElementSpecs.STANDARD_ATTRIBUTES`
  and `ElementValidator.TOP_LEVEL_DECLARATIONS` are unmodifiable `Set`s, and `XsltExtensionRegistry.EXTENSION_CLASS_NAMES`,
  `XsltExtensionRegistry.CORE_DECLARATIONS` and `XsltFunctions.SYSTEM_PROPERTIES` are private: before, any caller
  could change these shared tables for every thread
* Documented the thread rules of `CompiledXPath`, `XsltExecutable`, `XsdSchema` and node trees (README "XPath API",
  Javadoc)

v0.3.0 - 2026-11-17
* JAXP: the attribute names of `PhoxtrotTransformerFactory` (`ATTR_PREFIX`) are now URNs starting with
  `urn:fdc:helger.com:phoxtrot:jaxp:` instead of `http://helger.com/phoxtrot/jaxp/`, and `XdmSource.FEATURE` is
  `urn:fdc:helger.com:phoxtrot:jaxp:feature:xdm-source`; the old names are no longer accepted
* JAXP: `PhoxtrotTransformerFactory` now has `FEATURE_SECURE_PROCESSING` on by default (new
  `DEFAULT_SECURE_PROCESSING`), so `ACCESS_EXTERNAL_STYLESHEET` and `ACCESS_EXTERNAL_DTD` default to the empty string
  (`DEFAULT_ACCESS_EXTERNAL`; `ACCESS_EXTERNAL_ALL` is `all`) and `xsl:result-document` with `href` does not compile:
  includes, imports and documents need a `URIResolver` or an allowed scheme. Setting the feature to `false` now sets
  both access attributes to `all`
* JAXP: new `PhoxtrotTransformer.setCurrentDateTime` to fix the value of `fn:current-dateTime()`
* Schema-awareness: Apache Xerces-J is no longer supported as schema processor; the native validator (`phoxtrot-xsd`) is
  the only one. Removed the package `com.helger.phoxtrot.xdm.xerces`, the optional dependency on `xerces:xercesImpl`
  and the system property `xdm.schemaProvider` (`XdmSchemaSupport.SYSTEM_PROPERTY_PROVIDER`)
* XPath: `fn:parse-xml` can read the external DTD subset and external entities of the parsed string through an entity
  resolver (new `DynamicContext.setParseXmlEntityResolver`, `XsltTransformRequest.setParseXmlEntityResolver`; JAXP:
  the transformer's entity resolver when the factory loads external entities); without one nothing is read, as
  before. Relative references are resolved against the static base URI (new `XdmParser.parse (String, String)`)
* XPath: the optional Static Typing Feature (`StaticContext.setStaticTyping`) now infers the static type of every
  expression and raises a type error where the inferred type is not accepted: function arguments (after the function
  conversion rules, function coercion included), arithmetic, comparison, cast and range operands, path operands
  (XPTY0019/XPTY0020), dynamic calls and lookups, and inline function bodies against their declared return type; and
  XPST0005 for every expression other than `()` and `data(())` inferred as `empty-sequence()`. Before, it only
  rejected provably empty axis steps. It is pessimistic: hosts declare the types of what they supply with the new
  `StaticContext.setContextItemStaticType` and `StaticContext.declareVariable (name, SeqType)` (otherwise the context
  item is `item()` and a variable `item()*`)

v0.2.0 - 2026-11-14
* XSLT: the static context of every expression shares the set of the global variable names instead of copying it
  (Schematron stylesheets with hundreds of globals compile 30 to 40 % faster); new
  `StaticContext.declareSharedVariables`
* XPath/XSLT: the `xs:integer` values from -128 to 127 are shared instances; attribute value templates and
  `xsl:value-of` take the string of a single atomic value directly; the serializer appends runs of characters that
  need no escaping in one go
* XSD: identity constraints build the key of one field without a list and join, and do not cast `xs:string` values;
  the XSD 1.0 check of late schema location hints adds a namespace to its set only when it changes
* XSD 1.1: with assertions in the schema, the types recorded for the typed values that assertions read are dropped
  whenever no element with assertions is open, so the record no longer grows with the document (10,000 invoice lines:
  29 % faster, a quarter of the GC time); the per-name child counts of the dynamic EDC check are only kept for a content
  model with a lax or strict wildcard (from go-xslt 0.4.3)
* XPath: `fn:tokenize`, `fn:replace` and `fn:matches` with a pattern of plain characters (no flags and no
  metacharacter, or the `q` flag) search the string without a regular expression (44 % faster); `format-date`,
  `format-dateTime` and `format-time` render pictures of ASCII digits only (`[D01]`, `[Y0001]`) without parsing a digit
  pattern (40 % faster, 63 % less allocated) (from go-xslt 0.4.3)
* XPath: `fn:path` counts the sibling positions of the children of a parent once per run, so the paths of many
  siblings are linear; a predicate comparing `name()`, `local-name()` or `namespace-uri()` with a value that is the same
  for every candidate (`[name() = name(current())]`, also joined with `and`) evaluates that value once and compares the
  names directly (ISO Schematron locations about 4 to 7 times faster). `FnAccessor.getName`, `getLocalName` and
  `getNamespaceUri` are public; the XSLT `current()` is `FnContext.XSLT_CURRENT`
* XPath/XSD: regular expressions with category escapes (`\w`, `\W`, `\d`, `\p{..}`) are fast again on JDK 24 and
  later: an input without a code point whose category changed after Unicode 15.0 is matched with the built-in classes
  of the JDK instead of the corrected ones (same result); new `XPathRegex.getPattern (String)`
* XPath: `XPathStreamabilityAnalyzer` has a burst context mode (`setContextMode (EStreamContextMode.BURST)`, verdict
  `EStreamabilityVerdict.BURST`): is an expression evaluable on the buffered subtree of the context node, and if not,
  which construct leaves it; added `addExternalNodeVariable`. Behaviour change: `XPathStreamabilityResult.isStreamable ()`
  is `true` for motionless and consuming only; `isBurstStreamable ()` includes burst
* XPath: in burst mode `XPathStreamabilityAnalyzer.analyzePattern` gives `BURST` for a pattern whose last-step
  predicates need only the subtree of the matched node (`cac:Line[cbc:Quantity < 0]`), and navigating from an external
  node variable is free-ranging also in an otherwise motionless expression. In streamed and burst mode the context node
  is taken to be no document node: `/x` is the document element, an ancestor-or-self (`/x/@a`, `name (/*)`
  motionless, `/x/y` still free-ranging)
* XPath: `XPathStreamabilityAnalyzer.analyzeBinding` and `addBoundVariable` declare a variable with a binding
  expression compiled once (e.g. a `sch:let` shared by the assertions of a rule); every analysed expression gets the
  result of the same expression in `let $a := (...) return`
* XPath: added `FunctionLibrary.createDefaultWithXsltSignatures ()`, the built-in functions plus the signatures of the
  XSLT functions (`fn:key`, `fn:current`, `fn:document`, ...) for hosts that only compile and analyse expressions
* API change: every `IResourceResolver` method now takes a `ResourceUri` (the resolved URI, the reference as written
  and its base URI) instead of the absolute URI as string; implementations use `ResourceUri.absoluteUri ()`
* JAXP: the `URIResolver` is now called as JAXP specifies, with the `href` as written and the base URI (the empty
  string without one), so a resolver can apply its own default base for a stylesheet without system ID
* JAXP: a transformation into a `DOMResult` or `SAXResult` no longer serialises the result it never uses; added
  `XsltTransformRequest.setPrincipalTreeOnly`
* Added `IXdmNode.declaredNamespaces ()`, a read-only view of the own namespace declarations without copying, used by
  the serialiser and the DOM and SAX bridges
* JAXP: with `FEATURE_SECURE_PROCESSING` a stylesheet with an `xsl:result-document` that has an `href` attribute now
  fails to compile with XTSE0010, as in Saxon; added `XsltCompileOptions.setSecondaryResultsAllowed`, inherited by
  `fn:transform`
* XPath: a union of single attribute and child steps without predicates, such as `*|comment()|processing-instruction()`
  or `@*|*`, is evaluated in one pass over each axis without sorting (as in the ISO Schematron skeleton)
* XSLT: pattern matching creates no new dynamic context for a path pattern without predicates such as `/root/item`
  or `node()`
* XSLT: the elements built by one literal result element share one map of namespace declarations until one of them
  changes; added `IXdmNode.declareSharedNamespaces`
* `XdmDocumentNode.getChildElementsByName` no longer builds the name index of the whole document; it goes through the
  children of the parent once and keeps the result
* `DomToXdm` allocates less than half as much per conversion (no per-node walk records, attribute and namespace
  objects only where needed, DOM text used without copying); added `XdmTreeBuilder.characters (String)`
* JAXP: added the factory attribute `ATTR_MAX_DEPTH` and `PhoxtrotTransformer.setMaxDepth` for the nesting depth
  limit of templates and functions
* JAXP: the output properties are kept in the unsynchronised `NonBlockingProperties` of ph-commons; the JAXP methods
  still take and return `java.util.Properties`
* Behaviour change: `XsltTransformRequest.setMaxDepth` can now raise the limit above the default of 10,000; added
  `XsltTransformRequest.UNLIMITED_DEPTH` for no limit but the stack
* Behaviour change: `fn:generate-id` now returns values of the form `d1n5` (tree number and node number) instead of
  `id5`, so they no longer collide with IDs like `id1` written by authors
* Added `PhoxtrotVersion` with the version number and build timestamp of phoxtrot
* Updated to ph-commons 12.6.0 and replaced own helpers with its new ones (code points, XML whitespace and name
  characters, hex output, primitive `int` collections)
* The caches of compiled regular expressions and UCA collations are now readable without locking
* JAXP: added the factory attribute `ATTR_ENTITY_RESOLVER` and `PhoxtrotTransformer.setEntityResolver`, used for the
  external DTDs and entities of stylesheets, modules, sources and documents read at run time
* JAXP: `XMLConstants.ACCESS_EXTERNAL_DTD` is now applied (new `XdmParser.setAccessExternalDtd`), so secure processing
  also stops external DTDs and entities that only the entity resolver may then provide
* JAXP: a `jar:` URI is only read directly if `ACCESS_EXTERNAL_STYLESHEET` allows `jar` and its nested URI
* A `URIResolver` that refuses a URI now gives a located error with its reason: XTSE0165 for `xsl:include` and
  `xsl:import`, XTSE0020 for `xsl:output parameter-document`, XTSE3000 for `xsl:use-package`, FOXT0002 for
  `fn:transform`, FODC0002 for `document()` and `doc()`, FOUT1170 for `unparsed-text()` and `json-doc()`
* A document read directly that is not well-formed is now logged as a warning
* The `schema-location` of `xsl:import-schema` is now read through the resolver of the compile options (in JAXP the
  `URIResolver` and `ACCESS_EXTERNAL_STYLESHEET`); added `XsltCompileOptions.setReadLocalSchemaFiles`
* Schema processors never read referenced schema documents from the network; added
  `IXdmSchemaProvider.compile (List, boolean)` to forbid local reads as well, and the Xerces-J provider now uses secure
  processing
* Behaviour change: `XsdSchema.validate` no longer follows the `xsi:schemaLocation` hints of an instance unless
  `XsdValidationOptions.setFollowSchemaLocationHints (true)` is set
* Added `FunctionLibrary.freeze`: the function libraries of a compiled stylesheet are now frozen, so a static function
  call no longer reads the modification stamp of the library chain on every call
* Behaviour change: registering into the function library of a compiled stylesheet (e.g. the one of
  `XsltExecutable.createStaticContext`) now throws an `IllegalStateException`; register into a `createChild ()` instead
* XPath: a call of a built-in function whose value only depends on the context item and its variables is evaluated
  once while they stay the same, e.g. `normalize-space($item/a)` in a predicate or `normalize-space(a)` in the body
  of a `some`/`every`/`for` (loop-invariant hoisting); new `DynamicContext.getVariableToken`, `getRunMemo` and
  `getLoopFocus`
* XPath: `//name` from the document node and a child step from a node with many children reuse one sequence of the
  name index per run instead of copying it on every step; new `AxisNavigator.getNamedIndexList`
* API change: `EXsltThreading` is now `com.helger.phoxtrot.xpath.EThreading` and `LargeStackRunner` moved to
  `com.helger.phoxtrot.xpath` (module `phoxtrot-xpath`), so plain XPath evaluations can use the large-stack threads
* XPath: added a step limit for plain XPath (`DynamicContext.setMaxSteps`, XPDY0130 when exceeded, unlimited by
  default)
* XPath: added `CompiledXPath.getFreeVariables` (the variables of the static context an expression uses)
* XPath: a static error now has its position in the expression (`XPathException.getExpressionOffset`,
  `getExpressionLine`, `getExpressionColumn`)
* XPath: added `FunctionLibrary.register (XdmQName, List, SeqType, IXPathFunction)` for extension functions with typed
  signatures; the argument conversion is documented in `IXPathFunction`
* XPath: the public API of `phoxtrot-xpath` is listed in the README and the package documentation
* XSLT: an error raised in the body of an `xsl:function` names that function (`XsltException.getFunctionFrame`)
* DOM bridge: added `DomToXdm.convertWithMapping`, which keeps the DOM node of every XDM node (`DomNodeMapping`)
* DOM bridge: added `XdmDomView`, a read-only W3C DOM view over an XDM tree that copies nothing
* XPath: added `XPathStreamabilityAnalyzer`, the public streamability analysis of expressions and patterns; `EPosture`,
  `ESweep`, `PostureSweep` and `EStreamFunctionCategory` of `stream` are now public API

v0.1.0 - 2026-11-10
* Initial release
* XSD 1.0 and XSD 1.1 support
* XPath 3.1 support
* XSLT 3.0 support incl. streaming

# Contributing

Copyright (C) 2026 Philip Helger. Licensed under the Apache License, Version 2.0.

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.

# jSMIparser — Implementation Specification
### Java 17 port of pysmi's ASN.1 MIB → JSON pipeline
**License:** BSD-2-Clause | **Target:** Java 17, packaged as a single jar

---

## 1. Scope & Goals

Take a string of ASN.1 MIB text and return a JSON string that is structurally identical to what `pysmi`'s `JsonCodeGen` produces. No file I/O, no HTTP, no classpath scanning — that is the caller's concern. The library is a pure transformation engine.

**In scope:**
- SMIv1, SMIv2, and common de-facto dialect quirks (matching pysmi's `SmiV1CompatParser` / `SmiV2Parser` behaviour)
- All SMI construct types: `MODULE-IDENTITY`, `OBJECT-TYPE`, `NOTIFICATION-TYPE`, `TEXTUAL-CONVENTION`, `OBJECT-GROUP`, `NOTIFICATION-GROUP`, `MODULE-COMPLIANCE`, `AGENT-CAPABILITIES`, `OBJECT-IDENTITY`, `TRAP-TYPE` (SMIv1), macro stubs, type assignments
- Symbol table for cross-MIB dependency tracking (pysmi's `SymtableCodeGen` equivalent), needed for correct OID resolution when multiple MIBs are compiled together
- JSON output schema exactly matching pysmi 2.x (lextudio fork, current as of mid-2026)
- Optional: `genIndex()` equivalent producing the OID→MIB index blob

**Out of scope (for the initial jar):**
- File/ZIP/HTTP readers (caller passes `String mibText`)
- PySNMP Python codegen backend
- `mibdump` CLI wrapper (can be a separate thin module)

---

## 2. JSON Output Schema

The output is a JSON object keyed by SMI object name. Each value is an object whose shape depends on the `class` field. Below is the authoritative field mapping derived from pysmi's Jinja2 template and `IntermediateCodeGen`.

### 2.1 Top-level envelope

```json
{
  "<objectName>": { ... },
  "<objectName2>": { ... }
}
```

No wrapper object — the root is a flat map of all named objects in the MIB module.

### 2.2 Per-class shapes

#### `moduleidentity`
```json
{
  "name": "ifMIB",
  "oid": "1.3.6.1.2.1.31",
  "class": "moduleidentity",
  "revisions": ["2007-02-15 00:00", "1996-02-28 21:55"],
  "lastupdated": "200702150000Z",
  "organization": "IETF Interfaces MIB Working Group",
  "contactinfo": "...",
  "description": "..."
}
```

#### `objecttype`
```json
{
  "name": "ifNumber",
  "oid": "1.3.6.1.2.1.2.1",
  "class": "objecttype",
  "syntax": { "type": "Integer32" },
  "maxaccess": "read-only",
  "status": "current",
  "description": "...",
  "nodetype": "scalar"
}
```
`nodetype` is one of: `scalar`, `row`, `table`, `column`, `node`

Syntax variants:
```json
// Primitive
"syntax": { "type": "OctetString", "constraints": { "size": [{"min": 0, "max": 255}] } }

// Enumeration
"syntax": { "type": "Integer32", "constraints": { "enumeration": {"up": 1, "down": 2} } }

// Bits
"syntax": { "type": "Bits", "constraints": { "bits": {"bit0": 0, "bit1": 1} } }

// Sequence (table row)
"syntax": { "type": "IfEntry" }

// Textual convention reference
"syntax": { "type": "InterfaceIndex" }
```

#### `textualconvention`
```json
{
  "name": "InterfaceIndex",
  "class": "textualconvention",
  "displayhint": "d",
  "status": "current",
  "description": "...",
  "syntax": { "type": "Integer32", "constraints": { "range": [{"min": 1, "max": 2147483647}] } }
}
```

#### `notificationtype`
```json
{
  "name": "linkDown",
  "oid": "1.3.6.1.6.3.1.1.5.3",
  "class": "notificationtype",
  "objects": [{"module": "IF-MIB", "object": "ifIndex"}],
  "status": "current",
  "description": "..."
}
```

#### `objectgroup` / `notificationgroup`
```json
{
  "name": "ifGeneralInformationGroup",
  "oid": "1.3.6.1.2.1.31.2.1.1",
  "class": "objectgroup",
  "objects": [{"module": "IF-MIB", "object": "ifDescr"}],
  "status": "current",
  "description": "..."
}
```

#### `modulecompliance`
```json
{
  "name": "ifCompliance3",
  "oid": "1.3.6.1.2.1.31.2.1.3",
  "class": "modulecompliance",
  "modulecompliance": [
    {
      "module": "IF-MIB",
      "mandatory": ["ifGeneralInformationGroup"],
      "optional": [{"group": "ifCounterDiscontinuityGroup", "description": "..."}]
    }
  ],
  "status": "current",
  "description": "..."
}
```

#### `objectidentity`
```json
{
  "name": "transmission",
  "oid": "1.3.6.1.2.1.10",
  "class": "objectidentity",
  "status": "current",
  "description": "..."
}
```

#### `agentcapabilities`
```json
{
  "name": "myAgent",
  "oid": "1.3.6.1.2.1.999",
  "class": "agentcapabilities",
  "productsrelease": "...",
  "status": "current",
  "description": "..."
}
```

#### `moduleidentityoid` (bare OID assignment, i.e. `snmpMIBObjects OBJECT IDENTIFIER ::= { ... }`)
```json
{
  "name": "snmpMIBObjects",
  "oid": "1.3.6.1.6.3.1.1",
  "class": "objectidentity"
}
```

#### SMIv1 `traptype`
```json
{
  "name": "linkDown",
  "oid": "1.3.6.1.6.3.1.1.5.3",
  "class": "notificationtype",
  "objects": [...],
  "description": "..."
}
```
SMIv1 traps are normalised to `notificationtype` in the output.

### 2.3 Fields present only with `genTexts=true`
`description`, `reference`, `contactinfo`, `organization` — these are always included in the current lextudio 2.x default behaviour (they changed the default); the Java port should always emit them.

---

## 3. Architecture

```
MibParser (public API)
    │
    ├── Lexer          (hand-written or ANTLR4 — see §4)
    ├── SmiParser      (ANTLR4 grammar → CommonTree AST)
    ├── SymbolTable    (two-pass: first resolve imports, then OID chains)
    └── JsonGenerator  (AST walker → Jackson ObjectNode → String)
```

### 3.1 Public API surface (minimal)

```java
package io.github.yourorg.jsmiparser;

public final class MibParser {

    /**
     * Parse a single MIB module from text.
     *
     * @param mibText  raw ASN.1 MIB text
     * @return         JSON string matching pysmi JsonCodeGen output
     * @throws MibParseException on lexer/parser/semantic error
     */
    public static String toJson(String mibText) throws MibParseException { ... }

    /**
     * Parse multiple MIB modules together so cross-module OID chains
     * can be fully resolved. Keys are module names (used for error messages).
     *
     * @param mibs     map of moduleName → mibText
     * @return         map of moduleName → JSON string
     */
    public static Map<String, String> toJson(Map<String, String> mibs)
            throws MibParseException { ... }

    /**
     * Build the OID→MIB index JSON blob (equivalent to pysmi buildIndex()).
     *
     * @param compiled  results from toJson(Map)
     * @return          JSON index string
     */
    public static String buildIndex(Map<String, String> compiled) { ... }
}
```

No builders, no fluent chains, no config objects needed for v1. Add `MibParserOptions` later if dialect overrides are needed.

---

## 4. Parser Strategy

### Recommended: ANTLR4

**Why:** ANTLR4 is the Java-native equivalent of Python's PLY. It generates a typed visitor/listener tree, has excellent error recovery, and the runtime jar is small (~500 KB). The grammar can be written once and is readable enough to maintain parity with pysmi bug-fixes.

**Grammar file:** `SMI.g4` — one file covering both SMIv1 and SMIv2 constructs plus common dialect extensions (pysmi's "relaxed" mode). There are existing partial ANTLR grammars for ASN.1 to start from; SMI is a strict subset, so the grammar will be simpler than full ASN.1.

**Build:** ANTLR4 Maven/Gradle plugin generates the parser at compile time — no runtime generation, no temp directories (this eliminates one of pysmi's pain points).

**Alternative: Hand-written recursive descent** — feasible since SMI grammar is LL(1) for most constructs. Slightly more code but zero external dependencies beyond Jackson. Reasonable choice if the team prefers zero build-time codegen.

### Two-pass compilation (matches pysmi's `SymtableCodeGen` → `JsonCodeGen` flow)

Pass 1 — Symbol table: collect all defined names, their OID fragments, and import declarations from all supplied MIBs. Resolve OID chains (e.g. `{ enterprises 99 }` needs `enterprises` from `SNMPv2-SMI`).

Pass 2 — JSON generation: walk the AST with fully resolved OIDs and emit the JSON structure.

### SMIv1/SMIv2 dialect handling

pysmi's `SmiV1CompatParser` accepts both dialects. The Java grammar should do the same — SMIv1 constructs (`TRAP-TYPE`, `ACCESS` instead of `MAX-ACCESS`, `RFC1213-MIB` style integer enumerations on syntax) should parse without error and be normalised to their SMIv2-equivalent JSON representation on output.

---

## 5. Dependencies

| Purpose | Library | Notes |
|---|---|---|
| Parser generator | `org.antlr:antlr4:4.13.x` | Build-time only; runtime jar is `antlr4-runtime` |
| JSON serialization | `com.fasterxml.jackson.core:jackson-databind:2.17.x` | Produces the output; well-known, no surprises in Ignition |
| Testing | `org.junit.jupiter:junit-jupiter:5.x` | JUnit 5 |
| Test MIBs | Bundled in `src/test/resources/mibs/` | Use real IANA/IETF MIBs (RFC1213, IF-MIB, IP-MIB, SNMPv2-MIB, etc.) |

**No other runtime dependencies.** Ignition modules have a constrained classpath and extra transitive deps cause problems.

Jackson is the one dependency worth discussing — it pulls in `jackson-core` and `jackson-annotations` as well. If that is unacceptable (e.g. Ignition already provides a conflicting version), the JSON can be built with `StringBuilder` / a minimal hand-rolled serializer, since the output schema is fixed and well-understood. Worth checking Ignition's bundled Jackson version first.

---

## 6. Project Structure

```
jsmiparser/
├── build.gradle (or pom.xml)
├── src/
│   ├── main/
│   │   ├── antlr4/
│   │   │   └── io/github/yourorg/jsmiparser/SMI.g4
│   │   └── java/
│   │       └── io/github/yourorg/jsmiparser/
│   │           ├── MibParser.java          (public API)
│   │           ├── MibParseException.java
│   │           ├── internal/
│   │           │   ├── SmiLexer.java       (ANTLR-generated or hand-written)
│   │           │   ├── SmiAstParser.java   (ANTLR-generated)
│   │           │   ├── SymbolTable.java    (pass 1)
│   │           │   ├── JsonGenerator.java  (pass 2, AST walker)
│   │           │   ├── MibNode.java        (sealed hierarchy of AST node types)
│   │           │   └── OidResolver.java
│   │           └── model/                  (optional: typed Java records mirroring JSON schema)
│   └── test/
│       ├── java/.../
│       │   ├── IfMibTest.java
│       │   ├── IpMibTest.java
│       │   ├── Smiv1Test.java
│       │   └── JsonSchemaTest.java
│       └── resources/mibs/
│           ├── IF-MIB.txt
│           ├── IP-MIB.txt
│           ├── SNMPv2-SMI.txt
│           └── ...
```

---

## 7. Test Strategy

The test approach is differential: run pysmi against a corpus of real MIBs, capture the JSON output, then assert the Java library produces structurally equivalent JSON for the same inputs.

### Corpus
Start with ~20 well-known MIBs: `IF-MIB`, `IP-MIB`, `TCP-MIB`, `UDP-MIB`, `SNMPv2-MIB`, `HOST-RESOURCES-MIB`, `ENTITY-MIB`, `CISCO-SMI`, a few Cisco/vendor MIBs with SMIv1 constructs and quirks. These cover almost all grammar branches.

### Test levels

**Unit tests** on individual grammar constructs — single `OBJECT-TYPE` declarations, `TEXTUAL-CONVENTION` blocks, `IMPORTS` statements, OID chain resolution.

**Integration tests** — full MIB → JSON round-trip, compared against pysmi reference output stored as golden files in `src/test/resources/golden/`.

**Schema tests** — assert every output object has a valid `class` field, every `objecttype` has an `oid`, no null fields leak into the JSON.

**Parity CI check** — a Python script (run in CI) regenerates golden files from pysmi whenever the pysmi version is bumped, so drift is caught early.

---

## 8. Known Complexity Areas (ranked by risk)

### High
**OID chain resolution** — MIBs reference names from other MIBs (`IMPORTS enterprises FROM SNMPv2-SMI`). Without the imported module loaded, OIDs cannot be fully resolved. The `toJson(Map<String,String> mibs)` multi-module entry point addresses this. For single-module calls (`toJson(String)`), un-resolvable imports should emit a relative OID (e.g. `"enterprises.99"`) rather than failing hard — matching pysmi's lenient behaviour.

**SMIv1 normalisation** — `TRAP-TYPE` → `notificationtype`, `ACCESS` → `MAX-ACCESS` mapping, `SYNTAX` type coercion differences. Test against real SMIv1 MIBs early.

### Medium
**Constraint syntax** — `SIZE`, `RANGE`, `BITS` enumerations, `FROM` clauses inside constraints. These have several syntactic forms and pysmi has had bugs here (the BITS KeyError mentioned in issue #5). Implement all variants with explicit test cases.

**DESCRIPTION/text normalization** — pysmi normalises whitespace and indentation in multi-line description strings. Match this behaviour exactly or downstream tools that compare descriptions will break.

**Index clauses** — `INDEX { ifIndex, ... }` and `AUGMENTS { ... }` produce `"indices"` and `"augmention"` (note: pysmi has a typo — `augmention` not `augmentation`) fields in the JSON. The typo must be preserved for compatibility.

### Low
**MACRO stubs** — pysmi skips `MACRO` definitions; emit a stub object with `"class": "macro"`.

**Integer sub-typing** — `Gauge32`, `Counter32`, `TimeTicks` etc. are emitted as their type names, not as `Integer32`. Ensure the type name mapping table is complete.

---

## 9. Build & Packaging

```groovy
// build.gradle sketch
plugins {
    id 'java-library'
    id 'antlr'
    id 'maven-publish'
}

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

dependencies {
    antlr 'org.antlr:antlr4:4.13.2'
    api 'com.fasterxml.jackson.core:jackson-databind:2.17.2'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
}

generateGrammarSource {
    arguments += ['-package', 'io.github.yourorg.jsmiparser.internal']
    outputDirectory = file("build/generated-src/antlr/main/io/github/yourorg/jsmiparser/internal")
}

jar {
    // Fat jar for Ignition module convenience — include runtime deps
    from configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes 'Implementation-Title': 'jSMIparser' }
}
```

Publish to Maven Central or GitHub Packages. Ignition module devs add it as a `modlApi` dependency in their `build.gradle`.

---

## 10. Effort Estimate

| Phase | Work | Estimate |
|---|---|---|
| ANTLR4 SMI grammar (SMIv1 + SMIv2 + dialect quirks) | Core grammar rules, lexer tokens, error recovery | 5–8 days |
| AST node model + symbol table (pass 1) | OID resolution, import tracking | 3–4 days |
| JSON generator (pass 2) | All class types, constraint serialization | 4–6 days |
| Test corpus + golden files | pysmi parity CI | 2–3 days |
| Build/packaging/Javadoc | Fat jar, Maven publish | 1 day |
| **Total** | | **~3–4 weeks solo** |

The grammar is the critical path. Once it covers IF-MIB and IP-MIB cleanly, everything else is JSON plumbing.

---

## 11. pysmi Behavioural Notes to Preserve

These are subtle things discovered from the pysmi source and issue history that the Java port must replicate:

- **`augmention` typo** in JSON field name — intentional parity requirement
- **OID lexicographic sort** in `buildIndex()` output — sort as dotted-integer tuples, not strings
- **`baseMibs` stub list** — `SNMPv2-SMI`, `SNMPv2-TC`, `SNMPv2-CONF`, `RFC1155-SMI`, `RFC1213-MIB`, etc. are treated as pre-compiled stubs and should not produce JSON output (they are referenced but not expanded)
- **Revision timestamps** — output as `"YYYY-MM-DD HH:MM"` string (space-separated, not `T`)
- **Numeric OID prefix for sub-identifiers starting with a digit** — e.g. `802dot3(10006)` — the lexer must handle these as lower-cased tokens (pysmi CHANGES.rst bug fix)
- **`nodetype` field** — only present on `objecttype` objects, not on `objectidentity` or others
- **Empty `objects` array** — emit `[]` not omit the field, for `notificationtype` and `objectgroup`

---

## 12. Suggested Repository Name & Coordinates

```
GitHub:   github.com/yourorg/jsmiparser
Maven:    io.github.yourorg:jsmiparser:1.0.0
License:  BSD-2-Clause (matching pysmi)
```

If this ends up under the Embr/Musson Industrial umbrella, align the group ID with whatever convention the other Embr modules use.

---

## Project-specific notes (this repo)

- Base package: `za.co.integ.jsmiparser` (replaces the spec's `io.github.yourorg.jsmiparser` placeholder).
- Build tool: Gradle (Groovy DSL), per §9, with wrapper checked in.
- Current state: scaffolding only — build wiring, package layout, public API
  signatures, and a starter grammar are in place. The grammar rules, symbol
  table, and JSON generator logic (§4, §8) are not yet implemented.

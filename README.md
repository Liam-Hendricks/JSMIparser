# jSMIparser

Java 17 port of [pysmi](https://github.com/lextudio/pysmi)'s ASN.1 MIB → JSON pipeline.

Takes a string of ASN.1 MIB text and returns a JSON string structurally
identical to pysmi's `JsonCodeGen` output. No file I/O, no HTTP, no classpath
scanning — the caller supplies MIB text and receives JSON back.

> **Status: core pipeline working, single-module only.** `MibParser.toJson`
> parses a MIB and emits JSON for all major construct types (MODULE-IDENTITY,
> OBJECT-TYPE incl. scalar/table/row/column nodetype inference,
> NOTIFICATION-TYPE, TRAP-TYPE, TEXTUAL-CONVENTION, OBJECT-GROUP,
> NOTIFICATION-GROUP, MODULE-COMPLIANCE, AGENT-CAPABILITIES, MACRO stubs).
> Cross-module OID resolution (`toJson(Map<String,String>)` resolving names
> across modules) and byte-for-byte pysmi parity are not yet verified — see
> `SPEC.md` for the full design and known gaps.

## Build

```
./gradlew build
```

Requires JDK 17. The ANTLR4 grammar in `src/main/antlr4` is compiled to a
parser at build time via the `antlr` Gradle plugin — no runtime codegen.

## API

```java
import za.co.integ.jsmiparser.MibParser;

String json = MibParser.toJson(mibText);
```

See `MibParser` for the multi-module (`toJson(Map<String,String>)`) and
`buildIndex` entry points used when MIBs need cross-module OID resolution.

## License

BSD-2-Clause, matching pysmi.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

A reusable Java 21 library that converts arbitrary POJO classes into Apache Avro `Schema` objects and Apache Parquet `MessageType` objects. Intended to be consumed as a Maven/Gradle dependency by other projects.

## Repository layout

The Maven project is **nested one level deep**: the actual project root (with `pom.xml` and `src/`) is `java-pojo-to-parquet-schema/java-pojo-to-parquet-schema/`. Run Maven commands from there, not the git root.

## Common commands

Run from `java-pojo-to-parquet-schema/java-pojo-to-parquet-schema/`:

```bash
mvn test                                           # run all tests
mvn -Dtest=PojoSchemaGeneratorTest#<method> test   # run a single test method
mvn package                                        # build jar + sources jar + javadoc jar into target/
mvn install                                        # publish to local ~/.m2
```

On this machine Maven lives at `D:\Tools\apache-maven-3.9.6\bin\mvn` and is not on `PATH`; use the absolute path if `mvn` is not resolvable.

## Architecture

The library is a thin layered pipeline. Depth matters more than breadth — each layer has one responsibility.

```
PojoSchemaGenerator   <- public facade (static shortcuts + builder)
      │
      ├── AvroSchemaBuilder     <- reflection walker; the real work lives here
      │     └── SchemaOptions   <- immutable config
      │
      └── ParquetSchemaBuilder  <- runs AvroSchemaBuilder, then delegates to
                                   parquet-avro's AvroSchemaConverter
```

Key invariants to preserve when editing:

- **Avro is the canonical representation.** Parquet output is always derived from the Avro schema via `AvroSchemaConverter`. Do not add a separate direct-to-Parquet code path — it would drift from the Avro mapping.
- **Parquet cannot represent cycles.** Self-referential Avro schemas round-trip fine, but `AvroSchemaConverter` stack-overflows on them. `ParquetSchemaBuilder.detectCycle` walks the Avro tree and throws `SchemaGenerationException` up front before calling the converter. Keep this check — removing it turns a clear error into a `StackOverflowError`.
- **parquet-avro 1.14+ is required** for `local-timestamp-millis` (used by `LocalDateTime`) to survive conversion; 1.13.x silently drops the logical annotation. Don't downgrade without re-checking the temporal-types tests.
- **`AvroSchemaBuilder` is stateful during a single `build(...)` call.** The `recordCache` enables self-referential schemas: a record is put in the cache *before* its fields are populated (via `Schema.createRecord` + `setFields`), so fields that reference the same type get the in-progress Schema object back. Do not reorder this.
- **`PojoSchemaGenerator` creates a new builder per call.** Treat the facade as thread-safe; treat `AvroSchemaBuilder` instances as single-use.
- **Field discovery walks the class hierarchy superclass-first**, skipping `static`, `transient`, synthetic, and `@SchemaIgnore` fields. If you change this, audit every test fixture under `src/test/java/io/github/jabhijeet/schema/fixtures/`.
- **Flattening is a post-pass over the already-built Avro record** (`AvroSchemaBuilder#flattenTopLevel`). Per-segment `@SchemaField` renames and `FieldNamingStrategy` are already baked into each intermediate field name before flatten runs, so the flattener only joins names with the configured separator. The flattener stops at array/map boundaries, composes nullability with OR along the path, and detects cycles with a `visited` set. The top-level flat record carries the custom props `pojoSchemaFlattened=true` and `pojoSchemaFlattenSeparator=<sep>`, and each leaf field carries `pojoSchemaFlattenSourcePath=<dotted.original.path>` — `JsonToAvroConverter` reads these at runtime to walk nested JSON into flat leaves without a new API.

## Type mapping rules

The mapping lives in `AvroSchemaBuilder#mapClass` and `#mapType`. Additions should go there and be paired with a test in `PojoSchemaGeneratorTest`.

- Primitives → required Avro types. Boxed/reference types → nullable by default (tunable via `SchemaOptions#nullableByDefault` or `@SchemaField(nullability = ...)`).
- `Optional<T>` → always nullable; unwraps to `T`.
- `BigDecimal` → Avro `bytes` with `decimal` logical type. Precision/scale come from `@SchemaDecimal`, else `SchemaOptions` defaults (38, 18).
- Dates/times use logical types: `LocalDate` → `date`, `LocalTime` → `time-micros`, `LocalDateTime` → `local-timestamp-millis`, `Instant`/`OffsetDateTime`/`ZonedDateTime`/`java.util.Date`/`java.sql.Timestamp` → `timestamp-millis`.
- `UUID` → string with `uuid` logical type. `BigInteger` → plain string (no native Avro equivalent).
- `Map<K,V>` requires `K == String` (Avro constraint). Non-string keys throw `SchemaGenerationException`.
- Unknown `java.*` types fail fast with a field-path-qualified error, rather than silently producing a broken schema.
- `flattenNestedRecords(true)` collapses nested records into path-joined leaf fields on the top-level record (default separator `_`, configurable via `flattenSeparator`). Flattening stops at `List`/`Map` boundaries, propagates nullability with OR along the path, and rejects collisions via `FlattenCollisionStrategy.THROW` (default) or suffixes them with `AUTO_RENAME`. Disabled by default — existing call sites are byte-identical.

## Annotations (public API)

`io.github.jabhijeet.schema.annotation`:
- `@SchemaField(name, doc, nullability)` — rename, document, or force `NULLABLE`/`REQUIRED` (default `AUTO` defers to `SchemaOptions`).
- `@SchemaIgnore` — exclude a field.
- `@SchemaDecimal(precision, scale)` — required on `BigDecimal` fields that need non-default precision/scale.

When adding new annotations, read them inside `AvroSchemaBuilder#buildField` or `#mapClass` (for type-shape annotations). Keep the retention `RUNTIME`.

## Testing conventions

Fixtures are plain public-field classes under `src/test/java/io/github/jabhijeet/schema/fixtures/`. Tests assert against the constructed Avro `Schema` tree and, for one round-trip sanity check, the derived `MessageType`. When a field is nullable, assertions use the helper `unwrapNullable(...)` rather than comparing the raw union.

# Release Notes

## 3.2.1

### Dependency fixes

- Excluded the legacy transitive `javax.annotation:javax.annotation-api`
  dependency from `parquet-avro`.
- No public API changes.

---

## 3.2.0

### Release tooling

- Upgraded `central-publishing-maven-plugin` from `0.5.0` to `0.11.0`.
- Added deployment-state verification guidance before retrying an upload. This
  prevents duplicate attempts when Maven fails while polling after Central has
  already published an immutable release.

---

## 3.1.0

### Changes

- Prepared the next minor release for Maven Central. Maven Central releases are
  immutable, so this version supersedes the already-published `3.0.0` artifact.

---

## 3.0.0

### Breaking changes

#### Iceberg I/O — filesystem path replaced by in-memory table

`IcebergIO` no longer uses `HadoopTables` and filesystem paths. All Iceberg data is now held in a `InMemoryTable` object backed by Iceberg's `InMemoryCatalog` and `InMemoryFileIO`. No Hadoop installation or `HADOOP_HOME` environment variable is required.

**Before (2.x):**
```java
JsonIO.appendToIcebergTable(json, schema, Path.of("build/table"));
List<String> rows = JsonIO.fromIcebergTable(Path.of("build/table"));
```

**After (3.0):**
```java
IcebergIO.InMemoryTable table = IcebergIO.createTable(schema);
JsonIO.appendToIcebergTable(json, schema, table);
List<String> rows = JsonIO.fromIcebergTable(table);

// Or create + append in one call:
IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(json, schema);
```

`JsonIO.appendToIcebergTableAll` and `JsonIO.fromIcebergTable` have the same signature change (`Path` → `InMemoryTable`). The `IcebergIO` low-level API mirrors the change:

```java
// Before: IcebergIO.append(Path, Schema, Collection)
// After:
IcebergIO.InMemoryTable table = IcebergIO.createTable(avroSchema);
IcebergIO.append(table, avroSchema, records);
List<org.apache.iceberg.data.Record> rows = IcebergIO.readAll(table);
List<GenericRecord> avroRows = IcebergIO.readAllAsAvro(table);
```

#### `TimezoneHandling.PRESERVE` and `SYSTEM_DEFAULT` now produce distinct schemas

In 2.x all three `TimezoneHandling` values produced identical schemas (identical to `UTC`). In 3.0 they are fully implemented:

| Mode | Schema type for `OffsetDateTime` / `ZonedDateTime` |
|------|-----------------------------------------------------|
| `UTC` (default) | `timestamp-millis/micros` (unchanged) |
| `PRESERVE` | `string` (ISO-8601 with offset, e.g. `2025-01-02T10:30:00+05:30`) |
| `SYSTEM_DEFAULT` | `local-timestamp-millis/micros` |

If you previously set `PRESERVE` or `SYSTEM_DEFAULT` expecting UTC behavior, either switch to `UTC` or migrate data accordingly.

#### `SchemaGenerationException` is now `final`

Any code that subclassed `SchemaGenerationException` will no longer compile.

---

### New features

#### Schema-free JSON conversion to Avro, Parquet, and Iceberg

`JsonIO` now infers an Avro record schema directly from a JSON object or JSON
array. Callers no longer need to generate or provide a schema for common
ingestion flows:

```java
String json = """
    {"id":"evt-1","active":true,"score":42,"labels":["new","priority"]}
    """;

GenericRecord record = JsonIO.toRecord(json);
byte[] avroBytes = JsonIO.toAvroBytes(json);
byte[] parquetBytes = JsonIO.toParquetBytes(json);
IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(json);
```

Top-level arrays are supported by the batch APIs. Their record schema is
inferred from the first element and reused for every row:

```java
String batch = """
    [
      {"id":"evt-1","active":true,"score":10},
      {"id":"evt-2","active":false,"score":25}
    ]
    """;

List<GenericRecord> records = JsonIO.toRecords(batch);
byte[] avroBatch = JsonIO.toAvroBytesAll(batch);
byte[] parquetBatch = JsonIO.toParquetBytesAll(batch);
IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(batch);
```

The new `json.infer` package separates inference from conversion and I/O:

- `JsonSchemaInferrer` — extension point for inference strategies.
- `DefaultJsonSchemaInferrer` — default recursive JSON-to-Avro implementation.
- `SchemaInferenceOptions` — immutable options for root name, nested-array
  sample size, maximum depth, null handling, and field naming.
- `SchemaInferenceException` — reports invalid JSON and unsupported inference
  conditions.
- `InferredSchemaCache` — bounded, thread-safe LRU storage for inferred schemas.

Default scalar mapping is boolean → Avro `boolean`, integral number → `long`,
fractional number → `double`, and string → `string`. Objects become nested
records and arrays infer their element type from sampled values. The inferrer
does not guess UUID, decimal, date, or timestamp logical types from strings;
use the existing explicit-schema overloads when semantic logical types or a
stable external schema contract are required.

Batch elements must be compatible with the schema inferred from the first
element. Later shape drift is rejected by the existing path-qualified
`JsonConversionException` validation.

#### Avro and Parquet `InputStream` output

Avro and Parquet conversions can now return caller-owned, byte-array-backed
input streams suitable for synchronous object-storage and HTTP upload APIs:

```java
InputStream avro = JsonIO.toAvroInputStream(json);
InputStream parquet = JsonIO.toParquetInputStream(json);
InputStream avroBatch = JsonIO.toAvroInputStreamAll(batch);
InputStream parquetBatch = JsonIO.toParquetInputStreamAll(batch);
```

Explicit-schema overloads are also available, and low-level callers can use
`AvroIO.toInputStream(schema, records)` or
`ParquetIO.toInputStream(schema, records)`. Each method materializes the complete
file in memory before returning the stream, ensuring that Avro headers and
Parquet footers are complete.

Iceberg tables do not have an equivalent single-stream representation because
they consist of metadata, manifests, snapshots, and data files. Upload a
standalone Parquet stream only when a Parquet object, rather than an Iceberg
table, is the intended result. Persistent Iceberg-on-S3 deployments must use an
Iceberg catalog and S3-backed `FileIO`.

#### Fully in-memory Iceberg I/O — no Hadoop required

`IcebergIO` uses `InMemoryCatalog` + `InMemoryFileIO` from `iceberg-core` and writes data in Parquet format via `iceberg-parquet` (uses `parquet-column`, not `parquet-hadoop`). No filesystem, no `HADOOP_HOME`.

#### `TimezoneHandling` fully implemented

`PRESERVE` maps `OffsetDateTime`/`ZonedDateTime` to Avro `string` (ISO-8601 with offset), preserving the exact timezone offset through the round-trip. `SYSTEM_DEFAULT` maps UTC-normalizing types to `local-timestamp-millis/micros` for legacy ETL pipelines with a fixed system timezone.

#### `LocalDateTime` respects `TimestampPrecision`

In 2.x, `LocalDateTime` always emitted `local-timestamp-millis` regardless of the configured `TimestampPrecision`. In 3.0 it correctly emits `local-timestamp-millis` (MILLIS) or `local-timestamp-micros` (MICROS/NANOS).

#### `generateParquet` / `generateIceberg` reuse cached Avro schema

Both methods now call `generateAvro` internally, so a warm Avro cache is shared when all three schema types are generated for the same class. Previously each method built the Avro schema independently.

#### Java 22+ supported

The Maven Enforcer requirement changed from `[21,22)` to `[21,)`. The library now builds and runs on Java 22 and later.

---

### Bug fixes

- **`IcebergIO.DataWriter.write()`** — the Iceberg 1.6+ API renamed `add()` to `write()`. This was a hidden compile error that only surfaced after dependency convergence issues were resolved.
- **Cache stats thread safety** — `LruSchemaCache.stats()` is now `synchronized` to ensure hit, miss, and size counts are read atomically.
- **Dead code removed** — `GenericRecordToJsonConverter.nonNullBranch` (unreachable private method) removed.
- **`TimestampPrecision.NANOS` switch** — dead `default:` branch that silently returned millis was removed; the exhaustive condition is now explicit.
- **Dependency convergence** — multiple transitive Jackson, SLF4J, Aircompressor, and ORC version conflicts between `parquet-avro`, `iceberg-core`, and `iceberg-data` are resolved via targeted `<exclusion>` entries.
- **pom.xml dev email** — placeholder `jabhijeet@example.com` replaced with real address.

---

### Dependency updates

| Artifact | 2.3.0 | 3.0.0 |
|----------|-------|-------|
| `org.apache.avro:avro` | 1.12.1 | 1.12.1 |
| `org.apache.parquet:parquet-avro` | 1.16.0 | 1.16.0 |
| `org.apache.iceberg:iceberg-core` | 1.10.1 | 1.10.1 |
| `org.apache.iceberg:iceberg-data` | *(removed)* | 1.10.1 |
| `org.apache.iceberg:iceberg-parquet` | *(not present)* | 1.10.1 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.1 | 2.22.1 |
| `org.apache.hadoop:hadoop-common` | 3.4.3 optional | 3.4.3 optional |
| `org.apache.hadoop:hadoop-mapreduce-client-core` | 3.4.3 optional | 3.4.3 optional |

`iceberg-data` and `iceberg-parquet` are now required (not optional) because `IcebergIO` uses them at runtime for in-memory table writes and reads.
`hadoop-common` and `hadoop-mapreduce-client-core` remain `optional`; they satisfy compile-time references in `parquet-avro` and allow `ParquetIO` to run without a Hadoop installation.

---

### Known limitations

- **`LocalDateTime` in Iceberg with MILLIS precision** — Iceberg 1.10.1's `AvroSchemaUtil` does not recognise the `local-timestamp-millis` Avro logical type and maps it to a plain `LONG` in the Iceberg schema. Use `TimestampPrecision.MICROS` if you need `LocalDateTime` to appear as `TIMESTAMP` in Iceberg.
- **Parquet / Iceberg data I/O is in-memory only** — there is no filesystem-backed variant. For filesystem or cloud storage, use the Iceberg or Parquet client libraries directly with the schemas produced by `PojoSchemaGenerator`.

---

## 2.3.0 and earlier

See git history.

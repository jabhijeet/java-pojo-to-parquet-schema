# java-pojo-to-parquet-schema

![Maven Central](https://img.shields.io/maven-central/v/io.github.jabhijeet/java-pojo-to-parquet-schema?style=for-the-badge)

Zero-boilerplate Java library — reflection on a POJO produces Avro/Parquet/Iceberg schemas and in-memory binary data. No code generation, no schema files, no Hadoop installation.

**Three capabilities:**

1. **POJO → Schema** — reflect a Java class into Avro `Schema`, Parquet `MessageType`, and Iceberg `Schema`.
2. **JSON → Avro/Parquet bytes** — convert JSON documents to in-memory binary. No `HADOOP_HOME`.
3. **JSON → Iceberg table (in-memory)** — append JSON rows to a fully in-memory Iceberg table backed by `InMemoryCatalog`. No filesystem, no `HADOOP_HOME`.

---

## Requirements

- Java **21+**
- Maven or Gradle

## Installation

**Maven:**
```xml
<dependency>
    <groupId>io.github.jabhijeet</groupId>
    <artifactId>java-pojo-to-parquet-schema</artifactId>
    <version>3.0.0</version>
</dependency>
```

**Gradle (Kotlin DSL):**
```kotlin
implementation("io.github.jabhijeet:java-pojo-to-parquet-schema:3.0.0")
```

**Gradle (Groovy DSL):**
```groovy
implementation 'io.github.jabhijeet:java-pojo-to-parquet-schema:3.0.0'
```

### Dependencies

| Artifact                                       | Version | Scope    |
| ---------------------------------------------- | ------- | -------- |
| `org.apache.avro:avro`                         | 1.12.1  | compile  |
| `org.apache.parquet:parquet-avro`              | 1.17.0  | compile  |
| `org.apache.iceberg:iceberg-core`              | 1.10.1  | compile  |
| `org.apache.iceberg:iceberg-data`              | 1.10.1  | compile  |
| `org.apache.iceberg:iceberg-parquet`           | 1.10.1  | compile  |
| `com.fasterxml.jackson.core:jackson-databind`  | 2.19.4  | compile  |
| `org.slf4j:slf4j-api`                          | 2.0.17  | compile  |
| `org.apache.hadoop:hadoop-common`              | 3.3.6   | optional |
| `org.apache.hadoop:hadoop-mapreduce-client-core` | 3.3.6 | optional |

Hadoop deps are `optional` — they satisfy compile-time type references in `parquet-avro` and allow `ParquetIO` to run. No `HADOOP_HOME` environment variable is required. Schema-generation-only consumers can exclude them entirely.

---

## Use case 1 — POJO → Avro / Parquet / Iceberg schema

### Quick start

```java
import io.github.jabhijeet.schema.PojoSchemaGenerator;
import org.apache.avro.Schema;
import org.apache.parquet.schema.MessageType;

public class Person {
    public String name;
    public int age;
    public boolean active;
}

Schema         avro    = PojoSchemaGenerator.toAvro(Person.class);
MessageType    parquet = PojoSchemaGenerator.toParquet(Person.class);
org.apache.iceberg.Schema iceberg = PojoSchemaGenerator.toIceberg(Person.class);

System.out.println(avro.toString(true));   // pretty-printed Avro JSON
System.out.println(parquet.toString());    // Parquet schema DSL
System.out.println(iceberg);              // Iceberg schema with field IDs
```

### Configuring the generator

```java
import io.github.jabhijeet.schema.*;

PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
        .namespace("com.example.schemas")       // overrides package-derived namespace
        .nullableByDefault(false)               // reference fields become required
        .defaultDecimal(18, 4)                  // default BigDecimal precision/scale
        .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
        .timestampPrecision(TimestampPrecision.MICROS)
        .timezoneHandling(TimezoneHandling.UTC) // see Timezone section
        .preserveDefaultValues(true)            // carry Java initializers into schema
        .flattenNestedRecords(true)             // collapse nested POJOs to leaf columns
        .flattenSeparator("_")
        .flattenCollisionStrategy(FlattenCollisionStrategy.THROW) // or AUTO_RENAME
        .cacheStrategy(CacheStrategy.LRU)       // cache generated schemas
        .cacheSize(200)
        .build();

Schema    avro    = gen.generateAvro(Order.class);
MessageType parquet = gen.generateParquet(Order.class);
org.apache.iceberg.Schema iceberg = gen.generateIceberg(Order.class);
```

### Supported Java types

| Java type | Avro logical type | Notes |
|-----------|-------------------|-------|
| `boolean` / `Boolean` | `boolean` | |
| `byte` / `short` / `int` / boxed | `int` | |
| `long` / `Long` | `long` | |
| `float` / `double` / boxed | `float` / `double` | |
| `char` / `Character` / `String` / `CharSequence` | `string` | |
| `byte[]` / `ByteBuffer` | `bytes` | |
| `UUID` | `string` + `uuid` | |
| `BigInteger` | `string` | No native Avro equivalent |
| `BigDecimal` | `bytes` + `decimal` | Requires `@SchemaDecimal` or defaults |
| `LocalDate` / `java.sql.Date` | `int` + `date` | |
| `LocalTime` | `long` + `time-micros` | |
| `LocalDateTime` | `long` + `local-timestamp-millis/micros` | |
| `Instant` / `Date` / `Timestamp` | `long` + `timestamp-millis/micros` | UTC epoch |
| `OffsetDateTime` / `ZonedDateTime` | see TimezoneHandling | UTC by default |
| `enum` | `enum` | |
| `Collection<E>` | `array<E>` | |
| `Map<String, V>` | `map<V>` | Non-String keys rejected |
| `Optional<T>` | `union[null, T]` | Always nullable |
| Nested POJO | `record` | Self-referential supported |
| `T[]` | `array<T>` | |

Unknown `java.*` types throw `SchemaGenerationException` with field path.

### Field naming strategies

`FieldNamingStrategy` rewrites Java field names before they land in the schema. The
camelCase splitter is acronym-aware: `XMLParser` → `xml_parser`, not `x_m_l_parser`.

| Strategy | `xmlParser` | `userID` | `httpURLConnection` |
|----------|-------------|----------|---------------------|
| `AS_IS` (default) | `xmlParser` | `userID` | `httpURLConnection` |
| `SNAKE_CASE` | `xml_parser` | `user_id` | `http_url_connection` |
| `UPPER_SNAKE_CASE` | `XML_PARSER` | `USER_ID` | `HTTP_URL_CONNECTION` |
| `LOWER_CAMEL_CASE` | `xmlParser` | `userId` | `httpUrlConnection` |
| `KEBAB_CASE`* | `xml_parser` | `user_id` | `http_url_connection` |

\* Avro field names cannot contain hyphens; `KEBAB_CASE` produces underscores. Use `@SchemaField(name="...")` for a literal name.

### Timestamp precision and timezone

**Precision** — applies to `Instant`, `OffsetDateTime`, `ZonedDateTime`, `Date`, `Timestamp`, and `LocalDateTime`:

- `TimestampPrecision.MILLIS` (default) → `timestamp-millis` / `local-timestamp-millis`
- `TimestampPrecision.MICROS` → `timestamp-micros` / `local-timestamp-micros`
- `TimestampPrecision.NANOS` → falls back to `MICROS` (Avro has no nanosecond logical type)

**Timezone handling** — controls how `OffsetDateTime` and `ZonedDateTime` are stored:

| Mode | Schema type | Offset preserved? | Use when |
|------|-------------|-------------------|----------|
| `UTC` (default) | `timestamp-millis/micros` | No | Standard analytics pipelines |
| `PRESERVE` | `string` (ISO-8601 with offset, e.g. `2025-01-02T10:30:00+05:30`) | Yes | Offset must survive round-trip |
| `SYSTEM_DEFAULT` | `local-timestamp-millis/micros` | No | Legacy ETL with fixed system TZ |

`Instant`, `Date`, and `Timestamp` have no offset; they always map to UTC timestamps regardless of mode.

```java
// OffsetDateTime → ISO string (offset preserved)
PojoSchemaGenerator preserve = PojoSchemaGenerator.builder()
        .timezoneHandling(TimezoneHandling.PRESERVE)
        .build();

// OffsetDateTime → local-timestamp-millis (no UTC adjustment)
PojoSchemaGenerator sysDefault = PojoSchemaGenerator.builder()
        .timezoneHandling(TimezoneHandling.SYSTEM_DEFAULT)
        .build();
```

### Nullability rules

1. Primitives (`int`, `long`, `boolean`, …) are always required.
2. `Optional<T>` is always nullable.
3. `@SchemaField(nullability = NULLABLE | REQUIRED)` overrides any default.
4. Otherwise reference types follow `nullableByDefault()` (default `true`).

A nullable field becomes `union[null, T]` with `null` as default. With `preserveDefaultValues(true)` and a non-null Java initializer, union order flips to `[T, null]` so the default validates.

### Annotations

All annotations live in `io.github.jabhijeet.schema.annotation`.

#### `@SchemaField` — rename, document, or force nullability

```java
import io.github.jabhijeet.schema.annotation.SchemaField;
import io.github.jabhijeet.schema.annotation.SchemaField.Nullability;

public class Contact {
    @SchemaField(name = "email_address", doc = "Primary contact email")
    public String email;

    @SchemaField(nullability = Nullability.REQUIRED)
    public String tenantId;
}
```

#### `@SchemaIgnore` — exclude a field

```java
import io.github.jabhijeet.schema.annotation.SchemaIgnore;

public class Session {
    public String id;

    @SchemaIgnore
    public String internalCache;   // not in the schema
}
```

`transient` and `static` fields are excluded automatically.

#### `@SchemaDecimal` — BigDecimal precision/scale

```java
import io.github.jabhijeet.schema.annotation.SchemaDecimal;
import java.math.BigDecimal;

public class Invoice {
    @SchemaDecimal(precision = 12, scale = 2)
    public BigDecimal total;

    // default precision/scale from SchemaOptions:
    public BigDecimal taxAmount;
}
```

### Flattening nested records

Set `flattenNestedRecords(true)` to collapse nested POJOs into path-joined leaf columns. Useful for analytics tools that prefer flat/columnar schemas.

```java
public class Inner { public String c; public int n; }
public class Outer { public Inner inner; public String top; }

Schema flat = PojoSchemaGenerator.builder()
        .flattenNestedRecords(true)
        .build()
        .generateAvro(Outer.class);
// Fields: inner_c (string), inner_n (int), top (string)
```

Rules:
- **Separator** — `flattenSeparator(String)` defaults to `"_"`. Only `[A-Za-z0-9_]+` accepted.
- **Stops at collections/maps** — `List<Inner>` stays as `array<record>`.
- **Nullability propagates with OR** — leaf is nullable if any ancestor on the path is nullable.
- **Cycles throw** `SchemaGenerationException` up front; self-reference through a collection is allowed.
- **Collision strategy** — `THROW` (default) or `AUTO_RENAME` (adds `__1`, `__2` suffixes).
- **Self-describing** — the flat record carries `pojoSchemaFlattened=true` and each leaf carries `pojoSchemaFlattenSourcePath`; `JsonToGenericRecordConverter` uses these to accept both nested and flat JSON keys.

### Self-referential and cyclic types

Avro supports recursive records (`Node → List<Node>`). Parquet and Iceberg are finite trees — generating their schema from a cyclic POJO throws `SchemaGenerationException` up front. Break the cycle with `@SchemaIgnore` on the back-edge field.

### Caching

In long-running services that generate schemas for the same classes repeatedly, enable LRU caching:

```java
PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
        .cacheStrategy(CacheStrategy.LRU)
        .cacheSize(500)
        .build();

Schema avro1 = gen.generateAvro(Order.class);
Schema avro2 = gen.generateAvro(Order.class); // returned from cache, same object

System.out.println(avro1 == avro2);          // true
System.out.println(gen.cacheStats());        // hit/miss counters, hit ratio
```

`generateParquet` and `generateIceberg` call `generateAvro` internally, so the Avro schema is built once and reused across all three format generators per unique `(Class, SchemaOptions)` pair.

---

## Use case 2 — JSON → Avro / Parquet bytes (in-memory)

No filesystem, no `HADOOP_HOME`. All I/O is in-memory via custom `OutputFile`/`InputFile` implementations.

### One-call conversion with `JsonIO`

```java
import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.json.JsonIO;
import org.apache.avro.Schema;

Schema schema = PojoSchemaGenerator.toAvro(Order.class);

String json = "{\"orderId\":\"550e8400-...\",\"amount\":\"99.99\", ...}";

// Single document → Avro Object Container File bytes
byte[] avroBytes = JsonIO.toAvroBytes(json, schema);

// Single document → Parquet bytes (Snappy-compressed)
byte[] parquetBytes = JsonIO.toParquetBytes(json, schema);

// JSON array → bytes containing all records
String batch = "[{...},{...}]";
byte[] avroBatch    = JsonIO.toAvroBytesAll(batch, schema);
byte[] parquetBatch = JsonIO.toParquetBytesAll(batch, schema);

// Size-guarded variants (rejects input exceeding limit before parsing)
byte[] avroSafe = JsonIO.toAvroBytes(json, schema, 10 * 1024 * 1024); // 10 MB limit
```

### Reading back with `AvroIO` / `ParquetIO`

```java
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import org.apache.avro.generic.GenericRecord;
import java.util.List;

List<GenericRecord> avroRecords    = AvroIO.readAll(avroBytes);
List<GenericRecord> parquetRecords = ParquetIO.readAll(parquetBytes);

// Single record shortcut
GenericRecord first = AvroIO.fromBytes(avroBytes);

// From InputStream (stream is closed on return)
List<GenericRecord> fromStream = AvroIO.readAll(inputStream);
```

### Bytes → JSON (full round-trip)

```java
// Avro bytes → JSON strings
List<String> avroJson    = JsonIO.fromAvroBytes(avroBytes);

// Parquet bytes → JSON strings
List<String> parquetJson = JsonIO.fromParquetBytes(parquetBytes);

// GenericRecord → JSON
String json = JsonIO.fromRecord(record);
```

### Writing `GenericRecord`s directly

```java
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

GenericRecord record = new GenericData.Record(schema);
record.put("orderId", "550e8400-...");
record.put("amount", /* ByteBuffer decimal */ ...);

byte[] avroBytes    = AvroIO.toBytes(schema, record);
byte[] parquetBytes = ParquetIO.toBytes(schema, record);

// With explicit codec
byte[] parquetGzip = ParquetIO.toBytes(schema, records,
        org.apache.parquet.hadoop.metadata.CompressionCodecName.GZIP);

// To an OutputStream
AvroIO.writeTo(schema, List.of(record), outputStream);
ParquetIO.writeTo(schema, List.of(record), outputStream);
```

### Complete example

```java
import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.json.JsonIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import io.github.jabhijeet.schema.annotation.SchemaDecimal;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderPojo {
    public UUID orderId;
    public String customerId;
    @SchemaDecimal(precision = 12, scale = 2)
    public BigDecimal amount;
    public Instant placedAt;
    public List<String> tags;
}

Schema schema = PojoSchemaGenerator.toAvro(OrderPojo.class);

String json = """
    {
      "orderId":    "550e8400-e29b-41d4-a716-446655440000",
      "customerId": "CUST-001",
      "amount":     "199.98",
      "placedAt":   "2025-06-15T10:30:00Z",
      "tags":       ["urgent", "vip"]
    }
    """;

// Write Parquet
byte[] parquetBytes = JsonIO.toParquetBytes(json, schema);

// Read back
List<GenericRecord> records = ParquetIO.readAll(parquetBytes);
System.out.println(records.get(0).get("customerId")); // CUST-001

// Round-trip to JSON
List<String> jsons = JsonIO.fromParquetBytes(parquetBytes);
System.out.println(jsons.get(0));
// → {"orderId":"550e8400-...","customerId":"CUST-001","amount":"199.98",...}
```

### JSON type mapping

| Avro schema type | Accepted JSON forms |
|------------------|---------------------|
| `null` | `null` |
| `boolean` | `true` / `false` |
| `int` | JSON integer in `[-2^31, 2^31)` |
| `long` | JSON integer or numeric string (supports 64-bit IDs > JS safe-integer range) |
| `float` / `double` | JSON number |
| `string` | JSON string; numbers/booleans coerced via `.toString()` |
| `string` + `uuid` | Canonical UUID string, validated |
| `bytes` / `fixed` | Standard or URL-safe Base64 string |
| `bytes` + `decimal` | JSON number or numeric string (scale must match schema exactly) |
| `int` + `date` | ISO-8601 date (`2025-06-15`) or days-since-epoch integer |
| `long` + `time-micros` | ISO-8601 time (`10:30:00.123456`) or micros-since-midnight integer |
| `long` + `timestamp-millis` | ISO-8601 instant with offset (`2025-06-15T10:30:00Z`) or epoch millis |
| `long` + `local-timestamp-millis` | ISO-8601 local datetime (`2025-06-15T10:30:00`) or epoch millis |
| `enum` | JSON string matching one of the schema's symbols |
| `array` | JSON array; elements recursed against element schema |
| `map` | JSON object; all keys treated as strings |
| `record` | JSON object; unknown fields ignored |
| `union` | First branch whose shape fits the JSON value |

### Missing and null fields

- Missing field → schema default value if defined.
- Missing field + nullable schema → `null`.
- Missing field + required schema → `JsonConversionException` with JSON-pointer path (e.g. `$.order.items[2].price`).

### Error handling

```java
import io.github.jabhijeet.schema.json.JsonConversionException;

try {
    byte[] bytes = JsonIO.toAvroBytes(json, schema);
} catch (JsonConversionException e) {
    System.err.println(e.path());     // e.g. $.customer.zip
    System.err.println(e.getMessage());
}
```

---

## Use case 3 — JSON → Iceberg table (in-memory)

Fully in-memory: uses Iceberg's `InMemoryCatalog` and `InMemoryFileIO`. Data is written in Parquet format via `iceberg-parquet` (uses `parquet-column`, not `parquet-hadoop`). No filesystem, no `HADOOP_HOME`.

### Quick start

```java
import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.json.JsonIO;
import io.github.jabhijeet.schema.io.IcebergIO;
import org.apache.avro.Schema;

public class OrderEvent {
    public String orderId;
    public long createdAtMs;
    public boolean paid;
}

Schema schema = PojoSchemaGenerator.toAvro(OrderEvent.class);

// Create a table and append one record
IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(
    "{\"orderId\":\"ord-1001\",\"createdAtMs\":1716200000000,\"paid\":true}",
    schema);

// Read back as JSON
List<String> rows = JsonIO.fromIcebergTable(table);
System.out.println(rows.get(0));
// → {"orderId":"ord-1001","createdAtMs":1716200000000,"paid":true}
```

### Batch append

```java
IcebergIO.InMemoryTable table = IcebergIO.createTable(schema);

String batch = """
    [
      {"orderId":"ord-1001","createdAtMs":1716200000000,"paid":true},
      {"orderId":"ord-1002","createdAtMs":1716200005000,"paid":false}
    ]
    """;
JsonIO.appendToIcebergTableAll(batch, schema, table);

List<String> rows = JsonIO.fromIcebergTable(table);
System.out.println(rows.size()); // 2
```

### Multiple appends accumulate snapshots

```java
IcebergIO.InMemoryTable table = IcebergIO.createTable(schema);
JsonIO.appendToIcebergTable(jsonA, schema, table);  // snapshot 1
JsonIO.appendToIcebergTable(jsonB, schema, table);  // snapshot 2

List<String> all = JsonIO.fromIcebergTable(table);  // both rows visible
```

### Read as Iceberg Records

```java
import org.apache.iceberg.data.Record;

List<Record> iceRecords = IcebergIO.readAll(table);
System.out.println(iceRecords.get(0).getField("orderId")); // ord-1001

// Or as Avro GenericRecords
List<GenericRecord> avroRecords = IcebergIO.readAllAsAvro(table);
```

### Low-level table API

```java
import org.apache.avro.generic.GenericData;

// Create from Avro schema (auto-converts to Iceberg schema)
IcebergIO.InMemoryTable table = IcebergIO.createTable(avroSchema);

// Or create directly from an Iceberg schema
org.apache.iceberg.Schema icebergSchema = PojoSchemaGenerator.toIceberg(OrderEvent.class);
IcebergIO.InMemoryTable table2 = IcebergIO.createTable(icebergSchema);

// Append Avro GenericRecords
GenericRecord record = new GenericData.Record(avroSchema);
record.put("orderId", "ord-3001");
record.put("createdAtMs", 1716200010000L);
record.put("paid", true);
IcebergIO.append(table, avroSchema, List.of(record));

// Table schema
org.apache.iceberg.Schema schema = table.schema();
```

### Nested JSON → Iceberg → JSON

```java
public class CustomerOrder {
    public String orderId;
    public Address shippingAddress;
}
public class Address { public String city; public String country; }

Schema avroSchema = PojoSchemaGenerator.toAvro(CustomerOrder.class);
IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(
    "{\"orderId\":\"ord-2001\",\"shippingAddress\":{\"city\":\"Paris\",\"country\":\"FR\"}}",
    avroSchema);

List<String> rows = JsonIO.fromIcebergTable(table);
System.out.println(rows.get(0));
// → {"orderId":"ord-2001","shippingAddress":{"city":"Paris","country":"FR"}}
```

### Pipeline summary

```
POJO ──► Avro schema ──► Iceberg schema (AvroSchemaUtil)
JSON ──► Avro GenericRecord ──► Iceberg Record
Iceberg Record ──► Parquet bytes (iceberg-parquet) ──► InMemoryFileIO
InMemoryFileIO ──► IcebergGenerics.read ──► Iceberg Record
Iceberg Record ──► Avro GenericRecord ──► JSON
```

---

## Security

- **Apache Avro 1.12.1** — addresses CVE-2025-33042 (fixed in 1.11.5+)
- **Apache Parquet 1.17.0** — addresses CVE-2025-30065 and CVE-2025-46762 (fixed in 1.15.2+)

For production deployments:
- Scan dependencies with OWASP Dependency Check, Snyk, or Dependabot.
- Run `mvn dependency:tree` to verify transitive dependencies.
- Stay current with security advisories for Avro, Parquet, Hadoop, and Jackson.

Report security vulnerabilities privately via GitHub Security Advisories.

## Memory bounds and streaming

All `JsonIO` methods load the full JSON document into memory, parse it into a Jackson `JsonNode` tree, then convert to Avro `GenericRecord`. This is fast for moderate-sized documents but has limits:

| Component | Memory overhead |
|-----------|-----------------|
| JSON string | ~2× original size |
| Jackson tree | ~3–5× JSON size |
| Avro GenericRecord | ~2–4× JSON size |
| Parquet in-memory buffer | +1–2× |

**For large inputs:**
- Use size-limited overloads: `JsonIO.toAvroBytes(json, schema, maxBytes)`
- For streaming workloads, build `GenericRecord` objects incrementally with Jackson's streaming API and write directly with `AvroIO.writeTo(schema, records, outputStream)`.

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).

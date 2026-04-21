# java-pojo-to-parquet-schema

![Maven Central](https://img.shields.io/maven-central/v/io.github.jabhijeet/java-pojo-to-parquet-schema?style=for-the-badge)

A zero-boilerplate Java  library with two capabilities:

1. **POJO → Schema**: Convert plain Java classes into **Apache Avro** `Schema` and **Apache Parquet** `MessageType` objects through reflection. No code generation, no external schema files.

2. **JSON → Avro/Parquet bytes**: Convert raw JSON documents into Avro or Parquet binary, fully in-memory. No filesystem, no `HADOOP_HOME`.

## Why

Most data-pipeline tools (Avro, Parquet, Spark, Flink, Kafka Connect) need schemas and binary data. This library:

- Reads your Java classes at runtime and produces Avro/Parquet schemas automatically.
- Accepts JSON input and converts it to schema-validated Avro or Parquet bytes without touching disk.
- Works as a standalone Maven/Gradle dependency — usable on any platform, no Hadoop installation needed.

## Requirements

- Java **21+**
- Maven or Gradle

## Installation

**Maven coordinates:**
```xml
<dependency>
    <groupId>io.github.jabhijeet</groupId>
    <artifactId>java-pojo-to-parquet-schema</artifactId>
    <version>2.0.0</version>
</dependency>
```

**Gradle (Kotlin DSL):**
```kotlin
implementation("io.github.jabhijeet:java-pojo-to-parquet-schema:2.0.0")
```

**Gradle (Groovy DSL):**
```groovy
implementation 'io.github.jabhijeet:java-pojo-to-parquet-schema:2.0.0'
```

Transitive dependencies pulled in:

| Dependency                                    | Version |
| --------------------------------------------- | ------- |
| `org.apache.avro:avro`                        | 1.11.4  |
| `org.apache.parquet:parquet-avro`             | 1.15.0  |
| `com.fasterxml.jackson.core:jackson-databind` | 2..2  |
| `org.slf4j:slf4j-api`                         | 2.0.13  |

`hadoop-common` and `hadoop-mapreduce-client-core` are declared as `optional` — they are needed at runtime only when you use the Parquet I/O helpers. For schema-generation-only use cases they are not required.

---

## Use case 1 — POJO to Avro/Parquet schema

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

Schema avro         = PojoSchemaGenerator.toAvro(Person.class);
MessageType parquet = PojoSchemaGenerator.toParquet(Person.class);

System.out.println(avro.toString(true));   // pretty-printed Avro JSON
System.out.println(parquet.toString());    // Parquet schema DSL
```

### Configuring the generator

```java
PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
        .namespace("com.example.schemas")              // overrides package-derived namespace
        .nullableByDefault(false)                       // reference fields become required
        .defaultDecimal(18, 4)                          // default BigDecimal precision/scale
        .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
        .timestampPrecision(TimestampPrecision.MICROS)
        .timezoneHandling(TimezoneHandling.UTC)
        .preserveDefaultValues(true)                    // carry Java initializers into the schema
        .cacheStrategy(CacheStrategy.LRU)               // optional; caches generated schemas
        .cacheSize(200)
        .build();

Schema schema     = gen.generateAvro(Order.class);
MessageType mt    = gen.generateParquet(Order.class);
String avroJson   = gen.generateAvroJson(Order.class, /* pretty */ true);
String parquetDsl = gen.generateParquetString(Order.class);
```

#### Field naming strategies

`FieldNamingStrategy` rewrites Java field names before they land in the schema. The
`camelCase` splitter is acronym-aware, so `XMLParser` becomes `xml_parser` rather than
`x_m_l_parser`, and `httpURLConnection` becomes `http_url_connection`.

| Strategy            | `xmlParser`    | `userID`   | `httpURLConnection`    |
| ------------------- | -------------- | ---------- | ---------------------- |
| `AS_IS` (default)   | `xmlParser`    | `userID`   | `httpURLConnection`    |
| `SNAKE_CASE`        | `xml_parser`   | `user_id`  | `http_url_connection`  |
| `UPPER_SNAKE_CASE`  | `XML_PARSER`   | `USER_ID`  | `HTTP_URL_CONNECTION`  |
| `LOWER_CAMEL_CASE`  | `xmlParser`    | `userId`   | `httpUrlConnection`    |
| `KEBAB_CASE`        | `xml-parser`*  | `user-id`* | `http-url-connection`* |

\* Avro field names cannot contain hyphens; `KEBAB_CASE` falls back to underscores
when generating Avro. Use `@SchemaField(name = "...")` on a specific field if you
need a literal name that differs from the strategy output.

#### Timestamp precision and timezone

- `TimestampPrecision.MILLIS` (default) / `MICROS` controls the logical type emitted
  for `Instant`, `OffsetDateTime`, `ZonedDateTime`, `java.util.Date`,
  `java.sql.Timestamp`, and `LocalDateTime`. `TimestampPrecision.NANOS` is accepted
  for forward compatibility but Avro has no nanosecond logical type, so it currently
  falls back to `MICROS`.
- `TimezoneHandling.UTC` (default) emits the standard UTC-based `timestamp-*` logical
  types. `PRESERVE` and `SYSTEM_DEFAULT` are reserved for future use — Avro does not
  have an offset-preserving timestamp logical type, so they currently generate the
  same schema as `UTC`.

#### Preserving Java default values

With `preserveDefaultValues(true)`, the generator reads the Java-level initializer
from a fresh instance of the POJO and writes it into the Avro schema as the field's
`default` value. For a nullable field with a non-null initializer, the generated
union is ordered `[T, null]` (non-null branch first) so the default validates; for
nullable fields without an initializer the union stays `[null, T]` with `null` as
the default. `Float` fields are emitted as `Float` rather than `Double` in the
default value so they agree with the schema's `FLOAT` type.

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

`nullability` values: `AUTO` (default — defers to `SchemaOptions`), `NULLABLE`, `REQUIRED`.

#### `@SchemaIgnore` — skip a field entirely

```java
import io.github.jabhijeet.schema.annotation.SchemaIgnore;

public class Session {
    public String id;

    @SchemaIgnore
    public String internalCache;   // not included in the schema
}
```

`transient` and `static` fields are skipped automatically without an annotation.

#### `@SchemaDecimal` — declare `BigDecimal` precision/scale

Avro requires both values at schema time; use this whenever the defaults aren't right.

```java
import io.github.jabhijeet.schema.annotation.SchemaDecimal;
import java.math.BigDecimal;

public class Invoice {
    @SchemaDecimal(precision = 12, scale = 2)
    public BigDecimal total;
}
```

### Supported Java types

| Java                                                                      | Avro                              | Notes                              |
| ------------------------------------------------------------------------- | --------------------------------- | ---------------------------------- |
| `boolean` / `Boolean`                                                     | `boolean`                         |                                    |
| `byte` / `short` / `int` / boxed                                          | `int`                             |                                    |
| `long` / `Long`                                                           | `long`                            |                                    |
| `float` / `double` / boxed                                                | `float` / `double`                |                                    |
| `char` / `Character` / `String` / `CharSequence`                          | `string`                          |                                    |
| `byte[]` / `ByteBuffer`                                                   | `bytes`                           |                                    |
| `UUID`                                                                    | `string` + `uuid`                 |                                    |
| `BigInteger`                                                              | `string`                          | Avro has no native big-int         |
| `BigDecimal`                                                              | `bytes` + `decimal`               | See `@SchemaDecimal`               |
| `LocalDate` / `java.sql.Date`                                             | `int` + `date`                    |                                    |
| `LocalTime`                                                               | `long` + `time-micros`            |                                    |
| `LocalDateTime`                                                           | `long` + `local-timestamp-millis` |                                    |
| `Instant` / `OffsetDateTime` / `ZonedDateTime` / `Date` / `Timestamp`    | `long` + `timestamp-millis`       |                                    |
| `enum`                                                                    | `enum`                            |                                    |
| `Collection<E>` (`List`, `Set`, …)                                        | `array<E>`                        |                                    |
| `Map<String, V>`                                                          | `map<V>`                          | Non-`String` keys rejected         |
| `Optional<T>`                                                             | `union[null, T]`                  | Always nullable                    |
| Nested POJO                                                               | `record`                          | Self-referential types supported   |
| Arrays (`T[]`)                                                            | `array<T>`                        |                                    |

Unknown `java.*` types fail fast with a field-qualified `SchemaGenerationException` — no silent coercion.

### Nullability rules

1. **Primitives** are always required.
2. **`Optional<T>`** is always nullable.
3. **`@SchemaField(nullability = NULLABLE | REQUIRED)`** wins over any default.
4. Otherwise reference types follow `SchemaOptions.nullableByDefault()` (default: `true`).

A nullable field becomes an Avro `union[null, T]` with `null` as the default value.
When `preserveDefaultValues(true)` is set and the field has a non-null Java
initializer, the branch order flips to `[T, null]` so the non-null default
validates against the union's first branch.

### Self-referential types

Avro happily represents recursive records (`Node → List<Node>`), but the Parquet format is strictly a finite tree. Generating a Parquet schema from a cyclic POJO throws `SchemaGenerationException` up front. Break the cycle with `@SchemaIgnore` on the back-edge field if you need Parquet output.

---

## Use case 2 — JSON to Avro/Parquet bytes

### One-call conversion with `JsonIO`

```java
import io.github.jabhijeet.schema.json.JsonIO;
import io.github.jabhijeet.schema.PojoSchemaGenerator;
import org.apache.avro.Schema;

// 1. Get (or build) an Avro schema
Schema schema = PojoSchemaGenerator.toAvro(OrderPojo.class);

// 2. Convert a single JSON document → Avro bytes
String json = "{\"orderId\":\"550e8400-...\",\"amount\":\"99.99\", ...}";
byte[] avroBytes = JsonIO.toAvroBytes(json, schema);

// 3. Or convert to Parquet bytes
byte[] parquetBytes = JsonIO.toParquetBytes(json, schema);

// 4. Batch: a JSON array → bytes containing all records
String batch = "[{...},{...}]";
byte[] avroBatch    = JsonIO.toAvroBytesAll(batch, schema);
byte[] parquetBatch = JsonIO.toParquetBytesAll(batch, schema);
```

### Reading back with `AvroIO` / `ParquetIO`

```java
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import org.apache.avro.generic.GenericRecord;
import java.util.List;

// Read all Avro records from bytes
List<GenericRecord> avroRecords = AvroIO.readAll(avroBytes);

// Read all Parquet records from bytes
List<GenericRecord> parquetRecords = ParquetIO.readAll(parquetBytes);

// Read from an InputStream (stream is closed on return)
List<GenericRecord> fromStream = AvroIO.readAll(inputStream);
```

### Writing GenericRecords directly

```java
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

GenericRecord record = new GenericData.Record(schema);
record.put("orderId", "550e8400-...");
record.put("amount", ...);

byte[] avroBytes    = AvroIO.toBytes(schema, record);
byte[] parquetBytes = ParquetIO.toBytes(schema, record);

// Write to an OutputStream (Parquet buffers internally before flushing)
AvroIO.writeTo(schema, List.of(record), outputStream);
ParquetIO.writeTo(schema, List.of(record), outputStream);
```

### JSON type mapping

| Avro schema type                     | Accepted JSON forms                                                    |
| ------------------------------------ | ---------------------------------------------------------------------- |
| `null`                               | `null`                                                                 |
| `boolean`                            | `true` / `false`                                                       |
| `int`                                | JSON integer in `[-2^31, 2^31)`                                        |
| `long`                               | JSON integer or numeric string (for ids beyond JS safe-integer range)  |
| `float` / `double`                   | JSON number                                                            |
| `string`                             | JSON string; numbers/booleans are coerced via `.toString()`            |
| `string` + `uuid` logical type       | Canonical UUID string, validated with `UUID.fromString`                |
| `bytes` / `fixed`                    | Standard or URL-safe Base64 string                                     |
| `bytes` + `decimal` logical type     | JSON number or numeric string (scale must match schema exactly)        |
| `int` + `date`                       | ISO-8601 date (`2025-06-15`) or integer (days since epoch)             |
| `int` + `time-millis`                | ISO-8601 time (`10:30:00`) or integer (millis since midnight)          |
| `long` + `time-micros`               | ISO-8601 time or integer (micros since midnight)                       |
| `long` + `timestamp-millis/micros`   | ISO-8601 instant with offset (`2025-06-15T10:30:00Z`) or epoch long    |
| `long` + `local-timestamp-millis/micros` | ISO-8601 local datetime (`2025-06-15T10:30:00`) or epoch long      |
| `enum`                               | JSON string matching one of the schema's symbols                       |
| `array`                              | JSON array; elements recursed against the element schema               |
| `map`                                | JSON object; all keys are strings                                      |
| `record`                             | JSON object; unknown fields are ignored                                |
| `union`                              | First branch whose shape fits the JSON value                           |

### Missing and null field handling

- A missing JSON field uses the schema's **default value** if defined.
- Otherwise, if the field schema accepts `null`, `null` is used.
- Otherwise, a `JsonConversionException` is thrown with the full JSON-pointer path (e.g., `$.order.items[2].price`).

### Error handling

Conversion errors are reported as `JsonConversionException` (extends `RuntimeException`):

```java
try {
    byte[] bytes = JsonIO.toAvroBytes(json, schema);
} catch (JsonConversionException e) {
    System.err.println("Path: " + e.path());    // e.g. $.customer.zip
    System.err.println("Detail: " + e.getMessage());
}
```


## License

Apache License, Version 2.0. See [LICENSE](LICENSE).

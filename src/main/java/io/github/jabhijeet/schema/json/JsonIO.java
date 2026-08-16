package io.github.jabhijeet.schema.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.IcebergIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import io.github.jabhijeet.schema.json.infer.*;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * One-call facade for converting JSON documents into Avro or Parquet bytes and
 * appending to in-memory Iceberg tables, all without a filesystem or Hadoop installation.
 *
 * <p>Combines {@link JsonToGenericRecordConverter} (JSON → {@link GenericRecord}) with
 * {@link AvroIO}, {@link ParquetIO}, and {@link IcebergIO}. All operations are
 * fully in‑memory — no {@code HADOOP_HOME} or external services required.
 *
 * <p>A single shared {@link JsonToGenericRecordConverter} instance is used internally;
 * it is stateless and thread‑safe. All methods are static and can be safely
 * called from multiple threads concurrently.
 */
public final class JsonIO {

    private static final JsonToGenericRecordConverter CONVERTER = new JsonToGenericRecordConverter();
    private static final GenericRecordToJsonConverter TO_JSON   = new GenericRecordToJsonConverter();
    private static final JsonSchemaInferrer INFERRER = new DefaultJsonSchemaInferrer();
    private static final InferredSchemaCache CACHE = new InferredSchemaCache();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonIO() {
        // utility
    }

    // ---------------------------------------------------------------- JSON → record (schema-less)

    /**
     * Parses {@code json} into a {@link GenericRecord} that conforms to {@code schema}.
     *
     * @throws JsonConversionException if the JSON does not match the schema
     */
    public static GenericRecord toRecord(String json, Schema schema) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(schema, "schema");
        return CONVERTER.convert(json, schema);
    }

    /**
     * Parses {@code json} into a {@link GenericRecord} that conforms to {@code schema},
     * rejecting input longer than {@code maxJsonLength} characters.
     */
    public static GenericRecord toRecord(String json, Schema schema, int maxJsonLength) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(schema, "schema");
        return CONVERTER.convert(json, schema, maxJsonLength);
    }

    /**
     * Reads a JSON document from {@code in} and converts it to a {@link GenericRecord}.
     * Closes the stream before returning.
     *
     * @throws JsonConversionException if the JSON does not match the schema
     */
    public static GenericRecord toRecord(InputStream in, Schema schema) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(schema, "schema");
        return CONVERTER.convert(in, schema);
    }

    /**
     * Converts a JSON array (or single object) to a list of {@link GenericRecord}s.
     *
     * @throws JsonConversionException if any element does not match the schema
     */
    public static List<GenericRecord> toRecords(String json, Schema schema) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(schema, "schema");
        return CONVERTER.convertAll(json, schema);
    }

    /**
     * Converts a JSON array (or single object) to a list of {@link GenericRecord}s,
     * rejecting input longer than {@code maxJsonLength} characters.
     */
    public static List<GenericRecord> toRecords(String json, Schema schema, int maxJsonLength) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(schema, "schema");
        return CONVERTER.convertAll(json, schema, maxJsonLength);
    }

    // ---------------------------------------------------------------- JSON → record (schema-less)

    /**
     * Infers an Avro schema from {@code json} and parses it into a {@link GenericRecord}.
     *
     * @throws SchemaInferenceException if the JSON shape cannot be mapped to an Avro record
     */
    public static GenericRecord toRecord(String json) {
        Objects.requireNonNull(json, "json");
        Schema schema = INFERRER.infer(json);
        return toRecord(json, schema);
    }

    /**
     * Infers an Avro schema from {@code json} using {@code opts} and parses it into a {@link GenericRecord}.
     */
    public static GenericRecord toRecord(String json, SchemaInferenceOptions opts) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(opts, "opts");
        JsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(opts);
        Schema schema = inferrer.infer(json);
        return toRecord(json, schema);
    }

    /**
     * Converts a JSON array (or single object) to a list of {@link GenericRecord}s
     * by inferring the schema from the first element.
     */
    public static List<GenericRecord> toRecords(String json) {
        Objects.requireNonNull(json, "json");
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
        if (node.isArray() && node.size() > 0) {
            Schema schema = INFERRER.infer(node.get(0));
            return toRecords(json, schema);
        }
        Schema schema = INFERRER.infer(json);
        return toRecords(json, schema);
    }

    /**
     * Converts a JSON array (or single object) to a list of {@link GenericRecord}s
     * by inferring the schema using {@code opts}.
     */
    public static List<GenericRecord> toRecords(String json, SchemaInferenceOptions opts) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(opts, "opts");
        JsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(opts);
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
        if (node.isArray() && node.size() > 0) {
            Schema schema = inferrer.infer(node.get(0));
            return toRecords(json, schema);
        }
        Schema schema = inferrer.infer(json);
        return toRecords(json, schema);
    }

    // ---------------------------------------------------------------- JSON → Avro bytes (schema-less)

    /**
     * Converts a single JSON document to an Avro Object Container File and returns the bytes.
     */
    public static byte[] toAvroBytes(String json, Schema schema) {
        return AvroIO.toBytes(schema, toRecord(json, schema));
    }

    /**
     * Converts a single JSON document to Avro bytes, rejecting input longer than
     * {@code maxJsonLength} characters.
     */
    public static byte[] toAvroBytes(String json, Schema schema, int maxJsonLength) {
        return AvroIO.toBytes(schema, toRecord(json, schema, maxJsonLength));
    }

    /**
     * Reads a JSON document from {@code in} and converts it to Avro bytes.
     * Closes the stream before returning.
     */
    public static byte[] toAvroBytes(InputStream in, Schema schema) {
        return AvroIO.toBytes(schema, toRecord(in, schema));
    }

    /**
     * Converts a JSON array (or single object) to an Avro Object Container File
     * containing all matching records, and returns the bytes.
     */
    public static byte[] toAvroBytesAll(String json, Schema schema) {
        return AvroIO.toBytes(schema, toRecords(json, schema));
    }

    /**
     * Converts a JSON array (or single object) to Avro bytes, rejecting input longer
     * than {@code maxJsonLength} characters.
     */
    public static byte[] toAvroBytesAll(String json, Schema schema, int maxJsonLength) {
        return AvroIO.toBytes(schema, toRecords(json, schema, maxJsonLength));
    }

    /**
     * Converts one JSON object with an explicit schema to a caller-owned stream
     * containing an Avro Object Container File.
     */
    public static InputStream toAvroInputStream(String json, Schema schema) {
        return AvroIO.toInputStream(schema, toRecord(json, schema));
    }

    /**
     * Converts one size-limited JSON object with an explicit schema to a
     * caller-owned stream containing an Avro Object Container File.
     */
    public static InputStream toAvroInputStream(String json, Schema schema, int maxJsonLength) {
        return AvroIO.toInputStream(schema, toRecord(json, schema, maxJsonLength));
    }

    /**
     * Converts a JSON array (or one object) with an explicit schema to a
     * caller-owned stream containing all records in an Avro Object Container File.
     */
    public static InputStream toAvroInputStreamAll(String json, Schema schema) {
        return AvroIO.toInputStream(schema, toRecords(json, schema));
    }

    /**
     * Converts a size-limited JSON array (or one object) with an explicit schema
     * to a caller-owned stream containing an Avro Object Container File.
     */
    public static InputStream toAvroInputStreamAll(String json, Schema schema,
                                                   int maxJsonLength) {
        return AvroIO.toInputStream(schema, toRecords(json, schema, maxJsonLength));
    }

    // ---------------------------------------------------------------- JSON → Avro bytes (schema-less)

    /**
     * Infers an Avro schema from {@code json} and converts it to an Avro Object Container File.
     */
    public static byte[] toAvroBytes(String json) {
        Objects.requireNonNull(json, "json");
        Schema schema = INFERRER.infer(json);
        return toAvroBytes(json, schema);
    }

    /**
     * Infers an Avro schema from {@code json} using {@code opts} and converts it to Avro bytes.
     */
    public static byte[] toAvroBytes(String json, SchemaInferenceOptions opts) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(opts, "opts");
        JsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(opts);
        Schema schema = inferrer.infer(json);
        return toAvroBytes(json, schema);
    }

    /**
     * Infers an Avro schema from a JSON array (or single object) and converts all
     * matching records to an Avro Object Container File.
     */
    public static byte[] toAvroBytesAll(String json) {
        Objects.requireNonNull(json, "json");
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
        Schema schema;
        if (node.isArray() && node.size() > 0) {
            schema = INFERRER.infer(node.get(0));
        } else {
            schema = INFERRER.infer(json);
        }
        return toAvroBytesAll(json, schema);
    }

    /**
     * Infers an Avro schema from a JSON array (or single object) using {@code opts}
     * and converts all matching records to Avro bytes.
     */
    public static byte[] toAvroBytesAll(String json, SchemaInferenceOptions opts) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(opts, "opts");
        JsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(opts);
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
        Schema schema;
        if (node.isArray() && node.size() > 0) {
            schema = inferrer.infer(node.get(0));
        } else {
            schema = inferrer.infer(json);
        }
        return toAvroBytesAll(json, schema);
    }

    /**
     * Infers the schema and converts one JSON object to a caller-owned stream
     * containing an Avro Object Container File.
     */
    public static InputStream toAvroInputStream(String json) {
        return new ByteArrayInputStream(toAvroBytes(json));
    }

    /**
     * Infers the schema using {@code opts} and converts one JSON object to a
     * caller-owned stream containing an Avro Object Container File.
     */
    public static InputStream toAvroInputStream(String json, SchemaInferenceOptions opts) {
        return new ByteArrayInputStream(toAvroBytes(json, opts));
    }

    /**
     * Infers the schema from a JSON array's first element (or one object) and
     * returns a caller-owned Avro Object Container File stream containing all rows.
     */
    public static InputStream toAvroInputStreamAll(String json) {
        return new ByteArrayInputStream(toAvroBytesAll(json));
    }

    /**
     * Infers the schema using {@code opts} and returns a caller-owned Avro Object
     * Container File stream containing every JSON array element (or one object).
     */
    public static InputStream toAvroInputStreamAll(String json, SchemaInferenceOptions opts) {
        return new ByteArrayInputStream(toAvroBytesAll(json, opts));
    }

    // ---------------------------------------------------------------- JSON → Parquet bytes

    /**
     * Converts a single JSON document to Parquet bytes (Snappy-compressed by default).
     * No {@code HADOOP_HOME} required — uses in-memory I/O.
     */
    public static byte[] toParquetBytes(String json, Schema schema) {
        return ParquetIO.toBytes(schema, toRecord(json, schema));
    }

    /**
     * Converts a single JSON document to Parquet bytes, rejecting input longer than
     * {@code maxJsonLength} characters.
     */
    public static byte[] toParquetBytes(String json, Schema schema, int maxJsonLength) {
        return ParquetIO.toBytes(schema, toRecord(json, schema, maxJsonLength));
    }

    /**
     * Reads a JSON document from {@code in} and converts it to Parquet bytes.
     * Closes the stream before returning.
     */
    public static byte[] toParquetBytes(InputStream in, Schema schema) {
        return ParquetIO.toBytes(schema, toRecord(in, schema));
    }

    /**
     * Converts a JSON array (or single object) to a Parquet file containing all
     * matching records, and returns the bytes.
     */
    public static byte[] toParquetBytesAll(String json, Schema schema) {
        return ParquetIO.toBytes(schema, toRecords(json, schema));
    }

    /**
     * Converts a JSON array (or single object) to Parquet bytes, rejecting input
     * longer than {@code maxJsonLength} characters.
     */
    public static byte[] toParquetBytesAll(String json, Schema schema, int maxJsonLength) {
        return ParquetIO.toBytes(schema, toRecords(json, schema, maxJsonLength));
    }

    /**
     * Converts one JSON object with an explicit schema to a caller-owned stream
     * containing a complete Parquet file.
     */
    public static InputStream toParquetInputStream(String json, Schema schema) {
        return ParquetIO.toInputStream(schema, toRecord(json, schema));
    }

    /**
     * Converts one size-limited JSON object with an explicit schema to a
     * caller-owned stream containing a complete Parquet file.
     */
    public static InputStream toParquetInputStream(String json, Schema schema,
                                                   int maxJsonLength) {
        return ParquetIO.toInputStream(schema, toRecord(json, schema, maxJsonLength));
    }

    /**
     * Converts a JSON array (or one object) with an explicit schema to a
     * caller-owned stream containing all rows in a complete Parquet file.
     */
    public static InputStream toParquetInputStreamAll(String json, Schema schema) {
        return ParquetIO.toInputStream(schema, toRecords(json, schema));
    }

    /**
     * Converts a size-limited JSON array (or one object) with an explicit schema
     * to a caller-owned stream containing a complete Parquet file.
     */
    public static InputStream toParquetInputStreamAll(String json, Schema schema,
                                                      int maxJsonLength) {
        return ParquetIO.toInputStream(schema, toRecords(json, schema, maxJsonLength));
    }

    // ---------------------------------------------------------------- JSON → Parquet bytes (schema-less)

    /**
     * Infers an Avro schema from {@code json} and converts it to Parquet bytes
     * (Snappy-compressed by default). No {@code HADOOP_HOME} required.
     */
    public static byte[] toParquetBytes(String json) {
        Objects.requireNonNull(json, "json");
        Schema schema = INFERRER.infer(json);
        return toParquetBytes(json, schema);
    }

    /**
     * Infers an Avro schema from {@code json} using {@code opts} and converts it to Parquet bytes.
     */
    public static byte[] toParquetBytes(String json, SchemaInferenceOptions opts) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(opts, "opts");
        JsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(opts);
        Schema schema = inferrer.infer(json);
        return toParquetBytes(json, schema);
    }

    /**
     * Infers an Avro schema from a JSON array (or single object) and converts all
     * matching records to a Parquet file.
     */
    public static byte[] toParquetBytesAll(String json) {
        Objects.requireNonNull(json, "json");
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
        Schema schema;
        if (node.isArray() && node.size() > 0) {
            schema = INFERRER.infer(node.get(0));
        } else {
            schema = INFERRER.infer(json);
        }
        return toParquetBytesAll(json, schema);
    }

    /**
     * Infers an Avro schema from a JSON array (or single object) using {@code opts}
     * and converts all matching records to Parquet bytes.
     */
    public static byte[] toParquetBytesAll(String json, SchemaInferenceOptions opts) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(opts, "opts");
        JsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(opts);
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
        Schema schema;
        if (node.isArray() && node.size() > 0) {
            schema = inferrer.infer(node.get(0));
        } else {
            schema = inferrer.infer(json);
        }
        return toParquetBytesAll(json, schema);
    }

    /**
     * Infers the schema and converts one JSON object to a caller-owned stream
     * containing a complete Parquet file.
     */
    public static InputStream toParquetInputStream(String json) {
        return new ByteArrayInputStream(toParquetBytes(json));
    }

    /**
     * Infers the schema using {@code opts} and converts one JSON object to a
     * caller-owned stream containing a complete Parquet file.
     */
    public static InputStream toParquetInputStream(String json, SchemaInferenceOptions opts) {
        return new ByteArrayInputStream(toParquetBytes(json, opts));
    }

    /**
     * Infers the schema from a JSON array's first element (or one object) and
     * returns a caller-owned Parquet stream containing every row.
     */
    public static InputStream toParquetInputStreamAll(String json) {
        return new ByteArrayInputStream(toParquetBytesAll(json));
    }

    /**
     * Infers the schema using {@code opts} and returns a caller-owned Parquet
     * stream containing every JSON array element (or one object).
     */
    public static InputStream toParquetInputStreamAll(String json,
                                                      SchemaInferenceOptions opts) {
        return new ByteArrayInputStream(toParquetBytesAll(json, opts));
    }

    // ---------------------------------------------------------------- JSON → Iceberg (in-memory)

    /**
     * Creates a new in-memory Iceberg table from {@code avroSchema}, appends the
     * single JSON document as one row, and returns the table handle.
     *
     * <p>No filesystem or {@code HADOOP_HOME} required — the table lives in the
     * JVM heap via {@code InMemoryCatalog}.
     */
    public static IcebergIO.InMemoryTable toIcebergTable(String json, Schema avroSchema) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(avroSchema, "avroSchema");
        IcebergIO.InMemoryTable table = IcebergIO.createTable(avroSchema);
        IcebergIO.append(table, avroSchema, List.of(toRecord(json, avroSchema)));
        return table;
    }

    /**
     * Appends a single JSON document to an existing in-memory Iceberg table.
     */
    public static void appendToIcebergTable(String json, Schema avroSchema,
                                            IcebergIO.InMemoryTable table) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(avroSchema, "avroSchema");
        Objects.requireNonNull(table, "table");
        IcebergIO.append(table, avroSchema, List.of(toRecord(json, avroSchema)));
    }

    /**
     * Appends a JSON array (or single object) to an existing in-memory Iceberg table.
     */
    public static void appendToIcebergTableAll(String json, Schema avroSchema,
                                               IcebergIO.InMemoryTable table) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(avroSchema, "avroSchema");
        Objects.requireNonNull(table, "table");
        IcebergIO.append(table, avroSchema, toRecords(json, avroSchema));
    }

    // ---------------------------------------------------------------- JSON → Iceberg (schema-less)

    /**
     * Infers an Avro schema from a JSON object or array and creates a new in-memory
     * Iceberg table. An object produces one row; an array infers the schema from
     * its first element and appends every element.
     */
    public static IcebergIO.InMemoryTable toIcebergTable(String json) {
        Objects.requireNonNull(json, "json");
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
        Schema schema;
        if (node.isArray() && node.size() > 0) {
            schema = INFERRER.infer(node.get(0));
        } else {
            schema = INFERRER.infer(json);
        }
        IcebergIO.InMemoryTable table = IcebergIO.createTable(schema);
        if (node.isArray()) {
            IcebergIO.append(table, schema, toRecords(json, schema));
        } else {
            IcebergIO.append(table, schema, List.of(toRecord(json, schema)));
        }
        return table;
    }

    /**
     * Infers an Avro schema from a JSON object or array using {@code opts} and
     * creates a new in-memory Iceberg table. An object produces one row; an array
     * infers the schema from its first element and appends every element.
     */
    public static IcebergIO.InMemoryTable toIcebergTable(String json, SchemaInferenceOptions opts) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(opts, "opts");
        JsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(opts);
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
        Schema schema;
        if (node.isArray() && node.size() > 0) {
            schema = inferrer.infer(node.get(0));
        } else {
            schema = inferrer.infer(json);
        }
        IcebergIO.InMemoryTable table = IcebergIO.createTable(schema);
        if (node.isArray()) {
            IcebergIO.append(table, schema, toRecords(json, schema));
        } else {
            IcebergIO.append(table, schema, List.of(toRecord(json, schema)));
        }
        return table;
    }

    // ---------------------------------------------------------------- Parquet bytes → JSON

    /**
     * Reads all records from Parquet bytes and converts each to a JSON string.
     * No {@code HADOOP_HOME} required — uses in-memory I/O.
     */
    public static List<String> fromParquetBytes(byte[] parquetBytes) {
        return ParquetIO.readAll(parquetBytes).stream().map(TO_JSON::convert).toList();
    }

    // ---------------------------------------------------------------- Iceberg table → JSON

    /**
     * Reads all rows from an in-memory Iceberg table and converts each to a JSON string.
     */
    public static List<String> fromIcebergTable(IcebergIO.InMemoryTable table) {
        Objects.requireNonNull(table, "table");
        return IcebergIO.readAllAsAvro(table).stream().map(TO_JSON::convert).toList();
    }

    // ---------------------------------------------------------------- record → JSON

    /**
     * Converts a {@link GenericRecord} to a JSON string.
     *
     * <p>For flat schemas (carrying {@code pojoSchemaFlattened=true}), the flat fields
     * are reconstructed into a nested JSON document using each field's
     * {@code pojoSchemaFlattenSourcePath} property.
     */
    public static String fromRecord(GenericRecord record) {
        Objects.requireNonNull(record, "record");
        return TO_JSON.convert(record);
    }

    // ---------------------------------------------------------------- Avro bytes → JSON

    /**
     * Reads all records from Avro Object Container File bytes and converts each to a JSON string.
     */
    public static List<String> fromAvroBytes(byte[] avroBytes) {
        return AvroIO.readAll(avroBytes).stream().map(TO_JSON::convert).toList();
    }
}

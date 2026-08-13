package io.github.jabhijeet.schema.json;

import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.IcebergIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

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

    private JsonIO() {
        // utility
    }

    // ---------------------------------------------------------------- JSON → record

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

    // ---------------------------------------------------------------- JSON → Avro bytes

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

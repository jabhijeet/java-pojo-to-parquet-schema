package io.github.jabhijeet.schema.json;

import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * One-call facade for converting JSON documents into Avro or Parquet bytes.
 *
 * <p>Combines {@link JsonToAvroConverter} (JSON â†’ {@link GenericRecord}) with
 * {@link AvroIO} / {@link ParquetIO} (GenericRecord â†’ binary).  All operations
 * are fully in-memory â€” no filesystem, no {@code HADOOP_HOME} required.
 *
 * <p>A single shared {@link JsonToAvroConverter} instance is used internally;
 * it is stateless and thread-safe.
 *
 * <h2>Usage examples</h2>
 * <pre>{@code
 * Schema schema = PojoSchemaGenerator.toAvro(OrderPojo.class);
 *
 * // Single record
 * byte[] avro    = JsonIO.toAvroBytes(orderJson, schema);
 * byte[] parquet = JsonIO.toParquetBytes(orderJson, schema);
 *
 * // Batch (JSON array as input)
 * byte[] avroAll    = JsonIO.toAvroBytesAll(ordersJson, schema);
 * byte[] parquetAll = JsonIO.toParquetBytesAll(ordersJson, schema);
 *
 * // Read back
 * List<GenericRecord> records = AvroIO.readAll(avroAll);
 * }</pre>
 */
public final class JsonIO {

    private static final JsonToAvroConverter CONVERTER = new JsonToAvroConverter();

    private JsonIO() {
        // utility
    }

    // ---------------------------------------------------------------- JSON â†’ record

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

    // ---------------------------------------------------------------- JSON â†’ Avro bytes

    /**
     * Converts a single JSON document to an Avro Object Container File and returns the bytes.
     */
    public static byte[] toAvroBytes(String json, Schema schema) {
        return AvroIO.toBytes(schema, toRecord(json, schema));
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

    // ---------------------------------------------------------------- JSON â†’ Parquet bytes

    /**
     * Converts a single JSON document to Parquet bytes using the default codec (Snappy).
     */
    public static byte[] toParquetBytes(String json, Schema schema) {
        return ParquetIO.toBytes(schema, toRecord(json, schema));
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
}


package io.github.jabhijeet.schema.io;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * In-memory Parquet write and read helpers.
 *
 * <p>All I/O goes through {@link InMemoryOutputFile} and {@link InMemoryInputFile}.
 * No filesystem, no Hadoop {@code FileSystem}, and no {@code HADOOP_HOME} environment
 * variable required — Hadoop's {@code Configuration} class is used internally by the
 * Parquet writer but works with default settings and no Hadoop installation.
 *
 * <p>Every method closes the resources it creates. Caller-owned streams are
 * flushed but not closed.
 */
public final class ParquetIO {

    public static final CompressionCodecName DEFAULT_CODEC = CompressionCodecName.SNAPPY;

    private ParquetIO() {
        // utility
    }

    public static byte[] toBytes(Schema schema, GenericRecord record) {
        Objects.requireNonNull(record, "record");
        return toBytes(schema, Collections.singletonList(record), DEFAULT_CODEC);
    }

    public static byte[] toBytes(Schema schema, Collection<? extends GenericRecord> records) {
        return toBytes(schema, records, DEFAULT_CODEC);
    }

    public static byte[] toBytes(Schema schema,
                                 Collection<? extends GenericRecord> records,
                                 CompressionCodecName codec) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(codec, "codec");

        InMemoryOutputFile output = new InMemoryOutputFile();
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(output)
                .withSchema(schema)
                .withDataModel(GenericData.get())
                .withCompressionCodec(codec)
                .build()) {
            for (GenericRecord record : records) {
                if (record == null) {
                    throw new IllegalArgumentException("records collection contains null");
                }
                writer.write(record);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Parquet data", e);
        }
        return output.toByteArray();
    }

    public static void writeTo(Schema schema,
                               Collection<? extends GenericRecord> records,
                               OutputStream out) {
        writeTo(schema, records, out, DEFAULT_CODEC);
    }

    public static void writeTo(Schema schema,
                               Collection<? extends GenericRecord> records,
                               OutputStream out,
                               CompressionCodecName codec) {
        Objects.requireNonNull(out, "out");
        byte[] bytes = toBytes(schema, records, codec);
        try {
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Parquet to output stream", e);
        }
    }

    public static GenericRecord fromBytes(byte[] parquetBytes) {
        List<GenericRecord> all = readAll(parquetBytes);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("Parquet file contains no records");
        }
        return all.get(0);
    }

    public static List<GenericRecord> readAll(byte[] parquetBytes) {
        Objects.requireNonNull(parquetBytes, "parquetBytes");
        if (parquetBytes.length == 0) {
            throw new IllegalArgumentException("parquetBytes is empty");
        }
        InMemoryInputFile input = new InMemoryInputFile(parquetBytes);
        List<GenericRecord> out = new ArrayList<>();
        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(input)
                .withDataModel(GenericData.get())
                .build()) {
            GenericRecord record;
            while ((record = reader.read()) != null) {
                out.add(record);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Parquet data", e);
        }
        return out;
    }

    public static List<GenericRecord> readAll(InputStream in) {
        Objects.requireNonNull(in, "in");
        try (InputStream owned = in) {
            return readAll(owned.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Parquet input stream", e);
        }
    }
}

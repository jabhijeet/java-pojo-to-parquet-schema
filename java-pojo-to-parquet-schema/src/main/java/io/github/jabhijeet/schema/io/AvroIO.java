package io.github.jabhijeet.schema.io;

import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;

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
 * In-memory / stream-based Avro write and read helpers.
 *
 * <p>All methods produce or consume a single, self-contained Avro Object Container
 * File (the ".avro" binary format that carries the schema in its header), which
 * means consumers do not need to supply the schema to read.
 *
 * <p>Every method closes the resources it creates. Streams passed in by the caller
 * are flushed but not closed â€” the caller owns them.
 */
public final class AvroIO {

    /** Default Avro file compression â€” snappy is widely supported and offers a good ratio/speed trade-off. */
    public static final CodecFactory DEFAULT_CODEC = CodecFactory.snappyCodec();

    private AvroIO() {
        // utility
    }

    /**
     * Serializes a single record to an Avro Object Container File and returns the bytes.
     */
    public static byte[] toBytes(Schema schema, GenericRecord record) {
        Objects.requireNonNull(record, "record");
        return toBytes(schema, Collections.singletonList(record));
    }

    /**
     * Serializes a collection of records to an Avro Object Container File and returns the bytes.
     * The records collection may be empty (produces a valid file containing just the header).
     */
    public static byte[] toBytes(Schema schema, Collection<? extends GenericRecord> records) {
        return toBytes(schema, records, DEFAULT_CODEC);
    }

    /**
     * Serializes records to an Avro Object Container File and returns the bytes,
     * using the supplied compression codec.
     */
    public static byte[] toBytes(Schema schema, Collection<? extends GenericRecord> records, CodecFactory codec) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(codec, "codec");
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        writeTo(schema, records, out, codec);
        return out.toByteArray();
    }

    /**
     * Writes records as an Avro Object Container File to the supplied stream.
     * Flushes but does not close {@code out} â€” the caller owns the stream.
     */
    public static void writeTo(Schema schema, Collection<? extends GenericRecord> records, OutputStream out) {
        writeTo(schema, records, out, DEFAULT_CODEC);
    }

    /**
     * Writes records as an Avro Object Container File to the supplied stream with a specific codec.
     * Flushes but does not close {@code out} â€” the caller owns the stream.
     */
    public static void writeTo(Schema schema,
                               Collection<? extends GenericRecord> records,
                               OutputStream out,
                               CodecFactory codec) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(codec, "codec");

        GenericDatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
        try (DataFileWriter<GenericRecord> fileWriter = new DataFileWriter<>(datumWriter)) {
            fileWriter.setCodec(codec);
            fileWriter.create(schema, out);
            for (GenericRecord record : records) {
                if (record == null) {
                    throw new IllegalArgumentException("records collection contains null");
                }
                fileWriter.append(record);
            }
            fileWriter.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Avro data", e);
        }
    }

    /**
     * Reads the first record from Avro Object Container File bytes.
     *
     * @throws IllegalArgumentException if the bytes contain zero records
     */
    public static GenericRecord fromBytes(byte[] avroBytes) {
        List<GenericRecord> all = readAll(avroBytes);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("Avro file contains no records");
        }
        return all.get(0);
    }

    /**
     * Reads all records from Avro Object Container File bytes.
     */
    public static List<GenericRecord> readAll(byte[] avroBytes) {
        Objects.requireNonNull(avroBytes, "avroBytes");
        GenericDatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        List<GenericRecord> out = new ArrayList<>();
        try (SeekableByteArrayInput seekableInput = new SeekableByteArrayInput(avroBytes);
             DataFileReader<GenericRecord> fileReader = new DataFileReader<>(seekableInput, datumReader)) {
            while (fileReader.hasNext()) {
                // Pass null to force a fresh record per iteration â€” Avro reuses the
                // returned object otherwise, which caused sneaky aliasing bugs.
                out.add(fileReader.next(null));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Avro data", e);
        }
        return out;
    }

    /**
     * Reads all records from an Avro Object Container File {@link InputStream}.
     * Closes the input stream before returning.
     */
    public static List<GenericRecord> readAll(InputStream in) {
        Objects.requireNonNull(in, "in");
        try (InputStream owned = in) {
            return readAll(owned.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Avro input stream", e);
        }
    }
}


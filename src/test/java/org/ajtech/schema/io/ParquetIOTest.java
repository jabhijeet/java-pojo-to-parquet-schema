package org.ajtech.schema.io;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParquetIOTest {

    private static final Schema SCHEMA = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"Item\",\"namespace\":\"test\",\"fields\":[" +
            "{\"name\":\"id\",\"type\":\"int\"}," +
            "{\"name\":\"label\",\"type\":\"string\"}]}");

    private static GenericRecord record(int id, String label) {
        GenericRecord r = new GenericData.Record(SCHEMA);
        r.put("id", id);
        r.put("label", label);
        return r;
    }

    @Test
    void single_record_round_trip() {
        GenericRecord original = record(1, "alpha");
        byte[] bytes = ParquetIO.toBytes(SCHEMA, original);

        assertThat(bytes).isNotEmpty();

        GenericRecord result = ParquetIO.fromBytes(bytes);
        assertThat(result.get("id")).isEqualTo(1);
        assertThat(result.get("label").toString()).isEqualTo("alpha");
    }

    @Test
    void multiple_records_round_trip() {
        List<GenericRecord> originals = Arrays.asList(
                record(1, "alpha"),
                record(2, "beta"),
                record(3, "gamma"));

        byte[] bytes = ParquetIO.toBytes(SCHEMA, originals);
        List<GenericRecord> results = ParquetIO.readAll(bytes);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).get("id")).isEqualTo(1);
        assertThat(results.get(1).get("id")).isEqualTo(2);
        assertThat(results.get(2).get("id")).isEqualTo(3);
    }

    @Test
    void write_to_output_stream_and_read_back() {
        List<GenericRecord> originals = Arrays.asList(record(10, "x"), record(20, "y"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ParquetIO.writeTo(SCHEMA, originals, out);
        assertThat(out.size()).isGreaterThan(0);

        List<GenericRecord> results = ParquetIO.readAll(out.toByteArray());
        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("id")).isEqualTo(10);
        assertThat(results.get(1).get("id")).isEqualTo(20);
    }

    @Test
    void read_from_input_stream() {
        byte[] bytes = ParquetIO.toBytes(SCHEMA, Arrays.asList(record(5, "five")));
        List<GenericRecord> results = ParquetIO.readAll(new ByteArrayInputStream(bytes));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("id")).isEqualTo(5);
    }

    @Test
    void from_bytes_throws_on_zero_records() {
        // Write then read with no records — Parquet always has a footer, so the
        // file is non-empty, but fromBytes requires at least one record.
        byte[] bytes = ParquetIO.toBytes(SCHEMA, List.of());
        assertThatThrownBy(() -> ParquetIO.fromBytes(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no records");
    }

    @Test
    void null_bytes_throws() {
        assertThatThrownBy(() -> ParquetIO.readAll((byte[]) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void empty_bytes_throws() {
        assertThatThrownBy(() -> ParquetIO.readAll(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void null_schema_throws() {
        assertThatThrownBy(() -> ParquetIO.toBytes(null, record(1, "a")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_record_throws() {
        assertThatThrownBy(() -> ParquetIO.toBytes(SCHEMA, (GenericRecord) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void collection_with_null_element_throws() {
        List<GenericRecord> records = Arrays.asList(record(1, "a"), null);
        assertThatThrownBy(() -> ParquetIO.toBytes(SCHEMA, records))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void in_memory_output_file_works_without_filesystem() {
        // Explicit test that InMemoryOutputFile/InputFile are used (no HADOOP_HOME needed)
        InMemoryOutputFile out = new InMemoryOutputFile();
        assertThat(out.toByteArray()).isEmpty();

        byte[] bytes = ParquetIO.toBytes(SCHEMA, Arrays.asList(record(7, "seven")));
        assertThat(bytes.length).isGreaterThan(4);

        // Verify the Parquet magic bytes PAR1 at start and end
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'A');
        assertThat(bytes[2]).isEqualTo((byte) 'R');
        assertThat(bytes[3]).isEqualTo((byte) '1');
        assertThat(bytes[bytes.length - 4]).isEqualTo((byte) 'P');
        assertThat(bytes[bytes.length - 3]).isEqualTo((byte) 'A');
        assertThat(bytes[bytes.length - 2]).isEqualTo((byte) 'R');
        assertThat(bytes[bytes.length - 1]).isEqualTo((byte) '1');
    }
}

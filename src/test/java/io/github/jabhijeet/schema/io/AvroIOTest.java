package io.github.jabhijeet.schema.io;

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

class AvroIOTest {

    private static final Schema SCHEMA = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"Item\",\"namespace\":\"test\",\"fields\":[" +
            "{\"name\":\"id\",\"type\":\"int\"}," +
            "{\"name\":\"name\",\"type\":\"string\"}]}");

    private static GenericRecord record(int id, String name) {
        GenericRecord r = new GenericData.Record(SCHEMA);
        r.put("id", id);
        r.put("name", name);
        return r;
    }

    @Test
    void single_record_round_trip() {
        GenericRecord original = record(1, "alpha");
        byte[] bytes = AvroIO.toBytes(SCHEMA, original);

        assertThat(bytes).isNotEmpty();

        GenericRecord result = AvroIO.fromBytes(bytes);
        assertThat(result.get("id")).isEqualTo(1);
        assertThat(result.get("name").toString()).isEqualTo("alpha");
    }

    @Test
    void multiple_records_round_trip() {
        List<GenericRecord> originals = Arrays.asList(
                record(1, "alpha"),
                record(2, "beta"),
                record(3, "gamma"));

        byte[] bytes = AvroIO.toBytes(SCHEMA, originals);
        List<GenericRecord> results = AvroIO.readAll(bytes);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).get("id")).isEqualTo(1);
        assertThat(results.get(1).get("id")).isEqualTo(2);
        assertThat(results.get(2).get("id")).isEqualTo(3);
        assertThat(results.get(2).get("name").toString()).isEqualTo("gamma");
    }

    @Test
    void records_are_independent_objects() {
        // Avro object reuse bug: verify each call to readAll returns independent records
        List<GenericRecord> records = Arrays.asList(record(1, "a"), record(2, "b"));
        byte[] bytes = AvroIO.toBytes(SCHEMA, records);
        List<GenericRecord> results = AvroIO.readAll(bytes);
        assertThat(results.get(0)).isNotSameAs(results.get(1));
        assertThat(results.get(0).get("id")).isEqualTo(1);
        assertThat(results.get(1).get("id")).isEqualTo(2);
    }

    @Test
    void write_to_output_stream_and_read_back() {
        List<GenericRecord> originals = Arrays.asList(record(10, "x"), record(20, "y"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        AvroIO.writeTo(SCHEMA, originals, out);
        assertThat(out.size()).isGreaterThan(0);

        List<GenericRecord> results = AvroIO.readAll(out.toByteArray());
        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("id")).isEqualTo(10);
    }

    @Test
    void read_from_input_stream() {
        byte[] bytes = AvroIO.toBytes(SCHEMA, Arrays.asList(record(5, "five")));
        List<GenericRecord> results = AvroIO.readAll(new ByteArrayInputStream(bytes));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("id")).isEqualTo(5);
    }

    @Test
    void empty_collection_produces_valid_avro_header() {
        byte[] bytes = AvroIO.toBytes(SCHEMA, List.of());
        List<GenericRecord> results = AvroIO.readAll(bytes);
        assertThat(results).isEmpty();
        assertThat(bytes).isNotEmpty(); // schema header is still present
    }

    @Test
    void from_bytes_throws_on_empty_file() {
        byte[] bytes = AvroIO.toBytes(SCHEMA, List.of());
        assertThatThrownBy(() -> AvroIO.fromBytes(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no records");
    }

    @Test
    void null_bytes_throws() {
        assertThatThrownBy(() -> AvroIO.readAll((byte[]) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_schema_throws() {
        assertThatThrownBy(() -> AvroIO.toBytes(null, record(1, "a")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_record_throws() {
        assertThatThrownBy(() -> AvroIO.toBytes(SCHEMA, (GenericRecord) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void collection_with_null_element_throws() {
        List<GenericRecord> records = Arrays.asList(record(1, "a"), null);
        assertThatThrownBy(() -> AvroIO.toBytes(SCHEMA, records))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}


package io.github.jabhijeet.schema.json;

import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.fixtures.AllPrimitivesPojo;
import io.github.jabhijeet.schema.fixtures.NestedCollectionsPojo;
import io.github.jabhijeet.schema.fixtures.OuterPojo;
import io.github.jabhijeet.schema.fixtures.TemporalTypesPojo;
import io.github.jabhijeet.schema.io.IcebergIO;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.iceberg.data.Record;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests: JSON → in-memory Iceberg table → JSON.
 * No filesystem, no HADOOP_HOME required — all data lives in the JVM heap.
 */
class JsonIcebergIntegrationTest {

    // All required (primitive) fields + nullable boxed fields
    private static final String ALL_PRIM =
            "\"pBool\":true,\"pByte\":1,\"pShort\":2,\"pInt\":42,\"pLong\":100," +
            "\"pFloat\":1.5,\"pDouble\":3.14,\"pChar\":\"A\"," +
            "\"bBool\":null,\"bByte\":null,\"bShort\":null,\"bInt\":null," +
            "\"bLong\":null,\"bFloat\":null,\"bDouble\":null,\"bChar\":null";

    // ---------------------------------------------------------------- simple object

    @Test
    void simple_object_round_trips_through_iceberg_table() {
        Schema schema = PojoSchemaGenerator.toAvro(AllPrimitivesPojo.class);

        IcebergIO.InMemoryTable table = JsonIO.toIcebergTable("{" + ALL_PRIM + "}", schema);
        List<String> jsons = JsonIO.fromIcebergTable(table);

        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("\"pBool\":true");
        assertThat(jsons.get(0)).contains("\"pInt\":42");
    }

    @Test
    void simple_object_round_trips_via_readAll() {
        Schema schema = PojoSchemaGenerator.toAvro(AllPrimitivesPojo.class);
        String json = "{\"pBool\":false,\"pByte\":0,\"pShort\":0,\"pInt\":7,\"pLong\":999," +
                "\"pFloat\":0.5,\"pDouble\":2.718,\"pChar\":\"Z\"," +
                "\"bBool\":null,\"bByte\":null,\"bShort\":null,\"bInt\":null," +
                "\"bLong\":null,\"bFloat\":null,\"bDouble\":null,\"bChar\":null}";

        IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(json, schema);
        List<Record> rows = IcebergIO.readAll(table);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getField("pInt")).isEqualTo(7);
        assertThat(rows.get(0).getField("pDouble")).isEqualTo(2.718);
    }

    // ---------------------------------------------------------------- nested object

    @Test
    void nested_object_round_trips_through_iceberg_table() {
        Schema schema = PojoSchemaGenerator.toAvro(OuterPojo.class);
        String json = "{\"mid\":{\"midLabel\":\"hello\",\"leaf\":{\"leafValue\":99,\"leafLabel\":null}," +
                "\"leaves\":[]},\"mids\":[]}";

        IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(json, schema);
        List<String> jsons = JsonIO.fromIcebergTable(table);

        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("\"midLabel\"");
        assertThat(jsons.get(0)).contains("hello");
    }

    // ---------------------------------------------------------------- multiple appends

    @Test
    void multiple_append_calls_accumulate_rows() {
        Schema schema = PojoSchemaGenerator.toAvro(AllPrimitivesPojo.class);
        IcebergIO.InMemoryTable table = IcebergIO.createTable(schema);

        String row = "{" + ALL_PRIM + "}";
        JsonIO.appendToIcebergTable(row.replace("42", "1"), schema, table);
        JsonIO.appendToIcebergTable(row.replace("42", "2"), schema, table);
        JsonIO.appendToIcebergTable(row.replace("42", "3"), schema, table);

        List<Record> rows = IcebergIO.readAll(table);
        assertThat(rows).hasSize(3);
    }

    // ---------------------------------------------------------------- batch append

    @Test
    void batch_append_writes_all_records() {
        Schema schema = PojoSchemaGenerator.toAvro(AllPrimitivesPojo.class);
        IcebergIO.InMemoryTable table = IcebergIO.createTable(schema);

        String r1 = "{\"pBool\":true,\"pByte\":0,\"pShort\":0,\"pInt\":10,\"pLong\":100," +
                "\"pFloat\":1.0,\"pDouble\":1.0,\"pChar\":\"A\"," +
                "\"bBool\":null,\"bByte\":null,\"bShort\":null,\"bInt\":null," +
                "\"bLong\":null,\"bFloat\":null,\"bDouble\":null,\"bChar\":null}";
        String r2 = "{\"pBool\":false,\"pByte\":0,\"pShort\":0,\"pInt\":20,\"pLong\":200," +
                "\"pFloat\":2.0,\"pDouble\":2.0,\"pChar\":\"B\"," +
                "\"bBool\":null,\"bByte\":null,\"bShort\":null,\"bInt\":null," +
                "\"bLong\":null,\"bFloat\":null,\"bDouble\":null,\"bChar\":null}";

        JsonIO.appendToIcebergTableAll("[" + r1 + "," + r2 + "]", schema, table);

        List<Record> rows = IcebergIO.readAll(table);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getField("pInt")).isEqualTo(10);
        assertThat(rows.get(1).getField("pInt")).isEqualTo(20);
    }

    // ---------------------------------------------------------------- complex nested

    @Test
    void complex_nested_objects_round_trip_through_iceberg_table() {
        Schema schema = PojoSchemaGenerator.toAvro(NestedCollectionsPojo.class);
        String json = "{\"matrix\":[[1,2],[3,4]],\"indexByKey\":{\"a\":[\"x\",\"y\"]}," +
                "\"histograms\":[{\"k\":5}],\"nestedMap\":{\"outer\":{\"inner\":9.9}}," +
                "\"addresses\":[{\"city\":\"NYC\",\"postalCode\":10001}]}";

        IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(json, schema);
        List<String> jsons = JsonIO.fromIcebergTable(table);

        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("NYC");
    }

    @Test
    void temporal_logical_values_are_written_as_iceberg_temporal_objects() {
        Schema schema = PojoSchemaGenerator.toAvro(TemporalTypesPojo.class);
        GenericData.Record record = new GenericData.Record(schema);
        record.put("localDate", (int) LocalDate.of(2025, 6, 15).toEpochDay());
        record.put("localTime", LocalTime.of(10, 30).toNanoOfDay() / 1_000L);
        record.put("localDateTime", LocalDateTime.of(2025, 6, 15, 10, 30)
                .toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
        record.put("instant", Instant.parse("2025-06-15T10:30:00Z").toEpochMilli());

        IcebergIO.InMemoryTable table = IcebergIO.createTable(schema);
        assertThat(table.schema().findField("localDateTime").type().toString())
                .isEqualTo("timestamp");
        IcebergIO.append(table, schema, List.of(record));

        Record row = IcebergIO.readAll(table).get(0);
        assertThat(row.getField("localDate")).isInstanceOf(LocalDate.class);
        assertThat(row.getField("localTime")).isInstanceOf(LocalTime.class);
        assertThat(row.getField("localDateTime")).isInstanceOf(LocalDateTime.class);
        assertThat(row.getField("instant")).isNotInstanceOf(Number.class);
    }

    @Test
    void append_rejects_records_from_a_different_schema() {
        Schema tableSchema = PojoSchemaGenerator.toAvro(AllPrimitivesPojo.class);
        Schema recordSchema = PojoSchemaGenerator.toAvro(OuterPojo.class);
        IcebergIO.InMemoryTable table = IcebergIO.createTable(tableSchema);
        GenericData.Record record = new GenericData.Record(recordSchema);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> IcebergIO.append(table, recordSchema, List.of(record))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

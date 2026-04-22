package io.github.jabhijeet.schema.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.fixtures.FlatDemoEmployee;
import io.github.jabhijeet.schema.fixtures.FlattenOuter;
import io.github.jabhijeet.schema.fixtures.TemporalTypesPojo;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericRecordToJsonConverterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GenericRecordToJsonConverter CONVERTER = new GenericRecordToJsonConverter();

    // ---------------------------------------------------------------- flat schema round-trip

    @Test
    void flat_record_round_trips_to_nested_json() throws IOException {
        String inputJson = readResource("demo-fixtures/employee-flat.input.json");
        Schema schema = PojoSchemaGenerator.builder().flattenNestedRecords(true).build()
                .generateAvro(FlatDemoEmployee.class);

        GenericRecord record = JsonIO.toRecord(inputJson, schema);
        String outputJson = JsonIO.fromRecord(record);

        JsonNode input  = MAPPER.readTree(inputJson);
        JsonNode output = MAPPER.readTree(outputJson);

        // Scalars survive unchanged
        assertThat(output.path("employeeId").asText()).isEqualTo("EMP-2024-001");
        assertThat(output.path("fullName").asText()).isEqualTo("Grace Hopper");

        // Date round-trips as ISO-8601
        assertThat(output.path("hireDate").asText()).isEqualTo("1944-02-15");

        // Decimal round-trips as plain string
        assertThat(output.path("salary").asText()).isEqualTo("95000.00");

        // Nested objects reconstructed from flat fields
        assertThat(output.path("homeAddress").path("street").asText()).isEqualTo("456 Compiler Lane");
        assertThat(output.path("homeAddress").path("city").asText()).isEqualTo("New Haven");
        assertThat(output.path("homeAddress").path("zip").asText()).isEqualTo("06511");

        // 3-level depth with @SchemaField rename ("dept")
        assertThat(output.path("dept").path("code").asText()).isEqualTo("ENG-SW");
        assertThat(output.path("dept").path("name").asText()).isEqualTo("Software Engineering");
        assertThat(output.path("dept").path("location").path("building").asText()).isEqualTo("Building A");
        assertThat(output.path("dept").path("location").path("floor").asInt()).isEqualTo(3);

        // Nullable intermediate
        assertThat(output.path("manager").path("name").asText()).isEqualTo("Alan Turing");

        // Array boundary — projects stays as array of objects
        assertThat(output.path("projects").isArray()).isTrue();
        assertThat(output.path("projects").size()).isEqualTo(3);
        assertThat(output.path("projects").get(0).path("projectCode").asText()).isEqualTo("COBOL-1");

        // Map boundary — attributes stays as object with its original keys
        assertThat(output.path("attributes").path("clearance").asText()).isEqualTo("top-secret");
        assertThat(output.path("attributes").path("team").asText()).isEqualTo("compilers");
    }

    // ---------------------------------------------------------------- regular (non-flat) record

    @Test
    void regular_nested_record_to_json() {
        Schema schema = PojoSchemaGenerator.toAvro(FlattenOuter.class);

        Schema innerSchema = schema.getField("inner").schema().getTypes().stream()
                .filter(s -> s.getType() == Schema.Type.RECORD).findFirst().orElseThrow();
        GenericRecord inner = new GenericData.Record(innerSchema);
        inner.put("c", "hello");
        inner.put("n", 42);

        GenericRecord outer = new GenericData.Record(schema);
        outer.put("inner", inner);
        outer.put("top", "world");

        String json = CONVERTER.convert(outer);

        assertThat(json).contains("\"top\":\"world\"");
        assertThat(json).contains("\"c\":\"hello\"");
        assertThat(json).contains("\"n\":42");
    }

    // ---------------------------------------------------------------- temporal types

    @Test
    void temporal_types_produce_iso8601_strings() {
        Schema schema = PojoSchemaGenerator.toAvro(TemporalTypesPojo.class);

        GenericRecord record = new GenericData.Record(schema);
        // date
        record.put("localDate",      (int) LocalDate.of(2025, 6, 15).toEpochDay());
        record.put("sqlDate",        (int) LocalDate.of(2025, 6, 15).toEpochDay());
        // time-micros
        record.put("localTime",      LocalTime.of(10, 30, 0).toNanoOfDay() / 1_000L);
        // local-timestamp-millis
        record.put("localDateTime",  LocalDateTime.of(2025, 6, 15, 10, 30)
                .toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
        // timestamp-millis
        record.put("instant",        Instant.parse("2025-06-15T10:30:00Z").toEpochMilli());
        record.put("offsetDateTime", Instant.parse("2025-06-15T10:30:00Z").toEpochMilli());
        record.put("zonedDateTime",  Instant.parse("2025-06-15T10:30:00Z").toEpochMilli());
        record.put("utilDate",       Instant.parse("2025-06-15T10:30:00Z").toEpochMilli());
        record.put("sqlTimestamp",   Instant.parse("2025-06-15T10:30:00Z").toEpochMilli());

        String json = CONVERTER.convert(record);

        assertThat(json).contains("\"localDate\":\"2025-06-15\"");
        assertThat(json).contains("\"sqlDate\":\"2025-06-15\"");
        assertThat(json).contains("\"localTime\":\"10:30\"");
        assertThat(json).contains("\"localDateTime\":\"2025-06-15T10:30\"");
        assertThat(json).contains("\"instant\":\"2025-06-15T10:30:00Z\"");
    }

    // ---------------------------------------------------------------- decimal

    @Test
    void decimal_bytes_round_trip_to_plain_string() {
        Schema decimalSchema = Schema.create(Schema.Type.BYTES);
        LogicalTypes.decimal(12, 2).addToSchema(decimalSchema);
        Schema recordSchema = Schema.createRecord("DecTest", null, "test", false,
                List.of(new Schema.Field("amount",
                        Schema.createUnion(Schema.create(Schema.Type.NULL), decimalSchema),
                        null, null)));

        BigDecimal value = new BigDecimal("1234.56");
        Conversions.DecimalConversion conv = new Conversions.DecimalConversion();
        java.nio.ByteBuffer bytes = conv.toBytes(value, decimalSchema, LogicalTypes.decimal(12, 2));

        GenericRecord record = new GenericData.Record(recordSchema);
        record.put("amount", bytes);

        String json = CONVERTER.convert(record);
        assertThat(json).contains("\"amount\":\"1234.56\"");
    }

    // ---------------------------------------------------------------- null values

    @Test
    void null_field_becomes_json_null() {
        Schema schema = PojoSchemaGenerator.toAvro(FlattenOuter.class);
        GenericRecord record = new GenericData.Record(schema);
        record.put("inner", null);
        record.put("top", null);

        String json = CONVERTER.convert(record);

        assertThat(json).contains("\"inner\":null");
        assertThat(json).contains("\"top\":null");
    }

    // ---------------------------------------------------------------- JsonIO facade

    @Test
    void fromAvroBytes_returns_json_for_every_record() {
        Schema schema = PojoSchemaGenerator.toAvro(FlattenOuter.class);
        Schema innerSchema = schema.getField("inner").schema().getTypes().stream()
                .filter(s -> s.getType() == Schema.Type.RECORD).findFirst().orElseThrow();

        GenericRecord r1 = new GenericData.Record(schema);
        GenericRecord inner1 = new GenericData.Record(innerSchema);
        inner1.put("c", "a"); inner1.put("n", 1);
        r1.put("inner", inner1); r1.put("top", "first");

        GenericRecord r2 = new GenericData.Record(schema);
        r2.put("inner", null); r2.put("top", "second");

        byte[] avroBytes = AvroIO.toBytes(schema, List.of(r1, r2));
        List<String> jsons = JsonIO.fromAvroBytes(avroBytes);

        assertThat(jsons).hasSize(2);
        assertThat(jsons.get(0)).contains("\"top\":\"first\"");
        assertThat(jsons.get(1)).contains("\"top\":\"second\"");
    }

    @Test
    void fromParquetBytes_returns_json_for_every_record() {
        Schema schema = PojoSchemaGenerator.toAvro(FlattenOuter.class);
        Schema innerSchema = schema.getField("inner").schema().getTypes().stream()
                .filter(s -> s.getType() == Schema.Type.RECORD).findFirst().orElseThrow();

        GenericRecord r = new GenericData.Record(schema);
        GenericRecord inner = new GenericData.Record(innerSchema);
        inner.put("c", "x"); inner.put("n", 99);
        r.put("inner", inner); r.put("top", "parquet");

        byte[] parquetBytes = ParquetIO.toBytes(schema, r);
        List<String> jsons = JsonIO.fromParquetBytes(parquetBytes);

        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("\"top\":\"parquet\"");
        assertThat(jsons.get(0)).contains("\"n\":99");
    }

    // ---------------------------------------------------------------- union handling

    @Test
    void union_int_string_serializes_int_branch() {
        Schema union = Schema.createUnion(Schema.create(Schema.Type.INT), Schema.create(Schema.Type.STRING));
        Schema recordSchema = Schema.createRecord("Test", null, "test", false,
                List.of(new Schema.Field("value", union, null, null)));

        GenericRecord record = new GenericData.Record(recordSchema);
        record.put("value", 42);

        String json = CONVERTER.convert(record);
        assertThat(json).contains("\"value\":42");
    }

    @Test
    void union_int_string_serializes_string_branch() {
        Schema union = Schema.createUnion(Schema.create(Schema.Type.INT), Schema.create(Schema.Type.STRING));
        Schema recordSchema = Schema.createRecord("Test", null, "test", false,
                List.of(new Schema.Field("value", union, null, null)));

        GenericRecord record = new GenericData.Record(recordSchema);
        record.put("value", "hello");

        String json = CONVERTER.convert(record);
        assertThat(json).contains("\"value\":\"hello\"");
    }

    @Test
    void union_record_string_serializes_record_branch() {
        Schema innerRecord = Schema.createRecord("Inner", null, "test", false,
                List.of(new Schema.Field("field", Schema.create(Schema.Type.STRING), null, null)));
        Schema union = Schema.createUnion(innerRecord, Schema.create(Schema.Type.STRING));
        Schema recordSchema = Schema.createRecord("Test", null, "test", false,
                List.of(new Schema.Field("value", union, null, null)));

        GenericRecord inner = new GenericData.Record(innerRecord);
        inner.put("field", "testValue");
        GenericRecord record = new GenericData.Record(recordSchema);
        record.put("value", inner);

        String json = CONVERTER.convert(record);
        assertThat(json).contains("\"value\":{\"field\":\"testValue\"}");
    }

    @Test
    void union_value_without_matching_branch_throws() {
        Schema union = Schema.createUnion(Schema.create(Schema.Type.INT), Schema.create(Schema.Type.STRING));
        Schema recordSchema = Schema.createRecord("Test", null, "test", false,
                List.of(new Schema.Field("value", union, null, null)));

        GenericRecord record = new GenericData.Record(recordSchema);
        // Put a Long value which doesn't match any branch (INT expects Integer, not Long)
        record.put("value", 42L);

        assertThatThrownBy(() -> CONVERTER.convert(record))
                .isInstanceOf(JsonConversionException.class)
                .hasMessageContaining("does not match any branch");
    }

    // ---------------------------------------------------------------- helpers

    private static String readResource(String resource) throws IOException {
        ClassLoader cl = GenericRecordToJsonConverterTest.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

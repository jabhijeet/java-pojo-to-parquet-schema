package io.github.jabhijeet.schema.json;

import io.github.jabhijeet.schema.FlattenCollisionStrategy;
import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.fixtures.*;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonToGenericRecordConverterFlattenTest {

    private static final PojoSchemaGenerator FLAT = PojoSchemaGenerator.builder()
            .flattenNestedRecords(true)
            .build();

    @Test
    void reads_nested_json_into_flat_record() {
        Schema schema = FLAT.generateAvro(FlattenThreeL1.class);
        String json = "{\"b\":{\"c\":{\"leaf\":\"hello\"}}}";

        GenericRecord record = JsonIO.toRecord(json, schema);

        assertThat(record.get("b_c_leaf").toString()).isEqualTo("hello");
    }

    @Test
    void reads_literal_flat_key_as_fallback() {
        Schema schema = FLAT.generateAvro(FlattenThreeL1.class);
        String json = "{\"b_c_leaf\":\"literal\"}";

        GenericRecord record = JsonIO.toRecord(json, schema);

        assertThat(record.get("b_c_leaf").toString()).isEqualTo("literal");
    }

    @Test
    void nested_form_wins_when_both_present() {
        Schema schema = FLAT.generateAvro(FlattenThreeL1.class);
        String json = "{\"b\":{\"c\":{\"leaf\":\"nested\"}},\"b_c_leaf\":\"flat\"}";

        GenericRecord record = JsonIO.toRecord(json, schema);

        assertThat(record.get("b_c_leaf").toString()).isEqualTo("nested");
    }

    @Test
    void missing_intermediate_yields_null_for_nullable_leaf() {
        Schema schema = FLAT.generateAvro(FlattenOuter.class);
        String json = "{\"top\":\"only\"}";

        GenericRecord record = JsonIO.toRecord(json, schema);

        assertThat(record.get("top").toString()).isEqualTo("only");
        assertThat(record.get("inner_c")).isNull();
        assertThat(record.get("inner_n")).isNull();
    }

    @Test
    void missing_required_intermediate_throws_with_flat_name_in_path() {
        Schema schema = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .nullableByDefault(false)
                .build()
                .generateAvro(FlattenThreeL1.class);
        String json = "{}";

        assertThatThrownBy(() -> JsonIO.toRecord(json, schema))
                .isInstanceOf(JsonConversionException.class)
                .hasMessageContaining("b_c_leaf");
    }

    @Test
    void leaf_type_error_reports_flat_field_in_path() {
        Schema schema = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .nullableByDefault(false)
                .build()
                .generateAvro(FlattenOuter.class);
        String json = "{\"inner\":{\"c\":\"ok\",\"n\":\"not-an-int\"},\"top\":\"t\"}";

        assertThatThrownBy(() -> JsonIO.toRecord(json, schema))
                .isInstanceOf(JsonConversionException.class)
                .hasMessageContaining("inner_n");
    }

    @Test
    void array_element_records_stay_nested() {
        Schema schema = FLAT.generateAvro(FlattenWithListNested.class);
        String json = "{\"items\":[{\"c\":\"x\",\"n\":1},{\"c\":\"y\",\"n\":2}],\"top\":\"t\"}";

        GenericRecord record = JsonIO.toRecord(json, schema);

        @SuppressWarnings("unchecked")
        List<GenericRecord> items = (List<GenericRecord>) record.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("c").toString()).isEqualTo("x");
        assertThat(items.get(0).get("n")).isEqualTo(1);
        assertThat(items.get(1).get("c").toString()).isEqualTo("y");
        assertThat(record.get("top").toString()).isEqualTo("t");
    }

    @Test
    void custom_separator_round_trips() {
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .flattenSeparator("XX")
                .build();
        Schema schema = gen.generateAvro(FlattenOuter.class);
        String json = "{\"inner\":{\"c\":\"hello\",\"n\":42},\"top\":\"t\"}";

        GenericRecord record = JsonIO.toRecord(json, schema);

        assertThat(record.get("innerXXc").toString()).isEqualTo("hello");
        assertThat(record.get("innerXXn")).isEqualTo(42);
        assertThat(record.get("top").toString()).isEqualTo("t");
    }

    @Test
    void auto_rename_collision_reads_both_sources() {
        Schema schema = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .flattenCollisionStrategy(FlattenCollisionStrategy.AUTO_RENAME)
                .build()
                .generateAvro(FlattenCollisionPojo.class);
        String json = "{\"foo_bar\":\"direct\",\"foo\":{\"bar\":\"nested\"}}";

        GenericRecord record = JsonIO.toRecord(json, schema);

        assertThat(record.get("foo_bar").toString()).isEqualTo("direct");
        assertThat(record.get("foo_bar__1").toString()).isEqualTo("nested");
    }

    @Test
    void round_trip_to_avro_bytes() {
        Schema schema = FLAT.generateAvro(FlattenOuter.class);
        String json = "{\"inner\":{\"c\":\"round\",\"n\":7},\"top\":\"trip\"}";

        byte[] bytes = JsonIO.toAvroBytes(json, schema);
        List<GenericRecord> back = AvroIO.readAll(bytes);

        assertThat(back).hasSize(1);
        assertThat(back.get(0).get("inner_c").toString()).isEqualTo("round");
        assertThat(back.get(0).get("inner_n")).isEqualTo(7);
        assertThat(back.get(0).get("top").toString()).isEqualTo("trip");
    }

    @Test
    void round_trip_to_parquet_bytes() {
        Schema schema = FLAT.generateAvro(FlattenOuter.class);
        String json = "{\"inner\":{\"c\":\"round\",\"n\":9},\"top\":\"trip\"}";

        byte[] bytes = JsonIO.toParquetBytes(json, schema);
        List<GenericRecord> back = ParquetIO.readAll(bytes);

        assertThat(back).hasSize(1);
        assertThat(back.get(0).get("inner_c").toString()).isEqualTo("round");
        assertThat(back.get(0).get("inner_n")).isEqualTo(9);
        assertThat(back.get(0).get("top").toString()).isEqualTo("trip");
    }

    @Test
    void nested_schema_without_flat_marker_uses_direct_keys_only() {
        // Sanity: a plain nested schema should not trigger flat walking.
        Schema schema = PojoSchemaGenerator.toAvro(FlattenOuter.class);
        String json = "{\"inner\":{\"c\":\"x\",\"n\":1},\"top\":\"t\"}";

        GenericRecord record = JsonIO.toRecord(json, schema);

        GenericRecord inner = (GenericRecord) record.get("inner");
        assertThat(inner.get("c").toString()).isEqualTo("x");
        assertThat(record.get("top").toString()).isEqualTo("t");
    }

    @SuppressWarnings("unused")
    private static final Class<?> FIXTURE_IN_USE = FlattenInner.class;
}

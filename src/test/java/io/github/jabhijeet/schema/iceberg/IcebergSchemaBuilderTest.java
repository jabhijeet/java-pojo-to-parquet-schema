package io.github.jabhijeet.schema.iceberg;

import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.fixtures.AllPrimitivesPojo;
import io.github.jabhijeet.schema.fixtures.FlattenOuter;
import io.github.jabhijeet.schema.fixtures.NestedCollectionsPojo;
import io.github.jabhijeet.schema.fixtures.OuterPojo;
import io.github.jabhijeet.schema.fixtures.Person;
import io.github.jabhijeet.schema.fixtures.TemporalTypesPojo;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IcebergSchemaBuilderTest {

    @Test
    void simple_object_conversion_maps_primitive_and_boxed_fields() {
        Schema schema = PojoSchemaGenerator.toIceberg(AllPrimitivesPojo.class);

        assertThat(schema.findField("pBool").isRequired()).isTrue();
        assertThat(schema.findField("pInt").type().typeId()).isEqualTo(Type.TypeID.INTEGER);
        assertThat(schema.findField("pLong").type().typeId()).isEqualTo(Type.TypeID.LONG);
        assertThat(schema.findField("pFloat").type().typeId()).isEqualTo(Type.TypeID.FLOAT);
        assertThat(schema.findField("pDouble").type().typeId()).isEqualTo(Type.TypeID.DOUBLE);

        assertThat(schema.findField("bBool").isOptional()).isTrue();
        assertThat(schema.findField("bInt").type().typeId()).isEqualTo(Type.TypeID.INTEGER);
        assertThat(schema.findField("bLong").type().typeId()).isEqualTo(Type.TypeID.LONG);
        assertThat(schema.findField("bFloat").type().typeId()).isEqualTo(Type.TypeID.FLOAT);
        assertThat(schema.findField("bDouble").type().typeId()).isEqualTo(Type.TypeID.DOUBLE);
    }

    @Test
    void top_level_columns_have_stable_non_negative_ids() {
        Schema schema = PojoSchemaGenerator.toIceberg(Person.class);

        List<Types.NestedField> columns = schema.columns();
        assertThat(columns).isNotEmpty();
        // Iceberg 1.10+ assigns IDs starting from 0 (getAndIncrement); uniqueness is the invariant.
        assertThat(columns).extracting(Types.NestedField::fieldId)
                .doesNotHaveDuplicates()
                .allMatch(id -> id >= 0);
    }

    @Test
    void nested_records_lists_and_maps_convert_to_iceberg_types() {
        Schema schema = PojoSchemaGenerator.toIceberg(Person.class);

        assertThat(schema.findField("primaryAddress").type().typeId()).isEqualTo(Type.TypeID.STRUCT);
        assertThat(schema.findField("addresses").type().typeId()).isEqualTo(Type.TypeID.LIST);
        assertThat(schema.findField("tags").type().typeId()).isEqualTo(Type.TypeID.MAP);
    }

    @Test
    void nested_object_conversion_maps_structs_and_nested_lists() {
        Schema schema = PojoSchemaGenerator.toIceberg(OuterPojo.class);

        Types.NestedField mid = schema.findField("mid");
        assertThat(mid).isNotNull();
        assertThat(mid.type().typeId()).isEqualTo(Type.TypeID.STRUCT);

        Types.StructType midStruct = mid.type().asStructType();
        assertThat(midStruct.field("midLabel").type().typeId()).isEqualTo(Type.TypeID.STRING);
        assertThat(midStruct.field("leaf").type().typeId()).isEqualTo(Type.TypeID.STRUCT);
        assertThat(midStruct.field("leaves").type().typeId()).isEqualTo(Type.TypeID.LIST);

        Types.ListType mids = schema.findField("mids").type().asListType();
        assertThat(mids.elementType().typeId()).isEqualTo(Type.TypeID.STRUCT);
        assertThat(mids.elementType().asStructType().field("leaf").type().typeId()).isEqualTo(Type.TypeID.STRUCT);
    }

    @Test
    void complex_nested_object_conversion_maps_deep_lists_maps_and_structs() {
        Schema schema = PojoSchemaGenerator.toIceberg(NestedCollectionsPojo.class);

        Types.ListType matrix = schema.findField("matrix").type().asListType();
        assertThat(matrix.elementType().typeId()).isEqualTo(Type.TypeID.LIST);
        assertThat(matrix.elementType().asListType().elementType().typeId()).isEqualTo(Type.TypeID.INTEGER);

        Types.MapType indexByKey = schema.findField("indexByKey").type().asMapType();
        assertThat(indexByKey.keyType().typeId()).isEqualTo(Type.TypeID.STRING);
        assertThat(indexByKey.valueType().typeId()).isEqualTo(Type.TypeID.LIST);
        assertThat(indexByKey.valueType().asListType().elementType().typeId()).isEqualTo(Type.TypeID.STRING);

        Types.ListType histograms = schema.findField("histograms").type().asListType();
        assertThat(histograms.elementType().typeId()).isEqualTo(Type.TypeID.MAP);
        assertThat(histograms.elementType().asMapType().valueType().typeId()).isEqualTo(Type.TypeID.INTEGER);

        Types.MapType nestedMap = schema.findField("nestedMap").type().asMapType();
        assertThat(nestedMap.valueType().typeId()).isEqualTo(Type.TypeID.MAP);
        assertThat(nestedMap.valueType().asMapType().valueType().typeId()).isEqualTo(Type.TypeID.DOUBLE);

        Types.ListType addresses = schema.findField("addresses").type().asListType();
        assertThat(addresses.elementType().typeId()).isEqualTo(Type.TypeID.STRUCT);
        assertThat(addresses.elementType().asStructType().field("city").type().typeId()).isEqualTo(Type.TypeID.STRING);
        assertThat(addresses.elementType().asStructType().field("postalCode").type().typeId()).isEqualTo(Type.TypeID.INTEGER);
    }

    @Test
    void temporal_and_logical_types_map_to_correct_iceberg_types() {
        Schema schema = PojoSchemaGenerator.toIceberg(TemporalTypesPojo.class);

        assertThat(schema.findField("localDate").type().typeId()).isEqualTo(Type.TypeID.DATE);
        assertThat(schema.findField("sqlDate").type().typeId()).isEqualTo(Type.TypeID.DATE);
        assertThat(schema.findField("localTime").type().typeId()).isEqualTo(Type.TypeID.TIME);

        // UTC-normalizing types (timestamp-millis) → TIMESTAMP with zone
        assertThat(schema.findField("instant").type().typeId()).isEqualTo(Type.TypeID.TIMESTAMP);
        assertThat(schema.findField("offsetDateTime").type().typeId()).isEqualTo(Type.TypeID.TIMESTAMP);
        assertThat(schema.findField("zonedDateTime").type().typeId()).isEqualTo(Type.TypeID.TIMESTAMP);

        // LocalDateTime uses local-timestamp-millis (MILLIS precision default).
        // Iceberg's AvroSchemaUtil only handles local-timestamp-*micros*; the millis
        // variant falls through to LONG. Use TimestampPrecision.MICROS to get TIMESTAMP.
        assertThat(schema.findField("localDateTime").type().typeId()).isEqualTo(Type.TypeID.LONG);
    }

    @Test
    void local_datetime_also_maps_to_long_with_micros_precision() {
        Schema schema = PojoSchemaGenerator.builder()
                .timestampPrecision(io.github.jabhijeet.schema.TimestampPrecision.MICROS)
                .build()
                .generateIceberg(TemporalTypesPojo.class);

        // Iceberg 1.10.1 AvroSchemaUtil does not recognise local-timestamp-micros either;
        // both local-timestamp variants fall through to LONG.
        assertThat(schema.findField("localDateTime").type().typeId()).isEqualTo(Type.TypeID.LONG);
    }

    @Test
    void uuid_maps_to_iceberg_uuid_type() {
        Schema schema = PojoSchemaGenerator.toIceberg(Person.class);

        assertThat(schema.findField("id").type().typeId()).isEqualTo(Type.TypeID.UUID);
    }

    @Test
    void flattened_generation_keeps_flat_field_names_in_iceberg_schema() {
        Schema schema = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .build()
                .generateIceberg(FlattenOuter.class);

        assertThat(schema.findField("inner_c")).isNotNull();
        assertThat(schema.findField("inner_n")).isNotNull();
        assertThat(schema.findField("top")).isNotNull();
    }
}

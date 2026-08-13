package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.fixtures.Address;
import io.github.jabhijeet.schema.fixtures.Color;
import io.github.jabhijeet.schema.fixtures.Employee;
import io.github.jabhijeet.schema.fixtures.Node;
import io.github.jabhijeet.schema.fixtures.NonStringKeyMap;
import io.github.jabhijeet.schema.fixtures.Person;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.parquet.schema.MessageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PojoSchemaGeneratorTest {

    @Test
    void generates_record_with_namespace_and_name_from_package() {
        Schema s = PojoSchemaGenerator.toAvro(Address.class);

        assertThat(s.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(s.getName()).isEqualTo("Address");
        assertThat(s.getNamespace()).isEqualTo("io.github.jabhijeet.schema.fixtures");
    }

    @Test
    void primitives_are_required_and_reference_types_are_nullable_by_default() {
        Schema s = PojoSchemaGenerator.toAvro(Person.class);

        assertThat(fieldType(s, "age").getType()).isEqualTo(Schema.Type.INT);
        assertThat(fieldType(s, "active").getType()).isEqualTo(Schema.Type.BOOLEAN);

        Schema nameField = fieldType(s, "name");
        assertThat(nameField.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(nameField.getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
        assertThat(nameField.getTypes().get(1).getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void nullable_by_default_off_makes_reference_fields_required() {
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder().nullableByDefault(false).build();
        Schema s = gen.generateAvro(Address.class);

        assertThat(fieldType(s, "street").getType()).isEqualTo(Schema.Type.STRING);
        assertThat(fieldType(s, "city").getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void schema_field_overrides_name_and_doc() {
        Schema s = PojoSchemaGenerator.toAvro(Person.class);

        assertThat(s.getField("email")).isNull();
        Schema.Field renamed = s.getField("email_address");
        assertThat(renamed).isNotNull();
        assertThat(renamed.doc()).isEqualTo("Contact email");
    }

    @Test
    void schema_ignore_excludes_field_and_transient_is_skipped() {
        Schema s = PojoSchemaGenerator.toAvro(Person.class);

        assertThat(s.getField("internalNote")).isNull();
        assertThat(s.getField("cachedDisplay")).isNull();
    }

    @Test
    void optional_field_is_always_nullable() {
        Schema nickname = fieldType(PojoSchemaGenerator.toAvro(Person.class), "nickname");

        assertThat(nickname.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(nickname.getTypes()).extracting(Schema::getType)
                .containsExactly(Schema.Type.NULL, Schema.Type.STRING);
    }

    @Test
    void bigdecimal_uses_schema_decimal_annotation() {
        Schema balance = unwrapNullable(fieldType(PojoSchemaGenerator.toAvro(Person.class), "balance"));

        assertThat(balance.getType()).isEqualTo(Schema.Type.BYTES);
        LogicalType lt = balance.getLogicalType();
        assertThat(lt).isInstanceOf(LogicalTypes.Decimal.class);
        LogicalTypes.Decimal d = (LogicalTypes.Decimal) lt;
        assertThat(d.getPrecision()).isEqualTo(12);
        assertThat(d.getScale()).isEqualTo(2);
    }

    @Test
    void date_and_instant_map_to_avro_logical_types() {
        Schema s = PojoSchemaGenerator.toAvro(Person.class);

        Schema dob = unwrapNullable(fieldType(s, "dob"));
        assertThat(dob.getType()).isEqualTo(Schema.Type.INT);
        assertThat(dob.getLogicalType()).isEqualTo(LogicalTypes.date());

        Schema updatedAt = unwrapNullable(fieldType(s, "updatedAt"));
        assertThat(updatedAt.getType()).isEqualTo(Schema.Type.LONG);
        assertThat(updatedAt.getLogicalType()).isEqualTo(LogicalTypes.timestampMillis());
    }

    @Test
    void all_temporal_types_map_to_correct_avro_logical_types() {
        Schema s = PojoSchemaGenerator.toAvro(
                io.github.jabhijeet.schema.fixtures.TemporalTypesPojo.class);

        // LocalDate → date (INT)
        Schema localDate = unwrapNullable(fieldType(s, "localDate"));
        assertThat(localDate.getLogicalType()).isEqualTo(LogicalTypes.date());

        // LocalTime → time-micros (LONG)
        Schema localTime = unwrapNullable(fieldType(s, "localTime"));
        assertThat(localTime.getLogicalType()).isEqualTo(LogicalTypes.timeMicros());

        // LocalDateTime → local-timestamp-millis (LONG, not UTC-adjusted)
        Schema localDateTime = unwrapNullable(fieldType(s, "localDateTime"));
        assertThat(localDateTime.getLogicalType()).isEqualTo(LogicalTypes.localTimestampMillis());

        // Instant → timestamp-millis (LONG, UTC)
        Schema instant = unwrapNullable(fieldType(s, "instant"));
        assertThat(instant.getLogicalType()).isEqualTo(LogicalTypes.timestampMillis());

        // OffsetDateTime / ZonedDateTime → timestamp-millis (UTC, default mode)
        Schema offsetDt = unwrapNullable(fieldType(s, "offsetDateTime"));
        assertThat(offsetDt.getLogicalType()).isEqualTo(LogicalTypes.timestampMillis());

        Schema zonedDt = unwrapNullable(fieldType(s, "zonedDateTime"));
        assertThat(zonedDt.getLogicalType()).isEqualTo(LogicalTypes.timestampMillis());
    }

    @Test
    void uuid_maps_to_string_with_uuid_logical_type() {
        Schema s = PojoSchemaGenerator.toAvro(Person.class);

        Schema id = unwrapNullable(fieldType(s, "id"));
        assertThat(id.getType()).isEqualTo(Schema.Type.STRING);
        assertThat(id.getLogicalType()).isNotNull();
        assertThat(id.getLogicalType().getName()).isEqualTo("uuid");
    }

    @Test
    void enum_becomes_avro_enum() {
        Schema colour = unwrapNullable(fieldType(PojoSchemaGenerator.toAvro(Person.class), "favouriteColor"));

        assertThat(colour.getType()).isEqualTo(Schema.Type.ENUM);
        assertThat(colour.getEnumSymbols()).containsExactly("RED", "GREEN", "BLUE");
        assertThat(colour.getName()).isEqualTo("Color");
    }

    @Test
    void list_becomes_array_and_map_becomes_avro_map() {
        Schema s = PojoSchemaGenerator.toAvro(Person.class);

        Schema addresses = unwrapNullable(fieldType(s, "addresses"));
        assertThat(addresses.getType()).isEqualTo(Schema.Type.ARRAY);
        assertThat(addresses.getElementType().getType()).isEqualTo(Schema.Type.RECORD);

        Schema tags = unwrapNullable(fieldType(s, "tags"));
        assertThat(tags.getType()).isEqualTo(Schema.Type.MAP);
        assertThat(tags.getValueType().getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void nested_pojo_becomes_nested_record() {
        Schema address = unwrapNullable(fieldType(PojoSchemaGenerator.toAvro(Person.class), "primaryAddress"));

        assertThat(address.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(address.getName()).isEqualTo("Address");
    }

    @Test
    void inheritance_includes_superclass_fields_with_superclass_first() {
        Schema s = PojoSchemaGenerator.toAvro(Employee.class);

        assertThat(s.getField("name")).isNotNull();
        assertThat(s.getField("age")).isNotNull();
        assertThat(s.getField("employeeId")).isNotNull();
        assertThat(s.getField("salary")).isNotNull();

        String firstPersonField = s.getFields().get(0).name();
        assertThat(firstPersonField).isIn("id", "name");
    }

    @Test
    void namespace_override_replaces_derived_namespace() {
        Schema s = PojoSchemaGenerator.builder()
                .namespace("com.example.override")
                .build()
                .generateAvro(Address.class);

        assertThat(s.getNamespace()).isEqualTo("com.example.override");
    }

    @Test
    void self_referencing_record_is_supported_via_name_reference() {
        Schema s = PojoSchemaGenerator.toAvro(Node.class);

        Schema children = unwrapNullable(fieldType(s, "children"));
        assertThat(children.getType()).isEqualTo(Schema.Type.ARRAY);
        Schema element = children.getElementType();
        assertThat(element.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(element.getName()).isEqualTo("Node");
    }

    @Test
    void non_string_map_keys_are_rejected() {
        assertThatThrownBy(() -> PojoSchemaGenerator.toAvro(NonStringKeyMap.class))
                .isInstanceOf(SchemaGenerationException.class)
                .hasMessageContaining("String keys");
    }

    @Test
    void parquet_message_type_is_generated_and_matches_field_names() {
        MessageType mt = PojoSchemaGenerator.toParquet(Person.class);

        assertThat(mt.getName()).endsWith("Person");
        assertThat(mt.getFields()).extracting(f -> f.getName())
                .contains("id", "name", "age", "email_address", "favouriteColor");
    }

    @Test
    void iceberg_schema_is_generated_and_matches_field_contracts() {
        org.apache.iceberg.Schema schema = PojoSchemaGenerator.toIceberg(Person.class);

        assertThat(schema.findField("id")).isNotNull();
        assertThat(schema.findField("name").isOptional()).isTrue();
        assertThat(schema.findField("age").isRequired()).isTrue();
        assertThat(schema.findField("email_address")).isNotNull();
        assertThat(schema.findField("favouriteColor").type().typeId()).isEqualTo(Type.TypeID.STRING);

        Types.NestedField balance = schema.findField("balance");
        assertThat(balance.type().typeId()).isEqualTo(Type.TypeID.DECIMAL);
        Types.DecimalType decimal = (Types.DecimalType) balance.type();
        assertThat(decimal.precision()).isEqualTo(12);
        assertThat(decimal.scale()).isEqualTo(2);
    }


    @Test
    void enum_is_not_a_valid_top_level_target() {
        assertThatThrownBy(() -> PojoSchemaGenerator.toAvro(Color.class))
                .isInstanceOf(SchemaGenerationException.class);
    }

    @Test
    void iceberg_rejects_cyclic_records() {
        assertThatThrownBy(() -> PojoSchemaGenerator.toIceberg(Node.class))
                .isInstanceOf(SchemaGenerationException.class)
                .hasMessageContaining("Iceberg cannot represent cyclic records");
    }

    private static Schema fieldType(Schema record, String name) {
        Schema.Field f = record.getField(name);
        if (f == null) throw new AssertionError("Missing field " + name + " in " + record);
        return f.schema();
    }

    private static Schema unwrapNullable(Schema s) {
        if (s.getType() != Schema.Type.UNION) return s;
        return s.getTypes().stream()
                .filter(b -> b.getType() != Schema.Type.NULL)
                .findFirst()
                .orElseThrow();
    }
}


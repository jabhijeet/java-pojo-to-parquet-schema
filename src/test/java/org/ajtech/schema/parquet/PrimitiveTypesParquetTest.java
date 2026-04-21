package org.ajtech.schema.parquet;

import org.ajtech.schema.PojoSchemaGenerator;
import org.ajtech.schema.fixtures.AllPrimitivesPojo;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type.Repetition;
import org.junit.jupiter.api.Test;

import static org.ajtech.schema.parquet.ParquetTestSupport.primitive;
import static org.assertj.core.api.Assertions.assertThat;

class PrimitiveTypesParquetTest {

    private static final MessageType SCHEMA = PojoSchemaGenerator.toParquet(AllPrimitivesPojo.class);

    @Test
    void boolean_primitive_and_boxed_map_to_boolean() {
        assertThat(primitive(SCHEMA, "pBool").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BOOLEAN);
        assertThat(primitive(SCHEMA, "bBool").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BOOLEAN);
    }

    @Test
    void byte_short_int_and_boxed_map_to_int32() {
        assertThat(primitive(SCHEMA, "pByte").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
        assertThat(primitive(SCHEMA, "pShort").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
        assertThat(primitive(SCHEMA, "pInt").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
        assertThat(primitive(SCHEMA, "bByte").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
        assertThat(primitive(SCHEMA, "bShort").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
        assertThat(primitive(SCHEMA, "bInt").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
    }

    @Test
    void long_primitive_and_boxed_map_to_int64() {
        assertThat(primitive(SCHEMA, "pLong").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT64);
        assertThat(primitive(SCHEMA, "bLong").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT64);
    }

    @Test
    void float_primitive_and_boxed_map_to_float() {
        assertThat(primitive(SCHEMA, "pFloat").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.FLOAT);
        assertThat(primitive(SCHEMA, "bFloat").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.FLOAT);
    }

    @Test
    void double_primitive_and_boxed_map_to_double() {
        assertThat(primitive(SCHEMA, "pDouble").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.DOUBLE);
        assertThat(primitive(SCHEMA, "bDouble").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.DOUBLE);
    }

    @Test
    void char_primitive_and_boxed_map_to_binary_string() {
        assertThat(primitive(SCHEMA, "pChar").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);
        assertThat(primitive(SCHEMA, "bChar").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);

        assertThat(primitive(SCHEMA, "pChar").getLogicalTypeAnnotation())
                .isInstanceOf(LogicalTypeAnnotation.StringLogicalTypeAnnotation.class);
    }

    @Test
    void primitives_are_required_and_boxed_are_optional() {
        assertThat(SCHEMA.getType("pBool").getRepetition()).isEqualTo(Repetition.REQUIRED);
        assertThat(SCHEMA.getType("pInt").getRepetition()).isEqualTo(Repetition.REQUIRED);
        assertThat(SCHEMA.getType("pLong").getRepetition()).isEqualTo(Repetition.REQUIRED);
        assertThat(SCHEMA.getType("pDouble").getRepetition()).isEqualTo(Repetition.REQUIRED);

        assertThat(SCHEMA.getType("bBool").getRepetition()).isEqualTo(Repetition.OPTIONAL);
        assertThat(SCHEMA.getType("bInt").getRepetition()).isEqualTo(Repetition.OPTIONAL);
        assertThat(SCHEMA.getType("bLong").getRepetition()).isEqualTo(Repetition.OPTIONAL);
        assertThat(SCHEMA.getType("bDouble").getRepetition()).isEqualTo(Repetition.OPTIONAL);
    }

    @Test
    void every_expected_field_is_present() {
        assertThat(SCHEMA.getFields()).extracting(f -> f.getName()).containsExactlyInAnyOrder(
                "pBool", "pByte", "pShort", "pInt", "pLong", "pFloat", "pDouble", "pChar",
                "bBool", "bByte", "bShort", "bInt", "bLong", "bFloat", "bDouble", "bChar"
        );
    }
}

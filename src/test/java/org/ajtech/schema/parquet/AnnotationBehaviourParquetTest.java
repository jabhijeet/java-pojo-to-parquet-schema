package org.ajtech.schema.parquet;

import org.ajtech.schema.PojoSchemaGenerator;
import org.ajtech.schema.fixtures.AnnotatedPojo;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Type.Repetition;
import org.junit.jupiter.api.Test;

import static org.ajtech.schema.parquet.ParquetTestSupport.primitive;
import static org.assertj.core.api.Assertions.assertThat;

class AnnotationBehaviourParquetTest {

    private static final MessageType SCHEMA = PojoSchemaGenerator.toParquet(AnnotatedPojo.class);

    @Test
    void schema_field_rename_changes_parquet_field_name() {
        assertThat(SCHEMA.getFields()).extracting(Type::getName).contains("user_name");
        assertThat(SCHEMA.getFields()).extracting(Type::getName).doesNotContain("username");
    }

    @Test
    void schema_ignore_removes_the_field_entirely() {
        assertThat(SCHEMA.getFields()).extracting(Type::getName).doesNotContain("secret");
    }

    @Test
    void nullability_required_on_reference_type_produces_required_repetition() {
        assertThat(SCHEMA.getType("tenantId").getRepetition()).isEqualTo(Repetition.REQUIRED);
    }

    @Test
    void nullability_nullable_on_boxed_type_is_optional() {
        assertThat(SCHEMA.getType("retryCount").getRepetition()).isEqualTo(Repetition.OPTIONAL);
    }

    @Test
    void schema_decimal_precision_and_scale_flow_into_parquet() {
        PrimitiveType amount = primitive(SCHEMA, "amount");

        DecimalLogicalTypeAnnotation dec = (DecimalLogicalTypeAnnotation) amount.getLogicalTypeAnnotation();
        assertThat(dec.getPrecision()).isEqualTo(18);
        assertThat(dec.getScale()).isEqualTo(4);
    }

    @Test
    void renamed_field_is_still_optional_by_default() {
        assertThat(SCHEMA.getType("user_name").getRepetition()).isEqualTo(Repetition.OPTIONAL);
    }

    @Test
    void nullable_by_default_off_forces_reference_fields_to_required() {
        MessageType strict = PojoSchemaGenerator.builder()
                .nullableByDefault(false)
                .build()
                .generateParquet(AnnotatedPojo.class);

        assertThat(strict.getType("user_name").getRepetition()).isEqualTo(Repetition.REQUIRED);
        assertThat(strict.getType("amount").getRepetition()).isEqualTo(Repetition.REQUIRED);
        assertThat(strict.getType("retryCount").getRepetition()).isEqualTo(Repetition.OPTIONAL);
    }
}

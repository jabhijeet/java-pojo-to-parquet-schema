package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.fixtures.AcronymFieldsPojo;
import io.github.jabhijeet.schema.fixtures.DefaultValuesPojo;
import org.apache.avro.Schema;
import org.apache.parquet.schema.MessageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for production-hardening fixes: nullable+default union order,
 * acronym-aware name transforms, and Float default value typing.
 */
class ProductionHardeningTest {

    @Test
    void nullable_field_with_non_null_default_produces_valid_avro_schema() {
        // When preserveDefaultValues is on, a nullable reference field whose Java
        // initializer is non-null must place the concrete type first in the union
        // so Avro can validate the default. Previously this threw an Avro
        // AvroTypeException because the union was [null, string] with default
        // "world".
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
                .preserveDefaultValues(true)
                .build();
        Schema schema = gen.generateAvro(DefaultValuesPojo.class);

        Schema.Field nullableLabel = schema.getField("nullableLabel");
        assertThat(nullableLabel).isNotNull();
        assertThat(nullableLabel.schema().getType()).isEqualTo(Schema.Type.UNION);
        // Non-null branch first, because the default is a non-null String.
        assertThat(nullableLabel.schema().getTypes().get(0).getType()).isEqualTo(Schema.Type.STRING);
        assertThat(nullableLabel.schema().getTypes().get(1).getType()).isEqualTo(Schema.Type.NULL);
        assertThat(nullableLabel.defaultVal()).isEqualTo("world");

        // Parquet converter should accept the schema without blowing up.
        MessageType mt = gen.generateParquet(DefaultValuesPojo.class);
        assertThat(mt.getFields()).extracting(f -> f.getName()).contains("nullableLabel");
    }

    @Test
    void nullable_field_without_default_still_uses_null_first() {
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder().build();
        Schema schema = gen.generateAvro(DefaultValuesPojo.class);

        // boxedCount is nullable-by-default; since preserveDefaultValues is OFF,
        // no default is extracted and the union is [null, int] as conventional.
        Schema.Field boxedCount = schema.getField("boxedCount");
        assertThat(boxedCount.schema().getType()).isEqualTo(Schema.Type.UNION);
        assertThat(boxedCount.schema().getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
        assertThat(boxedCount.schema().getTypes().get(1).getType()).isEqualTo(Schema.Type.INT);
    }

    @Test
    void float_default_value_is_a_Float_not_a_Double() {
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
                .preserveDefaultValues(true)
                .build();
        Schema schema = gen.generateAvro(DefaultValuesPojo.class);

        // Primitive float â€” must not be nullable, and the default must be a Float
        // so it agrees with the schema's FLOAT type. Passing a Double would make
        // some Avro paths reject the schema.
        Schema.Field ratio = schema.getField("ratio");
        assertThat(ratio.schema().getType()).isEqualTo(Schema.Type.FLOAT);
        assertThat(ratio.defaultVal()).isInstanceOf(Float.class).isEqualTo(1.5f);
    }

    @Test
    void snake_case_splits_acronym_word_boundaries() {
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
                .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
                .build();
        Schema schema = gen.generateAvro(AcronymFieldsPojo.class);

        assertThat(schema.getField("xml_parser")).isNotNull();
        assertThat(schema.getField("user_id")).isNotNull();
        assertThat(schema.getField("http_url_connection")).isNotNull();
        assertThat(schema.getField("field2_name")).isNotNull();
    }

    @Test
    void upper_snake_case_splits_acronym_word_boundaries() {
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
                .fieldNamingStrategy(FieldNamingStrategy.UPPER_SNAKE_CASE)
                .build();
        Schema schema = gen.generateAvro(AcronymFieldsPojo.class);

        assertThat(schema.getField("XML_PARSER")).isNotNull();
        assertThat(schema.getField("USER_ID")).isNotNull();
    }

    @Test
    void kebab_case_splits_acronym_word_boundaries_and_uses_underscores_for_avro() {
        // Avro field names cannot contain hyphens; KEBAB_CASE falls back to underscores.
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
                .fieldNamingStrategy(FieldNamingStrategy.KEBAB_CASE)
                .build();
        Schema schema = gen.generateAvro(AcronymFieldsPojo.class);

        assertThat(schema.getField("xml_parser")).isNotNull();
        assertThat(schema.getField("user_id")).isNotNull();
    }

    @Test
    void null_pojo_class_produces_illegal_argument_exception() {
        PojoSchemaGenerator gen = PojoSchemaGenerator.builder().build();
        assertThatThrownBy(() -> gen.generateAvro(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pojoClass");
        assertThatThrownBy(() -> gen.generateParquet(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pojoClass");
    }
}


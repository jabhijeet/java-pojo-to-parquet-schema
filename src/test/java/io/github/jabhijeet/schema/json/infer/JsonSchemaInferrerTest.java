package io.github.jabhijeet.schema.json.infer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jabhijeet.schema.FieldNamingStrategy;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaInferrerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultJsonSchemaInferrer INFERRER = new DefaultJsonSchemaInferrer();

    // ---------------------------------------------------------------- primitives (via inferNode reflection or wrapped)

    @Test
    void object_with_boolean_field() throws IOException {
        JsonNode node = MAPPER.readTree("{\"flag\":true}");
        Schema schema = INFERRER.infer(node);
        assertThat(schema.getField("flag").schema().getType()).isEqualTo(Schema.Type.BOOLEAN);
    }

    @Test
    void object_with_integer_field() throws IOException {
        JsonNode node = MAPPER.readTree("{\"count\":42}");
        Schema schema = INFERRER.infer(node);
        assertThat(schema.getField("count").schema().getType()).isEqualTo(Schema.Type.LONG);
    }

    @Test
    void object_with_float_field() throws IOException {
        JsonNode node = MAPPER.readTree("{\"value\":3.14}");
        Schema schema = INFERRER.infer(node);
        assertThat(schema.getField("value").schema().getType()).isEqualTo(Schema.Type.DOUBLE);
    }

    @Test
    void object_with_string_field() throws IOException {
        JsonNode node = MAPPER.readTree("{\"name\":\"hello\"}");
        Schema schema = INFERRER.infer(node);
        assertThat(schema.getField("name").schema().getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void object_with_null_field() throws IOException {
        JsonNode node = MAPPER.readTree("{\"a\":null}");
        Schema schema = INFERRER.infer(node);
        Schema field = schema.getField("a").schema();
        assertThat(field.getType()).isEqualTo(Schema.Type.UNION);
        List<Schema> types = field.getTypes();
        assertThat(types.get(0).getType()).isEqualTo(Schema.Type.NULL);
        assertThat(types.get(1).getType()).isEqualTo(Schema.Type.STRING);
    }

    // ---------------------------------------------------------------- records

    @Test
    void object_infers_record_with_fields() throws IOException {
        JsonNode node = MAPPER.readTree("{\"id\":1,\"name\":\"x\"}");
        Schema schema = INFERRER.infer(node);
        assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(schema.getField("id").schema().getType()).isEqualTo(Schema.Type.LONG);
        assertThat(schema.getField("name").schema().getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void nested_object_infers_nested_record() throws IOException {
        JsonNode node = MAPPER.readTree("{\"user\":{\"id\":1}}");
        Schema schema = INFERRER.infer(node);
        Schema user = schema.getField("user").schema();
        assertThat(user.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(user.getField("id").schema().getType()).isEqualTo(Schema.Type.LONG);
    }

    // ---------------------------------------------------------------- arrays

    @Test
    void array_infers_array_of_element_type() throws IOException {
        JsonNode node = MAPPER.readTree("{\"items\":[1,2,3]}");
        Schema schema = INFERRER.infer(node);
        Schema items = schema.getField("items").schema();
        assertThat(items.getType()).isEqualTo(Schema.Type.ARRAY);
        assertThat(items.getElementType().getType()).isEqualTo(Schema.Type.LONG);
    }

    @Test
    void empty_array_infers_nullable_string_element() throws IOException {
        JsonNode node = MAPPER.readTree("{\"items\":[]}");
        Schema schema = INFERRER.infer(node);
        Schema items = schema.getField("items").schema();
        assertThat(items.getType()).isEqualTo(Schema.Type.ARRAY);
        Schema element = items.getElementType();
        assertThat(element.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(element.getTypes().get(1).getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void mixed_number_array_infers_double() throws IOException {
        DefaultJsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(
                SchemaInferenceOptions.builder().sampleSize(2).build());
        JsonNode node = MAPPER.readTree("{\"items\":[1,2.5]}");
        Schema schema = inferrer.infer(node);
        assertThat(schema.getField("items").schema().getElementType().getType())
                .isEqualTo(Schema.Type.DOUBLE);
    }

    @Test
    void mixed_type_array_infers_string() throws IOException {
        DefaultJsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(
                SchemaInferenceOptions.builder().sampleSize(2).build());
        JsonNode node = MAPPER.readTree("{\"items\":[1,\"two\"]}");
        Schema schema = inferrer.infer(node);
        assertThat(schema.getField("items").schema().getElementType().getType())
                .isEqualTo(Schema.Type.STRING);
    }

    // ---------------------------------------------------------------- root name

    @Test
    void custom_root_name_is_used() throws IOException {
        JsonNode node = MAPPER.readTree("{\"id\":1}");
        Schema schema = INFERRER.infer(node, "MyRoot");
        assertThat(schema.getName()).isEqualTo("MyRoot");
    }

    @Test
    void invalid_root_name_throws() {
        assertThatThrownBy(() -> INFERRER.infer(MAPPER.readTree("{}"), "123bad"))
                .isInstanceOf(SchemaInferenceException.class);
    }

    // ---------------------------------------------------------------- options

    @Test
    void nullableByDefault_false_keeps_required_fields() throws IOException {
        DefaultJsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(
                SchemaInferenceOptions.builder().nullableByDefault(false).build());
        JsonNode node = MAPPER.readTree("{\"id\":1}");
        Schema schema = inferrer.infer(node);
        assertThat(schema.getField("id").schema().getType()).isEqualTo(Schema.Type.LONG);
    }

    @Test
    void snake_case_naming_strategy() throws IOException {
        DefaultJsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(
                SchemaInferenceOptions.builder()
                        .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
                        .build());
        JsonNode node = MAPPER.readTree("{\"firstName\":\"x\"}");
        Schema schema = inferrer.infer(node);
        assertThat(schema.getField("first_name")).isNotNull();
    }

    @Test
    void max_depth_guards_against_deep_recursion() throws IOException {
        DefaultJsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(
                SchemaInferenceOptions.builder().maxDepth(2).build());
        String deep = "{\"a\":{\"b\":{\"c\":1}}}";
        try {
            inferrer.infer(deep);
            org.junit.jupiter.api.Assertions.fail("expected SchemaInferenceException");
        } catch (SchemaInferenceException ex) {
            assertThat(ex.getMessage()).contains("Failed to parse JSON for schema inference");
            assertThat(ex.getCause()).isInstanceOf(SchemaInferenceException.class)
                    .hasMessageContaining("Max depth");
        }
    }

    @Test
    void sample_size_inspects_multiple_elements() throws IOException {
        DefaultJsonSchemaInferrer inferrer = new DefaultJsonSchemaInferrer(
                SchemaInferenceOptions.builder().sampleSize(2).build());
        JsonNode node = MAPPER.readTree("{\"items\":[1,\"two\"]}");
        Schema schema = inferrer.infer(node);
        assertThat(schema.getField("items").schema().getElementType().getType())
                .isEqualTo(Schema.Type.STRING);
    }
}

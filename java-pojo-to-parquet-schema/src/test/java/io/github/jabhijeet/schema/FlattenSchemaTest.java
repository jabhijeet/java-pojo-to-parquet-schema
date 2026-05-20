package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.fixtures.FlattenAllRequired;
import io.github.jabhijeet.schema.fixtures.FlattenCollisionPojo;
import io.github.jabhijeet.schema.fixtures.FlattenCycleA;
import io.github.jabhijeet.schema.fixtures.FlattenEmptyHolder;
import io.github.jabhijeet.schema.fixtures.FlattenInner;
import io.github.jabhijeet.schema.fixtures.FlattenNullableIntermediate;
import io.github.jabhijeet.schema.fixtures.FlattenOuter;
import io.github.jabhijeet.schema.fixtures.FlattenRenamedIntermediate;
import io.github.jabhijeet.schema.fixtures.FlattenRenamedLeafOuter;
import io.github.jabhijeet.schema.fixtures.FlattenSnakeCase;
import io.github.jabhijeet.schema.fixtures.FlattenThreeL1;
import io.github.jabhijeet.schema.fixtures.FlattenWithListNested;
import io.github.jabhijeet.schema.fixtures.FlattenWithMapNested;
import io.github.jabhijeet.schema.fixtures.Node;
import org.apache.avro.Schema;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlattenSchemaTest {

    private static PojoSchemaGenerator flat() {
        return PojoSchemaGenerator.builder().flattenNestedRecords(true).build();
    }

    @Test
    void defaults_flatten_off() {
        assertThat(SchemaOptions.defaults().flattenNestedRecords()).isFalse();
        assertThat(SchemaOptions.defaults().flattenSeparator()).isEqualTo("_");
        assertThat(SchemaOptions.defaults().flattenCollisionStrategy())
                .isEqualTo(FlattenCollisionStrategy.THROW);
    }

    @Test
    void flattens_two_levels() {
        Schema s = flat().generateAvro(FlattenOuter.class);

        assertThat(s.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(s.getName()).isEqualTo("FlattenOuter");
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactlyInAnyOrder("inner_c", "inner_n", "top");
        assertThat(unwrapNullable(s.getField("inner_c").schema()).getType())
                .isEqualTo(Schema.Type.STRING);
        assertThat(unwrapNullable(s.getField("top").schema()).getType())
                .isEqualTo(Schema.Type.STRING);
    }

    @Test
    void flattens_three_levels() {
        Schema s = flat().generateAvro(FlattenThreeL1.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactly("b_c_leaf");
    }

    @Test
    void custom_separator_is_used() {
        Schema s = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .flattenSeparator("XX")
                .build()
                .generateAvro(FlattenOuter.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactlyInAnyOrder("innerXXc", "innerXXn", "top");
    }

    @Test
    void separator_validation_rejects_invalid_chars() {
        assertThatThrownBy(() -> SchemaOptions.builder().flattenSeparator("."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaOptions.builder().flattenSeparator("-"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaOptions.builder().flattenSeparator(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemaOptions.builder().flattenSeparator(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullable_ancestor_makes_required_leaf_nullable() {
        Schema s = flat().generateAvro(FlattenNullableIntermediate.class);
        Schema leaf = s.getField("inner_c").schema();
        assertThat(leaf.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(leaf.getTypes()).extracting(Schema::getType)
                .containsExactly(Schema.Type.NULL, Schema.Type.STRING);
    }

    @Test
    void all_required_path_produces_required_leaf() {
        Schema s = flat().generateAvro(FlattenAllRequired.class);
        Schema leaf = s.getField("inner_c").schema();
        assertThat(leaf.getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void flatten_stops_at_list_boundary() {
        Schema s = flat().generateAvro(FlattenWithListNested.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactlyInAnyOrder("items", "top");
        Schema items = unwrapNullable(s.getField("items").schema());
        assertThat(items.getType()).isEqualTo(Schema.Type.ARRAY);
        Schema element = items.getElementType();
        assertThat(element.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(element.getFields()).extracting(Schema.Field::name)
                .containsExactlyInAnyOrder("c", "n");
    }

    @Test
    void flatten_stops_at_map_boundary() {
        Schema s = flat().generateAvro(FlattenWithMapNested.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactlyInAnyOrder("byKey", "top");
        Schema map = unwrapNullable(s.getField("byKey").schema());
        assertThat(map.getType()).isEqualTo(Schema.Type.MAP);
        assertThat(map.getValueType().getType()).isEqualTo(Schema.Type.RECORD);
    }

    @Test
    void honors_rename_on_intermediate() {
        Schema s = flat().generateAvro(FlattenRenamedIntermediate.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactlyInAnyOrder("alias_c", "alias_n");
    }

    @Test
    void honors_rename_on_leaf() {
        Schema s = flat().generateAvro(FlattenRenamedLeafOuter.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactly("inner_zz");
    }

    @Test
    void honors_snake_case_naming_strategy() {
        Schema s = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
                .build()
                .generateAvro(FlattenSnakeCase.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactly("customer_address_zip_code");
    }

    @Test
    void detects_direct_cycle() {
        assertThatThrownBy(() -> flat().generateAvro(FlattenCycleA.class))
                .isInstanceOf(SchemaGenerationException.class)
                .hasMessageContaining("cyclic");
    }

    @Test
    void allows_self_reference_via_list() {
        Schema s = flat().generateAvro(Node.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactlyInAnyOrder("value", "children");
        Schema children = unwrapNullable(s.getField("children").schema());
        assertThat(children.getType()).isEqualTo(Schema.Type.ARRAY);
    }

    @Test
    void collision_throws_by_default() {
        assertThatThrownBy(() -> flat().generateAvro(FlattenCollisionPojo.class))
                .isInstanceOf(SchemaGenerationException.class)
                .hasMessageContaining("duplicate field 'foo_bar'")
                .hasMessageContaining("foo_bar")
                .hasMessageContaining("foo.bar");
    }

    @Test
    void collision_auto_renames_when_configured() {
        Schema s = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .flattenCollisionStrategy(FlattenCollisionStrategy.AUTO_RENAME)
                .build()
                .generateAvro(FlattenCollisionPojo.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .contains("foo_bar", "foo_bar__1");
    }

    @Test
    void empty_nested_record_is_elided() {
        Schema s = flat().generateAvro(FlattenEmptyHolder.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactly("top");
    }

    @Test
    void top_level_schema_marked_with_props() {
        Schema s = flat().generateAvro(FlattenOuter.class);
        assertThat(s.getObjectProp(SchemaProps.FLATTENED)).isEqualTo(Boolean.TRUE);
        assertThat(s.getObjectProp(SchemaProps.FLATTEN_SEPARATOR)).isEqualTo("_");
    }

    @Test
    void default_off_back_compat_nested_schema_is_unchanged() {
        Schema s = PojoSchemaGenerator.toAvro(FlattenOuter.class);
        assertThat(s.getFields()).extracting(Schema.Field::name)
                .containsExactly("inner", "top");
        Schema inner = unwrapNullable(s.getField("inner").schema());
        assertThat(inner.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(inner.getFields()).extracting(Schema.Field::name)
                .containsExactly("c", "n");
        assertThat(s.getObjectProp(SchemaProps.FLATTENED)).isNull();
    }

    @Test
    void parquet_round_trip_is_flat() {
        MessageType mt = flat().generateParquet(FlattenOuter.class);
        assertThat(mt.getFields()).extracting(Type::getName)
                .containsExactlyInAnyOrder("inner_c", "inner_n", "top");
        for (Type f : mt.getFields()) {
            assertThat(f.isPrimitive())
                    .as("expected flat primitive field, got group: " + f)
                    .isTrue();
        }
    }

    @Test
    void cache_isolates_flat_vs_nested() {
        PojoSchemaGenerator withCache = PojoSchemaGenerator.builder()
                .cacheStrategy(CacheStrategy.LRU)
                .cacheSize(16)
                .build();
        PojoSchemaGenerator withFlat = PojoSchemaGenerator.builder()
                .flattenNestedRecords(true)
                .cacheStrategy(CacheStrategy.LRU)
                .cacheSize(16)
                .build();
        Schema nested = withCache.generateAvro(FlattenOuter.class);
        Schema flattened = withFlat.generateAvro(FlattenOuter.class);
        assertThat(nested.getField("inner")).isNotNull();
        assertThat(flattened.getField("inner")).isNull();
        assertThat(flattened.getField("inner_c")).isNotNull();
    }

    private static Schema unwrapNullable(Schema s) {
        if (s.getType() != Schema.Type.UNION) return s;
        return s.getTypes().stream()
                .filter(b -> b.getType() != Schema.Type.NULL)
                .findFirst()
                .orElseThrow();
    }

    // Consume the FlattenInner import so static-analysis tools don't flag it.
    @SuppressWarnings("unused")
    private static final Class<?> FIXTURE_IN_USE = FlattenInner.class;
}

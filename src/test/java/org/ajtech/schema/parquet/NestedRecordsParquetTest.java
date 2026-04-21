package org.ajtech.schema.parquet;

import org.ajtech.schema.PojoSchemaGenerator;
import org.ajtech.schema.SchemaGenerationException;
import org.ajtech.schema.fixtures.DerivedPojo;
import org.ajtech.schema.fixtures.Node;
import org.ajtech.schema.fixtures.OuterPojo;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.junit.jupiter.api.Test;

import static org.ajtech.schema.parquet.ParquetTestSupport.group;
import static org.ajtech.schema.parquet.ParquetTestSupport.listElement;
import static org.ajtech.schema.parquet.ParquetTestSupport.primitive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NestedRecordsParquetTest {

    @Test
    void outer_contains_mid_group_with_its_fields() {
        MessageType schema = PojoSchemaGenerator.toParquet(OuterPojo.class);

        assertThat(schema.getFields()).extracting(Type::getName).contains("outerName", "mid", "mids");
        GroupType mid = group(schema, "mid");
        assertThat(mid.getFields()).extracting(Type::getName).contains("midLabel", "leaf", "leaves");
    }

    @Test
    void three_level_nesting_preserves_leaf_primitive_types() {
        MessageType schema = PojoSchemaGenerator.toParquet(OuterPojo.class);
        GroupType mid = group(schema, "mid");
        GroupType leaf = group(mid, "leaf");

        assertThat(primitive(leaf, "leafValue").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
        assertThat(primitive(leaf, "leafLabel").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);
    }

    @Test
    void list_of_records_inside_nested_record() {
        MessageType schema = PojoSchemaGenerator.toParquet(OuterPojo.class);
        GroupType mid = group(schema, "mid");
        GroupType leaves = group(mid, "leaves");

        Type element = listElement(leaves);
        GroupType leafGroup = element.asGroupType();
        assertThat(leafGroup.getFields()).extracting(Type::getName).contains("leafValue", "leafLabel");
    }

    @Test
    void list_of_mids_inside_outer_descends_to_leaf_list() {
        MessageType schema = PojoSchemaGenerator.toParquet(OuterPojo.class);
        GroupType mids = group(schema, "mids");

        Type midElement = listElement(mids);
        GroupType midGroup = midElement.asGroupType();
        GroupType leavesInsideMid = group(midGroup, "leaves");
        Type leafElement = listElement(leavesInsideMid);
        GroupType leafGroup = leafElement.asGroupType();

        assertThat(primitive(leafGroup, "leafValue").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
    }

    @Test
    void self_referential_records_are_rejected_because_parquet_is_acyclic() {
        assertThatThrownBy(() -> PojoSchemaGenerator.toParquet(Node.class))
                .isInstanceOf(SchemaGenerationException.class)
                .hasMessageContaining("cyclic");
    }

    @Test
    void inheritance_includes_fields_from_base_and_derived() {
        MessageType schema = PojoSchemaGenerator.toParquet(DerivedPojo.class);

        assertThat(schema.getFields()).extracting(Type::getName)
                .containsExactlyInAnyOrder("id", "createdAt", "label", "count");
        assertThat(primitive(schema, "createdAt").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT64);
        assertThat(primitive(schema, "count").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
    }

    @Test
    void inherited_primitive_retains_required_repetition() {
        MessageType schema = PojoSchemaGenerator.toParquet(DerivedPojo.class);

        assertThat(schema.getType("createdAt").getRepetition()).isEqualTo(Type.Repetition.REQUIRED);
        assertThat(schema.getType("count").getRepetition()).isEqualTo(Type.Repetition.REQUIRED);
    }
}

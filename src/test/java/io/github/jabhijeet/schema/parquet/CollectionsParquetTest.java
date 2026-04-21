package io.github.jabhijeet.schema.parquet;

import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.fixtures.CollectionsPojo;
import io.github.jabhijeet.schema.fixtures.NestedCollectionsPojo;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation.ListLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.MapLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.StringLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.junit.jupiter.api.Test;

import static io.github.jabhijeet.schema.parquet.ParquetTestSupport.group;
import static io.github.jabhijeet.schema.parquet.ParquetTestSupport.listElement;
import static io.github.jabhijeet.schema.parquet.ParquetTestSupport.mapKey;
import static io.github.jabhijeet.schema.parquet.ParquetTestSupport.mapValue;
import static org.assertj.core.api.Assertions.assertThat;

class CollectionsParquetTest {

    private static final MessageType FLAT = PojoSchemaGenerator.toParquet(CollectionsPojo.class);
    private static final MessageType NESTED = PojoSchemaGenerator.toParquet(NestedCollectionsPojo.class);

    @Test
    void list_of_string_is_list_group_of_binary_string() {
        GroupType tags = group(FLAT, "tags");

        assertThat(tags.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);
        Type element = listElement(tags);
        assertThat(element.asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);
        assertThat(element.getLogicalTypeAnnotation()).isInstanceOf(StringLogicalTypeAnnotation.class);
    }

    @Test
    void set_maps_to_list_group_just_like_list() {
        GroupType scores = group(FLAT, "scores");

        assertThat(scores.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);
        assertThat(listElement(scores).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
    }

    @Test
    void any_collection_subtype_maps_to_list_group() {
        GroupType readings = group(FLAT, "readings");

        assertThat(readings.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);
        assertThat(listElement(readings).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.DOUBLE);
    }

    @Test
    void map_with_string_keys_is_map_group_with_string_key_and_long_value() {
        GroupType counts = group(FLAT, "counts");

        assertThat(counts.getLogicalTypeAnnotation()).isInstanceOf(MapLogicalTypeAnnotation.class);
        assertThat(mapKey(counts).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);
        assertThat(mapKey(counts).getLogicalTypeAnnotation()).isInstanceOf(StringLogicalTypeAnnotation.class);
        assertThat(mapValue(counts).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT64);
    }

    @Test
    void list_of_list_preserves_both_levels() {
        GroupType matrix = group(NESTED, "matrix");
        assertThat(matrix.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);

        Type innerListType = listElement(matrix);
        GroupType innerList = innerListType.asGroupType();
        assertThat(innerList.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);

        Type innermost = listElement(innerList);
        assertThat(innermost.asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
    }

    @Test
    void map_of_list_keeps_map_outside_and_list_inside() {
        GroupType indexByKey = group(NESTED, "indexByKey");
        assertThat(indexByKey.getLogicalTypeAnnotation()).isInstanceOf(MapLogicalTypeAnnotation.class);

        Type value = mapValue(indexByKey);
        GroupType valueList = value.asGroupType();
        assertThat(valueList.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);
        assertThat(listElement(valueList).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);
    }

    @Test
    void list_of_map_keeps_list_outside_and_map_inside() {
        GroupType histograms = group(NESTED, "histograms");
        assertThat(histograms.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);

        Type element = listElement(histograms);
        GroupType elementMap = element.asGroupType();
        assertThat(elementMap.getLogicalTypeAnnotation()).isInstanceOf(MapLogicalTypeAnnotation.class);
        assertThat(mapValue(elementMap).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
    }

    @Test
    void map_of_map_nests_correctly() {
        GroupType nestedMap = group(NESTED, "nestedMap");

        Type value = mapValue(nestedMap);
        GroupType inner = value.asGroupType();
        assertThat(inner.getLogicalTypeAnnotation()).isInstanceOf(MapLogicalTypeAnnotation.class);
        assertThat(mapValue(inner).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.DOUBLE);
    }

    @Test
    void list_of_records_produces_group_inside_list_element() {
        GroupType addresses = group(NESTED, "addresses");

        Type element = listElement(addresses);
        assertThat(element.isPrimitive()).isFalse();
        GroupType addrGroup = element.asGroupType();
        assertThat(addrGroup.getFields()).extracting(Type::getName)
                .contains("street", "city", "postalCode");
    }
}


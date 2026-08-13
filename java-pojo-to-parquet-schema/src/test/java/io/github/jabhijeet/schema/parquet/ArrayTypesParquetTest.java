package io.github.jabhijeet.schema.parquet;

import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.fixtures.ArrayTypesPojo;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation.ListLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.StringLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.junit.jupiter.api.Test;

import static io.github.jabhijeet.schema.parquet.ParquetTestSupport.group;
import static io.github.jabhijeet.schema.parquet.ParquetTestSupport.listElement;
import static io.github.jabhijeet.schema.parquet.ParquetTestSupport.primitive;
import static org.assertj.core.api.Assertions.assertThat;

class ArrayTypesParquetTest {

    private static final MessageType SCHEMA = PojoSchemaGenerator.toParquet(ArrayTypesPojo.class);

    @Test
    void int_array_is_list_of_int32() {
        GroupType ints = group(SCHEMA, "ints");
        assertThat(ints.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);
        assertThat(listElement(ints).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
    }

    @Test
    void long_array_is_list_of_int64() {
        GroupType longs = group(SCHEMA, "longs");
        assertThat(longs.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);
        assertThat(listElement(longs).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT64);
    }

    @Test
    void boolean_array_is_list_of_boolean() {
        GroupType bools = group(SCHEMA, "bools");
        assertThat(bools.getLogicalTypeAnnotation()).isInstanceOf(ListLogicalTypeAnnotation.class);
        assertThat(listElement(bools).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BOOLEAN);
    }

    @Test
    void double_array_is_list_of_double() {
        GroupType doubles = group(SCHEMA, "doubles");
        assertThat(listElement(doubles).asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.DOUBLE);
    }

    @Test
    void string_array_is_list_of_string() {
        GroupType strings = group(SCHEMA, "strings");
        Type element = listElement(strings);

        assertThat(element.asPrimitiveType().getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);
        assertThat(element.getLogicalTypeAnnotation()).isInstanceOf(StringLogicalTypeAnnotation.class);
    }

    @Test
    void record_array_is_list_of_group_with_record_fields() {
        GroupType addresses = group(SCHEMA, "addresses");
        Type element = listElement(addresses);

        assertThat(element.isPrimitive()).isFalse();
        GroupType addrGroup = element.asGroupType();
        assertThat(addrGroup.getFields()).extracting(Type::getName)
                .contains("street", "city", "postalCode");
        assertThat(primitive(addrGroup, "postalCode").getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.INT32);
    }

    @Test
    void byte_array_is_special_cased_to_binary_not_a_list() {
        Type bytes = SCHEMA.getType("bytes");

        assertThat(bytes.isPrimitive()).isTrue();
        PrimitiveType pt = bytes.asPrimitiveType();
        assertThat(pt.getPrimitiveTypeName()).isEqualTo(PrimitiveTypeName.BINARY);
        assertThat(pt.getLogicalTypeAnnotation()).isNull();
    }
}


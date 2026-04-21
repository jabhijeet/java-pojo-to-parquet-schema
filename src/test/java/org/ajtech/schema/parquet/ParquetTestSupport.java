package org.ajtech.schema.parquet;

import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/** Navigation helpers for Parquet MessageType trees. */
final class ParquetTestSupport {

    private ParquetTestSupport() {}

    static PrimitiveType primitive(GroupType group, String name) {
        Type field = group.getType(name);
        if (!field.isPrimitive()) {
            throw new AssertionError("Field '" + name + "' is not primitive: " + field);
        }
        return field.asPrimitiveType();
    }

    static GroupType group(GroupType parent, String name) {
        Type field = parent.getType(name);
        if (field.isPrimitive()) {
            throw new AssertionError("Field '" + name + "' is primitive: " + field);
        }
        return field.asGroupType();
    }

    /**
     * Element of a parquet-avro LIST group. The default encoding is 2-level, where the
     * repeated field named {@code array} IS the element (primitive) or IS the element
     * group (record).
     */
    static Type listElement(GroupType listField) {
        return listField.getType("array");
    }

    /** Key of a MAP group: {@code <map> -> key_value -> key}. */
    static Type mapKey(GroupType mapField) {
        return mapField.getType("key_value").asGroupType().getType("key");
    }

    /** Value of a MAP group: {@code <map> -> key_value -> value}. */
    static Type mapValue(GroupType mapField) {
        return mapField.getType("key_value").asGroupType().getType("value");
    }
}

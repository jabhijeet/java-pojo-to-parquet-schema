package io.github.jabhijeet.schema.iceberg;

import io.github.jabhijeet.schema.SchemaGenerationException;
import io.github.jabhijeet.schema.SchemaOptions;
import io.github.jabhijeet.schema.avro.AvroSchemaBuilder;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Produces an Iceberg {@link org.apache.iceberg.Schema} by first generating an
 * Avro {@link Schema} and then converting it with Iceberg's Avro bridge.
 *
 * <p>This preserves the library's existing reflection rules, annotations,
 * flattening behavior, and field naming strategy across all schema targets.
 *
 * <p>Cyclic record types cannot be represented in Iceberg. They are detected
 * before conversion and surfaced as a {@link SchemaGenerationException}.
 */
public final class IcebergSchemaBuilder {

    private final SchemaOptions options;

    public IcebergSchemaBuilder(SchemaOptions options) {
        this.options = options;
    }

    public org.apache.iceberg.Schema build(Class<?> pojoClass) {
        return buildFromAvro(new AvroSchemaBuilder(options).build(pojoClass), pojoClass);
    }

    public org.apache.iceberg.Schema buildFromAvro(Schema avro, Class<?> pojoClass) {
        if (avro == null) throw new IllegalArgumentException("avro must not be null");
        detectCycle(avro, new HashSet<>(), pojoClass);
        try {
            return fromAvro(avro);
        } catch (RuntimeException e) {
            String rootName = pojoClass != null ? pojoClass.getName() : "<unknown>";
            throw new SchemaGenerationException(
                    "Failed to convert Avro schema to Iceberg schema for '" + rootName + "'", e);
        }
    }

    /** Converts Avro while preserving local-timestamp logical types. */
    public static org.apache.iceberg.Schema fromAvro(Schema avro) {
        Objects.requireNonNull(avro, "avro");
        org.apache.iceberg.Schema converted = AvroSchemaUtil.toIceberg(avro);
        if (avro.getType() != Schema.Type.RECORD) {
            throw new IllegalArgumentException("Avro table schema must be a record");
        }
        return new org.apache.iceberg.Schema(patchStruct(converted.asStruct(), avro).fields());
    }

    private static Types.StructType patchStruct(Types.StructType base, Schema avro) {
        List<Types.NestedField> fields = new ArrayList<>();
        for (Types.NestedField field : base.fields()) {
            Schema.Field avroField = avro.getField(field.name());
            if (avroField == null) {
                throw new IllegalArgumentException("Avro field missing: " + field.name());
            }
            fields.add(Types.NestedField.of(field.fieldId(), field.isOptional(), field.name(),
                    patchType(field.type(), unwrapNullable(avroField.schema())), field.doc()));
        }
        return Types.StructType.of(fields);
    }

    private static Type patchType(Type base, Schema avro) {
        LogicalType logical = avro.getLogicalType();
        if (logical instanceof LogicalTypes.LocalTimestampMillis
                || logical instanceof LogicalTypes.LocalTimestampMicros) {
            return Types.TimestampType.withoutZone();
        }
        return switch (base.typeId()) {
            case STRUCT -> patchStruct(base.asStructType(), avro);
            case LIST -> {
                Type element = patchType(base.asListType().elementType(),
                        unwrapNullable(avro.getElementType()));
                yield base.asListType().isElementRequired()
                        ? Types.ListType.ofRequired(base.asListType().elementId(), element)
                        : Types.ListType.ofOptional(base.asListType().elementId(), element);
            }
            case MAP -> {
                Type value = patchType(base.asMapType().valueType(),
                        unwrapNullable(avro.getValueType()));
                yield base.asMapType().isValueRequired()
                        ? Types.MapType.ofRequired(base.asMapType().keyId(), base.asMapType().valueId(),
                        base.asMapType().keyType(), value)
                        : Types.MapType.ofOptional(base.asMapType().keyId(), base.asMapType().valueId(),
                        base.asMapType().keyType(), value);
            }
            default -> base;
        };
    }

    private static Schema unwrapNullable(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) return schema;
        for (Schema branch : schema.getTypes()) {
            if (branch.getType() != Schema.Type.NULL) return branch;
        }
        return schema;
    }

    private static void detectCycle(Schema schema, Set<String> stack, Class<?> rootType) {
        String rootName = rootType != null ? rootType.getName() : "<unknown>";
        switch (schema.getType()) {
            case RECORD -> {
                String name = schema.getFullName();
                if (!stack.add(name)) {
                    throw new SchemaGenerationException(
                            "Iceberg cannot represent cyclic records; '" + rootName
                                    + "' references itself via '" + name
                                    + "'. Break the cycle with @SchemaIgnore.");
                }
                try {
                    for (Schema.Field f : schema.getFields()) {
                        detectCycle(f.schema(), stack, rootType);
                    }
                } finally {
                    stack.remove(name);
                }
            }
            case UNION -> {
                for (Schema branch : schema.getTypes()) detectCycle(branch, stack, rootType);
            }
            case ARRAY -> detectCycle(schema.getElementType(), stack, rootType);
            case MAP -> detectCycle(schema.getValueType(), stack, rootType);
            default -> {
                // primitive/logical
            }
        }
    }
}

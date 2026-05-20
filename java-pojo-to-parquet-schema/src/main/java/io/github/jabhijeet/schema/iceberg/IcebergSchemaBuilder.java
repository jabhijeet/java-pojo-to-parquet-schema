package io.github.jabhijeet.schema.iceberg;

import io.github.jabhijeet.schema.SchemaGenerationException;
import io.github.jabhijeet.schema.SchemaOptions;
import io.github.jabhijeet.schema.avro.AvroSchemaBuilder;
import org.apache.avro.Schema;
import org.apache.iceberg.avro.AvroSchemaUtil;

import java.util.HashSet;
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
            return AvroSchemaUtil.toIceberg(avro);
        } catch (RuntimeException e) {
            String rootName = pojoClass != null ? pojoClass.getName() : "<unknown>";
            throw new SchemaGenerationException(
                    "Failed to convert Avro schema to Iceberg schema for '" + rootName + "'", e);
        }
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

package io.github.jabhijeet.schema.parquet;

import io.github.jabhijeet.schema.SchemaGenerationException;
import io.github.jabhijeet.schema.SchemaOptions;
import io.github.jabhijeet.schema.avro.AvroSchemaBuilder;
import org.apache.avro.Schema;
import org.apache.parquet.avro.AvroSchemaConverter;
import org.apache.parquet.schema.MessageType;

import java.util.HashSet;
import java.util.Set;

/**
 * Produces a Parquet {@link MessageType} by first generating an Avro
 * {@link Schema} and then delegating to Parquet's {@link AvroSchemaConverter}.
 *
 * <p>Uses parquet-avro's default LIST encoding (legacy 2-level, {@code repeated â€¦ array}).
 * All modern Parquet readers accept both 2-level and 3-level encodings, so this is
 * the no-friction choice that avoids pulling in hadoop-common.
 *
 * <p>Cyclic record types cannot be represented in Parquet (its schema is a
 * finite tree). They are detected before conversion and surface as a
 * {@link SchemaGenerationException} rather than a native {@code StackOverflowError}.
 */
public final class ParquetSchemaBuilder {

    private final SchemaOptions options;

    public ParquetSchemaBuilder(SchemaOptions options) {
        this.options = options;
    }

    public MessageType build(Class<?> pojoClass) {
        return buildFromAvro(new AvroSchemaBuilder(options).build(pojoClass), pojoClass);
    }

    /**
     * Converts a pre-built Avro {@link Schema} into a Parquet {@link MessageType}.
     * Exposed so callers that have already generated the Avro schema (e.g. via
     * {@code PojoSchemaGenerator}) do not have to rebuild it.
     */
    public MessageType buildFromAvro(Schema avro, Class<?> pojoClass) {
        if (avro == null) throw new IllegalArgumentException("avro must not be null");
        detectCycle(avro, new HashSet<>(), pojoClass);
        return new AvroSchemaConverter().convert(avro);
    }

    private static void detectCycle(Schema schema, Set<String> stack, Class<?> rootType) {
        switch (schema.getType()) {
            case RECORD -> {
                String name = schema.getFullName();
                if (!stack.add(name)) {
                    throw new SchemaGenerationException(
                            "Parquet cannot represent cyclic records; '" + rootType.getName()
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
            default -> { /* primitive or logical â€” nothing to walk */ }
        }
    }
}


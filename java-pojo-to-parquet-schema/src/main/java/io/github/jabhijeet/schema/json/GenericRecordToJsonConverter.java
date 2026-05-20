package io.github.jabhijeet.schema.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.jabhijeet.schema.SchemaProps;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Converts a {@link GenericRecord} (Avro or Parquet) to a JSON string.
 *
 * <p>For flat schemas (carrying {@code pojoSchemaFlattened=true}), flat fields are
 * reconstructed into a nested JSON document using each field's
 * {@code pojoSchemaFlattenSourcePath} property — the reverse of what
 * {@link JsonToGenericRecordConverter} does on read.
 *
 * <p>Type mapping (reverse of {@link JsonToGenericRecordConverter}):
 * <ul>
 *   <li>Primitives → JSON scalars.</li>
 *   <li>Date/time logical types → ISO-8601 strings.</li>
 *   <li>Decimal bytes → plain decimal string (e.g. {@code "95000.00"}).</li>
 *   <li>Other bytes/fixed → standard Base64 string.</li>
 *   <li>Arrays → JSON arrays; maps → JSON objects.</li>
 *   <li>Unions → the non-null branch value, or {@code null}.</li>
 * </ul>
 *
 * <p>Instances are stateless and thread-safe.
 */
public final class GenericRecordToJsonConverter {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder BASE64 = Base64.getEncoder();
    private static final Conversions.DecimalConversion DECIMAL_CONVERSION = new Conversions.DecimalConversion();

    private final ObjectMapper mapper;

    public GenericRecordToJsonConverter() {
        this(DEFAULT_MAPPER);
    }

    public GenericRecordToJsonConverter(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Converts a {@link GenericRecord} to a compact JSON string.
     */
    public String convert(GenericRecord record) {
        return convert(record, false);
    }

    /**
     * Converts a {@link GenericRecord} to a JSON string.
     *
     * @param pretty if {@code true}, output is pretty-printed
     */
    public String convert(GenericRecord record, boolean pretty) {
        Objects.requireNonNull(record, "record");
        JsonNode node = recordToNode(record);
        try {
            return pretty
                    ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
                    : mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize record to JSON", e);
        }
    }

    // ---------------------------------------------------------------- record → node

    private JsonNode recordToNode(GenericRecord record) {
        Schema schema = record.getSchema();
        boolean flat = Boolean.TRUE.equals(schema.getObjectProp(SchemaProps.FLATTENED));
        return flat ? flatRecordToNode(record, schema) : regularRecordToNode(record, schema);
    }

    private ObjectNode regularRecordToNode(GenericRecord record, Schema schema) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        for (Schema.Field field : schema.getFields()) {
            out.set(field.name(), convertValue(record.get(field.name()), field.schema()));
        }
        return out;
    }

    /**
     * Reconstructs a nested JSON document from a flat Avro record by walking each
     * field's {@code pojoSchemaFlattenSourcePath} (e.g. {@code "dept.name"}) to
     * place the value at the correct nested position.
     */
    private ObjectNode flatRecordToNode(GenericRecord record, Schema schema) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        for (Schema.Field field : schema.getFields()) {
            Object value = record.get(field.name());
            JsonNode valueNode = convertValue(value, field.schema());

            Object sourcePath = field.getObjectProp(SchemaProps.FLATTEN_SOURCE_PATH);
            String[] parts;
            if (sourcePath instanceof String s && !s.isEmpty()) {
                parts = s.split("\\.", -1);
            } else {
                parts = new String[]{field.name()};
            }

            // Navigate/create intermediate ObjectNodes, then place leaf value.
            ObjectNode target = out;
            for (int i = 0; i < parts.length - 1; i++) {
                String segment = parts[i];
                JsonNode existing = target.get(segment);
                if (existing == null || existing.isNull() || existing.isMissingNode()) {
                    ObjectNode nested = JsonNodeFactory.instance.objectNode();
                    target.set(segment, nested);
                    target = nested;
                } else {
                    target = (ObjectNode) existing;
                }
            }
            target.set(parts[parts.length - 1], valueNode);
        }
        return out;
    }

    // ---------------------------------------------------------------- value → node

    @SuppressWarnings("unchecked")
    private JsonNode convertValue(Object value, Schema schema) {
        Schema effective = schema;
        if (schema.getType() == Schema.Type.UNION) {
            effective = findMatchingBranch(value, schema);
            if (effective.getType() == Schema.Type.NULL) {
                return JsonNodeFactory.instance.nullNode();
            }
        }
        if (value == null) return JsonNodeFactory.instance.nullNode();

        LogicalType logical = effective.getLogicalType();

        return switch (effective.getType()) {
            case NULL    -> JsonNodeFactory.instance.nullNode();
            case BOOLEAN -> JsonNodeFactory.instance.booleanNode((Boolean) value);
            case INT     -> convertInt((Integer) value, logical);
            case LONG    -> convertLong((Long) value, logical);
            case FLOAT   -> JsonNodeFactory.instance.numberNode((Float) value);
            case DOUBLE  -> JsonNodeFactory.instance.numberNode((Double) value);
            case STRING  -> JsonNodeFactory.instance.textNode(value.toString());
            case BYTES   -> convertBytes((ByteBuffer) value, effective, logical);
            case FIXED   -> JsonNodeFactory.instance.textNode(
                                BASE64.encodeToString(((GenericData.Fixed) value).bytes()));
            case ENUM    -> JsonNodeFactory.instance.textNode(value.toString());
            case ARRAY   -> {
                ArrayNode arr = JsonNodeFactory.instance.arrayNode();
                Schema elementSchema = effective.getElementType();
                for (Object element : (Iterable<?>) value) {
                    arr.add(convertValue(element, elementSchema));
                }
                yield arr;
            }
            case MAP     -> {
                ObjectNode obj = JsonNodeFactory.instance.objectNode();
                Schema valueSchema = effective.getValueType();
                for (Map.Entry<?, Object> entry : ((Map<?, Object>) value).entrySet()) {
                    obj.set(entry.getKey().toString(), convertValue(entry.getValue(), valueSchema));
                }
                yield obj;
            }
            case RECORD  -> recordToNode((GenericRecord) value);
            case UNION   -> convertValue(value, effective); // nested union — recurse
        };
    }

    private static JsonNode convertInt(int value, LogicalType logical) {
        if (logical instanceof LogicalTypes.Date) {
            return JsonNodeFactory.instance.textNode(LocalDate.ofEpochDay(value).toString());
        }
        if (logical instanceof LogicalTypes.TimeMillis) {
            return JsonNodeFactory.instance.textNode(
                    LocalTime.ofNanoOfDay((long) value * 1_000_000L).toString());
        }
        return JsonNodeFactory.instance.numberNode(value);
    }

    private static JsonNode convertLong(long value, LogicalType logical) {
        if (logical instanceof LogicalTypes.TimeMicros) {
            return JsonNodeFactory.instance.textNode(
                    LocalTime.ofNanoOfDay(value * 1_000L).toString());
        }
        if (logical instanceof LogicalTypes.TimestampMillis) {
            return JsonNodeFactory.instance.textNode(Instant.ofEpochMilli(value).toString());
        }
        if (logical instanceof LogicalTypes.TimestampMicros) {
            Instant inst = Instant.ofEpochSecond(
                    Math.floorDiv(value, 1_000_000L),
                    Math.floorMod(value, 1_000_000L) * 1_000L);
            return JsonNodeFactory.instance.textNode(inst.toString());
        }
        if (logical instanceof LogicalTypes.LocalTimestampMillis) {
            return JsonNodeFactory.instance.textNode(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.UTC).toString());
        }
        if (logical instanceof LogicalTypes.LocalTimestampMicros) {
            Instant inst = Instant.ofEpochSecond(
                    Math.floorDiv(value, 1_000_000L),
                    Math.floorMod(value, 1_000_000L) * 1_000L);
            return JsonNodeFactory.instance.textNode(
                    LocalDateTime.ofInstant(inst, ZoneOffset.UTC).toString());
        }
        return JsonNodeFactory.instance.numberNode(value);
    }

    private static JsonNode convertBytes(ByteBuffer buf, Schema schema, LogicalType logical) {
        if (logical instanceof LogicalTypes.Decimal dec) {
            BigDecimal bd = DECIMAL_CONVERSION.fromBytes(buf.duplicate(), schema, dec);
            return JsonNodeFactory.instance.textNode(bd.toPlainString());
        }
        byte[] bytes = new byte[buf.remaining()];
        buf.duplicate().get(bytes);
        return JsonNodeFactory.instance.textNode(BASE64.encodeToString(bytes));
    }

    private static Schema findMatchingBranch(Object value, Schema union) {
        if (value == null) {
            // Find null branch
            for (Schema branch : union.getTypes()) {
                if (branch.getType() == Schema.Type.NULL) {
                    return branch;
                }
            }
            throw new JsonConversionException("$", "Union value is null but no null branch in union " + union);
        }

        // Determine runtime type
        Class<?> clazz = value.getClass();

        for (Schema branch : union.getTypes()) {
            if (branch.getType() == Schema.Type.NULL) {
                continue; // skip null branch for non-null value
            }
            boolean matches = false;
            switch (branch.getType()) {
                case BOOLEAN -> matches = clazz == Boolean.class;
                case INT -> matches = clazz == Integer.class;
                case LONG -> matches = clazz == Long.class;
                case FLOAT -> matches = clazz == Float.class;
                case DOUBLE -> matches = clazz == Double.class;
                case STRING -> matches = clazz == String.class || clazz == Utf8.class;
                case BYTES -> matches = ByteBuffer.class.isAssignableFrom(clazz);
                case FIXED -> matches = clazz == GenericData.Fixed.class;
                case ENUM -> matches = GenericData.EnumSymbol.class.isAssignableFrom(clazz);
                case ARRAY -> matches = Iterable.class.isAssignableFrom(clazz);
                case MAP -> matches = Map.class.isAssignableFrom(clazz);
                case RECORD -> matches = GenericRecord.class.isAssignableFrom(clazz);
                case UNION -> {
                    // Nested union: recurse
                    try {
                        findMatchingBranch(value, branch);
                        matches = true;
                    } catch (JsonConversionException e) {
                        matches = false;
                    }
                }
                default -> matches = false;
            }
            if (matches) {
                return branch;
            }
        }

        throw new JsonConversionException("$", "Value of type " + clazz.getSimpleName() +
                " does not match any branch of union " + union);
    }
}

package io.github.jabhijeet.schema.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts a JSON document (supplied as a {@link JsonNode}, raw string, stream,
 * or reader) into an Avro {@link GenericRecord} that conforms to a given
 * {@link Schema}.
 *
 * <p>The converter is schema-driven: it walks the target Avro schema and pulls
 * corresponding values from the JSON tree, reporting the first incompatibility
 * with a path-qualified {@link JsonConversionException}.
 *
 * <h2>Type mapping</h2>
 * <ul>
 *   <li>Avro {@code null / boolean / int / long / float / double} â†’ JSON scalar of matching kind.
 *       Integer JSON numbers fit into {@code float/double} as well.</li>
 *   <li>Avro {@code string} â†’ JSON string. {@code uuid} logical type requires a
 *       canonical {@link UUID} string.</li>
 *   <li>Avro {@code bytes / fixed} â†’ JSON string encoded with standard Base64
 *       (no line wrapping). {@code decimal} bytes accept either a JSON number or
 *       a numeric JSON string and honor the schema's scale and precision.</li>
 *   <li>Avro {@code enum} â†’ JSON string matching one of the schema's symbols.</li>
 *   <li>Avro {@code array} â†’ JSON array; elements recurse against the element schema.</li>
 *   <li>Avro {@code map} â†’ JSON object with string keys.</li>
 *   <li>Avro {@code record} â†’ JSON object; unknown JSON fields are ignored.</li>
 *   <li>Avro {@code union} â†’ the first branch whose shape fits; {@code null}
 *       JSON values match a union containing {@code null}.</li>
 *   <li>Date / time / timestamp logical types accept either their raw numeric
 *       representation (e.g. epoch millis) or an ISO-8601 string.</li>
 * </ul>
 *
 * <h2>Missing and null fields</h2>
 * <ul>
 *   <li>A missing field uses the schema's default value if present.</li>
 *   <li>Otherwise, if the field's schema accepts {@code null}, {@code null} is used.</li>
 *   <li>Otherwise, the converter throws a {@link JsonConversionException}.</li>
 * </ul>
 *
 * <p>Instances are stateless and thread-safe once constructed.
 */
public final class JsonToAvroConverter {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();
    private static final Base64.Decoder BASE64 = Base64.getDecoder();
    private static final Base64.Decoder BASE64_URL = Base64.getUrlDecoder();
    private static final Conversions.DecimalConversion DECIMAL_CONVERSION = new Conversions.DecimalConversion();

    private final ObjectMapper mapper;

    public JsonToAvroConverter() {
        this(DEFAULT_MAPPER);
    }

    public JsonToAvroConverter(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    // ---------------------------------------------------------------- public API

    /**
     * Converts a JSON string into a {@link GenericRecord}.
     */
    public GenericRecord convert(String json, Schema schema) {
        Objects.requireNonNull(json, "json");
        return convert(parse(json), schema);
    }

    /**
     * Converts JSON read from a stream into a {@link GenericRecord}.
     * The stream is closed before returning.
     */
    public GenericRecord convert(InputStream in, Schema schema) {
        Objects.requireNonNull(in, "in");
        try (InputStream owned = in) {
            return convert(mapper.readTree(owned), schema);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JSON input", e);
        }
    }

    /**
     * Converts JSON read from a reader into a {@link GenericRecord}.
     * The reader is closed before returning.
     */
    public GenericRecord convert(Reader reader, Schema schema) {
        Objects.requireNonNull(reader, "reader");
        try (Reader owned = reader) {
            return convert(mapper.readTree(owned), schema);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JSON input", e);
        }
    }

    /**
     * Converts a parsed {@link JsonNode} into a {@link GenericRecord}.
     */
    public GenericRecord convert(JsonNode node, Schema schema) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(schema, "schema");
        if (schema.getType() != Schema.Type.RECORD) {
            throw new JsonConversionException("$",
                    "Root schema must be a RECORD but was " + schema.getType());
        }
        Object result = convertNode(node, schema, "$");
        return (GenericRecord) result;
    }

    /**
     * Converts a JSON array of records into a list of {@link GenericRecord}s.
     * The input must be a JSON array; each element is converted against the
     * supplied record schema.
     */
    public List<GenericRecord> convertAll(String json, Schema schema) {
        Objects.requireNonNull(json, "json");
        return convertAll(parse(json), schema);
    }

    /**
     * Converts a JSON array node into a list of {@link GenericRecord}s.
     * Accepts a single object as well (returns a singleton list) for convenience.
     */
    public List<GenericRecord> convertAll(JsonNode node, Schema schema) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(schema, "schema");
        if (schema.getType() != Schema.Type.RECORD) {
            throw new JsonConversionException("$",
                    "Root schema must be a RECORD but was " + schema.getType());
        }
        if (node.isObject()) {
            return Collections.singletonList(convert(node, schema));
        }
        if (!node.isArray()) {
            throw new JsonConversionException("$",
                    "Expected a JSON array or object but was " + describeNode(node));
        }
        List<GenericRecord> out = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            JsonNode element = node.get(i);
            String elementPath = "$[" + i + "]";
            if (!element.isObject()) {
                throw new JsonConversionException(elementPath,
                        "Expected an object but was " + describeNode(element));
            }
            out.add((GenericRecord) convertNode(element, schema, elementPath));
        }
        return out;
    }

    // ---------------------------------------------------------------- walker

    private Object convertNode(JsonNode node, Schema schema, String path) {
        // Handle unions up front â€” pick the branch that fits.
        if (schema.getType() == Schema.Type.UNION) {
            return convertUnion(node, schema, path);
        }

        if (node == null || node.isNull() || node.isMissingNode()) {
            if (schema.getType() == Schema.Type.NULL) {
                return null;
            }
            throw new JsonConversionException(path,
                    "Expected " + schema.getType() + " but was null");
        }

        LogicalType logical = schema.getLogicalType();

        switch (schema.getType()) {
            case NULL:
                // Already handled the null case above, so anything else is a mismatch.
                throw new JsonConversionException(path,
                        "Expected null but was " + describeNode(node));
            case BOOLEAN:
                return asBoolean(node, path);
            case INT:
                if (logical instanceof LogicalTypes.Date) {
                    return asDate(node, path);
                }
                if (logical instanceof LogicalTypes.TimeMillis) {
                    return asTimeMillis(node, path);
                }
                return asInt(node, path);
            case LONG:
                if (logical instanceof LogicalTypes.TimeMicros) {
                    return asTimeMicros(node, path);
                }
                if (logical instanceof LogicalTypes.TimestampMillis) {
                    return asTimestampMillis(node, path);
                }
                if (logical instanceof LogicalTypes.TimestampMicros) {
                    return asTimestampMicros(node, path);
                }
                if (logical instanceof LogicalTypes.LocalTimestampMillis) {
                    return asLocalTimestampMillis(node, path);
                }
                if (logical instanceof LogicalTypes.LocalTimestampMicros) {
                    return asLocalTimestampMicros(node, path);
                }
                return asLong(node, path);
            case FLOAT:
                return asFloat(node, path);
            case DOUBLE:
                return asDouble(node, path);
            case STRING:
                String s = asString(node, path);
                if (logical != null && "uuid".equals(logical.getName())) {
                    try {
                        UUID.fromString(s);
                    } catch (IllegalArgumentException e) {
                        throw new JsonConversionException(path,
                                "Invalid UUID string: '" + s + "'", e);
                    }
                }
                return s;
            case BYTES:
                if (logical instanceof LogicalTypes.Decimal dec) {
                    return asDecimalBytes(node, schema, dec, path);
                }
                return ByteBuffer.wrap(asBytes(node, path));
            case FIXED:
                return asFixed(node, schema, path);
            case ENUM:
                return asEnum(node, schema, path);
            case ARRAY:
                return asArray(node, schema, path);
            case MAP:
                return asMap(node, schema, path);
            case RECORD:
                return asRecord(node, schema, path);
            default:
                throw new JsonConversionException(path,
                        "Unsupported Avro schema type: " + schema.getType());
        }
    }

    // ---------------------------------------------------------------- unions

    private Object convertUnion(JsonNode node, Schema union, String path) {
        List<Schema> branches = union.getTypes();

        if (node == null || node.isNull() || node.isMissingNode()) {
            for (Schema branch : branches) {
                if (branch.getType() == Schema.Type.NULL) return null;
            }
            throw new JsonConversionException(path,
                    "JSON null is not permitted by union " + unionSummary(union));
        }

        // Prefer a branch that clearly matches the node shape â€” walk non-null
        // branches in schema order, trying each. Catch conversion errors so we
        // can fall back to the next branch. If all fail, report against the
        // best-matching branch to give the user a useful error.
        JsonConversionException firstFailure = null;
        for (Schema branch : branches) {
            if (branch.getType() == Schema.Type.NULL) continue;
            if (!nodeShapeMatches(node, branch)) continue;
            try {
                return convertNode(node, branch, path);
            } catch (JsonConversionException e) {
                if (firstFailure == null) firstFailure = e;
                // try next branch
            }
        }
        if (firstFailure != null) throw firstFailure;
        throw new JsonConversionException(path,
                "No union branch matches " + describeNode(node) + "; expected one of " + unionSummary(union));
    }

    private boolean nodeShapeMatches(JsonNode node, Schema branch) {
        return switch (branch.getType()) {
            case BOOLEAN -> node.isBoolean();
            case INT, LONG -> node.isIntegralNumber() || node.isTextual(); // textual allowed for logical types
            case FLOAT, DOUBLE -> node.isNumber();
            case STRING, ENUM -> node.isTextual();
            case BYTES, FIXED -> node.isTextual() || node.isNumber();
            case ARRAY -> node.isArray();
            case MAP, RECORD -> node.isObject();
            case NULL -> node.isNull();
            case UNION -> true; // nested unions are unusual; let convertNode handle it
        };
    }

    private static String unionSummary(Schema union) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < union.getTypes().size(); i++) {
            if (i > 0) sb.append(", ");
            Schema b = union.getTypes().get(i);
            sb.append(b.getType().getName());
            LogicalType lt = b.getLogicalType();
            if (lt != null) sb.append('(').append(lt.getName()).append(')');
        }
        return sb.append(']').toString();
    }

    // ---------------------------------------------------------------- scalars

    private static boolean asBoolean(JsonNode node, String path) {
        if (!node.isBoolean()) {
            throw new JsonConversionException(path,
                    "Expected boolean but was " + describeNode(node));
        }
        return node.booleanValue();
    }

    private static int asInt(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            long v = node.longValue();
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw new JsonConversionException(path,
                        "Value " + v + " does not fit into a 32-bit int");
            }
            return (int) v;
        }
        if (node.isNumber() && node.canConvertToInt()) {
            return node.intValue();
        }
        throw new JsonConversionException(path,
                "Expected int but was " + describeNode(node));
    }

    private static long asLong(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isNumber() && node.canConvertToLong()) {
            return node.longValue();
        }
        // Accept numeric strings so callers can ship 64-bit ids that exceed
        // JavaScript's safe integer range.
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.textValue().trim());
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        throw new JsonConversionException(path,
                "Expected long but was " + describeNode(node));
    }

    private static float asFloat(JsonNode node, String path) {
        if (node.isNumber()) return node.floatValue();
        throw new JsonConversionException(path,
                "Expected float but was " + describeNode(node));
    }

    private static double asDouble(JsonNode node, String path) {
        if (node.isNumber()) return node.doubleValue();
        throw new JsonConversionException(path,
                "Expected double but was " + describeNode(node));
    }

    private static String asString(JsonNode node, String path) {
        if (node.isTextual()) return node.textValue();
        // Numbers/booleans can be coerced to their lexical form â€” pragmatic and
        // matches how most consumers think about "stringy" JSON.
        if (node.isNumber() || node.isBoolean()) return node.asText();
        throw new JsonConversionException(path,
                "Expected string but was " + describeNode(node));
    }

    private static byte[] asBytes(JsonNode node, String path) {
        if (!node.isTextual()) {
            throw new JsonConversionException(path,
                    "Expected base64 string but was " + describeNode(node));
        }
        String txt = node.textValue();
        try {
            return BASE64.decode(txt);
        } catch (IllegalArgumentException first) {
            try {
                return BASE64_URL.decode(txt);
            } catch (IllegalArgumentException second) {
                throw new JsonConversionException(path,
                        "Value is not valid base64: '" + truncate(txt) + "'", first);
            }
        }
    }

    private static GenericData.Fixed asFixed(JsonNode node, Schema schema, String path) {
        byte[] raw = asBytes(node, path);
        if (raw.length != schema.getFixedSize()) {
            throw new JsonConversionException(path,
                    "Fixed type '" + schema.getFullName() + "' expects "
                            + schema.getFixedSize() + " bytes but got " + raw.length);
        }
        return new GenericData.Fixed(schema, raw);
    }

    private static GenericData.EnumSymbol asEnum(JsonNode node, Schema schema, String path) {
        String symbol = asString(node, path);
        if (!schema.hasEnumSymbol(symbol)) {
            throw new JsonConversionException(path,
                    "Enum '" + schema.getFullName() + "' has no symbol '" + symbol
                            + "'; valid symbols are " + schema.getEnumSymbols());
        }
        return new GenericData.EnumSymbol(schema, symbol);
    }

    // ---------------------------------------------------------------- decimal

    private static ByteBuffer asDecimalBytes(JsonNode node, Schema schema,
                                             LogicalTypes.Decimal dec, String path) {
        BigDecimal value;
        try {
            if (node.isNumber()) {
                value = node.decimalValue();
            } else if (node.isTextual()) {
                value = new BigDecimal(node.textValue().trim());
            } else {
                throw new JsonConversionException(path,
                        "Expected numeric or string value for decimal but was " + describeNode(node));
            }
        } catch (NumberFormatException e) {
            throw new JsonConversionException(path,
                    "Invalid decimal value: '" + node.asText() + "'", e);
        }

        BigDecimal scaled = value.setScale(dec.getScale(), java.math.RoundingMode.UNNECESSARY);
        BigInteger unscaled = scaled.unscaledValue();
        int digits = unscaled.abs().toString().length();
        if (digits > dec.getPrecision()) {
            throw new JsonConversionException(path,
                    "Decimal value " + value + " has " + digits
                            + " significant digits which exceeds schema precision " + dec.getPrecision());
        }
        return DECIMAL_CONVERSION.toBytes(scaled, schema, dec);
    }

    // ---------------------------------------------------------------- temporal

    private static int asDate(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return asInt(node, path);
        }
        String s = asString(node, path);
        try {
            long days = LocalDate.parse(s).toEpochDay();
            if (days < Integer.MIN_VALUE || days > Integer.MAX_VALUE) {
                throw new JsonConversionException(path,
                        "Date " + s + " does not fit into a 32-bit days-since-epoch");
            }
            return (int) days;
        } catch (DateTimeParseException e) {
            throw new JsonConversionException(path,
                    "Invalid date (expected ISO-8601 yyyy-MM-dd): '" + s + "'", e);
        }
    }

    private static int asTimeMillis(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return asInt(node, path);
        }
        String s = asString(node, path);
        try {
            LocalTime t = LocalTime.parse(s);
            return (int) (t.toNanoOfDay() / 1_000_000L);
        } catch (DateTimeParseException e) {
            throw new JsonConversionException(path,
                    "Invalid time-millis (expected ISO-8601 HH:mm[:ss[.SSS]]): '" + s + "'", e);
        }
    }

    private static long asTimeMicros(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return asLong(node, path);
        }
        String s = asString(node, path);
        try {
            LocalTime t = LocalTime.parse(s);
            return t.toNanoOfDay() / 1_000L;
        } catch (DateTimeParseException e) {
            throw new JsonConversionException(path,
                    "Invalid time-micros (expected ISO-8601 HH:mm[:ss[.SSSSSS]]): '" + s + "'", e);
        }
    }

    private static long asTimestampMillis(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return asLong(node, path);
        }
        return parseInstant(asString(node, path), path).toEpochMilli();
    }

    private static long asTimestampMicros(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return asLong(node, path);
        }
        Instant instant = parseInstant(asString(node, path), path);
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000L),
                instant.getNano() / 1_000L);
    }

    private static long asLocalTimestampMillis(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return asLong(node, path);
        }
        LocalDateTime ldt = parseLocalDateTime(asString(node, path), path);
        return ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static long asLocalTimestampMicros(JsonNode node, String path) {
        if (node.isIntegralNumber()) {
            return asLong(node, path);
        }
        LocalDateTime ldt = parseLocalDateTime(asString(node, path), path);
        Instant i = ldt.toInstant(ZoneOffset.UTC);
        return Math.addExact(Math.multiplyExact(i.getEpochSecond(), 1_000_000L), i.getNano() / 1_000L);
    }

    private static Instant parseInstant(String s, String path) {
        try {
            // Accept either a pure Instant (â€¦Z) or offset date-time (â€¦+HH:mm).
            try {
                return Instant.parse(s);
            } catch (DateTimeParseException ignore) {
                return OffsetDateTime.parse(s).toInstant();
            }
        } catch (DateTimeParseException e) {
            throw new JsonConversionException(path,
                    "Invalid timestamp (expected ISO-8601 with offset, e.g. 2025-01-02T03:04:05Z): '" + s + "'", e);
        }
    }

    private static LocalDateTime parseLocalDateTime(String s, String path) {
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new JsonConversionException(path,
                    "Invalid local timestamp (expected ISO-8601 local, e.g. 2025-01-02T03:04:05): '" + s + "'", e);
        }
    }

    // ---------------------------------------------------------------- containers

    private List<Object> asArray(JsonNode node, Schema schema, String path) {
        if (!node.isArray()) {
            throw new JsonConversionException(path,
                    "Expected array but was " + describeNode(node));
        }
        Schema elementSchema = schema.getElementType();
        List<Object> out = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            out.add(convertNode(node.get(i), elementSchema, path + "[" + i + "]"));
        }
        return out;
    }

    private Map<String, Object> asMap(JsonNode node, Schema schema, String path) {
        if (!node.isObject()) {
            throw new JsonConversionException(path,
                    "Expected object (for map) but was " + describeNode(node));
        }
        Schema valueSchema = schema.getValueType();
        Map<String, Object> out = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            out.put(entry.getKey(), convertNode(entry.getValue(), valueSchema,
                    path + "." + entry.getKey()));
        }
        return out;
    }

    private GenericRecord asRecord(JsonNode node, Schema schema, String path) {
        if (!node.isObject()) {
            throw new JsonConversionException(path,
                    "Expected object (for record " + schema.getFullName() + ") but was " + describeNode(node));
        }
        GenericData.Record record = new GenericData.Record(schema);
        for (Schema.Field field : schema.getFields()) {
            String fieldPath = path + "." + field.name();
            JsonNode valueNode = node.get(field.name());
            if (valueNode == null) {
                // Missing field: prefer a schema default, otherwise permit null if nullable.
                if (field.hasDefaultValue()) {
                    record.put(field.name(), defaultValue(field));
                    continue;
                }
                if (acceptsNull(field.schema())) {
                    record.put(field.name(), null);
                    continue;
                }
                throw new JsonConversionException(fieldPath,
                        "Missing required field '" + field.name() + "' (schema type " + field.schema().getType() + ")");
            }
            record.put(field.name(), convertNode(valueNode, field.schema(), fieldPath));
        }
        return record;
    }

    private Object defaultValue(Schema.Field field) {
        // GenericData.get().getDefaultValue decodes the default JSON embedded in the schema.
        return GenericData.get().getDefaultValue(field);
    }

    private static boolean acceptsNull(Schema schema) {
        if (schema.getType() == Schema.Type.NULL) return true;
        if (schema.getType() != Schema.Type.UNION) return false;
        for (Schema s : schema.getTypes()) {
            if (s.getType() == Schema.Type.NULL) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- utilities

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new JsonConversionException("$", "Input is not valid JSON", e);
        }
    }

    private static String describeNode(JsonNode node) {
        if (node == null || node.isMissingNode()) return "missing";
        if (node.isNull()) return "null";
        return node.getNodeType().name().toLowerCase() + " (" + truncate(node.toString()) + ")";
    }

    private static String truncate(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    /**
     * Returns a standalone {@link ObjectNode} builder useful in tests/examples.
     * @hidden â€” internal helper, not part of the stable API surface.
     */
    static ObjectNode newObjectNode() {
        return JsonNodeFactory.instance.objectNode();
    }

    /**
     * Convenience: convert a Java {@link String} containing UTF-8 JSON into bytes
     * for callers needing an {@link InputStream} source.
     */
    public static byte[] utf8(String json) {
        return Objects.requireNonNull(json, "json").getBytes(StandardCharsets.UTF_8);
    }
}


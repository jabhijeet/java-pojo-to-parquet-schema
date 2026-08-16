package io.github.jabhijeet.schema.json.infer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.jabhijeet.schema.FieldNamingStrategy;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;

import java.util.*;
import java.util.regex.Pattern;

public final class DefaultJsonSchemaInferrer implements JsonSchemaInferrer {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final ObjectMapper mapper;
    private final SchemaInferenceOptions options;

    public DefaultJsonSchemaInferrer() {
        this(DEFAULT_MAPPER, SchemaInferenceOptions.defaults());
    }

    public DefaultJsonSchemaInferrer(SchemaInferenceOptions options) {
        this(DEFAULT_MAPPER, options);
    }

    public DefaultJsonSchemaInferrer(ObjectMapper mapper, SchemaInferenceOptions options) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public Schema infer(String json) {
        try {
            return infer(DEFAULT_MAPPER.readTree(json), options.rootName());
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
    }

    @Override
    public Schema infer(JsonNode node) {
        return infer(node, options.rootName());
    }

    @Override
    public Schema infer(String json, String rootName) {
        try {
            return infer(DEFAULT_MAPPER.readTree(json), rootName);
        } catch (Exception e) {
            throw new SchemaInferenceException("Failed to parse JSON for schema inference", e);
        }
    }

    @Override
    public Schema infer(JsonNode node, String rootName) {
        Objects.requireNonNull(node, "node");
        if (rootName == null || rootName.isEmpty() || !FIELD_NAME_PATTERN.matcher(rootName).matches()) {
            throw new SchemaInferenceException(
                    "Invalid root name: '" + rootName + "' (must match " + FIELD_NAME_PATTERN.pattern() + ")");
        }
        Schema schema;
        if (node.isObject()) {
            schema = inferNode(node, rootName, 0);
        } else if (node.isArray()) {
            Schema element = inferArrayElement(node, 0);
            Schema arraySchema = Schema.createArray(element);
            Schema.Field field = new Schema.Field("items", arraySchema, null, null);
            Schema record = Schema.createRecord(rootName, null, null, false);
            record.setFields(List.of(field));
            schema = record;
        } else {
            Schema scalar = inferNode(node, "value", 0);
            Schema.Field field = new Schema.Field("value", scalar, null, null);
            Schema record = Schema.createRecord(rootName, null, null, false);
            record.setFields(List.of(field));
            schema = record;
        }
        if (schema.getType() != Schema.Type.RECORD) {
            throw new SchemaInferenceException(
                    "Root schema must be a RECORD but was " + schema.getType()
                            + "; wrap the JSON in an object or provide a rootName");
        }
        return schema;
    }

    private Schema inferNode(JsonNode node, String name, int depth) {
        if (depth > options.maxDepth()) {
            throw new SchemaInferenceException(
                    "Max depth " + options.maxDepth() + " exceeded at field '" + name + "'");
        }

        if (node == null || node.isNull() || node.isMissingNode()) {
            return nullableString();
        }

        if (node.isBoolean()) {
            return nullableIfNeeded(Schema.create(Schema.Type.BOOLEAN), node);
        }

        if (node.isFloatingPointNumber()) {
            return nullableIfNeeded(Schema.create(Schema.Type.DOUBLE), node);
        }

        if (node.isIntegralNumber()) {
            Schema intSchema = Schema.create(Schema.Type.LONG);
            return nullableIfNeeded(intSchema, node);
        }

        if (node.isTextual()) {
            Schema stringSchema = Schema.create(Schema.Type.STRING);
            return nullableIfNeeded(stringSchema, node);
        }

        if (node.isArray()) {
            return inferArray(node, name, depth);
        }

        if (node.isObject()) {
            return inferRecord(node, name, depth);
        }

        throw new SchemaInferenceException(
                "Unsupported JSON node type at '" + name + "': " + node.getNodeType());
    }

    private Schema inferArray(JsonNode node, String name, int depth) {
        int size = node.size();
        if (size == 0) {
            Schema element = nullableString();
            return Schema.createArray(element);
        }

        Schema element = inferArrayElement(node, depth);
        return Schema.createArray(element);
    }

    private Schema inferArrayElement(JsonNode node, int depth) {
        int sampleLimit = Math.min(options.sampleSize(), node.size());
        Set<Schema> elementSchemas = new LinkedHashSet<>();
        boolean anyNull = false;

        for (int i = 0; i < sampleLimit; i++) {
            JsonNode el = node.get(i);
            if (el == null || el.isNull() || el.isMissingNode()) {
                anyNull = true;
                continue;
            }
            elementSchemas.add(inferNode(el, "element", depth + 1));
        }

        if (elementSchemas.isEmpty()) {
            return nullableString();
        }

        if (elementSchemas.size() == 1) {
            Schema sole = elementSchemas.iterator().next();
            if (anyNull) {
                return makeNullable(sole);
            }
            return sole;
        }

        Schema merged = mergeSchemas(elementSchemas);
        if (anyNull) {
            merged = makeNullable(merged);
        }
        return merged;
    }

    private Schema inferRecord(JsonNode node, String name, int depth) {
        ObjectNode object = (ObjectNode) node;
        List<Schema.Field> fields = new ArrayList<>();
        Iterator<String> fieldNames = object.fieldNames();

        while (fieldNames.hasNext()) {
            String rawName = fieldNames.next();
            String fieldName = transformFieldName(rawName);
            JsonNode value = object.get(rawName);
            Schema fieldSchema = inferNode(value, fieldName, depth + 1);
            Object defaultVal = isNullable(fieldSchema) ? JsonProperties.NULL_VALUE : null;
            Schema.Field field = new Schema.Field(fieldName, fieldSchema, null, defaultVal);
            fields.add(field);
        }

        if (fields.isEmpty() && !options.nullableByDefault()) {
            throw new SchemaInferenceException(
                    "Empty object at '" + name + "' with nullableByDefault=false");
        }

        Schema record = Schema.createRecord(name, null, null, false);
        record.setFields(fields);
        return record;
    }

    private Schema mergeSchemas(Set<Schema> schemas) {
        if (schemas.isEmpty()) return nullableString();
        if (schemas.size() == 1) return schemas.iterator().next();

        Iterator<Schema> it = schemas.iterator();
        Schema first = it.next();

        while (it.hasNext()) {
            Schema next = it.next();
            first = mergePair(first, next);
        }

        return first;
    }

    private Schema mergePair(Schema a, Schema b) {
        if (a.equals(b)) return a;

        Schema.Type aType = a.getType();
        Schema.Type bType = b.getType();

        if (aType == Schema.Type.UNION || bType == Schema.Type.UNION) {
            Set<Schema> combined = new LinkedHashSet<>();
            collectNonNullBranches(a, combined);
            collectNonNullBranches(b, combined);
            if (combined.size() == 1) {
                return combined.iterator().next();
            }
            return Schema.createUnion(new ArrayList<>(combined));
        }

        if (aType == Schema.Type.NULL) return b;
        if (bType == Schema.Type.NULL) return a;

        if (aType == Schema.Type.STRING || bType == Schema.Type.STRING) {
            return Schema.create(Schema.Type.STRING);
        }

        if (aType == Schema.Type.BOOLEAN && bType == Schema.Type.BOOLEAN) {
            return Schema.create(Schema.Type.BOOLEAN);
        }

        if ((aType == Schema.Type.LONG || aType == Schema.Type.DOUBLE)
                && (bType == Schema.Type.LONG || bType == Schema.Type.DOUBLE)) {
            return Schema.create(Schema.Type.DOUBLE);
        }

        if (aType == Schema.Type.LONG && bType == Schema.Type.LONG) {
            return Schema.create(Schema.Type.LONG);
        }

        if ((aType == Schema.Type.LONG || aType == Schema.Type.DOUBLE)
                && (bType == Schema.Type.LONG || bType == Schema.Type.DOUBLE)) {
            return Schema.create(Schema.Type.DOUBLE);
        }

        if (aType == Schema.Type.RECORD && bType == Schema.Type.RECORD) {
            return mergeRecords(a, b);
        }

        if (aType == Schema.Type.ARRAY && bType == Schema.Type.ARRAY) {
            Schema mergedElement = mergePair(a.getElementType(), b.getElementType());
            return Schema.createArray(mergedElement);
        }

        if (aType == Schema.Type.MAP && bType == Schema.Type.MAP) {
            Schema mergedValue = mergePair(a.getValueType(), b.getValueType());
            return Schema.createMap(mergedValue);
        }

        return Schema.create(Schema.Type.STRING);
    }

    private Schema mergeRecords(Schema a, Schema b) {
        String name = a.getName();
        if (!name.equals(b.getName())) {
            name = options.rootName();
        }

        Map<String, Schema> fieldMap = new LinkedHashMap<>();
        for (Schema.Field f : a.getFields()) {
            fieldMap.put(f.name(), f.schema());
        }
        for (Schema.Field f : b.getFields()) {
            fieldMap.merge(f.name(), f.schema(), this::mergePair);
        }

        List<Schema.Field> mergedFields = new ArrayList<>();
        for (Map.Entry<String, Schema> entry : fieldMap.entrySet()) {
            Object defaultVal = isNullable(entry.getValue()) ? JsonProperties.NULL_VALUE : null;
            mergedFields.add(new Schema.Field(entry.getKey(), entry.getValue(), null, defaultVal));
        }

        Schema merged = Schema.createRecord(name, null, null, false);
        merged.setFields(mergedFields);
        return merged;
    }

    private Schema nullableIfNeeded(Schema schema, JsonNode sample) {
        if (sample.isNull() || sample.isMissingNode()) {
            return options.nullableByDefault() ? makeNullable(schema) : schema;
        }
        return schema;
    }

    private Schema nullableString() {
        Schema stringSchema = Schema.create(Schema.Type.STRING);
        return options.nullableByDefault() ? makeNullable(stringSchema) : stringSchema;
    }

    private Schema makeNullable(Schema schema) {
        if (isNullable(schema)) return schema;
        List<Schema> types = new ArrayList<>();
        types.add(Schema.create(Schema.Type.NULL));
        types.add(schema);
        return Schema.createUnion(types);
    }

    private boolean isNullable(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema branch : schema.getTypes()) {
                if (branch.getType() == Schema.Type.NULL) return true;
            }
        }
        return false;
    }

    private void collectNonNullBranches(Schema schema, Set<Schema> out) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema branch : schema.getTypes()) {
                if (branch.getType() != Schema.Type.NULL) {
                    collectNonNullBranches(branch, out);
                }
            }
        } else {
            out.add(schema);
        }
    }

    private String transformFieldName(String original) {
        FieldNamingStrategy strategy = options.fieldNamingStrategy();
        if (strategy == null || strategy == FieldNamingStrategy.AS_IS) {
            return original;
        }
        if (!FIELD_NAME_PATTERN.matcher(original).matches()) {
            return sanitizeFieldName(original);
        }

        return switch (strategy) {
            case SNAKE_CASE -> camelToSnake(original);
            case UPPER_SNAKE_CASE -> camelToSnake(original).toUpperCase();
            case LOWER_CAMEL_CASE -> toLowerCamelCase(original);
            case KEBAB_CASE -> camelToKebab(original).replace('-', '_');
            default -> original;
        };
    }

    private static String sanitizeFieldName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i == 0) {
                sb.append(Character.isJavaIdentifierStart(c) ? c : '_');
            } else {
                sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
            }
        }
        return sb.toString();
    }

    private static String camelToSnake(String input) {
        return splitCamel(input, '_');
    }

    private static String camelToKebab(String input) {
        return splitCamel(input, '-');
    }

    private static String splitCamel(String input, char separator) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder out = new StringBuilder(input.length() + 4);
        int n = input.length();
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                char prev = input.charAt(i - 1);
                boolean prevIsLowerOrDigit = Character.isLowerCase(prev) || Character.isDigit(prev);
                boolean acronymBoundary = Character.isUpperCase(prev)
                        && i + 1 < n && Character.isLowerCase(input.charAt(i + 1));
                if (prevIsLowerOrDigit || acronymBoundary) {
                    out.append(separator);
                }
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private static String toLowerCamelCase(String input) {
        if (input == null || input.isEmpty()) return input;
        if (input.indexOf('_') >= 0 || input.indexOf('-') >= 0) {
            StringBuilder out = new StringBuilder(input.length());
            boolean upperNext = false;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (c == '_' || c == '-') {
                    upperNext = out.length() > 0;
                    continue;
                }
                if (upperNext) {
                    out.append(Character.toUpperCase(c));
                    upperNext = false;
                } else {
                    out.append(Character.toLowerCase(c));
                }
            }
            return out.toString();
        }
        if (isAllUpper(input)) {
            return input.toLowerCase();
        }
        if (Character.isUpperCase(input.charAt(0))) {
            return Character.toLowerCase(input.charAt(0)) + input.substring(1);
        }
        return input;
    }

    private static boolean isAllUpper(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c) && !Character.isUpperCase(c)) return false;
        }
        return true;
    }
}

package io.github.jabhijeet.schema.avro;

import io.github.jabhijeet.schema.FieldNamingStrategy;
import io.github.jabhijeet.schema.FlattenCollisionStrategy;
import io.github.jabhijeet.schema.SchemaGenerationException;
import io.github.jabhijeet.schema.SchemaOptions;
import io.github.jabhijeet.schema.SchemaProps;
import io.github.jabhijeet.schema.TimestampPrecision;
import io.github.jabhijeet.schema.annotation.SchemaDecimal;
import io.github.jabhijeet.schema.annotation.SchemaField;
import io.github.jabhijeet.schema.annotation.SchemaIgnore;
import org.apache.avro.JsonProperties;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reflection-based builder that converts a POJO {@link Class} into an Avro {@link Schema}.
 *
 * <p>Instances are cheap but stateful during a single {@link #build(Class)} call
 * (they maintain a record cache for shared/self-referential types). Use a new
 * instance per call â€” or the facade {@code PojoSchemaGenerator}, which does this.
 */
public final class AvroSchemaBuilder {

    private static final Object NO_PROTOTYPE = new Object();

    private final SchemaOptions options;
    private final Map<Class<?>, Schema> recordCache = new HashMap<>();
    private final Map<Class<?>, Object> prototypeCache = new HashMap<>();

    public AvroSchemaBuilder(SchemaOptions options) {
        this.options = options;
    }

    public Schema build(Class<?> pojoClass) {
        if (pojoClass == null) throw new IllegalArgumentException("pojoClass must not be null");
        if (pojoClass.isPrimitive() || pojoClass.isArray() || pojoClass.isEnum()
                || pojoClass == String.class || pojoClass.getPackageName().startsWith("java.")) {
            throw new SchemaGenerationException(
                    "Top-level schema generation requires a POJO class, got: " + pojoClass.getName());
        }
        Schema nested = buildRecord(pojoClass);
        if (options.flattenNestedRecords()) {
            return flattenTopLevel(nested);
        }
        return nested;
    }

    private Schema buildRecord(Class<?> type) {
        Schema cached = recordCache.get(type);
        if (cached != null) return cached;

        String name = type.getSimpleName();
        String namespace = options.namespaceOverride() != null
                ? options.namespaceOverride()
                : type.getPackage() != null ? type.getPackage().getName() : null;

        Schema record = Schema.createRecord(name, null, namespace, false);
        recordCache.put(type, record);

        List<Schema.Field> fields = new ArrayList<>();
        for (Field f : collectFields(type)) {
            fields.add(buildField(f));
        }
        record.setFields(fields);
        return record;
    }

    private Schema.Field buildField(Field field) {
        SchemaField ann = field.getAnnotation(SchemaField.class);
        String originalName = (ann != null && !ann.name().isEmpty()) ? ann.name() : field.getName();
        String name = transformFieldName(originalName);
        String doc = (ann != null && !ann.doc().isEmpty()) ? ann.doc() : null;

        boolean optionalWrapped = field.getType() == Optional.class;
        Type typeToMap = optionalWrapped ? unwrapOptional(field.getGenericType()) : field.getGenericType();

        Schema fieldSchema = mapType(typeToMap, field);
        boolean nullable = determineNullability(field, ann, optionalWrapped);

        Object defaultValue = options.preserveDefaultValues() ? extractDefaultValue(field) : null;

        if (nullable) {
            // Avro requires a union's default to match the *first* branch. When a
            // non-null default is present we put the concrete type first; otherwise
            // [null, T] with a null default (the conventional nullable encoding).
            if (defaultValue != null) {
                fieldSchema = makeNullable(fieldSchema, /* nullFirst */ false);
                return new Schema.Field(name, fieldSchema, doc, defaultValue);
            }
            fieldSchema = makeNullable(fieldSchema, /* nullFirst */ true);
            return new Schema.Field(name, fieldSchema, doc, JsonProperties.NULL_VALUE);
        }

        return new Schema.Field(name, fieldSchema, doc, defaultValue);
    }

    private boolean determineNullability(Field field, SchemaField ann, boolean optionalWrapped) {
        if (optionalWrapped) return true;
        if (field.getType().isPrimitive()) {
            if (ann != null && ann.nullability() == SchemaField.Nullability.NULLABLE) {
                throw new SchemaGenerationException(
                        "Primitive field '" + field.getDeclaringClass().getName() + "#" + field.getName()
                                + "' cannot be NULLABLE");
            }
            return false;
        }
        if (ann != null) {
            switch (ann.nullability()) {
                case NULLABLE: return true;
                case REQUIRED: return false;
                case AUTO: /* fall through */
            }
        }
        return options.nullableByDefault();
    }

    private Schema mapType(Type type, Field context) {
        if (type instanceof Class<?> raw) {
            return mapClass(raw, context);
        }
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            Type[] args = pt.getActualTypeArguments();

            if (Collection.class.isAssignableFrom(raw)) {
                return Schema.createArray(mapType(args[0], context));
            }
            if (Map.class.isAssignableFrom(raw)) {
                Type keyType = args[0];
                if (!(keyType instanceof Class<?> keyClass) || keyClass != String.class) {
                    throw new SchemaGenerationException(
                            "Avro maps require String keys; got " + keyType.getTypeName()
                                    + " at " + fieldLabel(context));
                }
                return Schema.createMap(mapType(args[1], context));
            }
            if (raw == Optional.class) {
                return makeNullable(mapType(args[0], context));
            }
            return mapClass(raw, context);
        }
        throw new SchemaGenerationException(
                "Unsupported Java type: " + type.getTypeName() + " at " + fieldLabel(context));
    }

    private Schema mapClass(Class<?> cls, Field context) {
        if (cls == boolean.class || cls == Boolean.class) return Schema.create(Schema.Type.BOOLEAN);
        if (cls == byte.class || cls == Byte.class) return Schema.create(Schema.Type.INT);
        if (cls == short.class || cls == Short.class) return Schema.create(Schema.Type.INT);
        if (cls == int.class || cls == Integer.class) return Schema.create(Schema.Type.INT);
        if (cls == long.class || cls == Long.class) return Schema.create(Schema.Type.LONG);
        if (cls == float.class || cls == Float.class) return Schema.create(Schema.Type.FLOAT);
        if (cls == double.class || cls == Double.class) return Schema.create(Schema.Type.DOUBLE);
        if (cls == char.class || cls == Character.class) return Schema.create(Schema.Type.STRING);
        if (cls == String.class || cls == CharSequence.class) return Schema.create(Schema.Type.STRING);

        if (cls == byte[].class) return Schema.create(Schema.Type.BYTES);
        if (cls == ByteBuffer.class) return Schema.create(Schema.Type.BYTES);

        if (cls == UUID.class) return LogicalTypes.uuid().addToSchema(Schema.create(Schema.Type.STRING));
        if (cls == BigInteger.class) return Schema.create(Schema.Type.STRING);
        if (cls == BigDecimal.class) return decimalSchema(context);

        if (cls == LocalDate.class || cls == java.sql.Date.class) {
            return LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
        }
        if (cls == LocalTime.class) {
            return LogicalTypes.timeMicros().addToSchema(Schema.create(Schema.Type.LONG));
        }
        if (cls == LocalDateTime.class) {
            return LogicalTypes.localTimestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
        }
        if (cls == Instant.class || cls == Timestamp.class || cls == java.util.Date.class
                || cls == OffsetDateTime.class || cls == ZonedDateTime.class) {
            TimestampPrecision precision = options.timestampPrecision();
            switch (precision) {
                case MILLIS:
                    return LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
                case MICROS:
                    return LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));
                case NANOS:
                    // Avro doesn't have nanosecond precision, fall back to micros
                    return LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));
                default:
                    return LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
            }
        }

        if (cls.isEnum()) return enumSchema(cls);

        if (cls.isArray()) {
            return Schema.createArray(mapClass(cls.getComponentType(), context));
        }

        if (cls.getPackageName().startsWith("java.")) {
            throw new SchemaGenerationException(
                    "Unsupported standard-library type: " + cls.getName() + " at " + fieldLabel(context));
        }

        return buildRecord(cls);
    }

    private Schema decimalSchema(Field context) {
        int precision = options.defaultDecimalPrecision();
        int scale = options.defaultDecimalScale();
        SchemaDecimal ann = context != null ? context.getAnnotation(SchemaDecimal.class) : null;
        if (ann != null) {
            precision = ann.precision();
            scale = ann.scale();
        }
        if (precision <= 0 || scale < 0 || scale > precision) {
            throw new SchemaGenerationException(
                    "Invalid decimal precision/scale (" + precision + "," + scale + ") at " + fieldLabel(context));
        }
        return LogicalTypes.decimal(precision, scale).addToSchema(Schema.create(Schema.Type.BYTES));
    }

    private Schema enumSchema(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        List<String> symbols = new ArrayList<>(constants.length);
        for (Object c : constants) symbols.add(((Enum<?>) c).name());
        String namespace = options.namespaceOverride() != null
                ? options.namespaceOverride()
                : enumType.getPackage() != null ? enumType.getPackage().getName() : null;
        return Schema.createEnum(enumType.getSimpleName(), null, namespace, symbols);
    }

    private Schema flattenTopLevel(Schema nested) {
        String sep = options.flattenSeparator();
        Map<String, String> pathByName = new LinkedHashMap<>();
        Map<String, Integer> renameCounters = new HashMap<>();
        Deque<String> sourceStack = new ArrayDeque<>();
        List<Schema.Field> flatFields = flattenFields(
                nested, "", false, new HashSet<>(), pathByName, renameCounters, sourceStack, sep);

        Schema flat = Schema.createRecord(
                nested.getName(), nested.getDoc(), nested.getNamespace(), /*error*/ false);
        flat.setFields(flatFields);
        flat.addProp(SchemaProps.FLATTENED, true);
        flat.addProp(SchemaProps.FLATTEN_SEPARATOR, sep);
        return flat;
    }

    private List<Schema.Field> flattenFields(Schema record,
                                             String prefix,
                                             boolean pathNullable,
                                             Set<String> visited,
                                             Map<String, String> pathByName,
                                             Map<String, Integer> renameCounters,
                                             Deque<String> sourceStack,
                                             String separator) {
        String fullName = record.getFullName();
        if (!visited.add(fullName)) {
            throw new SchemaGenerationException(
                    "Cannot flatten cyclic record path at " + joinedSourcePath(sourceStack)
                            + "; revisits " + fullName
                            + ". Disable flattenNestedRecords or break the cycle with @SchemaIgnore.");
        }
        try {
            List<Schema.Field> out = new ArrayList<>();
            for (Schema.Field f : record.getFields()) {
                boolean fieldNullable = schemaAcceptsNull(f.schema());
                Schema core = unwrapNullableForFlatten(f.schema());
                String segment = f.name();
                String childName = prefix.isEmpty() ? segment : prefix + separator + segment;

                sourceStack.addLast(f.name());
                try {
                    if (core.getType() == Schema.Type.RECORD) {
                        out.addAll(flattenFields(core, childName,
                                pathNullable || fieldNullable,
                                visited, pathByName, renameCounters, sourceStack, separator));
                    } else {
                        String sourcePath = joinedSourcePath(sourceStack);
                        String finalName = resolveCollision(childName, sourcePath,
                                pathByName, renameCounters);
                        Schema leafSchema = core;
                        boolean effectiveNullable = pathNullable || fieldNullable;
                        if (effectiveNullable) {
                            leafSchema = makeNullable(leafSchema, /*nullFirst*/ true);
                        }
                        Object defaultVal = pathNullable ? JsonProperties.NULL_VALUE : f.defaultVal();
                        Schema.Field leafField = new Schema.Field(finalName, leafSchema, f.doc(), defaultVal);
                        leafField.addProp(SchemaProps.FLATTEN_SOURCE_PATH, sourcePath);
                        out.add(leafField);
                    }
                } finally {
                    sourceStack.removeLast();
                }
            }
            return out;
        } finally {
            visited.remove(fullName);
        }
    }

    private String resolveCollision(String proposed,
                                    String sourcePath,
                                    Map<String, String> pathByName,
                                    Map<String, Integer> renameCounters) {
        String existing = pathByName.putIfAbsent(proposed, sourcePath);
        if (existing == null) return proposed;
        if (options.flattenCollisionStrategy() == FlattenCollisionStrategy.THROW) {
            throw new SchemaGenerationException(
                    "Flatten produced duplicate field '" + proposed
                            + "' from paths '" + existing + "' and '" + sourcePath
                            + "'. Rename with @SchemaField(name=...), change flattenSeparator(...), "
                            + "or set flattenCollisionStrategy(AUTO_RENAME) to suffix duplicates.");
        }
        // AUTO_RENAME: find the next unused suffix.
        int n = renameCounters.getOrDefault(proposed, 0);
        String renamed;
        do {
            n++;
            renamed = proposed + "__" + n;
        } while (pathByName.containsKey(renamed));
        renameCounters.put(proposed, n);
        pathByName.put(renamed, sourcePath);
        return renamed;
    }

    private static boolean schemaAcceptsNull(Schema schema) {
        if (schema.getType() == Schema.Type.NULL) return true;
        if (schema.getType() != Schema.Type.UNION) return false;
        for (Schema b : schema.getTypes()) {
            if (b.getType() == Schema.Type.NULL) return true;
        }
        return false;
    }

    private static Schema unwrapNullableForFlatten(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) return schema;
        List<Schema> types = schema.getTypes();
        if (types.size() != 2) return schema;
        if (types.get(0).getType() == Schema.Type.NULL) return types.get(1);
        if (types.get(1).getType() == Schema.Type.NULL) return types.get(0);
        return schema;
    }

    private static String joinedSourcePath(Deque<String> stack) {
        if (stack.isEmpty()) return "<root>";
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = stack.iterator();
        while (it.hasNext()) {
            if (sb.length() > 0) sb.append('.');
            sb.append(it.next());
        }
        return sb.toString();
    }

    private static Schema makeNullable(Schema schema) {
        return makeNullable(schema, true);
    }

    private static Schema makeNullable(Schema schema, boolean nullFirst) {
        Schema nullSchema = Schema.create(Schema.Type.NULL);
        if (schema.getType() == Schema.Type.UNION) {
            List<Schema> existing = schema.getTypes();
            if (existing.stream().anyMatch(s -> s.getType() == Schema.Type.NULL)) {
                return schema;
            }
            List<Schema> branches = new ArrayList<>(existing.size() + 1);
            if (nullFirst) {
                branches.add(nullSchema);
                branches.addAll(existing);
            } else {
                branches.addAll(existing);
                branches.add(nullSchema);
            }
            return Schema.createUnion(branches);
        }
        return nullFirst
                ? Schema.createUnion(Arrays.asList(nullSchema, schema))
                : Schema.createUnion(Arrays.asList(schema, nullSchema));
    }

    private static Type unwrapOptional(Type genericType) {
        if (genericType instanceof ParameterizedType pt && pt.getRawType() == Optional.class) {
            return pt.getActualTypeArguments()[0];
        }
        return Object.class;
    }

    private static List<Field> collectFields(Class<?> type) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            chain.add(c);
        }
        Collections.reverse(chain);
        List<Field> all = new ArrayList<>();
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || f.isSynthetic()) continue;
                if (f.isAnnotationPresent(SchemaIgnore.class)) continue;
                all.add(f);
            }
        }
        return all;
    }

    private Object extractDefaultValue(Field field) {
        Class<?> declaringClass = field.getDeclaringClass();
        Object prototype = prototypeCache.computeIfAbsent(declaringClass,
                AvroSchemaBuilder::buildPrototype);
        if (prototype == NO_PROTOTYPE) return null;
        try {
            field.setAccessible(true);
            return convertToAvroDefault(field.get(prototype), field.getType());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object buildPrototype(Class<?> declaringClass) {
        int mods = declaringClass.getModifiers();
        if (declaringClass.isEnum()
                || declaringClass.isInterface()
                || Modifier.isAbstract(mods)
                || !Modifier.isPublic(mods)) {
            return NO_PROTOTYPE;
        }
        try {
            var ctor = declaringClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return NO_PROTOTYPE;
        }
    }

    private static Object convertToAvroDefault(Object value, Class<?> type) {
        if (value == null) return null;

        if (type == boolean.class || type == Boolean.class) return value;
        if (type == byte.class || type == Byte.class) return ((Number) value).intValue();
        if (type == short.class || type == Short.class) return ((Number) value).intValue();
        if (type == int.class || type == Integer.class) return ((Number) value).intValue();
        if (type == long.class || type == Long.class) return ((Number) value).longValue();
        if (type == float.class || type == Float.class) return ((Number) value).floatValue();
        if (type == double.class || type == Double.class) return ((Number) value).doubleValue();
        if (type == char.class || type == Character.class) return value.toString();
        if (type == String.class || type == CharSequence.class) return value.toString();

        // Other types (BigDecimal, temporal, enums, records, collections) have no
        // simple Avro default representation; let Avro use the schema default (null).
        return null;
    }

    private static String fieldLabel(Field f) {
        if (f == null) return "<root>";
        return f.getDeclaringClass().getSimpleName() + "#" + f.getName();
    }

    private String transformFieldName(String originalName) {
        FieldNamingStrategy strategy = options.fieldNamingStrategy();
        if (strategy == null || strategy == FieldNamingStrategy.AS_IS) {
            return originalName;
        }

        switch (strategy) {
            case SNAKE_CASE:
                return camelToSnake(originalName);
            case UPPER_SNAKE_CASE:
                return camelToSnake(originalName).toUpperCase();
            case LOWER_CAMEL_CASE:
                return toLowerCamelCase(originalName);
            case KEBAB_CASE:
                // Avro doesn't allow hyphens in field names, so convert to underscores
                return camelToKebab(originalName).replace('-', '_');
            default:
                return originalName;
        }
    }

    private static String camelToSnake(String input) {
        return splitCamel(input, '_');
    }

    private static String camelToKebab(String input) {
        return splitCamel(input, '-');
    }

    /**
     * Splits a camelCase / PascalCase / ACRONYM-containing identifier into
     * lowercase words joined by {@code separator}.
     *
     * <p>Word boundaries:
     * <ul>
     *   <li>between a lowercase/digit and an uppercase letter ({@code myField â†’ my_field})</li>
     *   <li>between the end of an acronym and the next word ({@code XMLParser â†’ xml_parser})</li>
     * </ul>
     */
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
        // ALL_CAPS style â†’ all_caps normalized to lower, then camelized.
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
        // Pure uppercase identifier (e.g. "EMAIL") â†’ fully lowercase.
        if (isAllUpper(input)) {
            return input.toLowerCase();
        }
        // Otherwise just lowercase the first character.
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


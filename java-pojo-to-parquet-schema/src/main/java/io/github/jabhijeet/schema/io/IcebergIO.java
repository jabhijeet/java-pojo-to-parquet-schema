package io.github.jabhijeet.schema.io;

import org.apache.avro.Conversions;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Table;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.inmemory.InMemoryCatalog;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.types.Types;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * In-memory Iceberg table helpers.
 *
 * <p>Uses Iceberg's {@code InMemoryCatalog} and {@code InMemoryFileIO} so that
 * all table metadata and data files are stored as byte arrays in the JVM heap.
 * No filesystem, no Hadoop installation, and no {@code HADOOP_HOME} environment
 * variable required.
 *
 * <p>Data is written in Parquet format via {@code iceberg-parquet} (which uses
 * {@code parquet-column} directly — not {@code parquet-hadoop}).
 */
public final class IcebergIO {

    private static final TableIdentifier TABLE_ID =
            TableIdentifier.of(org.apache.iceberg.catalog.Namespace.of("pojo"), "data");
    private static final Conversions.DecimalConversion DECIMAL_CONVERSION =
            new Conversions.DecimalConversion();

    private IcebergIO() {
        // utility
    }

    // ---------------------------------------------------------------- InMemoryTable

    /**
     * An in-memory Iceberg table backed by {@code InMemoryCatalog} and
     * {@code InMemoryFileIO}. All state lives in the JVM heap.
     *
     * <p>Not thread-safe for concurrent writes; concurrent reads are safe.
     */
    public static final class InMemoryTable {
        private final Table table;

        private InMemoryTable(Table table) {
            this.table = table;
        }

        /** Returns the Iceberg schema of this table. */
        public org.apache.iceberg.Schema schema() {
            return table.schema();
        }
    }

    // ---------------------------------------------------------------- factory

    /**
     * Creates an empty in-memory Iceberg table from an Iceberg schema.
     */
    public static InMemoryTable createTable(org.apache.iceberg.Schema icebergSchema) {
        Objects.requireNonNull(icebergSchema, "icebergSchema");
        InMemoryCatalog catalog = new InMemoryCatalog();
        catalog.initialize("mem", Map.of());
        catalog.createNamespace(TABLE_ID.namespace());
        Table table = catalog.createTable(TABLE_ID, icebergSchema, PartitionSpec.unpartitioned());
        return new InMemoryTable(table);
    }

    /**
     * Creates an empty in-memory Iceberg table derived from an Avro schema.
     */
    public static InMemoryTable createTable(Schema avroSchema) {
        Objects.requireNonNull(avroSchema, "avroSchema");
        return createTable(AvroSchemaUtil.toIceberg(avroSchema));
    }

    // ---------------------------------------------------------------- write

    /**
     * Appends Avro {@link GenericRecord}s to an in-memory Iceberg table.
     * A new Parquet data file is written for each call and registered as a new
     * table snapshot.
     */
    public static void append(InMemoryTable memTable,
                              Schema avroSchema,
                              Collection<? extends GenericRecord> avroRecords) {
        Objects.requireNonNull(memTable, "memTable");
        Objects.requireNonNull(avroSchema, "avroSchema");
        Objects.requireNonNull(avroRecords, "avroRecords");
        if (avroRecords.isEmpty()) return;

        Table table = memTable.table;
        GenericAppenderFactory factory =
                new GenericAppenderFactory(table.schema(), table.spec());
        String path = "data/" + UUID.randomUUID() + ".parquet";
        OutputFile outputFile = table.io().newOutputFile(path);

        DataFile dataFile;
        try {
            DataWriter<org.apache.iceberg.data.Record> writer = new DataWriter<>(
                    factory.newAppender(outputFile, FileFormat.PARQUET),
                    FileFormat.PARQUET,
                    outputFile.location(),
                    table.spec(),
                    null,   // partition key — null for unpartitioned
                    null);  // sort order — null for unsorted
            try (writer) {
                for (GenericRecord avroRecord : avroRecords) {
                    if (avroRecord == null) {
                        throw new IllegalArgumentException("records collection contains null");
                    }
                    writer.write(toIcebergRecord(avroRecord, table.schema()));
                }
            }
            dataFile = writer.toDataFile();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Iceberg data", e);
        }

        table.newAppend().appendFile(dataFile).commit();
    }

    // ---------------------------------------------------------------- read

    /**
     * Reads all rows from an in-memory Iceberg table as Iceberg {@link org.apache.iceberg.data.Record}s.
     */
    public static List<org.apache.iceberg.data.Record> readAll(InMemoryTable memTable) {
        Objects.requireNonNull(memTable, "memTable");
        List<org.apache.iceberg.data.Record> out = new ArrayList<>();
        try (CloseableIterable<org.apache.iceberg.data.Record> rows =
                     IcebergGenerics.read(memTable.table).build()) {
            for (org.apache.iceberg.data.Record row : rows) {
                out.add(row.copy());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Iceberg data", e);
        }
        return out;
    }

    /**
     * Reads all rows from an in-memory Iceberg table as Avro {@link GenericRecord}s.
     */
    public static List<GenericRecord> readAllAsAvro(InMemoryTable memTable) {
        Objects.requireNonNull(memTable, "memTable");
        Table table = memTable.table;
        Schema avroSchema = AvroSchemaUtil.convert(table.schema(), TABLE_ID.name());
        configureStringsAsString(avroSchema);

        List<GenericRecord> out = new ArrayList<>();
        try (CloseableIterable<org.apache.iceberg.data.Record> rows =
                     IcebergGenerics.read(table).build()) {
            for (org.apache.iceberg.data.Record row : rows) {
                out.add(toAvroRecord(row, avroSchema, table.schema().asStruct()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Iceberg data", e);
        }
        return out;
    }

    // ---------------------------------------------------------------- Avro → Iceberg conversion

    private static org.apache.iceberg.data.Record toIcebergRecord(
            GenericRecord avroRecord,
            org.apache.iceberg.Schema icebergSchema) {
        org.apache.iceberg.data.GenericRecord out =
                org.apache.iceberg.data.GenericRecord.create(icebergSchema);
        for (Types.NestedField field : icebergSchema.columns()) {
            Schema.Field avroField = avroRecord.getSchema().getField(field.name());
            if (avroField == null) {
                throw new IllegalArgumentException(
                        "Avro record is missing field '" + field.name() + "'");
            }
            out.setField(field.name(),
                    toIcebergValue(avroRecord.get(field.name()), field.type(), avroField.schema()));
        }
        return out;
    }

    private static Object toIcebergValue(Object value,
                                         org.apache.iceberg.types.Type icebergType,
                                         Schema avroSchema) {
        if (value == null) return null;
        Schema effectiveSchema = unwrapNullable(avroSchema);
        LogicalType logical = effectiveSchema.getLogicalType();

        return switch (icebergType.typeId()) {
            case BOOLEAN -> value;
            case INTEGER -> ((Number) value).intValue();
            case LONG    -> convertAvroLongToIceberg(value, logical);
            case FLOAT   -> ((Number) value).floatValue();
            case DOUBLE  -> ((Number) value).doubleValue();
            case DATE    -> ((Number) value).intValue();
            case TIME    -> convertAvroTimeToIceberg(value, logical);
            case TIMESTAMP -> convertAvroTimestampToIceberg(value, logical);
            case STRING  -> value.toString();
            case UUID    -> value instanceof UUID u ? u : UUID.fromString(value.toString());
            case FIXED, BINARY -> asByteBuffer(value);
            case DECIMAL -> convertAvroDecimalToIceberg(value, effectiveSchema, logical);
            case STRUCT  -> {
                GenericRecord nested = (GenericRecord) value;
                Types.StructType struct = icebergType.asStructType();
                org.apache.iceberg.data.GenericRecord record =
                        org.apache.iceberg.data.GenericRecord.create(struct);
                for (Types.NestedField f : struct.fields()) {
                    Schema.Field af = nested.getSchema().getField(f.name());
                    if (af == null) throw new IllegalArgumentException(
                            "Avro record missing nested field '" + f.name() + "'");
                    record.setField(f.name(),
                            toIcebergValue(nested.get(f.name()), f.type(), af.schema()));
                }
                yield record;
            }
            case LIST -> {
                Schema elementSchema = effectiveSchema.getElementType();
                List<Object> list = new ArrayList<>();
                for (Object element : (Iterable<?>) value) {
                    list.add(toIcebergValue(element, icebergType.asListType().elementType(),
                            elementSchema));
                }
                yield list;
            }
            case MAP -> {
                Schema valueSchema = effectiveSchema.getValueType();
                Map<Object, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    map.put(entry.getKey().toString(),
                            toIcebergValue(entry.getValue(),
                                    icebergType.asMapType().valueType(), valueSchema));
                }
                yield map;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported Iceberg type: " + icebergType);
        };
    }

    // ---------------------------------------------------------------- Iceberg → Avro conversion

    private static GenericRecord toAvroRecord(org.apache.iceberg.data.Record record,
                                               Schema avroSchema,
                                               Types.StructType struct) {
        GenericData.Record out = new GenericData.Record(avroSchema);
        for (Schema.Field field : avroSchema.getFields()) {
            Types.NestedField iceField = struct.field(field.name());
            if (iceField == null) throw new IllegalArgumentException(
                    "Iceberg record missing field '" + field.name() + "'");
            out.put(field.name(),
                    toAvroValue(record.getField(field.name()), field.schema(), iceField.type()));
        }
        return out;
    }

    private static Object toAvroValue(Object value,
                                      Schema avroSchema,
                                      org.apache.iceberg.types.Type icebergType) {
        if (value == null) return null;
        Schema effectiveSchema = unwrapNullable(avroSchema);
        LogicalType logical = effectiveSchema.getLogicalType();

        return switch (effectiveSchema.getType()) {
            case NULL    -> null;
            case BOOLEAN -> value;
            case INT     -> convertIcebergIntToAvro(value, logical);
            case LONG    -> convertIcebergLongToAvro(value, logical);
            case FLOAT   -> ((Number) value).floatValue();
            case DOUBLE  -> ((Number) value).doubleValue();
            case STRING  -> value.toString();
            case BYTES   -> convertIcebergBytesToAvro(value, effectiveSchema, logical);
            case FIXED   -> new GenericData.Fixed(effectiveSchema, toByteArray(value));
            case ENUM    -> new GenericData.EnumSymbol(effectiveSchema, value.toString());
            case ARRAY   -> {
                Schema elementSchema = effectiveSchema.getElementType();
                List<Object> list = new ArrayList<>();
                for (Object element : (Iterable<?>) value) {
                    list.add(toAvroValue(element, elementSchema,
                            icebergType.asListType().elementType()));
                }
                yield list;
            }
            case MAP -> {
                Schema valueSchema = effectiveSchema.getValueType();
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    map.put(entry.getKey().toString(),
                            toAvroValue(entry.getValue(), valueSchema,
                                    icebergType.asMapType().valueType()));
                }
                yield map;
            }
            case RECORD -> toAvroRecord((org.apache.iceberg.data.Record) value,
                    effectiveSchema, icebergType.asStructType());
            case UNION  -> toAvroValue(value, unwrapNullable(effectiveSchema), icebergType);
        };
    }

    // ---------------------------------------------------------------- temporal helpers

    private static Object convertAvroLongToIceberg(Object value, LogicalType logical) {
        long v = ((Number) value).longValue();
        if (logical instanceof LogicalTypes.TimestampMillis
                || logical instanceof LogicalTypes.LocalTimestampMillis) {
            return Math.multiplyExact(v, 1_000L);
        }
        return v;
    }

    private static Object convertAvroTimeToIceberg(Object value, LogicalType logical) {
        if (logical instanceof LogicalTypes.TimeMillis) {
            return Math.multiplyExact(((Number) value).longValue(), 1_000L);
        }
        return ((Number) value).longValue(); // micros — pass through
    }

    private static Object convertAvroTimestampToIceberg(Object value, LogicalType logical) {
        long v = ((Number) value).longValue();
        if (logical instanceof LogicalTypes.TimestampMillis
                || logical instanceof LogicalTypes.LocalTimestampMillis) {
            return Math.multiplyExact(v, 1_000L);
        }
        return v; // already micros
    }

    private static BigDecimal convertAvroDecimalToIceberg(Object value, Schema schema,
                                                           LogicalType logical) {
        if (!(logical instanceof LogicalTypes.Decimal decimal)) {
            throw new IllegalArgumentException("Expected decimal logical type");
        }
        if (value instanceof BigDecimal bd) return bd;
        return DECIMAL_CONVERSION.fromBytes(asByteBuffer(value), schema, decimal);
    }

    private static Object convertIcebergIntToAvro(Object value, LogicalType logical) {
        if (logical instanceof LogicalTypes.Date) return ((Number) value).intValue();
        if (logical instanceof LogicalTypes.TimeMillis) {
            return Math.toIntExact(divideExact(((Number) value).longValue(), 1_000L, "time-millis"));
        }
        return ((Number) value).intValue();
    }

    private static Object convertIcebergLongToAvro(Object value, LogicalType logical) {
        long v = ((Number) value).longValue();
        if (logical instanceof LogicalTypes.TimeMicros) return v;
        if (logical instanceof LogicalTypes.TimestampMicros
                || logical instanceof LogicalTypes.LocalTimestampMicros) return v;
        if (logical instanceof LogicalTypes.TimestampMillis
                || logical instanceof LogicalTypes.LocalTimestampMillis) {
            return divideExact(v, 1_000L, logical.getName());
        }
        return v;
    }

    private static Object convertIcebergBytesToAvro(Object value, Schema schema,
                                                     LogicalType logical) {
        if (logical instanceof LogicalTypes.Decimal decimal) {
            return DECIMAL_CONVERSION.toBytes((BigDecimal) value, schema, decimal);
        }
        return asByteBuffer(value);
    }

    // ---------------------------------------------------------------- utilities

    private static long divideExact(long value, long divisor, String name) {
        if (value % divisor != 0) throw new IllegalArgumentException(
                "Cannot losslessly convert Iceberg " + name + " value " + value
                        + " to Avro precision");
        return value / divisor;
    }

    private static ByteBuffer asByteBuffer(Object value) {
        if (value instanceof ByteBuffer buf) return buf.duplicate();
        if (value instanceof byte[] bytes) return ByteBuffer.wrap(bytes);
        if (value instanceof GenericData.Fixed fixed) return ByteBuffer.wrap(fixed.bytes());
        throw new IllegalArgumentException(
                "Expected bytes but got " + value.getClass().getName());
    }

    private static byte[] toByteArray(Object value) {
        ByteBuffer buf = asByteBuffer(value);
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }

    private static Schema unwrapNullable(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) return schema;
        for (Schema branch : schema.getTypes()) {
            if (branch.getType() != Schema.Type.NULL) return branch;
        }
        return schema;
    }

    private static void configureStringsAsString(Schema schema) {
        configureStringsAsString(schema, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void configureStringsAsString(Schema schema, Set<Schema> visited) {
        if (!visited.add(schema)) return;
        switch (schema.getType()) {
            case STRING, MAP -> {
                GenericData.setStringType(schema, GenericData.StringType.String);
                if (schema.getType() == Schema.Type.MAP) {
                    configureStringsAsString(schema.getValueType(), visited);
                }
            }
            case RECORD -> schema.getFields().forEach(f ->
                    configureStringsAsString(f.schema(), visited));
            case UNION  -> schema.getTypes().forEach(b ->
                    configureStringsAsString(b, visited));
            case ARRAY  -> configureStringsAsString(schema.getElementType(), visited);
            default     -> { /* nothing */ }
        }
    }
}

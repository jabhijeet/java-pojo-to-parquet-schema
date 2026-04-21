package org.ajtech.schema;

import org.ajtech.schema.fixtures.Address;
import org.ajtech.schema.fixtures.AllPrimitivesPojo;
import org.ajtech.schema.fixtures.ArrayTypesPojo;
import org.ajtech.schema.fixtures.NestedCollectionsPojo;
import org.ajtech.schema.io.AvroIO;
import org.ajtech.schema.io.InMemoryInputFile;
import org.ajtech.schema.io.InMemoryOutputFile;
import org.ajtech.schema.io.ParquetIO;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify generated Avro and Parquet schemas can be used
 * to write and read real data for various POJO structures.
 *
 * <p>All I/O is fully in-memory — no filesystem, no {@code HADOOP_HOME} required.
 * Avro reads use {@link GenericDatumReader} (not reflect) so field access returns a
 * {@link GenericRecord}. Strings are configured to deserialize as {@link String}
 * rather than {@link org.apache.avro.util.Utf8}. Parquet I/O goes through
 * {@link InMemoryOutputFile}/{@link InMemoryInputFile}.
 */
class JsonToAvroParquetIntegrationTest {

    @Test
    void normalJson_simplePrimitives() throws IOException {
        Schema avroSchema = PojoSchemaGenerator.toAvro(AllPrimitivesPojo.class);
        configureStringsAsString(avroSchema);

        // Build GenericRecord explicitly: Avro Reflect cannot fit a boxed Character
        // into a ["null","string"] union, so we represent char fields as strings.
        GenericRecord record = new GenericData.Record(avroSchema);
        record.put("pBool", true);
        record.put("pByte", 42);
        record.put("pShort", 1000);
        record.put("pInt", 123456);
        record.put("pLong", 999999999L);
        record.put("pFloat", 3.14f);
        record.put("pDouble", 2.71828);
        record.put("pChar", "A");
        record.put("bBool", Boolean.TRUE);
        record.put("bByte", 127);
        record.put("bShort", 2000);
        record.put("bInt", 654321);
        record.put("bLong", 888888888L);
        record.put("bFloat", 1.414f);
        record.put("bDouble", 1.618);
        record.put("bChar", "Z");

        // Avro round-trip via AvroIO
        byte[] avroBytes = AvroIO.toBytes(avroSchema, record);
        GenericRecord readRecord = AvroIO.fromBytes(avroBytes);

        assertThat(readRecord.get("pBool")).isEqualTo(true);
        assertThat(readRecord.get("pByte")).isEqualTo(42);
        assertThat(readRecord.get("pShort")).isEqualTo(1000);
        assertThat(readRecord.get("pInt")).isEqualTo(123456);
        assertThat(readRecord.get("pLong")).isEqualTo(999999999L);
        assertThat(readRecord.get("pFloat")).isEqualTo(3.14f);
        assertThat(readRecord.get("pDouble")).isEqualTo(2.71828);
        assertThat(readRecord.get("pChar")).isEqualTo("A");
        assertThat(readRecord.get("bBool")).isEqualTo(Boolean.TRUE);
        assertThat(readRecord.get("bByte")).isEqualTo(127);
        assertThat(readRecord.get("bShort")).isEqualTo(2000);
        assertThat(readRecord.get("bInt")).isEqualTo(654321);
        assertThat(readRecord.get("bLong")).isEqualTo(888888888L);
        assertThat(readRecord.get("bFloat")).isEqualTo(1.414f);
        assertThat(readRecord.get("bDouble")).isEqualTo(1.618);
        assertThat(readRecord.get("bChar")).isEqualTo("Z");

        // Parquet round-trip via ParquetIO
        byte[] parquetBytes = ParquetIO.toBytes(avroSchema, record);
        GenericRecord parquetRecord = ParquetIO.fromBytes(parquetBytes);

        assertThat(parquetRecord).isNotNull();
        assertThat(parquetRecord.get("pInt")).isEqualTo(123456);
        assertThat(parquetRecord.get("pDouble")).isEqualTo(2.71828);
        assertThat(parquetRecord.get("pChar")).isEqualTo("A");
    }

    @Test
    void arrayObjects() throws IOException {
        Schema avroSchema = PojoSchemaGenerator.toAvro(ArrayTypesPojo.class);
        configureStringsAsString(avroSchema);

        ArrayTypesPojo pojo = new ArrayTypesPojo();
        pojo.ints = new int[]{1, 2, 3};
        pojo.longs = new long[]{100L, 200L};
        pojo.bools = new boolean[]{true, false};
        pojo.doubles = new double[]{1.1, 2.2};
        pojo.strings = new String[]{"hello", "world"};
        pojo.addresses = new Address[]{new Address(), new Address()};
        pojo.addresses[0].city = "New York";
        pojo.addresses[0].street = "5th Ave";
        pojo.addresses[1].city = "London";
        pojo.addresses[1].street = "Baker St";
        pojo.bytes = new byte[]{0x01, 0x02, 0x03};

        // Write via ReflectDatumWriter into a byte array, read back generically.
        byte[] avroBytes = reflectToBytes(avroSchema, pojo);
        GenericRecord readRecord = readAvroGeneric(avroSchema, avroBytes);

        @SuppressWarnings("unchecked")
        List<Object> ints = (List<Object>) readRecord.get("ints");
        @SuppressWarnings("unchecked")
        List<Object> longs = (List<Object>) readRecord.get("longs");
        @SuppressWarnings("unchecked")
        List<Object> bools = (List<Object>) readRecord.get("bools");
        @SuppressWarnings("unchecked")
        List<Object> doubles = (List<Object>) readRecord.get("doubles");
        @SuppressWarnings("unchecked")
        List<Object> strings = (List<Object>) readRecord.get("strings");
        assertThat(ints).containsExactly(1, 2, 3);
        assertThat(longs).containsExactly(100L, 200L);
        assertThat(bools).containsExactly(true, false);
        assertThat(doubles).containsExactly(1.1, 2.2);
        assertThat(strings).containsExactly("hello", "world");

        List<?> addressList = (List<?>) readRecord.get("addresses");
        assertThat(addressList).hasSize(2);
        GenericRecord addr0 = (GenericRecord) addressList.get(0);
        assertThat(addr0.get("city")).isEqualTo("New York");
        assertThat(addr0.get("street")).isEqualTo("5th Ave");

        Object bytesVal = readRecord.get("bytes");
        assertThat(bytesVal).isInstanceOf(ByteBuffer.class);
        ByteBuffer bb = (ByteBuffer) bytesVal;
        byte[] byteArray = new byte[bb.remaining()];
        bb.get(byteArray);
        assertThat(byteArray).containsExactly((byte) 0x01, (byte) 0x02, (byte) 0x03);

        // Parquet round-trip — ReflectData model for writing, GenericData for reading
        InMemoryOutputFile outFile = new InMemoryOutputFile();
        try (ParquetWriter<ArrayTypesPojo> writer = AvroParquetWriter.<ArrayTypesPojo>builder(outFile)
                .withSchema(avroSchema)
                .withDataModel(org.apache.avro.reflect.ReflectData.get())
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build()) {
            writer.write(pojo);
        }

        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(
                        new InMemoryInputFile(outFile.toByteArray()))
                .withDataModel(GenericData.get())
                .build()) {
            GenericRecord parquetRecord = reader.read();
            assertThat(parquetRecord).isNotNull();
            @SuppressWarnings("unchecked")
            List<Object> parquetInts = (List<Object>) parquetRecord.get("ints");
            assertThat(parquetInts).containsExactly(1, 2, 3);
        }
    }

    @Test
    void complexNestedStructure() throws IOException {
        Schema avroSchema = PojoSchemaGenerator.toAvro(NestedCollectionsPojo.class);
        configureStringsAsString(avroSchema);

        NestedCollectionsPojo pojo = new NestedCollectionsPojo();
        pojo.matrix = Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList(4, 5, 6)
        );
        Map<String, List<String>> index = new HashMap<>();
        index.put("key1", Arrays.asList("a", "b"));
        index.put("key2", Collections.singletonList("c"));
        pojo.indexByKey = index;

        List<Map<String, Integer>> histograms = new ArrayList<>();
        Map<String, Integer> hist1 = new HashMap<>();
        hist1.put("x", 10);
        hist1.put("y", 20);
        histograms.add(hist1);
        pojo.histograms = histograms;

        Map<String, Map<String, Double>> nestedMap = new HashMap<>();
        Map<String, Double> inner = new HashMap<>();
        inner.put("temperature", 23.5);
        nestedMap.put("sensor", inner);
        pojo.nestedMap = nestedMap;

        Address addr = new Address();
        addr.city = "Paris";
        addr.street = "Champs-Elysees";
        pojo.addresses = new ArrayList<>(Collections.singletonList(addr));

        byte[] avroBytes = reflectToBytes(avroSchema, pojo);
        GenericRecord readRecord = readAvroGeneric(avroSchema, avroBytes);

        assertThat((List<?>) readRecord.get("matrix")).hasSize(2);
        @SuppressWarnings("unchecked")
        List<Object> matrixRow0 = (List<Object>) ((List<?>) readRecord.get("matrix")).get(0);
        assertThat(matrixRow0).containsExactly(1, 2, 3);

        Map<?, ?> indexMap = (Map<?, ?>) readRecord.get("indexByKey");
        assertThat(indexMap).hasSize(2);
        @SuppressWarnings("unchecked")
        List<Object> indexKey1 = (List<Object>) indexMap.get("key1");
        assertThat(indexKey1).containsExactly("a", "b");

        List<?> histList = (List<?>) readRecord.get("histograms");
        assertThat(histList).hasSize(1);
        Map<?, ?> histMap = (Map<?, ?>) histList.get(0);
        assertThat(histMap.get("x")).isEqualTo(10);

        Map<?, ?> nestedMapRead = (Map<?, ?>) readRecord.get("nestedMap");
        Map<?, ?> innerRead = (Map<?, ?>) nestedMapRead.get("sensor");
        assertThat(innerRead.get("temperature")).isEqualTo(23.5);

        // Parquet round-trip
        InMemoryOutputFile outFile = new InMemoryOutputFile();
        try (ParquetWriter<NestedCollectionsPojo> writer = AvroParquetWriter.<NestedCollectionsPojo>builder(outFile)
                .withSchema(avroSchema)
                .withDataModel(org.apache.avro.reflect.ReflectData.get())
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build()) {
            writer.write(pojo);
        }

        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(
                        new InMemoryInputFile(outFile.toByteArray()))
                .withDataModel(GenericData.get())
                .build()) {
            GenericRecord parquetRecord = reader.read();
            assertThat(parquetRecord).isNotNull();
            Map<?, ?> parquetIndex = (Map<?, ?>) parquetRecord.get("indexByKey");
            assertThat(parquetIndex).hasSize(2);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static <T> byte[] reflectToBytes(Schema schema, T pojo) throws IOException {
        DatumWriter<T> writer = new ReflectDatumWriter<>(schema);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataFileWriter<T> dfw = new DataFileWriter<>(writer)) {
            dfw.create(schema, out);
            dfw.append(pojo);
        }
        return out.toByteArray();
    }

    private static GenericRecord readAvroGeneric(Schema schema, byte[] bytes) throws IOException {
        try (org.apache.avro.file.DataFileReader<GenericRecord> reader =
                     new org.apache.avro.file.DataFileReader<>(
                             new SeekableByteArrayInput(bytes),
                             new GenericDatumReader<>(schema))) {
            return reader.next();
        }
    }

    /**
     * Recursively tags every STRING and MAP schema with {@code avro.java.string=String}
     * so generic reads return real {@link String}s instead of {@link org.apache.avro.util.Utf8}.
     */
    private static void configureStringsAsString(Schema schema) {
        configureStringsAsString(schema, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void configureStringsAsString(Schema schema, Set<Schema> visited) {
        if (!visited.add(schema)) return;
        switch (schema.getType()) {
            case STRING:
            case MAP:
                GenericData.setStringType(schema, GenericData.StringType.String);
                if (schema.getType() == Schema.Type.MAP) {
                    configureStringsAsString(schema.getValueType(), visited);
                }
                break;
            case RECORD:
                for (Schema.Field f : schema.getFields()) {
                    configureStringsAsString(f.schema(), visited);
                }
                break;
            case UNION:
                for (Schema s : schema.getTypes()) {
                    configureStringsAsString(s, visited);
                }
                break;
            case ARRAY:
                configureStringsAsString(schema.getElementType(), visited);
                break;
            default:
                break;
        }
    }
}

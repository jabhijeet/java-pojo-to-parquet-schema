package io.github.jabhijeet.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jabhijeet.schema.fixtures.CustomerOrderDemo;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import io.github.jabhijeet.schema.json.JsonIO;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.schema.MessageType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Visual + golden-fixture test: complex nested JSON in &rarr; Avro + Parquet out.
 *
 * <p>Fixtures live in {@code src/test/resources/demo-fixtures/}:
 * <ul>
 *   <li>{@code customer-order.input.json} &mdash; the nested input document (source of truth)</li>
 *   <li>{@code customer-order.avsc}        &mdash; expected generated Avro schema</li>
 *   <li>{@code customer-order.avro}        &mdash; expected Avro OCF payload</li>
 *   <li>{@code customer-order.parquet}     &mdash; expected Parquet payload</li>
 * </ul>
 *
 * <p>Binary {@code .avro} and {@code .parquet} files are <em>not</em> byte-deterministic
 * (Avro embeds a random 16-byte sync marker; Parquet writes a {@code created_by}
 * footer), so comparisons are done on decoded content: {@link Schema#equals} for the
 * Avro schema, {@link GenericRecord#equals} for records read back from the stored
 * binaries.
 *
 * <p>The test also re-writes the freshly-produced artifacts to
 * {@code target/demo-output/} so they can be inspected in external viewers and
 * promoted back into {@code src/test/resources/demo-fixtures/} when the schema
 * mapping changes intentionally.
 */
class ComplexJsonVisualDemoTest {

    private static final String FIXTURE_DIR = "demo-fixtures/";
    private static final String INPUT_JSON_RESOURCE = FIXTURE_DIR + "customer-order.input.json";
    private static final String AVSC_RESOURCE       = FIXTURE_DIR + "customer-order.avsc";
    private static final String AVRO_RESOURCE       = FIXTURE_DIR + "customer-order.avro";
    private static final String PARQUET_RESOURCE    = FIXTURE_DIR + "customer-order.parquet";

    @Test
    void demo_complex_nested_json_to_avro_and_parquet() throws IOException {
        String inputJson = readResourceAsString(INPUT_JSON_RESOURCE);

        Schema avroSchema = PojoSchemaGenerator.toAvro(CustomerOrderDemo.class);
        MessageType parquetSchema = PojoSchemaGenerator.toParquet(CustomerOrderDemo.class);

        // Strings as java.lang.String makes the generic round-trip output readable
        // (otherwise toString() is littered with Utf8 wrappers).
        configureStringsAsString(avroSchema);

        printBanner("1. INPUT JSON (complex nested document, from test resources)");
        System.out.println(prettyJson(inputJson));

        printBanner("2. GENERATED AVRO SCHEMA (from POJO via reflection)");
        System.out.println(avroSchema.toString(true));

        printBanner("3. GENERATED PARQUET SCHEMA (derived from Avro)");
        System.out.println(parquetSchema.toString());

        GenericRecord record = JsonIO.toRecord(inputJson, avroSchema);
        printBanner("4. AVRO GenericRecord (JSON parsed against the schema)");
        System.out.println(record);

        byte[] avroBytes = AvroIO.toBytes(avroSchema, record);
        printBanner("5. AVRO binary payload (Object Container File)");
        System.out.println("size: " + avroBytes.length + " bytes");
        System.out.println("hex : " + hexPreview(avroBytes, 128));

        byte[] parquetBytes = ParquetIO.toBytes(avroSchema, record);
        printBanner("6. PARQUET binary payload (Snappy-compressed)");
        System.out.println("size: " + parquetBytes.length + " bytes");
        System.out.println("hex : " + hexPreview(parquetBytes, 128));

        GenericRecord roundTripped = ParquetIO.fromBytes(parquetBytes);
        printBanner("7. ROUND-TRIP: record read back from Parquet bytes");
        System.out.println(roundTripped);

        // Re-emit to target/ for inspection and fixture-promotion workflow.
        Path outDir = Path.of("target", "demo-output");
        Files.createDirectories(outDir);
        Files.write(outDir.resolve("customer-order.avro"), avroBytes);
        Files.write(outDir.resolve("customer-order.parquet"), parquetBytes);
        Files.writeString(outDir.resolve("customer-order.avsc"), avroSchema.toString(true));
        Files.writeString(outDir.resolve("customer-order.input.json"), prettyJson(inputJson));

        printBanner("8. FILES WRITTEN (open these in external viewers)");
        System.out.println(outDir.resolve("customer-order.avro").toAbsolutePath());
        System.out.println(outDir.resolve("customer-order.parquet").toAbsolutePath());
        System.out.println(outDir.resolve("customer-order.avsc").toAbsolutePath());
        System.out.println(outDir.resolve("customer-order.input.json").toAbsolutePath());

        // ------------------------------------------------------------ golden-fixture comparisons

        // (a) Avro schema matches the stored .avsc structurally.
        Schema expectedSchema = new Schema.Parser().parse(readResourceAsString(AVSC_RESOURCE));
        assertThat(avroSchema)
                .as("generated Avro schema must match src/test/resources/%s", AVSC_RESOURCE)
                .isEqualTo(expectedSchema);

        // (b) Records decoded from the stored .avro match the freshly produced record.
        GenericRecord expectedFromAvro = AvroIO.fromBytes(readResourceAsBytes(AVRO_RESOURCE));
        assertThat(expectedFromAvro)
                .as("record decoded from %s must match freshly generated record", AVRO_RESOURCE)
                .isEqualTo(record);

        // (c) Records decoded from the stored .parquet match the freshly produced record.
        GenericRecord expectedFromParquet = ParquetIO.fromBytes(readResourceAsBytes(PARQUET_RESOURCE));
        assertThat(expectedFromParquet)
                .as("record decoded from %s must match freshly generated record", PARQUET_RESOURCE)
                .isEqualTo(record);

        // Final sanity check on the round-tripped record.
        assertThat(roundTripped.get("customerName").toString()).isEqualTo("Ada Lovelace");
        @SuppressWarnings("unchecked")
        List<GenericRecord> items = (List<GenericRecord>) roundTripped.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("sku").toString()).isEqualTo("BOOK-001");
    }

    // ---------------------------------------------------------------- resource loading

    private static String readResourceAsString(String resource) throws IOException {
        return new String(readResourceAsBytes(resource), StandardCharsets.UTF_8);
    }

    private static byte[] readResourceAsBytes(String resource) throws IOException {
        ClassLoader cl = ComplexJsonVisualDemoTest.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + resource);
            }
            return in.readAllBytes();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void printBanner(String title) {
        String bar = "=".repeat(Math.max(60, title.length() + 4));
        System.out.println();
        System.out.println(bar);
        System.out.println("== " + title);
        System.out.println(bar);
    }

    private static String prettyJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object tree = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        } catch (IOException e) {
            return json;
        }
    }

    private static String hexPreview(byte[] bytes, int maxBytes) {
        int limit = Math.min(bytes.length, maxBytes);
        StringBuilder sb = new StringBuilder(limit * 3);
        for (int i = 0; i < limit; i++) {
            if (i > 0 && i % 32 == 0) sb.append('\n').append("      ");
            sb.append(String.format("%02x ", bytes[i]));
        }
        if (bytes.length > limit) sb.append("... (+").append(bytes.length - limit).append(" bytes)");
        return sb.toString();
    }

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

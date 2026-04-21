package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.fixtures.CustomerOrderDemo;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import io.github.jabhijeet.schema.json.JsonIO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.schema.MessageType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Visual, end-to-end demo: complex nested JSON in â†’ Avro + Parquet out.
 *
 * <p>Prints each stage of the pipeline to stdout so the conversion is legible
 * when the test runs, and writes {@code customer-order.avro} and
 * {@code customer-order.parquet} to {@code target/demo-output/} so the binary
 * artifacts can be opened in external tools (e.g. {@code parquet-tools cat},
 * {@code avro-tools tojson}, or a Parquet viewer).
 */
class ComplexJsonVisualDemoTest {

    private static final String INPUT_JSON = """
            {
              "orderId":          "550e8400-e29b-41d4-a716-446655440000",
              "customerName":     "Ada Lovelace",
              "customerEmail":    "ada@example.com",
              "totalAmount":      "1249.97",
              "placedAt":         "2026-04-21T14:30:00Z",
              "expectedDelivery": "2026-04-28",
              "shippingAddress": {
                "street":     "10 Downing St",
                "city":       "London",
                "postalCode": 10001
              },
              "billingAddress": {
                "street":     "221B Baker St",
                "city":       "London",
                "postalCode": 22221
              },
              "items": [
                {"sku": "BOOK-001", "productName": "The Analytical Engine", "quantity": 2, "unitPrice": "399.99"},
                {"sku": "PEN-042",  "productName": "Fountain Pen",          "quantity": 1, "unitPrice": "49.99"},
                {"sku": "NOTE-007", "productName": "Leather Journal",       "quantity": 4, "unitPrice": "100.00"}
              ],
              "tags": {
                "channel": "web",
                "region":  "eu-west-1",
                "tier":    "gold"
              },
              "metadata": {
                "promotions":   ["SPRING25", "LOYALTY"],
                "giftMessages": ["Happy birthday!"],
                "flags":        ["priority", "signature-required"]
              }
            }
            """;

    @Test
    void demo_complex_nested_json_to_avro_and_parquet() throws IOException {
        Schema avroSchema = PojoSchemaGenerator.toAvro(CustomerOrderDemo.class);
        MessageType parquetSchema = PojoSchemaGenerator.toParquet(CustomerOrderDemo.class);

        // Strings as java.lang.String makes the generic round-trip output readable
        // (otherwise toString() is littered with Utf8 wrappers).
        configureStringsAsString(avroSchema);

        printBanner("1. INPUT JSON (complex nested document)");
        System.out.println(prettyJson(INPUT_JSON));

        printBanner("2. GENERATED AVRO SCHEMA (from POJO via reflection)");
        System.out.println(avroSchema.toString(true));

        printBanner("3. GENERATED PARQUET SCHEMA (derived from Avro)");
        System.out.println(parquetSchema.toString());

        GenericRecord record = JsonIO.toRecord(INPUT_JSON, avroSchema);
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

        Path outDir = Path.of("target", "demo-output");
        Files.createDirectories(outDir);
        Path avroOut = outDir.resolve("customer-order.avro");
        Path parquetOut = outDir.resolve("customer-order.parquet");
        Path schemaOut = outDir.resolve("customer-order.avsc");
        Path jsonOut = outDir.resolve("customer-order.input.json");
        Files.write(avroOut, avroBytes);
        Files.write(parquetOut, parquetBytes);
        Files.writeString(schemaOut, avroSchema.toString(true));
        Files.writeString(jsonOut, prettyJson(INPUT_JSON));

        printBanner("8. FILES WRITTEN (open these in external viewers)");
        System.out.println(avroOut.toAbsolutePath());
        System.out.println(parquetOut.toAbsolutePath());
        System.out.println(schemaOut.toAbsolutePath());
        System.out.println(jsonOut.toAbsolutePath());

        // Structural sanity â€” full value assertions live in JsonIntegrationTest;
        // here we just make sure the pipeline produced the expected shape.
        assertThat(avroBytes).isNotEmpty();
        assertThat(parquetBytes).isNotEmpty();
        assertThat(roundTripped.get("customerName").toString()).isEqualTo("Ada Lovelace");
        @SuppressWarnings("unchecked")
        List<GenericRecord> items = (List<GenericRecord>) roundTripped.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("sku").toString()).isEqualTo("BOOK-001");
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

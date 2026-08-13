package io.github.jabhijeet.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jabhijeet.schema.fixtures.FlatDemoEmployee;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import io.github.jabhijeet.schema.json.JsonIO;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Visual + golden-fixture demo: complex nested JSON in &rarr; <em>flat</em> Avro + Parquet out.
 *
 * <p>Mirrors {@code ComplexJsonVisualDemoTest} but enables {@code flattenNestedRecords(true)},
 * collapsing all nested records into top-level, path-joined fields. The same
 * {@code employee-flat.input.json} input document is read in its original nested form; the
 * flattening happens entirely in the schema and in {@link JsonIO}'s converter — the input
 * file is never rewritten.
 *
 * <p>Flattening showcase with {@code FlatDemoEmployee}:
 * <ul>
 *   <li><b>2-level</b>: {@code homeAddress.street} &rarr; {@code homeAddress_street}, etc.
 *       Leaf rename: {@code homeAddress.zipCode} annotated {@code @SchemaField(name="zip")}
 *       &rarr; {@code homeAddress_zip}.</li>
 *   <li><b>3-level</b>: {@code department} field renamed to {@code "dept"} via {@code @SchemaField};
 *       {@code department.location.building} &rarr; {@code dept_location_building}.</li>
 *   <li><b>Nullable propagation</b>: {@code manager} is a nullable reference &rarr;
 *       {@code manager_name} and {@code manager_title} inherit nullability even though the
 *       leaves themselves are Strings (nullable by default).</li>
 *   <li><b>List boundary</b>: {@code projects} stays as {@code ARRAY} of nested records —
 *       flatten stops at collection boundaries.</li>
 *   <li><b>Map boundary</b>: {@code attributes} stays as {@code MAP(STRING,STRING)} —
 *       flatten stops at map boundaries.</li>
 * </ul>
 *
 * <p>Fixtures live in {@code src/test/resources/demo-fixtures/}:
 * <ul>
 *   <li>{@code employee-flat.input.json}  &mdash; the nested input document (source of truth)</li>
 *   <li>{@code employee-flat.avsc}         &mdash; expected generated Avro schema (after flatten)</li>
 *   <li>{@code employee-flat.avro}         &mdash; expected Avro OCF payload</li>
 *   <li>{@code employee-flat.parquet}      &mdash; expected Parquet payload</li>
 * </ul>
 *
 * <p><b>Bootstrapping</b>: binary {@code .avro} and {@code .parquet} golden fixtures are
 * not byte-deterministic (Avro embeds a random sync marker; Parquet writes a
 * {@code created_by} footer).  On the very first run the golden assertions are
 * <em>skipped</em> (via {@link org.junit.jupiter.api.Assumptions#assumeTrue assumeTrue}) if
 * the resource is absent, and the freshly produced artifacts are written to
 * {@code target/demo-output/}.  Promote them to {@code src/test/resources/demo-fixtures/}
 * and subsequent runs will assert full golden equality on decoded content.
 *
 * <p>The test also writes the freshly-produced artifacts to {@code target/demo-output/} on
 * every run so they can be inspected in external Avro/Parquet viewers.
 */
class FlattenJsonVisualDemoTest {

    private static final String FIXTURE_DIR     = "demo-fixtures/";
    private static final String INPUT_JSON_RESOURCE = FIXTURE_DIR + "employee-flat.input.json";
    private static final String AVSC_RESOURCE   = FIXTURE_DIR + "employee-flat.avsc";
    private static final String AVRO_RESOURCE    = FIXTURE_DIR + "employee-flat.avro";
    private static final String PARQUET_RESOURCE = FIXTURE_DIR + "employee-flat.parquet";

    private static final PojoSchemaGenerator FLAT = PojoSchemaGenerator.builder()
            .flattenNestedRecords(true)
            .build();

    @Test
    void demo_complex_nested_json_to_flat_avro_and_parquet() throws IOException {
        String inputJson = readResourceAsString(INPUT_JSON_RESOURCE);

        Schema avroSchema   = FLAT.generateAvro(FlatDemoEmployee.class);
        MessageType parquetSchema = FLAT.generateParquet(FlatDemoEmployee.class);

        configureStringsAsString(avroSchema);

        // ---------------------------------------------------------------- console banners

        printBanner("1. INPUT JSON (complex nested document — will be read into a FLAT record)");
        System.out.println(prettyJson(inputJson));

        printBanner("2. FLAT AVRO SCHEMA (nested records collapsed by flattenNestedRecords=true)");
        System.out.println(avroSchema.toString(true));

        printBanner("3. FLAT PARQUET SCHEMA (all primitive, no nested GROUP)");
        System.out.println(parquetSchema.toString());

        // ---------------------------------------------------------------- JSON → flat record

        GenericRecord record = JsonIO.toRecord(inputJson, avroSchema);
        printBanner("4. FLAT GenericRecord (JSON walked along pojoSchemaFlattenSourcePath props)");
        System.out.println(record);

        // ---------------------------------------------------------------- serialise

        byte[] avroBytes    = AvroIO.toBytes(avroSchema, record);
        byte[] parquetBytes = ParquetIO.toBytes(avroSchema, record);

        printBanner("5. AVRO binary payload (Object Container File)");
        System.out.println("size: " + avroBytes.length + " bytes");
        System.out.println("hex : " + hexPreview(avroBytes, 128));

        printBanner("6. PARQUET binary payload (Snappy-compressed, in-memory — no HADOOP_HOME needed)");
        System.out.println("size: " + parquetBytes.length + " bytes");
        System.out.println("hex : " + hexPreview(parquetBytes, 128));

        GenericRecord roundTripped = ParquetIO.fromBytes(parquetBytes);
        printBanner("7. ROUND-TRIP: record read back from Parquet bytes");
        System.out.println(roundTripped);

        // ---------------------------------------------------------------- JSON round-trip

        String jsonFromRecord     = JsonIO.fromRecord(record);
        List<String> jsonFromAvro = JsonIO.fromAvroBytes(avroBytes);

        printBanner("6. JSON ROUND-TRIP: flat GenericRecord → nested JSON (JsonIO.fromRecord)");
        System.out.println(prettyJson(jsonFromRecord));

        printBanner("7. JSON ROUND-TRIP: Avro bytes → nested JSON (JsonIO.fromAvroBytes)");
        System.out.println(prettyJson(jsonFromAvro.get(0)));

        // ---------------------------------------------------------------- write to target/

        Path outDir = Path.of("target", "demo-output");
        Files.createDirectories(outDir);
        Files.write(outDir.resolve("employee-flat.avro"),        avroBytes);
        Files.write(outDir.resolve("employee-flat.parquet"),     parquetBytes);
        Files.writeString(outDir.resolve("employee-flat.avsc"),  avroSchema.toString(true));
        Files.writeString(outDir.resolve("employee-flat.input.json"), prettyJson(inputJson));
        Files.writeString(outDir.resolve("employee-flat.output.json"), prettyJson(jsonFromRecord));

        printBanner("8. FILES WRITTEN (open in external viewers; promote to resources/ to activate golden assertions)");
        System.out.println(outDir.resolve("employee-flat.avro").toAbsolutePath());
        System.out.println(outDir.resolve("employee-flat.avsc").toAbsolutePath());

        // ---------------------------------------------------------------- schema-shape assertions (always run)

        assertSchemaShape(avroSchema, parquetSchema);

        // ---------------------------------------------------------------- content assertions (always run)

        assertRecordContent(record);
        assertRecordContent(roundTripped);

        // ---------------------------------------------------------------- golden-fixture assertions (skipped until fixtures are promoted)

        byte[] goldenAvsc = tryReadResource(AVSC_RESOURCE);
        byte[] goldenAvro = tryReadResource(AVRO_RESOURCE);

        assumeTrue(goldenAvsc != null,
                "Golden .avsc not yet bootstrapped — copy target/demo-output/employee-flat.avsc "
                        + "to src/test/resources/demo-fixtures/ and re-run");
        Schema expectedSchema = new Schema.Parser().parse(new String(goldenAvsc, StandardCharsets.UTF_8));
        assertThat(avroSchema)
                .as("generated flat Avro schema must match %s", AVSC_RESOURCE)
                .isEqualTo(expectedSchema);

        assumeTrue(goldenAvro != null,
                "Golden .avro not yet bootstrapped — copy target/demo-output/employee-flat.avro "
                        + "to src/test/resources/demo-fixtures/ and re-run");
        GenericRecord expectedFromAvro = AvroIO.fromBytes(goldenAvro);
        assertThat(expectedFromAvro)
                .as("record decoded from %s must match freshly generated record", AVRO_RESOURCE)
                .isEqualTo(record);

        byte[] goldenParquet = tryReadResource(PARQUET_RESOURCE);
        if (goldenParquet != null) {
            GenericRecord expectedFromParquet = ParquetIO.fromBytes(goldenParquet);
            assertThat(expectedFromParquet)
                    .as("record decoded from %s must match freshly generated record", PARQUET_RESOURCE)
                    .isEqualTo(record);
        }

        // ---------------------------------------------------------------- JSON round-trip assertions

        ObjectMapper mapper = new ObjectMapper();

        List<String> jsonFromParquet = JsonIO.fromParquetBytes(parquetBytes);

        for (String json : List.of(jsonFromRecord, jsonFromAvro.get(0), jsonFromParquet.get(0))) {
            JsonNode node = mapper.readTree(json);

            // Scalars survive unchanged
            assertThat(node.path("employeeId").asText()).isEqualTo("EMP-2024-001");
            assertThat(node.path("fullName").asText()).isEqualTo("Grace Hopper");

            // Date and decimal round-trip as ISO-8601 / plain string
            assertThat(node.path("hireDate").asText()).isEqualTo("1944-02-15");
            assertThat(node.path("salary").asText()).isEqualTo("95000.00");

            // 2-level nesting reconstructed from flat fields (homeAddress_* → homeAddress.*)
            assertThat(node.path("homeAddress").path("street").asText()).isEqualTo("456 Compiler Lane");
            assertThat(node.path("homeAddress").path("city").asText()).isEqualTo("New Haven");
            assertThat(node.path("homeAddress").path("zip").asText()).isEqualTo("06511");

            // 3-level nesting + @SchemaField rename ("dept" not "department")
            assertThat(node.path("dept").path("code").asText()).isEqualTo("ENG-SW");
            assertThat(node.path("dept").path("name").asText()).isEqualTo("Software Engineering");
            assertThat(node.path("dept").path("location").path("building").asText()).isEqualTo("Building A");
            assertThat(node.path("dept").path("location").path("floor").asInt()).isEqualTo(3);

            // Nullable intermediate reconstructed
            assertThat(node.path("manager").path("name").asText()).isEqualTo("Alan Turing");

            // Array boundary — projects stays as JSON array of objects
            assertThat(node.path("projects").isArray()).isTrue();
            assertThat(node.path("projects").size()).isEqualTo(3);
            assertThat(node.path("projects").get(0).path("projectCode").asText()).isEqualTo("COBOL-1");

            // Map boundary — attributes stays as JSON object; keys use no separator
            assertThat(node.path("attributes").path("clearance").asText()).isEqualTo("top-secret");
            assertThat(node.path("attributes").path("team").asText()).isEqualTo("compilers");
        }
    }

    // ---------------------------------------------------------------- assertion helpers

    /**
     * Verifies the structural invariants of the flattened schema:
     * <ul>
     *   <li>Top-level record is flat (no RECORD-type fields, except inside ARRAY/MAP).</li>
     *   <li>Rename annotations are honoured in flat names.</li>
     *   <li>Collections remain as ARRAY / MAP at the top level.</li>
     *   <li>The schema carries the {@code pojoSchemaFlattened} prop.</li>
     *   <li>The Parquet schema mirrors the Avro flat structure.</li>
     * </ul>
     */
    private static void assertSchemaShape(Schema avroSchema, MessageType parquetSchema) {

        // Top-level schema props
        assertThat(avroSchema.getObjectProp(SchemaProps.FLATTENED))
                .as("flat schema must carry pojoSchemaFlattened=true")
                .isEqualTo(Boolean.TRUE);
        assertThat(avroSchema.getObjectProp(SchemaProps.FLATTEN_SEPARATOR))
                .as("separator must be the default '_'")
                .isEqualTo("_");

        List<String> fieldNames = avroSchema.getFields().stream()
                .map(Schema.Field::name)
                .collect(Collectors.toList());

        // Scalar fields survive unchanged
        assertThat(fieldNames).contains("employeeId", "fullName", "email", "salary", "hireDate");

        // homeAddress flattened (2-level); zipCode renamed to "zip" via @SchemaField
        assertThat(fieldNames).contains(
                "homeAddress_street", "homeAddress_city", "homeAddress_state",
                "homeAddress_zip",    "homeAddress_country");
        assertThat(fieldNames).doesNotContain("homeAddress", "homeAddress_zipCode");

        // department renamed to "dept" via @SchemaField; 3-level flatten of location
        assertThat(fieldNames).contains(
                "dept_code", "dept_name",
                "dept_location_building", "dept_location_floor");
        assertThat(fieldNames).doesNotContain("department", "dept_location");

        // manager flattened (nullable intermediate)
        assertThat(fieldNames).contains("manager_name", "manager_title");
        assertThat(fieldNames).doesNotContain("manager");

        // manager_name is nullable because the manager intermediate is nullable
        Schema managerName = avroSchema.getField("manager_name").schema();
        assertThat(managerName.getType())
                .as("manager_name must be nullable (inherited from nullable manager field)")
                .isEqualTo(Schema.Type.UNION);
        assertThat(managerName.getTypes()).extracting(Schema::getType)
                .contains(Schema.Type.NULL, Schema.Type.STRING);

        // projects: flatten stops at List boundary
        assertThat(fieldNames).contains("projects");
        Schema projectsSchema = unwrapNullable(avroSchema.getField("projects").schema());
        assertThat(projectsSchema.getType())
                .as("projects must remain an ARRAY (flatten stops at list boundaries)")
                .isEqualTo(Schema.Type.ARRAY);
        Schema projectElement = projectsSchema.getElementType();
        assertThat(projectElement.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(projectElement.getFields()).extracting(Schema.Field::name)
                .containsExactlyInAnyOrder("projectCode", "projectName", "role");

        // attributes: flatten stops at Map boundary
        assertThat(fieldNames).contains("attributes");
        Schema attributesSchema = unwrapNullable(avroSchema.getField("attributes").schema());
        assertThat(attributesSchema.getType())
                .as("attributes must remain a MAP (flatten stops at map boundaries)")
                .isEqualTo(Schema.Type.MAP);

        // No nested RECORD fields at top level (outside collections)
        for (Schema.Field f : avroSchema.getFields()) {
            Schema effectiveType = unwrapNullable(f.schema());
            if (effectiveType.getType() == Schema.Type.ARRAY
                    || effectiveType.getType() == Schema.Type.MAP) {
                continue; // collections are expected — element types may be records
            }
            assertThat(effectiveType.getType())
                    .as("top-level field '%s' must not be a RECORD after flattening", f.name())
                    .isNotEqualTo(Schema.Type.RECORD);
        }

        // Parquet schema: every top-level column must be primitive (no GROUP except inside LIST/MAP)
        for (Type col : parquetSchema.getFields()) {
            if (col.getName().equals("projects") || col.getName().equals("attributes")) {
                continue; // these are wrapped LIST/MAP groups
            }
            assertThat(col.isPrimitive())
                    .as("Parquet column '%s' must be primitive after flattening", col.getName())
                    .isTrue();
        }
    }

    private static void assertRecordContent(GenericRecord record) {
        assertThat(record.get("fullName").toString()).isEqualTo("Grace Hopper");
        assertThat(record.get("employeeId").toString()).isEqualTo("EMP-2024-001");

        // homeAddress flattened
        assertThat(record.get("homeAddress_street").toString()).isEqualTo("456 Compiler Lane");
        assertThat(record.get("homeAddress_city").toString()).isEqualTo("New Haven");
        assertThat(record.get("homeAddress_zip").toString()).isEqualTo("06511");

        // department renamed to "dept", 3-level
        assertThat(record.get("dept_code").toString()).isEqualTo("ENG-SW");
        assertThat(record.get("dept_name").toString()).isEqualTo("Software Engineering");
        assertThat(record.get("dept_location_building").toString()).isEqualTo("Building A");
        assertThat(record.get("dept_location_floor")).isEqualTo(3);

        // manager flattened (nullable)
        assertThat(record.get("manager_name").toString()).isEqualTo("Alan Turing");
        assertThat(record.get("manager_title").toString()).isEqualTo("VP Engineering");

        // projects stayed as array
        @SuppressWarnings("unchecked")
        List<GenericRecord> projects = (List<GenericRecord>) record.get("projects");
        assertThat(projects).hasSize(3);
        assertThat(projects.get(0).get("projectCode").toString()).isEqualTo("COBOL-1");
        assertThat(projects.get(1).get("projectCode").toString()).isEqualTo("FLOW-1");
        assertThat(projects.get(2).get("role").toString()).isEqualTo("Contributor");
    }

    // ---------------------------------------------------------------- resource loading

    private static String readResourceAsString(String resource) throws IOException {
        return new String(readResourceAsBytes(resource), StandardCharsets.UTF_8);
    }

    private static byte[] readResourceAsBytes(String resource) throws IOException {
        ClassLoader cl = FlattenJsonVisualDemoTest.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing test resource: " + resource);
            return in.readAllBytes();
        }
    }

    /** Returns the resource bytes, or {@code null} if the resource is not on the classpath. */
    private static byte[] tryReadResource(String resource) {
        ClassLoader cl = FlattenJsonVisualDemoTest.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Schema unwrapNullable(Schema s) {
        if (s.getType() != Schema.Type.UNION) return s;
        return s.getTypes().stream()
                .filter(b -> b.getType() != Schema.Type.NULL)
                .findFirst()
                .orElseThrow();
    }

    private static void printBanner(String title) {
        String bar = "=".repeat(Math.max(72, title.length() + 4));
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
                for (Schema.Field f : schema.getFields()) configureStringsAsString(f.schema(), visited);
                break;
            case UNION:
                for (Schema s : schema.getTypes()) configureStringsAsString(s, visited);
                break;
            case ARRAY:
                configureStringsAsString(schema.getElementType(), visited);
                break;
            default:
                break;
        }
    }
}

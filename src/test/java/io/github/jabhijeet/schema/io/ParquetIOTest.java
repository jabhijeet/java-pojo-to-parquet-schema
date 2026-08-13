package io.github.jabhijeet.schema.io;

import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.fixtures.AllPrimitivesPojo;
import io.github.jabhijeet.schema.fixtures.CollectionsPojo;
import io.github.jabhijeet.schema.fixtures.NestedCollectionsPojo;
import io.github.jabhijeet.schema.fixtures.OuterPojo;
import io.github.jabhijeet.schema.fixtures.Person;
import io.github.jabhijeet.schema.json.JsonIO;
import io.github.jabhijeet.schema.json.JsonToGenericRecordConverter;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link ParquetIO} can write and read Parquet fully in-memory
 * without a filesystem or HADOOP_HOME environment variable.
 */
class ParquetIOTest {

    private static final Schema PERSON_SCHEMA = PojoSchemaGenerator.toAvro(Person.class);

    // ---------------------------------------------------------------- toBytes / fromBytes

    @Test
    void single_record_round_trips() {
        GenericRecord record = buildPerson("Alice", 30);

        byte[] bytes = ParquetIO.toBytes(PERSON_SCHEMA, record);
        assertThat(bytes).isNotEmpty();

        GenericRecord back = ParquetIO.fromBytes(bytes);
        assertThat(back.get("age")).isEqualTo(30);
        assertThat(back.get("name").toString()).isEqualTo("Alice");
    }

    @Test
    void multiple_records_round_trip() {
        List<GenericRecord> records = List.of(
                buildPerson("Bob", 25),
                buildPerson("Carol", 35),
                buildPerson("Dave", 45));

        byte[] bytes = ParquetIO.toBytes(PERSON_SCHEMA, records);
        List<GenericRecord> back = ParquetIO.readAll(bytes);

        assertThat(back).hasSize(3);
        assertThat(back.get(0).get("name").toString()).isEqualTo("Bob");
        assertThat(back.get(1).get("age")).isEqualTo(35);
        assertThat(back.get(2).get("name").toString()).isEqualTo("Dave");
    }

    @Test
    void empty_collection_writes_valid_parquet_with_no_records() {
        byte[] bytes = ParquetIO.toBytes(PERSON_SCHEMA, List.of());
        assertThat(bytes).isNotEmpty(); // valid Parquet with footer
        assertThat(ParquetIO.readAll(bytes)).isEmpty();
    }

    @Test
    void fromBytes_empty_array_throws() {
        assertThatThrownBy(() -> ParquetIO.readAll(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromBytes_no_records_throws() {
        byte[] empty = ParquetIO.toBytes(PERSON_SCHEMA, List.of());
        assertThatThrownBy(() -> ParquetIO.fromBytes(empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no records");
    }

    // ---------------------------------------------------------------- JSON → Parquet → JSON

    @Test
    void json_to_parquet_bytes_and_back_via_json_io() {
        String json = "{\"name\":\"Eve\",\"age\":28,\"active\":true,\"score\":9.5," +
                "\"createdAtMs\":0,\"balance\":null,\"dob\":null,\"updatedAt\":null," +
                "\"favouriteColor\":null,\"primaryAddress\":null,\"addresses\":null," +
                "\"tags\":null,\"nickname\":null,\"id\":null,\"email_address\":null}";

        byte[] bytes = JsonIO.toParquetBytes(json, PERSON_SCHEMA);
        List<String> jsons = JsonIO.fromParquetBytes(bytes);

        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("\"age\":28");
        assertThat(jsons.get(0)).contains("\"name\":\"Eve\"");
    }

    @Test
    void batch_json_to_parquet_and_back() {
        Schema schema = PojoSchemaGenerator.toAvro(AllPrimitivesPojo.class);
        String nullBoxed = "\"bBool\":null,\"bByte\":null,\"bShort\":null,\"bInt\":null," +
                "\"bLong\":null,\"bFloat\":null,\"bDouble\":null,\"bChar\":null";
        String r1 = "{\"pBool\":true,\"pByte\":1,\"pShort\":1,\"pInt\":1,\"pLong\":2," +
                "\"pFloat\":3.0,\"pDouble\":4.0,\"pChar\":\"A\"," + nullBoxed + "}";
        String r2 = "{\"pBool\":false,\"pByte\":2,\"pShort\":2,\"pInt\":10,\"pLong\":20," +
                "\"pFloat\":30.0,\"pDouble\":40.0,\"pChar\":\"B\"," + nullBoxed + "}";

        byte[] bytes = JsonIO.toParquetBytesAll("[" + r1 + "," + r2 + "]", schema);
        List<String> jsons = JsonIO.fromParquetBytes(bytes);

        assertThat(jsons).hasSize(2);
        assertThat(jsons.get(0)).contains("\"pInt\":1");
        assertThat(jsons.get(1)).contains("\"pInt\":10");
    }

    @Test
    void nested_pojo_round_trips_through_parquet() {
        Schema schema = PojoSchemaGenerator.toAvro(OuterPojo.class);
        JsonToGenericRecordConverter converter = new JsonToGenericRecordConverter();
        String json = "{\"mid\":{\"midLabel\":\"m\",\"leaf\":{\"leafValue\":42,\"leafLabel\":null}," +
                "\"leaves\":[]},\"mids\":[]}";

        GenericRecord record = converter.convert(json, schema);
        byte[] bytes = ParquetIO.toBytes(schema, record);
        GenericRecord back = ParquetIO.fromBytes(bytes);

        GenericRecord mid = (GenericRecord) back.get("mid");
        assertThat(mid.get("midLabel").toString()).isEqualTo("m");
    }

    // ---------------------------------------------------------------- helpers

    private static GenericRecord buildPerson(String name, int age) {
        GenericData.Record r = new GenericData.Record(PERSON_SCHEMA);
        r.put("name", name);
        r.put("age", age);
        r.put("active", true);
        r.put("score", 0.0d);
        r.put("createdAtMs", 0L);
        return r;
    }
}

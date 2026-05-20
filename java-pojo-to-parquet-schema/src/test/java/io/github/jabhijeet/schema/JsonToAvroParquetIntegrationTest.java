package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.fixtures.AllPrimitivesPojo;
import io.github.jabhijeet.schema.fixtures.Person;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import io.github.jabhijeet.schema.json.JsonIO;
import io.github.jabhijeet.schema.json.JsonToGenericRecordConverter;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration: JSON string → GenericRecord → Avro bytes + Parquet bytes,
 * then read back and verify. All in-memory, no HADOOP_HOME required.
 */
class JsonToAvroParquetIntegrationTest {

    private static final Schema PERSON_SCHEMA = PojoSchemaGenerator.toAvro(Person.class);

    private static final String PERSON_JSON = """
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "name": "Ada Lovelace",
              "age": 36,
              "active": true,
              "score": 9.9,
              "createdAtMs": 1000000,
              "dob": "1815-12-10",
              "updatedAt": "1852-11-27T00:00:00Z",
              "favouriteColor": "BLUE",
              "balance": "1234.56",
              "primaryAddress": null,
              "addresses": [],
              "tags": {"role": "mathematician"},
              "nickname": "Ada",
              "email_address": "ada@example.com"
            }
            """;

    // ---------------------------------------------------------------- JSON → Avro → back

    @Test
    void json_to_avro_bytes_and_back() {
        byte[] bytes = JsonIO.toAvroBytes(PERSON_JSON, PERSON_SCHEMA);
        assertThat(bytes).isNotEmpty();

        List<GenericRecord> records = AvroIO.readAll(bytes);
        assertThat(records).hasSize(1);
        GenericRecord r = records.get(0);

        assertThat(r.get("name").toString()).isEqualTo("Ada Lovelace");
        assertThat(r.get("age")).isEqualTo(36);
        assertThat(r.get("active")).isEqualTo(true);
    }

    // ---------------------------------------------------------------- JSON → Parquet → back

    @Test
    void json_to_parquet_bytes_and_back() {
        byte[] bytes = JsonIO.toParquetBytes(PERSON_JSON, PERSON_SCHEMA);
        assertThat(bytes).isNotEmpty();

        List<GenericRecord> records = ParquetIO.readAll(bytes);
        assertThat(records).hasSize(1);
        GenericRecord r = records.get(0);

        assertThat(r.get("name").toString()).isEqualTo("Ada Lovelace");
        assertThat(r.get("age")).isEqualTo(36);
        assertThat(r.get("active")).isEqualTo(true);
    }

    // ---------------------------------------------------------------- cross-format parity

    @Test
    void avro_and_parquet_bytes_decode_to_equivalent_records() {
        JsonToGenericRecordConverter converter = new JsonToGenericRecordConverter();
        GenericRecord original = converter.convert(PERSON_JSON, PERSON_SCHEMA);

        byte[] avroBytes    = AvroIO.toBytes(PERSON_SCHEMA, original);
        byte[] parquetBytes = ParquetIO.toBytes(PERSON_SCHEMA, original);

        GenericRecord fromAvro    = AvroIO.fromBytes(avroBytes);
        GenericRecord fromParquet = ParquetIO.fromBytes(parquetBytes);

        // Core primitive fields must match across both formats
        assertThat(fromAvro.get("name").toString()).isEqualTo(fromParquet.get("name").toString());
        assertThat(fromAvro.get("age")).isEqualTo(fromParquet.get("age"));
        assertThat(fromAvro.get("active")).isEqualTo(fromParquet.get("active"));
    }

    // ---------------------------------------------------------------- batch

    @Test
    void batch_json_array_to_avro_and_parquet() {
        Schema schema = PojoSchemaGenerator.toAvro(AllPrimitivesPojo.class);
        String nullBoxed = "\"bBool\":null,\"bByte\":null,\"bShort\":null,\"bInt\":null," +
                "\"bLong\":null,\"bFloat\":null,\"bDouble\":null,\"bChar\":null";
        String batch = "[" +
                "{\"pBool\":true,\"pByte\":1,\"pShort\":1,\"pInt\":1,\"pLong\":10," +
                "\"pFloat\":1.0,\"pDouble\":1.0,\"pChar\":\"A\"," + nullBoxed + "}," +
                "{\"pBool\":false,\"pByte\":2,\"pShort\":2,\"pInt\":2,\"pLong\":20," +
                "\"pFloat\":2.0,\"pDouble\":2.0,\"pChar\":\"B\"," + nullBoxed + "}" +
                "]";

        byte[] avroBytes    = JsonIO.toAvroBytesAll(batch, schema);
        byte[] parquetBytes = JsonIO.toParquetBytesAll(batch, schema);

        assertThat(AvroIO.readAll(avroBytes)).hasSize(2);
        assertThat(ParquetIO.readAll(parquetBytes)).hasSize(2);

        assertThat(ParquetIO.readAll(parquetBytes).get(0).get("pInt")).isEqualTo(1);
        assertThat(ParquetIO.readAll(parquetBytes).get(1).get("pInt")).isEqualTo(2);
    }

    // ---------------------------------------------------------------- JSON round-trip

    @Test
    void json_parquet_json_round_trip() {
        byte[] parquetBytes = JsonIO.toParquetBytes(PERSON_JSON, PERSON_SCHEMA);
        List<String> jsons = JsonIO.fromParquetBytes(parquetBytes);

        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("\"name\":\"Ada Lovelace\"");
        assertThat(jsons.get(0)).contains("\"age\":36");
        assertThat(jsons.get(0)).contains("BLUE");
    }
}

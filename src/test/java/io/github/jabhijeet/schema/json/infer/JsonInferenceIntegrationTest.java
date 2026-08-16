package io.github.jabhijeet.schema.json.infer;

import io.github.jabhijeet.schema.FieldNamingStrategy;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.IcebergIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import io.github.jabhijeet.schema.json.JsonIO;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonInferenceIntegrationTest {

    private static final String ORDER_JSON = """
            {
              "orderId":    "550e8400-e29b-41d4-a716-446655440000",
              "customerId": "CUST-001",
              "amount":     "199.98",
              "placedAt":   "2025-06-15T10:30:00Z",
              "items": [
                {"sku": "PROD-A", "qty": 2, "unitPrice": "49.99"},
                {"sku": "PROD-B", "qty": 1, "unitPrice": "99.99"}
              ],
              "tags": {"source": "web", "region": "us-east-1"}
            }
            """;

    // ---------------------------------------------------------------- Parquet round-trip

    @Test
    void schema_less_to_parquet_bytes_and_back() {
        byte[] bytes = JsonIO.toParquetBytes(ORDER_JSON);
        assertThat(bytes).isNotEmpty();

        List<GenericRecord> records = ParquetIO.readAll(bytes);
        assertThat(records).hasSize(1);
        GenericRecord order = records.get(0);

        assertThat(order.get("customerId").toString()).isEqualTo("CUST-001");
        assertThat(order.get("orderId").toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(order.get("amount").toString()).isEqualTo("199.98");
        assertThat(order.get("placedAt").toString()).isEqualTo("2025-06-15T10:30:00Z");
    }

    @Test
    void schema_less_batch_to_parquet_bytes() {
        String batchJson = """
                [
                  {"orderId":"550e8400-e29b-41d4-a716-446655440001","customerId":"A",
                   "amount":"10.00","placedAt":"2025-01-01T00:00:00Z",
                   "items":[{"sku":"X","qty":1,"unitPrice":"10.00"}],
                   "tags":{"src":"app"}},
                  {"orderId":"550e8400-e29b-41d4-a716-446655440002","customerId":"B",
                   "amount":"20.00","placedAt":"2025-01-02T00:00:00Z",
                   "items":[{"sku":"Y","qty":2,"unitPrice":"10.00"}],
                   "tags":{"src":"web"}}
                ]
                """;

        byte[] bytes = JsonIO.toParquetBytesAll(batchJson);
        List<GenericRecord> records = ParquetIO.readAll(bytes);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("customerId").toString()).isEqualTo("A");
        assertThat(records.get(1).get("customerId").toString()).isEqualTo("B");
    }

    @Test
    void schema_less_parquet_input_stream_round_trips() {
        InputStream stream = JsonIO.toParquetInputStream(ORDER_JSON);

        List<GenericRecord> records = ParquetIO.readAll(stream);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("customerId").toString()).isEqualTo("CUST-001");
    }

    // ---------------------------------------------------------------- Avro round-trip

    @Test
    void schema_less_to_avro_bytes_and_back() {
        byte[] bytes = JsonIO.toAvroBytes(ORDER_JSON);
        assertThat(bytes).isNotEmpty();

        List<GenericRecord> records = AvroIO.readAll(bytes);
        assertThat(records).hasSize(1);
        GenericRecord order = records.get(0);

        assertThat(order.get("customerId").toString()).isEqualTo("CUST-001");
        assertThat(order.get("orderId").toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void schema_less_avro_batch_input_stream_round_trips() {
        String batchJson = """
                [
                  {"eventId":"evt-1","active":true,"score":10},
                  {"eventId":"evt-2","active":false,"score":25}
                ]
                """;

        InputStream stream = JsonIO.toAvroInputStreamAll(batchJson);

        List<GenericRecord> records = AvroIO.readAll(stream);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("eventId").toString()).isEqualTo("evt-1");
        assertThat(records.get(1).get("score")).isEqualTo(25L);
    }

    // ---------------------------------------------------------------- Iceberg round-trip

    @Test
    void schema_less_to_iceberg_table() {
        IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(ORDER_JSON);
        assertThat(table.schema().columns()).hasSizeGreaterThan(0);

        List<org.apache.iceberg.data.Record> rows =
                IcebergIO.readAll(table);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getField("customerId").toString()).isEqualTo("CUST-001");
    }

    @Test
    void schema_less_json_array_to_iceberg_table() {
        String batchJson = """
                [
                  {"eventId":"evt-1","active":true,"score":10},
                  {"eventId":"evt-2","active":false,"score":25}
                ]
                """;

        IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(batchJson);

        assertThat(table.schema().findField("eventId")).isNotNull();
        List<org.apache.iceberg.data.Record> rows = IcebergIO.readAll(table);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getField("eventId").toString()).isEqualTo("evt-1");
        assertThat(rows.get(1).getField("score")).isEqualTo(25L);

        assertThat(JsonIO.fromIcebergTable(table))
                .hasSize(2)
                .anySatisfy(json -> assertThat(json).contains("evt-2"));
    }

    // ---------------------------------------------------------------- toRecord

    @Test
    void schema_less_to_record() {
        GenericRecord record = JsonIO.toRecord(ORDER_JSON);
        assertThat(record).isNotNull();
        assertThat(record.get("customerId").toString()).isEqualTo("CUST-001");
    }

    @Test
    void schema_less_to_records_from_array() {
        String json = "[" +
                "{\"orderId\":\"550e8400-e29b-41d4-a716-446655440005\",\"customerId\":\"E\"," +
                "\"amount\":\"5.00\",\"placedAt\":1000,\"items\":[],\"tags\":{}}" +
                "]";
        List<GenericRecord> records = JsonIO.toRecords(json);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("customerId").toString()).isEqualTo("E");
    }

    // ---------------------------------------------------------------- custom options

    @Test
    void custom_options_snake_case_and_non_nullable() {
        String json = "{\"first_name\":\"John\",\"age\":30}";
        SchemaInferenceOptions opts = SchemaInferenceOptions.builder()
                .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
                .nullableByDefault(false)
                .build();

        GenericRecord record = JsonIO.toRecord(json, opts);
        assertThat(record.getSchema().getField("first_name")).isNotNull();
        assertThat(record.get("first_name").toString()).isEqualTo("John");
        assertThat(record.get("age")).isEqualTo(30L);
    }

    @Test
    void custom_options_with_precision() {
        String json = "{\"amount\":1234.56}";
        SchemaInferenceOptions opts = SchemaInferenceOptions.builder()
                .defaultDecimal(10, 2)
                .build();

        GenericRecord record = JsonIO.toRecord(json, opts);
        assertThat(record.getSchema().getField("amount")).isNotNull();
    }

    // ---------------------------------------------------------------- JSON → JSON round-trip

    @Test
    void schema_less_parquet_round_trip_to_json() {
        byte[] bytes = JsonIO.toParquetBytes(ORDER_JSON);
        List<String> jsons = JsonIO.fromParquetBytes(bytes);
        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("CUST-001");
    }

    @Test
    void schema_less_avro_round_trip_to_json() {
        byte[] bytes = JsonIO.toAvroBytes(ORDER_JSON);
        List<String> jsons = JsonIO.fromAvroBytes(bytes);
        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("CUST-001");
    }

    @Test
    void schema_less_iceberg_round_trip_to_json() {
        IcebergIO.InMemoryTable table = JsonIO.toIcebergTable(ORDER_JSON);
        List<String> jsons = JsonIO.fromIcebergTable(table);
        assertThat(jsons).hasSize(1);
        assertThat(jsons.get(0)).contains("CUST-001");
    }
}

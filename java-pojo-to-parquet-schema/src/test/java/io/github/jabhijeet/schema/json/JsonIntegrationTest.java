package io.github.jabhijeet.schema.json;

import io.github.jabhijeet.schema.PojoSchemaGenerator;
import io.github.jabhijeet.schema.TimezoneHandling;
import io.github.jabhijeet.schema.fixtures.TemporalTypesPojo;
import io.github.jabhijeet.schema.io.AvroIO;
import io.github.jabhijeet.schema.io.ParquetIO;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests: JSON string â†’ bytes (Avro or Parquet) â†’ read back â†’ verify values.
 *
 * <p>All I/O is in-memory. Uses {@link JsonIO} as the public entry point.
 */
class JsonIntegrationTest {

    // ---------------------------------------------------------------- schemas

    private static final Schema ORDER_SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "Order",
              "namespace": "io.github.jabhijeet.test",
              "fields": [
                {"name": "orderId",    "type": {"type": "string", "logicalType": "uuid"}},
                {"name": "customerId", "type": "string"},
                {"name": "amount",     "type": {"type": "bytes", "logicalType": "decimal",
                                                "precision": 12, "scale": 2}},
                {"name": "placedAt",   "type": {"type": "long", "logicalType": "timestamp-millis"}},
                {"name": "items",      "type": {"type": "array", "items": {
                  "type": "record", "name": "OrderItem",
                  "namespace": "io.github.jabhijeet.test",
                  "fields": [
                    {"name": "sku",      "type": "string"},
                    {"name": "qty",      "type": "int"},
                    {"name": "unitPrice","type": {"type": "bytes", "logicalType": "decimal",
                                                  "precision": 10, "scale": 2}}
                  ]
                }}},
                {"name": "tags",       "type": {"type": "map", "values": "string"}}
              ]
            }
            """);

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

    private static final Schema EVENT_SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "Event",
              "namespace": "io.github.jabhijeet.test",
              "fields": [
                {"name": "eventType", "type": "string"},
                {"name": "ts",        "type": {"type": "long", "logicalType": "local-timestamp-millis"}},
                {"name": "payload",   "type": {"type": "map", "values": {
                  "type": "array", "items": "string"
                }}},
                {"name": "severity",  "type": ["null", "int"], "default": null}
              ]
            }
            """);

    // ---------------------------------------------------------------- single record to Avro

    @Test
    void order_to_avro_bytes_and_back() {
        byte[] bytes = JsonIO.toAvroBytes(ORDER_JSON, ORDER_SCHEMA);
        assertThat(bytes).isNotEmpty();

        List<GenericRecord> records = AvroIO.readAll(bytes);
        assertThat(records).hasSize(1);
        GenericRecord order = records.get(0);

        assertThat(order.get("orderId").toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(order.get("customerId").toString()).isEqualTo("CUST-001");

        // Verify decimal amount = 199.98
        ByteBuffer amountBB = (ByteBuffer) order.get("amount");
        Schema amountSchema = ORDER_SCHEMA.getField("amount").schema();
        LogicalTypes.Decimal amountDec = (LogicalTypes.Decimal) amountSchema.getLogicalType();
        BigDecimal amount = new Conversions.DecimalConversion().fromBytes(amountBB.duplicate(), amountSchema, amountDec);
        assertThat(amount).isEqualByComparingTo(new BigDecimal("199.98"));

        // Verify timestamp
        long placedAt = (long) order.get("placedAt");
        assertThat(placedAt).isEqualTo(Instant.parse("2025-06-15T10:30:00Z").toEpochMilli());

        // Verify nested items array
        @SuppressWarnings("unchecked")
        List<GenericRecord> items = (List<GenericRecord>) order.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("sku").toString()).isEqualTo("PROD-A");
        assertThat(items.get(0).get("qty")).isEqualTo(2);

        Schema unitPriceSchema = ORDER_SCHEMA.getField("items").schema()
                .getElementType().getField("unitPrice").schema();
        LogicalTypes.Decimal upDec = (LogicalTypes.Decimal) unitPriceSchema.getLogicalType();
        ByteBuffer up0 = (ByteBuffer) items.get(0).get("unitPrice");
        assertThat(new Conversions.DecimalConversion().fromBytes(up0.duplicate(), unitPriceSchema, upDec))
                .isEqualByComparingTo(new BigDecimal("49.99"));

        // Verify map â€” GenericDatumReader returns Utf8 keys, not String
        @SuppressWarnings("unchecked")
        java.util.Map<Object, Object> tags = (java.util.Map<Object, Object>) order.get("tags");
        assertThat(tags.get(new Utf8("source")).toString()).isEqualTo("web");
        assertThat(tags.get(new Utf8("region")).toString()).isEqualTo("us-east-1");
    }

    // ---------------------------------------------------------------- single record to Parquet

    @Test
    void order_to_parquet_bytes_and_back() {
        byte[] bytes = JsonIO.toParquetBytes(ORDER_JSON, ORDER_SCHEMA);
        assertThat(bytes).isNotEmpty();

        List<GenericRecord> records = ParquetIO.readAll(bytes);
        assertThat(records).hasSize(1);
        GenericRecord order = records.get(0);

        assertThat(order.get("orderId").toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(order.get("customerId").toString()).isEqualTo("CUST-001");

        @SuppressWarnings("unchecked")
        List<GenericRecord> items = (List<GenericRecord>) order.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(1).get("sku").toString()).isEqualTo("PROD-B");
    }

    @Test
    void batch_orders_to_parquet_and_back() {
        String batchJson = """
                [
                  {"orderId":"550e8400-e29b-41d4-a716-446655440003","customerId":"C",
                   "amount":"30.00","placedAt":"2025-01-03T00:00:00Z",
                   "items":[{"sku":"Z","qty":3,"unitPrice":"10.00"}],
                   "tags":{"src":"api"}},
                  {"orderId":"550e8400-e29b-41d4-a716-446655440004","customerId":"D",
                   "amount":"40.00","placedAt":"2025-01-04T00:00:00Z",
                   "items":[],"tags":{}}
                ]
                """;

        byte[] bytes = JsonIO.toParquetBytesAll(batchJson, ORDER_SCHEMA);
        List<GenericRecord> records = ParquetIO.readAll(bytes);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("customerId").toString()).isEqualTo("C");
        assertThat(records.get(1).get("customerId").toString()).isEqualTo("D");
    }

    // ---------------------------------------------------------------- batch (JSON array)

    @Test
    void batch_orders_to_avro_and_back() {
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

        byte[] bytes = JsonIO.toAvroBytesAll(batchJson, ORDER_SCHEMA);
        List<GenericRecord> records = AvroIO.readAll(bytes);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("customerId").toString()).isEqualTo("A");
        assertThat(records.get(1).get("customerId").toString()).isEqualTo("B");
    }

    // ---------------------------------------------------------------- nullable + map of arrays

    @Test
    void event_with_nullable_field_and_map_of_arrays_to_avro() {
        String eventJson = """
                {
                  "eventType": "LOGIN",
                  "ts":        "2025-03-10T08:00:00",
                  "payload":   {"userIds":["u1","u2"], "roles":["admin"]},
                  "severity":  null
                }
                """;

        byte[] bytes = JsonIO.toAvroBytes(eventJson, EVENT_SCHEMA);
        List<GenericRecord> records = AvroIO.readAll(bytes);
        assertThat(records).hasSize(1);
        GenericRecord event = records.get(0);

        assertThat(event.get("eventType").toString()).isEqualTo("LOGIN");
        assertThat(event.get("severity")).isNull();

        // GenericDatumReader returns Utf8 keys in maps
        @SuppressWarnings("unchecked")
        java.util.Map<Object, List<?>> payload = (java.util.Map<Object, List<?>>) event.get("payload");
        assertThat(payload.get(new Utf8("userIds"))).hasSize(2);
        assertThat(payload.get(new Utf8("roles"))).hasSize(1);
    }

    @Test
    void event_missing_nullable_severity_defaults_to_null() {
        // severity has default:null so it's optional in the JSON input
        String eventJson = """
                {
                  "eventType": "LOGOUT",
                  "ts":        "2025-03-10T09:00:00",
                  "payload":   {}
                }
                """;

        byte[] bytes = JsonIO.toAvroBytes(eventJson, EVENT_SCHEMA);
        GenericRecord event = AvroIO.fromBytes(bytes);
        assertThat(event.get("eventType").toString()).isEqualTo("LOGOUT");
        assertThat(event.get("severity")).isNull();
    }

    // ---------------------------------------------------------------- toRecord convenience

    @Test
    void to_record_returns_generic_record() {
        GenericRecord r = JsonIO.toRecord(
                "{\"orderId\":\"550e8400-e29b-41d4-a716-446655440000\",\"customerId\":\"X\"," +
                "\"amount\":\"1.00\",\"placedAt\":\"2025-01-01T00:00:00Z\"," +
                "\"items\":[{\"sku\":\"A\",\"qty\":1,\"unitPrice\":\"1.00\"}]," +
                "\"tags\":{}}",
                ORDER_SCHEMA);
        assertThat(r).isNotNull();
        assertThat(r.get("customerId").toString()).isEqualTo("X");
    }

    @Test
    void to_records_from_array_json() {
        String json = "[" +
                "{\"orderId\":\"550e8400-e29b-41d4-a716-446655440005\",\"customerId\":\"E\"," +
                "\"amount\":\"5.00\",\"placedAt\":1000,\"items\":[],\"tags\":{}}" +
                "]";
        List<GenericRecord> records = JsonIO.toRecords(json, ORDER_SCHEMA);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("customerId").toString()).isEqualTo("E");
    }

    // ---------------------------------------------------------------- TimezoneHandling.PRESERVE roundtrip

    @Test
    void preserve_mode_stores_offset_datetime_as_iso_string_and_round_trips() {
        Schema schema = PojoSchemaGenerator.builder()
                .timezoneHandling(TimezoneHandling.PRESERVE)
                .build()
                .generateAvro(TemporalTypesPojo.class);

        // offsetDateTime / zonedDateTime are plain Avro strings in PRESERVE mode
        assertThat(schema.getField("offsetDateTime").schema().getTypes())
                .anyMatch(s -> s.getType() == Schema.Type.STRING);
        assertThat(schema.getField("zonedDateTime").schema().getTypes())
                .anyMatch(s -> s.getType() == Schema.Type.STRING);

        // JSON with ISO-8601 offset string → GenericRecord → Avro bytes → back → JSON
        String inputJson = """
                {
                  "localDate":null,"sqlDate":null,"localTime":null,
                  "localDateTime":null,
                  "instant":"2025-06-15T10:30:00Z",
                  "offsetDateTime":"2025-06-15T15:30:00+05:00",
                  "zonedDateTime":"2025-06-15T08:30:00-02:00",
                  "utilDate":null,"sqlTimestamp":null
                }
                """;

        byte[] avroBytes = JsonIO.toAvroBytes(inputJson, schema);
        List<String> jsons = JsonIO.fromAvroBytes(avroBytes);
        assertThat(jsons).hasSize(1);

        String out = jsons.get(0);
        // Offset strings must survive the round-trip intact
        assertThat(out).contains("2025-06-15T15:30:00+05:00");
        assertThat(out).contains("2025-06-15T08:30:00-02:00");
        // Instant still goes through timestamp-millis (UTC), comes back as ISO instant
        assertThat(out).contains("2025-06-15T10:30:00Z");
    }

    @Test
    void system_default_mode_stores_utc_types_as_local_timestamps_and_round_trips() {
        Schema schema = PojoSchemaGenerator.builder()
                .timezoneHandling(TimezoneHandling.SYSTEM_DEFAULT)
                .build()
                .generateAvro(TemporalTypesPojo.class);

        // instant → local-timestamp-millis (not UTC-adjusted)
        Schema instantField = schema.getField("instant").schema().getTypes().stream()
                .filter(s -> s.getType() != Schema.Type.NULL)
                .findFirst().orElseThrow();
        assertThat(instantField.getLogicalType().getName()).isEqualTo("local-timestamp-millis");

        // JSON with a local datetime string (no offset) → stores as local millis → reads back as LocalDateTime string
        String inputJson = """
                {
                  "localDate":null,"sqlDate":null,"localTime":null,
                  "localDateTime":"2025-06-15T10:30:00",
                  "instant":"2025-06-15T10:30:00",
                  "offsetDateTime":"2025-06-15T10:30:00",
                  "zonedDateTime":"2025-06-15T10:30:00",
                  "utilDate":null,"sqlTimestamp":null
                }
                """;

        byte[] avroBytes = JsonIO.toAvroBytes(inputJson, schema);
        List<String> jsons = JsonIO.fromAvroBytes(avroBytes);
        assertThat(jsons).hasSize(1);
        // All four local timestamps round-trip to the same local datetime string.
        // LocalDateTime.toString() omits :00 seconds when zero, so expect "T10:30" not "T10:30:00".
        assertThat(jsons.get(0)).contains("2025-06-15T10:30");
    }
}


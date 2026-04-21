package io.github.jabhijeet.schema.json;

import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonToAvroConverterTest {

    private final JsonToAvroConverter converter = new JsonToAvroConverter();

    // ---------------------------------------------------------------- schema builders

    private static Schema parse(String json) {
        return new Schema.Parser().parse(json);
    }

    private static Schema record(String name, String... fieldDefs) {
        StringBuilder sb = new StringBuilder()
                .append("{\"type\":\"record\",\"name\":\"").append(name)
                .append("\",\"namespace\":\"test\",\"fields\":[");
        for (int i = 0; i < fieldDefs.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(fieldDefs[i]);
        }
        sb.append("]}");
        return parse(sb.toString());
    }

    private static String field(String name, String typeJson) {
        return "{\"name\":\"" + name + "\",\"type\":" + typeJson + "}";
    }

    private static String field(String name, String typeJson, String defaultJson) {
        return "{\"name\":\"" + name + "\",\"type\":" + typeJson + ",\"default\":" + defaultJson + "}";
    }

    private static String nullable(String typeJson) {
        return "[\"null\"," + typeJson + "]";
    }

    // ---------------------------------------------------------------- primitives

    @Nested
    class Primitives {

        @Test
        void boolean_fromJson() {
            Schema s = record("B", field("v", "\"boolean\""));
            GenericRecord r = converter.convert("{\"v\":true}", s);
            assertThat(r.get("v")).isEqualTo(true);
        }

        @Test
        void int_fromJson() {
            Schema s = record("I", field("v", "\"int\""));
            GenericRecord r = converter.convert("{\"v\":42}", s);
            assertThat(r.get("v")).isEqualTo(42);
        }

        @Test
        void long_fromJson() {
            Schema s = record("L", field("v", "\"long\""));
            GenericRecord r = converter.convert("{\"v\":9999999999}", s);
            assertThat(r.get("v")).isEqualTo(9999999999L);
        }

        @Test
        void long_fromNumericString() {
            Schema s = record("L", field("v", "\"long\""));
            GenericRecord r = converter.convert("{\"v\":\"9999999999\"}", s);
            assertThat(r.get("v")).isEqualTo(9999999999L);
        }

        @Test
        void float_fromJson() {
            Schema s = record("F", field("v", "\"float\""));
            GenericRecord r = converter.convert("{\"v\":3.14}", s);
            assertThat((float) r.get("v")).isEqualTo(3.14f);
        }

        @Test
        void double_fromJson() {
            Schema s = record("D", field("v", "\"double\""));
            GenericRecord r = converter.convert("{\"v\":2.71828}", s);
            assertThat(r.get("v")).isEqualTo(2.71828);
        }

        @Test
        void string_fromJson() {
            Schema s = record("S", field("v", "\"string\""));
            GenericRecord r = converter.convert("{\"v\":\"hello\"}", s);
            assertThat(r.get("v").toString()).isEqualTo("hello");
        }

        @Test
        void string_coerced_fromNumber() {
            Schema s = record("S", field("v", "\"string\""));
            GenericRecord r = converter.convert("{\"v\":123}", s);
            assertThat(r.get("v").toString()).isEqualTo("123");
        }

        @Test
        void int_overflow_throws() {
            Schema s = record("I", field("v", "\"int\""));
            assertThatThrownBy(() -> converter.convert("{\"v\":9999999999}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("32-bit");
        }

        @Test
        void wrongType_boolean_throws() {
            Schema s = record("B", field("v", "\"boolean\""));
            assertThatThrownBy(() -> converter.convert("{\"v\":42}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("boolean");
        }
    }

    // ---------------------------------------------------------------- nullable unions

    @Nested
    class NullableUnions {

        @Test
        void null_value_accepted() {
            Schema s = record("N", field("v", nullable("\"string\"")));
            GenericRecord r = converter.convert("{\"v\":null}", s);
            assertThat(r.get("v")).isNull();
        }

        @Test
        void non_null_value_accepted() {
            Schema s = record("N", field("v", nullable("\"string\"")));
            GenericRecord r = converter.convert("{\"v\":\"hello\"}", s);
            assertThat(r.get("v").toString()).isEqualTo("hello");
        }

        @Test
        void null_in_required_field_throws() {
            Schema s = record("R", field("v", "\"string\""));
            assertThatThrownBy(() -> converter.convert("{\"v\":null}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("null");
        }
    }

    // ---------------------------------------------------------------- missing fields

    @Nested
    class MissingFields {

        @Test
        void missing_nullable_becomes_null() {
            Schema s = record("M", field("v", nullable("\"string\"")));
            GenericRecord r = converter.convert("{}", s);
            assertThat(r.get("v")).isNull();
        }

        @Test
        void missing_with_default_uses_default() {
            Schema s = record("M", field("v", "\"int\"", "99"));
            GenericRecord r = converter.convert("{}", s);
            assertThat(r.get("v")).isEqualTo(99);
        }

        @Test
        void missing_required_throws() {
            Schema s = record("M", field("v", "\"string\""));
            assertThatThrownBy(() -> converter.convert("{}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("Missing required field");
        }
    }

    // ---------------------------------------------------------------- enums

    @Nested
    class Enums {

        private static final String ENUM_TYPE =
                "{\"type\":\"enum\",\"name\":\"Color\",\"symbols\":[\"RED\",\"GREEN\",\"BLUE\"]}";

        @Test
        void valid_enum_symbol() {
            Schema s = record("E", field("c", ENUM_TYPE));
            GenericRecord r = converter.convert("{\"c\":\"RED\"}", s);
            assertThat(r.get("c").toString()).isEqualTo("RED");
        }

        @Test
        void invalid_enum_symbol_throws() {
            Schema s = record("E", field("c", ENUM_TYPE));
            assertThatThrownBy(() -> converter.convert("{\"c\":\"PURPLE\"}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("PURPLE");
        }
    }

    // ---------------------------------------------------------------- bytes and fixed

    @Nested
    class BytesAndFixed {

        @Test
        void bytes_from_base64() {
            Schema s = record("B", field("data", "\"bytes\""));
            GenericRecord r = converter.convert("{\"data\":\"AQID\"}", s);
            ByteBuffer bb = (ByteBuffer) r.get("data");
            byte[] bytes = new byte[bb.remaining()];
            bb.get(bytes);
            assertThat(bytes).containsExactly((byte) 1, (byte) 2, (byte) 3);
        }

        @Test
        void fixed_correct_size() {
            String fixedType = "{\"type\":\"fixed\",\"name\":\"MD5\",\"size\":3}";
            Schema s = record("F", field("hash", fixedType));
            GenericRecord r = converter.convert("{\"hash\":\"AQID\"}", s);
            assertThat(r.get("hash")).isInstanceOf(GenericData.Fixed.class);
        }

        @Test
        void fixed_wrong_size_throws() {
            String fixedType = "{\"type\":\"fixed\",\"name\":\"MD5\",\"size\":16}";
            Schema s = record("F", field("hash", fixedType));
            assertThatThrownBy(() -> converter.convert("{\"hash\":\"AQID\"}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("16 bytes");
        }
    }

    // ---------------------------------------------------------------- arrays

    @Nested
    class Arrays {

        @Test
        void int_array() {
            Schema s = record("A",
                    field("nums", "{\"type\":\"array\",\"items\":\"int\"}"));
            GenericRecord r = converter.convert("{\"nums\":[1,2,3]}", s);
            @SuppressWarnings("unchecked")
            List<Integer> nums = (List<Integer>) r.get("nums");
            assertThat(nums).containsExactly(1, 2, 3);
        }

        @Test
        void empty_array() {
            Schema s = record("A",
                    field("nums", "{\"type\":\"array\",\"items\":\"int\"}"));
            GenericRecord r = converter.convert("{\"nums\":[]}", s);
            @SuppressWarnings("unchecked")
            List<?> nums = (List<?>) r.get("nums");
            assertThat(nums).isEmpty();
        }

        @Test
        void array_of_records() {
            Schema s = record("A",
                    field("items",
                            "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Item\",\"namespace\":\"test\",\"fields\":[" +
                                    "{\"name\":\"val\",\"type\":\"int\"}]}}"));
            GenericRecord r = converter.convert("{\"items\":[{\"val\":1},{\"val\":2}]}", s);
            @SuppressWarnings("unchecked")
            List<GenericRecord> items = (List<GenericRecord>) r.get("items");
            assertThat(items).hasSize(2);
            assertThat(items.get(0).get("val")).isEqualTo(1);
            assertThat(items.get(1).get("val")).isEqualTo(2);
        }
    }

    // ---------------------------------------------------------------- maps

    @Nested
    class Maps {

        @Test
        void string_map() {
            Schema s = record("M",
                    field("attrs", "{\"type\":\"map\",\"values\":\"string\"}"));
            GenericRecord r = converter.convert("{\"attrs\":{\"k1\":\"v1\",\"k2\":\"v2\"}}", s);
            @SuppressWarnings("unchecked")
            Map<String, Object> attrs = (Map<String, Object>) r.get("attrs");
            assertThat(attrs).hasSize(2);
            assertThat(attrs.get("k1").toString()).isEqualTo("v1");
        }

        @Test
        void int_map() {
            Schema s = record("M",
                    field("counts", "{\"type\":\"map\",\"values\":\"int\"}"));
            GenericRecord r = converter.convert("{\"counts\":{\"a\":1,\"b\":2}}", s);
            @SuppressWarnings("unchecked")
            Map<?, ?> counts = (Map<?, ?>) r.get("counts");
            assertThat(counts.get("a")).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- nested records

    @Nested
    class NestedRecords {

        @Test
        void nested_record() {
            Schema s = record("Outer",
                    field("inner",
                            "{\"type\":\"record\",\"name\":\"Inner\",\"namespace\":\"test\",\"fields\":[" +
                                    "{\"name\":\"x\",\"type\":\"int\"}]}"));
            GenericRecord r = converter.convert("{\"inner\":{\"x\":42}}", s);
            GenericRecord inner = (GenericRecord) r.get("inner");
            assertThat(inner.get("x")).isEqualTo(42);
        }

        @Test
        void nullable_nested_record() {
            String innerType = "{\"type\":\"record\",\"name\":\"Inner\",\"namespace\":\"test\",\"fields\":[" +
                    "{\"name\":\"x\",\"type\":\"int\"}]}";
            Schema s = record("Outer", field("inner", nullable(innerType)));
            GenericRecord r = converter.convert("{\"inner\":null}", s);
            assertThat(r.get("inner")).isNull();
        }
    }

    // ---------------------------------------------------------------- logical types

    @Nested
    class LogicalTypeTests {

        @Test
        void date_from_iso_string() {
            Schema s = record("D",
                    field("d", "{\"type\":\"int\",\"logicalType\":\"date\"}"));
            GenericRecord r = converter.convert("{\"d\":\"2025-06-15\"}", s);
            int days = (int) r.get("d");
            assertThat(days).isEqualTo((int) LocalDate.of(2025, 6, 15).toEpochDay());
        }

        @Test
        void date_from_int() {
            Schema s = record("D",
                    field("d", "{\"type\":\"int\",\"logicalType\":\"date\"}"));
            GenericRecord r = converter.convert("{\"d\":19523}", s);
            assertThat(r.get("d")).isEqualTo(19523);
        }

        @Test
        void time_millis_from_iso_string() {
            Schema s = record("T",
                    field("t", "{\"type\":\"int\",\"logicalType\":\"time-millis\"}"));
            GenericRecord r = converter.convert("{\"t\":\"10:30:00\"}", s);
            int millis = (int) r.get("t");
            assertThat(millis).isEqualTo((int) (LocalTime.of(10, 30, 0).toNanoOfDay() / 1_000_000));
        }

        @Test
        void time_micros_from_iso_string() {
            Schema s = record("T",
                    field("t", "{\"type\":\"long\",\"logicalType\":\"time-micros\"}"));
            GenericRecord r = converter.convert("{\"t\":\"10:30:00.123456\"}", s);
            long micros = (long) r.get("t");
            assertThat(micros).isEqualTo(LocalTime.of(10, 30, 0, 123456000).toNanoOfDay() / 1_000L);
        }

        @Test
        void timestamp_millis_from_iso_string() {
            Schema s = record("TS",
                    field("ts", "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}"));
            GenericRecord r = converter.convert("{\"ts\":\"2025-06-15T10:30:00Z\"}", s);
            long millis = (long) r.get("ts");
            assertThat(millis).isEqualTo(Instant.parse("2025-06-15T10:30:00Z").toEpochMilli());
        }

        @Test
        void timestamp_millis_from_long() {
            Schema s = record("TS",
                    field("ts", "{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}"));
            long expected = Instant.parse("2025-06-15T10:30:00Z").toEpochMilli();
            GenericRecord r = converter.convert("{\"ts\":" + expected + "}", s);
            assertThat(r.get("ts")).isEqualTo(expected);
        }

        @Test
        void local_timestamp_millis_from_iso_string() {
            Schema s = record("LTS",
                    field("lts", "{\"type\":\"long\",\"logicalType\":\"local-timestamp-millis\"}"));
            GenericRecord r = converter.convert("{\"lts\":\"2025-06-15T10:30:00\"}", s);
            long millis = (long) r.get("lts");
            long expected = LocalDateTime.of(2025, 6, 15, 10, 30, 0)
                    .toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            assertThat(millis).isEqualTo(expected);
        }

        @Test
        void decimal_from_number() {
            Schema s = record("Dec",
                    field("price",
                            "{\"type\":\"bytes\",\"logicalType\":\"decimal\",\"precision\":10,\"scale\":2}"));
            GenericRecord r = converter.convert("{\"price\":99.99}", s);
            ByteBuffer bb = (ByteBuffer) r.get("price");
            Schema fieldSchema = s.getField("price").schema();
            LogicalTypes.Decimal dec = (LogicalTypes.Decimal) fieldSchema.getLogicalType();
            BigDecimal value = new Conversions.DecimalConversion().fromBytes(bb.duplicate(), fieldSchema, dec);
            assertThat(value).isEqualByComparingTo(new BigDecimal("99.99"));
        }

        @Test
        void decimal_from_string() {
            Schema s = record("Dec",
                    field("price",
                            "{\"type\":\"bytes\",\"logicalType\":\"decimal\",\"precision\":10,\"scale\":2}"));
            GenericRecord r = converter.convert("{\"price\":\"123.45\"}", s);
            ByteBuffer bb = (ByteBuffer) r.get("price");
            Schema fieldSchema = s.getField("price").schema();
            LogicalTypes.Decimal dec = (LogicalTypes.Decimal) fieldSchema.getLogicalType();
            BigDecimal value = new Conversions.DecimalConversion().fromBytes(bb.duplicate(), fieldSchema, dec);
            assertThat(value).isEqualByComparingTo(new BigDecimal("123.45"));
        }

        @Test
        void decimal_precision_exceeded_throws() {
            Schema s = record("Dec",
                    field("price",
                            "{\"type\":\"bytes\",\"logicalType\":\"decimal\",\"precision\":5,\"scale\":2}"));
            // 99999.99 has 7 significant digits â€” exceeds precision 5
            assertThatThrownBy(() -> converter.convert("{\"price\":\"99999.99\"}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("precision");
        }

        @Test
        void uuid_valid_string() {
            Schema s = record("U",
                    field("id",
                            "{\"type\":\"string\",\"logicalType\":\"uuid\"}"));
            GenericRecord r = converter.convert(
                    "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\"}", s);
            assertThat(r.get("id").toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        }

        @Test
        void uuid_invalid_string_throws() {
            Schema s = record("U",
                    field("id",
                            "{\"type\":\"string\",\"logicalType\":\"uuid\"}"));
            assertThatThrownBy(() -> converter.convert("{\"id\":\"not-a-uuid\"}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("UUID");
        }
    }

    // ---------------------------------------------------------------- convertAll

    @Nested
    class ConvertAll {

        @Test
        void json_array_produces_multiple_records() {
            Schema s = record("R", field("v", "\"int\""));
            List<GenericRecord> records =
                    converter.convertAll("[{\"v\":1},{\"v\":2},{\"v\":3}]", s);
            assertThat(records).hasSize(3);
            assertThat(records.get(0).get("v")).isEqualTo(1);
            assertThat(records.get(2).get("v")).isEqualTo(3);
        }

        @Test
        void single_object_returns_singleton_list() {
            Schema s = record("R", field("v", "\"int\""));
            List<GenericRecord> records = converter.convertAll("{\"v\":42}", s);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).get("v")).isEqualTo(42);
        }

        @Test
        void empty_array_returns_empty_list() {
            Schema s = record("R", field("v", "\"int\"", "0"));
            List<GenericRecord> records = converter.convertAll("[]", s);
            assertThat(records).isEmpty();
        }
    }

    // ---------------------------------------------------------------- error cases

    @Nested
    class ErrorCases {

        @Test
        void invalid_json_throws() {
            Schema s = record("R", field("v", "\"int\""));
            assertThatThrownBy(() -> converter.convert("{not valid json}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("JSON");
        }

        @Test
        void root_must_be_record_schema() {
            Schema s = Schema.create(Schema.Type.STRING);
            assertThatThrownBy(() -> converter.convert("{}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .hasMessageContaining("RECORD");
        }

        @Test
        void error_includes_path() {
            Schema s = record("R",
                    field("outer",
                            "{\"type\":\"record\",\"name\":\"Inner\",\"namespace\":\"test\",\"fields\":[" +
                                    "{\"name\":\"val\",\"type\":\"boolean\"}]}"));
            assertThatThrownBy(() -> converter.convert("{\"outer\":{\"val\":42}}", s))
                    .isInstanceOf(JsonConversionException.class)
                    .satisfies(e -> {
                        JsonConversionException jce = (JsonConversionException) e;
                        assertThat(jce.path()).contains("outer").contains("val");
                    });
        }
    }
}


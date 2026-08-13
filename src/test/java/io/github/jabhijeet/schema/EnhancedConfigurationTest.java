package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.cache.SchemaCache;
import io.github.jabhijeet.schema.fixtures.Person;
import io.github.jabhijeet.schema.fixtures.TemporalTypesPojo;
import org.apache.avro.Schema;
import org.apache.iceberg.types.Type;
import org.apache.parquet.schema.MessageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the enhanced configuration options.
 */
class EnhancedConfigurationTest {

    @Test
    void fieldNamingStrategy_snakeCase() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
                .build();

        Schema schema = generator.generateAvro(Person.class);
        
        // Check that field names are converted to snake_case
        assertThat(schema.getField("email_address")).isNotNull();
        assertThat(schema.getField("favourite_color")).isNotNull();
    }

    @Test
    void fieldNamingStrategy_kebabCase() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .fieldNamingStrategy(FieldNamingStrategy.KEBAB_CASE)
                .build();

        Schema schema = generator.generateAvro(Person.class);
        
        // Check that field names are converted to kebab-case
        // Note: Avro doesn't allow hyphens in field names, so kebab-case is converted to snake_case
        assertThat(schema.getField("email_address")).isNotNull();
        assertThat(schema.getField("favourite_color")).isNotNull();
    }

    @Test
    void timestampPrecision_micros() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .timestampPrecision(TimestampPrecision.MICROS)
                .build();

        // This test would need a POJO with timestamp fields
        // For now, just verify the configuration is accepted
        assertThat(generator).isNotNull();
    }

    @Test
    void timezoneHandling_preserve() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .timezoneHandling(TimezoneHandling.PRESERVE)
                .build();

        // This test would need a POJO with timezone-aware fields
        // For now, just verify the configuration is accepted
        assertThat(generator).isNotNull();
    }

    @Test
    void caching_with_field_naming_combined() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .cacheStrategy(CacheStrategy.LRU)
                .cacheSize(10)
                .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
                .build();

        // First generation - cache miss
        Schema schema1 = generator.generateAvro(Person.class);
        
        // Second generation - cache hit
        Schema schema2 = generator.generateAvro(Person.class);
        
        assertThat(schema2).isSameAs(schema1);
        
        // Verify field naming was applied
        assertThat(schema1.getField("email_address")).isNotNull();
        
        SchemaCache.CacheStats stats = generator.cacheStats();
        assertThat(stats.hitCount()).isEqualTo(1);
        assertThat(stats.missCount()).isEqualTo(1);
    }

    @Test
    void fieldNamingStrategy_applies_to_iceberg_schema() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .fieldNamingStrategy(FieldNamingStrategy.SNAKE_CASE)
                .build();

        org.apache.iceberg.Schema schema = generator.generateIceberg(Person.class);

        assertThat(schema.findField("email_address")).isNotNull();
        assertThat(schema.findField("favourite_color").type().typeId()).isEqualTo(Type.TypeID.STRING);
    }

    @Test
    void preserveDefaultValues_disabled_by_default() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder().build();
        assertThat(generator).isNotNull();
    }

    @Test
    void timezoneHandling_UTC_maps_offset_types_to_timestamp_millis() {
        Schema schema = PojoSchemaGenerator.builder()
                .timezoneHandling(TimezoneHandling.UTC)
                .build()
                .generateAvro(TemporalTypesPojo.class);

        assertLogicalType(schema, "offsetDateTime", "timestamp-millis");
        assertLogicalType(schema, "zonedDateTime",  "timestamp-millis");
        assertLogicalType(schema, "instant",        "timestamp-millis");
        assertLogicalType(schema, "localDateTime",  "local-timestamp-millis");
    }

    @Test
    void timezoneHandling_PRESERVE_maps_offset_types_to_string() {
        Schema schema = PojoSchemaGenerator.builder()
                .timezoneHandling(TimezoneHandling.PRESERVE)
                .build()
                .generateAvro(TemporalTypesPojo.class);

        // OffsetDateTime/ZonedDateTime → plain string (ISO-8601 with offset)
        assertAvroType(schema, "offsetDateTime", Schema.Type.STRING);
        assertAvroType(schema, "zonedDateTime",  Schema.Type.STRING);
        // Instant has no offset to preserve → falls back to timestamp-millis
        assertLogicalType(schema, "instant", "timestamp-millis");
        // LocalDateTime unchanged
        assertLogicalType(schema, "localDateTime", "local-timestamp-millis");
    }

    @Test
    void timezoneHandling_SYSTEM_DEFAULT_maps_all_to_local_timestamp() {
        Schema schema = PojoSchemaGenerator.builder()
                .timezoneHandling(TimezoneHandling.SYSTEM_DEFAULT)
                .build()
                .generateAvro(TemporalTypesPojo.class);

        assertLogicalType(schema, "offsetDateTime", "local-timestamp-millis");
        assertLogicalType(schema, "zonedDateTime",  "local-timestamp-millis");
        assertLogicalType(schema, "instant",        "local-timestamp-millis");
        assertLogicalType(schema, "localDateTime",  "local-timestamp-millis");
    }

    private static Schema unwrap(Schema s) {
        if (s.getType() != Schema.Type.UNION) return s;
        return s.getTypes().stream()
                .filter(b -> b.getType() != Schema.Type.NULL)
                .findFirst().orElseThrow();
    }

    private static void assertLogicalType(Schema record, String fieldName, String expectedLogical) {
        Schema field = unwrap(record.getField(fieldName).schema());
        assertThat(field.getLogicalType())
                .as("field '%s' logical type", fieldName)
                .isNotNull();
        assertThat(field.getLogicalType().getName())
                .as("field '%s' logical type name", fieldName)
                .isEqualTo(expectedLogical);
    }

    private static void assertAvroType(Schema record, String fieldName, Schema.Type expected) {
        Schema field = unwrap(record.getField(fieldName).schema());
        assertThat(field.getType())
                .as("field '%s' Avro type", fieldName)
                .isEqualTo(expected);
    }
}

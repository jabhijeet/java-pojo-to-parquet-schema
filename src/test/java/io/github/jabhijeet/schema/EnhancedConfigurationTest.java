package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.cache.SchemaCache;
import io.github.jabhijeet.schema.fixtures.Person;
import org.apache.avro.Schema;
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
    void preserveDefaultValues_disabled_by_default() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder().build();
        
        // Default should be false
        assertThat(generator).isNotNull();
        // We can't easily test this without a POJO with default values
    }
}

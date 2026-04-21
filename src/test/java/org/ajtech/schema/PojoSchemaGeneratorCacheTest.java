package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.cache.LruSchemaCache;
import io.github.jabhijeet.schema.cache.SchemaCache;
import io.github.jabhijeet.schema.fixtures.Address;
import io.github.jabhijeet.schema.fixtures.Employee;
import io.github.jabhijeet.schema.fixtures.Person;
import org.apache.avro.Schema;
import org.apache.parquet.schema.MessageType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PojoSchemaGeneratorCacheTest {

    @Test
    void caching_disabled_by_default() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder().build();
        assertThat(generator.cacheStats()).isNull();
    }

    @Test
    void lru_cache_hit_and_miss_behavior() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .cacheStrategy(CacheStrategy.LRU)
                .cacheSize(2)
                .build();

        // First generation - cache miss
        Schema schema1 = generator.generateAvro(Person.class);
        SchemaCache.CacheStats stats = generator.cacheStats();
        assertThat(stats.hitCount()).isEqualTo(0);
        assertThat(stats.missCount()).isEqualTo(1);

        // Second generation - cache hit
        Schema schema2 = generator.generateAvro(Person.class);
        stats = generator.cacheStats();
        assertThat(stats.hitCount()).isEqualTo(1);
        assertThat(stats.missCount()).isEqualTo(1);
        assertThat(schema2).isSameAs(schema1);

        // Different class - cache miss
        Schema schema3 = generator.generateAvro(Address.class);
        stats = generator.cacheStats();
        assertThat(stats.missCount()).isEqualTo(2);

        // Third class triggers LRU eviction (cache size is 2)
        Schema schema4 = generator.generateAvro(Employee.class);
        stats = generator.cacheStats();
        assertThat(stats.missCount()).isEqualTo(3);

        // Person was evicted when Employee was added (cache size is 2, Person was LRU)
        Schema schema5 = generator.generateAvro(Person.class);
        stats = generator.cacheStats();
        assertThat(stats.hitCount()).isEqualTo(1); // Only one hit from step 2
        assertThat(stats.missCount()).isEqualTo(4); // Person, Address, Employee, Person again
    }

    @Test
    void cache_works_for_both_avro_and_parquet() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .cacheStrategy(CacheStrategy.LRU)
                .cacheSize(10)
                .build();

        // Generate Avro schema
        Schema avro1 = generator.generateAvro(Person.class);
        Schema avro2 = generator.generateAvro(Person.class);
        assertThat(avro2).isSameAs(avro1);

        // Generate Parquet schema
        MessageType parquet1 = generator.generateParquet(Person.class);
        MessageType parquet2 = generator.generateParquet(Person.class);
        assertThat(parquet2).isSameAs(parquet1);

        SchemaCache.CacheStats stats = generator.cacheStats();
        assertThat(stats.hitCount()).isEqualTo(2); // One for Avro, one for Parquet
        assertThat(stats.missCount()).isEqualTo(2); // First generation of each
    }

    @Test
    void cache_clear_resets_statistics() {
        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .cacheStrategy(CacheStrategy.LRU)
                .build();

        generator.generateAvro(Person.class);
        generator.generateAvro(Person.class); // hit

        SchemaCache.CacheStats stats = generator.cacheStats();
        assertThat(stats.hitCount()).isEqualTo(1);

        generator.clearCache();
        
        // After clear, cache should be empty and stats reset
        generator.generateAvro(Person.class); // miss again
        stats = generator.cacheStats();
        assertThat(stats.hitCount()).isEqualTo(0);
        assertThat(stats.missCount()).isEqualTo(1); // Stats were reset by clear()
    }

    @Test
    void custom_cache_implementation() {
        SchemaCache customCache = new SchemaCache() {
            private Schema lastAvroSchema;
            private MessageType lastParquetSchema;
            
            @Override
            public Schema getAvroSchema(Class<?> pojoClass, SchemaOptions options) {
                return lastAvroSchema;
            }
            
            @Override
            public void putAvroSchema(Class<?> pojoClass, SchemaOptions options, Schema schema) {
                lastAvroSchema = schema;
            }
            
            @Override
            public MessageType getParquetSchema(Class<?> pojoClass, SchemaOptions options) {
                return lastParquetSchema;
            }
            
            @Override
            public void putParquetSchema(Class<?> pojoClass, SchemaOptions options, MessageType schema) {
                lastParquetSchema = schema;
            }
            
            @Override
            public void clear() {
                lastAvroSchema = null;
                lastParquetSchema = null;
            }
            
            @Override
            public int size() {
                return (lastAvroSchema != null ? 1 : 0) + (lastParquetSchema != null ? 1 : 0);
            }
            
            @Override
            public int maxSize() {
                return 10; // Arbitrary value
            }
        };

        PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .customCache(customCache)
                .build();

        Schema schema1 = generator.generateAvro(Person.class);
        Schema schema2 = generator.generateAvro(Person.class);
        assertThat(schema2).isSameAs(schema1);
    }

    @Test
    void thread_safe_cache_under_concurrent_load() throws InterruptedException {
        final int threadCount = 10;
        final int iterations = 100;
        final PojoSchemaGenerator generator = PojoSchemaGenerator.builder()
                .cacheStrategy(CacheStrategy.LRU)
                .cacheSize(100)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final Class<?> pojoClass = (i % 2 == 0) ? Person.class : Address.class;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        generator.generateAvro(pojoClass);
                        generator.generateParquet(pojoClass);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertThat(errors.get()).isEqualTo(0);
        
        // Verify cache statistics
        SchemaCache.CacheStats stats = generator.cacheStats();
        assertThat(stats.hitCount()).isGreaterThan(0);
        // Total operations = threadCount * iterations * 2 (Avro + Parquet)
        int totalOperations = threadCount * iterations * 2;
        assertThat(stats.hitCount() + stats.missCount()).isEqualTo(totalOperations);
    }
}

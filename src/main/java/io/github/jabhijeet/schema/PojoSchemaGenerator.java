package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.avro.AvroSchemaBuilder;
import io.github.jabhijeet.schema.cache.SchemaCache;
import io.github.jabhijeet.schema.parquet.ParquetSchemaBuilder;
import org.apache.avro.Schema;
import org.apache.parquet.schema.MessageType;

/**
 * Entry point for converting Java POJOs into Avro and Parquet schemas.
 *
 * <p>Each call creates fresh per-invocation state, so a single generator is safe
 * to share across threads once constructed.
 *
 * <pre>{@code
 * // Quickest form
 * Schema avro = PojoSchemaGenerator.toAvro(Person.class);
 * MessageType parquet = PojoSchemaGenerator.toParquet(Person.class);
 *
 * // Configured with caching
 * PojoSchemaGenerator gen = PojoSchemaGenerator.builder()
 *         .namespace("com.example.schemas")
 *         .nullableByDefault(false)
 *         .cacheStrategy(CacheStrategy.LRU)
 *         .cacheSize(200)
 *         .build();
 * Schema schema = gen.generateAvro(Person.class);
 * }</pre>
 */
public final class PojoSchemaGenerator {

    private final SchemaOptions options;
    private final SchemaCache cache;

    public PojoSchemaGenerator(SchemaOptions options) {
        if (options == null) throw new IllegalArgumentException("options must not be null");
        this.options = options;
        this.cache = options.effectiveCache();
    }

    public PojoSchemaGenerator() {
        this(SchemaOptions.defaults());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Schema generateAvro(Class<?> pojoClass) {
        if (pojoClass == null) throw new IllegalArgumentException("pojoClass must not be null");
        if (cache != null) {
            Schema cached = cache.getAvroSchema(pojoClass, options);
            if (cached != null) return cached;
        }
        Schema schema = new AvroSchemaBuilder(options).build(pojoClass);
        if (cache != null) {
            cache.putAvroSchema(pojoClass, options, schema);
        }
        return schema;
    }

    public MessageType generateParquet(Class<?> pojoClass) {
        if (pojoClass == null) throw new IllegalArgumentException("pojoClass must not be null");
        if (cache != null) {
            MessageType cached = cache.getParquetSchema(pojoClass, options);
            if (cached != null) return cached;
        }
        MessageType schema = new ParquetSchemaBuilder(options).build(pojoClass);
        if (cache != null) {
            cache.putParquetSchema(pojoClass, options, schema);
        }
        return schema;
    }

    public String generateAvroJson(Class<?> pojoClass, boolean pretty) {
        return generateAvro(pojoClass).toString(pretty);
    }

    public String generateParquetString(Class<?> pojoClass) {
        return generateParquet(pojoClass).toString();
    }

    public static Schema toAvro(Class<?> pojoClass) {
        return new PojoSchemaGenerator().generateAvro(pojoClass);
    }

    public static MessageType toParquet(Class<?> pojoClass) {
        return new PojoSchemaGenerator().generateParquet(pojoClass);
    }

    /**
     * Clears the cache if caching is enabled.
     */
    public void clearCache() {
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Returns cache statistics if caching is enabled, or {@code null} otherwise.
     */
    public SchemaCache.CacheStats cacheStats() {
        return cache != null ? cache.stats() : null;
    }

    public static final class Builder {
        private final SchemaOptions.Builder inner = SchemaOptions.builder();

        public Builder namespace(String namespace) {
            inner.namespace(namespace);
            return this;
        }

        public Builder nullableByDefault(boolean nullable) {
            inner.nullableByDefault(nullable);
            return this;
        }

        public Builder defaultDecimal(int precision, int scale) {
            inner.defaultDecimal(precision, scale);
            return this;
        }

        /**
         * Enables caching with the specified strategy.
         *
         * @param strategy the caching strategy
         * @return this builder
         */
        public Builder cacheStrategy(CacheStrategy strategy) {
            inner.cacheStrategy(strategy);
            return this;
        }

        /**
         * Sets the maximum cache size for LRU strategy.
         *
         * @param size the maximum number of entries to cache
         * @return this builder
         * @throws IllegalArgumentException if size is less than 1
         */
        public Builder cacheSize(int size) {
            inner.cacheSize(size);
            return this;
        }

        /**
         * Provides a custom cache implementation.
         *
         * @param cache the custom cache implementation
         * @return this builder
         */
        public Builder customCache(SchemaCache cache) {
            inner.customCache(cache);
            return this;
        }

        /**
         * Sets the field naming strategy for converting Java field names to schema field names.
         *
         * @param strategy the naming strategy
         * @return this builder
         */
        public Builder fieldNamingStrategy(FieldNamingStrategy strategy) {
            inner.fieldNamingStrategy(strategy);
            return this;
        }

        /**
         * Sets the timestamp precision for temporal fields.
         *
         * @param precision the timestamp precision
         * @return this builder
         */
        public Builder timestampPrecision(TimestampPrecision precision) {
            inner.timestampPrecision(precision);
            return this;
        }

        /**
         * Sets the timezone handling strategy for temporal fields.
         *
         * @param handling the timezone handling strategy
         * @return this builder
         */
        public Builder timezoneHandling(TimezoneHandling handling) {
            inner.timezoneHandling(handling);
            return this;
        }

        /**
         * Controls whether default values from Java fields should be preserved in the schema.
         * When enabled, field default values will be included in the generated schema.
         *
         * @param preserve true to preserve default values, false to ignore them
         * @return this builder
         */
        public Builder preserveDefaultValues(boolean preserve) {
            inner.preserveDefaultValues(preserve);
            return this;
        }

        /**
         * Enables flattening of nested POJO records into a single top-level record
         * whose leaf names are joined by {@link #flattenSeparator(String)} (default {@code "_"}).
         * Flattening stops at collection/map boundaries.
         */
        public Builder flattenNestedRecords(boolean flatten) {
            inner.flattenNestedRecords(flatten);
            return this;
        }

        /**
         * Separator used for flattened field names. Must match {@code ^[A-Za-z0-9_]+$}.
         */
        public Builder flattenSeparator(String separator) {
            inner.flattenSeparator(separator);
            return this;
        }

        /**
         * Selects how duplicate flattened field names are resolved.
         */
        public Builder flattenCollisionStrategy(FlattenCollisionStrategy strategy) {
            inner.flattenCollisionStrategy(strategy);
            return this;
        }

        public PojoSchemaGenerator build() {
            return new PojoSchemaGenerator(inner.build());
        }
    }
}


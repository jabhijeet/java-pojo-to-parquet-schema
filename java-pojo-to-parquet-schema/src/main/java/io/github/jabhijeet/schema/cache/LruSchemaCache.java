package io.github.jabhijeet.schema.cache;

import io.github.jabhijeet.schema.SchemaOptions;
import org.apache.avro.Schema;
import org.apache.parquet.schema.MessageType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe LRU (Least Recently Used) cache implementation for schemas.
 * <p>
 * This cache uses {@link LinkedHashMap} with access-order mode and a fixed maximum size.
 * When the cache exceeds its maximum size, the least recently used entries are evicted.
 * </p>
 *
 * @since 1.1.0
 */
public final class LruSchemaCache implements SchemaCache {

    private static final class CacheKey {
        private final Class<?> pojoClass;
        private final SchemaOptions options;
        private final int hashCode;

        CacheKey(Class<?> pojoClass, SchemaOptions options) {
            this.pojoClass = Objects.requireNonNull(pojoClass, "pojoClass must not be null");
            this.options = Objects.requireNonNull(options, "options must not be null");
            this.hashCode = Objects.hash(pojoClass, options);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return pojoClass.equals(cacheKey.pojoClass) && options.equals(cacheKey.options);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return "CacheKey{" +
                    "pojoClass=" + pojoClass.getName() +
                    ", optionsHash=" + options.hashCode() +
                    '}';
        }
    }

    private static final class CacheEntry {
        final Schema avroSchema;
        final MessageType parquetSchema;
        final org.apache.iceberg.Schema icebergSchema;

        CacheEntry(Schema avroSchema, MessageType parquetSchema, org.apache.iceberg.Schema icebergSchema) {
            this.avroSchema = avroSchema;
            this.parquetSchema = parquetSchema;
            this.icebergSchema = icebergSchema;
        }
    }

    private final Map<CacheKey, CacheEntry> cache;
    private final int maxSize;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    /**
     * Creates an LRU cache with the specified maximum size.
     *
     * @param maxSize the maximum number of entries the cache can hold
     * @throws IllegalArgumentException if maxSize is less than 1
     */
    public LruSchemaCache(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be at least 1");
        }
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<CacheKey, CacheEntry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, CacheEntry> eldest) {
                return size() > LruSchemaCache.this.maxSize;
            }
        };
    }

    /**
     * Creates an LRU cache with a default maximum size of 100.
     */
    public LruSchemaCache() {
        this(100);
    }

    @Override
    public synchronized Schema getAvroSchema(Class<?> pojoClass, SchemaOptions options) {
        CacheKey key = new CacheKey(pojoClass, options);
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.avroSchema != null) {
            hitCount.incrementAndGet();
            return entry.avroSchema;
        }
        missCount.incrementAndGet();
        return null;
    }

    @Override
    public synchronized void putAvroSchema(Class<?> pojoClass, SchemaOptions options, Schema schema) {
        CacheKey key = new CacheKey(pojoClass, options);
        CacheEntry existing = cache.get(key);
        if (existing != null) {
            // Update existing entry with new Avro schema, preserving other schema types.
            cache.put(key, new CacheEntry(schema, existing.parquetSchema, existing.icebergSchema));
        } else {
            cache.put(key, new CacheEntry(schema, null, null));
        }
    }

    @Override
    public synchronized MessageType getParquetSchema(Class<?> pojoClass, SchemaOptions options) {
        CacheKey key = new CacheKey(pojoClass, options);
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.parquetSchema != null) {
            hitCount.incrementAndGet();
            return entry.parquetSchema;
        }
        missCount.incrementAndGet();
        return null;
    }

    @Override
    public synchronized void putParquetSchema(Class<?> pojoClass, SchemaOptions options, MessageType schema) {
        CacheKey key = new CacheKey(pojoClass, options);
        CacheEntry existing = cache.get(key);
        if (existing != null) {
            // Update existing entry with new Parquet schema, preserving other schema types.
            cache.put(key, new CacheEntry(existing.avroSchema, schema, existing.icebergSchema));
        } else {
            cache.put(key, new CacheEntry(null, schema, null));
        }
    }

    @Override
    public synchronized org.apache.iceberg.Schema getIcebergSchema(Class<?> pojoClass, SchemaOptions options) {
        CacheKey key = new CacheKey(pojoClass, options);
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.icebergSchema != null) {
            hitCount.incrementAndGet();
            return entry.icebergSchema;
        }
        missCount.incrementAndGet();
        return null;
    }

    @Override
    public synchronized void putIcebergSchema(Class<?> pojoClass,
                                              SchemaOptions options,
                                              org.apache.iceberg.Schema schema) {
        CacheKey key = new CacheKey(pojoClass, options);
        CacheEntry existing = cache.get(key);
        if (existing != null) {
            cache.put(key, new CacheEntry(existing.avroSchema, existing.parquetSchema, schema));
        } else {
            cache.put(key, new CacheEntry(null, null, schema));
        }
    }

    @Override
    public synchronized void clear() {
        cache.clear();
        hitCount.set(0);
        missCount.set(0);
    }

    @Override
    public synchronized int size() {
        return cache.size();
    }

    @Override
    public int maxSize() {
        return maxSize;
    }

    @Override
    public synchronized CacheStats stats() {
        return new CacheStats(cache.size(), maxSize, hitCount.get(), missCount.get());
    }

    /**
     * Returns the cache hit ratio.
     *
     * @return the ratio of cache hits to total requests
     */
    public double hitRatio() {
        CacheStats stats = stats();
        return stats.hitRatio();
    }
}

package io.github.jabhijeet.schema.cache;

import io.github.jabhijeet.schema.SchemaOptions;
import org.apache.avro.Schema;
import org.apache.parquet.schema.MessageType;

/**
 * Cache interface for storing and retrieving generated schemas.
 * <p>
 * Implementations should be thread-safe if the cache is shared across threads.
 * The cache key is based on the POJO class and {@link SchemaOptions} configuration.
 * </p>
 *
 * @since 1.1.0
 */
public interface SchemaCache {

    /**
     * Retrieves an Avro schema from the cache, or returns {@code null} if not present.
     *
     * @param pojoClass the POJO class
     * @param options   the schema generation options
     * @return the cached Avro schema, or {@code null} if not cached
     */
    Schema getAvroSchema(Class<?> pojoClass, SchemaOptions options);

    /**
     * Stores an Avro schema in the cache.
     *
     * @param pojoClass the POJO class
     * @param options   the schema generation options
     * @param schema    the Avro schema to cache
     */
    void putAvroSchema(Class<?> pojoClass, SchemaOptions options, Schema schema);

    /**
     * Retrieves a Parquet schema from the cache, or returns {@code null} if not present.
     *
     * @param pojoClass the POJO class
     * @param options   the schema generation options
     * @return the cached Parquet schema, or {@code null} if not cached
     */
    MessageType getParquetSchema(Class<?> pojoClass, SchemaOptions options);

    /**
     * Stores a Parquet schema in the cache.
     *
     * @param pojoClass the POJO class
     * @param options   the schema generation options
     * @param schema    the Parquet schema to cache
     */
    void putParquetSchema(Class<?> pojoClass, SchemaOptions options, MessageType schema);

    /**
     * Retrieves an Iceberg schema from the cache, or returns {@code null} if not present.
     *
     * @param pojoClass the POJO class
     * @param options   the schema generation options
     * @return the cached Iceberg schema, or {@code null} if not cached
     */
    org.apache.iceberg.Schema getIcebergSchema(Class<?> pojoClass, SchemaOptions options);

    /**
     * Stores an Iceberg schema in the cache.
     *
     * @param pojoClass the POJO class
     * @param options   the schema generation options
     * @param schema    the Iceberg schema to cache
     */
    void putIcebergSchema(Class<?> pojoClass, SchemaOptions options, org.apache.iceberg.Schema schema);

    /**
     * Removes all entries from the cache.
     */
    void clear();

    /**
     * Returns the number of entries currently in the cache.
     *
     * @return the cache size
     */
    int size();

    /**
     * Returns the maximum number of entries the cache can hold.
     *
     * @return the maximum cache size
     */
    int maxSize();

    /**
     * Returns statistics about cache usage.
     *
     * @return cache statistics
     */
    default CacheStats stats() {
        return new CacheStats(size(), maxSize(), 0, 0); // Default implementation
    }

    /**
     * Simple record for cache statistics.
     */
    record CacheStats(int size, int maxSize, long hitCount, long missCount) {
        
        /**
         * Returns the cache hit ratio (hitCount / (hitCount + missCount)).
         * Returns 0.0 if no requests have been made.
         */
        public double hitRatio() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }
    }
}

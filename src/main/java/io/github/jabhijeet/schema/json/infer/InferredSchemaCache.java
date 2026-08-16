package io.github.jabhijeet.schema.json.infer;

import org.apache.avro.Schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class InferredSchemaCache {

    private static final class CacheKey {
        private final String schemaJson;
        private final int hashCode;

        CacheKey(String schemaJson) {
            this.schemaJson = Objects.requireNonNull(schemaJson, "schemaJson must not be null");
            this.hashCode = schemaJson.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return schemaJson.equals(cacheKey.schemaJson);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private final Map<CacheKey, Schema> cache;
    private final int maxSize;

    public InferredSchemaCache(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be at least 1");
        }
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<CacheKey, Schema>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, Schema> eldest) {
                return size() > InferredSchemaCache.this.maxSize;
            }
        };
    }

    public InferredSchemaCache() {
        this(200);
    }

    public synchronized Schema get(String schemaJson) {
        CacheKey key = new CacheKey(schemaJson);
        return cache.get(key);
    }

    public synchronized void put(String schemaJson, Schema schema) {
        CacheKey key = new CacheKey(schemaJson);
        cache.put(key, schema);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }

    public int maxSize() {
        return maxSize;
    }
}

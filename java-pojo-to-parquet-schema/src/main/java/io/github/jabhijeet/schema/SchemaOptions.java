package io.github.jabhijeet.schema;

import io.github.jabhijeet.schema.cache.LruSchemaCache;
import io.github.jabhijeet.schema.cache.SchemaCache;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable configuration for {@link PojoSchemaGenerator}.
 *
 * <p>Instances are created through {@link #builder()} or obtained as {@link #defaults()}.
 */
public final class SchemaOptions {

    static final Pattern FLATTEN_SEPARATOR_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private final String namespaceOverride;
    private final boolean nullableByDefault;
    private final int defaultDecimalPrecision;
    private final int defaultDecimalScale;
    private final CacheStrategy cacheStrategy;
    private final int cacheSize;
    private final SchemaCache customCache;
    private final FieldNamingStrategy fieldNamingStrategy;
    private final TimestampPrecision timestampPrecision;
    private final TimezoneHandling timezoneHandling;
    private final boolean preserveDefaultValues;
    private final boolean flattenNestedRecords;
    private final String flattenSeparator;
    private final FlattenCollisionStrategy flattenCollisionStrategy;

    private SchemaOptions(Builder b) {
        this.namespaceOverride = b.namespaceOverride;
        this.nullableByDefault = b.nullableByDefault;
        this.defaultDecimalPrecision = b.defaultDecimalPrecision;
        this.defaultDecimalScale = b.defaultDecimalScale;
        this.cacheStrategy = b.cacheStrategy;
        this.cacheSize = b.cacheSize;
        this.customCache = b.customCache;
        this.fieldNamingStrategy = b.fieldNamingStrategy;
        this.timestampPrecision = b.timestampPrecision;
        this.timezoneHandling = b.timezoneHandling;
        this.preserveDefaultValues = b.preserveDefaultValues;
        this.flattenNestedRecords = b.flattenNestedRecords;
        this.flattenSeparator = b.flattenSeparator;
        this.flattenCollisionStrategy = b.flattenCollisionStrategy;
    }

    public static SchemaOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String namespaceOverride() {
        return namespaceOverride;
    }

    public boolean nullableByDefault() {
        return nullableByDefault;
    }

    public int defaultDecimalPrecision() {
        return defaultDecimalPrecision;
    }

    public int defaultDecimalScale() {
        return defaultDecimalScale;
    }

    public CacheStrategy cacheStrategy() {
        return cacheStrategy;
    }

    public int cacheSize() {
        return cacheSize;
    }

    public SchemaCache customCache() {
        return customCache;
    }

    public FieldNamingStrategy fieldNamingStrategy() {
        return fieldNamingStrategy;
    }

    public TimestampPrecision timestampPrecision() {
        return timestampPrecision;
    }

    public TimezoneHandling timezoneHandling() {
        return timezoneHandling;
    }

    public boolean preserveDefaultValues() {
        return preserveDefaultValues;
    }

    public boolean flattenNestedRecords() {
        return flattenNestedRecords;
    }

    public String flattenSeparator() {
        return flattenSeparator;
    }

    public FlattenCollisionStrategy flattenCollisionStrategy() {
        return flattenCollisionStrategy;
    }

    /**
     * Returns the effective cache instance based on configuration.
     * For internal use by {@link PojoSchemaGenerator}.
     */
    SchemaCache effectiveCache() {
        if (customCache != null) {
            return customCache;
        }
        if (cacheStrategy == CacheStrategy.NONE) {
            return null;
        }
        if (cacheStrategy == CacheStrategy.LRU) {
            return new LruSchemaCache(cacheSize);
        }
        // UNLIMITED strategy uses LRU with Integer.MAX_VALUE size
        return new LruSchemaCache(Integer.MAX_VALUE);
    }

    public static final class Builder {
        private String namespaceOverride;
        private boolean nullableByDefault = true;
        private int defaultDecimalPrecision = 38;
        private int defaultDecimalScale = 18;
        private CacheStrategy cacheStrategy = CacheStrategy.NONE;
        private int cacheSize = 100;
        private SchemaCache customCache;
        private FieldNamingStrategy fieldNamingStrategy = FieldNamingStrategy.AS_IS;
        private TimestampPrecision timestampPrecision = TimestampPrecision.MILLIS;
        private TimezoneHandling timezoneHandling = TimezoneHandling.UTC;
        private boolean preserveDefaultValues = false;
        private boolean flattenNestedRecords = false;
        private String flattenSeparator = "_";
        private FlattenCollisionStrategy flattenCollisionStrategy = FlattenCollisionStrategy.THROW;

        public Builder namespace(String namespace) {
            this.namespaceOverride = namespace;
            return this;
        }

        public Builder nullableByDefault(boolean nullable) {
            this.nullableByDefault = nullable;
            return this;
        }

        public Builder defaultDecimal(int precision, int scale) {
            if (precision <= 0) throw new IllegalArgumentException("precision must be positive");
            if (scale < 0 || scale > precision) {
                throw new IllegalArgumentException("scale must be in [0, precision]");
            }
            this.defaultDecimalPrecision = precision;
            this.defaultDecimalScale = scale;
            return this;
        }

        /**
         * Enables caching with the specified strategy.
         *
         * @param strategy the caching strategy
         * @return this builder
         */
        public Builder cacheStrategy(CacheStrategy strategy) {
            this.cacheStrategy = Objects.requireNonNull(strategy, "strategy must not be null");
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
            if (size < 1) {
                throw new IllegalArgumentException("cacheSize must be at least 1");
            }
            this.cacheSize = size;
            return this;
        }

        /**
         * Provides a custom cache implementation.
         * When set, {@link #cacheStrategy(CacheStrategy)} is ignored.
         *
         * @param cache the custom cache implementation
         * @return this builder
         */
        public Builder customCache(SchemaCache cache) {
            this.customCache = cache;
            return this;
        }

        /**
         * Sets the field naming strategy for converting Java field names to schema field names.
         *
         * @param strategy the naming strategy
         * @return this builder
         */
        public Builder fieldNamingStrategy(FieldNamingStrategy strategy) {
            this.fieldNamingStrategy = Objects.requireNonNull(strategy, "strategy must not be null");
            return this;
        }

        /**
         * Sets the timestamp precision for temporal fields.
         *
         * @param precision the timestamp precision
         * @return this builder
         */
        public Builder timestampPrecision(TimestampPrecision precision) {
            this.timestampPrecision = Objects.requireNonNull(precision, "precision must not be null");
            return this;
        }

        /**
         * Sets the timezone handling strategy for temporal fields.
         *
         * @param handling the timezone handling strategy
         * @return this builder
         */
        public Builder timezoneHandling(TimezoneHandling handling) {
            this.timezoneHandling = Objects.requireNonNull(handling, "handling must not be null");
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
            this.preserveDefaultValues = preserve;
            return this;
        }

        /**
         * When enabled, nested POJO records are collapsed into a single top-level record whose
         * leaf field names are joined by {@link #flattenSeparator(String)} (default {@code "_"}).
         * Flattening stops at collection and map boundaries: the element type of
         * {@code List<Pojo>} / {@code Map<String,Pojo>} remains a nested record.
         *
         * <p>Default: {@code false}.
         */
        public Builder flattenNestedRecords(boolean flatten) {
            this.flattenNestedRecords = flatten;
            return this;
        }

        /**
         * Separator used to join nested field names when
         * {@link #flattenNestedRecords(boolean) flattenNestedRecords} is enabled.
         * Must match {@code ^[A-Za-z0-9_]+$} so generated names remain Avro-legal.
         *
         * <p>Default: {@code "_"}.
         *
         * @throws IllegalArgumentException if the separator is null, empty, or contains characters
         *     outside {@code [A-Za-z0-9_]}.
         */
        public Builder flattenSeparator(String separator) {
            if (separator == null || separator.isEmpty()) {
                throw new IllegalArgumentException("flattenSeparator must be non-empty");
            }
            if (!FLATTEN_SEPARATOR_PATTERN.matcher(separator).matches()) {
                throw new IllegalArgumentException(
                        "flattenSeparator '" + separator + "' must match " + FLATTEN_SEPARATOR_PATTERN.pattern()
                                + " (Avro field names only allow letters, digits, and underscores)");
            }
            this.flattenSeparator = separator;
            return this;
        }

        /**
         * Controls how duplicate flattened field names are resolved. Default is
         * {@link FlattenCollisionStrategy#THROW}.
         */
        public Builder flattenCollisionStrategy(FlattenCollisionStrategy strategy) {
            this.flattenCollisionStrategy =
                    Objects.requireNonNull(strategy, "strategy must not be null");
            return this;
        }

        public SchemaOptions build() {
            return new SchemaOptions(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemaOptions other)) return false;
        return nullableByDefault == other.nullableByDefault
                && defaultDecimalPrecision == other.defaultDecimalPrecision
                && defaultDecimalScale == other.defaultDecimalScale
                && cacheSize == other.cacheSize
                && preserveDefaultValues == other.preserveDefaultValues
                && flattenNestedRecords == other.flattenNestedRecords
                && Objects.equals(namespaceOverride, other.namespaceOverride)
                && cacheStrategy == other.cacheStrategy
                && Objects.equals(customCache, other.customCache)
                && fieldNamingStrategy == other.fieldNamingStrategy
                && timestampPrecision == other.timestampPrecision
                && timezoneHandling == other.timezoneHandling
                && Objects.equals(flattenSeparator, other.flattenSeparator)
                && flattenCollisionStrategy == other.flattenCollisionStrategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespaceOverride, nullableByDefault, defaultDecimalPrecision,
                defaultDecimalScale, cacheStrategy, cacheSize, customCache,
                fieldNamingStrategy, timestampPrecision, timezoneHandling, preserveDefaultValues,
                flattenNestedRecords, flattenSeparator, flattenCollisionStrategy);
    }
}


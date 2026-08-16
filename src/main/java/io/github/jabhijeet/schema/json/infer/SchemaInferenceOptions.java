package io.github.jabhijeet.schema.json.infer;

import io.github.jabhijeet.schema.FieldNamingStrategy;
import io.github.jabhijeet.schema.TimestampPrecision;

import java.util.Objects;
import java.util.regex.Pattern;

public final class SchemaInferenceOptions {

    static final Pattern ROOT_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final String rootName;
    private final boolean nullableByDefault;
    private final int defaultDecimalPrecision;
    private final int defaultDecimalScale;
    private final TimestampPrecision timestampPrecision;
    private final FieldNamingStrategy fieldNamingStrategy;
    private final int maxDepth;
    private final int sampleSize;

    private SchemaInferenceOptions(Builder b) {
        this.rootName = b.rootName;
        this.nullableByDefault = b.nullableByDefault;
        this.defaultDecimalPrecision = b.defaultDecimalPrecision;
        this.defaultDecimalScale = b.defaultDecimalScale;
        this.timestampPrecision = b.timestampPrecision;
        this.fieldNamingStrategy = b.fieldNamingStrategy;
        this.maxDepth = b.maxDepth;
        this.sampleSize = b.sampleSize;
    }

    public static SchemaInferenceOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String rootName() {
        return rootName;
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

    public TimestampPrecision timestampPrecision() {
        return timestampPrecision;
    }

    public FieldNamingStrategy fieldNamingStrategy() {
        return fieldNamingStrategy;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int sampleSize() {
        return sampleSize;
    }

    public static final class Builder {
        private String rootName = "Record";
        private boolean nullableByDefault = true;
        private int defaultDecimalPrecision = 38;
        private int defaultDecimalScale = 18;
        private TimestampPrecision timestampPrecision = TimestampPrecision.MILLIS;
        private FieldNamingStrategy fieldNamingStrategy = FieldNamingStrategy.AS_IS;
        private int maxDepth = 32;
        private int sampleSize = 1;

        public Builder rootName(String rootName) {
            if (rootName == null || rootName.isEmpty()) {
                throw new IllegalArgumentException("rootName must not be empty");
            }
            if (!ROOT_NAME_PATTERN.matcher(rootName).matches()) {
                throw new IllegalArgumentException(
                        "rootName '" + rootName + "' must match " + ROOT_NAME_PATTERN.pattern());
            }
            this.rootName = rootName;
            return this;
        }

        public Builder nullableByDefault(boolean nullableByDefault) {
            this.nullableByDefault = nullableByDefault;
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

        public Builder timestampPrecision(TimestampPrecision timestampPrecision) {
            this.timestampPrecision = Objects.requireNonNull(timestampPrecision);
            return this;
        }

        public Builder fieldNamingStrategy(FieldNamingStrategy fieldNamingStrategy) {
            this.fieldNamingStrategy = Objects.requireNonNull(fieldNamingStrategy);
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be at least 1");
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder sampleSize(int sampleSize) {
            if (sampleSize < 1) throw new IllegalArgumentException("sampleSize must be at least 1");
            this.sampleSize = sampleSize;
            return this;
        }

        public SchemaInferenceOptions build() {
            return new SchemaInferenceOptions(this);
        }
    }
}

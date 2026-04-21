package org.ajtech.schema;

/**
 * Precision for timestamp fields in generated schemas.
 */
public enum TimestampPrecision {
    /**
     * Millisecond precision (compatible with Avro's timestamp-millis logical type).
     */
    MILLIS,
    
    /**
     * Microsecond precision (compatible with Avro's timestamp-micros logical type).
     */
    MICROS,
    
    /**
     * Nanosecond precision. Avro does not define a nanosecond timestamp logical
     * type, so the generator falls back to {@link #MICROS} for this setting.
     */
    NANOS
}
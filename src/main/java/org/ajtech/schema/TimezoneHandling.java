package org.ajtech.schema;

/**
 * Strategy for handling timezone information in temporal fields.
 *
 * <p>Avro's timestamp logical types are either UTC-normalized
 * ({@code timestamp-millis}/{@code timestamp-micros}) or zone-agnostic
 * ({@code local-timestamp-millis}/{@code local-timestamp-micros}). There is no
 * native way to record a timezone offset inside an Avro value, so this setting
 * is advisory: UTC-normalizing types ({@link java.time.Instant},
 * {@link java.time.OffsetDateTime}, {@link java.time.ZonedDateTime},
 * {@link java.util.Date}, {@link java.sql.Timestamp}) always map to the
 * UTC-normalized logical type regardless of this setting, and
 * {@link java.time.LocalDateTime} always maps to the local logical type.
 */
public enum TimezoneHandling {
    /**
     * Use UTC-normalized timestamp logical types (the most portable choice
     * and the default).
     */
    UTC,

    /**
     * Reserved for preserving offset information where the schema format
     * allows it. Treated identically to {@link #UTC} today because Avro has
     * no offset-preserving timestamp logical type.
     */
    PRESERVE,

    /**
     * Reserved for interpreting zone-agnostic values in the JVM's default
     * timezone. Treated identically to {@link #UTC} today.
     */
    SYSTEM_DEFAULT
}
package io.github.jabhijeet.schema;

/**
 * Strategy for handling timezone information in temporal fields.
 *
 * <p>Controls how {@link java.time.OffsetDateTime}, {@link java.time.ZonedDateTime},
 * {@link java.time.Instant}, {@link java.util.Date}, and {@link java.sql.Timestamp}
 * fields are mapped to Avro logical types. {@link java.time.LocalDateTime} is always
 * mapped to {@code local-timestamp-millis}/{@code local-timestamp-micros} regardless
 * of this setting — it carries no timezone information by definition.
 *
 * <table border="1">
 *   <caption>Java type → Avro logical type per strategy</caption>
 *   <tr><th>Java type</th><th>UTC (default)</th><th>PRESERVE</th><th>SYSTEM_DEFAULT</th></tr>
 *   <tr><td>OffsetDateTime, ZonedDateTime</td>
 *       <td>timestamp-millis/micros</td>
 *       <td>string (ISO-8601 with offset)</td>
 *       <td>local-timestamp-millis/micros</td></tr>
 *   <tr><td>Instant, Date, Timestamp</td>
 *       <td>timestamp-millis/micros</td>
 *       <td>timestamp-millis/micros (no offset to preserve)</td>
 *       <td>local-timestamp-millis/micros</td></tr>
 *   <tr><td>LocalDateTime</td>
 *       <td>local-timestamp-millis/micros</td>
 *       <td>local-timestamp-millis/micros</td>
 *       <td>local-timestamp-millis/micros</td></tr>
 * </table>
 */
public enum TimezoneHandling {
    /**
     * UTC-normalized timestamp logical types (the default). All timestamps are
     * stored as epoch millis/micros from UTC. Offset information in
     * {@link java.time.OffsetDateTime} and {@link java.time.ZonedDateTime} is
     * discarded.
     */
    UTC,

    /**
     * Preserve offset information for {@link java.time.OffsetDateTime} and
     * {@link java.time.ZonedDateTime} by storing them as Avro {@code string} in
     * ISO-8601 format (e.g. {@code "2025-01-02T03:04:05+05:30"}). UTC-only types
     * ({@link java.time.Instant}, {@link java.util.Date}, etc.) fall back to
     * {@code timestamp-millis}/{@code timestamp-micros} as there is no offset to
     * preserve.
     */
    PRESERVE,

    /**
     * Zone-agnostic storage. All UTC-normalizing types are mapped to
     * {@code local-timestamp-millis}/{@code local-timestamp-micros}, signalling
     * to consumers that values should be interpreted in a configured local timezone
     * rather than as absolute UTC instants. Useful in legacy ETL pipelines that
     * operate in a fixed system timezone.
     */
    SYSTEM_DEFAULT
}

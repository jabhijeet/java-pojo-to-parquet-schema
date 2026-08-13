package io.github.jabhijeet.schema;

/**
 * Strategy for converting Java field names to schema field names.
 */
public enum FieldNamingStrategy {
    /**
     * Keep the original Java field name as-is.
     */
    AS_IS,
    
    /**
     * Convert camelCase to snake_case (e.g., "firstName" â†’ "first_name").
     */
    SNAKE_CASE,
    
    /**
     * Convert camelCase to lower camelCase (no change for already camelCase).
     */
    LOWER_CAMEL_CASE,
    
    /**
     * Convert to UPPER_SNAKE_CASE (e.g., "firstName" â†’ "FIRST_NAME").
     */
    UPPER_SNAKE_CASE,
    
    /**
     * Convert to kebab-case word boundaries, but use underscores as separators
     * (e.g., {@code "firstName"} → {@code "first_name"}).
     * Avro field names do not permit hyphens, so this strategy produces the same
     * output as {@link #SNAKE_CASE}. It exists as a distinct value so that code
     * which semantically intends kebab-case is distinguishable from explicit
     * snake_case intent without changing any generated schema output.
     */
    KEBAB_CASE
}

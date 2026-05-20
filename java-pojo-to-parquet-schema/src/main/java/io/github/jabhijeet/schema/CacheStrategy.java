package io.github.jabhijeet.schema;

/**
 * Defines the caching strategy for schema generation.
 *
 * @since 1.1.0
 */
public enum CacheStrategy {

    /**
     * No caching - each schema generation request performs full reflection.
     * This is the default for backward compatibility.
     */
    NONE,

    /**
     * LRU (Least Recently Used) caching with a fixed maximum size.
     * Suitable for most applications with repeated schema generation.
     */
    LRU,

    /**
     * Unlimited caching - all generated schemas are cached indefinitely.
     * Use with caution in long-running applications with many distinct POJO classes.
     */
    UNLIMITED
}

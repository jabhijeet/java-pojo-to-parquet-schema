package io.github.jabhijeet.schema;

/**
 * Controls what happens when {@link SchemaOptions#flattenNestedRecords() flattening}
 * produces two leaf fields with the same final name.
 */
public enum FlattenCollisionStrategy {

    /**
     * Throw {@link SchemaGenerationException} with both source paths in the message.
     */
    THROW,

    /**
     * Suffix later duplicates with {@code __1}, {@code __2}, &hellip; in encounter order.
     */
    AUTO_RENAME
}

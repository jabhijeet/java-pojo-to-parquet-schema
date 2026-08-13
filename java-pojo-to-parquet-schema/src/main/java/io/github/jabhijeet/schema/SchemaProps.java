package io.github.jabhijeet.schema;

/**
 * Custom Avro {@link org.apache.avro.Schema} property names used to mark a schema
 * that was produced by {@link PojoSchemaGenerator} with
 * {@link SchemaOptions#flattenNestedRecords() flattenNestedRecords} enabled.
 *
 * <p>{@code JsonToAvroConverter} reads these properties off the top-level record
 * to decide whether to walk nested JSON into flat leaf fields.
 */
public final class SchemaProps {

    public static final String FLATTENED = "pojoSchemaFlattened";

    public static final String FLATTEN_SEPARATOR = "pojoSchemaFlattenSeparator";

    /**
     * Field-level property carrying the dotted original source path (e.g.
     * {@code "foo.bar"}) that a flat leaf was derived from. Set by the flattener
     * on every leaf field so the JSON converter can walk the original nested
     * shape even when the flat field name was auto-renamed because of a
     * collision or contains literal separator characters.
     */
    public static final String FLATTEN_SOURCE_PATH = "pojoSchemaFlattenSourcePath";

    private SchemaProps() {
    }
}

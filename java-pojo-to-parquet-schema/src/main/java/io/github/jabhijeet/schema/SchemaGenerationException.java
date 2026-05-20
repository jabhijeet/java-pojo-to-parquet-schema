package io.github.jabhijeet.schema;

/** Thrown when a POJO cannot be mapped to a schema. */
public final class SchemaGenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchemaGenerationException(String message) {
        super(message);
    }

    public SchemaGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}


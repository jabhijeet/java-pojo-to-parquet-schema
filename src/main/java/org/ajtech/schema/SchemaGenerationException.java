package org.ajtech.schema;

/** Thrown when a POJO cannot be mapped to a schema. */
public class SchemaGenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchemaGenerationException(String message) {
        super(message);
    }

    public SchemaGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

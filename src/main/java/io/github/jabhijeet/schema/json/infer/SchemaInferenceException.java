package io.github.jabhijeet.schema.json.infer;

public final class SchemaInferenceException extends RuntimeException {

    public SchemaInferenceException(String message) {
        super(message);
    }

    public SchemaInferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

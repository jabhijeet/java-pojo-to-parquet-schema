package org.ajtech.schema.json;

/**
 * Thrown when a JSON document cannot be converted to an Avro {@code GenericRecord}
 * against a specific schema.
 *
 * <p>The message always includes a JSON-pointer-style path (for example
 * {@code $.customer.addresses[2].zip}) so the offending field is obvious.
 */
public class JsonConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String path;

    public JsonConversionException(String path, String message) {
        super(formatMessage(path, message));
        this.path = path;
    }

    public JsonConversionException(String path, String message, Throwable cause) {
        super(formatMessage(path, message), cause);
        this.path = path;
    }

    /**
     * Returns the JSON path where the error was detected (e.g. {@code $.foo[0].bar}).
     */
    public String path() {
        return path;
    }

    private static String formatMessage(String path, String message) {
        return (path == null || path.isEmpty() ? "$" : path) + ": " + message;
    }
}

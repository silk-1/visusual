package silkvis.config.json;

/**
 * Raised when JSON text is malformed. Checked on purpose: callers that read files must
 * decide between "safe defaults" and "report the error", never crash the client.
 */
public class JsonException extends Exception {
    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}

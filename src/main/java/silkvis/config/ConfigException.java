package silkvis.config;

import silkvis.core.ErrorCode;

/**
 * Validation or load failure with a stable field path (CFG-02). The field path is what the
 * log and the diagnostics export show; user-facing text lives in lang files.
 */
public final class ConfigException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String fieldPath;

    public ConfigException(String fieldPath, String message) {
        super(fieldPath + ": " + message);
        this.fieldPath = fieldPath == null ? "" : fieldPath;
    }

    public static ConfigException of(String fieldPath, String message) {
        return new ConfigException(fieldPath, message);
    }

    public String fieldPath() {
        return fieldPath;
    }

    public ErrorCode code() {
        return ErrorCode.CONFIG_INVALID;
    }
}

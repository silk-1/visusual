package silkvis.config;

import silkvis.config.json.JsonObject;

/**
 * Encode/decode contract for one config file. Every codec owns its schema version so that
 * a file written by a newer build is never silently downgraded (CFG-04).
 */
public interface ConfigCodec<T> {
    int currentSchemaVersion();

    T defaults();

    T decode(JsonObject root) throws ConfigException;

    JsonObject encode(T value);

    void validate(T value) throws ConfigException;
}

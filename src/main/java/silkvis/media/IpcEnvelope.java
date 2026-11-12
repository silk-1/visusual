package silkvis.media;

import silkvis.config.json.JsonObject;

/**
 * One NDJSON protocol line (MEDIA-07). {@code stdout} carries protocol lines only;
 * diagnostics go to {@code stderr}.
 */
public record IpcEnvelope(int schemaVersion, String type, Long requestId, JsonObject payload) {
    public static final int SCHEMA_VERSION = 1;

    public static IpcEnvelope of(String type, JsonObject payload) {
        return new IpcEnvelope(SCHEMA_VERSION, type, null, payload == null ? new JsonObject() : payload);
    }

    public static IpcEnvelope request(long requestId, String type, JsonObject payload) {
        return new IpcEnvelope(SCHEMA_VERSION, type, requestId, payload == null ? new JsonObject() : payload);
    }
}

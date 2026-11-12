package silkvis.media;

import silkvis.config.json.JsonException;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Strict NDJSON codec for the helper bridge (MEDIA-07). Oversized or malformed lines
 * are rejected, metadata strings are length-capped, and payloads are never executed.
 */
public final class IpcCodec {
    private IpcCodec() {
    }

    public static String encode(IpcEnvelope envelope) {
        JsonObject root = JsonValue.newObject()
                .put("schemaVersion", envelope.schemaVersion())
                .put("type", envelope.type());
        if (envelope.requestId() != null) {
            root.put("requestId", envelope.requestId());
        }
        root.put("payload", envelope.payload() == null ? JsonValue.newObject() : envelope.payload());
        return root.write(false);
    }

    public static IpcEnvelope decodeLine(String line) throws IpcException {
        if (line == null) {
            throw new IpcException("empty line");
        }
        int bytes = line.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > IpcProtocol.MAX_LINE_BYTES) {
            throw new IpcException("line exceeds " + IpcProtocol.MAX_LINE_BYTES + " bytes");
        }
        JsonObject root;
        try {
            root = JsonValue.parse(line).object();
        } catch (JsonException | RuntimeException e) {
            throw new IpcException("malformed json");
        }
        int schema = intField(root, "schemaVersion", 0);
        if (schema != IpcProtocol.PROTOCOL_VERSION) {
            throw new IpcException("unsupported schemaVersion " + schema);
        }
        String type = stringField(root, "type", "");
        if (type.isBlank() || type.length() > 32) {
            throw new IpcException("missing or invalid type");
        }
        Long requestId = null;
        if (root.has("requestId")) {
            requestId = longField(root, "requestId");
        }
        JsonObject payload = root.has("payload") && !root.get("payload").isNull()
                ? root.get("payload").object() : new JsonObject();
        checkStringCaps(payload);
        return new IpcEnvelope(schema, type, requestId, payload);
    }

    private static void checkStringCaps(JsonObject payload) throws IpcException {
        for (var e : payload.asMap().entrySet()) {
            JsonValue v = e.getValue();
            if (v instanceof silkvis.config.json.JsonString s
                    && s.value().length() > IpcProtocol.MAX_METADATA_CHARS) {
                throw new IpcException("metadata field '" + e.getKey() + "' is too long");
            }
        }
    }

    private static int intField(JsonObject root, String key, int fallback) {
        if (!root.has(key)) {
            return fallback;
        }
        return root.get(key).number().intValueExact();
    }

    private static long longField(JsonObject root, String key) {
        BigDecimal value = root.get(key).number();
        return value.longValueExact();
    }

    private static String stringField(JsonObject root, String key, String fallback) {
        if (!root.has(key)) {
            return fallback;
        }
        return root.get(key).string();
    }

    /** Protocol violation; callers degrade the provider instead of crashing the client. */
    public static final class IpcException extends Exception {
        private static final long serialVersionUID = 1L;

        public IpcException(String message) {
            super(message);
        }
    }
}

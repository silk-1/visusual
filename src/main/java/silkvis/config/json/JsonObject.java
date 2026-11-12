package silkvis.config.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** JSON object. Insertion order is preserved so written files stay diff-friendly. */
public final class JsonObject extends JsonValue {
    private final Map<String, JsonValue> entries = new LinkedHashMap<>();

    public JsonObject() {
        super(Kind.OBJECT, null);
    }

    public JsonObject put(String key, JsonValue value) {
        entries.put(key, value == null ? nullValue() : value);
        return this;
    }

    public JsonObject put(String key, String value) {
        return put(key, JsonValue.of(value));
    }

    public JsonObject put(String key, long value) {
        return put(key, JsonValue.of(value));
    }

    public JsonObject put(String key, int value) {
        return put(key, JsonValue.of((long) value));
    }

    public JsonObject put(String key, double value) {
        return put(key, JsonValue.of(value));
    }

    public JsonObject put(String key, boolean value) {
        return put(key, JsonValue.of(value));
    }

    public JsonValue get(String key) {
        JsonValue value = entries.get(key);
        return value == null ? nullValue() : value;
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Set<String> keySet() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /** Read-only view for protocol code that must iterate unknown keys. */
    public Map<String, JsonValue> asMap() {
        return Collections.unmodifiableMap(entries);
    }

    @Override
    protected void writeTo(StringBuilder out, int indent, int depth) {
        if (entries.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, JsonValue> entry : entries.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            if (indent >= 0) {
                newline(out, indent, depth + 1);
            }
            writeString(out, entry.getKey());
            out.append(indent >= 0 ? ": " : ":");
            entry.getValue().writeTo(out, indent, depth + 1);
        }
        if (indent >= 0) {
            newline(out, indent, depth);
        }
        out.append('}');
    }
}

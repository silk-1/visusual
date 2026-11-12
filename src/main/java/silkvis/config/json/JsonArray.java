package silkvis.config.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JSON array. */
public final class JsonArray extends JsonValue {
    private final List<JsonValue> items = new ArrayList<>();

    JsonArray() {
        super(Kind.ARRAY, null);
    }

    public JsonArray add(JsonValue value) {
        items.add(value == null ? nullValue() : value);
        return this;
    }

    public JsonArray add(String value) {
        return add(JsonValue.of(value));
    }

    public JsonArray add(long value) {
        return add(JsonValue.of(value));
    }

    public JsonArray add(int value) {
        return add(JsonValue.of((long) value));
    }

    public JsonArray add(double value) {
        return add(JsonValue.of(value));
    }

    public JsonArray add(boolean value) {
        return add(JsonValue.of(value));
    }

    public JsonValue get(int index) {
        return items.get(index);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<JsonValue> asList() {
        return Collections.unmodifiableList(items);
    }

    @Override
    protected void writeTo(StringBuilder out, int indent, int depth) {
        if (items.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            if (indent >= 0) {
                newline(out, indent, depth + 1);
            }
            items.get(i).writeTo(out, indent, depth + 1);
        }
        if (indent >= 0) {
            newline(out, indent, depth);
        }
        out.append(']');
    }
}

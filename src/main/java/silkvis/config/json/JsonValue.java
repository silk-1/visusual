package silkvis.config.json;

import java.math.BigDecimal;

/**
 * Minimal JSON tree (CFG-01). Deliberately dependency-free: no Gson/Jackson, per the
 * dependency policy, and no dynamic typing surprises beyond what the config schema needs.
 *
 * <p>Accessors are unchecked: a wrong type throws {@link IllegalArgumentException}.
 * Callers that read untrusted files guard with {@link JsonObject#has(String)} and convert
 * failures into {@code ConfigException}.</p>
 */
public class JsonValue {
    /** Hard depth cap for parsing: a malformed or hostile document cannot exhaust the stack. */
    public static final int MAX_DEPTH = 64;

    protected enum Kind {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }

    private static final JsonValue NULL = new JsonValue(Kind.NULL, null);

    private final Kind kind;
    private final Object raw;

    protected JsonValue(Kind kind, Object raw) {
        this.kind = kind;
        this.raw = raw;
    }

    public static JsonObject newObject() {
        return new JsonObject();
    }

    public static JsonArray newArray() {
        return new JsonArray();
    }

    public static JsonValue of(String value) {
        return value == null ? NULL : new JsonString(value);
    }

    public static JsonValue of(long value) {
        return new JsonValue(Kind.NUMBER, BigDecimal.valueOf(value));
    }

    public static JsonValue of(double value) {
        return Double.isFinite(value) ? new JsonValue(Kind.NUMBER, BigDecimal.valueOf(value)) : NULL;
    }

    static JsonValue ofRawNumber(String raw) {
        return new JsonValue(Kind.NUMBER, new BigDecimal(raw));
    }

    public static JsonValue of(boolean value) {
        return new JsonValue(Kind.BOOLEAN, value);
    }

    public static JsonValue nullValue() {
        return NULL;
    }

    public static JsonValue parse(String text) throws JsonException {
        if (text == null) {
            throw new JsonException("null input");
        }
        return new JsonParser(text).parseDocument();
    }

    public boolean isNull() {
        return kind == Kind.NULL;
    }

    public String string() {
        if (kind != Kind.STRING) {
            throw new IllegalArgumentException("expected string, got " + kind.name().toLowerCase());
        }
        return (String) raw;
    }

    /** Exact decimal value: colours, byte counts and versions must never round-trip through double. */
    public BigDecimal number() {
        if (kind != Kind.NUMBER) {
            throw new IllegalArgumentException("expected number, got " + kind.name().toLowerCase());
        }
        return (BigDecimal) raw;
    }

    public boolean bool() {
        if (kind != Kind.BOOLEAN) {
            throw new IllegalArgumentException("expected boolean, got " + kind.name().toLowerCase());
        }
        return (Boolean) raw;
    }

    public JsonObject object() {
        if (kind != Kind.OBJECT) {
            throw new IllegalArgumentException("expected object, got " + kind.name().toLowerCase());
        }
        return (JsonObject) this;
    }

    public JsonArray array() {
        if (kind != Kind.ARRAY) {
            throw new IllegalArgumentException("expected array, got " + kind.name().toLowerCase());
        }
        return (JsonArray) this;
    }

    public String write(boolean pretty) {
        StringBuilder out = new StringBuilder(256);
        writeTo(out, pretty ? 2 : -1, 0);
        return out.toString();
    }

    protected void writeTo(StringBuilder out, int indent, int depth) {
        switch (kind) {
            case NULL -> out.append("null");
            case BOOLEAN -> out.append(((Boolean) raw) ? "true" : "false");
            case STRING -> writeString(out, (String) raw);
            case NUMBER -> writeNumber(out, (Number) raw);
            default -> throw new IllegalStateException("container must override writeTo");
        }
    }

    protected static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void writeNumber(StringBuilder out, Number value) {
        out.append(((BigDecimal) value).toPlainString());
    }

    protected static void newline(StringBuilder out, int indent, int depth) {
        if (indent < 0) {
            return;
        }
        out.append('\n');
        out.append(" ".repeat(indent * depth));
    }
}

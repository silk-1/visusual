package silkvis.config.json;

/** String leaf. Exposed as a type so protocol code can pattern-match on it. */
public final class JsonString extends JsonValue {
    JsonString(String value) {
        super(Kind.STRING, value);
    }

    public String value() {
        return string();
    }
}

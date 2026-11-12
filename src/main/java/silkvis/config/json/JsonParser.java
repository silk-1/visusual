package silkvis.config.json;

/**
 * Strict JSON parser: rejects single quotes, NaN/Infinity, trailing content, raw control
 * characters in strings and documents deeper than {@link JsonValue#MAX_DEPTH}.
 * Message text is English on purpose (logs); user-facing strings live in lang files.
 */
final class JsonParser {
    private final String text;
    private int pos;

    JsonParser(String text) {
        this.text = text;
    }

    JsonValue parseDocument() throws JsonException {
        skipWhitespace();
        if (pos >= text.length()) {
            throw error("empty document");
        }
        JsonValue value = parseValue(0);
        skipWhitespace();
        if (pos != text.length()) {
            throw error("trailing content");
        }
        return value;
    }

    private JsonValue parseValue(int depth) throws JsonException {
        if (depth > JsonValue.MAX_DEPTH) {
            throw error("too deep");
        }
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> parseObject(depth);
            case '[' -> parseArray(depth);
            case '"' -> JsonValue.of(parseString());
            case 't' -> literal("true", JsonValue.of(true));
            case 'f' -> literal("false", JsonValue.of(false));
            case 'n' -> literal("null", JsonValue.nullValue());
            default -> parseNumber();
        };
    }

    private JsonValue literal(String word, JsonValue value) throws JsonException {
        if (!text.startsWith(word, pos)) {
            throw error("unexpected token");
        }
        pos += word.length();
        return value;
    }

    private JsonObject parseObject(int depth) throws JsonException {
        JsonObject object = JsonValue.newObject();
        pos++;
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected string key");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, parseValue(depth + 1));
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return object;
            }
            throw error("expected ',' or '}'");
        }
    }

    private JsonArray parseArray(int depth) throws JsonException {
        JsonArray array = JsonValue.newArray();
        pos++;
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(parseValue(depth + 1));
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return array;
            }
            throw error("expected ',' or ']'");
        }
    }

    private String parseString() throws JsonException {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw error("raw control character in string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("unterminated escape");
            }
            char esc = text.charAt(pos++);
            switch (esc) {
                case '"', '\\', '/' -> out.append(esc);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("truncated unicode escape");
                    }
                    String hex = text.substring(pos, pos + 4);
                    try {
                        out.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw error("bad unicode escape");
                    }
                    pos += 4;
                }
                default -> throw error("bad escape");
            }
        }
    }

    private JsonValue parseNumber() throws JsonException {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (pos >= text.length() || !isDigit(text.charAt(pos))) {
            throw error("unexpected token");
        }
        if (text.charAt(pos) == '0') {
            pos++;
        } else {
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
            }
        }
        boolean integral = true;
        if (pos < text.length() && text.charAt(pos) == '.') {
            integral = false;
            pos++;
            int digits = 0;
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
                digits++;
            }
            if (digits == 0) {
                throw error("bad fraction");
            }
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            integral = false;
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            int digits = 0;
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
                digits++;
            }
            if (digits == 0) {
                throw error("bad exponent");
            }
        }
        String raw = text.substring(start, pos);
        try {
            return JsonValue.ofRawNumber(raw);
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private char peek() throws JsonException {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(pos);
    }

    private void expect(char c) throws JsonException {
        if (pos >= text.length() || text.charAt(pos) != c) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private JsonException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new JsonException(message + " at " + line + ":" + column);
    }
}

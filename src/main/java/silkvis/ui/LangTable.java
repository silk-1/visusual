package silkvis.ui;

import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * SilkVis' own language table (LOC-*), loaded straight from the mod jar.
 *
 * <p>Minecraft's {@code Text.translatable} resolves against the <em>game</em> language, which
 * made the “Язык” setting in our settings tab do nothing at all: it was written to the config
 * and read back only by the bug report. This table makes the setting real — the UI follows the
 * selected language whatever the game is set to, and falls back to the game language for keys
 * that are missing here.</p>
 */
public final class LangTable {
    /** Code stored in config → lang file inside the jar. */
    private static final Map<String, String> FILES = Map.of(
            "en", "en_us",
            "ru", "ru_ru");

    private static final Map<String, LangTable> CACHE = new HashMap<>();

    private final Map<String, String> entries;

    private LangTable(Map<String, String> entries) {
        this.entries = entries;
    }

    public static LangTable empty() {
        return new LangTable(Map.of());
    }

    public static synchronized LangTable load(String language) {
        String code = language == null ? "en" : language.trim().toLowerCase(Locale.ROOT);
        LangTable cached = CACHE.get(code);
        if (cached != null) {
            return cached;
        }
        LangTable table = new LangTable(read(FILES.getOrDefault(code, "en_us")));
        CACHE.put(code, table);
        return table;
    }

    private static Map<String, String> read(String file) {
        String path = "/assets/silkvis/lang/" + file + ".json";
        try (InputStream in = LangTable.class.getResourceAsStream(path)) {
            if (in == null) {
                return Map.of();
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = JsonValue.parse(text).object();
            Map<String, String> out = new HashMap<>(root.asMap().size() * 2);
            for (var entry : root.asMap().entrySet()) {
                try {
                    out.put(entry.getKey(), entry.getValue().string());
                } catch (RuntimeException ignored) {
                    // Non-string values are not translations; skip instead of failing a whole
                    // language because of one bad entry.
                }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            // A broken language file must not break the UI: the game language takes over.
            return Map.of();
        }
    }

    /** Localized text, or null when this table has no such key. */
    public String get(String key) {
        return key == null ? null : entries.get(key);
    }

    public int size() {
        return entries.size();
    }
}

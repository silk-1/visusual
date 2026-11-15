package silkvis.visual.soup.setting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import silkvis.config.json.JsonArray;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonString;
import silkvis.config.json.JsonValue;
import silkvis.visual.soup.module.SoupModule;

/**
 * Saves and loads the ported module settings.
 *
 * <p>The original keeps them in {@code soupvisuals.json} next to its other configs; here the
 * file is {@code silkvis/soupvisuals.json}. Without a file on disk every module came back off
 * after a restart, which makes the menu look broken: switch a module on, restart, and it is
 * off again with its settings reset.</p>
 *
 * <p>Shape: {@code {"version":1,"modules":{"hitbubbles":true},
 * "settings":{"hitbubbles.appearduration":0.4}}}. Keys come from {@link SettingRepository}, so
 * the names on disk are the ones the original used and a hand edit stays readable.</p>
 *
 * <p>Uses the mod's own JSON tree (CFG-01) rather than a new dependency, and never throws on a
 * damaged file: a broken config must not cost the player the whole menu.</p>
 */
public final class SoupSettingsFile {
    /** Cap on the file the mod is willing to read back; the real file is a few kilobytes. */
    private static final long MAX_BYTES = 1L << 20;

    private SoupSettingsFile() {
    }

    /** Writes every module's on/off state and every persisted setting value. */
    public static void save(Path file, List<SoupModule> modules) throws IOException {
        JsonObject root = new JsonObject();
        root.put("version", 1);
        JsonObject states = new JsonObject();
        JsonObject values = new JsonObject();
        for (SoupModule module : modules) {
            states.put(moduleKey(module), module.isEnabled());
            for (Map.Entry<String, Setting> entry
                    : SettingRepository.byConfigKey(module.getRootSettings()).entrySet()) {
                JsonValue encoded = encode(entry.getValue());
                if (encoded != null) {
                    values.put(entry.getKey(), encoded);
                }
            }
        }
        root.put("modules", states);
        root.put("settings", values);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path target = file.toAbsolutePath();
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, root.write(true) + "\n", StandardCharsets.UTF_8);
        // Written through a temporary file and moved: the client can be killed at any moment,
        // and a half-written config would come back as "no settings at all".
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Applies a saved file over the defaults. A missing file is not an error (first run).
     *
     * @return true when the file was read and applied
     */
    public static boolean load(Path file, List<SoupModule> modules) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            if (Files.size(file) > MAX_BYTES) {
                // Same cap as every other config file: a runaway file is treated as damaged.
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        JsonObject root;
        try {
            root = JsonValue.parse(Files.readString(file, StandardCharsets.UTF_8)).object();
        } catch (IOException | RuntimeException e) {
            return false;
        }
        Map<String, Setting> index = new LinkedHashMap<>();
        for (SoupModule module : modules) {
            index.putAll(SettingRepository.byConfigKey(module.getRootSettings()));
        }
        JsonObject values = object(root, "settings");
        if (values != null) {
            for (Map.Entry<String, Setting> entry : index.entrySet()) {
                JsonValue value = values.get(entry.getKey());
                if (value != null && !value.isNull()) {
                    apply(entry.getValue(), value);
                }
            }
        }
        JsonObject states = object(root, "modules");
        if (states != null) {
            for (SoupModule module : modules) {
                JsonValue state = states.get(moduleKey(module));
                if (state != null && !state.isNull()) {
                    try {
                        module.setEnabled(state.bool());
                    } catch (RuntimeException ignored) {
                        // A non-boolean leaves the default in place.
                    }
                }
            }
        }
        return true;
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonValue value = root.get(key);
        try {
            return value == null ? null : value.object();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void apply(Setting setting, JsonValue value) {
        try {
            if (setting instanceof BooleanSetting bool) {
                bool.setValue(value.bool());
            } else if (setting instanceof ValueSetting number) {
                number.setValue((float) number(value));
            } else if (setting instanceof SelectSetting select) {
                select.setSelected(value.string());
            } else if (setting instanceof MultiSelectSetting multi && value instanceof JsonArray array) {
                List<String> selected = new ArrayList<>();
                for (JsonValue element : array.asList()) {
                    if (element instanceof JsonString text) {
                        selected.add(text.value());
                    }
                }
                multi.setSelected(selected);
            } else if (setting instanceof ColorSetting color) {
                color.setColor((int) number(value));
            }
        } catch (RuntimeException ignored) {
            // Wrong type for this key: keep the default instead of failing the whole file.
        }
    }

    private static double number(JsonValue value) {
        return value.number().doubleValue();
    }

    /** The JSON form of one setting, or null when the type is not persisted. */
    static JsonValue encode(Setting setting) {
        if (setting instanceof BooleanSetting bool) {
            return JsonValue.of(bool.isValue());
        }
        if (setting instanceof ValueSetting value) {
            return JsonValue.of((double) value.getValue());
        }
        if (setting instanceof SelectSetting select) {
            // A select with no selection yet (a module can declare the list after the value)
            // would write JSON null and overwrite the default on the next load.
            return select.getSelected() == null ? null : JsonValue.of(select.getSelected());
        }
        if (setting instanceof MultiSelectSetting multi) {
            JsonArray array = new JsonArray();
            for (String option : multi.getSelected()) {
                array.add(option);
            }
            return array;
        }
        if (setting instanceof ColorSetting color) {
            return JsonValue.of((long) color.getColor());
        }
        return null;
    }

    /** Public form of {@link #moduleKey(SoupModule)} for ids outside this package. */
    public static String keyOf(SoupModule module) {
        return moduleKey(module);
    }

    /**
     * Stable module id from its lang key: {@code module.hitbubbles.name} and
     * {@code setting.hitbubbles.something.name} both give {@code hitbubbles}.
     *
     * <p>Both prefixes have to be stripped: taking the first segment as-is produced
     * {@code module} for every single module, which would have written ten modules to one key
     * and made the saved file useless.</p>
     */
    static String moduleKey(SoupModule module) {
        String key = module.getNameKey();
        if (key == null) {
            return "unknown";
        }
        String stripped = key;
        if (stripped.startsWith("setting.")) {
            stripped = stripped.substring("setting.".length());
        } else if (stripped.startsWith("module.")) {
            stripped = stripped.substring("module.".length());
        }
        int dot = stripped.indexOf('.');
        String id = dot > 0 ? stripped.substring(0, dot) : stripped;
        return id.toLowerCase(Locale.ROOT);
    }
}

package silkvis.visual.soup.setting;

import silkvis.config.json.JsonArray;
import silkvis.config.json.JsonException;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonString;
import silkvis.config.json.JsonValue;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.modules.AmbientParticles;
import silkvis.visual.soup.modules.HitBubbles;
import silkvis.visual.soup.modules.JumpCircles;
import silkvis.visual.soup.modules.TotemParticles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Built-in particle looks and named, user-owned snapshots for SoupVisuals. */
public final class SoupVisualPresets {
    private static final long MAX_BYTES = 1L << 20;
    private static final int MAX_PRESETS = 50;
    private static final List<String> BUILT_INS = List.of("clean", "aurora", "sparks");

    private SoupVisualPresets() {
    }

    public static List<String> builtIns() {
        return BUILT_INS;
    }

    /** Applies a restrained ambient look and keeps the ring effects disabled. */
    public static boolean applyBuiltIn(String id, List<SoupModule> modules) {
        if (id == null || modules == null || !BUILT_INS.contains(id)) return false;
        AmbientParticles ambient = find(modules, AmbientParticles.class);
        TotemParticles totem = find(modules, TotemParticles.class);
        JumpCircles circles = find(modules, JumpCircles.class);
        HitBubbles bubbles = find(modules, HitBubbles.class);
        if (ambient == null || totem == null || circles == null || bubbles == null) return false;

        circles.setEnabled(false);
        bubbles.setEnabled(false);
        totem.setEnabled(false);
        ambient.setEnabled(false);
        switch (id) {
            case "clean" -> {
                ambient.modeSetting().setSelected(List.of("Stars", "Snowflake"));
                ambient.physicsSetting().setSelected(List.of("Fly"));
                setValue(ambient, "ambientparticles.regularcount", 35);
                setValue(ambient, "ambientparticles.regularscale", 1.5);
                setValue(ambient, "ambientparticles.spawnradius", 16);
                setValue(ambient, "ambientparticles.spawnheight", 8);
                ambient.setEnabled(true);
            }
            case "aurora" -> {
                ambient.modeSetting().setSelected(List.of("Stars", "Snowflake", "Network"));
                ambient.physicsSetting().setSelected(List.of("Fly"));
                setValue(ambient, "ambientparticles.regularcount", 70);
                setValue(ambient, "ambientparticles.regularscale", 2);
                setValue(ambient, "ambientparticles.spawnradius", 22);
                setValue(ambient, "ambientparticles.spawnheight", 12);
                setValue(ambient, "ambientparticles.linkdistance", 5);
                ambient.setEnabled(true);
            }
            case "sparks" -> {
                ambient.modeSetting().setSelected(List.of("Stars", "Hearts", "Dollar", "Snowflake"));
                ambient.physicsSetting().setSelected(List.of("Fly", "Fall"));
                setValue(ambient, "ambientparticles.regularcount", 100);
                setValue(ambient, "ambientparticles.regularscale", 2.5);
                setValue(ambient, "ambientparticles.spawnradius", 25);
                setValue(ambient, "ambientparticles.spawnheight", 14);
                totem.modeSetting().setSelected(List.of("Stars", "Hearts", "Bloom"));
                totem.physicsSetting().setSelected(List.of("Fly"));
                totem.particleCountSetting().setValue(35.0F);
                totem.setEnabled(true);
                ambient.setEnabled(true);
            }
            default -> { return false; }
        }
        return true;
    }

    public static boolean save(Path file, List<SoupModule> modules, String rawName) throws IOException {
        String name = normalizeName(rawName);
        if (name == null || modules == null) return false;
        JsonObject root = readRoot(file);
        if (root == null) root = new JsonObject().put("version", 1).put("presets", new JsonObject());
        JsonObject presets = object(root, "presets");
        if (presets == null) {
            presets = new JsonObject();
            root.put("presets", presets);
        }
        if (!presets.has(name) && presets.size() >= MAX_PRESETS) return false;
        presets.put(name, snapshot(modules));
        write(file, root);
        return true;
    }

    public static boolean load(Path file, List<SoupModule> modules, String rawName) {
        String name = normalizeName(rawName);
        if (name == null || modules == null) return false;
        JsonObject root = readRoot(file);
        JsonObject presets = root == null ? null : object(root, "presets");
        JsonObject preset = presets == null ? null : object(presets, name);
        return preset != null && applySnapshot(preset, modules);
    }

    public static List<String> names(Path file) {
        JsonObject root = readRoot(file);
        JsonObject presets = root == null ? null : object(root, "presets");
        if (presets == null) return List.of();
        List<String> names = new ArrayList<>();
        for (String name : presets.keySet()) if (normalizeName(name) != null) names.add(name);
        return List.copyOf(names);
    }

    public static boolean delete(Path file, String rawName) throws IOException {
        String name = normalizeName(rawName);
        JsonObject root = readRoot(file);
        JsonObject presets = root == null ? null : object(root, "presets");
        if (name == null || presets == null || !presets.has(name)) return false;
        JsonObject kept = new JsonObject();
        for (String key : presets.keySet()) if (!key.equals(name)) kept.put(key, presets.get(key));
        root.put("presets", kept);
        write(file, root);
        return true;
    }

    private static JsonObject snapshot(List<SoupModule> modules) {
        JsonObject states = new JsonObject();
        JsonObject values = new JsonObject();
        for (SoupModule module : modules) {
            states.put(SoupSettingsFile.keyOf(module), module.isEnabled());
            for (Map.Entry<String, Setting> entry
                    : SettingRepository.byConfigKey(module.getRootSettings()).entrySet()) {
                JsonValue encoded = SoupSettingsFile.encode(entry.getValue());
                if (encoded != null) values.put(entry.getKey(), encoded);
            }
        }
        return new JsonObject().put("modules", states).put("settings", values);
    }

    private static boolean applySnapshot(JsonObject snapshot, List<SoupModule> modules) {
        JsonObject values = object(snapshot, "settings");
        JsonObject states = object(snapshot, "modules");
        if (values == null || states == null) return false;
        Map<String, Setting> settings = new LinkedHashMap<>();
        for (SoupModule module : modules) settings.putAll(SettingRepository.byConfigKey(module.getRootSettings()));
        for (Map.Entry<String, Setting> entry : settings.entrySet()) {
            JsonValue value = values.get(entry.getKey());
            if (value != null && !value.isNull()) applyValue(entry.getValue(), value);
        }
        for (SoupModule module : modules) {
            JsonValue state = states.get(SoupSettingsFile.keyOf(module));
            if (state != null && !state.isNull()) {
                try { module.setEnabled(state.bool()); } catch (RuntimeException ignored) { }
            }
        }
        return true;
    }

    private static void applyValue(Setting setting, JsonValue value) {
        try {
            if (setting instanceof BooleanSetting bool) {
                bool.setValue(value.bool());
            } else if (setting instanceof ValueSetting number) {
                double parsed = value.number().doubleValue();
                if (Double.isFinite(parsed)) number.setValue((float) Math.max(number.getMin(),
                        Math.min(number.getMax(), parsed)));
            } else if (setting instanceof SelectSetting select) {
                String option = value.string();
                if (select.getList() != null && select.getList().contains(option)) select.setSelected(option);
            } else if (setting instanceof MultiSelectSetting multi && value instanceof JsonArray array) {
                List<String> selected = new ArrayList<>();
                for (JsonValue item : array.asList()) {
                    if (item instanceof JsonString text && multi.getList().contains(text.value())) {
                        selected.add(text.value());
                    }
                }
                multi.setSelected(selected);
            } else if (setting instanceof ColorSetting color) {
                double parsed = value.number().doubleValue();
                if (Double.isFinite(parsed)) color.setColor((int) parsed);
            }
        } catch (RuntimeException ignored) { }
    }

    private static void setValue(SoupModule module, String key, float value) {
        Setting setting = SettingRepository.byConfigKey(module.getRootSettings()).get(key);
        if (setting instanceof ValueSetting number) number.setValue(value);
    }

    private static JsonObject readRoot(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            if (Files.size(file) > MAX_BYTES) return null;
            JsonObject root = JsonValue.parse(Files.readString(file, StandardCharsets.UTF_8)).object();
            return root.get("version").number().intValue() == 1 ? root : null;
        } catch (IOException | JsonException | RuntimeException e) { return null; }
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return null;
        try { return parent.get(key).object(); } catch (RuntimeException e) { return null; }
    }

    private static void write(Path file, JsonObject root) throws IOException {
        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, root.write(true) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalizeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        if (name.isEmpty() || name.length() > 32) return null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-')) return null;
        }
        return name;
    }

    private static <T extends SoupModule> T find(List<SoupModule> modules, Class<T> type) {
        for (SoupModule module : modules) if (type.isInstance(module)) return type.cast(module);
        return null;
    }
}

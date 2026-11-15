package silkvis.visual.soup.setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattening helper for the ported settings tree (the original keeps the flat list in
 * {@code SettingRepository}; here it is built from the module's own groups so the JSON
 * codec and the settings screen see the same order).
 *
 * <p>Order matters: the settings screen of the original renders groups in declaration
 * order, and the config file is written in that same order.</p>
 */
public final class SettingRepository {
    private SettingRepository() {
    }

    /** Depth-first flattening: group headers stay in the list, their children follow. */
    public static List<Setting> flatten(List<Setting> roots) {
        List<Setting> out = new ArrayList<>();
        for (Setting setting : roots) {
            flattenInto(setting, out);
        }
        return out;
    }

    private static void flattenInto(Setting setting, List<Setting> out) {
        out.add(setting);
        if (setting instanceof GroupSetting group) {
            for (Setting child : group.getSubSettings()) {
                flattenInto(child, out);
            }
        } else if (setting instanceof MultiColorSetting palette) {
            // The palette is a container of four colour slots, so the slots are expanded here
            // and the palette itself only appears in the UI.
            out.add(palette.getColor1());
            out.add(palette.getColor2());
            out.add(palette.getColor3());
            out.add(palette.getColor4());
        }
    }

    /**
     * Only settings that are actually persisted. Groups and the multi-colour palette are
     * containers (their colour slots are the values), buttons are actions.
     */
    public static List<Setting> valueSettings(List<Setting> roots) {
        List<Setting> out = new ArrayList<>();
        for (Setting setting : flatten(roots)) {
            if (setting instanceof ColorSetting color && isColorSlot(color)) {
                out.add(color);
            } else if (!(setting instanceof GroupSetting)
                    && !(setting instanceof ButtonSetting)
                    && !(setting instanceof MultiColorSetting)) {
                out.add(setting);
            }
        }
        return out;
    }

    /** A colour slot lives under its palette key ({@code ...gradientcolors.color1}). */
    private static boolean isColorSlot(ColorSetting setting) {
        return setting.getNameKey().contains(".color") && setting.getNameKey().matches(".*\\.color[1-4]\\.name");
    }

    /**
     * Stable config key of a setting, e.g. {@code hitbubbles.appearDuration}. The name key
     * of the original already contains the module ({@code setting.hitbubbles.x.name}), so
     * it is used verbatim minus the {@code setting.} prefix and the {@code .name} suffix;
     * settings without a dotted key fall back to their module context plus index.
     */
    public static String configKey(Setting setting) {
        String key = setting.getNameKey();
        if (key.contains(".")) {
            String stripped = key.startsWith("setting.") ? key.substring("setting.".length()) : key;
            if (stripped.endsWith(".name")) {
                stripped = stripped.substring(0, stripped.length() - ".name".length());
            }
            return stripped;
        }
        String context = setting.getModuleContext();
        return (context == null ? "misc" : context.toLowerCase().replace(" ", "")) + "." + key;
    }

    /** Index of every persisted setting by {@link #configKey(Setting)}. */
    public static Map<String, Setting> byConfigKey(List<Setting> roots) {
        Map<String, Setting> out = new LinkedHashMap<>();
        for (Setting setting : valueSettings(roots)) {
            out.put(configKey(setting), setting);
        }
        return out;
    }
}

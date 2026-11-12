package silkvis.config;

import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;
import silkvis.map.MapSettings;
import silkvis.ui.hud.HudLayoutConfig;
import silkvis.visual.VisualSettings;

/**
 * Per-profile UI settings: HUD layout, map settings and visual effects. Kept separate from
 * {@link GlobalConfig} so two worlds on one installation do not share HUD positions.
 */
public record UiConfig(HudLayoutConfig hud, MapSettings map, VisualSettings visual) {
    public UiConfig {
        if (hud == null) {
            hud = HudLayoutConfig.defaults();
        }
        if (map == null) {
            map = MapSettings.defaults();
        }
        if (visual == null) {
            visual = VisualSettings.defaults();
        }
    }

    public static UiConfig defaults() {
        return new UiConfig(HudLayoutConfig.defaults(), MapSettings.defaults(), VisualSettings.defaults());
    }

    public static final class Codec implements ConfigCodec<UiConfig> {
        public static final int SCHEMA_VERSION = 1;

        @Override
        public int currentSchemaVersion() {
            return SCHEMA_VERSION;
        }

        @Override
        public UiConfig defaults() {
            return UiConfig.defaults();
        }

        @Override
        public UiConfig decode(JsonObject root) throws ConfigException {
            UiConfig d = defaults();
            HudLayoutConfig hud = root.has("hud") ? HudLayoutConfig.decode(root.get("hud").object()) : d.hud();
            MapSettings map = root.has("map") ? MapSettings.decode(root.get("map").object()) : d.map();
            VisualSettings visual = root.has("visual")
                    ? VisualSettings.decode(root.get("visual").object())
                    : d.visual();
            return new UiConfig(hud, map, visual);
        }

        @Override
        public JsonObject encode(UiConfig value) {
            return JsonValue.newObject()
                    .put("hud", value.hud().encode())
                    .put("map", value.map().encode())
                    .put("visual", value.visual().encode());
        }

        @Override
        public void validate(UiConfig value) throws ConfigException {
            value.hud().validate();
            value.map().validate();
            value.visual().validate();
        }
    }
}

package silkvis.ui.hud;

import silkvis.config.ConfigException;
import silkvis.config.json.JsonArray;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted HUD layout, anchor + logical offsets only (HUD-02). Missing widgets are
 * filled with defaults so a partial file never breaks the editor.
 */
public record HudLayoutConfig(List<HudWidgetConfig> widgets) {
    public static final int MAX_OFFSET = 4000;

    public HudLayoutConfig {
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }

    public static HudLayoutConfig defaults() {
        List<HudWidgetConfig> list = new ArrayList<>();
        for (HudWidgetId id : HudWidgetId.values()) {
            list.add(HudWidgetConfig.defaults(id));
        }
        return new HudLayoutConfig(list);
    }

    public Map<HudWidgetId, HudWidgetConfig> byId() {
        Map<HudWidgetId, HudWidgetConfig> map = new EnumMap<>(HudWidgetId.class);
        for (HudWidgetConfig c : widgets) {
            map.put(c.id(), c);
        }
        for (HudWidgetId id : HudWidgetId.values()) {
            map.putIfAbsent(id, HudWidgetConfig.defaults(id));
        }
        return map;
    }

    public HudWidgetConfig get(HudWidgetId id) {
        return byId().get(id);
    }

    public HudLayoutConfig with(HudWidgetConfig config) {
        Map<HudWidgetId, HudWidgetConfig> map = byId();
        map.put(config.id(), config);
        return new HudLayoutConfig(new ArrayList<>(map.values()));
    }

    /** Editing/reload/resize must not push widgets under the hotbar (HUD-02). */
    public List<HudWidgetId> widgetsOverProtectedArea(silkvis.ui.Rect screen, int lineHeight) {
        List<HudWidgetId> offenders = new ArrayList<>();
        for (HudWidgetConfig c : byId().values()) {
            if (!c.enabled()) {
                continue;
            }
            silkvis.ui.Rect rect = HudLayoutEngine.resolve(screen, HudLayoutEngine.safeArea(screen), c,
                    120, Math.max(1, lineHeight));
            if (HudLayoutEngine.overlapsProtected(rect, screen)) {
                offenders.add(c.id());
            }
        }
        return offenders;
    }

    public static HudLayoutConfig decode(JsonObject root) throws ConfigException {
        if (!root.has("widgets")) {
            return defaults();
        }
        JsonArray array = root.get("widgets").array();
        List<HudWidgetConfig> list = new ArrayList<>();
        for (JsonValue value : array.asList()) {
            JsonObject o = value.object();
            HudWidgetId id;
            try {
                id = HudWidgetId.valueOf(o.get("id").string());
            } catch (IllegalArgumentException e) {
                throw ConfigException.of("hud.widgets.id", "unknown widget id");
            }
            if (!id.p1()) {
                throw ConfigException.of("hud.widgets.id", "P2 widget is not available in this build");
            }
            Anchor anchor;
            try {
                anchor = Anchor.valueOf(o.has("anchor") ? o.get("anchor").string() : "TOP_LEFT");
            } catch (IllegalArgumentException e) {
                throw ConfigException.of("hud.widgets.anchor", "unknown anchor");
            }
            int offsetX = o.has("offsetX") ? o.get("offsetX").number().intValueExact() : 0;
            int offsetY = o.has("offsetY") ? o.get("offsetY").number().intValueExact() : 0;
            if (Math.abs(offsetX) > MAX_OFFSET || Math.abs(offsetY) > MAX_OFFSET) {
                throw ConfigException.of("hud.widgets.offset", "offset out of range");
            }
            float scale = o.has("scale") ? o.get("scale").number().floatValue() : 1f;
            float alpha = o.has("alpha") ? o.get("alpha").number().floatValue() : 1f;
            int z = o.has("zIndex") ? o.get("zIndex").number().intValueExact() : 10;
            VisibilityPolicy policy = o.has("visibility")
                    ? decodeVisibility(o.get("visibility").object())
                    : VisibilityPolicy.defaults();
            list.add(new HudWidgetConfig(id,
                    !o.has("enabled") || o.get("enabled").bool(),
                    anchor, offsetX, offsetY, scale, alpha, z, policy));
        }
        return new HudLayoutConfig(list);
    }

    private static VisibilityPolicy decodeVisibility(JsonObject o) {
        VisibilityPolicy d = VisibilityPolicy.defaults();
        return new VisibilityPolicy(
                o.has("hideOnF1") ? o.get("hideOnF1").bool() : d.hideOnF1(),
                o.has("hideOnDebugOverlay") ? o.get("hideOnDebugOverlay").bool() : d.hideOnDebugOverlay(),
                o.has("hideOnChat") ? o.get("hideOnChat").bool() : d.hideOnChat(),
                o.has("hideInInventory") ? o.get("hideInInventory").bool() : d.hideInInventory(),
                o.has("hideInPauseMenu") ? o.get("hideInPauseMenu").bool() : d.hideInPauseMenu(),
                o.has("hideInSpectator") ? o.get("hideInSpectator").bool() : d.hideInSpectator());
    }

    public JsonObject encode() {
        JsonArray array = JsonValue.newArray();
        for (HudWidgetConfig c : byId().values()) {
            array.add(JsonValue.newObject()
                    .put("id", c.id().name())
                    .put("enabled", c.enabled())
                    .put("anchor", c.anchor().name())
                    .put("offsetX", c.offsetX())
                    .put("offsetY", c.offsetY())
                    .put("scale", c.scale())
                    .put("alpha", c.alpha())
                    .put("zIndex", c.zIndex())
                    .put("visibility", JsonValue.newObject()
                            .put("hideOnF1", c.visibility().hideOnF1())
                            .put("hideOnDebugOverlay", c.visibility().hideOnDebugOverlay())
                            .put("hideOnChat", c.visibility().hideOnChat())
                            .put("hideInInventory", c.visibility().hideInInventory())
                            .put("hideInPauseMenu", c.visibility().hideInPauseMenu())
                            .put("hideInSpectator", c.visibility().hideInSpectator())));
        }
        return JsonValue.newObject().put("widgets", array);
    }

    public void validate() throws ConfigException {
        Map<HudWidgetId, Boolean> seen = new EnumMap<>(HudWidgetId.class);
        for (HudWidgetConfig c : widgets) {
            if (seen.put(c.id(), Boolean.TRUE) != null) {
                throw ConfigException.of("hud.widgets", "duplicate widget " + c.id());
            }
        }
    }
}

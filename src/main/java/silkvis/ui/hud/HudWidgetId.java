package silkvis.ui.hud;

import java.util.Locale;

/** Stable HUD widget ids (HUD-01). Ids are part of config, logs and lang keys. */
public enum HudWidgetId {
    COORDINATES,
    FPS,
    DIRECTION,
    BIOME,
    WORLD_TIME,
    EFFECTS,
    ARMOR,
    ACTIVE_TOOL,
    MINIMAP,
    NOW_PLAYING,
    /** Reserved for P2; must stay unavailable in the P1 build (0.2). */
    TASK_STATUS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "silkvis.hud.widget." + id();
    }

    public boolean p1() {
        return this != TASK_STATUS;
    }
}

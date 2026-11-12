package silkvis.ui.hud;

/**
 * Per-widget presentation settings (HUD-01). Every widget owns its own enabled flag,
 * scale, alpha, anchor, offset, zIndex and visibility policy.
 */
public record HudWidgetConfig(
        HudWidgetId id,
        boolean enabled,
        Anchor anchor,
        int offsetX,
        int offsetY,
        float scale,
        float alpha,
        int zIndex,
        VisibilityPolicy visibility
) {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;

    public HudWidgetConfig {
        if (anchor == null) {
            anchor = Anchor.TOP_LEFT;
        }
        if (visibility == null) {
            visibility = VisibilityPolicy.defaults();
        }
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        alpha = Math.max(0f, Math.min(1f, alpha));
    }

    /** Sensible defaults per widget; offsets are logical units, never screen pixels. */
    public static HudWidgetConfig defaults(HudWidgetId id) {
        return switch (id) {
            case COORDINATES -> new HudWidgetConfig(id, true, Anchor.TOP_LEFT, 6, 6, 1f, 0.95f, 10,
                    VisibilityPolicy.defaults());
            case FPS -> new HudWidgetConfig(id, true, Anchor.TOP_LEFT, 6, 24, 1f, 0.95f, 9,
                    VisibilityPolicy.defaults());
            case DIRECTION -> new HudWidgetConfig(id, false, Anchor.TOP_LEFT, 6, 42, 1f, 0.95f, 8,
                    VisibilityPolicy.defaults());
            case BIOME -> new HudWidgetConfig(id, false, Anchor.TOP_LEFT, 6, 60, 1f, 0.95f, 7,
                    VisibilityPolicy.defaults());
            case WORLD_TIME -> new HudWidgetConfig(id, false, Anchor.TOP_RIGHT, 6, 6, 1f, 0.95f, 7,
                    VisibilityPolicy.defaults());
            case EFFECTS -> new HudWidgetConfig(id, true, Anchor.TOP_RIGHT, 6, 24, 1f, 0.95f, 8,
                    VisibilityPolicy.defaults());
            case ARMOR -> new HudWidgetConfig(id, true, Anchor.BOTTOM_LEFT, 6, 60, 1f, 0.95f, 9,
                    VisibilityPolicy.defaults());
            case ACTIVE_TOOL -> new HudWidgetConfig(id, false, Anchor.BOTTOM_RIGHT, 6, 60, 1f, 0.95f, 9,
                    VisibilityPolicy.defaults());
            case MINIMAP -> new HudWidgetConfig(id, true, Anchor.TOP_RIGHT, 6, 60, 1f, 0.9f, 20,
                    VisibilityPolicy.defaults());
            case NOW_PLAYING -> new HudWidgetConfig(id, true, Anchor.BOTTOM_RIGHT, 6, 6, 1f, 0.95f, 21,
                    VisibilityPolicy.defaults());
            case TASK_STATUS -> new HudWidgetConfig(id, false, Anchor.BOTTOM_CENTER, 0, 80, 1f, 0.95f, 22,
                    VisibilityPolicy.defaults());
        };
    }

    public HudWidgetConfig withPosition(Anchor newAnchor, int newOffsetX, int newOffsetY) {
        return new HudWidgetConfig(id, enabled, newAnchor, newOffsetX, newOffsetY, scale, alpha, zIndex, visibility);
    }

    public HudWidgetConfig withEnabled(boolean value) {
        return new HudWidgetConfig(id, value, anchor, offsetX, offsetY, scale, alpha, zIndex, visibility);
    }
}

package silkvis.ui.hud;

import silkvis.ui.Rect;

/**
 * Anchor/offset resolution, safe-area clamping and snapping for HUD widgets (HUD-02).
 * Pure geometry so it can be tested without a game instance.
 */
public final class HudLayoutEngine {
    /** Reserved vanilla areas that SilkVis must not cover by default (HUD-03). */
    public static final int HOTBAR_HEIGHT = 22;
    public static final int BOSS_BAR_HEIGHT = 12;
    public static final int SUBTITLE_HEIGHT = 18;

    private HudLayoutEngine() {
    }

    /** Resolves the widget box for given content size, then clamps into the safe area. */
    public static Rect resolve(Rect screen, Rect safeArea, HudWidgetConfig cfg, int contentWidth, int contentHeight) {
        int w = Math.round(contentWidth * cfg.scale());
        int h = Math.round(contentHeight * cfg.scale());
        int availableX = safeArea.width() - w;
        int availableY = safeArea.height() - h;
        int baseX = safeArea.x() + Math.round(availableX * cfg.anchor().fractionX());
        int baseY = safeArea.y() + Math.round(availableY * cfg.anchor().fractionY());
        int x = baseX + cfg.offsetX() * cfg.anchor().offsetSignX();
        int y = baseY + cfg.offsetY() * cfg.anchor().offsetSignY();
        return Rect.clampInto(new Rect(x, y, w, h), safeArea);
    }

    /** Safe area excludes the vanilla hotbar/health band at the bottom. */
    public static Rect safeArea(Rect screen) {
        int bottomReserve = HOTBAR_HEIGHT + 24;
        return new Rect(screen.x() + 2, screen.y() + 2,
                Math.max(0, screen.width() - 4), Math.max(0, screen.height() - bottomReserve));
    }

    /** Areas SilkVis must keep clear unless the user explicitly overrides them. */
    public static Rect[] protectedAreas(Rect screen) {
        int hotbarWidth = Math.min(screen.width(), 182);
        return new Rect[] {
                new Rect((screen.width() - hotbarWidth) / 2, screen.bottom() - HOTBAR_HEIGHT - 16,
                        hotbarWidth, HOTBAR_HEIGHT + 16),
                new Rect(0, 0, screen.width(), BOSS_BAR_HEIGHT),
                new Rect(0, screen.bottom() - HOTBAR_HEIGHT - SUBTITLE_HEIGHT - 20, screen.width(), SUBTITLE_HEIGHT)
        };
    }

    public static boolean overlapsProtected(Rect widget, Rect screen) {
        for (Rect guarded : protectedAreas(screen)) {
            if (widget.intersects(guarded)) {
                return true;
            }
        }
        return false;
    }

    /** Snaps a coordinate to the configured grid (0 = no snapping). */
    public static int snap(int value, int grid) {
        if (grid <= 0) {
            return value;
        }
        return Math.round((float) value / grid) * grid;
    }

    /**
     * Returns the alignment guide offset to draw for the dragged box, or {@code null}
     * when no guide is within {@code threshold} logical units.
     */
    public static Guide alignmentGuide(Rect dragged, Rect other, int threshold) {
        Integer x = null;
        if (Math.abs(dragged.x() - other.x()) <= threshold) {
            x = other.x();
        } else if (Math.abs(dragged.right() - other.right()) <= threshold) {
            x = other.right();
        } else if (Math.abs(dragged.centerX() - other.centerX()) <= threshold) {
            x = other.centerX();
        }
        Integer y = null;
        if (Math.abs(dragged.y() - other.y()) <= threshold) {
            y = other.y();
        } else if (Math.abs(dragged.bottom() - other.bottom()) <= threshold) {
            y = other.bottom();
        } else if (Math.abs(dragged.centerY() - other.centerY()) <= threshold) {
            y = other.centerY();
        }
        return x == null && y == null ? null : new Guide(x, y);
    }

    /** Vertical or horizontal guide line in logical units; {@code null} means absent. */
    public record Guide(Integer x, Integer y) {
    }
}

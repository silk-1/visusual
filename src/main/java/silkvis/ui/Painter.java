package silkvis.ui;

/**
 * Rendering port used by widgets (ARCH-01: render adapters make no business decisions).
 * The Minecraft-specific implementation lives in {@code silkvis.render}.
 */
public interface Painter {
    void fill(Rect rect, int argb);

    void outline(Rect rect, int argb, int thickness);

    /** Draws text and returns its width in logical units. */
    int text(String text, int x, int y, int argb, boolean shadow);

    int textWidth(String text);

    int lineHeight();

    void pushClip(Rect rect);

    void popClip();

    /**
     * Optional native texture draw. The default is a no-op so that a missing texture can
     * never introduce a hard-coded colour outside the theme tokens (UI-01); callers draw
     * their own placeholder from {@link ThemeTokens}.
     */
    default void texture(String textureId, Rect rect, float alpha) {
        // intentionally empty
    }

    /**
     * Rounded rectangle built from horizontal runs (UI-02). The theme already carries
     * {@code radiusPanel}/{@code radiusControl}, but vanilla {@code fill} draws square
     * corners only, so the radius was never visible.
     *
     * <p>Corner rows are narrowed instead of blended: the shape is drawn on top of whatever
     * is already there, so a panel over a panel keeps its own colour and the backdrop shows
     * through the corners. No new render state, no shader, no extra passes.</p>
     */
    default void roundRect(Rect rect, int radius, int argb) {
        int r = Math.max(0, Math.min(radius, Math.min(rect.width(), rect.height()) / 2));
        if (r == 0) {
            fill(rect, argb);
            return;
        }
        for (int row = 0; row < r; row++) {
            int inset = cornerInset(r, row);
            int width = rect.width() - inset * 2;
            if (width <= 0) {
                continue;
            }
            fill(new Rect(rect.x() + inset, rect.y() + row, width, 1), argb);
            fill(new Rect(rect.x() + inset, rect.bottom() - 1 - row, width, 1), argb);
        }
        fill(new Rect(rect.x(), rect.y() + r, rect.width(), rect.height() - r * 2), argb);
    }

    /** Rounded panel with a one-unit border: the border colour is the outer shape. */
    default void roundPanel(Rect rect, int radius, int fillArgb, int borderArgb) {
        roundRect(rect, radius, borderArgb);
        roundRect(rect.inset(1, 1), Math.max(0, radius - 1), fillArgb);
    }

    /** Horizontal run inset for a given corner row, from the circle equation. */
    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
        return (int) Math.round(radius - dx);
    }
}

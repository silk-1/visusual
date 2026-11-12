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
}

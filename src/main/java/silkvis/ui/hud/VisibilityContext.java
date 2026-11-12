package silkvis.ui.hud;

/** Snapshot of UI state feeding {@link VisibilityPolicy}; no Minecraft objects. */
public record VisibilityContext(
        boolean debugHudHidden,
        boolean debugOverlayOpen,
        boolean chatFocused,
        boolean containerScreenOpen,
        boolean pauseMenuOpen,
        boolean spectator
) {
    public static VisibilityContext gameplay() {
        return new VisibilityContext(false, false, false, false, false, false);
    }
}

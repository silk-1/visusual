package silkvis.ui.hud;

/**
 * Visibility rules (HUD-03). Defaults hide decorative widgets with F1 and while the
 * debug overlay, chat, inventory or pause menu are open.
 */
public record VisibilityPolicy(
        boolean hideOnF1,
        boolean hideOnDebugOverlay,
        boolean hideOnChat,
        boolean hideInInventory,
        boolean hideInPauseMenu,
        boolean hideInSpectator
) {
    public static VisibilityPolicy defaults() {
        return new VisibilityPolicy(true, true, true, true, true, true);
    }

    public static VisibilityPolicy alwaysVisible() {
        return new VisibilityPolicy(false, false, false, false, false, false);
    }

    public boolean permits(VisibilityContext ctx) {
        if (hideOnF1 && ctx.debugHudHidden()) {
            return false;
        }
        if (hideOnDebugOverlay && ctx.debugOverlayOpen()) {
            return false;
        }
        if (hideOnChat && ctx.chatFocused()) {
            return false;
        }
        if (hideInInventory && ctx.containerScreenOpen()) {
            return false;
        }
        if (hideInPauseMenu && ctx.pauseMenuOpen()) {
            return false;
        }
        return !(hideInSpectator && ctx.spectator());
    }
}

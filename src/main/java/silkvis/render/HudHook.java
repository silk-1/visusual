package silkvis.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.IdentifiedLayer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import silkvis.client.SilkVisRuntime;
import silkvis.client.HudOverlayRenderer;

/**
 * Version-specific HUD hook (BUILD-04, ADR-0002).
 *
 * <p>1.21.9/1.21.10 changed the HUD registration path; the old per-frame render callback
 * is not assumed. This adapter registers one identified layer after the vanilla HUD and
 * degrades to a status message when the callback is unavailable.</p>
 *
 * <p>If the mapped API differs in the pinned version, only this class needs changing:
 * the renderer behind it is plain {@link silkvis.ui.Painter} code.</p>
 */
public final class HudHook {
    public static final Identifier LAYER_ID = Identifier.of("silkvis", "hud_overlay");
    private static boolean registered;

    private HudHook() {
    }

    public static boolean register() {
        if (registered) {
            return true;
        }
        try {
            HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerAfter(
                    IdentifiedLayer.MISC_OVERLAYS, LAYER_ID, HudHook::renderLayer));
            registered = true;
            SilkVisRuntime.get().integrations().probe("silkvis:hud_hook", () -> true);
            return true;
        } catch (RuntimeException | LinkageError e) {
            SilkVisRuntime.get().integrations().probe("silkvis:hud_hook", () -> false);
            SilkVisRuntime.get().log().log("CAPABILITY_UNSUPPORTED", "hud_layer_callback");
            return false;
        }
    }

    private static void renderLayer(DrawContext context, RenderTickCounter tickCounter) {
        HudOverlayRenderer.render(context, tickCounter);
    }

    public static boolean available() {
        return registered;
    }
}

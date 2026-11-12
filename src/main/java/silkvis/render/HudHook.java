package silkvis.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import silkvis.client.HudOverlayRenderer;
import silkvis.client.SilkVisRuntime;

/**
 * Version-specific HUD hook (BUILD-04, ADR-0002).
 *
 * <p>1.21.9/1.21.10 replaced the old per-frame HUD callback with an identified element
 * registry. Verified against Fabric API 1.21.10
 * ({@code net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry}):
 * {@code attachElementAfter(afterThis, identifier, element)}.</p>
 *
 * <p>The element is attached after {@link VanillaHudElements#MISC_OVERLAYS}, so SilkVis
 * draws before the crosshair, hotbar, status bars and boss bar and inherits the vanilla
 * {@code hudHidden} render condition (F1 hides it automatically, HUD-03).</p>
 *
 * <p>If the mapped API differs in the pinned version, only this class needs changing:
 * everything behind it is plain {@link silkvis.ui.Painter} code.</p>
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
            HudElement element = HudHook::render;
            HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, LAYER_ID, element);
            registered = true;
            SilkVisRuntime.get().integrations().probe("silkvis:hud_hook", () -> true);
            return true;
        } catch (RuntimeException | LinkageError e) {
            SilkVisRuntime.get().integrations().probe("silkvis:hud_hook", () -> false);
            SilkVisRuntime.get().log().log("CAPABILITY_UNSUPPORTED", "hud_element_registry");
            return false;
        }
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        HudOverlayRenderer.render(context, tickCounter);
    }

    public static boolean available() {
        return registered;
    }
}

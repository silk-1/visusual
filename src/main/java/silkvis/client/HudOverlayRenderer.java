package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import silkvis.hud.HudViewModel;
import silkvis.map.MapIdentity;
import silkvis.map.MapService;
import silkvis.media.MediaService;
import silkvis.media.MediaSnapshot;
import silkvis.media.MediaTimeline;
import silkvis.render.VanillaPainter;
import silkvis.ui.Color;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;
import silkvis.ui.hud.Anchor;
import silkvis.ui.hud.HudLayoutConfig;
import silkvis.ui.hud.HudLayoutEngine;
import silkvis.ui.hud.HudWidgetConfig;
import silkvis.ui.hud.HudWidgetId;
import silkvis.ui.hud.VisibilityContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * HUD overlay (HUD-01..HUD-04).
 *
 * <p>Rendering reads only cached data: the immutable {@link HudViewModel}, the media
 * snapshot and the tile cache. Visibility follows the per-widget policy and the overlay
 * never covers the hotbar/health band unless the user overrode it in the editor.</p>
 */
public final class HudOverlayRenderer {
    private HudOverlayRenderer() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        SilkVisRuntime runtime = SilkVisRuntime.get();
        if (client.currentScreen != null && !runtime.hudInteractionMode()) {
            return;
        }
        VanillaPainter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();
        Rect screen = new Rect(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        Rect safe = HudLayoutEngine.safeArea(screen);
        HudLayoutConfig layout = runtime.hudLayout();
        HudViewModel model = SilkVisClient.hudModel();
        VisibilityContext visibility = new VisibilityContext(
                false,
                client.getDebugHud().shouldShowDebugHud(),
                false,
                false,
                client.currentScreen != null,
                client.player.isSpectator());

        List<HudWidgetConfig> enabled = new ArrayList<>();
        for (HudWidgetConfig cfg : layout.byId().values()) {
            if (cfg.enabled() && cfg.id().p1() && cfg.visibility().permits(visibility)) {
                enabled.add(cfg);
            }
        }
        // Top-down instead of z-order: with no overlap the z-order stops mattering, and a
        // widget higher on the screen keeps its slot while the ones below shift down. Saved
        // layouts from before 2026-11-15 carry single-line offsets, so coordinates used to
        // paint on top of FPS; this repairs existing configs without a migration.
        enabled.sort(Comparator.comparingInt(HudWidgetConfig::offsetY)
                .thenComparingInt(HudWidgetConfig::zIndex));

        List<Rect> occupied = new ArrayList<>();
        for (HudWidgetConfig cfg : enabled) {
            List<String> lines = model.lines(cfg.id());
            if (cfg.id() == HudWidgetId.NOW_PLAYING) {
                renderNowPlaying(painter, theme, screen, safe, cfg, runtime, occupied);
                continue;
            }
            if (cfg.id() == HudWidgetId.MINIMAP) {
                Optional<MapIdentity> identity = runtime.mapIdentity();
                Optional<MapService> map = runtime.mapService();
                if (identity.isEmpty() || map.isEmpty()) {
                    continue;
                }
                int size = Math.min(128, Math.min(screen.width(), screen.height()) / 3);
                Rect box = avoidOverlap(HudLayoutEngine.resolve(screen, safe, cfg, size, size),
                        occupied, screen);
                occupied.add(box);
                MinimapRenderer.render(painter, box, theme, map.get(), identity.get(),
                        runtime.ui().map(), client,
                        net.minecraft.text.Text.translatable("silkvis.map.no_tiles").getString());
                continue;
            }
            if (lines.isEmpty()) {
                continue;
            }
            renderPanel(painter, theme, screen, safe, cfg, lines, occupied);
        }
    }

    /**
     * Pushes a widget down until it no longer intersects one already placed. Bounded: after
     * a few attempts (or when it would leave the screen) the original box is returned, so a
     * dense layout can never make widgets run away or disappear entirely.
     */
    private static Rect avoidOverlap(Rect box, List<Rect> occupied, Rect screen) {
        Rect current = box;
        for (int guard = 0; guard < 8; guard++) {
            Rect hit = null;
            for (Rect other : occupied) {
                if (current.intersects(other)) {
                    hit = other;
                    break;
                }
            }
            if (hit == null) {
                return current;
            }
            int shift = hit.bottom() + 2 - current.y();
            if (shift <= 0) {
                return current;
            }
            current = current.translate(0, shift);
            if (current.bottom() > screen.bottom()) {
                return box;
            }
        }
        return current;
    }

    private static void renderPanel(VanillaPainter painter, ThemeTokens theme, Rect screen, Rect safe,
                                    HudWidgetConfig cfg, List<String> lines, List<Rect> occupied) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, painter.textWidth(line));
        }
        int height = lines.size() * painter.lineHeight() + Math.max(0, lines.size() - 1) * 2;
        Rect box = avoidOverlap(HudLayoutEngine.resolve(screen, safe, cfg, width + 8, height + 6),
                occupied, screen);
        occupied.add(box);
        painter.roundRect(box, theme.radiusControl(),
                Color.argb(theme.surface().withAlpha(theme.surface().alpha() * 0.55f * cfg.alpha())));
        int y = box.y() + 3;
        for (String line : lines) {
            painter.text(line, box.x() + 4, y, Color.argb(theme.textPrimary().withAlpha(cfg.alpha())), true);
            y += painter.lineHeight() + 2;
        }
    }

    private static void renderNowPlaying(VanillaPainter painter, ThemeTokens theme, Rect screen, Rect safe,
                                         HudWidgetConfig cfg, SilkVisRuntime runtime, List<Rect> occupied) {
        Optional<MediaService> service = runtime.mediaService();
        if (service.isEmpty()) {
            return;
        }
        Optional<MediaSnapshot> snapshotOpt = service.get().snapshot();
        if (snapshotOpt.isEmpty()) {
            return;
        }
        MediaSnapshot snapshot = snapshotOpt.get();
        int width = 240;
        int height = 56;
        Rect box = avoidOverlap(HudLayoutEngine.resolve(screen, safe, cfg, width, height), occupied, screen);
        occupied.add(box);
        painter.roundPanel(box, theme.radiusControl(),
                Color.argb(theme.surface().withAlpha(theme.surface().alpha() * 0.7f * cfg.alpha())),
                Color.argb(theme.border()));
        Rect art = new Rect(box.x() + 4, box.y() + 4, 48, 48);
        // Artwork upload is pending (docs/known-limitations.md): neutral placeholder is
        // shown instead of a foreign cover.
        painter.fill(art, Color.argb(theme.surfaceHover()));
        int textX = art.right() + 6;
        String title = snapshot.titleOpt().orElse("—");
        painter.text(ellipsize(painter, title, width - 60), textX, box.y() + 6,
                Color.argb(theme.textPrimary()), false);
        if (snapshot.artistOpt().isPresent()) {
            painter.text(ellipsize(painter, snapshot.artistOpt().get(), width - 60), textX, box.y() + 18,
                    Color.argb(theme.textSecondary()), false);
        }
        painter.text(snapshot.displaySource(), textX, box.y() + 30, Color.argb(theme.textSecondary()), false);
        long position = MediaTimeline.projectPosition(snapshot, runtime.clock().monotonicNanos(), runtime.clock())
                .orElse(0L);
        String progress = MediaTimeline.formatPosition(position) + " / " + MediaTimeline.describeLength(snapshot);
        painter.text(progress, textX, box.y() + 42, Color.argb(theme.textSecondary()), false);
    }

    private static String ellipsize(VanillaPainter painter, String text, int maxWidth) {
        if (painter.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (painter.textWidth(sb.toString() + c + ellipsis) > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }
}

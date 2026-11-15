package silkvis.client;

import net.minecraft.client.MinecraftClient;
import silkvis.map.MapIdentity;
import silkvis.map.MapService;
import silkvis.map.MapSettings;
import silkvis.map.MinimapView;
import silkvis.map.TileKey;
import silkvis.ui.hud.HudWidgetId;

import java.util.List;
import java.util.Optional;

/**
 * Keeps the tiles the minimap is looking at requested (MAP-03, MAP-06).
 *
 * <p>Rendering must never sample: the renderer only reads the cache. But nothing else was
 * asking the service to build anything, so the minimap showed {@code «Нет тайлов»} forever
 * (reported 2026-11-15). This class closes that loop: every other second it marks the
 * visible tiles that are not in the cache yet as dirty, and {@code MapService.tick()}
 * samples them within its 1 ms/tick budget.</p>
 *
 * <p>Deliberately conservative: the minimap is a convenience, not a reason to burn CPU.
 * Cached tiles are never re-requested, so a standing still player costs nothing.</p>
 */
final class MinimapPrefetcher {
    /** 20 ticks/s / 10 = at most two passes per second. */
    private static final int PASS_EVERY_TICKS = 10;
    /** Hard cap per pass: the view is bounded anyway, this is belt and braces. */
    private static final int MAX_TILES_PER_PASS = 16;

    private int ticks;

    void tick(SilkVisRuntime runtime, MinecraftClient client) {
        if (++ticks < PASS_EVERY_TICKS) {
            return;
        }
        ticks = 0;
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        if (!runtime.hudLayout().get(HudWidgetId.MINIMAP).enabled()) {
            return;
        }
        Optional<MapService> service = runtime.mapService();
        Optional<MapIdentity> identity = runtime.mapIdentity();
        if (service.isEmpty() || identity.isEmpty()) {
            return;
        }
        MapService map = service.get();
        if (map.paused()) {
            return;
        }
        MapSettings settings = runtime.ui().map();
        // Same geometry the renderer uses, otherwise prefetch and draw disagree about LOD.
        int size = Math.min(128, Math.min(client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight()) / 3);
        MinimapView view = new MinimapView(settings, client.player.getBlockX(),
                client.player.getBlockZ(), size);
        List<TileKey> keys = view.visibleTiles(identity.get());
        int requested = 0;
        for (TileKey key : keys) {
            if (requested >= MAX_TILES_PER_PASS) {
                break;
            }
            if (map.view(key).isPresent()) {
                continue;
            }
            map.markDirty(key);
            requested++;
        }
    }
}

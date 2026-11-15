package silkvis.client;

import net.minecraft.client.MinecraftClient;
import silkvis.map.MapIdentity;
import silkvis.map.MapService;
import silkvis.map.MapSettings;
import silkvis.map.MinimapView;
import silkvis.map.TileKey;
import silkvis.map.TileMath;
import silkvis.ui.hud.HudWidgetId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Asks {@link MapService} to build the tiles that are about to be looked at (MAP-01, MAP-03).
 *
 * <p>Rendering never samples: the renderer only reads the cache. But nothing else was
 * asking the service to build anything, so both the minimap and the full map stayed empty
 * forever (reported 2026-11-15). This class closes the loop; {@code MapService.tick()}
 * then samples the requested tiles inside its 1 ms/tick budget.</p>
 *
 * <p>Deliberately conservative: cached tiles are never re-requested, per-pass counts are
 * capped, and a player standing still costs nothing. The minimap asks twice a second, the
 * map screen at most every 400 ms, always nearest-to-centre first — so the area under the
 * crosshair fills before the corners.</p>
 */
final class MapPrefetcher {
    /** 20 ticks/s / 10 → at most two minimap passes per second. */
    private static final int PASS_EVERY_TICKS = 10;
    /** Hard cap per pass: views are bounded anyway, this is belt and braces. */
    private static final int MAX_TILES_PER_PASS = 16;
    private static final long AREA_INTERVAL_NANOS = 400_000_000L;

    /** Shared: the map screen renders at frame rate, requests are rate-limited here. */
    private static long nextAreaRequestAt;

    private int ticks;

    /** Minimap pass, called once per client tick. */
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
        request(map, view.visibleTiles(identity.get()), client.player.getBlockX(),
                client.player.getBlockZ());
    }

    /**
     * Rectangular area request used by the full-screen map: the map pans and zooms freely,
     * so it cannot reuse {@link MinimapView}, which is anchored on the player.
     */
    static void requestArea(MapService service, MapIdentity identity, int minBlockX,
                            int minBlockZ, int spanX, int spanZ, int blocksPerPixel) {
        if (service == null || identity == null || service.paused()) {
            return;
        }
        long now = System.nanoTime();
        if (now - nextAreaRequestAt < 0) {
            return;
        }
        nextAreaRequestAt = now + AREA_INTERVAL_NANOS;
        int lod = TileMath.lodForBlocksPerPixel(blocksPerPixel);
        int firstX = TileMath.tileCoordOf(minBlockX, lod);
        int lastX = TileMath.tileCoordOf(minBlockX + Math.max(0, spanX) - 1, lod);
        int firstZ = TileMath.tileCoordOf(minBlockZ, lod);
        int lastZ = TileMath.tileCoordOf(minBlockZ + Math.max(0, spanZ) - 1, lod);
        List<TileKey> keys = new ArrayList<>();
        for (int tz = firstZ; tz <= lastZ; tz++) {
            for (int tx = firstX; tx <= lastX; tx++) {
                keys.add(new TileKey(identity, lod, tx, tz));
            }
        }
        request(service, keys, minBlockX + spanX / 2, minBlockZ + spanZ / 2);
    }

    /** Marks the missing tiles dirty, nearest to the focus point first. */
    private static void request(MapService service, List<TileKey> keys, int focusBlockX,
                                int focusBlockZ) {
        List<TileKey> missing = new ArrayList<>();
        for (TileKey key : keys) {
            if (service.view(key).isEmpty()) {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        missing.sort(Comparator.comparingLong(key -> {
            long dx = key.originBlockX() + TileKey.TILE_SIZE / 2L - focusBlockX;
            long dz = key.originBlockZ() + TileKey.TILE_SIZE / 2L - focusBlockZ;
            return dx * dx + dz * dz;
        }));
        int requested = 0;
        for (TileKey key : missing) {
            if (requested >= MAX_TILES_PER_PASS) {
                break;
            }
            service.markDirty(key);
            requested++;
        }
    }
}

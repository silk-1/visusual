package silkvis.client;

import net.minecraft.client.MinecraftClient;
import silkvis.map.MapIdentity;
import silkvis.map.MapService;
import silkvis.map.MapTile;
import silkvis.map.MapSettings;
import silkvis.map.MinimapView;
import silkvis.map.TileKey;
import silkvis.map.TileMath;
import silkvis.render.VanillaPainter;
import silkvis.ui.Color;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;

import java.util.Optional;

/**
 * Minimap renderer (MAP-03, MAP-04).
 *
 * <p>Draws from the immutable tile cache only; no sampling, disk or network access
 * happens during rendering. Output is deliberately downscaled (48x48 quads) because a
 * textured upload path is still pending (see docs/known-limitations.md).</p>
 */
public final class MinimapRenderer {
    private static final int OUTPUT = 48;

    private MinimapRenderer() {
    }

    public static void render(VanillaPainter painter, Rect bounds, ThemeTokens theme,
                              MapService mapService, MapIdentity identity, MapSettings settings,
                              MinecraftClient client) {
        painter.fill(bounds, Color.argb(theme.surface().withAlpha(0.72f)));
        if (client.player == null || mapService == null) {
            drawPlaceholder(painter, bounds, theme);
            return;
        }
        int centerX = client.player.getBlockX();
        int centerZ = client.player.getBlockZ();
        MinimapView view = new MinimapView(settings, centerX, centerZ, bounds.width());
        int lod = view.lod();
        // Map one output pixel to one block-column at the current zoom.
        int outputBlocks = view.visibleSpanBlocks();
        int blocksPerOutput = Math.max(1, outputBlocks / OUTPUT);
        int cell = Math.max(1, bounds.width() / OUTPUT);
        int drawn = 0;
        for (int oz = 0; oz < OUTPUT; oz++) {
            for (int ox = 0; ox < OUTPUT; ox++) {
                int blockX = view.minBlockX() + ox * blocksPerOutput;
                int blockZ = view.minBlockZ() + oz * blocksPerOutput;
                TileKey key = TileMath.tileOf(identity, lod, blockX, blockZ);
                Optional<MapTile> tile = mapService.view(key);
                if (tile.isEmpty()) {
                    continue;
                }
                int px = TileMath.pixelInTile(blockX, lod);
                int pz = TileMath.pixelInTile(blockZ, lod);
                int color = tile.get().colorAt(px, pz);
                painter.fill(new Rect(bounds.x() + ox * cell, bounds.y() + oz * cell, cell, cell), color);
                drawn++;
            }
        }
        if (drawn == 0) {
            drawPlaceholder(painter, bounds, theme);
        }
        // Player heading marker (depth-aware by construction: it is the player position).
        int cx = bounds.x() + bounds.width() / 2;
        int cy = bounds.y() + bounds.height() / 2;
        painter.fill(new Rect(cx - 1, cy - 1, 3, 3), Color.argb(theme.accent()));
        if (settings.showCardinals()) {
            painter.text("N", bounds.centerX() - painter.textWidth("N") / 2, bounds.y() + 2,
                    Color.argb(theme.textPrimary()), true);
        }
        painter.outline(bounds, Color.argb(theme.border()), 1);
    }

    private static void drawPlaceholder(VanillaPainter painter, Rect bounds, ThemeTokens theme) {
        painter.fill(bounds, Color.argb(theme.surface().withAlpha(0.85f)));
        String text = "map";
        painter.text(text, bounds.centerX() - painter.textWidth(text) / 2,
                bounds.centerY() - painter.lineHeight() / 2, Color.argb(theme.textSecondary()), false);
    }
}

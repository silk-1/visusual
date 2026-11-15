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
                              MinecraftClient client, String emptyLabel, String northLabel) {
        painter.roundPanel(bounds, theme.radiusControl(),
                Color.argb(theme.surface().withAlpha(0.72f)), Color.argb(theme.border()));
        if (client.player == null || mapService == null) {
            drawPlaceholder(painter, bounds, theme, emptyLabel);
            return;
        }
        int centerX = client.player.getBlockX();
        int centerZ = client.player.getBlockZ();
        MinimapView view = new MinimapView(settings, centerX, centerZ, bounds.width());
        int lod = view.lod();
        // Map one output cell to a block column at the current zoom. Cell edges are placed
        // proportionally, so 48 cells fill the whole square exactly instead of leaving a
        // 32 px strip of empty panel on the right and bottom.
        int outputBlocks = view.visibleSpanBlocks();
        int drawn = 0;
        for (int oz = 0; oz < OUTPUT; oz++) {
            int cellY0 = bounds.y() + (oz * bounds.height()) / OUTPUT;
            int cellY1 = bounds.y() + ((oz + 1) * bounds.height()) / OUTPUT;
            int blockZ = view.minBlockZ() + (oz * outputBlocks) / OUTPUT;
            for (int ox = 0; ox < OUTPUT; ox++) {
                int blockX = view.minBlockX() + (ox * outputBlocks) / OUTPUT;
                TileKey key = TileMath.tileOf(identity, lod, blockX, blockZ);
                Optional<MapTile> tile = mapService.view(key);
                if (tile.isEmpty()) {
                    continue;
                }
                int px = TileMath.pixelInTile(blockX, lod);
                int pz = TileMath.pixelInTile(blockZ, lod);
                int color = tile.get().colorAt(px, pz);
                int cellX0 = bounds.x() + (ox * bounds.width()) / OUTPUT;
                int cellX1 = bounds.x() + ((ox + 1) * bounds.width()) / OUTPUT;
                painter.fill(new Rect(cellX0, cellY0, Math.max(1, cellX1 - cellX0),
                        Math.max(1, cellY1 - cellY0)), color);
                drawn++;
            }
        }
        if (drawn == 0) {
            drawPlaceholder(painter, bounds, theme, emptyLabel);
        }
        // Player heading marker (depth-aware by construction: it is the player position).
        int cx = bounds.x() + bounds.width() / 2;
        int cy = bounds.y() + bounds.height() / 2;
        painter.fill(new Rect(cx - 1, cy - 1, 3, 3), Color.argb(theme.accent()));
        if (settings.showCardinals() && northLabel != null && !northLabel.isEmpty()) {
            painter.text(northLabel, bounds.centerX() - painter.textWidth(northLabel) / 2,
                    bounds.y() + 4, Color.argb(theme.textPrimary()), true);
        }
    }

    /**
     * No tiles yet (the GPU upload path is still pending, docs/known-limitations.md). The
     * old placeholder printed the literal word "map", which read like a bug; the caller now
     * passes a localized line instead.
     */
    private static void drawPlaceholder(VanillaPainter painter, Rect bounds, ThemeTokens theme, String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        painter.text(label, bounds.centerX() - painter.textWidth(label) / 2,
                bounds.centerY() - painter.lineHeight() / 2, Color.argb(theme.textSecondary()), false);
    }
}

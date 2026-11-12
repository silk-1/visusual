package silkvis.map;

/**
 * Pure geometry for the minimap (MAP-03): which tiles cover the visible square, the
 * scale factor and the player marker position. Rendering itself lives in the client
 * adapter layer.
 */
public final class MinimapView {
    private final MapSettings settings;
    private final int centerBlockX;
    private final int centerBlockZ;
    private final int pixels;

    public MinimapView(MapSettings settings, int centerBlockX, int centerBlockZ, int pixels) {
        this.settings = settings;
        this.centerBlockX = centerBlockX;
        this.centerBlockZ = centerBlockZ;
        this.pixels = Math.max(16, pixels);
    }

    public int pixels() {
        return pixels;
    }

    public int blocksPerPixel() {
        return Math.max(1, settings.zoom());
    }

    public int visibleSpanBlocks() {
        return TileMath.minimapBlockSpan(pixels, blocksPerPixel());
    }

    public int lod() {
        return TileMath.lodForBlocksPerPixel(blocksPerPixel());
    }

    public int minBlockX() {
        return centerBlockX - visibleSpanBlocks() / 2;
    }

    public int minBlockZ() {
        return centerBlockZ - visibleSpanBlocks() / 2;
    }

    public MapIdentity identity() {
        return new MapIdentity("view", "minecraft:overworld", MapIdentity.FORMAT_VERSION, MapMode.SURFACE, 0);
    }

    /**
     * Lists the tiles intersecting the view. Bounded by construction: the number of
     * tiles is at most (span / tileSpan + 2)^2, and never grows with zoom-out.
     */
    public java.util.List<TileKey> visibleTiles(MapIdentity identity) {
        int lod = lod();
        java.util.LinkedHashSet<TileKey> keys = new java.util.LinkedHashSet<>();
        int firstX = TileMath.tileCoordOf(minBlockX(), lod);
        int lastX = TileMath.tileCoordOf(minBlockX() + visibleSpanBlocks() - 1, lod);
        int firstZ = TileMath.tileCoordOf(minBlockZ(), lod);
        int lastZ = TileMath.tileCoordOf(minBlockZ() + visibleSpanBlocks() - 1, lod);
        for (int tz = firstZ; tz <= lastZ; tz++) {
            for (int tx = firstX; tx <= lastX; tx++) {
                keys.add(new TileKey(identity, lod, tx, tz));
            }
        }
        return new java.util.ArrayList<>(keys);
    }

    /** Player position inside the minimap square for the given rendering mode. */
    public float playerPixelX(float yawDegrees) {
        if (settings.northUp()) {
            return pixels / 2f;
        }
        return pixels / 2f;
    }

    public float playerPixelZ(float yawDegrees) {
        return pixels / 2f;
    }

    /** Screen-space rotation applied when the map is player-up. */
    public float mapRotationDegrees(float yawDegrees) {
        return settings.northUp() ? 0f : -yawDegrees;
    }
}

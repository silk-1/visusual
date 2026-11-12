package silkvis.map;

/** Coordinate conversions in block space; handles negative coordinates without overflow. */
public final class TileMath {
    private TileMath() {
    }

    public static int tileCoordOf(int block, int lod) {
        long span = (long) TileKey.TILE_SIZE * (1L << lod);
        return (int) Math.floorDiv((long) block, span);
    }

    /** Pixel index inside a tile (0..127). */
    public static int pixelInTile(int block, int lod) {
        long span = (long) TileKey.TILE_SIZE * (1L << lod);
        return (int) Math.floorMod((long) block, span) / (1 << lod);
    }

    public static TileKey tileOf(MapIdentity identity, int lod, int blockX, int blockZ) {
        return new TileKey(identity, lod, tileCoordOf(blockX, lod), tileCoordOf(blockZ, lod));
    }

    /** Index into the flat 128x128 array. */
    public static int index(int px, int pz) {
        return pz * TileKey.TILE_SIZE + px;
    }

    /** Block distance covered by a minimap of {@code pixels} at the given zoom. */
    public static int minimapBlockSpan(int pixels, int blocksPerPixel) {
        return pixels * Math.max(1, blocksPerPixel);
    }

    /**
     * Chooses the LOD whose pixel is closest to, but not larger than, the requested
     * blocks-per-pixel. Zoom 1 -> LOD 0, zoom 2..3 -> LOD 1, etc.
     */
    public static int lodForBlocksPerPixel(int blocksPerPixel) {
        int bpp = Math.max(1, blocksPerPixel);
        int lod = 0;
        while ((1 << (lod + 1)) <= bpp && lod < 6) {
            lod++;
        }
        return lod;
    }
}

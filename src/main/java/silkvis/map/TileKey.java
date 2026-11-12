package silkvis.map;

/**
 * One 128x128 tile at a given LOD (MAP-06). LOD 0 = one tile pixel per block;
 * LOD n = 2^n blocks per pixel.
 */
public record TileKey(MapIdentity identity, int lod, int tileX, int tileZ) {
    public static final int TILE_SIZE = 128;

    public TileKey {
        if (lod < 0 || lod > 6) {
            throw new IllegalArgumentException("lod out of range: " + lod);
        }
    }

    public int blocksPerPixel() {
        return 1 << lod;
    }

    /** World-space origin of the tile's north-west corner. */
    public int originBlockX() {
        return tileX * TILE_SIZE * blocksPerPixel();
    }

    public int originBlockZ() {
        return tileZ * TILE_SIZE * blocksPerPixel();
    }

    public int spanBlocks() {
        return TILE_SIZE * blocksPerPixel();
    }

    public TileKey parent() {
        if (lod == 0) {
            return null;
        }
        return new TileKey(identity, lod - 1,
                Math.floorDiv(tileX, 2), Math.floorDiv(tileZ, 2));
    }

    public String storageName() {
        return "t" + tileX + "_" + tileZ + "_l" + lod + ".svt";
    }

    /** Coalescing key for the dirty queue (ARCH-05): same tile collapses to one job. */
    public String dirtyKey() {
        return identity.storageKey() + "|" + lod + "|" + tileX + "|" + tileZ;
    }
}

package silkvis.map;

import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Immutable terrain tile: top-down ARGB colours plus an optional height (or slice
 * shading) plane, a revision and a CRC32 checksum of the payload (MAP-06, MAP-08).
 */
public final class MapTile {
    public static final int SIZE = TileKey.TILE_SIZE;

    private final TileKey key;
    private final int[] colors;
    private final short[] heights;
    private final long revision;
    private final long checksum;

    public MapTile(TileKey key, int[] colors, short[] heights, long revision) {
        if (key == null) {
            throw new IllegalArgumentException("key required");
        }
        if (colors == null || colors.length != SIZE * SIZE) {
            throw new IllegalArgumentException("colors must be " + (SIZE * SIZE) + " entries");
        }
        if (heights != null && heights.length != SIZE * SIZE) {
            throw new IllegalArgumentException("heights must be " + (SIZE * SIZE) + " entries");
        }
        this.key = key;
        this.colors = colors;
        this.heights = heights;
        this.revision = revision;
        this.checksum = computeChecksum(key, colors, heights);
    }

    public TileKey key() {
        return key;
    }

    public long revision() {
        return revision;
    }

    public long checksum() {
        return checksum;
    }

    public boolean hasHeights() {
        return heights != null;
    }

    public int colorAt(int px, int pz) {
        return colors[TileMath.index(clamp(px), clamp(pz))];
    }

    public int heightAt(int px, int pz) {
        return heights == null ? 0 : heights[TileMath.index(clamp(px), clamp(pz))];
    }

    public int[] copyColors() {
        return colors.clone();
    }

    public short[] copyHeights() {
        return heights == null ? null : heights.clone();
    }

    /** Approximate retained bytes, used by the bounded cache (MAP-07). */
    public int byteSize() {
        return colors.length * 4 + (heights == null ? 0 : heights.length * 2) + 64;
    }

    public MapTile withRevision(long newRevision) {
        return new MapTile(key, colors, heights, newRevision);
    }

    /** Derives a parent (lower LOD) tile by 2x2 averaging of colours and heights. */
    public MapTile downsample() {
        TileKey parentKey = key.parent();
        if (parentKey == null) {
            return null;
        }
        int[] out = new int[SIZE * SIZE];
        short[] outHeights = heights == null ? null : new short[SIZE * SIZE];
        for (int pz = 0; pz < SIZE; pz++) {
            for (int px = 0; px < SIZE; px++) {
                int c00 = colorAt(px * 2, pz * 2);
                int c10 = colorAt(px * 2 + 1, pz * 2);
                int c01 = colorAt(px * 2, pz * 2 + 1);
                int c11 = colorAt(px * 2 + 1, pz * 2 + 1);
                out[TileMath.index(px, pz)] = average(c00, c10, c01, c11);
                if (outHeights != null) {
                    int sum = heightAt(px * 2, pz * 2) + heightAt(px * 2 + 1, pz * 2)
                            + heightAt(px * 2, pz * 2 + 1) + heightAt(px * 2 + 1, pz * 2 + 1);
                    outHeights[TileMath.index(px, pz)] = (short) (sum / 4);
                }
            }
        }
        return new MapTile(parentKey, out, outHeights, revision);
    }

    private static int average(int a, int b, int c, int d) {
        int alpha = (((a >>> 24) & 0xFF) + ((b >>> 24) & 0xFF) + ((c >>> 24) & 0xFF) + ((d >>> 24) & 0xFF)) / 4;
        int red = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF) + ((c >> 16) & 0xFF) + ((d >> 16) & 0xFF)) / 4;
        int green = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF) + ((c >> 8) & 0xFF) + ((d >> 8) & 0xFF)) / 4;
        int blue = ((a & 0xFF) + (b & 0xFF) + (c & 0xFF) + (d & 0xFF)) / 4;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(SIZE - 1, v));
    }

    static long computeChecksum(TileKey key, int[] colors, short[] heights) {
        CRC32 crc = new CRC32();
        crc.update(key.storageName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (int color : colors) {
            crc.update(color);
            crc.update(color >>> 8);
            crc.update(color >>> 16);
            crc.update(color >>> 24);
        }
        if (heights != null) {
            for (short h : heights) {
                crc.update(h);
                crc.update(h >>> 8);
            }
        }
        return crc.getValue();
    }

    @Override
    public String toString() {
        return "MapTile[" + key.storageName() + ", rev=" + revision + ", bytes=" + byteSize() + "]";
    }

    /** Structural equality on payload; used by tests and cache dedup. */
    public boolean sameContent(MapTile other) {
        return other != null && other.key.equals(key) && Arrays.equals(other.colors, colors)
                && Arrays.equals(other.heights, heights);
    }
}

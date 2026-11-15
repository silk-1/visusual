package silkvis.hud.extra;

/** The literal formula requested by the HUD extras contract. */
public final class SlimeChunk {
    private SlimeChunk() {
    }

    /**
     * Preserves Java int overflow and promotion order exactly as specified.
     * The supplied formula omits worldSeed; this parameter is deliberately unused.
     * Consequently this literal contract is not a seed-dependent vanilla predicate.
     */
    public static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
        long l = (long) (chunkX * chunkX * 4987142 + chunkX * 5947611
                + chunkZ * chunkZ * 4392871L + chunkZ * 389711) ^ 987234911L;
        return new java.util.Random(l).nextInt(10) == 0;
    }

    /** Packs floor-divided chunk X in the low 32 bits and chunk Z in the high 32 bits. */
    public static long chunkIndexOf(int blockX, int blockZ) {
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }
}

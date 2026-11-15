package silkvis.automation;

/**
 * Immutable integer block position. Deliberately not the Minecraft class: the search, the plans
 * and the self checks all use this, and only the executor converts to {@code net.minecraft} when
 * it emits input. Blocks with negative coordinates hash correctly (no {@code x | y << 8} tricks).
 */
public record BlockPos(int x, int y, int z) implements Comparable<BlockPos> {
    @Override
    public int compareTo(BlockPos other) {
        int byY = Integer.compare(y, other.y);
        if (byY != 0) {
            return byY;
        }
        int byX = Integer.compare(x, other.x);
        return byX != 0 ? byX : Integer.compare(z, other.z);
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public long distanceSquared(BlockPos other) {
        long dx = (long) x - other.x;
        long dy = (long) y - other.y;
        long dz = (long) z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Squared horizontal distance: enough for "is it the same column" tests. */
    public long horizontalDistanceSquared(BlockPos other) {
        long dx = (long) x - other.x;
        long dz = (long) z - other.z;
        return dx * dx + dz * dz;
    }
}

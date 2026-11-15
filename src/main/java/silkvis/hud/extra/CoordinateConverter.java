package silkvis.hud.extra;

/** Integer portal coordinates; clamping is explicit rather than silently changing conversions. */
public final class CoordinateConverter {
    public static final long WORLD_BORDER = 29_999_984L;

    private CoordinateConverter() {
    }

    /** Floors toward negative infinity, including coordinates west/north of zero. */
    public static long toNether(long overworld) {
        return Math.floorDiv(overworld, 8L);
    }

    /** Multiplies exactly; throws ArithmeticException if a long cannot hold the result. */
    public static long toOverworld(long nether) {
        return Math.multiplyExact(nether, 8L);
    }

    /** Limits either horizontal coordinate to the supported world border. */
    public static long clampToWorldBorder(long coord) {
        return Math.max(-WORLD_BORDER, Math.min(WORLD_BORDER, coord));
    }

    /** Returns the fixed coordinate text contract, independent of the default locale. */
    public static String format(int x, int y, int z) {
        return "X " + x + " | Y " + y + " | Z " + z;
    }

    /** Accepts Overworld X/Z and reports its Nether destination alongside the original coordinates. */
    public static String portals(int x, int z) {
        return "\u0410\u0434: " + toNether(x) + " " + toNether(z)
                + " \u00b7 \u0412\u0435\u0440\u0445: " + x + " " + z;
    }
}

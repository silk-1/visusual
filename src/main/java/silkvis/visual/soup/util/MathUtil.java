package silkvis.visual.soup.util;

/**
 * Subset of SoupVisuals {@code padej.soup.base.util.math.MathUtil} (Padej_).
 *
 * <p>{@link #interpolate(float, float)} uses the game's tick progress; the value is passed
 * in by the caller so this class stays free of {@code net.minecraft.*}.</p>
 */
public final class MathUtil {
    private MathUtil() {
    }

    /** Frame interpolation between the previous and the current tick position. */
    public static double interpolate(double previous, double current, double tickProgress) {
        return previous + (current - previous) * tickProgress;
    }

    public static float interpolate(float previous, float current, float tickProgress) {
        return previous + (current - previous) * tickProgress;
    }

    public static double round(double value, int places) {
        double factor = Math.pow(10.0, places);
        return Math.round(value * factor) / factor;
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}

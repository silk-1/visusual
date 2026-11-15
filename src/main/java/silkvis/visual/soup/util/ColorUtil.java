package silkvis.visual.soup.util;

/**
 * Subset of SoupVisuals {@code padej.soup.base.util.color.ColorUtil} (Padej_) that the
 * ported particle modules use: ARGB packing, alpha multiplication, gradient blending and
 * "the client colour".
 *
 * <p>Package note: SoupVisuals stores colours as {@code int} ARGB and takes them from its
 * theme module. SilkVis forbids {@code net.minecraft.*} imports outside the render
 * adapter, so the arithmetic is plain Java. The client colour comes from a
 * {@link ClientColor} hook the client layer installs; without it the original fallback
 * (opaque white) is used, which is exactly what the original does when the theme has no
 * colours configured.</p>
 */
public final class ColorUtil {
    /** Where "Sync" takes its colour from. Installed by the client layer, never null. */
    public interface ClientColor {
        int argb();
    }

    private static volatile ClientColor clientColor;

    private ColorUtil() {
    }

    public static void setClientColor(ClientColor color) {
        clientColor = color;
    }

    /** "Sync" colour of the original ({@code ColorUtil.getClientColor()}). */
    public static int getClientColor() {
        ClientColor source = clientColor;
        return source == null ? 0xFFFFFFFF : source.argb();
    }

    public static int red(int c) {
        return c >> 16 & 0xFF;
    }

    public static int green(int c) {
        return c >> 8 & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int alpha(int c) {
        return c >> 24 & 0xFF;
    }

    public static float redf(int c) {
        return red(c) / 255.0F;
    }

    public static float greenf(int c) {
        return green(c) / 255.0F;
    }

    public static float bluef(int c) {
        return blue(c) / 255.0F;
    }

    public static float alphaf(int c) {
        return alpha(c) / 255.0F;
    }

    public static int getColor(int red, int green, int blue, int alpha) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF);
    }

    public static int getColor(int red, int green, int blue) {
        return getColor(red, green, blue, 255);
    }

    public static int getColor(int brightness) {
        return getColor(brightness, brightness, brightness);
    }

    public static int getColor(int brightness, int alpha) {
        return getColor(brightness, brightness, brightness, alpha);
    }

    public static int multAlpha(int color, float percent01) {
        return getColor(red(color), green(color), blue(color), Math.round(alpha(color) * percent01));
    }

    /** Clamped component-wise blend, the original's {@code overCol}. */
    public static int overCol(int color1, int color2, float percent01) {
        float percent = clamp(percent01, 0.0F, 1.0F);
        return getColor(
                lerpInt(percent, red(color1), red(color2)),
                lerpInt(percent, green(color1), green(color2)),
                lerpInt(percent, blue(color1), blue(color2)),
                lerpInt(percent, alpha(color1), alpha(color2)));
    }

    /** {@code MathHelper.lerp} on ints without importing the game class. */
    public static int lerpInt(float delta, int start, int end) {
        return start + Math.round(delta * (end - start));
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}

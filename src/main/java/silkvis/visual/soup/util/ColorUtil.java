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

    // ---------------------------------------------------------------- batch 2 additions

    /** Original {@code ColorUtil.replAlpha(int, int)}: same RGB, new alpha. */
    public static int replAlpha(int color, int alpha) {
        return getColor(red(color), green(color), blue(color), alpha);
    }

    /** Original {@code ColorUtil.replAlpha(int, float)}: alpha given as 0..1. */
    public static int replAlpha(int color, float alpha) {
        return replAlpha(color, Math.round(clamp(alpha, 0.0F, 1.0F) * 255.0F));
    }

    /**
     * Original {@code ColorUtil.gradientToRed}: pushes a colour towards red by
     * {@code damageIntensity} (0..1), keeping its alpha. Used for the hurt flash of
     * Hitboxes and of the target render.
     */
    public static int gradientToRed(int color, float damageIntensity) {
        float intensity = clamp(damageIntensity, 0.0F, 1.0F);
        return getColor(
                Math.round(lerpFloat(intensity, red(color), 255)),
                Math.round(lerpFloat(intensity, green(color), 0)),
                Math.round(lerpFloat(intensity, blue(color), 0)),
                alpha(color));
    }

    /** {@code MathHelper.lerp} on floats without importing the game class. */
    public static float lerpFloat(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /**
     * Original {@code ColorUtil.fade(int index)} on the client colour: the theme accent
     * oscillates between its brighter and its darker variant with an 8 ms step, so a
     * multi-segment shape shows a moving gradient.
     */
    public static int fade(int index) {
        int base = getClientColor();
        return fade(8, index, multBright(base, 0.7F), multDark(base, 0.7F));
    }

    /** Original {@code ColorUtil.fade(int speed, int index, int first, int second)}. */
    public static int fade(int speed, int index, int first, int second) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360L);
        angle = angle >= 180 ? 360 - angle : angle;
        return overCol(first, second, angle / 180.0F);
    }

    /** Original {@code ColorUtil.multBright}: blends towards white by the given factor. */
    public static int multBright(int color, float percent) {
        return overCol(color, getColor(255, 255, 255, alpha(color)), percent);
    }

    /** Original {@code ColorUtil.multDark}: blends towards black by the given factor. */
    public static int multDark(int color, float percent) {
        return overCol(color, getColor(0, 0, 0, alpha(color)), percent);
    }

    /** Where "friend" colours come from. Installed by the client layer, like {@link ClientColor}. */
    public interface FriendColor {
        int argb();
    }

    private static volatile FriendColor friendColor;

    public static void setFriendColor(FriendColor color) {
        friendColor = color;
    }

    /**
     * Original {@code ColorUtil.getFriendColor}: the palette's friend colour. Without an
     * installed source the original's own default (vanilla green, {@code 0xFF00FF00}) is used,
     * which is also the default of the Hitboxes friend colour picker.
     */
    public static int getFriendColor() {
        FriendColor source = friendColor;
        return source == null ? 0xFF00FF00 : source.argb();
    }
}

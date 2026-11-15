package silkvis.visual.soup.setting;

import java.util.function.Supplier;

/**
 * Ported from SoupVisuals {@code ...setting.implement.ColorSetting} (Padej_).
 *
 * <p>Kept in the original shape: a single ARGB colour with its own alpha channel, so the
 * colour window of the original (hue / saturation / alpha editors) maps onto it unchanged.
 * The original stores colours as {@code int} ARGB, which is also what the render helpers
 * of this port take.</p>
 */
public class ColorSetting extends Setting {
    private int color;
    private Integer defaultColor;
    private boolean alpha = true;

    public ColorSetting(String name, String description) {
        super(name, description);
    }

    public ColorSetting visible(Supplier<Boolean> visible) {
        this.setVisible(visible);
        return this;
    }

    /** Value in ARGB, exactly like the original's {@code getColor()}. */
    public int getColor() {
        return this.color;
    }

    public float[] getColorArray() {
        return new float[]{
                ((this.color >> 16) & 0xFF) / 255.0F,
                ((this.color >> 8) & 0xFF) / 255.0F,
                (this.color & 0xFF) / 255.0F,
                ((this.color >> 24) & 0xFF) / 255.0F
        };
    }

    public ColorSetting setColor(int color) {
        if (this.defaultColor == null) {
            this.defaultColor = color;
            this.color = color;
            return this;
        }
        boolean changed = this.color != color;
        this.color = color;
        if (changed) {
            this.notifyChange();
        }
        return this;
    }

    /** Programmatic setter for sliders: {@code r/g/b/a} are 0..1 like the original window. */
    public ColorSetting setColor(float red, float green, float blue, float alpha01) {
        return setColor(argb(red, green, blue, this.alpha ? alpha01 : 1.0F));
    }

    public static int argb(float red, float green, float blue, float alpha01) {
        int a = clamp255(Math.round(alpha01 * 255.0F));
        int r = clamp255(Math.round(red * 255.0F));
        int g = clamp255(Math.round(green * 255.0F));
        int b = clamp255(Math.round(blue * 255.0F));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int clamp255(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    public boolean isAlpha() {
        return this.alpha;
    }

    public ColorSetting setAlpha(boolean alpha) {
        this.alpha = alpha;
        return this;
    }

    @Override
    public boolean isModified() {
        return this.defaultColor != null && this.color != this.defaultColor;
    }

    @Override
    public void reset() {
        if (this.defaultColor != null) {
            this.setColor(this.defaultColor);
        }
    }

    public Integer getDefaultColor() {
        return this.defaultColor;
    }
}

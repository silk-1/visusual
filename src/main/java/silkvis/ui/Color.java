package silkvis.ui;

/**
 * sRGB colour with a separate alpha value (UI-01). Rendering code must never contain
 * raw hex literals: everything comes from {@link ThemeTokens}.
 */
public record Color(int red, int green, int blue, float alpha) {
    public Color {
        red = clamp255(red);
        green = clamp255(green);
        blue = clamp255(blue);
        alpha = Math.max(0f, Math.min(1f, alpha));
    }

    public static Color hex(String hex) {
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() != 6) {
            throw new IllegalArgumentException("expected #RRGGBB");
        }
        int rgb = Integer.parseInt(value, 16);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 1f);
    }

    public Color withAlpha(float newAlpha) {
        return new Color(red, green, blue, newAlpha);
    }

    /** Packed ARGB used by the renderer adapter. */
    public int argb() {
        int a = Math.round(alpha * 255f);
        return (a << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int argb(Color color) {
        return color == null ? 0 : color.argb();
    }

    public Color multiplyAlpha(float factor) {
        return withAlpha(Math.max(0f, Math.min(1f, alpha * factor)));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}

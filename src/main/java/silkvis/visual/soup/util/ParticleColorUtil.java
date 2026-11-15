package silkvis.visual.soup.util;

import silkvis.visual.soup.module.ParticleModule;

/**
 * Ported from SoupVisuals {@code padej.soup.base.util.particle.ParticleColorUtil} (Padej_).
 * Same maths, same offsets; only the imports changed.
 */
public final class ParticleColorUtil {
    private ParticleColorUtil() {
    }

    public static int getWaveColor(int[] colors, float alpha, int offset) {
        if (colors == null || colors.length == 0) {
            return ColorUtil.multAlpha(-1, alpha);
        }
        if (colors.length == 1) {
            return ColorUtil.multAlpha(colors[0], alpha);
        }
        if (colors.length == 2) {
            int angle = (int) ((System.currentTimeMillis() / 8L + offset) % 360L);
            angle = angle >= 180 ? 360 - angle : angle;
            return ColorUtil.multAlpha(ColorUtil.overCol(colors[0], colors[1], angle / 180.0F), alpha);
        }
        float timeProgress = (float) ((System.currentTimeMillis() / 10L + offset) % (colors.length * 360L)) / 360.0F;
        int index1 = (int) Math.floor(timeProgress) % colors.length;
        int index2 = (index1 + 1) % colors.length;
        float lerp = timeProgress - (int) Math.floor(timeProgress);
        return ColorUtil.multAlpha(ColorUtil.overCol(colors[index1], colors[index2], lerp), alpha);
    }

    public static int getWaveColor(int[] colors, float alpha) {
        return getWaveColor(colors, alpha, 0);
    }

    public static int getVertexGradientColor(int angleOffset, int[] colors, float alpha) {
        if (colors == null || colors.length == 0) {
            return ColorUtil.multAlpha(-1, alpha);
        }
        if (colors.length == 1) {
            return ColorUtil.multAlpha(colors[0], alpha);
        }
        int angle = (int) ((System.currentTimeMillis() / 8L + angleOffset) % 360L);
        if (colors.length == 2) {
            angle = angle >= 180 ? 360 - angle : angle;
            return ColorUtil.multAlpha(ColorUtil.overCol(colors[0], colors[1], angle / 180.0F), alpha);
        }
        float progress = angle / 360.0F;
        float colorIndex = progress * colors.length;
        int index1 = (int) colorIndex % colors.length;
        int index2 = (index1 + 1) % colors.length;
        float lerp = colorIndex - (int) colorIndex;
        return ColorUtil.multAlpha(ColorUtil.overCol(colors[index1], colors[index2], lerp), alpha);
    }

    /**
     * Colour of a particle given the module's colour mode, in the original order
     * ({@code Sync} -> client colour, {@code Vanilla} -> the particle's own colour,
     * otherwise the custom gradient with wave or vertex animation).
     */
    public static int resolve(ParticleModule module, int baseColor, float alpha, int colorOffset) {
        if (module.getColorMode().isSelected("Sync")) {
            return ColorUtil.multAlpha(ColorUtil.getClientColor(), alpha);
        }
        if (module.getColorMode().isSelected("Vanilla")) {
            return ColorUtil.multAlpha(baseColor, alpha);
        }
        int[] colors = module.getCustomColors();
        if (colors != null && colors.length > 0) {
            return module.getColorAnimation().isSelected("Vertex")
                    ? getVertexGradientColor(colorOffset, colors, alpha)
                    : getWaveColor(colors, alpha, colorOffset);
        }
        return ColorUtil.multAlpha(-1, alpha);
    }
}

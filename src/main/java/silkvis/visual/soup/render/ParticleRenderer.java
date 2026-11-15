package silkvis.visual.soup.render;

import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import silkvis.visual.soup.module.ParticleModule;
import silkvis.visual.soup.util.ParticleColorUtil;

/**
 * Ported from SoupVisuals {@code ...particles.render.ParticleRenderer} (Padej_).
 *
 * <p>Every particle mode of the original maps to one texture and one quad; the mode table,
 * the texture paths and the colour resolution (Sync / Vanilla / Custom with wave or vertex
 * gradient) are unchanged. Texture paths moved from
 * {@code assets/minecraft/textures/particles/...} to {@code assets/silkvis/textures/soup/...}
 * so the fork never overrides a vanilla or third-party texture.</p>
 *
 * <p><b>Not ported:</b> the {@code Cube} / {@code Pyramid} modes with their bloom pass and
 * the {@code Text} mode. In the original these go through {@code ParticleBatchRenderer}
 * (a separate VBO-based batch with its own shader); that is the next step together with the
 * bloom texture. Selecting them now renders nothing instead of a wrong-looking fallback --
 * the same behaviour the original has while its bloom texture is still loading.</p>
 */
public final class ParticleRenderer {
    private static final String BASE = "textures/soup/particles/";

    private ParticleRenderer() {
    }

    /** Queues one billboarded textured particle of the given mode. */
    public static void render(Matrix4f matrix, String mode, float size, float alpha, int baseColor,
                              String glyphTexture, ParticleModule module, int colorOffset) {
        if (mode == null) {
            return;
        }
        switch (mode) {
            case "Stars" -> quad(matrix, BASE + "star.png", size, alpha, baseColor, module, colorOffset);
            case "Hearts" -> quad(matrix, BASE + "heart.png", size, alpha, baseColor, module, colorOffset);
            case "Bloom" -> quad(matrix, BASE + "firefly.png", size, alpha, baseColor, module, colorOffset);
            case "Glyph" -> quad(matrix, BASE + "glyph/" + glyphTexture, size, alpha, baseColor, module, colorOffset);
            case "Things" -> quad(matrix, BASE + "glyph_alt/" + glyphTexture, size, alpha, baseColor, module, colorOffset);
            case "Blink" -> quad(matrix, BASE + "blink.png", size, alpha, baseColor, module, colorOffset);
            case "Coron" -> quad(matrix, BASE + "coron.png", size, alpha, baseColor, module, colorOffset);
            case "Dollar" -> quad(matrix, BASE + "dollar.png", size, alpha, baseColor, module, colorOffset);
            case "Flame" -> quad(matrix, BASE + "flame.png", size, alpha, baseColor, module, colorOffset);
            case "Geometric" -> quad(matrix, BASE + "geometric.png", size, alpha, baseColor, module, colorOffset);
            case "Snowflake" -> quad(matrix, BASE + "snowflake.png", size, alpha, baseColor, module, colorOffset);
            case "Logo" -> quad(matrix, BASE + "soupapi_3.png", size, alpha, baseColor, module, colorOffset);
            case "Virus" -> quad(matrix, BASE + "virus.png", size, alpha, baseColor, module, colorOffset);
            case "SoupAPI Old" -> quad(matrix, "textures/soup/vanilla/soupapi_old.png", size, alpha, baseColor, module, colorOffset);
            case "Sword" -> quad(matrix, "textures/soup/vanilla/sword.png", size, alpha, baseColor, module, colorOffset);
            case "Network" -> {
                // The original draws a network node as a small bloom quad, handled by the
                // link renderer; the node itself needs no billboard.
            }
            default -> {
                // Cube / Pyramid / Text: see the class comment.
            }
        }
    }

    private static void quad(Matrix4f matrix, String path, float size, float alpha, int baseColor,
                             ParticleModule module, int colorOffset) {
        if (path == null) {
            return;
        }
        int color = ParticleColorUtil.resolve(module, baseColor, alpha, colorOffset);
        // The caller has already translated the matrix to the particle position and applied
        // the camera rotation (like the original's WorldParticle#render), so the quad is
        // centred on the origin of that local space.
        float half = size / 2.0F;
        SoupRender.drawTexture(matrix, Identifier.of("silkvis", path), -half, -half, size, size,
                color, false);
    }
}

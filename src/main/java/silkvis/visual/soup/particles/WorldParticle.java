package silkvis.visual.soup.particles;

import org.joml.Matrix4f;
import silkvis.visual.soup.module.ParticleModule;
import silkvis.visual.soup.render.ParticleRenderer;
import silkvis.visual.soup.util.MathUtil;

/**
 * Ported from SoupVisuals {@code ...particles.types.WorldParticle} (Padej_).
 *
 * <p>One textured billboard in the world. The original builds a copy of the matrix stack so
 * it can translate/scale/rotate without touching the shared stack; here a copy of the
 * camera matrix does the same job.</p>
 *
 * <p>The camera-facing rotation and the tick interpolation are passed into
 * {@link #render(Matrix4f, double, double, double, float, float, float)} instead of being
 * read from {@code MinecraftClient.getGameRenderer().getCamera()}: it is the same data, but
 * the class stays testable and free of game imports elsewhere.</p>
 */
public class WorldParticle extends AbstractParticle {
    private final String particleMode;
    private final String glyphTexture;
    private final float health;
    private final ParticleModule module;

    public WorldParticle(float x, float y, float z, int colorArgb, float rotationAngle,
                         float rotationSpeed, float health, String mode, String physics,
                         String glyphTexture, float lifeTime, float scale, float speed,
                         ParticleModule module) {
        super(x, y, z, colorArgb, rotationAngle, rotationSpeed, lifeTime, scale, physics,
                !"Network".equals(mode));
        this.health = health;
        this.particleMode = mode;
        this.glyphTexture = glyphTexture;
        this.module = module;
        this.initMotion(speed);
    }

    /**
     * @param cameraMatrix camera-relative world matrix (already translated to the camera)
     * @param camX/camY/camZ camera position, used to place the particle relative to the camera
     * @param tickProgress frame interpolation factor, 0..1
     * @param cameraYaw/cameraPitch view angles of the camera
     */
    @Override
    public void render(Matrix4f cameraMatrix) {
        render(cameraMatrix, 0, 0, 0, 1.0F, 0, 0);
    }

    public void render(Matrix4f cameraMatrix, double camX, double camY, double camZ,
                       float tickProgress, float cameraYaw, float cameraPitch) {
        float size = this.scale;
        float renderScale = this.particleMode.equals("Text") ? 0.025F * size : 0.07F;
        float alpha = this.cachedAlpha;
        double posX = MathUtil.interpolate(this.px, this.x, tickProgress);
        double posY = MathUtil.interpolate(this.py, this.y, tickProgress);
        double posZ = MathUtil.interpolate(this.pz, this.z, tickProgress);
        float interpolatedAngle = MathUtil.interpolate(this.prevRotationAngle, this.rotationAngle, tickProgress);

        Matrix4f matrix = new Matrix4f(cameraMatrix);
        matrix.translate((float) (posX - camX), (float) (posY - camY), (float) (posZ - camZ));
        matrix.scale(renderScale, renderScale, renderScale);
        matrix.rotateY((float) Math.toRadians(-cameraYaw));
        matrix.rotateX((float) Math.toRadians(cameraPitch));

        boolean zAxisLocked = this.particleMode.equals("Text") || this.particleMode.equals("Flame");
        if (zAxisLocked) {
            matrix.rotateZ((float) Math.toRadians(180.0F));
        } else if (!this.particleMode.equals("Cube")) {
            matrix.rotateZ((float) Math.toRadians(interpolatedAngle));
        }

        if (!this.particleMode.equals("Cube") && !this.particleMode.equals("Pyramid")) {
            matrix.translate(-size / 2.0F, -size / 2.0F, 0.0F);
            ParticleRenderer.render(matrix, this.particleMode, size, alpha, this.color,
                    this.glyphTexture, this.module, this.colorOffset);
        } else if (this.particleMode.equals("Cube")) {
            matrix.rotateX((float) Math.toRadians(interpolatedAngle));
            matrix.rotateY((float) Math.toRadians(interpolatedAngle * 0.7F));
            ParticleRenderer.render(matrix, this.particleMode, size, alpha, this.color,
                    this.glyphTexture, this.module, this.colorOffset);
        } else {
            matrix.rotateX((float) Math.toRadians(interpolatedAngle));
            ParticleRenderer.render(matrix, this.particleMode, size, alpha, this.color,
                    this.glyphTexture, this.module, this.colorOffset);
        }
    }

    public String getParticleMode() {
        return this.particleMode;
    }

    public float getHealth() {
        return this.health;
    }
}

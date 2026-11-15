package silkvis.visual.soup.util;

/**
 * Immutable view of one entity for the world render pass.
 *
 * <p>SoupVisuals reads the game objects directly from its renderers
 * ({@code entity.getBoundingBox()}, {@code entity.getPos()}, {@code entity.hurtTime}). SilkVis
 * keeps {@code net.minecraft.*} out of the module code (AGENTS.md rule 10), so the client
 * layer copies exactly the fields the ported modules use into this record once per frame and
 * the modules keep working on plain numbers. Nothing here changes the original maths: the
 * tick interpolation is the same {@code prev + (cur - prev) * delta} that
 * {@code MathUtil.interpolate(Entity)} does.</p>
 */
public record EntitySnapshot(int id, String name, boolean player, boolean friend,
                             double x, double y, double z,
                             double prevX, double prevY, double prevZ,
                             double eyeY, float width, float height,
                             int hurtTime, float health, boolean alive, boolean living,
                             boolean elytraFlying) {

    public double interpolatedX(float tickProgress) {
        return this.prevX + (this.x - this.prevX) * tickProgress;
    }

    public double interpolatedY(float tickProgress) {
        return this.prevY + (this.y - this.prevY) * tickProgress;
    }

    public double interpolatedZ(float tickProgress) {
        return this.prevZ + (this.z - this.prevZ) * tickProgress;
    }

    /** Squared distance between this entity's previous and current tick position. */
    public double movedSquared() {
        double dx = this.x - this.prevX;
        double dy = this.y - this.prevY;
        double dz = this.z - this.prevZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Eye height above the feet, as the original's {@code getEyePos} ray origin uses it. */
    public double eyeOffset() {
        return this.eyeY - this.y;
    }

    /** The original's {@code getPlayerHitboxHeight}: crouching halves the box, self only. */
    public float colliderHeight(boolean self, boolean selfSneaking) {
        return self && selfSneaking ? Math.min(this.height, 1.5F) : this.height;
    }
}

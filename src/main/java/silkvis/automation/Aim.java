package silkvis.automation;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * What the player is pointing at, computed from the camera angles alone.
 *
 * <p>Why not the client's own crosshair hit: that value is a render-thread artefact of the last
 * frame, it is {@code null} between frames, and it would tie this decision to a field of
 * {@code MinecraftClient}. A ray through the block grid is the same answer, is available on any
 * tick, and - being plain arithmetic - can be checked without the game.</p>
 *
 * <p>The ray is walked in small steps; the first cell that satisfies {@code solid} is the answer.
 * A step of a quarter block is well below the smallest block dimension, so no cell can be jumped
 * over.</p>
 */
public final class Aim {
    /** One step of the ray, in blocks. */
    private static final double STEP = 0.25;

    private Aim() {
    }

    /**
     * @param eye   position the ray starts from (eye level)
     * @param yaw   camera yaw in degrees, Minecraft convention (-90 looks east)
     * @param pitch camera pitch in degrees, -90 straight up
     * @param reach how far to look, in blocks
     * @param solid the block predicate; the first cell that answers true wins
     * @return the first solid cell along the ray, or empty when the ray ends in air
     */
    public static Optional<BlockPos> firstSolid(double eyeX, double eyeY, double eyeZ,
                                                float yaw, float pitch, double reach,
                                                Predicate<BlockPos> solid) {
        if (solid == null || reach <= 0) {
            return Optional.empty();
        }
        // Minecraft convention, verified against the compass in the HUD (yaw 0 = south, -90 =
        // east, +90 = north) and against the pitch sign (positive pitch looks down):
        //   forward = (-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRadians);
        double dx = -Math.sin(yawRadians) * horizontal;
        double dy = -Math.sin(pitchRadians);
        double dz = Math.cos(yawRadians) * horizontal;
        for (double t = 0; t <= reach; t += STEP) {
            BlockPos cell = new BlockPos((int) Math.floor(eyeX + dx * t),
                    (int) Math.floor(eyeY + dy * t), (int) Math.floor(eyeZ + dz * t));
            if (solid.test(cell)) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }
}

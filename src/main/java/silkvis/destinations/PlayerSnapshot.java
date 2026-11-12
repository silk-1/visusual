package silkvis.destinations;

/** Immutable player observation used for command preconditions and verification. */
public record PlayerSnapshot(
        String dimensionId,
        double x,
        double y,
        double z,
        boolean moving,
        boolean damagedRecently,
        boolean spectator
) {
    public double distanceTo(DestinationTarget.Coordinates target) {
        double dx = x - target.x();
        double dy = y - target.y();
        double dz = z - target.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

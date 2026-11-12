package silkvis.core;

/**
 * User limits are stored explicitly as enabled + value (CFG-04). Negative values
 * and {@code -1} are never used as an implicit "infinite".
 */
public record OptionalLimit(boolean enabled, long value) {
    public OptionalLimit {
        if (value < 0) {
            throw new IllegalArgumentException("limit value must be >= 0, got " + value);
        }
    }

    public static OptionalLimit disabled(long fallback) {
        return new OptionalLimit(false, fallback);
    }

    /** Effective value clamped to a hard technical cap that a toggle cannot remove. */
    public long effective(long hardTechnicalCap, long fallbackWhenDisabled) {
        long base = enabled ? value : fallbackWhenDisabled;
        return Math.min(base, hardTechnicalCap);
    }
}

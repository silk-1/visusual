package silkvis.core;

/** Injectable clock. Tests must use a fake clock instead of real sleeps (TEST-01). */
public interface Clock {
    long monotonicNanos();

    long epochMillis();

    static Clock system() {
        return SystemClock.INSTANCE;
    }
}

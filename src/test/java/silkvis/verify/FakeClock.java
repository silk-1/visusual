package silkvis.verify;

import silkvis.core.Clock;

/** Deterministic clock for the self checks (TEST-01: no real sleeps). */
final class FakeClock implements Clock {
    private long millis = 1_700_000_000_000L;
    /** Monotonic time starts at zero so "capturedAt = 0" means "at the start of the test". */
    private long nanos = 0L;

    @Override
    public long monotonicNanos() {
        return nanos;
    }

    @Override
    public long epochMillis() {
        return millis;
    }

    void advanceMillis(long delta) {
        millis += delta;
        nanos += delta * 1_000_000L;
    }

    void advanceNanos(long delta) {
        nanos += delta;
    }
}

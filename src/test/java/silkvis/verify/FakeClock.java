package silkvis.verify;

import silkvis.core.Clock;

/** Deterministic clock for the self checks (TEST-01: no real sleeps). */
final class FakeClock implements Clock {
    private long millis = 1_700_000_000_000L;
    private long nanos = 1_000_000_000L;

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

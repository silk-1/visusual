package silkvis.hud;

import silkvis.core.Clock;

/** Minimal monotonic rate limiter for HUD updates (coordinates <= 10 Hz, HUD-04). */
public final class RateLimiter {
    private final Clock clock;
    private final long intervalNanos;
    private long next;

    public RateLimiter(Clock clock, int hz) {
        this.clock = clock;
        this.intervalNanos = 1_000_000_000L / Math.max(1, hz);
    }

    public boolean tryAcquire() {
        long now = clock.monotonicNanos();
        if (now < next) {
            return false;
        }
        next = now + intervalNanos;
        return true;
    }

    public void reset() {
        next = 0L;
    }
}

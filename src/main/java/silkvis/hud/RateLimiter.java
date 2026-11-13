package silkvis.hud;

import silkvis.core.Clock;

/** Minimal monotonic rate limiter for HUD updates (coordinates <= 10 Hz, HUD-04). */
public final class RateLimiter {
    private final Clock clock;
    private final long intervalNanos;
    private long next;
    private boolean primed;

    public RateLimiter(Clock clock, int hz) {
        this.clock = clock;
        this.intervalNanos = 1_000_000_000L / Math.max(1, hz);
    }

    /**
     * The first call always succeeds. Comparing against a default {@code 0} assumed a clock
     * that starts at zero: {@link System#nanoTime()} has an arbitrary origin and may be
     * negative, which would have starved the 10 Hz coordinate updates forever.
     */
    public boolean tryAcquire() {
        long now = clock.monotonicNanos();
        if (primed && now - next < 0) {
            return false;
        }
        primed = true;
        next = now + intervalNanos;
        return true;
    }

    public void reset() {
        next = 0L;
        primed = false;
    }
}

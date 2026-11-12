package silkvis.hud;

import silkvis.core.Clock;

/**
 * FPS/frame-time aggregation at 2 Hz (HUD-04). Values are exposed from a cached model so
 * text does not jitter every frame and positions do not jump with changing length.
 */
public final class FpsAggregator {
    private final Clock clock;
    private final long intervalNanos;
    private long windowStart;
    private int frames;
    private int cachedFps;
    private double cachedFrameMs;
    private long lastFrameStart;
    private final silkvis.diagnostics.Timings frameTimes = new silkvis.diagnostics.Timings(256);

    public FpsAggregator(Clock clock, int hz) {
        this.clock = clock;
        this.intervalNanos = 1_000_000_000L / Math.max(1, hz);
        this.windowStart = clock.monotonicNanos();
    }

    /** Call once per rendered frame. */
    public void onFrame() {
        long now = clock.monotonicNanos();
        if (lastFrameStart != 0L) {
            frameTimes.record(now - lastFrameStart);
        }
        lastFrameStart = now;
        frames++;
        long elapsed = now - windowStart;
        if (elapsed >= intervalNanos) {
            cachedFps = (int) Math.round(frames * 1_000_000_000.0 / elapsed);
            cachedFrameMs = frames == 0 ? 0 : (elapsed / 1_000_000.0) / frames;
            frames = 0;
            windowStart = now;
        }
    }

    public int fps() {
        return cachedFps;
    }

    public double frameTimeMs() {
        return cachedFrameMs;
    }

    public double p95FrameMs() {
        return frameTimes.p95Ms();
    }
}

package silkvis.ui;

/**
 * Time-based UI transitions (UI-07: 120–180 ms).
 *
 * <p>Duration always comes from {@link ThemeTokens#motionMs()}, which is 0 when the user
 * enabled «Меньше движения». A zero duration snaps to the target on the first frame instead
 * of animating, so the reduced-motion path is the same code with no special cases.</p>
 *
 * <p>Time is read from {@link System#nanoTime()} rather than from a frame delta argument:
 * widgets are rendered from several call sites (main window, HUD editor, map screen) and
 * none of them is required to thread a delta through the widget interface.</p>
 */
public final class Animation {
    private Animation() {
    }

    /** Eased 0..1 value that walks towards its target over the theme's motion duration. */
    public static final class Fade {
        private float value;
        private boolean started;
        private long lastNanos;

        public float update(boolean on, int durationMs) {
            return update(on ? 1f : 0f, durationMs);
        }

        public float update(float target, int durationMs) {
            long now = System.nanoTime();
            float clamped = Math.max(0f, Math.min(1f, target));
            if (!started || durationMs <= 0) {
                started = true;
                lastNanos = now;
                value = clamped;
                return value;
            }
            long delta = Math.max(0L, now - lastNanos);
            lastNanos = now;
            float step = (float) delta / (durationMs * 1_000_000f);
            if (value < clamped) {
                value = Math.min(clamped, value + step);
            } else if (value > clamped) {
                value = Math.max(clamped, value - step);
            }
            return value;
        }

        public float value() {
            return value;
        }
    }

    /** 0..1 progress of a one-shot transition that started at {@code startNanos}. */
    public static float progress(long startNanos, int durationMs) {
        if (durationMs <= 0) {
            return 1f;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        float t = (float) elapsed / (durationMs * 1_000_000f);
        if (t >= 1f) {
            return 1f;
        }
        // Smoothstep: linear easing looks mechanical on a 140 ms fade.
        return t * t * (3f - 2f * t);
    }

    /** Blends two packed ARGB colours, including alpha. */
    public static int mix(int fromArgb, int toArgb, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        if (k <= 0f) {
            return fromArgb;
        }
        if (k >= 1f) {
            return toArgb;
        }
        int a = lerp((fromArgb >>> 24) & 0xFF, (toArgb >>> 24) & 0xFF, k);
        int r = lerp((fromArgb >>> 16) & 0xFF, (toArgb >>> 16) & 0xFF, k);
        int g = lerp((fromArgb >>> 8) & 0xFF, (toArgb >>> 8) & 0xFF, k);
        int b = lerp(fromArgb & 0xFF, toArgb & 0xFF, k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}

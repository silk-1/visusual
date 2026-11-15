package silkvis.hud.extra;

import silkvis.core.Clock;

import java.util.Locale;
import java.util.Objects;

/** A session duration that is unaffected by wall-clock corrections. */
public final class SessionClock {
    private final Clock clock;
    private long startedAtNanos;

    /** Starts immediately using the injected monotonic clock. */
    public SessionClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        reset();
    }

    /** Starts a new session at the current monotonic instant. */
    public void reset() {
        startedAtNanos = clock.monotonicNanos();
    }

    /** Returns complete elapsed milliseconds; a backwards test clock yields zero. */
    public long elapsedMillis() {
        return Math.max(0L, clock.monotonicNanos() - startedAtNanos) / 1_000_000L;
    }

    /** Formats the current session with seconds precision. */
    public String format() {
        return formatDuration(elapsedMillis());
    }

    /** Formats mm:ss below one hour, otherwise h:mm:ss; negative durations display zero. */
    public static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds / 60L) % 60L;
        return hours == 0L
                ? String.format(Locale.ROOT, "%02d:%02d", minutes, seconds % 60L)
                : String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds % 60L);
    }
}

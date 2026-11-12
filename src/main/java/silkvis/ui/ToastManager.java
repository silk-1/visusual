package silkvis.ui;

import silkvis.core.Clock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded toast queue for confirmations and recoverable errors (UI-05). */
public final class ToastManager {
    public enum Level {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    public record Toast(String key, Level level, long expiresAtNanos) {
    }

    private static final int MAX_VISIBLE = 3;

    private final Clock clock;
    private final long lifetimeNanos;
    private final Deque<Toast> visible = new ArrayDeque<>();

    public ToastManager(Clock clock, long lifetimeMillis) {
        this.clock = clock;
        this.lifetimeNanos = lifetimeMillis * 1_000_000L;
    }

    public void push(String key, Level level) {
        visible.addLast(new Toast(key, level, clock.monotonicNanos() + lifetimeNanos));
        while (visible.size() > MAX_VISIBLE) {
            visible.removeFirst();
        }
    }

    public void tick() {
        long now = clock.monotonicNanos();
        visible.removeIf(t -> t.expiresAtNanos() < now);
    }

    public List<Toast> current() {
        return new ArrayList<>(visible);
    }

    public void clear() {
        visible.clear();
    }
}

package silkvis.config;

import silkvis.core.Clock;

import java.util.function.Consumer;

/**
 * Coalesces rapid edits into one write (CFG-04). Driven by {@link #tick()} from the client
 * tick, so no thread and no sleep is involved; tests use a fake clock.
 */
public final class Debouncer<T> {
    private final long delayMillis;
    private final Clock clock;
    private final Consumer<T> sink;

    private T pending;
    private boolean hasPending;
    private long deadlineNanos;

    public Debouncer(long delayMillis, Clock clock, Consumer<T> sink) {
        this.delayMillis = Math.max(0L, delayMillis);
        this.clock = clock;
        this.sink = sink;
    }

    public void submit(T value) {
        this.pending = value;
        this.hasPending = true;
        this.deadlineNanos = clock.monotonicNanos() + delayMillis * 1_000_000L;
    }

    public void tick() {
        if (hasPending && clock.monotonicNanos() >= deadlineNanos) {
            fire();
        }
    }

    public void flush() {
        if (hasPending) {
            fire();
        }
    }

    public boolean hasPending() {
        return hasPending;
    }

    private void fire() {
        T value = pending;
        pending = null;
        hasPending = false;
        sink.accept(value);
    }
}

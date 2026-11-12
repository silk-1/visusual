package silkvis.diagnostics;

import silkvis.core.Clock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Size- and rate-limited diagnostics log (ARCH-06). Repeats are collapsed and the total
 * entry count is capped; metadata is never written here (MEDIA-17).
 */
public final class BoundedLog {
    public record Entry(long epochMillis, String code, String detail, int repeats) {
    }

    private final Clock clock;
    private final int maxEntries;
    private final long minIntervalMs;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<String, Long> lastSeen = new HashMap<>();
    private long suppressed;

    public BoundedLog(Clock clock, int maxEntries, long minIntervalMs) {
        this.clock = clock;
        this.maxEntries = Math.max(16, maxEntries);
        this.minIntervalMs = Math.max(0, minIntervalMs);
    }

    public synchronized void log(String code, String detail) {
        long now = clock.epochMillis();
        Long last = lastSeen.get(detail);
        if (last != null && now - last < minIntervalMs) {
            suppressed++;
            return;
        }
        lastSeen.put(detail, now);
        entries.addLast(new Entry(now, code, detail, 1));
        while (entries.size() > maxEntries) {
            entries.removeFirst();
        }
    }

    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    public synchronized long suppressedCount() {
        return suppressed;
    }

    public synchronized void clear() {
        entries.clear();
        lastSeen.clear();
        suppressed = 0;
    }

    public synchronized String render() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(e.epochMillis()).append(' ').append(e.code());
            if (!e.detail().isEmpty()) {
                sb.append(' ').append(e.detail());
            }
            if (e.repeats() > 1) {
                sb.append(" x").append(e.repeats());
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}

package silkvis.diagnostics;

import java.util.Arrays;

/**
 * Rolling percentile statistics for the acceptance budgets (PERF-02/PERF-03). Values are
 * nanoseconds; the buffer is fixed-size so diagnostics cannot grow without bound.
 */
public final class Timings {
    private final long[] samples;
    private int index;
    private int count;

    public Timings(int capacity) {
        this.samples = new long[Math.max(16, capacity)];
    }

    public void record(long nanos) {
        samples[index] = Math.max(0L, nanos);
        index = (index + 1) % samples.length;
        count = Math.min(count + 1, samples.length);
    }

    public int count() {
        return count;
    }

    private long[] sorted() {
        long[] copy = Arrays.copyOf(samples, count);
        Arrays.sort(copy);
        return copy;
    }

    public double percentile(double p) {
        if (count == 0) {
            return 0;
        }
        long[] s = sorted();
        int idx = (int) Math.round((s.length - 1) * Math.max(0, Math.min(1, p)));
        return s[idx] / 1_000_000.0;
    }

    public double p50Ms() {
        return percentile(0.50);
    }

    public double p95Ms() {
        return percentile(0.95);
    }

    public void reset() {
        index = 0;
        count = 0;
    }
}

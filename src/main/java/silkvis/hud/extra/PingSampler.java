package silkvis.hud.extra;

import java.util.Arrays;

/** Fixed-capacity ping history. Sorting never changes insertion order. */
public final class PingSampler {
    public static final int DEFAULT_CAPACITY = 40;
    private final int[] samples;
    private int next;
    private int size;

    /** Retains the most recent 40 measurements. */
    public PingSampler() {
        this(DEFAULT_CAPACITY);
    }

    /** Creates a fixed positive capacity, useful for alternate reporting windows. */
    public PingSampler(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        samples = new int[capacity];
    }

    /** Adds nonnegative milliseconds; invalid samples throw without changing the buffer. */
    public void push(int ms) {
        if (ms < 0) {
            throw new IllegalArgumentException("ping must be nonnegative");
        }
        samples[next] = ms;
        next = (next + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
    }

    /** Returns the last inserted measurement, or -1 when empty. */
    public int latest() {
        return size == 0 ? -1 : samples[(next + samples.length - 1) % samples.length];
    }

    /** Uses the middle value, or the floor of the two middle values' mean for an even count. */
    public int median() {
        if (size == 0) {
            return -1;
        }
        int[] sorted = sortedCopy();
        int middle = size / 2;
        return size % 2 != 0 ? sorted[middle] : (int) (((long) sorted[middle - 1] + sorted[middle]) / 2);
    }

    /** Returns the nearest-rank 95th percentile, or -1 when empty. */
    public int p95() {
        if (size == 0) {
            return -1;
        }
        int rank = (int) ((95L * size + 99L) / 100L);
        return sortedCopy()[rank - 1];
    }

    private int[] sortedCopy() {
        int[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        return sorted;
    }
}

package silkvis.automation;

/**
 * Per-tick work budget, in "operations" (one block inspected, one node expanded, one raycast).
 *
 * <p>This is the single most important number in the engine. Baritone solves the same problem
 * with a settings-driven allowance per tick; here it is a fixed, documented budget taken from
 * the project's own performance rule (PERF: stay inside a 1 ms slice per tick for sampling), and
 * every expensive loop in the automation package takes a {@code Budget} and must return when it
 * is spent. A goal that cannot finish a step inside the budget simply continues next tick: the
 * engine is allowed to be slow, never to stutter.</p>
 *
 * <p>Not thread-safe on purpose: one budget belongs to one tick.</p>
 */
public final class Budget {
    /** Default allowance: about what a 1 ms slice can afford on a mid-range CPU. */
    public static final int DEFAULT_PER_TICK = 512;

    private final int limit;
    private int used;

    public Budget(int limit) {
        this.limit = Math.max(1, limit);
    }

    public static Budget perTick() {
        return new Budget(DEFAULT_PER_TICK);
    }

    /** Takes one operation; false means "stop and continue next tick". */
    public boolean spend() {
        if (used >= limit) {
            return false;
        }
        used++;
        return true;
    }

    public boolean spent() {
        return used >= limit;
    }

    public int used() {
        return used;
    }

    public int limit() {
        return limit;
    }

    public int remaining() {
        return limit - used;
    }
}

package silkvis.automation;

import java.util.List;

/**
 * A goal is a thing the player wants done ("mine this ore", "cut these trees", "go there"), and
 * it knows nothing about input. It is asked, once per tick, for the next batch of steps.
 *
 * <p>The contract is deliberately narrow, and each clause exists because the alternative has a
 * known failure mode:</p>
 * <ul>
 *   <li>{@link #plan} must return an empty list when it has nothing to do right now. Waiting is
 *       expressed by returning nothing, never by blocking the tick.</li>
 *   <li>It receives a {@link Budget}; anything unbounded (scanning, searching) has to spend from
 *       it, so a goal cannot freeze the frame.</li>
 *   <li>It may not touch the player, the world or the input; the {@link Snapshot} is a read-only
 *       view for exactly that reason.</li>
 * </ul>
 */
public interface Goal {
    /** Stable id used in config and logs, matching {@code AutomationCatalog} (A01..A22). */
    String id();

    /** Lang key of the display name ({@code silkvis.automation.<slug>}). */
    String titleKey();

    /** Minimum mode this goal needs. A MINE goal in {@link AutomationMode#MOVE} is refused. */
    AutomationMode requiredMode();

    /** True when the goal is finished and should be cleared automatically. */
    boolean done();

    /** Why the last run stopped; {@link StopReason#NONE} while it is fine. */
    StopReason stopReason();

    /** Clears state for a new run (also called after a world change). */
    void reset(Snapshot world);

    /**
     * Produces the steps for this tick.
     *
     * @return steps to execute in order; an empty list means "wait, still fine"
     */
    List<Step> plan(Snapshot world, Budget budget);

    /** One line for the HUD/status label ("3/8 logs"), already localized or numeric. */
    String progress();
}

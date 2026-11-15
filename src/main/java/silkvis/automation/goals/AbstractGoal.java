package silkvis.automation.goals;

import silkvis.automation.Budget;
import silkvis.automation.Goal;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StopReason;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared plumbing for the bundled goals: state, stop attribution and a bounded plan list.
 *
 * <p>Nothing here decides behaviour; it only removes the boilerplate that would otherwise be
 * copied into every goal (and diverge, which is how "stopped for no reason" bugs happen).</p>
 */
public abstract class AbstractGoal implements Goal {
    private boolean done;
    private StopReason reason = StopReason.NONE;
    private Snapshot lastWorld;

    @Override
    public boolean done() {
        return done;
    }

    @Override
    public StopReason stopReason() {
        return reason;
    }

    /** Marks the goal finished and attributes the stop. */
    protected final void stop(StopReason why) {
        this.reason = why == null ? StopReason.INTERNAL : why;
        this.done = true;
    }

    /** Marks success. */
    protected final void finish() {
        this.reason = StopReason.DONE;
        this.done = true;
    }

    protected final void clearStop() {
        this.reason = StopReason.NONE;
        this.done = false;
    }

    protected final Snapshot world() {
        return this.lastWorld;
    }

    @Override
    public void reset(Snapshot world) {
        this.lastWorld = world;
        this.reason = StopReason.NONE;
        this.done = false;
        onReset(world);
    }

    /** Subclass state reset; the defaults are stateless. */
    protected void onReset(Snapshot world) {
    }

    /**
     * The plan for this tick, with the steps appended by {@link #next} into the caller's list.
     * {@link Goal#plan} allocates a list per tick, which is right for the engine; this variant
     * exists so a check can inspect the steps of one specific call without guessing at state.
     */
    public final List<Step> planSteps(Snapshot world, Budget budget, List<Step> into) {
        this.lastWorld = world;
        if (this.done) {
            return into;
        }
        try {
            next(world, budget, into);
        } catch (RuntimeException e) {
            stop(StopReason.INTERNAL);
        }
        return into;
    }

    @Override
    public List<Step> plan(Snapshot world, Budget budget) {
        this.lastWorld = world;
        if (this.done) {
            return List.of();
        }
        List<Step> steps = new ArrayList<>();
        try {
            next(world, budget, steps);
        } catch (RuntimeException e) {
            // A goal that throws must not take the engine (or the frame) down with it. The
            // stop is attributed so the UI can say what happened.
            stop(StopReason.INTERNAL);
        }
        return steps;
    }

    /**
     * @param steps append here; the engine executes them in order and caps how many it takes
     */
    protected abstract void next(Snapshot world, Budget budget, List<Step> steps);
}

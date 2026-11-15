package silkvis.automation.goals;

import silkvis.automation.AutomationMode;
import silkvis.automation.BlockPos;
import silkvis.automation.Budget;
import silkvis.automation.Movement;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StopReason;

import java.util.List;

/**
 * "Walk to this block" (A19 navigate, and the base of every other movement goal).
 *
 * <p>The path is computed once and then followed step by step; it is recomputed when the player
 * drifts off the plan, when a block of the plan changed, or when the plan is exhausted without
 * arriving. That is the same lifecycle Baritone's {@code PathExecutor} has, trimmed to what a
 * client-side client needs: no chunk loading, no cache across goals.</p>
 */
public class GotoGoal extends AbstractGoal {
    private final BlockPos target;
    private final boolean allowBreak;
    private final double arrivalRadius;
    /** Node cap for one search; the engine passes the profile value in, see {@link #withMaxNodes}. */
    private int maxNodes = 1024;
    private List<Step> path = List.of();
    private int cursor;
    private BlockPos pathStart;
    private StopReason failedReason = StopReason.NONE;

    public GotoGoal(BlockPos target, boolean allowBreak, double arrivalRadius) {
        this.target = target;
        this.allowBreak = allowBreak;
        this.arrivalRadius = Math.max(0.5, arrivalRadius);
    }

    /** The engine hands the profile's node cap to the goal before planning. */
    public GotoGoal withMaxNodes(int nodes) {
        this.maxNodes = Math.max(64, nodes);
        return this;
    }

    @Override
    public String id() {
        return "A19";
    }

    @Override
    public String titleKey() {
        return "silkvis.automation.navigate";
    }

    @Override
    public AutomationMode requiredMode() {
        // Walking is movement only: it needs MOVE, never FULL.
        return AutomationMode.MOVE;
    }

    @Override
    protected void onReset(Snapshot world) {
        this.path = List.of();
        this.cursor = 0;
        this.pathStart = null;
        this.failedReason = StopReason.NONE;
    }

    @Override
    protected void next(Snapshot world, Budget budget, List<Step> steps) {
        if (arrived(world.player())) {
            finish();
            return;
        }
        if (this.path.isEmpty() || this.cursor >= this.path.size() || needsReplan(world)) {
            Movement.Path found = Movement.find(world.grid(), world.player(), this.target,
                    this.allowBreak, this.maxNodes, budget);
            if (found == null) {
                // Null means either "no route" or "budget ran out". Waiting one tick and trying
                // again is correct for the second case and harmless for the first, but a goal
                // that never finds a route must not spin forever.
                if (budget.spent()) {
                    return;
                }
                stop(failedReason == StopReason.NONE ? StopReason.NO_PATH : failedReason);
                return;
            }
            this.path = found.steps();
            this.cursor = 0;
            this.pathStart = world.player();
            if (this.path.isEmpty()) {
                finish();
                return;
            }
        }
        Step step = this.path.get(this.cursor);
        if (!stepPossible(world, step)) {
            // The world changed under the plan (a block was placed, a hole opened). Recompute
            // once, then report BLOCK_CHANGED if the very first block is gone again.
            this.failedReason = StopReason.BLOCK_CHANGED;
            this.path = List.of();
            return;
        }
        this.failedReason = StopReason.NONE;
        steps.add(step);
        this.cursor++;
    }

    private boolean arrived(BlockPos player) {
        long dx = (long) player.x() - this.target.x();
        long dy = (long) player.y() - this.target.y();
        long dz = (long) player.z() - this.target.z();
        return dx * dx + dy * dy + dz * dz <= this.arrivalRadius * this.arrivalRadius;
    }

    /** Re-plan when the player left the plan, or a planned block/floor is gone. */
    private boolean needsReplan(Snapshot world) {
        if (this.pathStart == null) {
            return true;
        }
        if (world.player().distanceSquared(this.pathStart) > 4) {
            return true;
        }
        for (int i = this.cursor; i < this.path.size(); i++) {
            Step step = this.path.get(i);
            if (step.kind() == silkvis.automation.StepKind.GOTO
                    && !world.grid().passable(step.x(), step.y(), step.z())) {
                return true;
            }
        }
        return false;
    }

    private static boolean stepPossible(Snapshot world, Step step) {
        if (step.kind() == silkvis.automation.StepKind.MINE) {
            return world.grid().solid(step.x(), step.y(), step.z());
        }
        if (step.kind() == silkvis.automation.StepKind.GOTO) {
            return world.grid().passable(step.x(), step.y(), step.z());
        }
        return true;
    }

    @Override
    public String progress() {
        if (this.path.isEmpty()) {
            return "0/" + this.target.x() + " " + this.target.y() + " " + this.target.z();
        }
        return this.cursor + "/" + this.path.size();
    }

    public BlockPos target() {
        return this.target;
    }

    @Override
    public String toString() {
        return "GotoGoal" + this.target;
    }
}

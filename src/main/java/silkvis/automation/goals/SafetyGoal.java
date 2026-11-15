package silkvis.automation.goals;

import silkvis.automation.AutomationMode;
import silkvis.automation.BlockPos;
import silkvis.automation.Budget;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StopReason;

import java.util.List;

/**
 * "Get me out of here" (A22 emergency exit) - a not-in-Baritone extra.
 *
 * <p>Baritone's answer to danger is a process that stops the job; this one actively walks the
 * player to safety, because on a survival server the two dangerous situations (falling into a
 * cave, standing in lava) need a direction, not a stop message.</p>
 *
 * <p>It never breaks blocks: an emergency exit that starts mining is an emergency of its own. It
 * looks for the nearest column where the player can stand on solid ground, out of fluids, at least
 * {@code safeDistance} from the danger source, and walks there.</p>
 */
public class SafetyGoal extends AbstractGoal {
    private final BlockPos danger;
    private final double safeDistance;
    private BlockPos refuge;

    public SafetyGoal(BlockPos danger, double safeDistance) {
        this.danger = danger;
        this.safeDistance = Math.max(4.0, safeDistance);
    }

    @Override
    public String id() {
        return "A22";
    }

    @Override
    public String titleKey() {
        return "silkvis.automation.emergency_exit";
    }

    @Override
    public AutomationMode requiredMode() {
        return AutomationMode.MOVE;
    }

    @Override
    protected void onReset(Snapshot world) {
        this.refuge = null;
    }

    @Override
    protected void next(Snapshot world, Budget budget, List<Step> steps) {
        if (safe(world.player(), world)) {
            finish();
            return;
        }
        if (this.refuge == null) {
            this.refuge = findRefuge(world, budget);
        }
        if (this.refuge == null) {
            // Nowhere safe within the budget: staying put and reporting is better than a random
            // direction that could be into the lava.
            stop(StopReason.NO_PATH);
            return;
        }
        steps.add(Step.gotoBlock(this.refuge.x(), this.refuge.y(), this.refuge.z()));
    }

    /** A cell is safe when it is not dangerous, standable and far enough from the source. */
    private boolean safe(BlockPos pos, Snapshot world) {
        if (world.grid().dangerous(pos.x(), pos.y(), pos.z())) {
            return false;
        }
        if (!world.grid().passable(pos.x(), pos.y(), pos.z())
                || !world.grid().passable(pos.x(), pos.y() + 1, pos.z())) {
            return false;
        }
        if (world.grid().water(pos.x(), pos.y(), pos.z())) {
            return false;
        }
        long dx = (long) pos.x() - this.danger.x();
        long dz = (long) pos.z() - this.danger.z();
        return dx * dx + dz * dz >= this.safeDistance * this.safeDistance;
    }

    /** Spiral scan outward from the player; nearest safe column wins. */
    private BlockPos findRefuge(Snapshot world, Budget budget) {
        BlockPos start = world.player();
        int radius = (int) Math.ceil(this.safeDistance) + 8;
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    if (budget != null && !budget.spend()) {
                        return best;
                    }
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos candidate = start.offset(dx, dy, dz);
                        if (!safe(candidate, world)) {
                            continue;
                        }
                        // A floor has to exist, or "safe" is a free fall.
                        if (world.grid().passable(candidate.x(), candidate.y() - 1, candidate.z())) {
                            continue;
                        }
                        long distance = candidate.distanceSquared(start);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    @Override
    public String progress() {
        return this.refuge == null ? "-" : this.refuge.x() + " " + this.refuge.y() + " "
                + this.refuge.z();
    }
}

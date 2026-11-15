package silkvis.automation.goals;

import silkvis.automation.AutomationMode;
import silkvis.automation.BlockPos;
import silkvis.automation.Budget;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StopReason;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Cut down this tree" (A03 tree farm, single-tree version).
 *
 * <p>Not in Baritone either: the original has no tree goal, players pair "mine" with a schematic.
 * The interesting part of a tree is that the block list is not known up front: it is whatever is
 * connected to the trunk through log blocks. So the goal floods the log cluster from the block the
 * player is looking at, capped by the budget and by a total block cap, and mines from the top down
 * so the trunk does not fall on the player's head as separate entities.</p>
 *
 * <p>Leaves are ignored: they are not logs, and a "cut the tree" that also strips leaves is a
 * different goal.</p>
 */
public class TreeFarmGoal extends AbstractGoal {
    /** Hard cap of one tree; a "tree" bigger than this is a build, not a tree. */
    static final int MAX_LOGS = 256;

    private final Set<BlockPos> logs = new HashSet<>();
    private final Deque<BlockPos> pending = new ArrayDeque<>();
    private BlockPos trunk;
    private boolean scanned;
    private int mined;
    /** Logs the flood found; kept after they are removed so progress has a denominator. */
    private int total;

    public TreeFarmGoal() {
    }

    @Override
    public String id() {
        return "A03";
    }

    @Override
    public String titleKey() {
        return "silkvis.automation.tree_farm";
    }

    @Override
    public AutomationMode requiredMode() {
        return AutomationMode.FULL;
    }

    @Override
    protected void onReset(Snapshot world) {
        // The trunk is configuration (which tree), not state: clearing it here made
        // "at(...) then reset(...)" silently forget the target and report TARGET_GONE.
        this.logs.clear();
        this.pending.clear();
        this.scanned = false;
        this.mined = 0;
        this.total = 0;
    }

    /** Seeds the goal on a trunk block the caller resolved (the block under the cursor). */
    public TreeFarmGoal at(BlockPos trunkBlock) {
        this.trunk = trunkBlock;
        this.scanned = false;
        return this;
    }

    @Override
    protected void next(Snapshot world, Budget budget, List<Step> steps) {
        if (this.trunk == null) {
            stop(StopReason.TARGET_GONE);
            return;
        }
        if (!this.scanned) {
            flood(world, budget);
            if (this.logs.isEmpty()) {
                // The trunk is gone (someone else cut it, or the chunk unloaded).
                stop(StopReason.TARGET_GONE);
                return;
            }
            this.scanned = true;
            this.total = this.logs.size();
        }
        this.logs.removeIf(pos -> !world.grid().solid(pos.x(), pos.y(), pos.z()));
        if (this.logs.isEmpty()) {
            finish();
            return;
        }
        // Highest log first: the tree goes down from the top, so the remaining part stays
        // connected and nothing falls on the player.
        BlockPos next = this.logs.stream()
                .min(java.util.Comparator
                        .comparingInt(BlockPos::y).reversed()
                        .thenComparingLong(pos -> pos.distanceSquared(world.player())))
                .orElseThrow();
        if (!MineGoal.withinReach(world.player(), next)) {
            steps.add(Step.gotoBlock(next.x(), next.y(), next.z()));
            return;
        }
        if (world.breakTicks(next.x(), next.y(), next.z()) == Integer.MAX_VALUE) {
            stop(StopReason.NO_TOOL);
            return;
        }
        steps.add(Step.mine(next.x(), next.y(), next.z()));
        this.mined++;
    }

    /**
     * Breadth-first flood over log blocks, four-connected plus up/down. Diagonals are excluded on
     * purpose: a diagonal log next to a tree is a different tree half the time, and cutting the
     * neighbour's trunk is not what the player asked for.
     */
    private void flood(Snapshot world, Budget budget) {
        this.logs.clear();
        this.pending.clear();
        this.pending.add(this.trunk);
        Set<BlockPos> seen = new HashSet<>();
        seen.add(this.trunk);
        while (!this.pending.isEmpty() && this.logs.size() < MAX_LOGS) {
            if (budget != null && !budget.spend()) {
                break;
            }
            BlockPos pos = this.pending.poll();
            if (!isLog(world, pos)) {
                continue;
            }
            this.logs.add(pos);
            for (BlockPos neighbour : neighbours(pos)) {
                if (seen.add(neighbour)) {
                    this.pending.add(neighbour);
                }
            }
        }
    }

    private static List<BlockPos> neighbours(BlockPos pos) {
        List<BlockPos> out = new ArrayList<>(6);
        out.add(pos.offset(1, 0, 0));
        out.add(pos.offset(-1, 0, 0));
        out.add(pos.offset(0, 0, 1));
        out.add(pos.offset(0, 0, -1));
        out.add(pos.offset(0, 1, 0));
        out.add(pos.offset(0, -1, 0));
        return out;
    }

    /** A log is any block whose id ends with {@code _log} or {@code _wood} (all wood types). */
    static boolean isLogBlock(String id) {
        if (id == null) {
            return false;
        }
        return id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem")
                || id.endsWith("_hyphae");
    }

    private static boolean isLog(Snapshot world, BlockPos pos) {
        return world.grid().solid(pos.x(), pos.y(), pos.z())
                && isLogBlock(world.blockId(pos.x(), pos.y(), pos.z()));
    }

    @Override
    public String progress() {
        return this.mined + "/" + Math.max(this.total, this.mined);
    }
}

package silkvis.automation.goals;

import silkvis.automation.AutomationMode;
import silkvis.automation.BlockPos;
import silkvis.automation.Budget;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StopReason;
import silkvis.automation.ToolSelect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Mine these blocks" (A01 ore mining, A02 area excavation).
 *
 * <p>The original mod's equivalent is a whole family of goals; the shape that matters here is
 * the order of work: block first, then the walk, and a re-check of every block right before it
 * is mined. Mining a block that is already gone is the classic way to a broken plan: the executor
 * would dig air forever and the goal would look stuck.</p>
 *
 * <p>Blocks are ordered by distance from the player (nearest first) and, for the same distance,
 * top-down, because that is the order that keeps the player from standing under falling gravel.</p>
 */
public class MineGoal extends AbstractGoal {
    private final List<BlockPos> targets;
    private final boolean allowBreak;
    private final List<BlockPos> remaining = new ArrayList<>();
    private BlockPos current;
    private int walkedTo = -1;
    private int mined;

    /** @param targets blocks to remove; the list is copied so the caller cannot mutate the plan */
    public MineGoal(List<BlockPos> targets, boolean allowBreak) {
        this.targets = List.copyOf(targets);
        this.allowBreak = allowBreak;
    }

    @Override
    public String id() {
        return "A01";
    }

    @Override
    public String titleKey() {
        return "silkvis.automation.ore_mining";
    }

    @Override
    public AutomationMode requiredMode() {
        // Breaking a block is interaction, which MOVE explicitly refuses.
        return AutomationMode.FULL;
    }

    @Override
    protected void onReset(Snapshot world) {
        this.remaining.clear();
        this.remaining.addAll(this.targets);
        this.current = null;
        this.walkedTo = -1;
        this.mined = 0;
    }

    @Override
    protected void next(Snapshot world, Budget budget, List<Step> steps) {
        this.remaining.removeIf(pos -> !world.grid().solid(pos.x(), pos.y(), pos.z()));
        if (this.remaining.isEmpty()) {
            finish();
            return;
        }
        this.remaining.sort(Comparator
                .comparingLong((BlockPos pos) -> pos.distanceSquared(world.player()))
                .thenComparing(Comparator.comparingInt(BlockPos::y).reversed()));
        BlockPos next = this.remaining.get(0);
        if (!next.equals(this.current)) {
            this.current = next;
            this.walkedTo = -1;
        }
        if (!withinReach(world.player(), next)) {
            // Walk once; if the player does not get closer the executor decides (it owns the
            // movement), the goal simply keeps asking for the same step.
            steps.add(Step.gotoBlock(next.x(), next.y(), next.z()));
            this.walkedTo = 1;
            return;
        }
        // Hold the tool the block wants before asking the executor to break it. Without this
        // the goal gave up as NO_TOOL whenever the right tool existed in the hotbar but was not
        // the selected slot -- the block's break time is computed for the held item, so a
        // pickaxe one slot away looked exactly like no pickaxe at all (A10).
        steps.addAll(ToolSelect.stepsToHoldTool(world, world.blockId(next.x(), next.y(), next.z())));
        int ticks = world.breakTicks(next.x(), next.y(), next.z());
        if (ticks == Integer.MAX_VALUE) {
            stop(StopReason.NO_TOOL);
            return;
        }
        this.walkedTo = 0;
        steps.add(Step.mine(next.x(), next.y(), next.z()));
        if (ticks == 0) {
            this.mined++;
        }
    }

    /** Reach is 4 blocks, the vanilla interaction distance with a margin for the eye height. */
    static boolean withinReach(BlockPos player, BlockPos target) {
        long dx = (long) player.x() - target.x();
        long dy = (long) player.y() - target.y();
        long dz = (long) player.z() - target.z();
        return dx * dx + dy * dy + dz * dz <= 25;
    }

    @Override
    public String progress() {
        int total = this.targets.size();
        int left = this.remaining.size();
        return (total - left) + "/" + total;
    }
}

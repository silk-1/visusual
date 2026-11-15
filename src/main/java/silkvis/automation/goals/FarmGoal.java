package silkvis.automation.goals;

import silkvis.automation.AutomationMode;
import silkvis.automation.BlockPos;
import silkvis.automation.Budget;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StopReason;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Harvest and replant this field" (A04 crop farm) - the part Baritone does not have at all,
 * because the original is a pathfinding library and leaves farm loops to scripts.
 *
 * <p>The cycle per crop is: harvest the ripe block, then place the seed back into the same
 * farmland, then move on. Two details make it work in practice:</p>
 * <ul>
 *   <li>It only acts on blocks it can actually reach, so the walk interleaves with the work
 *       instead of doing all the walking first and finding half the field regrown.</li>
 *   <li>It never harvests a block that is not ripe: the snapshot reports growth, and breaking an
 *       immature crop wastes the item and the seed.</li>
 * </ul>
 */
public class FarmGoal extends AbstractGoal {
    /** Crops this goal understands, by registry id, with the seed that replants them. */
    private static final String[][] CROPS = {
            {"minecraft:wheat", "minecraft:wheat_seeds"},
            {"minecraft:carrots", "minecraft:carrot"},
            {"minecraft:potatoes", "minecraft:potato"},
            {"minecraft:beetroots", "minecraft:beetroot_seeds"}
    };

    private final List<BlockPos> crops;
    private final double radius;
    private int harvested;
    private int replanted;
    private BlockPos last;

    public FarmGoal(List<BlockPos> crops, double radius) {
        this.crops = List.copyOf(crops);
        this.radius = Math.max(2.0, radius);
    }

    @Override
    public String id() {
        return "A04";
    }

    @Override
    public String titleKey() {
        return "silkvis.automation.crop_farm";
    }

    @Override
    public AutomationMode requiredMode() {
        return AutomationMode.FULL;
    }

    @Override
    protected void onReset(Snapshot world) {
        this.harvested = 0;
        this.replanted = 0;
        this.last = null;
    }

    @Override
    protected void next(Snapshot world, Budget budget, List<Step> steps) {
        List<BlockPos> ripe = new ArrayList<>();
        for (BlockPos pos : this.crops) {
            if (budget != null && !budget.spend()) {
                break;
            }
            String id = world.blockId(pos.x(), pos.y(), pos.z());
            if (isRipe(world, pos, id) && near(world.player(), pos)) {
                ripe.add(pos);
            }
        }
        if (ripe.isEmpty()) {
            // Nothing ripe in reach: walk to the middle of the field and look again next tick.
            BlockPos centre = centre(this.crops);
            if (centre != null && world.player().distanceSquared(centre) > this.radius * this.radius) {
                steps.add(Step.gotoBlock(centre.x(), centre.y(), centre.z()));
                return;
            }
            if (this.harvested > 0) {
                finish();
            }
            return;
        }
        ripe.sort(Comparator.comparingLong(pos -> pos.distanceSquared(world.player())));
        BlockPos target = ripe.get(0);
        this.last = target;
        steps.add(Step.mine(target.x(), target.y(), target.z()));
        this.harvested++;
        // Replant right after the harvest: the executor's USE_ITEM places the seed in the
        // farmland the crop just left, which is why the two steps are adjacent.
        steps.add(Step.useItem());
        this.replanted++;
    }

    /** Ripe when the block id matches a crop and the snapshot says it is fully grown. */
    private static boolean isRipe(Snapshot world, BlockPos pos, String id) {
        if (id == null || seedFor(id) == null) {
            return false;
        }
        return world.cropRipe(pos.x(), pos.y(), pos.z());
    }

    private static boolean near(BlockPos player, BlockPos pos) {
        return player.distanceSquared(pos) <= 25;
    }

    private BlockPos centre(List<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        long x = 0;
        long y = 0;
        long z = 0;
        for (BlockPos pos : blocks) {
            x += pos.x();
            y += pos.y();
            z += pos.z();
        }
        int n = blocks.size();
        return new BlockPos((int) (x / n), (int) (y / n), (int) (z / n));
    }

    /** Seed the goal needs in the hotbar for the crop it is about to replant. */
    public static String seedFor(String cropId) {
        for (String[] crop : CROPS) {
            if (crop[0].equals(cropId)) {
                return crop[1];
            }
        }
        return null;
    }

    @Override
    public String progress() {
        return "h" + this.harvested + " r" + this.replanted
                + (this.last == null ? "" : " @ " + this.last.x() + " " + this.last.z());
    }

}

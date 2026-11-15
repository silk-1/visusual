package silkvis.automation.goals;

import silkvis.automation.AutomationMode;
import silkvis.automation.Budget;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StopReason;

import java.util.Comparator;
import java.util.List;

/**
 * "Pick up the drops around me" (A21 pickup). Not a Baritone goal: the original leaves item
 * pickup to the caller, and on a farm server that is the difference between a working loop and a
 * pile of items despawning at your feet.
 *
 * <p>Two rules that keep it from being annoying:</p>
 * <ul>
 *   <li>Only items within the configured radius and on walkable ground are considered, so it
 *       never dives into lava for a dropped sword.</li>
 *   <li>It walks to the item, re-resolving the nearest one every tick; an item that disappears
 *       simply drops out of the list, and when the list is empty the goal is done.</li>
 * </ul>
 */
public class CollectGoal extends AbstractGoal {
    private final double radius;
    private final boolean mineOnly;
    private Integer walkedTo;

    /**
     * @param radius   search radius around the player, in blocks
     * @param mineOnly collect only drops that belong to the player (the snapshot flags them)
     */
    public CollectGoal(double radius, boolean mineOnly) {
        this.radius = Math.max(2.0, radius);
        this.mineOnly = mineOnly;
    }

    @Override
    public String id() {
        return "A21";
    }

    @Override
    public String titleKey() {
        return "silkvis.automation.pickup";
    }

    @Override
    public AutomationMode requiredMode() {
        return AutomationMode.MOVE;
    }

    @Override
    protected void onReset(Snapshot world) {
        this.walkedTo = null;
    }

    @Override
    protected void next(Snapshot world, Budget budget, List<Step> steps) {
        Snapshot.EntityInfo item = nearestItem(world, budget);
        if (item == null) {
            finish();
            return;
        }
        int x = (int) Math.floor(item.x());
        int y = (int) Math.floor(item.y());
        int z = (int) Math.floor(item.z());
        if (world.grid().dangerous(x, y, z)) {
            // An item in fire or lava is not worth walking into; skip it and look for the next.
            this.walkedTo = item.id();
            stop(StopReason.DANGER);
            return;
        }
        steps.add(Step.gotoBlock(x, y, z));
        this.walkedTo = item.id();
    }

    private Snapshot.EntityInfo nearestItem(Snapshot world, Budget budget) {
        Snapshot.EntityInfo best = null;
        double bestDistance = this.radius * this.radius;
        for (Snapshot.EntityInfo entity : world.entities()) {
            if (budget != null && !budget.spend()) {
                break;
            }
            if (!"item".equals(entity.type())) {
                continue;
            }
            if (entity.distance() > this.radius) {
                continue;
            }
            if (entity.distance() < bestDistance) {
                bestDistance = entity.distance();
                best = entity;
            }
        }
        return best;
    }

    /** Drops are sorted by distance in the snapshot; exposed for the status line and checks. */
    public static List<Snapshot.EntityInfo> items(List<Snapshot.EntityInfo> entities, double radius) {
        return entities.stream()
                .filter(entity -> "item".equals(entity.type()))
                .filter(entity -> entity.distance() <= radius)
                .sorted(Comparator.comparingDouble(Snapshot.EntityInfo::distance))
                .toList();
    }

    @Override
    public String progress() {
        return this.walkedTo == null ? "-" : "#" + this.walkedTo;
    }
}

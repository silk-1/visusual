package silkvis.automation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The goals this build actually ships, in the catalogue's order.
 *
 * <p>Deliberately a hand-written list and not a classpath scan: the automation tab shows exactly
 * these, the config validates ids against exactly these, and a goal that is not here cannot be
 * activated by a hand-edited config. Adding a goal is one line plus its own checks.</p>
 */
public final class GoalRegistry {
    /** Metadata for one activatable goal. */
    public record Entry(String id, String titleKey, AutomationMode mode, Factory factory) {
    }

    /** Creates a fresh goal for a run; goals are stateful, so they are never shared. */
    public interface Factory {
        Goal create(BlockPos target);
    }

    /**
     * Safety net for goals that have no meaningful target block (pickup, emergency exit): an
     * origin is passed instead of a real target and the goal ignores it.
     */
    public static final BlockPos NO_TARGET = new BlockPos(0, 0, 0);

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public GoalRegistry() {
        register("A19", "silkvis.automation.navigate", AutomationMode.MOVE,
                target -> new silkvis.automation.goals.GotoGoal(target, false, 1.5));
        register("A01", "silkvis.automation.ore_mining", AutomationMode.FULL,
                target -> new silkvis.automation.goals.MineGoal(List.of(target), true));
        register("A03", "silkvis.automation.tree_farm", AutomationMode.FULL,
                target -> new silkvis.automation.goals.TreeFarmGoal().at(target));
        register("A04", "silkvis.automation.crop_farm", AutomationMode.FULL,
                target -> new silkvis.automation.goals.FarmGoal(List.of(target), 8.0));
        register("A21", "silkvis.automation.pickup", AutomationMode.MOVE,
                target -> new silkvis.automation.goals.CollectGoal(16.0, true));
        // No target: eating is about the player's own food level, so NO_TARGET is passed and
        // the goal ignores it, like the two other targetless goals.
        register("A09", "silkvis.automation.auto_eat", AutomationMode.FULL,
                target -> new silkvis.automation.goals.EatGoal());
        // Tool select is not a goal: it is a prerequisite every breaking goal calls. It is
        // registered so the tab can show it and so the catalogue and registry stay in step,
        // but the factory returns the mining goal it serves, on the target given.
        register("A10", "silkvis.automation.tool_select", AutomationMode.FULL,
                target -> new silkvis.automation.goals.MineGoal(List.of(target), true));
        // The danger source is the player's own position when the goal is created: emergency
        // exit means "get away from where I am standing now", not "from a stored coordinate".
        register("A22", "silkvis.automation.emergency_exit", AutomationMode.MOVE,
                target -> new silkvis.automation.goals.SafetyGoal(target, 8.0));
    }

    private void register(String id, String titleKey, AutomationMode mode, Factory factory) {
        this.entries.put(id, new Entry(id, titleKey, mode, factory));
    }

    public Optional<Entry> get(String id) {
        return Optional.ofNullable(this.entries.get(id));
    }

    public List<Entry> all() {
        return new ArrayList<>(this.entries.values());
    }

    /**
     * Creates a goal for a run, refusing one whose mode is above what the player allowed.
     *
     * @return the goal, or empty when the id is unknown or the mode is too low (the caller reports
     *         {@link StopReason#MODE_TOO_LOW} / {@link StopReason#INTERNAL} accordingly)
     */
    public Optional<Goal> create(String id, BlockPos target, AutomationMode allowed) {
        Entry entry = this.entries.get(id);
        // HINT is the one mode that plans a goal it would not be allowed to execute: showing the
        // plan is the whole point of it, and nothing is pressed. OFF is always refused.
        if (entry == null || allowed == null || allowed == AutomationMode.OFF) {
            return Optional.empty();
        }
        boolean planOnly = allowed == AutomationMode.HINT;
        if (!planOnly && allowed.ordinal() < entry.mode().ordinal()) {
            return Optional.empty();
        }
        Goal goal = entry.factory().create(target);
        goal.reset(null);
        return Optional.of(goal);
    }

    /** Ids in catalogue order, used by the settings validation and the UI. */
    public List<String> ids() {
        return new ArrayList<>(this.entries.keySet());
    }
}

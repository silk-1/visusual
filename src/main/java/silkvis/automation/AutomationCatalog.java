package silkvis.automation;

import java.util.List;
import java.util.Set;

/**
 * The automation catalogue: the 22 approved task types.
 *
 * <p>The ids are stable and are used as config and log keys, so they never get renamed. The
 * status is not decoration: {@link #canStart} is what the Automation tab asks before it offers a
 * start button, and an entry is READY only when a goal for it exists in {@link GoalRegistry}. That
 * keeps the two lists from drifting: the check {@code catalogMatchesRegistry} in the self checks
 * fails the build when they disagree.</p>
 */
public final class AutomationCatalog {
    public enum Status {
        PLANNED,
        READY
    }

    public record Entry(String id, String titleKey, String descriptionKey, Status status) {
    }

    /**
     * Task types that have a working goal in this build. Everything else stays PLANNED and the
     * tab says so instead of offering a button that would do nothing.
     */
    private static final Set<String> IMPLEMENTED = Set.of(
            "A01", // ore mining (MineGoal)
            "A03", // tree farm (TreeFarmGoal)
            "A04", // crop farm (FarmGoal)
            "A09", // auto eat (EatGoal)
            "A10", // tool select (ToolSelect)
            "A19", // navigate to a point (GotoGoal)
            "A21", // pick up dropped items (CollectGoal)
            "A22"); // emergency exit (SafetyGoal)

    private static final List<Entry> ENTRIES = List.of(
            planned("A01", "ore_mining"),
            planned("A02", "excavate_area"),
            planned("A03", "tree_farm"),
            planned("A04", "crop_farm"),
            planned("A05", "cane_bamboo"),
            planned("A06", "fishing"),
            planned("A07", "combat"),
            planned("A08", "defense"),
            planned("A09", "auto_eat"),
            planned("A10", "tool_select"),
            planned("A11", "tool_guard"),
            planned("A12", "equipment"),
            planned("A13", "inventory_sort"),
            planned("A14", "unload"),
            planned("A15", "storage"),
            planned("A16", "trading"),
            planned("A17", "schematic_build"),
            planned("A18", "road_build"),
            planned("A19", "navigate"),
            planned("A20", "explore"),
            planned("A21", "pickup"),
            planned("A22", "emergency_exit"));

    private AutomationCatalog() {
    }

    private static Entry planned(String id, String slug) {
        return new Entry(id, "silkvis.automation." + slug, "silkvis.automation." + slug + ".desc",
                IMPLEMENTED.contains(id) ? Status.READY : Status.PLANNED);
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static boolean anyReady() {
        return ENTRIES.stream().anyMatch(e -> e.status() == Status.READY);
    }

    /** Guard used by the UI: a task type can only be started when it is READY. */
    public static boolean canStart(String id) {
        return ENTRIES.stream().anyMatch(e -> e.id().equals(id) && e.status() == Status.READY);
    }
}

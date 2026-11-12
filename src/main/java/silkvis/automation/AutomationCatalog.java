package silkvis.automation;

import java.util.List;

/**
 * Approved P2 automation catalogue (section 10).
 *
 * <p>In the P1 build every entry is PLANNED: no scheduler, no bots and no fake toggles
 * are exposed (0.2). The ids are stable and already used for config/log keys so the P2
 * work does not need a rename.</p>
 */
public final class AutomationCatalog {
    public enum Status {
        PLANNED,
        READY
    }

    public record Entry(String id, String titleKey, String descriptionKey, Status status) {
    }

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
                Status.PLANNED);
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static boolean anyReady() {
        return ENTRIES.stream().anyMatch(e -> e.status() == Status.READY);
    }

    /**
     * Guard used by the UI: a task type can only be started when it is READY. In P1 this
     * always returns false, which is the intended visible behaviour.
     */
    public static boolean canStart(String id) {
        return ENTRIES.stream().anyMatch(e -> e.id().equals(id) && e.status() == Status.READY);
    }
}

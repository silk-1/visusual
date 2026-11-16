package silkvis.automation.baritone;

/**
 * What Baritone is doing, as far as SilkVis can tell, in a Minecraft-free value.
 *
 * <p>Baritone runs its own state machine and is driven from its own tick; SilkVis is a guest in
 * that relationship. So the status is deliberately thin: whether it is loaded, whether anything
 * is running, which process is in charge, and a reason when it cannot be used. Anything richer
 * would be a copy of Baritone's internals, which this project does not have and does not want
 * (the integration calls its API and reads back the few fields it needs).</p>
 */
public final class BaritoneStatus {
    /** Why Baritone cannot be used right now, when it cannot. */
    public enum Availability {
        /** The mod is not installed. The tab says so and offers nothing. */
        MISSING,
        /** Installed, but the API class could not be reached through reflection. */
        BROKEN,
        /** Installed and reachable. */
        READY
    }

    private final Availability availability;
    private final boolean pathing;
    private final String process;
    private final String detail;

    private BaritoneStatus(Availability availability, boolean pathing, String process,
                           String detail) {
        this.availability = availability;
        this.pathing = pathing;
        this.process = process == null ? "" : process;
        this.detail = detail == null ? "" : detail;
    }

    public static BaritoneStatus missing() {
        return new BaritoneStatus(Availability.MISSING, false, "", "not installed");
    }

    public static BaritoneStatus broken(String why) {
        return new BaritoneStatus(Availability.BROKEN, false, "", why);
    }

    public static BaritoneStatus ready(boolean pathing, String process) {
        return new BaritoneStatus(Availability.READY, pathing, process, "");
    }

    public Availability availability() {
        return this.availability;
    }

    /** True while Baritone is calculating or following a path. */
    public boolean pathing() {
        return this.pathing;
    }

    /** Name of the process in charge ("Mine", "Explore", ...), empty when idle. */
    public String process() {
        return this.process;
    }

    /** Free-form detail for the log: why it is broken, or what it is doing. */
    public String detail() {
        return this.detail;
    }

    public boolean usable() {
        return this.availability == Availability.READY;
    }

    /** One line for the HUD status widget; never null, never empty. */
    public String describe() {
        return switch (this.availability) {
            case MISSING -> "baritone: not installed";
            case BROKEN -> "baritone: unusable (" + this.detail + ")";
            case READY -> this.pathing
                    ? "baritone: " + (this.process.isEmpty() ? "working" : this.process)
                    : "baritone: idle";
        };
    }
}

package silkvis.automation;

/**
 * Why a goal stopped. Every stop is attributed: "the bot stopped and I do not know why" is the
 * failure mode that makes automation untrustworthy, so the reason is a value, not a log line.
 *
 * <p>Codes are transport-neutral and become lang keys as
 * {@code silkvis.automation.stop.<code>}.</p>
 */
public enum StopReason {
    /** Still running. */
    NONE("none"),
    /** The goal reported success. */
    DONE("done"),
    /** The player pressed the panic key or switched the goal off. */
    BY_PLAYER("by_player"),
    /** No route to the target after the search budget was spent. */
    NO_PATH("no_path"),
    /** The target block/entity disappeared or is already what the goal wanted. */
    TARGET_GONE("target_gone"),
    /** Nothing suitable in the hotbar or inventory. */
    NO_TOOL("no_tool"),
    /** A block the plan needed is not there any more. */
    BLOCK_CHANGED("block_changed"),
    /** Health or food crossed the safety threshold. */
    LOW_HEALTH("low_health"),
    /** The player is falling, in lava, on fire or drowned; movement is handed back. */
    DANGER("danger"),
    /** Another mod or the player moved the camera/movement, so the engine backs off. */
    CONFLICT("conflict"),
    /** The world changed (dimension, respawn, disconnect). */
    WORLD_CHANGED("world_changed"),
    /** The tick budget for one tick was exceeded. */
    BUDGET("budget"),
    /** Mode was set below what the goal needs (for example MOVE for a mining goal). */
    MODE_TOO_LOW("mode_too_low"),
    /** An unexpected condition; the detail carries the message. */
    INTERNAL("internal");

    private final String code;

    StopReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public String langKey() {
        return "silkvis.automation.stop." + code;
    }
}

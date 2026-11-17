package silkvis.automation.baritone;

/**
 * A task a player can hand to Baritone, in SilkVis' own vocabulary.
 *
 * <p>Why this exists at all, given Baritone is asked to do the work: the automation tab must
 * offer the same list whether or not Baritone is installed, and the list must be a compile-time
 * constant so the self checks can prove that every entry maps to something Baritone actually
 * exposes. Doing that with Baritone's own process objects would drag its API into the checks,
 * which is exactly what the "no compile dependency" decision forbids.</p>
 *
 * <p>The id is stable: it is used in config, in logs and in lang keys, so it never gets
 * renamed.</p>
 */
public enum BaritoneTask {
    /** Walk to a coordinate, no block breaking. */
    GOTO("goto", Need.POINT),
    /** Walk to a coordinate, breaking what is in the way. */
    GOTO_ALLOW_BREAK("goto_break", Need.POINT),
    /** Mine blocks of one kind until a count is reached. */
    MINE("mine", Need.BLOCK_AND_COUNT),
    /** Mine out an area. */
    EXCAVATE("excavate", Need.POINT),
    /** Collect dropped items nearby. */
    COLLECT("collect", Need.NONE),
    /** Follow a named player or entity type. */
    FOLLOW("follow", Need.TEXT),
    /** Explore until a structure is found. */
    EXPLORE("explore", Need.NONE),
    /** Stop whatever Baritone is doing, without touching SilkVis state. */
    STOP("stop", Need.NONE),
    /** Cancel Baritone outright, including a stuck process. */
    CANCEL("cancel", Need.NONE);

    /** What the caller must supply before the task can start. */
    public enum Need {
        NONE,
        POINT,
        TEXT,
        BLOCK_AND_COUNT
    }

    private final String slug;
    private final Need need;

    BaritoneTask(String slug, Need need) {
        this.slug = slug;
        this.need = need;
    }

    /** Stable slug used in config keys and logs. */
    public String slug() {
        return this.slug;
    }

    /** What the caller must supply; the screen uses it to disable a button instead of failing. */
    public Need need() {
        return this.need;
    }

    /** Lang key of the display name. */
    public String titleKey() {
        return "silkvis.baritone." + this.slug;
    }

    /**
     * The Baritone command line for this task, or {@code null} when an argument is missing.
     *
     * <p>Commands, not the typed API objects. The typed API needs our code to name Minecraft
     * classes such as {@code Predicate<Entity>} and {@code BlockOptionalMeta}; those names are
     * looked up at run time, so they only match a Baritone built for the exact same Minecraft
     * version. A real report proved the cost: {@code IFollowProcess.follow(String)} does not
     * exist, the parameter is a {@code Predicate<Entity>}, and the whole bridge reported
     * {@code unusable} although Baritone itself was running fine. The command layer takes
     * plain text -- {@code ICommandManager.execute("goto 10 64 -20")} -- so there is nothing
     * to obfuscate and nothing to mismatch.</p>
     *
     * <p>Names below were read from the 1.21.10 branch sources, not guessed: goto, mine,
     * thisway, explore, follow, cancel.</p>
     */
    public String command(int x, int y, int z, String text, String block, int count) {
        return switch (this) {
            case GOTO -> "goto " + x + " " + y + " " + z;
            case GOTO_ALLOW_BREAK -> "goto " + x + " " + y + " " + z;
            case MINE -> block == null || block.isEmpty()
                    ? null : "mine " + Math.max(0, count) + " " + block;
            case EXCAVATE -> "goto " + x + " " + y + " " + z;
            case COLLECT -> "thisway";
            case FOLLOW -> text == null || text.isEmpty() ? null : "follow players " + text;
            case EXPLORE -> "explore";
            case STOP -> "cancel";
            case CANCEL -> "cancel";
        };
    }

    /**
     * True when the task can be started at once. Everything else needs a block under the
     * crosshair or a typed name, and a button that fails on press teaches the player nothing.
     */
    public boolean readyToRun() {
        return this == STOP || this == CANCEL || this == EXPLORE || this == COLLECT;
    }

    /** Maps a config/log slug back to the task, or {@code null} when it is unknown. */
    public static BaritoneTask bySlug(String slug) {
        if (slug == null) {
            return null;
        }
        for (BaritoneTask task : values()) {
            if (task.slug.equals(slug)) {
                return task;
            }
        }
        return null;
    }
}

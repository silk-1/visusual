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
    STOP("stop", Need.NONE);

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

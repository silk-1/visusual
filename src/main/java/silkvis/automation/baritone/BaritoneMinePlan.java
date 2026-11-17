package silkvis.automation.baritone;

import java.util.EnumSet;

/**
 * What the Mine page is about to ask Baritone to do: which ores, and how many.
 *
 * <p>Mutable on purpose, and owned by the runtime rather than by the screen: the selection is
 * the player's work, so it must survive closing the window and reopening it. It holds no
 * Minecraft or Baritone objects, so the self checks can build a command from it without a game
 * running.</p>
 *
 * <p>The command is assembled by {@link BaritoneTask#MINE} rather than here, so the syntax lives
 * in one place: {@code mine <blocks...>} with no limit, {@code mine <n> <blocks...>} with one --
 * exactly the two usage lines Baritone's own MineCommand help prints.</p>
 */
public final class BaritoneMinePlan {
    /** Step of the limit buttons, in blocks. */
    public static final int LIMIT_STEP = 16;

    /**
     * Upper bound of the limit. Past this the number stops meaning anything (a run that long
     * ends when the inventory is full anyway) and the label would only get longer.
     */
    public static final int MAX_LIMIT = 512;

    private final EnumSet<BaritoneOre> selected = EnumSet.noneOf(BaritoneOre.class);
    private int limit;

    public boolean selected(BaritoneOre ore) {
        return ore != null && this.selected.contains(ore);
    }

    /** Adds or removes one ore; the checkbox is the only way in, so this is never a maybe. */
    public void toggle(BaritoneOre ore) {
        if (ore == null) {
            return;
        }
        if (!this.selected.remove(ore)) {
            this.selected.add(ore);
        }
    }

    public void clear() {
        this.selected.clear();
    }

    public int count() {
        return this.selected.size();
    }

    /** Blocks to mine before stopping; 0 means "no limit". */
    public int limit() {
        return this.limit;
    }

    /**
     * Sets the limit, clamped to 0..{@link #MAX_LIMIT} and snapped to the step so the label can
     * never show a number the buttons cannot reach again.
     */
    public void setLimit(int value) {
        int clamped = Math.max(0, Math.min(MAX_LIMIT, value));
        this.limit = clamped - clamped % LIMIT_STEP;
    }

    /** Moves the limit by whole steps; the minus button stops at zero instead of going negative. */
    public void bumpLimit(int steps) {
        setLimit(this.limit + steps * LIMIT_STEP);
    }

    /** Space-separated ids of everything selected, in menu order. */
    public String blocks() {
        StringBuilder out = new StringBuilder();
        for (BaritoneOre ore : BaritoneOre.values()) {
            if (!this.selected.contains(ore)) {
                continue;
            }
            for (String block : ore.blocks()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(block);
            }
        }
        return out.toString();
    }

    /** The command line, or {@code null} when nothing is selected and Start must stay off. */
    public String command() {
        String blocks = blocks();
        if (blocks.isEmpty()) {
            return null;
        }
        return BaritoneTask.MINE.command(0, 0, 0, "", blocks, this.limit);
    }
}

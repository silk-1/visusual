package silkvis.automation.baritone;

import java.util.ArrayList;
import java.util.List;

/**
 * The visual menu's catalogue: every Baritone action the panel offers, one entry per tile.
 *
 * <p>Why a catalogue instead of a page of hand-written buttons: the panel is data-driven, so the
 * screen renders sections, tiles and forms from this list and a new action costs one constant --
 * not a new method, a new page and two new language keys per language. The command words are
 * copied from Baritone's own default commands, read from its sources, and the argument shapes
 * come from each command's help text (see {@link #command(BaritoneParams)}).</p>
 *
 * <p>Everything here is a plain value: no Minecraft types, no Baritone types. That keeps the
 * catalogue testable in the self checks and keeps the "one adapter" rule intact -- the bridge
 * sends a string, and nothing else in the project knows what Baritone is.</p>
 */
public enum BaritoneAction {
    // -- movement ---------------------------------------------------------------------------
    GOTO("goto", Category.MOVE, "map", Arg.POINT),
    GOTO_BLOCK("goto", Category.MOVE, "spyglass", Arg.SINGLE_BLOCK),
    THISWAY("thisway", Category.MOVE, "arrow", Arg.NUMBER, 100),
    TUNNEL("tunnel", Category.MOVE, "iron_shovel", Arg.NUMBERS, 2, 1, 1),
    FOLLOW("follow player", Category.MOVE, "name_tag", Arg.TEXT),
    SURFACE("surface", Category.MOVE, "ladder", Arg.NONE),
    INVERT("invert", Category.MOVE, "compass", Arg.NONE),

    // -- digging ----------------------------------------------------------------------------
    MINE("mine", Category.MINE, "diamond_ore", Arg.BLOCKS_AND_COUNT),
    FARM("farm", Category.MINE, "wheat", Arg.NUMBER, 0),
    PICKUP("pickup", Category.MINE, "hopper", Arg.NONE),

    // -- building ---------------------------------------------------------------------------
    SEL_POS1("sel pos1", Category.BUILD, "golden_axe", Arg.NONE),
    SEL_POS2("sel pos2", Category.BUILD, "iron_axe", Arg.NONE),
    SEL_CLEAR("sel clear", Category.BUILD, "cobweb", Arg.NONE),
    SEL_FILL("sel set", Category.BUILD, "bricks", Arg.SINGLE_BLOCK),
    BUILD("build", Category.BUILD, "oak_planks", Arg.TEXT),

    // -- searching --------------------------------------------------------------------------
    EXPLORE("explore", Category.FIND, "filled_map", Arg.NONE),
    FIND("find", Category.FIND, "torch", Arg.BLOCKS),

    // -- control ----------------------------------------------------------------------------
    PAUSE("pause", Category.CONTROL, "clock", Arg.NONE),
    RESUME("resume", Category.CONTROL, "lime_dye", Arg.NONE),
    CANCEL("cancel", Category.CONTROL, "barrier", Arg.NONE);

    /** Section of the menu; the order of the constants is the order of the sections. */
    public enum Category {
        MOVE,
        MINE,
        BUILD,
        FIND,
        CONTROL;

        public String langKey() {
            return "silkvis.baritone.cat." + this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** What the player has to supply before this action can run. */
    public enum Arg {
        /** Nothing: the command is complete as it stands. */
        NONE,
        /** The block under the crosshair (or the feet), read at press time. */
        POINT,
        /** One picked block. */
        SINGLE_BLOCK,
        /** One or more picked blocks. */
        BLOCKS,
        /** Picked blocks plus a limit, in the {@code mine <n> <blocks>} shape. */
        BLOCKS_AND_COUNT,
        /** One number: a distance or a range. */
        NUMBER,
        /** Three numbers: height, width and depth of a tunnel. */
        NUMBERS,
        /** Free text: a schematic name. */
        TEXT
    }

    private final String command;
    private final Category category;
    private final String icon;
    private final Arg arg;
    private final int[] defaults;

    BaritoneAction(String command, Category category, String icon, Arg arg, int... defaults) {
        this.command = command;
        this.category = category;
        this.icon = icon;
        this.arg = arg;
        this.defaults = defaults;
    }

    /** The command word(s), exactly as Baritone spells them. */
    public String commandWord() {
        return this.command;
    }

    public Category category() {
        return this.category;
    }

    /** Item id used as the tile icon; decoration only, the tile always shows the name too. */
    public String icon() {
        return this.icon;
    }

    public Arg arg() {
        return this.arg;
    }

    /** Defaults of the number fields, in field order. */
    public int[] defaults() {
        return this.defaults.clone();
    }

    /** Stable id for language keys and logs. */
    public String id() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    public String titleKey() {
        return "silkvis.baritone.action." + id();
    }

    public String noteKey() {
        return "silkvis.baritone.action." + id() + ".note";
    }

    /** Hint key of the text field: a player name and a file name are not the same thing. */
    public String textHintKey() {
        return this == FOLLOW ? "silkvis.baritone.follow.hint" : "silkvis.baritone.text.hint";
    }

    /** Step of the plus/minus buttons for the number fields. */
    public int numberStep() {
        return this.arg == Arg.NUMBERS ? 1 : 10;
    }

    /** Lowest value the primary number may take. {@code thisway} needs a distance to work. */
    public int numberMin() {
        return this == THISWAY ? 1 : 0;
    }

    public int numberMax() {
        return this.arg == Arg.NUMBERS ? 256 : 1000;
    }

    /**
     * The command line for these parameters, or {@code null} when something required is missing.
     *
     * <p>Shapes verified against Baritone's own help text, not remembered:</p>
     * <ul>
     *   <li>{@code goto <x> <y> <z>}, {@code goto <block>};</li>
     *   <li>{@code thisway <distance>} -- exactly one argument, so a bare {@code thisway} is not
     *       a command;</li>
     *   <li>{@code tunnel [<height> <width> <depth>]} -- all three or none;</li>
     *   <li>{@code mine <blocks...>} and {@code mine <n> <blocks...>} -- the count is optional
     *       and must therefore be omitted when it is zero;</li>
     *   <li>{@code farm [<range>]} -- a bare {@code farm} farms everything in reach;</li>
     *   <li>{@code follow player <name>} -- the group is singular and the name follows it, so
     *       {@code follow players <name>} is not a usage the command accepts;</li>
     *   <li>{@code sel pos1|pos2|clear|set <block>}, {@code build <filename>};</li>
     *   <li>{@code find <block> [...]}, {@code pickup}, {@code surface}, {@code invert},
     *       {@code explore}, {@code pause}, {@code resume}, {@code cancel}.</li>
     * </ul>
     */
    public String command(BaritoneParams params) {
        BaritoneParams p = params == null ? BaritoneParams.forAction(this) : params;
        String blocks = p.blocksLine();
        return switch (this.arg) {
            case NONE -> this.command;
            case POINT -> p.point() == null ? null
                    : this.command + " " + p.point().x() + " " + p.point().y() + " " + p.point().z();
            case SINGLE_BLOCK -> p.firstBlock().isEmpty()
                    ? null : this.command + " " + p.firstBlock();
            case BLOCKS -> blocks.isEmpty() ? null : this.command + " " + blocks;
            case BLOCKS_AND_COUNT -> blocks.isEmpty() ? null
                    : p.number() > 0 ? this.command + " " + p.number() + " " + blocks
                    : this.command + " " + blocks;
            // thisway takes exactly one argument, so a zero distance is "not ready", while farm
            // with no range is a complete command that harvests everything in reach.
            case NUMBER -> p.number() > 0 ? this.command + " " + p.number()
                    : this.numberMin() > 0 ? null : this.command;
            case NUMBERS -> this.command + " " + p.number() + " " + p.number2() + " "
                    + p.number3();
            case TEXT -> p.text().isBlank() ? null : this.command + " " + p.text().trim();
        };
    }

    /** True when nothing is missing, so the Start button may be enabled. */
    public boolean ready(BaritoneParams params) {
        return command(params) != null;
    }

    /** Every action of one section, in catalogue order. */
    public static List<BaritoneAction> of(Category category) {
        List<BaritoneAction> out = new ArrayList<>();
        for (BaritoneAction action : values()) {
            if (action.category == category) {
                out.add(action);
            }
        }
        return out;
    }
}

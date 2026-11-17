package silkvis.automation.baritone;

import java.util.List;

/**
 * One row of the Mine page: a name the player recognises and the block ids Baritone must mine.
 *
 * <p>Why a fixed list instead of the game's block registry: Baritone's own reference for
 * {@code mine} is a handful of ores, and a page that listed every block would bury them under a
 * thousand entries and turn a two-click job into typing. The ids are written out in full, with
 * the deepslate variant next to its stone one, because a player asking for diamonds wants both
 * and should not have to know that the second id exists.</p>
 *
 * <p>The ids are unqualified on purpose: Baritone reads a bare name as {@code minecraft:name},
 * and the short form is what its own help text shows. The id is a language key, so it is never
 * renamed.</p>
 */
public enum BaritoneOre {
    DIAMOND("diamond", "diamond_ore", "deepslate_diamond_ore"),
    IRON("iron", "iron_ore", "deepslate_iron_ore"),
    COAL("coal", "coal_ore", "deepslate_coal_ore"),
    COPPER("copper", "copper_ore", "deepslate_copper_ore"),
    GOLD("gold", "gold_ore", "deepslate_gold_ore"),
    REDSTONE("redstone", "redstone_ore", "deepslate_redstone_ore"),
    LAPIS("lapis", "lapis_ore", "deepslate_lapis_ore"),
    EMERALD("emerald", "emerald_ore", "deepslate_emerald_ore"),
    QUARTZ("quartz", "nether_quartz_ore"),
    NETHER_GOLD("nether_gold", "nether_gold_ore"),
    ANCIENT_DEBRIS("ancient_debris", "ancient_debris");

    private final String id;
    private final List<String> blocks;

    BaritoneOre(String id, String... blocks) {
        this.id = id;
        this.blocks = List.of(blocks);
    }

    /** Stable id used in language keys and logs; never renamed. */
    public String id() {
        return this.id;
    }

    /** Block ids as Baritone spells them, without the {@code minecraft:} prefix. */
    public List<String> blocks() {
        return this.blocks;
    }

    /** Language key of the name shown on the button. */
    public String langKey() {
        return "silkvis.baritone.ore." + this.id;
    }

    /** Blocks as one space-separated argument list, the shape {@code mine} takes. */
    public String blockList() {
        return String.join(" ", this.blocks);
    }
}

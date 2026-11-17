package silkvis.automation.baritone;

import java.util.ArrayList;
import java.util.List;

/**
 * A block a player can point a Baritone task at, with the ids Baritone has to hear.
 *
 * <p>The list is curated, not the game's registry. A menu that offered every block would bury
 * "diamond ore" under a thousand entries and turn two clicks into typing; what a player actually
 * asks for is a short list of ores and a handful of bulk materials. Deepslate variants sit next
 * to their stone ones inside a single entry, because a player asking for diamonds wants both and
 * should not have to know that the second id exists.</p>
 *
 * <p>Ids are unqualified on purpose: Baritone reads a bare name as {@code minecraft:name}, and
 * the short form is what its own help text shows. The first id of each entry doubles as its
 * icon, because every block here has a block item under the same name.</p>
 */
public enum BaritoneBlock {
    // -- ores -------------------------------------------------------------------------------
    DIAMOND(Group.ORE, "diamond", "diamond_ore", "deepslate_diamond_ore"),
    IRON(Group.ORE, "iron", "iron_ore", "deepslate_iron_ore"),
    COAL(Group.ORE, "coal", "coal_ore", "deepslate_coal_ore"),
    COPPER(Group.ORE, "copper", "copper_ore", "deepslate_copper_ore"),
    GOLD(Group.ORE, "gold", "gold_ore", "deepslate_gold_ore"),
    REDSTONE(Group.ORE, "redstone", "redstone_ore", "deepslate_redstone_ore"),
    LAPIS(Group.ORE, "lapis", "lapis_ore", "deepslate_lapis_ore"),
    EMERALD(Group.ORE, "emerald", "emerald_ore", "deepslate_emerald_ore"),
    QUARTZ(Group.ORE, "quartz", "nether_quartz_ore"),
    NETHER_GOLD(Group.ORE, "nether_gold", "nether_gold_ore"),
    ANCIENT_DEBRIS(Group.ORE, "ancient_debris", "ancient_debris"),

    // -- bulk blocks ------------------------------------------------------------------------
    STONE(Group.BLOCK, "stone", "stone", "deepslate", "granite", "diorite", "andesite", "tuff"),
    DIRT(Group.BLOCK, "dirt", "dirt", "grass_block", "coarse_dirt"),
    SAND(Group.BLOCK, "sand", "sand", "red_sand", "gravel"),
    LOGS(Group.BLOCK, "logs", "oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log",
            "dark_oak_log", "mangrove_log", "cherry_log"),
    LEAVES(Group.BLOCK, "leaves", "oak_leaves", "birch_leaves", "spruce_leaves", "jungle_leaves",
            "acacia_leaves", "dark_oak_leaves"),
    OBSIDIAN(Group.BLOCK, "obsidian", "obsidian"),
    NETHERRACK(Group.BLOCK, "netherrack", "netherrack"),
    END_STONE(Group.BLOCK, "end_stone", "end_stone"),
    SNOW(Group.BLOCK, "snow", "snow_block", "ice", "packed_ice", "blue_ice"),
    CLAY(Group.BLOCK, "clay", "clay", "terracotta");

    /** Which half of the grid an entry belongs to. */
    public enum Group {
        ORE,
        BLOCK
    }

    private final Group group;
    private final String id;
    private final List<String> blocks;

    BaritoneBlock(Group group, String id, String... blocks) {
        this.group = group;
        this.id = id;
        this.blocks = List.of(blocks);
    }

    public Group group() {
        return this.group;
    }

    /** Stable id used in language keys and logs; never renamed. */
    public String id() {
        return this.id;
    }

    /** Block ids as Baritone spells them, without the {@code minecraft:} prefix. */
    public List<String> blocks() {
        return this.blocks;
    }

    /** Id of the icon: the entry's first block, which has a block item of the same name. */
    public String icon() {
        return this.blocks.get(0);
    }

    /** Language key of the name shown next to the icon. */
    public String langKey() {
        return "silkvis.baritone.block." + this.id;
    }

    /** Blocks as one space-separated argument list, the shape {@code mine} takes. */
    public String blockList() {
        return String.join(" ", this.blocks);
    }

    /** All entries of one group, in menu order. */
    public static List<BaritoneBlock> of(Group group) {
        List<BaritoneBlock> out = new ArrayList<>();
        for (BaritoneBlock block : values()) {
            if (block.group == group) {
                out.add(block);
            }
        }
        return out;
    }
}

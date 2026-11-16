package silkvis.automation;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Picks the hotbar slot holding the tool a block needs (A10 tool select, A11 tool guard).
 *
 * <p>Why this is a shared helper and not a goal: "hold the right pickaxe" is not something the
 * player wants done, it is a prerequisite of every block-breaking goal. As its own goal it could
 * not run at the same time as the goal that needs it, because the engine runs exactly one goal at
 * a time. So it is a pure function that goals call, and the catalogue entry is what the UI shows
 * while a run is holding a tool.</p>
 *
 * <p>What it deliberately does not do: swap from the main inventory. That is A13's job, it costs
 * a screen interaction, and doing it here would make the executor open a menu mid-walk. Only the
 * nine hotbar slots are considered, which is the same constraint {@code hotbarOnly} describes.</p>
 *
 * <p>Tool choice follows the vanilla rule that matters for speed: a block is mined fastest by the
 * matching tool of the highest tier. Wrong-tool is not treated as "impossible" -- breaking stone
 * with a sword works, just slowly -- so the helper returns the best available slot and lets the
 * caller decide whether the resulting {@code breakTicks} is acceptable.</p>
 */
public final class ToolSelect {
    private ToolSelect() {
    }

    /** The tool families the engine distinguishes; order is by tier inside a family. */
    public enum Tool {
        NONE,
        WOOD,
        STONE,
        IRON,
        GOLD,
        DIAMOND,
        NETHERITE
    }

    /** Which suffix a tool family uses for the blocks it speeds up. */
    private enum Family {
        PICKAXE("pickaxe", Set.of("pickaxe")),
        AXE("axe", Set.of("axe")),
        SHOVEL("shovel", Set.of("shovel")),
        HOE("hoe", Set.of("hoe")),
        SHEARS("shears", Set.of("shears")),
        SWORD("sword", Set.of("sword"));

        private final String slug;
        private final Set<String> keys;

        Family(String slug, Set<String> keys) {
            this.slug = slug;
            this.keys = keys;
        }

        String slug() {
            return this.slug;
        }

        boolean matches(String itemPath) {
            for (String key : this.keys) {
                if (itemPath.endsWith("_" + key) || itemPath.equals(key)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Registry ids of blocks that must be broken with a pickaxe before they drop anything, and
     * which cannot be broken at all without one. {@link Snapshot#breakTicks} already answers
     * "how long", so this list exists only for the goal that needs to know <em>why</em>.
     */
    private static final Set<String> NEEDS_PICKAXE = Set.of(
            "minecraft:stone", "minecraft:cobblestone", "minecraft:deepslate",
            "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
            "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore",
            "minecraft:ancient_debris", "minecraft:obsidian", "minecraft:netherrack",
            "minecraft:blackstone", "minecraft:basalt", "minecraft:andesite",
            "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:calcite");

    private static final Set<String> NEEDS_AXE = Set.of(
            "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
            "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
            "minecraft:mangrove_log", "minecraft:cherry_log", "minecraft:pale_oak_log",
            "minecraft:crimson_stem", "minecraft:warped_stem");

    private static final Set<String> NEEDS_SHOVEL = Set.of(
            "minecraft:dirt", "minecraft:grass_block", "minecraft:sand", "minecraft:gravel",
            "minecraft:clay", "minecraft:snow_block", "minecraft:soul_sand", "minecraft:soul_soil",
            "minecraft:mud", "minecraft:podzol", "minecraft:mycelium", "minecraft:farmland",
            "minecraft:snow");

    /**
     * The family a block wants, or {@link Family#PICKAXE} for anything unrecognised.
     *
     * <p>Defaulting to pickaxe is the safe direction: stone is the overwhelming majority of what
     * a mining goal touches, and a wrong pickaxe is slower rather than harmful, while a wrong
     * sword on stone is the classic "why is this taking forever".</p>
     */
    static Family familyFor(String blockId) {
        if (blockId == null) {
            return Family.PICKAXE;
        }
        String key = blockId.toLowerCase(Locale.ROOT);
        if (NEEDS_AXE.contains(key)) {
            return Family.AXE;
        }
        if (NEEDS_SHOVEL.contains(key)) {
            return Family.SHOVEL;
        }
        if (key.contains("_leaves") || key.endsWith(":cobweb") || key.endsWith(":tall_grass")
                || key.endsWith(":short_grass") || key.endsWith(":fern")) {
            return Family.SHEARS;
        }
        if (key.contains("_wool") || key.contains("_planks") || key.endsWith(":bookshelf")) {
            return Family.AXE;
        }
        if (key.contains("_crop") || key.contains("wheat") || key.contains("carrots")
                || key.contains("potatoes") || key.contains("beetroots")) {
            return Family.HOE;
        }
        return Family.PICKAXE;
    }

    /** Index of the hotbar slot with the best tool for the block, or {@code -1}. */
    public static int bestSlotFor(Snapshot world, String blockId) {
        String[] hotbar = world.hotbar();
        if (hotbar == null) {
            return -1;
        }
        Family wanted = familyFor(blockId);
        int bestSlot = -1;
        Tool bestTier = Tool.NONE;
        for (int i = 0; i < hotbar.length; i++) {
            Tool tier = tierOf(hotbar[i], wanted);
            if (tier.ordinal() > bestTier.ordinal()) {
                bestTier = tier;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /**
     * Tier of the item when it belongs to the wanted family, or {@link Tool#NONE}.
     *
     * <p>Shears have no tier: they are the only item that works on leaves at speed, so they are
     * reported as the top tier. Reporting them as {@code WOOD} would let a wooden axe win on
     * leaves purely by ordinal, which is backwards.</p>
     */
    static Tool tierOf(String itemId, Family wanted) {
        if (itemId == null || itemId.isEmpty()) {
            return Tool.NONE;
        }
        String key = itemId.toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        if (!wanted.matches(path)) {
            return Tool.NONE;
        }
        if (wanted == Family.SHEARS) {
            return Tool.NETHERITE;
        }
        if (path.startsWith("wooden_") || path.startsWith("golden_")) {
            // Golden tools are the fastest but the least durable; for a speed question they
            // rank with wood, and treating them above diamond would exhaust them in seconds.
            return Tool.WOOD;
        }
        if (path.startsWith("stone_")) {
            return Tool.STONE;
        }
        if (path.startsWith("iron_")) {
            return Tool.IRON;
        }
        if (path.startsWith("diamond_")) {
            return Tool.DIAMOND;
        }
        if (path.startsWith("netherite_")) {
            return Tool.NETHERITE;
        }
        return Tool.NONE;
    }

    /**
     * Steps that put the right tool in hand, empty when the held slot already has it.
     *
     * @return at most one {@link Step#selectSlot} step
     */
    public static List<Step> stepsToHoldTool(Snapshot world, String blockId) {
        int slot = bestSlotFor(world, blockId);
        if (slot < 0) {
            return List.of();
        }
        if (world.selectedSlot() == slot) {
            return List.of();
        }
        return List.of(Step.selectSlot(slot));
    }

    /** True when the block is one the goal cannot mine without a matching tool at all. */
    public static boolean requiresPickaxe(String blockId) {
        return blockId != null && NEEDS_PICKAXE.contains(blockId.toLowerCase(Locale.ROOT));
    }
}

package silkvis.automation.goals;

import silkvis.automation.AutomationMode;
import silkvis.automation.Budget;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StopReason;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Eat when hungry" (A09 auto eat).
 *
 * <p>Why this goal exists and why it is not just a safety rule: the engine already stops every
 * run when food drops to {@code foodFloor} and reports {@link StopReason#LOW_HEALTH}. That is the
 * wrong answer for a hungry player -- automation gives up instead of fixing a problem a carrot
 * solves. The goal runs before the gate matters, eats, and the run continues.</p>
 *
 * <p>Scope kept deliberately small:</p>
 * <ul>
 *   <li>Only the hotbar is read. Swapping inventory slots is inventory work (A13), and doing it
 *       from here would make two goals fight over the same selection.</li>
 *   <li>Food is recognised by registry id from a fixed list, not by a food-component lookup:
 *       {@link Snapshot} has no component access, and a wrong guess here means either eating a
 *       rock or starving next to a steak. The list is the vanilla edible set; a modded food that
 *       is not listed is simply not eaten, which is the safe direction.</li>
 *   <li>Anything unsafe (poisonous potato, spider eye) is refused. The engine is not allowed to
 *       decide that a hunger point is worth a debuff.</li>
 * </ul>
 *
 * <p>One step list per tick: select the slot, use the item, then wait. The wait is not padding --
 * eating takes {@code 32} ticks and using the item on the same tick as the selection would send
 * the use to the previously held slot.</p>
 */
public class EatGoal extends AbstractGoal {
    /** Food level at or below which the goal wants to eat. Vanilla starts warning at 6. */
    public static final int DEFAULT_THRESHOLD = 14;

    /**
     * Vanilla foods that are safe to eat, by registry id. Kept as written-out ids instead of a
     * prefix test so a non-food that happens to share a name ("minecraft:cake" is edible only
     * through its block form) cannot slip in.
     */
    private static final Set<String> FOODS = Set.of(
            "minecraft:apple",
            "minecraft:golden_apple",
            "minecraft:enchanted_golden_apple",
            "minecraft:bread",
            "minecraft:cooked_beef",
            "minecraft:cooked_porkchop",
            "minecraft:cooked_chicken",
            "minecraft:cooked_mutton",
            "minecraft:cooked_rabbit",
            "minecraft:cooked_cod",
            "minecraft:cooked_salmon",
            "minecraft:baked_potato",
            "minecraft:beef",
            "minecraft:porkchop",
            "minecraft:chicken",
            "minecraft:mutton",
            "minecraft:rabbit",
            "minecraft:cod",
            "minecraft:salmon",
            "minecraft:carrot",
            "minecraft:potato",
            "minecraft:beetroot",
            "minecraft:beetroot_soup",
            "minecraft:mushroom_stew",
            "minecraft:rabbit_stew",
            "minecraft:suspicious_stew",
            "minecraft:melon_slice",
            "minecraft:sweet_berries",
            "minecraft:glow_berries",
            "minecraft:chorus_fruit",
            "minecraft:honey_bottle",
            "minecraft:dried_kelp",
            "minecraft:pumpkin_pie",
            "minecraft:cookie",
            "minecraft:tropical_fish");

    /**
     * Explicitly refused ids, kept as a written-out list even though none of them is in
     * {@link #FOODS}: the check is what makes the refusal a stated decision instead of a
     * side effect of the allow-list. A later edit that adds one of these to the allow list
     * then has to delete it here, and {@code refusedFoodStaysRefused} fails the build if the
     * two lists ever overlap.
     */
    private static final Set<String> REFUSED = Set.of(
            "minecraft:poisonous_potato",
            "minecraft:spider_eye",
            "minecraft:rotten_flesh",
            "minecraft:pufferfish");

    private final int threshold;
    private final int eatTicks;
    private boolean ateThisRun;
    private String lastEaten = "";

    public EatGoal() {
        this(DEFAULT_THRESHOLD, 40);
    }

    /**
     * @param threshold eat at or below this food level
     * @param eatTicks  ticks to wait after using the item (vanilla eating is 32; a margin covers
     *                  the item-use cooldown)
     */
    public EatGoal(int threshold, int eatTicks) {
        this.threshold = Math.max(1, Math.min(20, threshold));
        this.eatTicks = Math.max(32, eatTicks);
    }

    @Override
    public String id() {
        return "A09";
    }

    @Override
    public String titleKey() {
        return "silkvis.automation.auto_eat";
    }

    @Override
    public AutomationMode requiredMode() {
        // Using an item is a FULL action in the executor (it right-clicks), so the goal may not
        // claim to be MOVE-only: the engine would then run it in a mode the executor refuses.
        return AutomationMode.FULL;
    }

    @Override
    protected void onReset(Snapshot world) {
        this.ateThisRun = false;
        this.lastEaten = "";
    }

    @Override
    protected void next(Snapshot world, Budget budget, List<Step> steps) {
        if (world.food() > this.threshold) {
            // Fed well enough. The goal is not "done": it is a standing order that will be
            // needed again after the next fight, so it waits instead of finishing.
            return;
        }
        int slot = foodSlot(world);
        if (slot < 0) {
            // Hungry with nothing to eat. This is the one case where giving up is honest, and
            // the reason names it instead of pretending the run succeeded.
            stop(StopReason.NO_TOOL);
            return;
        }
        this.lastEaten = world.hotbar()[slot];
        this.ateThisRun = true;
        if (world.selectedSlot() != slot) {
            steps.add(Step.selectSlot(slot));
        }
        steps.add(Step.useItem());
        steps.add(Step.wait(this.eatTicks));
    }

    /**
     * First hotbar slot holding something safe to eat, or {@code -1}.
     *
     * <p>The selected slot wins ties: if the player already holds bread, eating must not first
     * swap to a different food and back, which would be visible and pointless.</p>
     */
    private int foodSlot(Snapshot world) {
        String[] hotbar = world.hotbar();
        if (hotbar == null) {
            return -1;
        }
        int selected = world.selectedSlot();
        if (selected >= 0 && selected < hotbar.length && edible(hotbar[selected])) {
            return selected;
        }
        for (int i = 0; i < hotbar.length; i++) {
            if (edible(hotbar[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * True when the id is food the goal is willing to eat.
     *
     * <p>Refusal is checked first and is the deciding rule, so an id can never be eaten by
     * being added to the allow list alone.</p>
     */
    public static boolean edible(String id) {
        if (id == null) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        return !REFUSED.contains(key) && FOODS.contains(key);
    }

    /** True when the two tables contradict each other; the self check fails the build on it. */
    public static boolean listsOverlap() {
        for (String refused : REFUSED) {
            if (FOODS.contains(refused)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String progress() {
        if (!this.ateThisRun) {
            return "";
        }
        String name = this.lastEaten == null ? "" : this.lastEaten;
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}

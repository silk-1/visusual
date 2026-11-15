package silkvis.automation;

import java.util.List;

/**
 * Which goal the situation calls for, decided from a {@link Snapshot} alone.
 *
 * <p>The rules are data, not judgement calls scattered through the client, and the order is the
 * design: <b>danger before loot, loot before walking</b>. Being on fire outranks a dropped
 * diamond; a hostile mob two blocks away outranks both. Anything that is not urgent enough to be
 * decided here is left to the caller, which knows what the camera is looking at.</p>
 *
 * <p>Deliberately Minecraft-free so the ordering can be asserted in the self checks: the classic
 * automation bug is a rule table where "low health" sits below "pick up item".</p>
 */
public final class Selector {
    /** Hostile mob this close and the emergency goal outranks everything else. */
    public static final double THREAT_RADIUS = 6.0;
    /** Dropped items are worth a walk only when they are near. */
    public static final double LOOT_RADIUS = 8.0;

    /** The chosen goal, or why nothing was chosen. */
    public record Pick(String goalId, StopReason reason) {
        public boolean found() {
            return goalId != null;
        }

        static Pick of(String id) {
            return new Pick(id, StopReason.NONE);
        }

        static Pick none(StopReason why) {
            return new Pick(null, why);
        }
    }

    private Selector() {
    }

    /**
     * Picks the urgent goal for this snapshot.
     *
     * @return a pick with a goal id, or a pick carrying {@link StopReason#NONE} when nothing is
     * urgent (the caller may still choose to walk somewhere) or a reason when automation is off
     */
    public static Pick urgent(Snapshot world, AutomationMode mode) {
        if (world == null) {
            return Pick.none(StopReason.WORLD_CHANGED);
        }
        if (mode == null || mode == AutomationMode.OFF) {
            return Pick.none(StopReason.MODE_TOO_LOW);
        }
        BlockPos feet = world.player();
        if (inDanger(world, feet) || threatNearby(world, THREAT_RADIUS)) {
            return Pick.of("A22");
        }
        if (lootNearby(world, LOOT_RADIUS)) {
            return Pick.of("A21");
        }
        // Nothing urgent: the caller decides between "walk to what I look at" and "do nothing".
        return Pick.none(StopReason.NONE);
    }

    /** Standing in or on a block that hurts, right now. */
    public static boolean inDanger(Snapshot world, BlockPos feet) {
        Movement.Grid grid = world.grid();
        return grid.dangerous(feet.x(), feet.y(), feet.z())
                || grid.dangerous(feet.x(), feet.y() - 1, feet.z());
    }

    public static boolean threatNearby(Snapshot world, double radius) {
        if (radius <= 0) {
            return false;
        }
        for (Snapshot.EntityInfo entity : world.entities()) {
            if (entity.hostile() && entity.distance() <= radius) {
                return true;
            }
        }
        return false;
    }

    public static boolean lootNearby(Snapshot world, double radius) {
        if (radius <= 0) {
            return false;
        }
        for (Snapshot.EntityInfo entity : world.entities()) {
            if (!entity.hostile() && isItem(entity.type()) && entity.distance() <= radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Item entities arrive as registry ids ({@code minecraft:item}); the fake world in the checks
     * writes the bare {@code item}, so both spellings count. A prefix is not enough: an id like
     * {@code minecraft:item_frame} is not loot.
     */
    static boolean isItem(String type) {
        if (type == null) {
            return false;
        }
        int colon = type.indexOf(':');
        String path = colon >= 0 ? type.substring(colon + 1) : type;
        return "item".equals(path);
    }

    /** Convenience for callers that only want the id. */
    public static List<String> goalOrder() {
        return List.of("A22", "A21");
    }
}

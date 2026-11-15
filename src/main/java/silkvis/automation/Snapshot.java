package silkvis.automation;

/**
 * Read-only view of everything a goal may know about the world in one tick.
 *
 * <p>Why a snapshot instead of live Minecraft objects: goals are pure functions of this data, so
 * they can be exercised in the self checks with a hand-built world, and no goal can accidentally
 * keep a reference to an entity that dies three ticks later. It also makes the "no access to the
 * player" rule enforceable by the type system instead of by review.</p>
 *
 * <p>All coordinates are block coordinates. {@code player} is the block the player's feet are in.</p>
 */
public interface Snapshot {
    long tick();

    BlockPos player();

    /** Yaw in degrees, -180..180, as the camera sees it. */
    float yaw();

    /** Pitch in degrees, -90..90. */
    float pitch();

    /** True when the player is standing on something the game considers ground. */
    boolean onGround();

    float health();

    int food();

    /** The world the player is in; equal values mean the same dimension. */
    String dimension();

    /** Blocks the goal may query. */
    Movement.Grid grid();

    /** Entities within the configured reach radius, nearest first. */
    java.util.List<EntityInfo> entities();

    /**
     * What is in the hotbar, index 0..8, {@code null} for an empty slot. Item ids are registry
     * ids ({@code minecraft:iron_pickaxe}) so goals can compare without Minecraft classes.
     */
    String[] hotbar();

    /** Selected hotbar slot, 0..8. */
    int selectedSlot();

    /** Block at a position as a registry id ({@code minecraft:stone}), or {@code air}. */
    String blockId(int x, int y, int z);

    /**
     * Ticks the current tool needs for one block, capped; {@link Integer#MAX_VALUE} when the
     * block cannot be broken at all (bedrock, unbreakable). 0 means "instant".
     */
    int breakTicks(int x, int y, int z);

    /**
     * True when the block is a crop that is fully grown ({@code age} at its maximum). Growth is a
     * block-state fact: deriving it from break time (as an earlier draft did) would call a crop
     * ripe because a hoe happened to be in hand.
     */
    boolean cropRipe(int x, int y, int z);

    /** A readable snapshot for the status line and for failures. */
    record EntityInfo(int id, String type, double x, double y, double z, float health,
                      boolean hostile, double distance) {
    }
}

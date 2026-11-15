package silkvis.verify;

import silkvis.automation.BlockPos;
import silkvis.automation.Movement;
import silkvis.automation.Snapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A hand-built world for the automation checks: a height map of block ids, a player, and the
 * handful of entity/item facts the goals read.
 *
 * <p>Deliberately small and literal. The point of the automation tests is that the pathfinder and
 * the goals are pure functions of this data, so a scenario is written as "here are the blocks,
 * here is where I stand, here is what I expect the plan to be" and not as a mock framework.</p>
 */
final class FakeWorld implements Snapshot, Movement.Grid {
    private final Map<BlockPos, String> blocks = new HashMap<>();
    private final Set<BlockPos> dangerous = new HashSet<>();
    private final Set<BlockPos> doors = new HashSet<>();
    private final List<Snapshot.EntityInfo> entities = new ArrayList<>();
    private final Map<BlockPos, Integer> breakTicks = new HashMap<>();
    private final Set<BlockPos> ripe = new HashSet<>();
    private BlockPos player = new BlockPos(0, 64, 0);
    private float health = 20f;
    private int food = 20;
    private boolean onGround = true;
    private String dimension = "minecraft:overworld";
    private String[] hotbar = new String[9];
    private int selectedSlot;
    private float yaw;
    private float pitch;
    private long tick;

    /** Fills a solid floor of stone at {@code y-1} for the given square around the player. */
    FakeWorld withFloor(int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                blocks.put(new BlockPos(x, player.y() - 1, z), "minecraft:stone");
            }
        }
        return this;
    }

    FakeWorld withBlock(int x, int y, int z, String id) {
        blocks.put(new BlockPos(x, y, z), id);
        return this;
    }

    FakeWorld withDangerous(int x, int y, int z) {
        dangerous.add(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld withDoor(int x, int y, int z) {
        doors.add(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld withBreakTicks(int x, int y, int z, int ticks) {
        breakTicks.put(new BlockPos(x, y, z), ticks);
        return this;
    }

    FakeWorld withRipe(int x, int y, int z) {
        ripe.add(new BlockPos(x, y, z));
        return this;
    }

    FakeWorld withItem(int id, double x, double y, double z) {
        double distance = Math.sqrt(player.distanceSquared(new BlockPos(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))));
        entities.add(new EntityInfo(id, "item", x, y, z, 0f, false, distance));
        return this;
    }

    FakeWorld withEntity(int id, String type, double x, double y, double z, boolean hostile) {
        double distance = Math.sqrt(player.distanceSquared(new BlockPos(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))));
        entities.add(new EntityInfo(id, type, x, y, z, 20f, hostile, distance));
        return this;
    }

    FakeWorld at(int x, int y, int z) {
        this.player = new BlockPos(x, y, z);
        return this;
    }

    FakeWorld withHealth(float value) {
        this.health = value;
        return this;
    }

    FakeWorld withFood(int value) {
        this.food = value;
        return this;
    }

    FakeWorld inDimension(String id) {
        this.dimension = id;
        return this;
    }

    FakeWorld tick(long value) {
        this.tick = value;
        return this;
    }

    // ------------------------------------------------------------------ Snapshot

    @Override
    public long tick() {
        return this.tick;
    }

    @Override
    public BlockPos player() {
        return this.player;
    }

    @Override
    public float yaw() {
        return this.yaw;
    }

    @Override
    public float pitch() {
        return this.pitch;
    }

    @Override
    public boolean onGround() {
        return this.onGround;
    }

    @Override
    public float health() {
        return this.health;
    }

    @Override
    public int food() {
        return this.food;
    }

    @Override
    public String dimension() {
        return this.dimension;
    }

    @Override
    public Movement.Grid grid() {
        return this;
    }

    @Override
    public List<EntityInfo> entities() {
        return this.entities;
    }

    @Override
    public String[] hotbar() {
        return this.hotbar;
    }

    @Override
    public int selectedSlot() {
        return this.selectedSlot;
    }

    @Override
    public String blockId(int x, int y, int z) {
        return this.blocks.getOrDefault(new BlockPos(x, y, z), "minecraft:air");
    }

    @Override
    public int breakTicks(int x, int y, int z) {
        return this.breakTicks.getOrDefault(new BlockPos(x, y, z), 10);
    }

    @Override
    public boolean cropRipe(int x, int y, int z) {
        return this.ripe.contains(new BlockPos(x, y, z));
    }

    // ------------------------------------------------------------------ Grid

    @Override
    public boolean solid(int x, int y, int z) {
        String id = blockId(x, y, z);
        return !"minecraft:air".equals(id) && !id.endsWith("_door");
    }

    @Override
    public boolean passable(int x, int y, int z) {
        String id = blockId(x, y, z);
        return "minecraft:air".equals(id) || id.endsWith("_door")
                || "minecraft:water".equals(id);
    }

    @Override
    public boolean water(int x, int y, int z) {
        return "minecraft:water".equals(blockId(x, y, z));
    }

    @Override
    public boolean breakable(int x, int y, int z) {
        return solid(x, y, z) && !"minecraft:bedrock".equals(blockId(x, y, z));
    }

    @Override
    public boolean door(int x, int y, int z) {
        return this.doors.contains(new BlockPos(x, y, z));
    }

    @Override
    public boolean dangerous(int x, int y, int z) {
        return this.dangerous.contains(new BlockPos(x, y, z));
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int maxY() {
        return 320;
    }

    /** Every block explicitly placed, for assertions about what a plan did. */
    Map<BlockPos, String> placed() {
        return this.blocks;
    }
}

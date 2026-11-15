package silkvis.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.world.ClientWorld;
import silkvis.automation.Snapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the game into the MineCraft-free {@link Snapshot} the goals and the pathfinder consume.
 *
 * <p>This is the second half of the adapter boundary (the executor is the other one): every
 * {@code net.minecraft} call the planner needs lives here, and everything below is plain data. The
 * class is also where the cheap-but-important decisions are made once instead of in each goal:</p>
 *
 * <ul>
 *   <li>a <b>grid</b> that only answers from loaded chunks, so a goal can never pull a chunk in;</li>
 *   <li>a fixed, small <b>query budget per snapshot</b>: reading a snapshot is bounded, not the
 *       goals' problem;</li>
 *   <li><b>danger</b> as its own predicate (lava, fire, magma, cactus, powder snow), because "is
 *       this solid" and "will this kill me" are different questions;</li>
 *   <li><b>break time</b> as a small estimate capped for the goals, never the real break progress.</li>
 * </ul>
 */
public final class AutomationSnapshot implements Snapshot {
    /** Same numbers as the goals' reach, kept here so estimates and checks agree. */
    private static final int UNBREAKABLE = Integer.MAX_VALUE;

    private final ClientWorld world;
    private final ClientPlayerEntity player;
    private final float health;
    private final int food;
    private final boolean onGround;
    private final String dimension;
    private final BlockPos position;
    private final List<EntityInfo> entities;
    private final String[] hotbar;
    private final int selectedSlot;
    private final long tick;
    private final Grid grid;

    private AutomationSnapshot(ClientWorld world, ClientPlayerEntity player, long tick,
                               double entityRadius) {
        this.world = world;
        this.player = player;
        this.tick = tick;
        this.health = player.getHealth();
        this.food = player.getHungerManager().getFoodLevel();
        this.onGround = player.isOnGround();
        this.dimension = world.getRegistryKey().getValue().toString();
        this.position = player.getBlockPos();
        this.hotbar = readHotbar(player);
        this.selectedSlot = player.getInventory().getSelectedSlot();
        this.entities = readEntities(world, player, entityRadius);
        this.grid = new LiveGrid(world);
    }

    /**
     * Builds a snapshot, or returns null when there is nothing to read (main menu, world leaving).
     * Never throws: the caller is a tick loop, and a failed snapshot must become "automation
     * stops" with a reason, not a crash in the middle of a tick.
     */
    public static AutomationSnapshot capture(MinecraftClient client, long tick,
                                             double entityRadius) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        try {
            return new AutomationSnapshot(client.world, client.player, tick, entityRadius);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String[] readHotbar(ClientPlayerEntity player) {
        String[] slots = new String[9];
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            slots[i] = stack == null || stack.isEmpty() ? null
                    : Registries.ITEM.getId(stack.getItem()).toString();
        }
        return slots;
    }

    private static List<EntityInfo> readEntities(ClientWorld world, ClientPlayerEntity player,
                                                 double radius) {
        List<EntityInfo> out = new ArrayList<>();
        if (radius <= 0) {
            return out;
        }
        // ClientWorld exposes its entity list directly; the distance filter below is what makes
        // this a "radar" and not a dump of everything loaded.
        // ClientWorld declares the list as a raw Iterable, so the element type has to be named.
        for (Entity entity : (Iterable<Entity>) world.getEntities()) {
            if (entity == player || !entity.isAlive()) {
                continue;
            }
            double distance = Math.sqrt(player.squaredDistanceTo(entity));
            if (distance > radius) {
                continue;
            }
            boolean hostile = entity instanceof HostileEntity;
            String type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            float entityHealth = entity instanceof LivingEntity living ? living.getHealth() : 0f;
            out.add(new EntityInfo(entity.getId(), type, entity.getX(), entity.getY(),
                    entity.getZ(), entityHealth, hostile, distance));
        }
        out.sort(java.util.Comparator.comparingDouble(EntityInfo::distance));
        return out;
    }

    @Override
    public long tick() {
        return this.tick;
    }

    @Override
    public silkvis.automation.BlockPos player() {
        return new silkvis.automation.BlockPos(this.position.getX(), this.position.getY(),
                this.position.getZ());
    }

    @Override
    public float yaw() {
        return this.player.getYaw();
    }

    @Override
    public float pitch() {
        return this.player.getPitch();
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
    public silkvis.automation.Movement.Grid grid() {
        return this.grid;
    }

    @Override
    public List<EntityInfo> entities() {
        return this.entities;
    }

    @Override
    public String[] hotbar() {
        return this.hotbar.clone();
    }

    @Override
    public int selectedSlot() {
        return this.selectedSlot;
    }

    @Override
    public String blockId(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!this.world.isPosLoaded(pos)) {
            // Reading an unloaded position would load the chunk. A goal that meets air it cannot
            // see stops instead of generating terrain to walk into.
            return "minecraft:air";
        }
        BlockState state = this.world.getBlockState(pos);
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    @Override
    public int breakTicks(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!this.world.isPosLoaded(pos)) {
            return UNBREAKABLE;
        }
        BlockState state = this.world.getBlockState(pos);
        if (state.isAir()) {
            return 0;
        }
        float hardness = state.getHardness(this.world, pos);
        if (hardness < 0) {
            // Bedrock, barrier, portal frame: negative hardness means "never".
            return UNBREAKABLE;
        }
        ItemStack tool = this.player.getMainHandStack();
        boolean suitable = tool != null && tool.isSuitableFor(state);
        // A coarse estimate is enough for the goal's decisions (it answers "can I, and roughly how
        // long"), and it deliberately does not try to model the break progress of this client.
        if (state.isToolRequired() && !suitable) {
            // Needs a class of tool the player is not holding: better a reason than a slow dig.
            return UNBREAKABLE;
        }
        double ticks = (hardness * (suitable ? 1.5 : 5.0)) + 1.0;
        return (int) Math.min(ticks, 100);
    }

    @Override
    public boolean cropRipe(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!this.world.isPosLoaded(pos)) {
            return false;
        }
        BlockState state = this.world.getBlockState(pos);
        // Age is the one property every crop has; a block without it is not a crop at all.
        if (!(state.getBlock() instanceof net.minecraft.block.CropBlock crop)) {
            return false;
        }
        return state.get(crop.getAgeProperty()) >= crop.getMaxAge();
    }

    /** The live grid: answers only from loaded chunks and prices nothing. */
    private static final class LiveGrid implements silkvis.automation.Movement.Grid {
        private final ClientWorld world;

        LiveGrid(ClientWorld world) {
            this.world = world;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            BlockState state = state(x, y, z);
            if (state == null || state.isAir()) {
                return false;
            }
            return !state.getCollisionShape(this.world,
                    new BlockPos(x, y, z)).isEmpty();
        }

        @Override
        public boolean passable(int x, int y, int z) {
            BlockState state = state(x, y, z);
            if (state == null) {
                return false;
            }
            return state.getCollisionShape(this.world, new BlockPos(x, y, z)).isEmpty();
        }

        @Override
        public boolean water(int x, int y, int z) {
            BlockState state = state(x, y, z);
            return state != null && !state.getFluidState().isEmpty();
        }

        @Override
        public boolean breakable(int x, int y, int z) {
            BlockState state = state(x, y, z);
            if (state == null || state.isAir()) {
                return false;
            }
            return state.getHardness(this.world, new BlockPos(x, y, z)) >= 0;
        }

        @Override
        public boolean door(int x, int y, int z) {
            BlockState state = state(x, y, z);
            return state != null && state.getBlock() instanceof net.minecraft.block.DoorBlock;
        }

        @Override
        public boolean dangerous(int x, int y, int z) {
            BlockState state = state(x, y, z);
            if (state == null) {
                return true;
            }
            return lava(state) || fire(state) || magma(state) || cactus(state)
                    || powderSnow(state);
        }

        @Override
        public int minY() {
            return this.world.getBottomY();
        }

        @Override
        public int maxY() {
            return this.world.getTopYInclusive();
        }

        private BlockState state(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!this.world.isPosLoaded(pos)) {
                return null;
            }
            return this.world.getBlockState(pos);
        }

        /**
         * Lava is a block, not just a fluid: comparing the block avoids depending on a fluid tag
         * name, and {@code Blocks.LAVA} covers both the source and the flowing state.
         */
        private static boolean lava(BlockState state) {
            return state.getBlock() == net.minecraft.block.Blocks.LAVA;
        }

        private static boolean fire(BlockState state) {
            var block = state.getBlock();
            return block == net.minecraft.block.Blocks.FIRE || block == net.minecraft.block.Blocks.SOUL_FIRE;
        }

        private static boolean magma(BlockState state) {
            return state.getBlock() == net.minecraft.block.Blocks.MAGMA_BLOCK;
        }

        private static boolean cactus(BlockState state) {
            return state.getBlock() == net.minecraft.block.Blocks.CACTUS;
        }

        private static boolean powderSnow(BlockState state) {
            return state.getBlock() == net.minecraft.block.Blocks.POWDER_SNOW;
        }
    }
}

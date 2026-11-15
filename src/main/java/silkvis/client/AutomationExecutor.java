package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import silkvis.automation.AutomationMode;
import silkvis.automation.Step;

/**
 * The only place in the automation subsystem that touches {@code net.minecraft} (ARCH-05).
 *
 * <p>The engine decides, this class presses. Everything above it works on a {@code Snapshot} and
 * emits {@link Step}s, so the whole planning half can be exercised without a client; the half that
 * can actually hurt a player is confined to this file.</p>
 *
 * <p>Two rules are enforced here rather than trusted to the caller:</p>
 * <ul>
 *   <li><b>The mode is re-checked.</b> {@link AutomationMode#MOVE} may walk and look, never dig or
 *       place. A goal that asks for more than the player allowed gets {@code BLOCKED}, which stops
 *       the run with a reason in the log instead of quietly doing the unsafe thing.</li>
 *   <li><b>Every branch is bounded.</b> A step either finishes in this tick or reports
 *       {@code RUNNING}; nothing loops inside a tick and nothing waits for the world.</li>
 * </ul>
 *
 * <p>Input goes through the same paths vanilla uses ({@code player.input} flags, the interaction
 * manager), not through synthesised key packets, so the server sees an ordinary player.</p>
 */
public final class AutomationExecutor implements silkvis.automation.AutomationEngine.Executor {
    /** How close (squared) the player must be before a GOTO step counts as done. */
    private static final double ARRIVED_SQUARED = 0.6;
    /** Reach used for mining/interacting, matching the vanilla interaction distance with margin. */
    private static final double REACH = 5.0;
    /** Ticks a single dig may take before the step is considered stuck rather than slow. */
    private static final int MAX_DIG_TICKS = 200;

    private final MinecraftClient client;
    private boolean forward;
    private boolean jumped;
    private BlockPos digging;
    private int digTicks;
    private int waitTicks;
    private Step current;
    /** Last position seen while walking, to notice "pushing into a wall" without a stuck timer. */
    private double lastX;
    private double lastZ;
    private int stuckTicks;

    public AutomationExecutor(MinecraftClient client) {
        this.client = client;
    }

    @Override
    public Result execute(Step step, AutomationMode mode) {
        if (this.client == null || this.client.player == null || this.client.world == null) {
            return Result.BLOCKED;
        }
        if (mode == AutomationMode.OFF || mode == AutomationMode.HINT) {
            // Nothing should have reached here; refuse rather than press a key by accident.
            return Result.BLOCKED;
        }
        this.current = step;
        ClientPlayerEntity player = this.client.player;
        return switch (step.kind()) {
            case GOTO -> walkTo(player, step);
            case LOOK -> lookAt(player, step);
            case MINE -> requiresFull(mode) ? mine(player, step) : Result.BLOCKED;
            case PLACE -> requiresFull(mode) ? place(player, step) : Result.BLOCKED;
            case USE_ITEM -> requiresFull(mode) ? useItem(player) : Result.BLOCKED;
            case ATTACK -> requiresFull(mode) ? attack(player, step) : Result.BLOCKED;
            case SELECT_SLOT -> selectSlot(player, step);
            case WAIT -> waitStep(step);
        };
    }

    @Override
    public void abort() {
        releaseMovement();
        this.digging = null;
        this.digTicks = 0;
        this.waitTicks = 0;
        this.current = null;
        MinecraftClient mc = this.client;
        if (mc != null && mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            // Cancelling the break is a no-op if the server already had it; leaving it open is
            // how "the block keeps breaking after stop" happens.
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    /** Short description of the step in flight, for the status line and the log. */
    public String currentStep() {
        return this.current == null ? "" : this.current.describe();
    }

    private static boolean requiresFull(AutomationMode mode) {
        return mode == AutomationMode.FULL;
    }

    // ------------------------------------------------------------------ movement

    /**
     * Walks towards the centre of the target block.
     *
     * <p>Two things matter more than the steering: the step must not fight the player (the engine
     * already stops on player input), and it must end. A GOTO far away stays {@code RUNNING} for as
     * many ticks as it needs and is re-issued by the goal, which keeps this method a single tick of
     * work.</p>
     */
    private Result walkTo(ClientPlayerEntity player, Step step) {
        double targetX = step.x() + 0.5;
        double targetY = step.y();
        double targetZ = step.z() + 0.5;
        double dx = targetX - player.getX();
        double dz = targetZ - player.getZ();
        double horizontal = dx * dx + dz * dz;
        if (horizontal <= ARRIVED_SQUARED && Math.abs(targetY - player.getY()) < 1.2) {
            releaseMovement();
            this.stuckTicks = 0;
            return Result.DONE;
        }
        // Look where we are going: the input flags are relative to the camera, so steering is
        // "turn first, then push forward".
        player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        // Jump when the target is a step up, or when pushing forward has stopped moving the
        // player: the second case is the wall in front, and jumping is how a player climbs it.
        double moved = Math.abs(player.getX() - this.lastX) + Math.abs(player.getZ() - this.lastZ);
        this.stuckTicks = moved < 0.01 ? this.stuckTicks + 1 : 0;
        this.lastX = player.getX();
        this.lastZ = player.getZ();
        boolean needsJump = player.isOnGround()
                && (targetY > player.getY() + 0.6 || this.stuckTicks > 2);
        pressForward(true);
        if (needsJump) {
            pressJump(true);
        }
        return horizontal <= 1.5 ? Result.DONE : Result.RUNNING;
    }

    private Result lookAt(ClientPlayerEntity player, Step step) {
        Vec3d eye = player.getEyePos();
        double dx = step.x() + 0.5 - eye.x;
        double dy = step.y() + 0.5 - eye.y;
        double dz = step.z() + 0.5 - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        player.setPitch((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
        return Result.DONE;
    }

    // ------------------------------------------------------------------ interaction

    private Result mine(ClientPlayerEntity player, Step step) {
        if (this.client.interactionManager == null) {
            return Result.BLOCKED;
        }
        BlockPos pos = new BlockPos(step.x(), step.y(), step.z());
        if (this.client.world.getBlockState(pos).isAir()) {
            // Already gone: mining air is the classic way to a plan that never finishes.
            this.digging = null;
            return Result.DONE;
        }
        if (distanceSquared(player, pos) > REACH * REACH) {
            return Result.BLOCKED;
        }
        boolean fresh = !pos.equals(this.digging);
        if (fresh) {
            this.digging = pos;
            this.digTicks = 0;
        }
        player.setYaw(yawTo(player, pos));
        player.setPitch(pitchTo(player, pos));
        // attackBlock *starts* a break and resets its progress; updateBlockBreakingProgress
        // advances it. Calling the first one every tick would restart the block forever, which
        // is exactly the "it stands there hitting stone" failure.
        if (fresh) {
            if (!this.client.interactionManager.attackBlock(pos, faceTowards(player, pos))) {
                this.digging = null;
                return Result.BLOCKED;
            }
        } else {
            this.client.interactionManager.updateBlockBreakingProgress(pos,
                    faceTowards(player, pos));
        }
        this.digTicks++;
        if (this.digTicks > MAX_DIG_TICKS) {
            this.digging = null;
            return Result.BLOCKED;
        }
        // The break completes inside the interaction manager; the caller sees the block
        // disappear on a later tick and the goal re-plans from the new snapshot.
        return Result.RUNNING;
    }

    private Result place(ClientPlayerEntity player, Step step) {
        if (this.client.interactionManager == null) {
            return Result.BLOCKED;
        }
        BlockPos pos = new BlockPos(step.x(), step.y(), step.z());
        if (!this.client.world.getBlockState(pos).isAir()) {
            return Result.DONE;
        }
        if (distanceSquared(player, pos) > REACH * REACH) {
            return Result.BLOCKED;
        }
        player.setYaw(yawTo(player, pos));
        player.setPitch(pitchTo(player, pos));
        Vec3d hit = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        this.client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, pos, false));
        return Result.DONE;
    }

    private Result useItem(ClientPlayerEntity player) {
        if (this.client.interactionManager == null) {
            return Result.BLOCKED;
        }
        this.client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        return Result.DONE;
    }

    private Result attack(ClientPlayerEntity player, Step step) {
        if (this.client.interactionManager == null) {
            return Result.BLOCKED;
        }
        net.minecraft.entity.Entity target = this.client.world.getEntityById(step.x());
        if (target == null) {
            return Result.BLOCKED;
        }
        if (player.squaredDistanceTo(target) > REACH * REACH) {
            return Result.BLOCKED;
        }
        player.setYaw(yawTo(player, target.getBlockPos()));
        this.client.interactionManager.attackEntity(player, target);
        return Result.DONE;
    }

    private Result selectSlot(ClientPlayerEntity player, Step step) {
        int slot = step.x();
        if (slot < 0 || slot >= 9) {
            return Result.BLOCKED;
        }
        // The setter, not the field: selectedSlot is private in PlayerInventory (the compiler
        // said so once), and setSelectedSlot is what the vanilla hotbar keys end up calling.
        player.getInventory().setSelectedSlot(slot);
        return Result.DONE;
    }

    private Result waitStep(Step step) {
        this.waitTicks++;
        if (this.waitTicks >= step.ticks()) {
            this.waitTicks = 0;
            return Result.DONE;
        }
        return Result.RUNNING;
    }

    // ------------------------------------------------------------------ input plumbing

    private void pressForward(boolean on) {
        if (this.forward == on) {
            return;
        }
        this.forward = on;
        MinecraftClient mc = this.client;
        if (mc == null || mc.options == null || mc.options.forwardKey == null) {
            return;
        }
        mc.options.forwardKey.setPressed(on);
    }

    private void pressJump(boolean on) {
        if (this.jumped == on) {
            return;
        }
        this.jumped = on;
        MinecraftClient mc = this.client;
        if (mc == null || mc.options == null || mc.options.jumpKey == null) {
            return;
        }
        mc.options.jumpKey.setPressed(on);
    }

    /**
     * Releases everything this executor pressed. Called on every stop path, because a key left
     * pressed is a player who keeps walking into a wall after pressing "stop".
     */
    public void releaseMovement() {
        pressForward(false);
        pressJump(false);
    }

    private static double distanceSquared(ClientPlayerEntity player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5);
        double dy = player.getY() + player.getStandingEyeHeight() - (pos.getY() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static float yawTo(ClientPlayerEntity player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    private static float pitchTo(ClientPlayerEntity player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - player.getEyePos().y;
        double dz = pos.getZ() + 0.5 - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    /** The block face nearest to the player: what a click on that side would hit. */
    private static Direction faceTowards(ClientPlayerEntity player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5);
        double dy = player.getEyePos().y - (pos.getY() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}

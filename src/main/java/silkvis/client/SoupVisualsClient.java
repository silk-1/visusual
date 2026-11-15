package silkvis.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hand.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import silkvis.visual.soup.modules.AmbientParticles;
import silkvis.visual.soup.modules.HitBubbles;
import silkvis.visual.soup.modules.TotemParticles;
import silkvis.visual.soup.render.SoupRender;
import silkvis.visual.soup.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Client glue for the ported SoupVisuals modules (HitBubbles, TotemParticles,
 * AmbientParticles).
 *
 * <p>This is the only class of the ported code that touches the game: it owns the three
 * module instances, feeds them the frame data they need (camera, tick progress, player
 * position), draws their queued geometry, and runs the two detectors the original implemented
 * with mixins (attack / totem pop) through public vanilla and Fabric API hooks.</p>
 *
 * <p>Why a separate class instead of {@code HudHook}: the HUD element renders into the GUI
 * space, while these modules draw in the world. {@link WorldRenderEvents} is the Fabric API
 * 1.21.10 replacement for the old world render callbacks and needs no mixin, which keeps
 * SilkVis' "no mixins" rule (ADR-0002).</p>
 *
 * <p>Modules stay disabled until the player switches them on: nothing is drawn, nothing is
 * spawned and no texture is loaded before that.</p>
 */
public final class SoupVisualsClient {
    private static final SoupVisualsClient INSTANCE = new SoupVisualsClient();

    private final HitBubbles hitBubbles = new HitBubbles();
    private final TotemParticles totemParticles = new TotemParticles();
    private final AmbientParticles ambientParticles = new AmbientParticles();
    private final List<LivingEntity> totemWatch = new ArrayList<>();
    private boolean registered;
    /** World the particle state belongs to; a world change clears everything. */
    private Object world;
    /** Debounces duplicated attack callbacks for one physical click. */
    private long lastAttackNanos;

    private SoupVisualsClient() {
        // "Sync" colour source: the theme accent of SilkVis. The original took the same value
        // from its own theme module, so a themed client still paints particles in accent.
        ColorUtil.setClientColor(() -> SilkVisRuntime.get().theme().accent().argb());
        // Feature flags stay off (AGENTS.md rule 7: nothing draws on the first start). A
        // follow-up commit wires them to the settings screen; the modules, their settings and
        // their render paths are complete and can be switched on in one place.
        hitBubbles.setEnabled(false);
        totemParticles.setEnabled(false);
        ambientParticles.setEnabled(false);
    }

    /** Settings of the ported modules, for a settings screen (next step). */
    public java.util.List<silkvis.visual.soup.module.SoupModule> modules() {
        return java.util.List.of(this.hitBubbles, this.totemParticles, this.ambientParticles);
    }

    public static SoupVisualsClient get() {
        return INSTANCE;
    }

    public HitBubbles hitBubbles() {
        return this.hitBubbles;
    }

    public TotemParticles totemParticles() {
        return this.totemParticles;
    }

    public AmbientParticles ambientParticles() {
        return this.ambientParticles;
    }

    /** Wires the world render hook once; safe to call from the client initialiser. */
    public void register() {
        if (this.registered) {
            return;
        }
        this.registered = true;
        // Setting labels resolve through SilkVis' own language table, so a ported module shows
        // Russian or English exactly like the rest of the client.
        silkvis.visual.soup.setting.Setting.setTextSink(key -> SilkVisRuntime.get().localizer().get(key));
        // END_MAIN clears the HUD/hand state of 1.21.10, so queued quads can be submitted
        // directly with the world consumer provider at that point.
        WorldRenderEvents.END_MAIN.register(this::renderWorld);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // HitBubbles' attack hook. Fabric fires this right before the vanilla attack packet
        // is sent, which is where the original's own event sat as well; returning PASS keeps
        // the hit itself untouched.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            onAttack(MinecraftClient.getInstance(), entity);
            return ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ per tick

    private void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            this.totemWatch.clear();
            this.world = null;
            return;
        }
        resetOnWorldChange(client.world);
        Box playerBox = player.getBoundingBox().expand(10.0);
        // Culling happens against last frame's frustum, exactly like the original's
        // ProjectionUtil.canSee; null when the frustum has not been captured yet.
        Frustum frustum = client.worldRenderer.getCapturedFrustum();
        this.ambientParticles.tick(player.getX(), player.getY(), player.getZ(),
                (x, y, z, size) -> canSee(frustum, x, y, z, size));
        this.totemParticles.tick();
        this.hitBubbles.tick();
        detectTotemPops(client, player, playerBox);
    }

    private void resetOnWorldChange(Object currentWorld) {
        if (this.world == currentWorld) {
            return;
        }
        this.world = currentWorld;
        // Drop everything that belonged to the previous world. No activation is needed here:
        // the render path is stateless and both particles and bubbles belong to the world
        // that spawned them, exactly like the original's world-change reset.
        this.hitBubbles.clearWorldState();
        this.totemParticles.clearWorldState();
        this.ambientParticles.clearWorldState();
        this.totemWatch.clear();
    }

    /**
     * Totem pop detector, replacing the original's {@code ClientPlayNetworkHandler} mixin on
     * {@code EntityStatuses.USE_TOTEM_OF_UNDYING} (the vanilla entity status packet is not
     * exposed through the public API). A totem only pops while the entity is taking lethal
     * damage, so the check is: an entity near the player had a totem in its hands last tick
     * and lost it this tick.
     */
    private void detectTotemPops(MinecraftClient client, ClientPlayerEntity player, Box area) {
        List<LivingEntity> seen = new ArrayList<>();
        for (LivingEntity entity : client.world.getEntitiesByClass(LivingEntity.class, area, Entity::isAlive)) {
            boolean hadTotem = this.totemWatch.contains(entity);
            boolean hasTotem = holdsTotem(entity);
            if (hadTotem && !hasTotem) {
                Vec3d pos = entity.getPos();
                this.totemParticles.onTotemPop(pos.x, pos.y, pos.z, entity.getHeight());
            }
            if (hasTotem) {
                seen.add(entity);
            }
        }
        this.totemWatch.clear();
        this.totemWatch.addAll(seen);
    }

    private static boolean holdsTotem(LivingEntity entity) {
        return isTotem(entity.getStackInHand(Hand.MAIN_HAND)) || isTotem(entity.getStackInHand(Hand.OFF_HAND));
    }

    private static boolean isTotem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING);
    }

    /**
     * Attack hook for HitBubbles. Called by the interaction layer right before the vanilla
     * attack is sent, with the target the player is looking at, so the crit flag and the hit
     * point match what the server is about to resolve.
     */
    public void onAttack(MinecraftClient client, Entity target) {
        ClientPlayerEntity player = client.player;
        if (player == null || target == null) {
            return;
        }
        // One attack per click: the callback can fire twice for the same click (main and off
        // hand), and the original spawns exactly one bubble per hit.
        long now = System.nanoTime();
        if (now - this.lastAttackNanos < 50_000_000L) {
            return;
        }
        this.lastAttackNanos = now;
        boolean crit = HitBubbles.isCrit(
                player.getAttackCooldownProgress(0.5F),
                player.fallDistance,
                player.isOnGround(),
                player.isClimbing(),
                player.isTouchingWater(),
                player.hasStatusEffect(StatusEffects.BLINDNESS),
                player.isSprinting(),
                player.hasVehicle());
        this.hitBubbles.onAttackPre(crit);
        Vec3d eye = player.getEyePos();
        Vec3d hit = hitPoint(player, target);
        this.hitBubbles.onAttack(hit.x, hit.y, hit.z, eye.x, eye.y, eye.z);
    }

    /** Closest point on the target's box along the view ray, falling back to its centre. */
    private static Vec3d hitPoint(ClientPlayerEntity player, Entity target) {
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVector(1.0F, 1.0F).multiply(4.5));
        return target.getBoundingBox().raycast(eye, end).orElseGet(() -> target.getBoundingBox().getCenter());
    }

    // ------------------------------------------------------------------ per frame

    private void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        MatrixStack matrices = context.matrices();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        float cameraYaw = client.gameRenderer.getCamera().getYaw();
        float cameraPitch = client.gameRenderer.getCamera().getPitch();

        matrices.push();
        this.hitBubbles.render(matrices.peek().getPositionMatrix(), cameraPitch, cameraYaw,
                camera.x, camera.y, camera.z);
        this.totemParticles.render(matrices.peek().getPositionMatrix(), camera.x, camera.y, camera.z,
                tickProgress, cameraYaw, cameraPitch);
        this.ambientParticles.render(matrices.peek().getPositionMatrix(), camera.x, camera.y, camera.z,
                tickProgress, cameraYaw, cameraPitch);
        matrices.pop();

        SoupRender.flush(matrices, context.consumers());
    }

    /**
     * Frustum test exposed for the ambient culling (the original had
     * {@code ProjectionUtil.canSee(Box)}); kept here because it needs the live
     * {@link Frustum}, which only the client has.
     */
    public static boolean canSee(Frustum frustum, float x, float y, float z, float size) {
        if (frustum == null) {
            return true;
        }
        return frustum.isVisible(new Box(x - size, y - size, z - size, x + size, y + size, z + size));
    }
}

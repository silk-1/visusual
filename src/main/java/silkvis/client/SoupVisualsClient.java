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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import silkvis.visual.soup.modules.AmbientParticles;
import silkvis.visual.soup.modules.FriendsTargetRender;
import silkvis.visual.soup.modules.HandTweaks;
import silkvis.visual.soup.modules.HitBubbles;
import silkvis.visual.soup.modules.HitSounds;
import silkvis.visual.soup.modules.Hitboxes;
import silkvis.visual.soup.modules.JumpCircles;
import silkvis.visual.soup.modules.KillEffect;
import silkvis.visual.soup.modules.TotemParticles;
import silkvis.visual.soup.modules.Trails;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.render.SoupRender;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.EntitySnapshot;
import silkvis.visual.soup.util.FriendList;
import silkvis.visual.soup.util.SoupSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client glue for the ported SoupVisuals modules.
 *
 * <p>This is the only class of the ported code that touches the game: it owns the module
 * instances, feeds them the frame data they need (camera, tick progress, entity snapshots),
 * draws their queued geometry, and runs the detectors the original implemented with mixins
 * (attack, jump, damage, death, kill) through public vanilla and Fabric API hooks.</p>
 *
 * <p>Why a separate class instead of {@code HudHook}: the HUD element renders into the GUI
 * space, while these modules draw in the world. {@link WorldRenderEvents} is the Fabric API
 * 1.21.10 replacement for the old world render callbacks and needs no mixin, which keeps
 * SilkVis' "no mixins" rule (ADR-0002).</p>
 *
 * <p>Batch 1: HitBubbles, TotemParticles, AmbientParticles. Batch 2: Hitboxes, JumpCircles,
 * Trails, HitSounds, KillEffect, FriendsTargetRender, HandTweaks.</p>
 *
 * <p>Modules stay disabled until the player switches them on: nothing is drawn, nothing is
 * spawned and no texture is loaded before that.</p>
 */
public final class SoupVisualsClient {
    private static final SoupVisualsClient INSTANCE = new SoupVisualsClient();
    /** Search radius of the entity snapshot pass, in blocks. */
    private static final double SCAN_RADIUS = 160.0;
    /** The original's raycast reach for a friend's target: 5.3 while sprinting, else 3.3. */
    private static final float RAYCAST_SPRINTING = 5.3F;
    private static final float RAYCAST_DEFAULT = 3.3F;
    /** Reach of the hit-point raycast: the original's vanilla attack reach with margin. */
    private static final double HIT_RAY_LENGTH = 4.5;

    /**
     * Last tick's position per entity id, i.e. what the original read from the (then public)
     * {@code Entity#prevX/prevY/prevZ} fields. In 1.21.10 those fields are private and the
     * public {@code getLerpedPos(delta)} depends on the client's position-tracker state, so the
     * client layer keeps the three numbers itself: one entry per entity, updated per tick,
     * dropped when the entity leaves the scan radius (the map is cleared on world change).
     * {@code Trail} needs exactly this pair (previous/current) and nothing else.
     */
    private final Map<Integer, double[]> previousPositions = new HashMap<>();

    private final HitBubbles hitBubbles = new HitBubbles();
    private final TotemParticles totemParticles = new TotemParticles();
    private final AmbientParticles ambientParticles = new AmbientParticles();
    private final Hitboxes hitboxes = new Hitboxes();
    private final JumpCircles jumpCircles = new JumpCircles();
    private final Trails trails = new Trails();
    private final HitSounds hitSounds = new HitSounds();
    private final KillEffect killEffect = new KillEffect();
    private final FriendsTargetRender friendsTargetRender = new FriendsTargetRender();
    private final HandTweaks handTweaks = new HandTweaks();

    private final List<LivingEntity> totemWatch = new ArrayList<>();
    /** Sound events registered by this mod, by path -- see {@link #registerSounds()}. */
    private final Map<String, SoundEvent> soundEvents = new HashMap<>();
    private boolean registered;
    /** World the particle state belongs to; a world change clears everything. */
    private Object world;
    /** Debounces duplicated attack callbacks for one physical click. */
    private long lastAttackNanos;
    /** Previous tick's on-ground state of the player, for the jump detector. */
    private boolean wasOnGround = true;
    /** Reused snapshots of the current frame (render thread only). */
    private List<EntitySnapshot> frameEntities = List.of();

    private SoupVisualsClient() {
        // "Sync" colour source: the theme accent of SilkVis. The original took the same value
        // from its own theme module, so a themed client still paints particles in accent.
        ColorUtil.setClientColor(() -> SilkVisRuntime.get().theme().accent().argb());
        ColorUtil.setFriendColor(() -> 0xFF00FF00);
        // Sound playback goes through the vanilla sound manager, exactly like the original's
        // PositionedSoundInstance.master(sound, pitch, volume) call.
        SoupSounds.setPlayer(this::playSound);
        KillEffect.setWorldEffects(this::spawnLightning);
        // Feature flags stay off (AGENTS.md rule 7: nothing draws on the first start). The
        // modules, their settings and their render paths are complete and are switched on from
        // the settings screen, one module at a time.
        for (SoupModule module : this.modules()) {
            module.setEnabled(false);
        }
    }

    /** Settings of the ported modules, for the settings screen. */
    public List<SoupModule> modules() {
        return List.of(this.hitBubbles, this.totemParticles, this.ambientParticles,
                this.hitboxes, this.jumpCircles, this.trails, this.hitSounds, this.killEffect,
                this.friendsTargetRender, this.handTweaks);
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

    public Hitboxes hitboxes() {
        return this.hitboxes;
    }

    public JumpCircles jumpCircles() {
        return this.jumpCircles;
    }

    public Trails trails() {
        return this.trails;
    }

    public HitSounds hitSounds() {
        return this.hitSounds;
    }

    public KillEffect killEffect() {
        return this.killEffect;
    }

    public FriendsTargetRender friendsTargetRender() {
        return this.friendsTargetRender;
    }

    public HandTweaks handTweaks() {
        return this.handTweaks;
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
        registerSounds();
        // END_MAIN clears the HUD/hand state of 1.21.10, so queued quads can be submitted
        // directly with the world consumer provider at that point.
        WorldRenderEvents.END_MAIN.register(this::renderWorld);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // The attack hooks. Fabric fires this right before the vanilla attack packet is sent,
        // which is where the original's own event sat as well; returning PASS keeps the hit
        // itself untouched.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            onAttack(MinecraftClient.getInstance(), entity);
            return ActionResult.PASS;
        });
    }

    /**
     * Registers the custom sounds.
     *
     * <p>The original registers its own sounds into the {@code minecraft} namespace of the
     * {@code SOUND_EVENT} registry at client start
     * ({@code Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id))}). The port does
     * the same in the {@code silkvis} namespace, so nothing vanilla is ever shadowed; the sound
     * files and their {@code sounds.json} entries live under
     * {@code assets/silkvis/sounds/}. Without the registration the client logs
     * "Unable to play unknown soundEvent" and stays silent -- that is why this is the very
     * first thing {@link #register()} does.</p>
     */
    private void registerSounds() {
        for (String path : SoupSounds.ALL_PATHS) {
            Identifier id = Identifier.of("silkvis", path);
            SoundEvent event = SoundEvent.of(id);
            Registry.register(Registries.SOUND_EVENT, id, event);
            this.soundEvents.put(path, event);
        }
    }

    /** Original {@code SoundManager.playSound}: master category, no attenuation. */
    private void playSound(String path, float volume, float pitch) {
        SoundEvent event = this.soundEvents.get(path);
        MinecraftClient client = MinecraftClient.getInstance();
        if (event == null || client.getSoundManager() == null) {
            return;
        }
        client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance
                .master(event, pitch, volume));
    }

    /** Original {@code spawnThunderEffect}: a client-side lightning bolt with no damage. */
    private void spawnLightning(double x, double y, double z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, client.world);
        lightning.setPosition(x, y, z);
        // True = cosmetic only: the original passes true as well, so no fire and no damage.
        lightning.setCosmetic(true);
        client.world.addEntity(lightning);
    }

    // ------------------------------------------------------------------ per tick

    private void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            this.totemWatch.clear();
            this.world = null;
            this.wasOnGround = true;
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
        detectJump(client, player);
        tickBatchTwo(client, player);
    }

    private void resetOnWorldChange(Object currentWorld) {
        if (this.world == currentWorld) {
            return;
        }
        this.world = currentWorld;
        // Drop everything that belonged to the previous world. No activation is needed here:
        // the render path is stateless and both particles and bubbles belong to the world
        // that spawned them, exactly like the original's world-change reset.
        for (SoupModule module : this.modules()) {
            module.clearWorldState();
        }
        this.totemWatch.clear();
        this.wasOnGround = true;
        this.frameEntities = List.of();
    }

    /**
     * Jump detector, replacing the original's {@code LivingEntityMixin} on
     * {@code LivingEntity#jump} (which fires {@code JumpEvent}). A vanilla jump is "the player
     * was on the ground last tick, is not now, and moves upwards": exactly the pair of
     * conditions the mixin sits between, and it also covers bunny-hopping without re-firing
     * while airborne.
     */
    private void detectJump(MinecraftClient client, ClientPlayerEntity player) {
        boolean onGround = player.isOnGround();
        boolean jumped = this.wasOnGround && !onGround && player.getVelocity().y > 0.0;
        this.wasOnGround = onGround;
        if (!jumped) {
            return;
        }
        this.jumpCircles.onJump(player.getX(), player.getY(), player.getZ());
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
                // The original reads entity.getPos(). In 1.21.10 Entity has no getPos() any
                // more; the synced position is the same value the original used.
                Vec3d pos = entity.getSyncedPos();
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
     * Attack hook. Called by the interaction layer right before the vanilla attack is sent,
     * with the target the player is looking at, so the crit flag and the hit point match what
     * the server is about to resolve.
     *
     * <p>Feeds HitBubbles (bubble + crit), HitSounds (crit flag, then the custom sound) and
     * KillEffect (the attack record). All three only act while their module is enabled.</p>
     */
    public void onAttack(MinecraftClient client, Entity target) {
        ClientPlayerEntity player = client.player;
        if (player == null || target == null) {
            return;
        }
        // One attack per click: the callback can fire twice for the same click (main and off
        // hand), and the original spawns exactly one bubble per hit.
        long now = System.nanoTime();
        boolean duplicate = now - this.lastAttackNanos < 50_000_000L;
        if (duplicate) {
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
        this.hitSounds.onAttack(true, crit);
        if (target instanceof LivingEntity living) {
            this.killEffect.onAttack(snapshot(living, player.getUuid()));
        }
    }

    /**
     * Closest point on the target's box along the player's actual view ray, falling back to
     * the box centre. The direction is the entity's own look vector (yaw, pitch) -- passing
     * constants here would place the bubble at a fixed point in the target's box, not where
     * the hit landed.
     */
    private static Vec3d hitPoint(ClientPlayerEntity player, Entity target) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector(player.getYaw(1.0F), player.getPitch(1.0F));
        Vec3d end = eye.add(look.multiply(HIT_RAY_LENGTH));
        return target.getBoundingBox().raycast(eye, end)
                .orElseGet(() -> target.getBoundingBox().getCenter());
    }

    // ------------------------------------------------------------------ snapshots

    /**
     * Everything the batch-2 modules need about one entity, exactly the fields the original
     * reads from the live object.
     */
    private EntitySnapshot snapshot(LivingEntity entity, java.util.UUID selfId) {
        boolean self = entity.getUuid().equals(selfId);
        // Previous tick's position, from our own cache: on the very first frame it is the
        // current position, which gives a zero-length first segment instead of a trail that
        // shoots in from nowhere.
        double[] previous = this.previousPositions.computeIfAbsent(entity.getId(),
                id -> new double[]{entity.getX(), entity.getY(), entity.getZ()});
        return new EntitySnapshot(
                entity.getId(),
                entity.getName().getString(),
                entity instanceof PlayerEntity,
                entity instanceof PlayerEntity && FriendList.isFriend(entity.getName().getString()),
                entity.getX(), entity.getY(), entity.getZ(),
                previous[0], previous[1], previous[2],
                entity.getEyeY(),
                entity.getWidth(),
                entity.getHeight(),
                entity.hurtTime,
                entity.getHealth(),
                entity.isAlive(),
                true,
                entity.isGliding());
    }

    /** Every living entity in the scan radius, minus the local player, as snapshots. */
    private List<EntitySnapshot> collectEntities(MinecraftClient client, ClientPlayerEntity player) {
        Box box = player.getBoundingBox().expand(SCAN_RADIUS);
        List<EntitySnapshot> out = new ArrayList<>();
        for (LivingEntity entity : client.world.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (entity == player) {
                continue;
            }
            out.add(snapshot(entity, player.getUuid()));
        }
        return out;
    }

    /** Runs the per-tick half of the batch-2 modules that need the whole entity list. */
    private void tickBatchTwo(MinecraftClient client, ClientPlayerEntity player) {
        boolean anyBatchTwoEnabled = this.hitboxes.isEnabled() || this.jumpCircles.isEnabled()
                || this.trails.isEnabled() || this.killEffect.isEnabled()
                || this.friendsTargetRender.isEnabled();
        if (!anyBatchTwoEnabled) {
            // Keep the previous-position cache warm anyway (see advancePreviousPositions);
            // the entity scan is the same one the modules need, so this costs one pass.
            advancePreviousPositions(collectEntities(client, player));
            return;
        }
        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        List<EntitySnapshot> entities = collectEntities(client, player);
        this.frameEntities = entities;
        if (this.trails.isEnabled()) {
            this.trails.tick(entities, tickProgress, player.isSneaking());
        }
        if (this.killEffect.isEnabled()) {
            this.killEffect.tick(entities, player.isAlive() && !player.isDead() && player.getHealth() > 0.0F,
                    player.getX(), player.getY(), player.getZ(), tickProgress);
        }
        if (this.friendsTargetRender.isEnabled()) {
            this.friendsTargetRender.tick(collectFriends(client, player, entities), byId(entities),
                    player.getX(), player.getY(), player.getZ());
        }
        // The snapshots above used last tick's positions; remember this tick's for the next
        // pass, and forget entities that left the scan radius so the map cannot grow forever.
        advancePreviousPositions(entities);
    }

    /**
     * Remembers this tick's positions for the next snapshot pass.
     *
     * <p>Called even when no module is enabled: if the cache only advanced while a module was
     * on, the first frame after switching Trails on would compare against a position from
     * minutes ago and draw a trail across the whole map.</p>
     */
    private void advancePreviousPositions(List<EntitySnapshot> entities) {
        Map<Integer, EntitySnapshot> byId = byId(entities);
        this.previousPositions.keySet().retainAll(byId.keySet());
        for (EntitySnapshot entity : entities) {
            this.previousPositions.put(entity.id(), new double[]{entity.x(), entity.y(), entity.z()});
        }
    }

    private static Map<Integer, EntitySnapshot> byId(List<EntitySnapshot> entities) {
        Map<Integer, EntitySnapshot> out = new HashMap<>();
        for (EntitySnapshot entity : entities) {
            out.put(entity.id(), entity);
        }
        return out;
    }

    /**
     * Finds, for every friend, the entity that friend is aiming at.
     *
     * <p>Port of {@code FriendsTargetRender#getFriendTarget}: a raycast from the friend's own
     * eyes along its own view vector, reach 5.3 while sprinting and 3.3 otherwise, with the
     * original's box expansion of one block. This needs the {@code World}, which is why it
     * lives in the client layer instead of the module.</p>
     */
    private List<FriendsTargetRender.FriendSnapshot> collectFriends(MinecraftClient client,
                                                                   ClientPlayerEntity player,
                                                                   List<EntitySnapshot> entities) {
        List<FriendsTargetRender.FriendSnapshot> out = new ArrayList<>();
        Box area = player.getBoundingBox().expand(SCAN_RADIUS);
        for (PlayerEntity friend : client.world.getEntitiesByClass(PlayerEntity.class, area,
                entity -> entity != player && FriendList.isFriend(entity.getName().getString()))) {
            float raycastDistance = friend.isSprinting() ? RAYCAST_SPRINTING : RAYCAST_DEFAULT;
            float tickProgress = client.getRenderTickCounter().getTickProgress(false);
            Vec3d start = friend.getEyePos();
            // Interpolated look vector. 1.21.10 has no getRotationVector(tickProgress) overload,
            // so the yaw/pitch are lerped first and fed to the two-argument version -- the same
            // order the original's MathUtil.interpolate uses.
            Vec3d direction = friend.getRotationVector(
                    friend.getLerpedYaw(tickProgress), friend.getLerpedPitch(tickProgress));
            Vec3d end = start.add(direction.multiply(raycastDistance));
            Box box = friend.getBoundingBox().expand(raycastDistance).expand(1.0);
            EntityHitResult result = ProjectileUtil.raycast(friend, start, end, box,
                    entity -> !entity.isSpectator() && entity.isAlive() && entity instanceof LivingEntity,
                    raycastDistance * raycastDistance);
            int targetId = result != null && result.getEntity() instanceof LivingEntity target
                    ? target.getId() : -1;
            out.add(new FriendsTargetRender.FriendSnapshot(friend.getUuidAsString(),
                    friend.getName().getString(), targetId));
        }
        return out;
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
        ClientPlayerEntity player = client.player;
        boolean thirdPerson = !client.options.getPerspective().isFirstPerson();

        matrices.push();
        this.hitBubbles.render(matrices.peek().getPositionMatrix(), cameraPitch, cameraYaw,
                camera.x, camera.y, camera.z);
        this.totemParticles.render(matrices.peek().getPositionMatrix(), camera.x, camera.y, camera.z,
                tickProgress, cameraYaw, cameraPitch);
        this.ambientParticles.render(matrices.peek().getPositionMatrix(), camera.x, camera.y, camera.z,
                tickProgress, cameraYaw, cameraPitch);
        this.jumpCircles.render(matrices.peek().getPositionMatrix(), cameraPitch, cameraYaw,
                camera.x, camera.y, camera.z);
        List<EntitySnapshot> entities = frameEntitiesFor(client, player, tickProgress);
        this.trails.render(matrices.peek().getPositionMatrix(), entities, tickProgress,
                camera.x, camera.y, camera.z, player.isSneaking());
        this.killEffect.render(matrices.peek().getPositionMatrix(), cameraPitch, cameraYaw,
                camera.x, camera.y, camera.z, tickProgress);
        this.friendsTargetRender.render(matrices.peek().getPositionMatrix(), cameraPitch, cameraYaw,
                camera.x, camera.y, camera.z, -1);
        if (this.hitboxes.isEnabled()) {
            for (EntitySnapshot entity : entities) {
                this.hitboxes.renderEntity(matrices.peek().getPositionMatrix(), entity, tickProgress,
                        false, thirdPerson);
            }
            if (thirdPerson) {
                this.hitboxes.renderEntity(matrices.peek().getPositionMatrix(),
                        snapshot(player, player.getUuid()), tickProgress, true, true);
            }
        }
        matrices.pop();

        SoupRender.flush(matrices, context.consumers());
    }

    /** The tick pass already built the list; rebuild it only when nothing was collected. */
    private List<EntitySnapshot> frameEntitiesFor(MinecraftClient client, ClientPlayerEntity player,
                                                  float tickProgress) {
        if (!this.frameEntities.isEmpty()) {
            return this.frameEntities;
        }
        return collectEntities(client, player);
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

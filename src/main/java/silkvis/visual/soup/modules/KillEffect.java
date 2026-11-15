package silkvis.visual.soup.modules;

import org.joml.Matrix4f;
import silkvis.visual.soup.module.ParticleModule;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.module.SoupModuleCategory;
import silkvis.visual.soup.particles.WorldParticle;
import silkvis.visual.soup.setting.BooleanSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.MultiColorSetting;
import silkvis.visual.soup.setting.MultiSelectSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.ValueSetting;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.EntitySnapshot;
import silkvis.visual.soup.util.SoupSounds;
import silkvis.visual.soup.util.SpatialGrid3D;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KillEffect -- an effect on the spot where the player (or a friend) killed something.
 *
 * <p>Ported from SoupVisuals 1.21.11
 * {@code padej.soup.implement.features.modules.other.KillEffect} (Padej_, SOUP-1.0, used by
 * written permission). Settings, groups and defaults are the original's.</p>
 *
 * <p><b>What was ported:</b> the whole kill detection chain of the original -- attack record
 * with a 5 s timeout, damage confirmation, the death-event path, the "entity vanished from the
 * world" pending path with its 100 ms grace period, the "entity teleported and healed" path,
 * the 2 s de-duplication, the clear on own death / respawn / teleport, the friend branch and
 * the target filter (Mobs / Player / Both); plus the particle burst (all modes and physics
 * selections, random per particle, network links through the spatial grid) and the kill
 * sounds.</p>
 *
 * <p><b>Difficulties:</b> the original hooks {@code EventAttack}, an {@code EntityDamageEvent}
 * built on the damage packet, and {@code EntityDeathEvent}, all of them mixin-based. SilkVis
 * has none of those (ADR-0002), so:</p>
 * <ul>
 *   <li>the attack record comes from the public {@code AttackEntityCallback};</li>
 *   <li>damage is <em>confirmed</em> by observing {@code hurtTime} on the tracked entity in the
 *       following ticks, which is the public equivalent of "the server accepted the hit";</li>
 *   <li>death is detected in the per-tick poll of the original's own {@code onTick} (it already
 *       checks {@code !isAlive() || health <= 0}), so no mixin is needed;</li>
 *   <li>{@code TR Sync} (hand the effect over to the target-render module) has no counterpart
 *       yet: TargetRender is not ported, so that option falls back to the particle burst. This
 *       is listed in {@code docs/known-limitations.md}.</li>
 * </ul>
 *
 * <p><b>To verify in game:</b> a normal kill, a kill on a friend's target, a kill by a fall
 * (entity vanishes), a hit that does not kill (nothing must fire), a respawn (nothing fires),
 * the thunder option, and each kill sound.</p>
 */
public class KillEffect extends SoupModule implements ParticleModule {
    private static final long ATTACK_TIMEOUT_MS = 5000L;
    private static final long DEDUP_COOLDOWN_MS = 2000L;
    private static final double TELEPORT_DIST_SQ = 64.0;
    private static final long PENDING_DELAY_MS = 100L;

    private final Map<Integer, AttackRecord> recentlyAttacked = new ConcurrentHashMap<>();
    private final Map<Integer, AttackRecord> recentlyAttackedByFriends = new ConcurrentHashMap<>();
    private final Map<Integer, PendingKill> pendingKills = new ConcurrentHashMap<>();
    private final Map<Integer, Long> firedKills = new ConcurrentHashMap<>();

    private final List<WorldParticle> particles = new ArrayList<>();
    private final List<WorldParticle> networkParticles = new ArrayList<>();
    private SpatialGrid3D<WorldParticle> spatialGrid;
    private double[] playerLastTickPos;
    private boolean playedThisTick;

    public final SelectSetting effectType = new SelectSetting(
            "setting.killeffect.effecttype.name", "setting.killeffect.effecttype.desc")
            .value("Particles", "Thunder", "TR Sync")
            .selected("Particles");
    public final SelectSetting friendEffectType = new SelectSetting(
            "setting.killeffect.friendeffecttype.name", "setting.killeffect.friendeffecttype.desc")
            .value("Particles", "Thunder", "TR Sync")
            .selected("Thunder");
    public final BooleanSetting showForFriends = new BooleanSetting(
            "setting.killeffect.showforfriends.name", "setting.killeffect.showforfriends.desc")
            .setValue(true);
    public final SelectSetting targetType = new SelectSetting(
            "setting.killeffect.targettype.name", "setting.killeffect.targettype.desc")
            .value("Mobs", "Player", "Both")
            .selected("Both");
    public final SelectSetting colorMode = new SelectSetting(
            "setting.killeffect.colormode.name", "setting.killeffect.colormode.desc")
            .value("Sync", "Custom")
            .selected("Sync")
            .visible(this::isParticleMode);
    public final SelectSetting customColorsCount = new SelectSetting(
            "setting.killeffect.colorcount.name", "setting.killeffect.colorcount.desc")
            .value("Solo", "Duo", "Triple", "Quartet")
            .selected("Solo")
            .visible(() -> this.colorMode.isSelected("Custom") && this.isParticleMode());
    public final MultiColorSetting customColors = new MultiColorSetting(
            "setting.killeffect.gradientcolors.name", "setting.killeffect.gradientcolors.desc")
            .colors("Color 1", "Color 2", "Color 3", "Color 4")
            .defaultColors(-1499549, -13273872, -6596170, -409301)
            .visible(() -> this.colorMode.isSelected("Custom") && this.isParticleMode());
    public final SelectSetting colorAnimation = new SelectSetting(
            "setting.killeffect.coloranimation.name", "setting.killeffect.coloranimation.desc")
            .value("Wave", "Vertexes")
            .selected("Wave")
            .visible(() -> this.colorMode.isSelected("Custom") && this.isParticleMode());
    public final MultiSelectSetting mode = new MultiSelectSetting(
            "setting.killeffect.mode.name", "setting.killeffect.mode.desc")
            .value("Stars", "Hearts", "Bloom", "Glyph", "Things", "Blink", "Coron", "Dollar",
                    "Flame", "Geometric", "Snowflake", "Logo", "Virus", "SoupAPI Old", "Sword",
                    "Network", "Cube", "Pyramid")
            .selected("Stars", "Hearts", "Bloom")
            .visible(this::isParticleMode);
    public final MultiSelectSetting physics = new MultiSelectSetting(
            "setting.killeffect.physics.name", "setting.killeffect.physics.desc")
            .value("Fall", "Fly", "Emerge")
            .selected("Emerge")
            .visible(this::isParticleMode);
    public final ValueSetting scale = new ValueSetting(
            "setting.killeffect.scale.name", "setting.killeffect.scale.desc")
            .setValue(1.5F)
            .range(0.5F, 5.0F)
            .visible(this::isParticleMode);
    public final ValueSetting lifeTime = new ValueSetting(
            "setting.killeffect.lifetime.name", "setting.killeffect.lifetime.desc")
            .setValue(3.0F)
            .range(1, 10)
            .visible(this::isParticleMode);
    public final ValueSetting speed = new ValueSetting(
            "setting.killeffect.speed.name", "setting.killeffect.speed.desc")
            .setValue(1.0F)
            .range(0.1F, 3.0F)
            .visible(this::isParticleMode);
    public final ValueSetting amount = new ValueSetting(
            "setting.killeffect.amount.name", "setting.killeffect.amount.desc")
            .setValue(30.0F)
            .range(10, 70)
            .visible(this::isParticleMode);
    public final ValueSetting linkDistance = new ValueSetting(
            "setting.killeffect.linkdistance.name", "setting.killeffect.linkdistance.desc")
            .setValue(1.0F)
            .range(0.5F, 2.0F)
            .visible(() -> this.isParticleMode() && this.mode.getSelected().contains("Network"));
    public final BooleanSetting enableSound = new BooleanSetting(
            "setting.killeffect.enablesound.name", "setting.killeffect.enablesound.desc")
            .setValue(false);
    public final SelectSetting soundType = new SelectSetting(
            "setting.killeffect.soundtype.name", "setting.killeffect.soundtype.desc")
            .value("Abmiss", "Critow", "Final Blink", "Final Pok", "Neptune", "Rust Headshot", "Whii")
            .selected("Neptune")
            .visible(this.enableSound::isValue);
    public final ValueSetting soundVolume = new ValueSetting(
            "setting.killeffect.soundvolume.name", "setting.killeffect.soundvolume.desc")
            .setValue(1.0F)
            .range(0.1F, 2.0F)
            .visible(this.enableSound::isValue);
    public final ValueSetting soundPitch = new ValueSetting(
            "setting.killeffect.soundpitch.name", "setting.killeffect.soundpitch.desc")
            .setValue(1.0F)
            .range(0.5F, 2.0F)
            .visible(this.enableSound::isValue);
    public final ValueSetting trsLifetime = new ValueSetting(
            "setting.killeffect.trslifetime.name", "setting.killeffect.trslifetime.desc")
            .setValue(1.8F)
            .range(0.5F, 5.0F)
            .visible(this::isTargetRenderSyncMode);
    public final ValueSetting trsExtraSpeed = new ValueSetting(
            "setting.killeffect.trsextraspeed.name", "setting.killeffect.trsextraspeed.desc")
            .setValue(1.0F)
            .range(0.1F, 3.0F)
            .visible(this::isTargetRenderSyncMode);
    public final BooleanSetting trsDisableRedEffect = new BooleanSetting(
            "setting.killeffect.trsdisablered.name", "setting.killeffect.trsdisablered.desc")
            .setValue(false)
            .visible(this::isTargetRenderSyncMode);

    /**
     * Client-side world effects that need the game: at the moment only the lightning bolt of
     * the {@code Thunder} option. Installed by the client layer.
     */
    public interface WorldEffects {
        void spawnLightning(double x, double y, double z);
    }

    private static volatile WorldEffects worldEffects;

    public static void setWorldEffects(WorldEffects effects) {
        worldEffects = effects;
    }

    public KillEffect() {
        super("module.killeffect.name", SoupModuleCategory.OTHER);
        GroupSetting effectGroup = new GroupSetting("group.killeffect.effect.name",
                "group.killeffect.effect.desc", false)
                .settings(this.effectType, this.friendEffectType, this.showForFriends, this.targetType);
        GroupSetting colorGroup = new GroupSetting("group.killeffect.colors.name",
                "group.killeffect.colors.desc", false)
                .settings(this.colorMode, this.customColorsCount, this.customColors, this.colorAnimation)
                .visible(this::isParticleMode);
        GroupSetting appearanceGroup = new GroupSetting("group.killeffect.appearance.name",
                "group.killeffect.appearance.desc", false)
                .settings(this.mode, this.physics, this.scale)
                .visible(this::isParticleMode);
        GroupSetting behaviorGroup = new GroupSetting("group.killeffect.behavior.name",
                "group.killeffect.behavior.desc", false)
                .settings(this.lifeTime, this.speed, this.amount)
                .visible(this::isParticleMode);
        GroupSetting trsGroup = new GroupSetting("group.killeffect.trs.name",
                "group.killeffect.trs.desc", false)
                .settings(this.trsLifetime, this.trsExtraSpeed, this.trsDisableRedEffect)
                .visible(this::isTargetRenderSyncMode);
        GroupSetting soundGroup = new GroupSetting("group.killeffect.sound.name",
                "group.killeffect.sound.desc", false)
                .settings(this.enableSound, this.soundType, this.soundVolume, this.soundPitch);
        this.setup(effectGroup, colorGroup, appearanceGroup, behaviorGroup, this.linkDistance,
                trsGroup, soundGroup);
    }

    private boolean isParticleMode() {
        return this.effectType.isSelected("Particles")
                || this.showForFriends.isValue() && this.friendEffectType.isSelected("Particles");
    }

    private boolean isTargetRenderSyncMode() {
        return this.effectType.isSelected("TR Sync")
                || this.showForFriends.isValue() && this.friendEffectType.isSelected("TR Sync");
    }

    @Override
    protected void deactivate() {
        this.particles.clear();
        this.networkParticles.clear();
        this.recentlyAttacked.clear();
        this.recentlyAttackedByFriends.clear();
        this.pendingKills.clear();
        this.firedKills.clear();
        this.playerLastTickPos = null;
        this.spatialGrid = null;
    }

    // ------------------------------------------------------------------ hooks

    /**
     * Attack hook: remembers who was hit and with how much health, exactly like the original's
     * {@code EventAttack} post half.
     */
    public void onAttack(EntitySnapshot target) {
        if (!this.isEnabled() || target == null) {
            return;
        }
        if (this.firedKills.containsKey(target.id())) {
            return;
        }
        this.recentlyAttacked.computeIfAbsent(target.id(),
                id -> new AttackRecord(System.currentTimeMillis(), target.x(), target.y(),
                        target.z(), healthOf(target)));
    }

    /**
     * Per-tick kill detection: the original's {@code onTick}, with the damage-confirmation and
     * death-event paths folded into the same poll because both are public-hook-less in SilkVis.
     *
     * @param entities         every loaded living entity except the local player
     * @param playerAlive      {@code player.isAlive() && !player.isDead() && health > 0}
     * @param playerX/Y/Z      current player position, for the teleport check
     */
    public void tick(List<EntitySnapshot> entities, boolean playerAlive,
                     double playerX, double playerY, double playerZ, float tickProgress) {
        if (!this.isEnabled()) {
            return;
        }
        if (!playerAlive) {
            this.clearTracking();
            this.playerLastTickPos = null;
            return;
        }
        if (this.playerLastTickPos != null) {
            double dx = playerX - this.playerLastTickPos[0];
            double dy = playerY - this.playerLastTickPos[1];
            double dz = playerZ - this.playerLastTickPos[2];
            if (dx * dx + dy * dy + dz * dz > TELEPORT_DIST_SQ) {
                this.clearTracking();
            }
        }
        this.playerLastTickPos = new double[]{playerX, playerY, playerZ};

        long currentTime = System.currentTimeMillis();
        this.recentlyAttacked.entrySet().removeIf(e -> currentTime - e.getValue().timestamp > ATTACK_TIMEOUT_MS);
        this.recentlyAttackedByFriends.entrySet()
                .removeIf(e -> currentTime - e.getValue().timestamp > ATTACK_TIMEOUT_MS);
        this.firedKills.entrySet().removeIf(e -> currentTime - e.getValue() > DEDUP_COOLDOWN_MS);

        Set<Integer> seen = new HashSet<>();
        for (EntitySnapshot entity : entities) {
            seen.add(entity.id());
            AttackRecord ours = this.recentlyAttacked.get(entity.id());
            AttackRecord friends = this.recentlyAttackedByFriends.get(entity.id());
            boolean dead = !entity.alive();
            if (dead) {
                if (ours != null) {
                    this.fireKillEffect(entity, false);
                    continue;
                }
                if (friends != null) {
                    this.fireKillEffect(entity, true);
                    continue;
                }
            }
            // T4 rule of the original: a confirmed hit whose target suddenly moved far away and
            // gained health was killed by the server (e.g. void death with a teleport back), so
            // the effect fires at the last known position.
            if (ours != null && ours.damageConfirmed && ours.lastKnownPos != null
                    && !this.firedKills.containsKey(entity.id())) {
                double dx = entity.x() - ours.lastKnownPos[0];
                double dy = entity.y() - ours.lastKnownPos[1];
                double dz = entity.z() - ours.lastKnownPos[2];
                if (dx * dx + dy * dy + dz * dz > TELEPORT_DIST_SQ
                        && healthOf(entity) > ours.lastKnownHealth) {
                    this.firedKills.put(entity.id(), currentTime);
                    this.recentlyAttacked.remove(entity.id());
                    this.pendingKills.remove(entity.id());
                    this.fireKillEffectAtPosition(
                            new double[]{ours.lastKnownPos[0], ours.lastKnownPos[1] + entity.height() / 2.0,
                                    ours.lastKnownPos[2]}, false);
                    continue;
                }
            }
            if (friends != null && friends.damageConfirmed && friends.lastKnownPos != null
                    && !this.firedKills.containsKey(entity.id())) {
                double dx = entity.x() - friends.lastKnownPos[0];
                double dy = entity.y() - friends.lastKnownPos[1];
                double dz = entity.z() - friends.lastKnownPos[2];
                if (dx * dx + dy * dy + dz * dz > TELEPORT_DIST_SQ
                        && healthOf(entity) > friends.lastKnownHealth) {
                    this.firedKills.put(entity.id(), currentTime);
                    this.recentlyAttackedByFriends.remove(entity.id());
                    this.pendingKills.remove(entity.id());
                    this.fireKillEffectAtPosition(
                            new double[]{friends.lastKnownPos[0],
                                    friends.lastKnownPos[1] + entity.height() / 2.0,
                                    friends.lastKnownPos[2]}, true);
                    continue;
                }
            }

            // "Confirmed" = the target reacted with a hurt flash after our attack. Both records
            // get confirmed, as the original does on the server damage packet.
            if (entity.hurtTime() > 0) {
                if (ours != null) {
                    ours.damageConfirmed = true;
                }
                if (friends != null) {
                    friends.damageConfirmed = true;
                }
            }
            if (ours != null) {
                ours.lastKnownHealth = healthOf(entity);
                ours.lastKnownPos = new double[]{entity.x(), entity.y(), entity.z()};
            }
            if (friends != null) {
                friends.lastKnownHealth = healthOf(entity);
                friends.lastKnownPos = new double[]{entity.x(), entity.y(), entity.z()};
            }
            this.pendingKills.remove(entity.id());
        }

        this.addVanishedToPending(this.recentlyAttacked, currentTime, false, seen);
        this.addVanishedToPending(this.recentlyAttackedByFriends, currentTime, true, seen);

        var pendingIt = this.pendingKills.entrySet().iterator();
        while (pendingIt.hasNext()) {
            Map.Entry<Integer, PendingKill> entry = pendingIt.next();
            PendingKill pending = entry.getValue();
            if (currentTime - pending.removedTime >= PENDING_DELAY_MS) {
                if (!seen.contains(entry.getKey())) {
                    this.fireKillEffectAtPosition(pending.position, pending.killedByFriend);
                    this.firedKills.put(entry.getKey(), currentTime);
                    this.recentlyAttacked.remove(entry.getKey());
                    this.recentlyAttackedByFriends.remove(entry.getKey());
                }
                pendingIt.remove();
            }
        }
        this.tickParticles(currentTime, tickProgress);
    }

    /** Port of {@code addVanishedToPending}: an entity that left the world after a confirmed hit. */
    private void addVanishedToPending(Map<Integer, AttackRecord> attackMap, long currentTime,
                                      boolean byFriend, Set<Integer> seen) {
        for (Map.Entry<Integer, AttackRecord> entry : attackMap.entrySet()) {
            int entityId = entry.getKey();
            AttackRecord record = entry.getValue();
            boolean inWorld = seen.contains(entityId);
            boolean hasPending = this.pendingKills.containsKey(entityId);
            boolean hasFired = this.firedKills.containsKey(entityId);
            if (record.damageConfirmed && !inWorld && !hasPending && !hasFired
                    && record.lastKnownPos != null) {
                this.pendingKills.put(entityId, new PendingKill(record.lastKnownPos, currentTime, byFriend));
            }
        }
    }

    /** Port of {@code fireKillEffect}: filters, then runs the selected effect. */
    private void fireKillEffect(EntitySnapshot entity, boolean killedByFriend) {
        int entityId = entity.id();
        if (this.firedKills.containsKey(entityId)) {
            return;
        }
        this.firedKills.put(entityId, System.currentTimeMillis());
        this.recentlyAttacked.remove(entityId);
        this.recentlyAttackedByFriends.remove(entityId);
        this.pendingKills.remove(entityId);
        String targetTypeSelected = this.targetType.getSelected();
        if ("Player".equals(targetTypeSelected) && !entity.player()) {
            return;
        }
        if ("Mobs".equals(targetTypeSelected) && entity.player()) {
            return;
        }
        if (killedByFriend && !this.showForFriends.isValue()) {
            return;
        }
        String selectedEffectType = killedByFriend
                ? this.friendEffectType.getSelected() : this.effectType.getSelected();
        double[] position = {entity.x(), entity.y() + entity.height() / 2.0, entity.z()};
        switch (selectedEffectType) {
            case "Thunder" -> this.spawnThunderEffect(position);
            case "TR Sync" -> {
                // TargetRender is not ported yet: keep the position-based particles so the kill
                // is still visible. Recorded in docs/known-limitations.md.
                this.spawnParticlesEffect(position);
            }
            default -> this.spawnParticlesEffect(position);
        }
        if (this.enableSound.isValue()) {
            this.playKillSound();
        }
    }

    private void fireKillEffectAtPosition(double[] position, boolean killedByFriend) {
        if (killedByFriend && !this.showForFriends.isValue()) {
            return;
        }
        String selectedEffectType = killedByFriend
                ? this.friendEffectType.getSelected() : this.effectType.getSelected();
        switch (selectedEffectType) {
            case "Thunder" -> this.spawnThunderEffect(position);
            case "TR Sync" -> this.spawnParticlesEffect(position);
            default -> this.spawnParticlesEffect(position);
        }
        if (this.enableSound.isValue()) {
            this.playKillSound();
        }
    }

    private void playKillSound() {
        SoupSounds.play(soundPathFor(this.soundType.getSelected()),
                this.soundVolume.getValue(), this.soundPitch.getValue());
    }

    /**
     * Original {@code KillEffect.playSound}: the option label of the settings screen mapped to
     * a registered {@code silkvis:kill_sounds/*} path. Public so the self-check can prove the
     * label -> sound mapping without a sound manager; "Neptune" is the default of the
     * {@code switch}, exactly like the original's {@code default} branch.
     */
    public static String soundPathFor(String option) {
        return switch (option == null ? "" : option) {
            case "Abmiss" -> SoupSounds.KILL_ABMISS;
            case "Critow" -> SoupSounds.KILL_CRITOW;
            case "Final Blink" -> SoupSounds.KILL_FINAL_BLINK;
            case "Final Pok" -> SoupSounds.KILL_FINAL_POK;
            case "Rust Headshot" -> SoupSounds.KILL_RUST_HEADSHOT;
            case "Whii" -> SoupSounds.KILL_WHII;
            default -> SoupSounds.KILL_NEPTUNE;
        };
    }

    private void spawnParticlesEffect(double[] position) {
        int colorInt;
        if (this.colorMode.isSelected("Sync")) {
            colorInt = ColorUtil.getClientColor();
        } else {
            int[] colors = this.getCustomColors();
            colorInt = colors != null && colors.length > 0 ? colors[0] : -1;
        }
        int count = (int) this.amount.getValue();
        for (int i = 0; i < count; i++) {
            String selectedMode = this.getRandomMode();
            String selectedPhysics = "Network".equals(selectedMode) ? "Fly" : this.getRandomPhysics();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            BurstParticle particle = new BurstParticle(
                    (float) position[0], (float) position[1], (float) position[2],
                    colorInt,
                    (float) (random.nextDouble() * 360.0),
                    (float) (random.nextDouble() * 25.0 + 5.0),
                    0.0F,
                    selectedMode,
                    selectedPhysics,
                    "Glyph".equals(selectedMode) ? silkvis.visual.soup.particles.ParticleData.getRandomGlyphTexture()
                            : ("Things".equals(selectedMode)
                            ? silkvis.visual.soup.particles.ParticleData.getRandomGlyphAltTexture() : null),
                    this.lifeTime.getValue(),
                    this.scale.getValue(),
                    this.speed.getValue(),
                    this);
            this.particles.add(particle);
            if ("Network".equals(selectedMode)) {
                if (this.spatialGrid == null) {
                    this.spatialGrid = new SpatialGrid3D<>(this.linkDistance.getValue());
                }
                this.networkParticles.add(particle);
                this.spatialGrid.insert(particle, particle.getX(), particle.getY(), particle.getZ());
            }
        }
    }

    private void spawnThunderEffect(double[] position) {
        WorldEffects effects = worldEffects;
        if (effects != null) {
            effects.spawnLightning(position[0], position[1], position[2]);
        }
    }

    private void tickParticles(long currentTime, float tickProgress) {
        this.playedThisTick = false;
        silkvis.visual.soup.particles.ParticleUpdateExecutor.updateParticlesInPlace(
                this.particles, currentTime, particle -> true, null, particle -> {
                    if ("Network".equals(particle.getParticleMode()) && this.spatialGrid != null) {
                        this.spatialGrid.remove(particle, particle.getX(), particle.getY(), particle.getZ());
                        this.networkParticles.remove(particle);
                    }
                });
        if (this.spatialGrid != null && !this.networkParticles.isEmpty()) {
            for (WorldParticle particle : this.networkParticles) {
                this.spatialGrid.update(particle, particle.getPx(), particle.getPy(), particle.getPz(),
                        particle.getX(), particle.getY(), particle.getZ());
            }
        }
    }

    /** Port of {@code onWorldRender}: particles plus the network links of the spatial grid. */
    public void render(Matrix4f cameraMatrix, float cameraPitch, float cameraYaw,
                       double camX, double camY, double camZ, float tickProgress) {
        if (!this.isEnabled() || this.particles.isEmpty()) {
            return;
        }
        for (WorldParticle particle : this.particles) {
            particle.render(cameraMatrix, camX, camY, camZ, tickProgress, cameraYaw, cameraPitch);
        }
        if (this.mode.getSelected().contains("Network") && this.spatialGrid != null
                && this.networkParticles.size() >= 2) {
            this.renderNetworkLinks(cameraMatrix, camX, camY, camZ);
        }
    }

    private void renderNetworkLinks(Matrix4f cameraMatrix, double camX, double camY, double camZ) {
        float maxDistance = this.linkDistance.getValue();
        int color = ColorUtil.multAlpha(ColorUtil.getClientColor(), 0.6F);
        Set<Long> visited = this.spatialGrid.newVisitedSet();
        for (WorldParticle particle : this.networkParticles) {
            List<WorldParticle> neighbours = this.spatialGrid.query(particle.getX(), particle.getY(),
                    particle.getZ(), maxDistance);
            for (WorldParticle neighbour : neighbours) {
                if (neighbour == particle) {
                    continue;
                }
                long key = SpatialGrid3D.pairKey((int) particle.getX(), (int) particle.getY(),
                        (int) particle.getZ(), (int) neighbour.getX(), (int) neighbour.getY(),
                        (int) neighbour.getZ());
                if (!visited.add(key)) {
                    continue;
                }
                silkvis.visual.soup.render.SoupRender.drawLine(cameraMatrix,
                        new org.joml.Vector3f((float) (particle.getX() - camX),
                                (float) (particle.getY() - camY), (float) (particle.getZ() - camZ)),
                        new org.joml.Vector3f((float) (neighbour.getX() - camX),
                                (float) (neighbour.getY() - camY), (float) (neighbour.getZ() - camZ)),
                        color, color, false);
            }
        }
    }

    private void clearTracking() {
        this.recentlyAttacked.clear();
        this.recentlyAttackedByFriends.clear();
        this.pendingKills.clear();
    }

    private String getRandomMode() {
        List<String> selected = this.mode.getSelected();
        return selected.isEmpty() ? "Stars" : selected.get(ThreadLocalRandom.current().nextInt(selected.size()));
    }

    private String getRandomPhysics() {
        List<String> selected = this.physics.getSelected();
        return selected.isEmpty() ? "Emerge" : selected.get(ThreadLocalRandom.current().nextInt(selected.size()));
    }

    /** Health carried by the snapshot, so the "teleported and healed" path keeps working. */
    private static float healthOf(EntitySnapshot entity) {
        return entity.health();
    }

    @Override
    public int[] getCustomColors() {
        if (!this.colorMode.isSelected("Custom")) {
            return null;
        }
        return switch (this.customColorsCount.getSelected()) {
            case "Solo" -> new int[]{this.customColors.getColor1().getColor()};
            case "Duo" -> new int[]{
                    this.customColors.getColor1().getColor(), this.customColors.getColor2().getColor()};
            case "Triple" -> new int[]{
                    this.customColors.getColor1().getColor(), this.customColors.getColor2().getColor(),
                    this.customColors.getColor3().getColor()};
            case "Quartet" -> this.customColors.getColorValues();
            default -> null;
        };
    }

    @Override
    public SelectSetting getColorMode() {
        return this.colorMode;
    }

    @Override
    public SelectSetting getColorAnimation() {
        return this.colorAnimation;
    }

    @Override
    public MultiColorSetting getMultiColorSetting() {
        return this.customColors;
    }

    public List<WorldParticle> getParticles() {
        return this.particles;
    }

    public int pendingKillCount() {
        return this.pendingKills.size();
    }

    public int trackedKillCount() {
        return this.recentlyAttacked.size();
    }

    /** One attack the player (or a friend) landed, with the last data seen of the target. */
    static final class AttackRecord {
        final long timestamp;
        final double x;
        final double y;
        final double z;
        boolean damageConfirmed;
        float lastKnownHealth;
        double[] lastKnownPos;

        AttackRecord(long timestamp, double x, double y, double z, float health) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastKnownHealth = health;
            this.lastKnownPos = new double[]{x, y, z};
        }
    }

    /** A kill that has to be confirmed by a 100 ms absence from the world. */
    static final class PendingKill {
        final double[] position;
        final long removedTime;
        final boolean killedByFriend;

        PendingKill(double[] position, long removedTime, boolean killedByFriend) {
            this.position = position;
            this.removedTime = removedTime;
            this.killedByFriend = killedByFriend;
        }
    }

    /**
     * Port of the original's anonymous {@code WorldParticle} subclass: the same gaussian
     * motion of {@code speed * 0.05} on every axis instead of the physics-mode motion.
     */
    static final class BurstParticle extends WorldParticle {
        BurstParticle(float x, float y, float z, int color, float rotationAngle,
                      float rotationSpeed, float health, String mode, String physics,
                      String glyphTexture, float lifeTime, float scale, float speed,
                      ParticleModule module) {
            super(x, y, z, color, rotationAngle, rotationSpeed, health, mode, physics,
                    glyphTexture, lifeTime, scale, speed, module);
        }

        @Override
        protected void initMotion(float speed) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float scale = speed * 0.05F;
            this.motionX = (float) (random.nextGaussian() * scale);
            this.motionY = (float) (random.nextGaussian() * scale);
            this.motionZ = (float) (random.nextGaussian() * scale);
        }
    }
}

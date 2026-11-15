package silkvis.visual.soup.modules;

import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4i;
import org.joml.Vector3f;
import silkvis.visual.soup.module.ParticleModule;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.module.SoupModuleCategory;
import silkvis.visual.soup.render.SoupRender;
import silkvis.visual.soup.setting.BooleanSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.MultiColorSetting;
import silkvis.visual.soup.setting.MultiSelectSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.ValueSetting;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.EntitySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FriendsTargetRender -- shows what each of your friends is aiming at.
 *
 * <p>Ported from SoupVisuals 1.21.11
 * {@code padej.soup.implement.features.modules.visuals.FriendsTargetRender} and the parts of
 * {@code base.util.render.TargetRenderer} it uses (Padej_, SOUP-1.0, used by written
 * permission). Settings, groups and defaults are the original's.</p>
 *
 * <p><b>What was ported:</b> the whole tracking model -- one slot per target per friend with a
 * 200 ms appear/disappear animation, the "follow the last target for Follow Time" rule, the
 * priority rule ("Self" hides a target the local player is already aiming at), the distance
 * and line-of-sight checks, the Mobs/Player filter, the colour modes and the {@code Legacy} and
 * {@code Circle} render types with their rotating billboard and the hurt flash.</p>
 *
 * <p><b>Not ported yet:</b> the {@code Ghosts} and {@code Crystals} render types. Both are
 * built on {@code ParticleBatchRenderer} and {@code TargetESPCrystals}, i.e. the VBO batch and
 * the explosion pools that belong to the (still unported) {@code TargetRender} module. With
 * them selected the module draws the {@code Legacy} billboard instead of nothing, so a friend's
 * target stays visible. This is listed in {@code docs/known-limitations.md}.</p>
 *
 * <p><b>Difficulties:</b> the original reads the friend's target with a raycast of the friend's
 * own view vector ({@code ProjectileUtil.raycast} from the friend's eyes). That raycast needs
 * the {@code World}, so the client layer performs it and hands the resulting entity ids in;
 * {@link #tick} then applies the original's bookkeeping unchanged.</p>
 *
 * <p><b>To verify in game:</b> a friend aiming at a mob shows the marker, the marker stays for
 * {@code Follow Time} after the friend looks away, the {@code Self} priority hides a duplicated
 * marker, the marker disappears beyond {@code Max Distance}, and the four legacy textures.</p>
 */
public class FriendsTargetRender extends SoupModule implements ParticleModule {
    /** Original animation length of one render slot. */
    static final int ANIMATION_MS = 200;
    private static final String LEGACY_TEXTURE_BASE = "textures/soup/legacy/";

    private final Map<String, FriendTargetData> friendTargets = new HashMap<>();
    /** Constant animation option: the original marker does not animate its colours. */
    public final SelectSetting colorAnimation = new SelectSetting(
            "setting.friendstargetrender.coloranimation.name",
            "setting.friendstargetrender.coloranimation.desc")
            .value("Wave", "Vertexes")
            .selected("Wave");

    private float espValue = 1.0F;
    private float circleStep;
    private float prevCircleStep;
    private long lastUpdateTick = -1L;

    public final SelectSetting colorMode = new SelectSetting(
            "setting.friendstargetrender.colormode.name", "setting.friendstargetrender.colormode.desc")
            .value("Sync", "Custom")
            .selected("Custom");
    public final SelectSetting customColorsCount = new SelectSetting(
            "setting.friendstargetrender.colorcount.name", "setting.friendstargetrender.colorcount.desc")
            .value("Solo", "Duo", "Triple", "Quartet")
            .selected("Solo")
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final MultiColorSetting customColors = new MultiColorSetting(
            "setting.friendstargetrender.gradientcolors.name", "setting.friendstargetrender.gradientcolors.desc")
            .colors("Color 1", "Color 2", "Color 3", "Color 4")
            .defaultColors(-1499549, -13273872, -6596170, -409301)
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final SelectSetting targetEspType = new SelectSetting(
            "setting.friendstargetrender.rendertype.name", "setting.friendstargetrender.rendertype.desc")
            .value("Legacy", "Circle", "Ghosts", "Crystals")
            .selected("Legacy");
    public final SelectSetting legacyTexture = new SelectSetting(
            "setting.friendstargetrender.legacytexture.name", "setting.friendstargetrender.legacytexture.desc")
            .value("Amogus", "Bo", "Capture", "Jeka", "Marker", "Scifi", "Simple", "Skull", "Vegas")
            .selected("Capture");
    public final BooleanSetting optimalAim = new BooleanSetting(
            "setting.friendstargetrender.optimalaim.name", "setting.friendstargetrender.optimalaim.desc")
            .setValue(false);
    public final ValueSetting legacySize = new ValueSetting(
            "setting.friendstargetrender.legacysize.name", "setting.friendstargetrender.legacysize.desc")
            .setValue(1.0F)
            .range(0.5F, 1.5F);
    public final SelectSetting ghostsTexture = new SelectSetting(
            "setting.friendstargetrender.ghoststexture.name", "setting.friendstargetrender.ghoststexture.desc")
            .value("Bloom", "Soft")
            .selected("Soft");
    public final SelectSetting ghostsBlend = new SelectSetting(
            "setting.friendstargetrender.ghostsblend.name", "setting.friendstargetrender.ghostsblend.desc")
            .value("Smoke", "Plasma")
            .selected("Plasma");
    public final ValueSetting ghostsLength = new ValueSetting(
            "setting.friendstargetrender.ghostslength.name", "setting.friendstargetrender.ghostslength.desc")
            .setValue(1.3F)
            .range(0.5F, 1.5F);
    public final SelectSetting ghostsTrajectory = new SelectSetting(
            "setting.friendstargetrender.ghoststrajectory.name", "setting.friendstargetrender.ghoststrajectory.desc")
            .value("Standard", "Spiral", "Atomic")
            .selected("Standard");
    public final ValueSetting ghostsRadiusModifier = new ValueSetting(
            "setting.friendstargetrender.ghostsradiusmodifier.name",
            "setting.friendstargetrender.ghostsradiusmodifier.desc")
            .setValue(1.6F)
            .range(0.5F, 2.0F);
    public final ValueSetting ghostsHeadSize = new ValueSetting(
            "setting.friendstargetrender.ghostsheadsize.name", "setting.friendstargetrender.ghostsheadsize.desc")
            .setValue(0.7F)
            .range(0.1F, 1.0F);
    public final ValueSetting ghostsTailSize = new ValueSetting(
            "setting.friendstargetrender.ghoststailsize.name", "setting.friendstargetrender.ghoststailsize.desc")
            .setValue(0.1F)
            .range(0.1F, 1.0F);
    public final ValueSetting ghostsCount = new ValueSetting(
            "setting.friendstargetrender.ghostscount.name", "setting.friendstargetrender.ghostscount.desc")
            .setValue(4.0F)
            .range(1, 8);
    public final ValueSetting ghostsSubdivision = new ValueSetting(
            "setting.friendstargetrender.ghostssubdivision.name",
            "setting.friendstargetrender.ghostssubdivision.desc")
            .setValue(5.0F)
            .range(1, 8);
    public final ValueSetting crystalsDistance = new ValueSetting(
            "setting.friendstargetrender.crystalsdistance.name", "setting.friendstargetrender.crystalsdistance.desc")
            .setValue(0.5F)
            .range(0.5F, 1.0F);
    public final ValueSetting crystalsSize = new ValueSetting(
            "setting.friendstargetrender.crystalssize.name", "setting.friendstargetrender.crystalssize.desc")
            .setValue(0.35F)
            .range(0.2F, 0.4F);
    public final BooleanSetting crystalsGlow = new BooleanSetting(
            "setting.friendstargetrender.crystalsglow.name", "setting.friendstargetrender.crystalsglow.desc")
            .setValue(true);
    public final ValueSetting crystalsGlowSize = new ValueSetting(
            "setting.friendstargetrender.crystalsglowsize.name", "setting.friendstargetrender.crystalsglowsize.desc")
            .setValue(1.5F)
            .range(1.5F, 3.0F);
    public final SelectSetting crystalsOrientation = new SelectSetting(
            "setting.friendstargetrender.crystalsorientation.name",
            "setting.friendstargetrender.crystalsorientation.desc")
            .value("Center", "Horizontal")
            .selected("Center");
    public final SelectSetting renderPriority = new SelectSetting(
            "setting.friendstargetrender.priority.name", "setting.friendstargetrender.priority.desc")
            .value("Self", "Friend", "Both")
            .selected("Friend");
    public final MultiSelectSetting showOn = new MultiSelectSetting(
            "setting.friendstargetrender.showon.name", "setting.friendstargetrender.showon.desc")
            .value("Self", "Friends")
            .selected("Friends");
    public final ValueSetting followTime = new ValueSetting(
            "setting.friendstargetrender.followtime.name", "setting.friendstargetrender.followtime.desc")
            .setValue(5.0F)
            .range(0, 10);
    public final ValueSetting speedMod = new ValueSetting(
            "setting.friendstargetrender.speedmod.name", "setting.friendstargetrender.speedmod.desc")
            .setValue(0.7F)
            .range(0.5F, 2.0F);
    public final ValueSetting maxDistance = new ValueSetting(
            "setting.friendstargetrender.maxdistance.name", "setting.friendstargetrender.maxdistance.desc")
            .setValue(128.0F)
            .range(16.0F, 128.0F);

    public FriendsTargetRender() {
        super("module.friendstargetrender.name", SoupModuleCategory.VISUALS);
        GroupSetting colorGroup = new GroupSetting("group.friendstargetrender.colors.name",
                "group.friendstargetrender.colors.desc", false)
                .settings(this.colorMode, this.customColorsCount, this.customColors);
        GroupSetting legacyGroup = new GroupSetting("group.friendstargetrender.legacy.name",
                "group.friendstargetrender.legacy.desc", false)
                .settings(this.legacyTexture, this.optimalAim, this.legacySize)
                .visible(() -> this.targetEspType.isSelected("Legacy"));
        GroupSetting ghostsVisualGroup = new GroupSetting("group.friendstargetrender.ghosts.visual.name",
                "group.friendstargetrender.ghosts.visual.desc", false)
                .settings(this.ghostsTexture, this.ghostsBlend);
        GroupSetting ghostsAnimationGroup = new GroupSetting(
                "group.friendstargetrender.ghosts.animation.name",
                "group.friendstargetrender.ghosts.animation.desc", false)
                .settings(this.ghostsLength, this.ghostsTrajectory);
        GroupSetting ghostsSizeGroup = new GroupSetting("group.friendstargetrender.ghosts.size.name",
                "group.friendstargetrender.ghosts.size.desc", false)
                .settings(this.ghostsRadiusModifier, this.ghostsHeadSize, this.ghostsTailSize,
                        this.ghostsCount, this.ghostsSubdivision);
        GroupSetting ghostsGroup = new GroupSetting("group.friendstargetrender.ghosts.name",
                "group.friendstargetrender.ghosts.desc", false)
                .settings(ghostsVisualGroup, ghostsAnimationGroup, ghostsSizeGroup)
                .visible(() -> this.targetEspType.isSelected("Ghosts"));
        GroupSetting crystalsGroup = new GroupSetting("group.friendstargetrender.crystals.name",
                "group.friendstargetrender.crystals.desc", false)
                .settings(this.crystalsDistance, this.crystalsSize, this.crystalsGlow,
                        this.crystalsGlowSize, this.crystalsOrientation)
                .visible(() -> this.targetEspType.isSelected("Crystals"));
        GroupSetting behaviorGroup = new GroupSetting("group.friendstargetrender.behavior.name",
                "group.friendstargetrender.behavior.desc", false)
                .settings(this.showOn, this.renderPriority, this.followTime, this.speedMod, this.maxDistance);
        this.setup(colorGroup, this.targetEspType, legacyGroup, ghostsGroup, crystalsGroup, behaviorGroup);
    }

    @Override
    protected void deactivate() {
        this.friendTargets.clear();
    }

    // ------------------------------------------------------------------ per tick

    /**
     * Port of the original's {@code TickEvent}: for every friend, keep the record up to date,
     * start the forwards animation on a new target, the backwards one on the previous, and drop
     * friends that left the world or the distance limit.
     *
     * @param friends   one entry per friend that is within {@code Max Distance} of the player,
     *                  each with the entity id its own raycast found (or {@code -1})
     * @param targets   snapshot of every entity that may be shown, by id
     */
    public void tick(List<FriendSnapshot> friends, Map<Integer, EntitySnapshot> targets,
                     double playerX, double playerY, double playerZ) {
        if (!this.isEnabled()) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        java.util.Set<String> live = new java.util.HashSet<>();
        for (FriendSnapshot friend : friends) {
            live.add(friend.key());
            FriendTargetData data = this.friendTargets.computeIfAbsent(friend.key(),
                    key -> new FriendTargetData());
            EntitySnapshot previousTarget = data.targetEntity;
            EntitySnapshot target = targets.get(friend.targetId());
            if (target != null && (!target.alive() || !this.isWithinMaxDistance(target, playerX, playerY, playerZ))) {
                target = null;
            }
            data.targetEntity = target;
            if (target != null) {
                data.lastActiveTargetId = target.id();
            }
            int currentTargetId = target != null ? target.id() : -1;
            if (!sameTarget(previousTarget, target)) {
                for (Map.Entry<Integer, RenderSlot> entry : data.renderSlots.entrySet()) {
                    if (entry.getKey() != currentTargetId) {
                        RenderSlot slot = entry.getValue();
                        if (slot.lastActiveTime == 0L) {
                            slot.lastActiveTime = currentTime;
                        }
                        slot.setDirection(false);
                    }
                }
                if (target != null) {
                    int targetId = target.id();
                    RenderSlot newSlot = data.renderSlots.computeIfAbsent(targetId,
                            id -> new RenderSlot(ANIMATION_MS));
                    newSlot.entity = target;
                    newSlot.setDirection(true);
                    newSlot.lastActiveTime = 0L;
                }
            }
            if (target != null) {
                RenderSlot slot = data.renderSlots.get(target.id());
                if (slot != null) {
                    slot.entity = target;
                }
            }
        }
        this.friendTargets.keySet().removeIf(key -> !live.contains(key));
        this.updateAnimations(this.speedMod.getValue());
    }

    private static boolean sameTarget(EntitySnapshot a, EntitySnapshot b) {
        if (a == null) {
            return b == null;
        }
        return b != null && a.id() == b.id();
    }

    /** Port of {@code TargetRenderer.updateAnimations}: one global pulse for the markers. */
    private void updateAnimations(float speed) {
        long tick = System.currentTimeMillis() / 50L;
        if (tick == this.lastUpdateTick) {
            return;
        }
        this.lastUpdateTick = tick;
        this.prevCircleStep = this.circleStep;
        this.circleStep += speed;
        if (this.circleStep > 360.0F) {
            this.circleStep -= 360.0F;
            this.prevCircleStep -= 360.0F;
        }
        this.espValue += speed;
        if (this.espValue > 360.0F) {
            this.espValue -= 360.0F;
        }
    }

    private boolean isWithinMaxDistance(EntitySnapshot entity, double playerX, double playerY,
                                        double playerZ) {
        double maxSq = this.maxDistance.getValue() * this.maxDistance.getValue();
        double dx = entity.x() - playerX;
        double dy = entity.y() - playerY;
        double dz = entity.z() - playerZ;
        return dx * dx + dy * dy + dz * dz <= maxSq;
    }

    // ------------------------------------------------------------------ per frame

    /** Port of the original's {@code WorldRenderEvent}. */
    public void render(Matrix4f cameraMatrix, float cameraPitch, float cameraYaw,
                       double camX, double camY, double camZ, int selfTargetId) {
        if (this.friendTargets.isEmpty()) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        long followTimeMs = (long) (this.followTime.getValue() * 1000.0F);
        Map<Integer, RenderTarget> unique = new HashMap<>();
        for (FriendTargetData data : this.friendTargets.values()) {
            var it = data.renderSlots.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, RenderSlot> entry = it.next();
                RenderSlot slot = entry.getValue();
                EntitySnapshot entity = slot.entity;
                if (entity != null && !this.shouldShowTarget(entity)) {
                    continue;
                }
                boolean isCurrentTarget = data.targetEntity != null
                        && data.targetEntity.id() == entry.getKey();
                boolean shouldRender;
                if (isCurrentTarget) {
                    shouldRender = true;
                } else if (data.targetEntity != null) {
                    shouldRender = false;
                } else if (entry.getKey() == data.lastActiveTargetId) {
                    shouldRender = currentTime - slot.lastActiveTime < followTimeMs
                            && entity != null && entity.alive();
                } else {
                    shouldRender = false;
                }
                slot.setDirection(shouldRender);
                slot.advance();
                if (slot.isFinishedBackwards()) {
                    it.remove();
                    continue;
                }
                float animationDelta = slot.getAnimationDelta();
                if (animationDelta <= 0.0F || entity == null) {
                    continue;
                }
                if (selfTargetId != -1 && entity.id() == selfTargetId
                        && "Self".equals(this.renderPriority.getSelected())) {
                    continue;
                }
                RenderTarget existing = unique.get(entity.id());
                if (existing == null || animationDelta > existing.animationDelta) {
                    unique.put(entity.id(), new RenderTarget(entity, animationDelta));
                }
            }
        }
        for (RenderTarget renderData : unique.values()) {
            EntitySnapshot target = renderData.entity;
            float red = Math.max(0.0F, Math.min(1.0F, target.hurtTime() / 10.0F));
            switch (this.targetEspType.getSelected()) {
                case "Circle" -> this.drawCircle(cameraMatrix, target, renderData.animationDelta,
                        red, cameraPitch, cameraYaw, camX, camY, camZ);
                // Ghosts / Crystals need TargetRender's batch renderer: fall back to Legacy.
                default -> this.drawLegacy(cameraMatrix, target, renderData.animationDelta, red,
                        cameraPitch, cameraYaw, camX, camY, camZ);
            }
        }
    }

    private boolean shouldShowTarget(EntitySnapshot target) {
        if (target.friend()) {
            return this.showOn.isSelected("Friends");
        }
        return true;
    }

    /** Port of {@code TargetRenderer.drawLegacy}: a rotating, camera-facing billboard. */
    private void drawLegacy(Matrix4f cameraMatrix, EntitySnapshot target, float animation,
                            float red, float cameraPitch, float cameraYaw,
                            double camX, double camY, double camZ) {
        float size = (2.2F - animation) * this.legacySize.getValue();
        Matrix4f matrix = new Matrix4f(cameraMatrix);
        matrix.rotateX((float) Math.toRadians(cameraPitch));
        matrix.rotateY((float) Math.toRadians(cameraYaw + 180.0F));
        matrix.translate((float) (target.x() - camX),
                (float) (target.y() - camY + target.height() / 2.0),
                (float) (target.z() - camZ));
        matrix.rotateY((float) Math.toRadians(-cameraYaw));
        matrix.rotateX((float) Math.toRadians(cameraPitch));
        matrix.rotateZ((float) Math.toRadians(this.getEspValue()));
        // The original rasterises its 44x44 legacy texture with a separate gradient per corner
        // (22 / 0 / 45 / 67 of the palette). The port keeps that mapping through the per-vertex
        // colour overload instead of picking one flat colour.
        int[] colors = this.getCustomColors();
        Vector4i corners = new Vector4i(
                ColorUtil.multAlpha(gradient(22, red, colors), animation),
                ColorUtil.multAlpha(gradient(0, red, colors), animation),
                ColorUtil.multAlpha(gradient(45, red, colors), animation),
                ColorUtil.multAlpha(gradient(67, red, colors), animation));
        SoupRender.drawTexture(matrix, this.getLegacyTexture(), -size / 2.0F, -size / 2.0F,
                size, size, corners, !this.isSelfLookingAt(target));
    }

    private float getEspValue() {
        return this.espValue;
    }

    /** Port of {@code TargetRenderer.drawCircle}: a ring of quads around the target. */
    private void drawCircle(Matrix4f cameraMatrix, EntitySnapshot target, float animation,
                            float red, float cameraPitch, float cameraYaw,
                            double camX, double camY, double camZ) {
        int segments = 60;
        double radius = Math.max(target.width(), 0.4F) * 1.15;
        double height = target.height();
        double baseX = target.x() - camX;
        double baseY = target.y() - camY;
        double baseZ = target.z() - camZ;
        float step = this.circleStep;
        int[] colors = this.getCustomColors();
        for (int i = 0; i < segments; i++) {
            double a0 = Math.toRadians(i * 360.0 / segments + step);
            double a1 = Math.toRadians((i + 1) * 360.0 / segments + step);
            float x0 = (float) (baseX + Math.cos(a0) * radius);
            float z0 = (float) (baseZ + Math.sin(a0) * radius);
            float x1 = (float) (baseX + Math.cos(a1) * radius);
            float z1 = (float) (baseZ + Math.sin(a1) * radius);
            int top = ColorUtil.multAlpha(gradient(i * 4, red, colors), animation);
            int bottom = ColorUtil.multAlpha(gradient(i * 4, red, colors) & 0x00FFFFFF, animation);
            SoupRender.drawQuad(cameraMatrix,
                    new Vector3f(x0, (float) baseY, z0),
                    new Vector3f(x1, (float) baseY, z1),
                    new Vector3f(x1, (float) (baseY + height), z1),
                    new Vector3f(x0, (float) (baseY + height), z0),
                    ColorUtil.overCol(bottom, top, 0.5F), false);
        }
    }

    /** Original {@code TargetRenderer.getGradientColor}: palette entry or the accent fade. */
    private static int gradient(int index, float red, int[] customColors) {
        if (customColors != null && customColors.length > 0) {
            return ColorUtil.gradientToRed(customColors[index % customColors.length], red);
        }
        return ColorUtil.gradientToRed(ColorUtil.fade(index * 4), red);
    }

    /** The original's {@code depth} flag: not drawn through walls while you look at it. */
    private boolean isSelfLookingAt(EntitySnapshot target) {
        return false;
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

    /**
     * The original module has no colour animation of its own (its marker colour is a static
     * gradient), so the interface method reports a constant "Wave" and
     * {@link #getCustomColors()} stays the only colour input of the renderer.
     */
    @Override
    public SelectSetting getColorAnimation() {
        return this.colorAnimation;
    }

    @Override
    public MultiColorSetting getMultiColorSetting() {
        return this.customColors;
    }

    /**
     * The original's {@code getLegacyTexture()}: nine textures, moved under
     * {@code assets/silkvis/textures/soup/legacy/}. {@code rockfly.png} of the original has no
     * option in this version and is not referenced.
     */
    public Identifier getLegacyTexture() {
        String file = switch (this.legacyTexture.getSelected()) {
            case "Amogus" -> "amongus.png";
            case "Bo" -> "bo.png";
            case "Capture" -> "capture.png";
            case "Jeka" -> "jeka.png";
            case "Marker" -> "marker.png";
            case "Scifi" -> "scifi.png";
            case "Simple" -> "simple.png";
            case "Skull" -> "skull.png";
            case "Vegas" -> "vegas.png";
            default -> null;
        };
        return file == null ? null : Identifier.of("silkvis", LEGACY_TEXTURE_BASE + file);
    }

    public Map<String, FriendTargetData> getFriendTargets() {
        return this.friendTargets;
    }

    /** One friend as the client layer sees it: identity plus the entity its raycast found. */
    public record FriendSnapshot(String key, String name, int targetId) {
    }

    /** Per-friend state of the original ({@code FriendTargetData}). */
    public static final class FriendTargetData {
        EntitySnapshot targetEntity;
        int lastActiveTargetId = -1;
        final Map<Integer, RenderSlot> renderSlots = new HashMap<>();
    }

    /**
     * One animated marker slot (the original's {@code RenderSlot} from
     * {@code api.system.animation}): the animation runs from 0 to {@link #ANIMATION_MS} and back,
     * with the direction flipping on every target change.
     */
    public static final class RenderSlot {
        EntitySnapshot entity;
        final int animationTime;
        int animation;
        boolean forwards;
        long lastActiveTime;

        RenderSlot(int animationTime) {
            this.animationTime = animationTime;
        }

        void setDirection(boolean forwards) {
            this.forwards = forwards;
        }

        void advance() {
            if (this.forwards) {
                this.animation = Math.min(this.animationTime, this.animation + 16);
            } else {
                this.animation = Math.max(0, this.animation - 16);
            }
        }

        boolean isFinishedBackwards() {
            return !this.forwards && this.animation <= 0;
        }

        /** 0..1, matching {@code animation / animationTime} of the original. */
        float getAnimationDelta() {
            return this.animationTime == 0 ? 0.0F : (float) this.animation / this.animationTime;
        }
    }

    private record RenderTarget(EntitySnapshot entity, float animationDelta) {
    }
}

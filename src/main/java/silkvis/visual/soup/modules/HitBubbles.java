package silkvis.visual.soup.modules;

import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4i;
import silkvis.visual.soup.animation.Interpolations;
import silkvis.visual.soup.animation.ThreeStageAnimation;
import silkvis.visual.soup.module.ParticleModule;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.module.SoupModuleCategory;
import silkvis.visual.soup.render.SoupRender;
import silkvis.visual.soup.setting.BooleanSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.MultiColorSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.ValueSetting;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.StopWatch;

import java.util.ArrayList;
import java.util.List;

/**
 * HitBubbles -- bubbles on the point the player hit.
 *
 * <p>Ported from SoupVisuals 1.21.11 {@code padej.soup.implement.features.modules.particles.HitBubbles}
 * (author Padej_, SOUP-1.0, used by written permission). Settings, their order, their
 * names and their defaults are unchanged.</p>
 *
 * <p><b>What was ported:</b> the whole module logic -- attack hook (crit detection via
 * {@code getAttackCooldownProgress > 0.9} and the vanilla crit conditions), the
 * {@link Bubble} record, the three-stage animation, the colour modes (Sync / Custom with
 * 1..4 colours and Wave / Vertexes animation), the bubble styles and the render of one
 * billboarded quad with fade/scale.</p>
 *
 * <p><b>Difficulties:</b> the original gets the hit position from its own {@code EventAttack}
 * (a mixin), which SilkVis does not have (no mixins, ADR-0002). {@link #onAttack} therefore
 * takes the hit position as an argument and the client layer feeds it from the vanilla
 * {@code AttackEntityCallback} plus the vanilla attack path. Bubble rotation is computed the
 * same way as in the original: yaw from the hit vector, negated, pitch from asin(y).</p>
 *
 * <p><b>To verify in game:</b> bubble faces the camera on a hit, rotation speed, "Only crits"
 * filter, and that bubbles do not survive a world change (they are cleared in
 * {@link #deactivate()} and also when the world reference changes).</p>
 */
public class HitBubbles extends SoupModule implements ParticleModule {
    private final List<Bubble> bubbles = new ArrayList<>();
    private boolean wasLastAttackCrit;

    public final SelectSetting colorMode = new SelectSetting(
            "setting.hitbubbles.colormode.name", "setting.hitbubbles.colormode.desc")
            .value("Sync", "Custom")
            .selected("Sync");
    public final SelectSetting customColorsCount = new SelectSetting(
            "setting.hitbubbles.colorcount.name", "setting.hitbubbles.colorcount.desc")
            .value("Solo", "Duo", "Triple", "Quartet")
            .selected("Solo")
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final MultiColorSetting customColors = new MultiColorSetting(
            "setting.hitbubbles.gradientcolors.name", "setting.hitbubbles.gradientcolors.desc")
            .colors("Color 1", "Color 2", "Color 3", "Color 4")
            .defaultColors(-1499549, -13273872, -6596170, -409301)
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final SelectSetting colorAnimation = new SelectSetting(
            "setting.hitbubbles.coloranimation.name", "setting.hitbubbles.coloranimation.desc")
            .value("Wave", "Vertexes")
            .selected("Wave")
            .visible(() -> this.colorMode.isSelected("Custom"));
    private final SelectSetting bubbleTexture = new SelectSetting(
            "setting.hitbubbles.bubblestyle.name", "setting.hitbubbles.bubblestyle.desc")
            .value("Default", "Bold", "Portal", "Soup")
            .selected("Portal");
    public final SelectSetting animationType = new SelectSetting(
            "setting.hitbubbles.animationtype.name", "setting.hitbubbles.animationtype.desc")
            .value("Fade", "Scale", "Both")
            .selected("Both");
    public final ValueSetting appearDuration = new ValueSetting(
            "setting.hitbubbles.appearduration.name", "setting.hitbubbles.appearduration.desc")
            .setValue(0.3F)
            .range(0.1F, 3.0F);
    public final ValueSetting existDuration = new ValueSetting(
            "setting.hitbubbles.existduration.name", "setting.hitbubbles.existduration.desc")
            .setValue(0.5F)
            .range(0.1F, 3.0F);
    public final ValueSetting disappearDuration = new ValueSetting(
            "setting.hitbubbles.disappearduration.name", "setting.hitbubbles.disappearduration.desc")
            .setValue(0.5F)
            .range(0.1F, 3.0F);
    public final SelectSetting appearInterpolation = new SelectSetting(
            "setting.hitbubbles.appearinterpolation.name", "setting.hitbubbles.appearinterpolation.desc")
            .value(Interpolations.getAllNames())
            .selected("Bounce");
    public final SelectSetting disappearInterpolation = new SelectSetting(
            "setting.hitbubbles.disappearinterpolation.name", "setting.hitbubbles.disappearinterpolation.desc")
            .value(Interpolations.getAllNames())
            .selected("Smooth");
    public final ValueSetting rotateSpeed = new ValueSetting(
            "setting.hitbubbles.rotatespeed.name", "setting.hitbubbles.rotatespeed.desc")
            .setValue(2.0F)
            .range(0.5F, 5.0F);
    public final ValueSetting scale = new ValueSetting(
            "setting.hitbubbles.scale.name", "setting.hitbubbles.scale.desc")
            .setValue(0.75F)
            .range(0.5F, 2.0F);
    public final BooleanSetting onlyCrits = new BooleanSetting(
            "setting.hitbubbles.onlycrits.name", "setting.hitbubbles.onlycrits.desc")
            .setValue(false);

    public HitBubbles() {
        super("module.hitbubbles.name", SoupModuleCategory.PARTICLES);
        GroupSetting colorGroup = new GroupSetting("group.hitbubbles.colors.name",
                "group.hitbubbles.colors.desc", false)
                .settings(this.colorMode, this.customColorsCount, this.customColors, this.colorAnimation);
        GroupSetting durationGroup = new GroupSetting("group.hitbubbles.duration.name",
                "group.hitbubbles.duration.desc", false)
                .settings(this.appearDuration, this.existDuration, this.disappearDuration);
        GroupSetting animationGroup = new GroupSetting("group.hitbubbles.animation.name",
                "group.hitbubbles.animation.desc", false)
                .settings(this.appearInterpolation, this.disappearInterpolation, this.rotateSpeed, this.scale);
        this.setup(colorGroup, this.bubbleTexture, this.animationType, durationGroup, animationGroup, this.onlyCrits);
    }

    // ------------------------------------------------------------------ attack hook

    /**
     * Vanilla crit conditions, copied from the original {@code isCrit()}: attack cooldown
     * above 90 %, a fall, not on ground, not climbing, not in water, no blindness effect.
     *
     * @param attackCooldownProgress {@code player.getAttackCooldownProgress(0.5F)}
     * @param fallDistance           {@code player.fallDistance}
     * @param onGround               {@code player.isOnGround()}
     * @param climbing               {@code player.isClimbing()}
     * @param touchingWater          {@code player.isTouchingWater()}
     * @param blindness              {@code player.hasStatusEffect(StatusEffects.BLINDNESS)}
     * @param sprinting              {@code player.isSprinting()}
     * @param hasVehicle             {@code player.hasVehicle()}
     */
    public static boolean isCrit(float attackCooldownProgress, double fallDistance, boolean onGround,
                                 boolean climbing, boolean touchingWater, boolean blindness,
                                 boolean sprinting, boolean hasVehicle) {
        return attackCooldownProgress > 0.9F
                && fallDistance > 0.0
                && !onGround
                && !climbing
                && !touchingWater
                && !blindness
                && !hasVehicle
                && !sprinting;
    }

    /**
     * Pre-attack part of the original: remembers whether the hit that is about to land will
     * be a crit. Called from the vanilla attack path before the hit is applied.
     */
    public void onAttackPre(boolean crit) {
        this.wasLastAttackCrit = crit;
    }

    /**
     * Post-attack part of the original {@code onAttack}: spawns one bubble at the hit point.
     * The view angles are computed the same way ({@code atan2}/{@code asin} of the hit
     * vector), so the quad is oriented towards the player who hit.
     *
     * @param hitX/hitY/hitZ hit point in world space
     * @param eyeX/eyeY/eyeZ the attacker's eye position
     */
    public void onAttack(double hitX, double hitY, double hitZ,
                         double eyeX, double eyeY, double eyeZ) {
        if (!isEnabled()) {
            return;
        }
        if (this.onlyCrits.isValue() && !this.wasLastAttackCrit) {
            return;
        }
        double dx = hitX - eyeX;
        double dy = hitY - eyeY;
        double dz = hitZ - eyeZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.asin(dy)));
        this.bubbles.add(new Bubble(hitX, hitY, hitZ, -yaw, pitch, new StopWatch(), rotationAngle()));
    }

    private float rotationAngle() {
        // The original seeds the spin with the current rotation of the render pass; a
        // per-bubble random start keeps repeating bubbles from looking identical.
        return (float) (Math.random() * 360.0);
    }

    // ------------------------------------------------------------------ tick / render

    /** Drops finished bubbles. Called once per client tick (original: world render). */
    public void tick() {
        if (this.bubbles.isEmpty()) {
            return;
        }
        double totalDuration = getTotalAnimationDuration();
        this.bubbles.removeIf(bubble -> bubble.timer().elapsedTime() / 1000.0 > totalDuration);
        float speed = this.rotateSpeed.getValue();
        for (Bubble bubble : this.bubbles) {
            bubble.rotation += speed;
        }
    }

    /**
     * Draws every live bubble. Called from the world render event with the camera-relative
     * matrix stack; the transform order is the original's
     * (camera pitch, camera yaw, translate to bubble, bubble yaw, bubble pitch, spin).
     */
    public void render(Matrix4f cameraMatrix, float cameraPitch, float cameraYaw,
                       double camX, double camY, double camZ) {
        if (this.bubbles.isEmpty()) {
            return;
        }
        Identifier texture = getBubbleTexture();
        for (Bubble bubble : this.bubbles) {
            double elapsed = bubble.timer().elapsedTime() / 1000.0;
            ThreeStageAnimation animation = createAnimation();
            if (animation.isFinished(elapsed)) {
                continue;
            }
            double value = animation.getValue(elapsed);
            float alpha = calculateAlpha(value);
            float bubbleScale = calculateScale(value);
            Vector4i colors = getBubbleColors(alpha);
            Matrix4f matrix = new Matrix4f(cameraMatrix);
            matrix.rotateX((float) Math.toRadians(cameraPitch));
            matrix.rotateY((float) Math.toRadians(cameraYaw + 180.0F));
            // The 1.21.10 world render event hands out an identity stack in world space, so
            // the camera offset is subtracted here exactly like the original does
            // (targetPos = pos - camera.getPos()).
            matrix.translate((float) (bubble.x() - camX), (float) (bubble.y() - camY),
                    (float) (bubble.z() - camZ));
            matrix.rotateY((float) Math.toRadians(bubble.yaw()));
            matrix.rotateX((float) Math.toRadians(bubble.pitch()));
            matrix.rotateZ((float) Math.toRadians(bubble.rotation() + bubble.spinStart()));
            float size = 2.0F * bubbleScale;
            // The matrix already carries the camera offset, so the quad is emitted around the
            // origin of the translated space; depth=false matches the original.
            SoupRender.drawTexture(matrix, texture, -size / 2.0F, -size / 2.0F, size, size,
                    colors.x, false);
        }
    }

    private ThreeStageAnimation createAnimation() {
        return new ThreeStageAnimation(
                this.appearDuration.getValue(),
                this.existDuration.getValue(),
                this.disappearDuration.getValue(),
                Interpolations.getByName(this.appearInterpolation.getSelected()),
                Interpolations.getByName(this.disappearInterpolation.getSelected()));
    }

    private float calculateAlpha(double animationValue) {
        String animType = this.animationType.getSelected();
        return "Scale".equals(animType) ? 1.0F : (float) Math.max(0.0, Math.min(1.0, animationValue));
    }

    private float calculateScale(double animationValue) {
        String animType = this.animationType.getSelected();
        if ("Fade".equals(animType)) {
            return this.scale.getValue();
        }
        float scaleFactor = (float) Math.max(0.0, Math.min(1.0, animationValue));
        return this.scale.getValue() * scaleFactor;
    }

    /** Four per-vertex colours in the original order (x, y, w, z of its Vector4i). */
    private Vector4i getBubbleColors(float alpha) {
        if (this.colorMode.isSelected("Custom")) {
            int[] colors = getCustomColors();
            if (colors != null && colors.length > 0) {
                String animType = this.colorAnimation.getSelected();
                if ("Wave".equals(animType)) {
                    int color = getWaveColor(colors, alpha);
                    return new Vector4i(color, color, color, color);
                }
                if ("Vertex".equals(animType)) {
                    return getVertexColors(colors, alpha);
                }
            }
        }
        int playerColor = ColorUtil.multAlpha(ColorUtil.getClientColor(), alpha);
        return new Vector4i(playerColor, playerColor, playerColor, playerColor);
    }

    private static int getWaveColor(int[] colors, float alpha) {
        if (colors.length == 1) {
            return ColorUtil.multAlpha(colors[0], alpha);
        } else if (colors.length == 2) {
            int angle = (int) (System.currentTimeMillis() / 8L % 360L);
            angle = angle >= 180 ? 360 - angle : angle;
            return ColorUtil.multAlpha(ColorUtil.overCol(colors[0], colors[1], angle / 180.0F), alpha);
        }
        float timeProgress = (float) (System.currentTimeMillis() / 10L % (colors.length * 360L)) / 360.0F;
        int index1 = (int) Math.floor(timeProgress) % colors.length;
        int index2 = (index1 + 1) % colors.length;
        float lerp = timeProgress - (int) Math.floor(timeProgress);
        return ColorUtil.multAlpha(ColorUtil.overCol(colors[index1], colors[index2], lerp), alpha);
    }

    private static Vector4i getVertexColors(int[] colors, float alpha) {
        if (colors.length == 1) {
            int color = ColorUtil.multAlpha(colors[0], alpha);
            return new Vector4i(color, color, color, color);
        }
        return new Vector4i(
                getVertexGradientColor(0, colors, alpha),
                getVertexGradientColor(90, colors, alpha),
                getVertexGradientColor(180, colors, alpha),
                getVertexGradientColor(270, colors, alpha));
    }

    private static int getVertexGradientColor(int angleOffset, int[] colors, float alpha) {
        if (colors.length == 1) {
            return ColorUtil.multAlpha(colors[0], alpha);
        }
        int angle = (int) ((System.currentTimeMillis() / 8L + angleOffset) % 360L);
        if (colors.length == 2) {
            angle = angle >= 180 ? 360 - angle : angle;
            return ColorUtil.multAlpha(ColorUtil.overCol(colors[0], colors[1], angle / 180.0F), alpha);
        }
        float progress = angle / 360.0F;
        float colorIndex = progress * colors.length;
        int index1 = (int) colorIndex % colors.length;
        int index2 = (index1 + 1) % colors.length;
        float lerp = colorIndex - (int) colorIndex;
        return ColorUtil.multAlpha(ColorUtil.overCol(colors[index1], colors[index2], lerp), alpha);
    }

    public Identifier getBubbleTexture() {
        String path = switch (this.bubbleTexture.getSelected()) {
            case "Default" -> "circles/circle.png";
            case "Bold" -> "circles/circle_bold.png";
            case "Portal" -> "circles/portal.png";
            case "Soup" -> "circles/soup.png";
            default -> "circles/portal.png";
        };
        return Identifier.of("silkvis", "textures/soup/" + path);
    }

    // ------------------------------------------------------------------ interface impl

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

    @Override
    public int[] getCustomColors() {
        if (!this.colorMode.isSelected("Custom")) {
            return null;
        }
        return switch (this.customColorsCount.getSelected()) {
            case "Solo" -> new int[]{this.customColors.getColor1().getColor()};
            case "Duo" -> new int[]{this.customColors.getColor1().getColor(),
                    this.customColors.getColor2().getColor()};
            case "Triple" -> new int[]{this.customColors.getColor1().getColor(),
                    this.customColors.getColor2().getColor(), this.customColors.getColor3().getColor()};
            case "Quartet" -> this.customColors.getColorValues();
            default -> null;
        };
    }

    public double getTotalAnimationDuration() {
        return this.appearDuration.getValue() + this.existDuration.getValue()
                + this.disappearDuration.getValue();
    }

    @Override
    protected void deactivate() {
        this.bubbles.clear();
    }

    public List<Bubble> getBubbles() {
        return this.bubbles;
    }

    public boolean isWasLastAttackCrit() {
        return this.wasLastAttackCrit;
    }

    /**
     * One live bubble. The original keeps a mutable field inside the module; the state that
     * changes every frame (rotation) is mutable here too, everything else is final.
     */
    public static final class Bubble {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final StopWatch timer;
        private final float spinStart;
        private float rotation;

        public Bubble(double x, double y, double z, float yaw, float pitch, StopWatch timer,
                      float spinStart) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.timer = timer;
            this.spinStart = spinStart;
        }

        public double x() {
            return this.x;
        }

        public double y() {
            return this.y;
        }

        public double z() {
            return this.z;
        }

        public float yaw() {
            return this.yaw;
        }

        public float pitch() {
            return this.pitch;
        }

        public StopWatch timer() {
            return this.timer;
        }

        public float spinStart() {
            return this.spinStart;
        }

        public float rotation() {
            return this.rotation;
        }
    }
}

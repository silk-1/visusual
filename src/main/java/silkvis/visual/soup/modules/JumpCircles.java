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
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.MultiColorSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.ValueSetting;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JumpCircles -- a burst of expanding circles under the player on every jump.
 *
 * <p>Ported from SoupVisuals 1.21.11
 * {@code padej.soup.implement.features.modules.visuals.JumpCircles} and its
 * {@code base.util.render.JumpCircleRenderer} (Padej_, SOUP-1.0, used by written permission).
 * Settings, groups and defaults are the original's.</p>
 *
 * <p><b>What was ported:</b> the whole module -- the {@link Circle} state record, the
 * three-stage envelope with the original's negative-"exist" overlap branch, the per-circle
 * rotation that keeps spinning while the circle is visible, the four circle textures
 * (Default / Bold / Portal / Soup), the appear/disappear animations (Fade / Scale / Both) and
 * both colour animations (Wave = one colour for the quad, Vertexes = a gradient across the
 * four corners).</p>
 *
 * <p><b>Difficulties:</b> the original triggers on its own {@code JumpEvent}, which it fires
 * from a mixin on {@code LivingEntity#jump}. SilkVis has no mixins (ADR-0002), so the client
 * layer calls {@link #onJump} from a tick poll that detects the vanilla jump (player leaves
 * the ground with an upwards velocity). The renderer builds the camera-facing quad exactly
 * like the original: yaw/pitch of the camera, translate to the circle with {@code +0.01} on
 * Y, then a 90 degree X rotation and the circle's own Z rotation.</p>
 *
 * <p><b>To verify in game:</b> one circle per jump (with the count from {@code Bright}),
 * the appearing bounce, the rotation direction and speed, that a negative
 * {@code Exist Duration} shortens the circle instead of holding it, and that the four
 * textures line up with the ring of the original.</p>
 */
public class JumpCircles extends SoupModule implements ParticleModule {
    private final List<Circle> circles = new ArrayList<>();

    public final SelectSetting colorMode = new SelectSetting(
            "setting.jumpcircles.colormode.name", "setting.jumpcircles.colormode.desc")
            .value("Sync", "Custom")
            .selected("Sync");
    public final SelectSetting customColorsCount = new SelectSetting(
            "setting.jumpcircles.colorcount.name", "setting.jumpcircles.colorcount.desc")
            .value("Solo", "Duo", "Triple", "Quartet")
            .selected("Solo")
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final MultiColorSetting customColors = new MultiColorSetting(
            "setting.jumpcircles.gradientcolors.name", "setting.jumpcircles.gradientcolors.desc")
            .colors("Color 1", "Color 2", "Color 3", "Color 4")
            .defaultColors(-1499549, -13273872, -6596170, -409301)
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final SelectSetting colorAnimation = new SelectSetting(
            "setting.jumpcircles.coloranimation.name", "setting.jumpcircles.coloranimation.desc")
            .value("Wave", "Vertexes")
            .selected("Wave")
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final SelectSetting circleTexture = new SelectSetting(
            "setting.jumpcircles.circlestyle.name", "setting.jumpcircles.circlestyle.desc")
            .value("Default", "Bold", "Portal", "Soup")
            .selected("Default");
    public final SelectSetting appearAnimationType = new SelectSetting(
            "setting.jumpcircles.appearanimationtype.name", "setting.jumpcircles.appearanimationtype.desc")
            .value("Fade", "Scale", "Both")
            .selected("Both");
    public final SelectSetting disappearAnimationType = new SelectSetting(
            "setting.jumpcircles.disappearanimationtype.name", "setting.jumpcircles.disappearanimationtype.desc")
            .value("Fade", "Scale", "Both")
            .selected("Both");
    public final ValueSetting appearDuration = new ValueSetting(
            "setting.jumpcircles.appearduration.name", "setting.jumpcircles.appearduration.desc")
            .setValue(1.0F)
            .range(0.1F, 3.0F);
    public final ValueSetting existDuration = new ValueSetting(
            "setting.jumpcircles.existduration.name", "setting.jumpcircles.existduration.desc")
            .setValue(1.5F)
            .range(-3.0F, 3.0F);
    public final ValueSetting disappearDuration = new ValueSetting(
            "setting.jumpcircles.disappearduration.name", "setting.jumpcircles.disappearduration.desc")
            .setValue(1.0F)
            .range(0.1F, 3.0F);
    public final SelectSetting appearInterpolation = new SelectSetting(
            "setting.jumpcircles.appearinterpolation.name", "setting.jumpcircles.appearinterpolation.desc")
            .value(Interpolations.getAllNames())
            .selected("Bounce");
    public final SelectSetting disappearInterpolation = new SelectSetting(
            "setting.jumpcircles.disappearinterpolation.name", "setting.jumpcircles.disappearinterpolation.desc")
            .value(Interpolations.getAllNames())
            .selected("Smooth");
    public final ValueSetting rotateSpeed = new ValueSetting(
            "setting.jumpcircles.rotatespeed.name", "setting.jumpcircles.rotatespeed.desc")
            .setValue(2.0F)
            .range(0.5F, 5.0F);
    public final ValueSetting circleScale = new ValueSetting(
            "setting.jumpcircles.circlescale.name", "setting.jumpcircles.circlescale.desc")
            .setValue(1.0F)
            .range(0.5F, 3.0F);
    public final ValueSetting bright = new ValueSetting(
            "setting.jumpcircles.bright.name", "setting.jumpcircles.bright.desc")
            .setValue(1.0F)
            .range(1, 5);

    public JumpCircles() {
        super("module.jumpcircles.name", SoupModuleCategory.VISUALS);
        GroupSetting colorGroup = new GroupSetting("group.jumpcircles.colors.name",
                "group.jumpcircles.colors.desc", false)
                .settings(this.colorMode, this.customColorsCount, this.customColors, this.colorAnimation);
        GroupSetting appearanceGroup = new GroupSetting("group.jumpcircles.appearance.name",
                "group.jumpcircles.appearance.desc", false)
                .settings(this.circleTexture, this.appearAnimationType, this.disappearAnimationType);
        GroupSetting durationGroup = new GroupSetting("group.jumpcircles.duration.name",
                "group.jumpcircles.duration.desc", false)
                .settings(this.appearDuration, this.existDuration, this.disappearDuration);
        GroupSetting animationGroup = new GroupSetting("group.jumpcircles.animation.name",
                "group.jumpcircles.animation.desc", false)
                .settings(this.appearInterpolation, this.disappearInterpolation, this.rotateSpeed,
                        this.circleScale, this.bright);
        this.setup(colorGroup, appearanceGroup, durationGroup, animationGroup);
    }

    @Override
    protected void deactivate() {
        this.circles.clear();
    }

    /**
     * Original {@code JumpEvent} handler. {@code Bright} doubles as the number of circles
     * spawned per jump, exactly like the original; every circle of one jump shares the random
     * start angle and angular velocity, so they rotate together.
     *
     * @param x/y/z feet position of the player that jumped
     */
    public void onJump(double x, double y, double z) {
        if (!this.isEnabled()) {
            return;
        }
        int circleCount = (int) this.bright.getValue();
        float sharedRotationAngle = (float) (ThreadLocalRandom.current().nextDouble() * 360.0);
        float sharedAngularVelocity = (float) (ThreadLocalRandom.current().nextDouble() * 2.0 + 1.0);
        for (int i = 0; i < circleCount; i++) {
            this.circles.add(new Circle(x, y, z, new StopWatch(), sharedRotationAngle,
                    sharedAngularVelocity));
        }
    }

    /** Per frame: ages the circles out, advances their rotation and queues their quads. */
    public void render(Matrix4f cameraMatrix, float cameraPitch, float cameraYaw,
                       double camX, double camY, double camZ) {
        if (this.circles.isEmpty()) {
            return;
        }
        double totalDuration = this.getTotalAnimationDuration();
        this.circles.removeIf(circle -> circle.timer.elapsedTime() / 1000.0 > totalDuration);
        if (this.circles.isEmpty()) {
            return;
        }
        Identifier texture = this.getCircleTexture();
        if (texture == null) {
            return;
        }
        for (Circle circle : this.circles) {
            circle.updateRotation(this);
            circle.updateAnimation(this);
            this.renderCircle(cameraMatrix, circle, texture, cameraPitch, cameraYaw,
                    camX, camY, camZ);
        }
    }

    /** Port of {@code JumpCircleRenderer.renderCircle}. */
    private void renderCircle(Matrix4f cameraMatrix, Circle circle, Identifier texture,
                              float cameraPitch, float cameraYaw,
                              double camX, double camY, double camZ) {
        ThreeStageAnimation animation = circle.animation;
        if (animation == null) {
            return;
        }
        double elapsedSeconds = circle.timer.elapsedTime() / 1000.0;
        if (animation.isFinished(elapsedSeconds)) {
            return;
        }
        ThreeStageAnimation.AnimationStage stage = animation.getStage(elapsedSeconds);
        double animationValue = animation.getValue(elapsedSeconds);
        boolean isOverlapping = animation.getExistDuration() < 0.0;
        double overlapStart = animation.getAppearDuration() + animation.getExistDuration();
        boolean inOverlapZone = isOverlapping && elapsedSeconds > overlapStart
                && elapsedSeconds <= animation.getAppearDuration();
        float alpha;
        float scale;
        if (inOverlapZone) {
            alpha = calculateOverlappingAlpha(elapsedSeconds, animation);
            scale = calculateOverlappingScale(elapsedSeconds, animation);
        } else {
            alpha = calculateAlpha(animationValue, stage);
            scale = calculateScale(animationValue, stage);
        }

        Vector4i colors = this.getCircleColors(alpha);
        Matrix4f matrix = new Matrix4f(cameraMatrix);
        matrix.rotateX((float) Math.toRadians(cameraPitch));
        matrix.rotateY((float) Math.toRadians(cameraYaw + 180.0F));
        matrix.translate((float) (circle.x - camX), (float) (circle.y - camY) + 0.01F,
                (float) (circle.z - camZ));
        matrix.rotateX((float) Math.toRadians(90.0F));
        matrix.rotateZ((float) Math.toRadians(circle.rotationAngle));
        float size = 2.0F * scale;
        // depth=true like the original: circles are occluded by blocks.
        SoupRender.drawTexture(matrix, texture, -size / 2.0F, -size / 2.0F, size, size,
                colors, true);
    }

    private Vector4i getCircleColors(float alpha) {
        if (this.colorMode.isSelected("Custom")) {
            int[] colors = this.getCustomColors();
            if (colors != null && colors.length > 0) {
                String animType = this.colorAnimation.getSelected();
                if ("Wave".equals(animType)) {
                    int color = getWaveColor(colors, alpha);
                    return new Vector4i(color, color, color, color);
                }
                if ("Vertexes".equals(animType)) {
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
        }
        if (colors.length == 2) {
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

    private float calculateAlpha(double animationValue, ThreeStageAnimation.AnimationStage stage) {
        String animType;
        if (stage == ThreeStageAnimation.AnimationStage.APPEAR) {
            animType = this.appearAnimationType.getSelected();
        } else {
            if (stage != ThreeStageAnimation.AnimationStage.DISAPPEAR) {
                return 1.0F;
            }
            animType = this.disappearAnimationType.getSelected();
        }
        return "Scale".equals(animType) ? 1.0F : (float) Math.max(0.0, Math.min(1.0, animationValue));
    }

    private float calculateScale(double animationValue, ThreeStageAnimation.AnimationStage stage) {
        String animType;
        if (stage == ThreeStageAnimation.AnimationStage.APPEAR) {
            animType = this.appearAnimationType.getSelected();
        } else {
            if (stage != ThreeStageAnimation.AnimationStage.DISAPPEAR) {
                return this.circleScale.getValue();
            }
            animType = this.disappearAnimationType.getSelected();
        }
        if ("Fade".equals(animType)) {
            return this.circleScale.getValue();
        }
        float scaleFactor = (float) Math.max(0.0, Math.min(1.0, animationValue));
        return this.circleScale.getValue() * scaleFactor;
    }

    private float calculateOverlappingAlpha(double elapsedSeconds, ThreeStageAnimation animation) {
        String appearType = this.appearAnimationType.getSelected();
        String disappearType = this.disappearAnimationType.getSelected();
        double overlapStart = animation.getAppearDuration() + animation.getExistDuration();
        double appearProgress = elapsedSeconds / animation.getAppearDuration();
        double disappearProgress = (elapsedSeconds - overlapStart) / animation.getDisappearDuration();
        boolean bothUseFade = !"Scale".equals(appearType) && !"Scale".equals(disappearType);
        if (bothUseFade) {
            return (float) Math.max(0.0, Math.min(1.0, appearProgress));
        }
        float appearAlpha = 1.0F;
        float disappearAlpha = 1.0F;
        if (!"Scale".equals(appearType)) {
            appearAlpha = (float) Math.max(0.0, Math.min(1.0, appearProgress));
        }
        if (!"Scale".equals(disappearType)) {
            disappearAlpha = (float) Math.max(0.0, Math.min(1.0, 1.0 - disappearProgress));
        }
        return Math.min(appearAlpha, disappearAlpha);
    }

    private float calculateOverlappingScale(double elapsedSeconds, ThreeStageAnimation animation) {
        String appearType = this.appearAnimationType.getSelected();
        String disappearType = this.disappearAnimationType.getSelected();
        double overlapStart = animation.getAppearDuration() + animation.getExistDuration();
        double appearProgress = elapsedSeconds / animation.getAppearDuration();
        double disappearProgress = (elapsedSeconds - overlapStart) / animation.getDisappearDuration();
        boolean bothUseScale = !"Fade".equals(appearType) && !"Fade".equals(disappearType);
        if (bothUseScale) {
            float scaleFactor = (float) Math.max(0.0, Math.min(1.0, appearProgress));
            return this.circleScale.getValue() * scaleFactor;
        }
        float appearScale = this.circleScale.getValue();
        float disappearScale = this.circleScale.getValue();
        if (!"Fade".equals(appearType)) {
            float scaleFactor = (float) Math.max(0.0, Math.min(1.0, appearProgress));
            appearScale = this.circleScale.getValue() * scaleFactor;
        }
        if (!"Fade".equals(disappearType)) {
            float scaleFactor = (float) Math.max(0.0, Math.min(1.0, 1.0 - disappearProgress));
            disappearScale = this.circleScale.getValue() * scaleFactor;
        }
        return Math.min(appearScale, disappearScale);
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

    public double getTotalAnimationDuration() {
        return this.appearDuration.getValue() + this.existDuration.getValue()
                + this.disappearDuration.getValue();
    }

    public ThreeStageAnimation createAnimation() {
        return new ThreeStageAnimation(
                this.appearDuration.getValue(),
                this.existDuration.getValue(),
                this.disappearDuration.getValue(),
                Interpolations.getByName(this.appearInterpolation.getSelected()),
                Interpolations.getByName(this.disappearInterpolation.getSelected()));
    }

    /** The four ring textures, moved under {@code assets/silkvis/textures/soup/circles/}. */
    public Identifier getCircleTexture() {
        return switch (this.circleTexture.getSelected()) {
            case "Default" -> Identifier.of("silkvis", "textures/soup/circles/circle.png");
            case "Bold" -> Identifier.of("silkvis", "textures/soup/circles/circle_bold.png");
            case "Portal" -> Identifier.of("silkvis", "textures/soup/circles/portal.png");
            case "Soup" -> Identifier.of("silkvis", "textures/soup/circles/soup.png");
            default -> null;
        };
    }

    public List<Circle> getCircles() {
        return this.circles;
    }

    /**
     * One circle of one jump. The original's fields are kept one to one, including the
     * {@code lastUpdateTime} that clamps a frame delta to 0.1 s so a lag spike does not spin
     * the circle several turns at once.
     */
    public static final class Circle {
        final double x;
        final double y;
        final double z;
        final StopWatch timer;
        float rotationAngle;
        final float angularVelocity;
        long lastUpdateTime;
        ThreeStageAnimation animation;

        Circle(double x, double y, double z, StopWatch timer, float initialRotation,
               float angularVelocity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timer = timer;
            this.rotationAngle = initialRotation;
            this.angularVelocity = angularVelocity;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        void updateAnimation(JumpCircles module) {
            if (this.animation == null) {
                this.animation = module.createAnimation();
            }
        }

        /**
         * Port of {@code JumpCircles$Circle#updateRotation}. The original's switch has three
         * cases: APPEAR and EXIST spin at full speed, DISAPPEAR slows down with the fade
         * factor, FINISHED does not spin. (The original has a fall-through from the DISAPPEAR
         * case into FINISHED; since the FINISHED branch is empty, falling through changes
         * nothing, so it is written as an explicit break here.)
         */
        void updateRotation(JumpCircles module) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (float) (currentTime - this.lastUpdateTime) / 1000.0F;
            this.lastUpdateTime = currentTime;
            deltaTime = Math.min(deltaTime, 0.1F);
            double elapsedSeconds = this.timer.elapsedTime() / 1000.0;
            if (this.animation == null) {
                return;
            }
            ThreeStageAnimation.AnimationStage stage = this.animation.getStage(elapsedSeconds);
            float frameTime = 0.016666668F;
            float normalizedDelta = deltaTime / frameTime;
            switch (stage) {
                case APPEAR, EXIST ->
                        this.rotationAngle = this.rotationAngle
                                - this.angularVelocity * normalizedDelta * module.rotateSpeed.getValue();
                case DISAPPEAR -> {
                    double disappearProgress = (elapsedSeconds - this.animation.getAppearDuration()
                            - this.animation.getExistDuration()) / this.animation.getDisappearDuration();
                    float fadeFactor = (float) (1.0 - disappearProgress);
                    this.rotationAngle = this.rotationAngle
                            - this.angularVelocity * normalizedDelta * fadeFactor * module.rotateSpeed.getValue();
                }
                case FINISHED -> {
                }
            }
        }
    }
}

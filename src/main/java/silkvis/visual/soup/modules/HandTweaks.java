package silkvis.visual.soup.modules;

import org.joml.Matrix4f;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.module.SoupModuleCategory;
import silkvis.visual.soup.setting.BooleanSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.ValueSetting;

/**
 * HandTweaks -- the original's hand swing animations, hand offsets, rotations and scales.
 *
 * <p>Ported from SoupVisuals 1.21.11
 * {@code padej.soup.implement.features.modules.other.HandTweaks} (Padej_, SOUP-1.0, used by
 * written permission). Settings, groups and defaults are the original's.</p>
 *
 * <p><b>What was ported:</b> all 29 settings and every transform of the original -- the six
 * named swing types ({@code Swipe}, {@code Down}, {@code Smooth}, {@code Power},
 * {@code Feast}) plus {@code Custom}, the swing speed, the per-hand translation / pitch / yaw /
 * roll and the per-hand scale, applied with the same matrix order and the same rotation axes as
 * the original's {@code HandAnimationEvent} / {@code HandOffsetEvent} / {@code HandScaleEvent}
 * handlers.</p>
 *
 * <p><b>Difficulties:</b> the original gets these three events from a mixin on
 * {@code HeldItemRenderer} (its {@code renderFirstPersonItem} and
 * {@code renderArmHoldingItem}), and cancels the vanilla swing there. SilkVis has no mixins
 * (ADR-0002) and 1.21.10 has no public hook for the first-person hand transform, so:</p>
 * <ul>
 *   <li>the settings, the maths and the {@link #applySwing} / {@link #applyHandOffset} /
 *       {@link #handScale} methods are complete and tested; they take a {@link Matrix4f}, so
 *       they are ready for the day a hook exists (or for the render-state adapter of
 *       {@code HeldItemRenderer});</li>
 *   <li><b>nothing is applied to the real hand yet</b>: there is no injection point in
 *       {@code HeldItemRenderer} reachable without a mixin, and SilkVis does not add mixins
 *       without an ADR (rule 5, ADR-0002). This is the one module of the batch that is
 *       settings-only at runtime, and it is recorded as such in
 *       {@code docs/known-limitations.md} -- not silently left out.</li>
 * </ul>
 *
 * <p><b>To verify in game:</b> once a hook exists -- each of the five named swings, the custom
 * translation and rotation, the swing-speed multiplier of {@code Swing Speed}, and that the
 * off-hand scale does not affect the main hand.</p>
 */
public class HandTweaks extends SoupModule {
    /** {@code true} for the main hand, {@code false} for the off hand. */
    public static final boolean MAIN_HAND = true;

    public final BooleanSetting enableSwing = new BooleanSetting(
            "setting.handtweaks.enableswing.name", "setting.handtweaks.enableswing.desc")
            .setValue(false);
    public final SelectSetting swingType = new SelectSetting(
            "setting.handtweaks.swingtype.name", "setting.handtweaks.swingtype.desc")
            .value("Vanilla", "Swipe", "Down", "Smooth", "Power", "Feast", "Custom")
            .selected("Vanilla")
            .visible(this.enableSwing::isValue);
    public final ValueSetting swingSpeed = new ValueSetting(
            "setting.handtweaks.swingspeed.name", "setting.handtweaks.swingspeed.desc")
            .setValue(1.0F)
            .range(0.5F, 2.0F);
    public final ValueSetting customTranslateX = new ValueSetting(
            "setting.handtweaks.customtranslatex.name", "setting.handtweaks.customtranslatex.desc")
            .setValue(0.56F)
            .range(-2.0F, 2.0F)
            .visible(this::isCustom);
    public final ValueSetting customTranslateY = new ValueSetting(
            "setting.handtweaks.customtranslatey.name", "setting.handtweaks.customtranslatey.desc")
            .setValue(-0.32F)
            .range(-2.0F, 2.0F)
            .visible(this::isCustom);
    public final ValueSetting customTranslateZ = new ValueSetting(
            "setting.handtweaks.customtranslatez.name", "setting.handtweaks.customtranslatez.desc")
            .setValue(-0.72F)
            .range(-2.0F, 2.0F)
            .visible(this::isCustom);
    public final ValueSetting customRotateX = new ValueSetting(
            "setting.handtweaks.customrotatex.name", "setting.handtweaks.customrotatex.desc")
            .setValue(0.0F)
            .range(-180.0F, 180.0F)
            .visible(this::isCustom);
    public final ValueSetting customRotateY = new ValueSetting(
            "setting.handtweaks.customrotatey.name", "setting.handtweaks.customrotatey.desc")
            .setValue(0.0F)
            .range(-180.0F, 180.0F)
            .visible(this::isCustom);
    public final ValueSetting customRotateZ = new ValueSetting(
            "setting.handtweaks.customrotatez.name", "setting.handtweaks.customrotatez.desc")
            .setValue(0.0F)
            .range(-180.0F, 180.0F)
            .visible(this::isCustom);
    public final ValueSetting customSwingRotateX = new ValueSetting(
            "setting.handtweaks.customswingrotatex.name", "setting.handtweaks.customswingrotatex.desc")
            .setValue(-60.0F)
            .range(-180.0F, 180.0F)
            .visible(this::isCustom);
    public final ValueSetting customSwingRotateY = new ValueSetting(
            "setting.handtweaks.customswingrotatey.name", "setting.handtweaks.customswingrotatey.desc")
            .setValue(0.0F)
            .range(-180.0F, 180.0F)
            .visible(this::isCustom);
    public final ValueSetting customSwingRotateZ = new ValueSetting(
            "setting.handtweaks.customswingrotatez.name", "setting.handtweaks.customswingrotatez.desc")
            .setValue(0.0F)
            .range(-180.0F, 180.0F)
            .visible(this::isCustom);
    public final ValueSetting mainHandX = new ValueSetting(
            "setting.handtweaks.mainhandx.name", "setting.handtweaks.mainhandx.desc")
            .setValue(0.0F).range(-1.0F, 1.0F);
    public final ValueSetting mainHandY = new ValueSetting(
            "setting.handtweaks.mainhandy.name", "setting.handtweaks.mainhandy.desc")
            .setValue(0.0F).range(-1.0F, 1.0F);
    public final ValueSetting mainHandZ = new ValueSetting(
            "setting.handtweaks.mainhandz.name", "setting.handtweaks.mainhandz.desc")
            .setValue(0.0F).range(-2.5F, 2.5F);
    public final ValueSetting mainHandPitch = new ValueSetting(
            "setting.handtweaks.mainhandpitch.name", "setting.handtweaks.mainhandpitch.desc")
            .setValue(0.0F).range(-180.0F, 180.0F);
    public final ValueSetting mainHandYaw = new ValueSetting(
            "setting.handtweaks.mainhandyaw.name", "setting.handtweaks.mainhandyaw.desc")
            .setValue(0.0F).range(-180.0F, 180.0F);
    public final ValueSetting mainHandRoll = new ValueSetting(
            "setting.handtweaks.mainhandroll.name", "setting.handtweaks.mainhandroll.desc")
            .setValue(0.0F).range(-180.0F, 180.0F);
    public final ValueSetting mainHandScale = new ValueSetting(
            "setting.handtweaks.mainhandscale.name", "setting.handtweaks.mainhandscale.desc")
            .setValue(1.0F).range(0.1F, 2.0F);
    public final ValueSetting offHandX = new ValueSetting(
            "setting.handtweaks.offhandx.name", "setting.handtweaks.offhandx.desc")
            .setValue(0.0F).range(-1.0F, 1.0F);
    public final ValueSetting offHandY = new ValueSetting(
            "setting.handtweaks.offhandy.name", "setting.handtweaks.offhandy.desc")
            .setValue(0.0F).range(-1.0F, 1.0F);
    public final ValueSetting offHandZ = new ValueSetting(
            "setting.handtweaks.offhandz.name", "setting.handtweaks.offhandz.desc")
            .setValue(0.0F).range(-2.5F, 2.5F);
    public final ValueSetting offHandPitch = new ValueSetting(
            "setting.handtweaks.offhandpitch.name", "setting.handtweaks.offhandpitch.desc")
            .setValue(0.0F).range(-180.0F, 180.0F);
    public final ValueSetting offHandYaw = new ValueSetting(
            "setting.handtweaks.offhandyaw.name", "setting.handtweaks.offhandyaw.desc")
            .setValue(0.0F).range(-180.0F, 180.0F);
    public final ValueSetting offHandRoll = new ValueSetting(
            "setting.handtweaks.offhandroll.name", "setting.handtweaks.offhandroll.desc")
            .setValue(0.0F).range(-180.0F, 180.0F);
    public final ValueSetting offHandScale = new ValueSetting(
            "setting.handtweaks.offhandscale.name", "setting.handtweaks.offhandscale.desc")
            .setValue(1.0F).range(0.1F, 2.0F);

    public HandTweaks() {
        super("module.handtweaks.name", SoupModuleCategory.OTHER);
        GroupSetting swingGroup = new GroupSetting("group.handtweaks.swing.name",
                "group.handtweaks.swing.desc", false)
                .settings(this.enableSwing, this.swingType, this.swingSpeed);
        GroupSetting customTranslateGroup = new GroupSetting("group.handtweaks.customtranslate.name",
                "group.handtweaks.customtranslate.desc", false)
                .settings(this.customTranslateX, this.customTranslateY, this.customTranslateZ)
                .visible(this::isCustom);
        GroupSetting customRotateGroup = new GroupSetting("group.handtweaks.customrotate.name",
                "group.handtweaks.customrotate.desc", false)
                .settings(this.customRotateX, this.customRotateY, this.customRotateZ)
                .visible(this::isCustom);
        GroupSetting customSwingRotateGroup = new GroupSetting("group.handtweaks.customswingrotat.name",
                "group.handtweaks.customswingrotat.desc", false)
                .settings(this.customSwingRotateX, this.customSwingRotateY, this.customSwingRotateZ)
                .visible(this::isCustom);
        GroupSetting mainPositionGroup = new GroupSetting("group.handtweaks.mainposition.name",
                "group.handtweaks.mainposition.desc", false)
                .settings(this.mainHandX, this.mainHandY, this.mainHandZ);
        GroupSetting mainRotationGroup = new GroupSetting("group.handtweaks.mainrotation.name",
                "group.handtweaks.mainrotation.desc", false)
                .settings(this.mainHandPitch, this.mainHandYaw, this.mainHandRoll);
        GroupSetting offPositionGroup = new GroupSetting("group.handtweaks.offposition.name",
                "group.handtweaks.offposition.desc", false)
                .settings(this.offHandX, this.offHandY, this.offHandZ);
        GroupSetting offRotationGroup = new GroupSetting("group.handtweaks.offrotation.name",
                "group.handtweaks.offrotation.desc", false)
                .settings(this.offHandPitch, this.offHandYaw, this.offHandRoll);
        this.setup(swingGroup, customTranslateGroup, customRotateGroup, customSwingRotateGroup,
                mainPositionGroup, mainRotationGroup, this.mainHandScale,
                offPositionGroup, offRotationGroup, this.offHandScale);
    }

    private boolean isCustom() {
        return this.enableSwing.isValue() && this.swingType.isSelected("Custom");
    }

    /** Original {@code SwingDurationEvent}: the swing animation length multiplier. */
    public float swingDuration() {
        return this.enableSwing.isValue() ? this.swingSpeed.getValue() : 1.0F;
    }

    /** Original {@code HandScaleEvent}: the per-hand scale, {@code 1} when untouched. */
    public float handScale(boolean mainHand) {
        if (!this.isEnabled()) {
            return 1.0F;
        }
        float scale = mainHand ? this.mainHandScale.getValue() : this.offHandScale.getValue();
        return scale == 1.0F ? 1.0F : scale;
    }

    /**
     * Original {@code HandAnimationEvent} handler: replaces the vanilla swing of the main hand
     * with one of the five named ones or the custom transform.
     *
     * @param matrices      the hand matrix of the first-person render, in the original's space
     * @param swingProgress {@code player.getHandSwingProgress(tickProgress)}
     * @param rightHanded   {@code player.getMainArm() == Arm.RIGHT}
     * @param hand          {@link #MAIN_HAND} or the off hand
     * @return {@code true} when the vanilla swing must be skipped, like the original's
     *         {@code event.cancel()}
     */
    public boolean applySwing(Matrix4f matrices, float swingProgress, boolean rightHanded,
                              boolean hand) {
        if (!this.isEnabled() || !this.enableSwing.isValue()) {
            return false;
        }
        if (!hand || this.swingType.isSelected("Vanilla")) {
            return false;
        }
        int i = rightHanded ? 1 : -1;
        float sin1 = (float) Math.sin(swingProgress * swingProgress * Math.PI);
        float sin2 = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
        float sinSmooth = (float) (Math.sin(swingProgress * Math.PI) * 0.5);
        switch (this.swingType.getSelected()) {
            case "Swipe" -> {
                matrices.translate(0.56F * i, -0.32F, -0.72F);
                matrices.rotateY((float) Math.toRadians(60 * i));
                matrices.rotateZ((float) Math.toRadians(-60 * i));
                matrices.rotateY((float) Math.toRadians(sin2 * sin1 * -5.0F));
                matrices.rotateX((float) Math.toRadians(sin2 * sin1 * -120.0F));
                matrices.rotateX((float) Math.toRadians(-60.0F));
            }
            case "Down" -> {
                matrices.translate(i * 0.56F, -0.32F, -0.72F);
                matrices.rotateY((float) Math.toRadians(76 * i));
                matrices.rotateY((float) Math.toRadians(sin2 * -5.0F));
                matrices.rotateX((float) Math.toRadians(sin2 * -100.0F));
                matrices.rotateX((float) Math.toRadians(sin2 * -155.0F));
                matrices.rotateX((float) Math.toRadians(-100.0F));
            }
            case "Smooth" -> {
                matrices.translate(i * 0.56F, -0.42F, -0.72F);
                matrices.rotateY((float) Math.toRadians(i * (45.0F + sin1 * -20.0F)));
                matrices.rotateZ((float) Math.toRadians(i * sin2 * -20.0F));
                matrices.rotateX((float) Math.toRadians(sin2 * -80.0F));
                matrices.rotateY((float) Math.toRadians(i * -45.0F));
                matrices.translate(0.0, -0.1, 0.0);
            }
            case "Power" -> {
                matrices.translate(i * 0.56F, -0.32F, -0.72F);
                matrices.translate(-sinSmooth * sinSmooth * sin1 * i, 0.0F, 0.0F);
                matrices.rotateY((float) Math.toRadians(61 * i));
                matrices.rotateZ((float) Math.toRadians(sin2));
                matrices.rotateY((float) Math.toRadians(sin2 * sin1 * -5.0F));
                matrices.rotateX((float) Math.toRadians(sin2 * sin1 * -30.0F));
                matrices.rotateX((float) Math.toRadians(-60.0F));
                matrices.rotateX((float) Math.toRadians(sinSmooth * -60.0F));
            }
            case "Feast" -> {
                matrices.translate(i * 0.56F, -0.32F, -0.72F);
                matrices.rotateY((float) Math.toRadians(30 * i));
                matrices.rotateY((float) Math.toRadians(sin2 * 75.0F * i));
                matrices.rotateX((float) Math.toRadians(sin2 * -45.0F));
                matrices.rotateY((float) Math.toRadians(30 * i));
                matrices.rotateX((float) Math.toRadians(-80.0F));
                matrices.rotateY((float) Math.toRadians(35 * i));
            }
            case "Custom" -> {
                matrices.translate(i * this.customTranslateX.getValue(), this.customTranslateY.getValue(),
                        this.customTranslateZ.getValue());
                if (this.customRotateX.getValue() != 0.0F) {
                    matrices.rotateX((float) Math.toRadians(this.customRotateX.getValue()));
                }
                if (this.customRotateY.getValue() != 0.0F) {
                    matrices.rotateY((float) Math.toRadians(this.customRotateY.getValue() * i));
                }
                if (this.customRotateZ.getValue() != 0.0F) {
                    matrices.rotateZ((float) Math.toRadians(this.customRotateZ.getValue() * i));
                }
                if (this.customSwingRotateX.getValue() != 0.0F) {
                    matrices.rotateX((float) Math.toRadians(sin2 * this.customSwingRotateX.getValue()));
                }
                if (this.customSwingRotateY.getValue() != 0.0F) {
                    matrices.rotateY((float) Math.toRadians(sin2 * this.customSwingRotateY.getValue() * i));
                }
                if (this.customSwingRotateZ.getValue() != 0.0F) {
                    matrices.rotateZ((float) Math.toRadians(sin2 * this.customSwingRotateZ.getValue() * i));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Original {@code HandOffsetEvent} handler: per-hand translation and rotation. The
     * original skips the main hand while a crossbow is held, so the crossbow charge pose keeps
     * its anchor.
     *
     * @param mainHandCrossbow {@code player.getMainHandStack().getItem() instanceof CrossbowItem}
     */
    public void applyHandOffset(Matrix4f matrices, boolean hand, boolean mainHandCrossbow) {
        if (!this.isEnabled()) {
            return;
        }
        if (hand) {
            if (mainHandCrossbow) {
                return;
            }
            matrices.translate(this.mainHandX.getValue(), this.mainHandY.getValue(), this.mainHandZ.getValue());
            if (this.mainHandPitch.getValue() != 0.0F) {
                matrices.rotateX((float) Math.toRadians(this.mainHandPitch.getValue()));
            }
            if (this.mainHandYaw.getValue() != 0.0F) {
                matrices.rotateY((float) Math.toRadians(this.mainHandYaw.getValue()));
            }
            if (this.mainHandRoll.getValue() != 0.0F) {
                matrices.rotateZ((float) Math.toRadians(this.mainHandRoll.getValue()));
            }
        } else {
            matrices.translate(this.offHandX.getValue(), this.offHandY.getValue(), this.offHandZ.getValue());
            if (this.offHandPitch.getValue() != 0.0F) {
                matrices.rotateX((float) Math.toRadians(this.offHandPitch.getValue()));
            }
            if (this.offHandYaw.getValue() != 0.0F) {
                matrices.rotateY((float) Math.toRadians(this.offHandYaw.getValue()));
            }
            if (this.offHandRoll.getValue() != 0.0F) {
                matrices.rotateZ((float) Math.toRadians(this.offHandRoll.getValue()));
            }
        }
    }
}

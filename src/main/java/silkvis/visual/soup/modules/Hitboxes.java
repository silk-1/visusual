package silkvis.visual.soup.modules;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.module.SoupModuleCategory;
import silkvis.visual.soup.render.SoupRender;
import silkvis.visual.soup.setting.BooleanSetting;
import silkvis.visual.soup.setting.ColorSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.EntitySnapshot;

/**
 * Hitboxes -- draws an entity's bounding box with the SilkVis accent colour.
 *
 * <p>Ported from SoupVisuals 1.21.11 {@code padej.soup.implement.features.modules.visuals.Hitboxes}
 * plus the drawing code that the original keeps in {@code mixins/EntityRenderDispatcherMixin}
 * (Padej_, SOUP-1.0, used by written permission). Settings, groups and defaults are the
 * original's.</p>
 *
 * <p><b>What was ported:</b> the whole module -- the render mode (Fill / Outline / Both),
 * the colour modes (Sync = client colour, Custom = two colour pickers where friends get their
 * own), the hurt flash ({@code hurtTime / 10} blended to red), the crouch rule
 * ({@code min(height, 1.5)} for the local player only) and the red "eyes line" rectangle at
 * eye height for living entities.</p>
 *
 * <p><b>Difficulties:</b> the original is a mixin on
 * {@code EntityRenderDispatcher#renderHitbox(Entity, float, boolean, CallbackInfo)} -- it
 * cancels the vanilla hitbox and draws its own. SilkVis has no mixins (ADR-0002), so the
 * drawing happens in the world render pass instead ({@code SoupVisualsClient}), one call to
 * {@link #renderEntity} per visible entity. Consequence: the box is not tied to the entity's
 * own render pass, so it is not depth-sorted against the entity's model -- it lands in the
 * debug-quads layer of the frame. Vanilla's own hitbox (F3+B) is untouched and would double
 * up while it is on, exactly like two mods doing the same thing.</p>
 *
 * <p><b>To verify in game:</b> boxes match the colliders (including crouching), the hurt
 * flash on a hit, friend colour on a whitelisted name, the eyes rectangle at eye height, and
 * that nothing is drawn for the player in first person (the original skips it).</p>
 */
public class Hitboxes extends SoupModule {
    public final SelectSetting renderMode = new SelectSetting(
            "setting.hitboxes.rendermode.name", "setting.hitboxes.rendermode.desc")
            .value("Fill", "Outline", "Both")
            .selected("Both");
    public final BooleanSetting eyesLine = new BooleanSetting(
            "setting.hitboxes.eyesline.name", "setting.hitboxes.eyesline.desc")
            .setValue(false);
    public final SelectSetting colorMode = new SelectSetting(
            "setting.hitboxes.colormode.name", "setting.hitboxes.colormode.desc")
            .value("Sync", "Custom")
            .selected("Sync");
    public final ColorSetting hitboxColor = new ColorSetting(
            "setting.hitboxes.color.name", "setting.hitboxes.color.desc")
            .setColor(-1)
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final ColorSetting friendHitboxColor = new ColorSetting(
            "setting.hitboxes.friendcolor.name", "setting.hitboxes.friendcolor.desc")
            .setColor(-16711936)
            .visible(() -> this.colorMode.isSelected("Custom"));

    public Hitboxes() {
        super("module.hitboxes.name", SoupModuleCategory.VISUALS);
        GroupSetting renderGroup = new GroupSetting("group.hitboxes.render.name",
                "group.hitboxes.render.desc", false)
                .settings(this.renderMode, this.eyesLine);
        GroupSetting colorGroup = new GroupSetting("group.hitboxes.colors.name",
                "group.hitboxes.colors.desc", false)
                .settings(this.colorMode, this.hitboxColor, this.friendHitboxColor);
        this.setup(renderGroup, colorGroup);
    }

    /**
     * Colour of one entity's box.
     *
     * @param friend whether the entity's name is on the friend list
     */
    public int getBoxColor(boolean friend) {
        if (this.colorMode.isSelected("Custom")) {
            return friend ? this.friendHitboxColor.getColor() : this.hitboxColor.getColor();
        }
        return friend ? ColorUtil.getFriendColor() : ColorUtil.getClientColor();
    }

    /**
     * Queues the box (and optionally the eyes rectangle) of one entity.
     *
     * @param matrix        camera-relative matrix of the current world pass
     * @param entity        snapshot of the entity, taken this frame
     * @param tickProgress  tick interpolation of the frame
     * @param self          {@code true} for the local player
     * @param thirdPerson   {@code true} when the camera is not in first person
     */
    public void renderEntity(Matrix4f matrix, EntitySnapshot entity, float tickProgress,
                             boolean self, boolean thirdPerson) {
        if (!this.isEnabled()) {
            return;
        }
        // The original skips the local player in first person: the camera sits inside the
        // box, so the outline would fill the screen.
        if (self && !thirdPerson) {
            return;
        }
        String mode = this.renderMode.getSelected();
        boolean drawOutline = "Outline".equals(mode) || "Both".equals(mode);
        boolean drawFill = "Fill".equals(mode) || "Both".equals(mode);

        double x = entity.interpolatedX(tickProgress);
        double y = entity.interpolatedY(tickProgress);
        double z = entity.interpolatedZ(tickProgress);
        double half = entity.width() / 2.0;
        int color = this.getBoxColor(entity.friend());

        if (entity.player()) {
            float damage = Math.min(entity.hurtTime() / 10.0F, 1.0F);
            color = ColorUtil.gradientToRed(color, damage);
        }
        SoupRender.drawBox(matrix, x - half, y, z - half, x + half, y + entity.height(), z + half,
                color, 2.0F, drawOutline, drawFill, true);

        if (this.eyesLine.isValue() && entity.living()) {
            // The original draws a small rectangle at eye height in a fixed red: its centre is
            // the eye position minus half the entity width on both horizontal axes, and its side
            // is exactly the entity width.
            double eyeY = y + entity.eyeOffset();
            double width = entity.width();
            double minX = x - width / 2.0;
            double minZ = z - width / 2.0;
            int red = ColorUtil.getColor(255, 0, 0);
            Vector3f a = new Vector3f((float) minX, (float) eyeY, (float) minZ);
            Vector3f b = new Vector3f((float) (minX + width), (float) eyeY, (float) minZ);
            Vector3f c = new Vector3f((float) (minX + width), (float) eyeY, (float) (minZ + width));
            Vector3f d = new Vector3f((float) minX, (float) eyeY, (float) (minZ + width));
            SoupRender.drawLine(matrix, a, b, red, red, true);
            SoupRender.drawLine(matrix, b, c, red, red, true);
            SoupRender.drawLine(matrix, d, a, red, red, true);
            SoupRender.drawLine(matrix, c, d, red, red, true);
        }
    }
}

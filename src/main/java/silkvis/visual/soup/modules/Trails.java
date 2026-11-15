package silkvis.visual.soup.modules;

import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
import silkvis.visual.soup.util.EntitySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trails -- a ribbon behind every moving player.
 *
 * <p>Ported from SoupVisuals 1.21.11
 * {@code padej.soup.implement.features.modules.visuals.Trails}, {@code Trails$Trail} and
 * {@code base.util.render.TrailRenderer} (Padej_, SOUP-1.0, used by written permission).
 * Settings, groups and defaults are the original's.</p>
 *
 * <p><b>What was ported:</b> the two player modes ({@code Trail} = a solid ribbon from the
 * recorded points, {@code Tail} = billboards along the path), the three ribbon styles
 * (Solid / Faded / Invert) with their alpha reconstructions, the {@code Render Half} option,
 * the {@code Down} offset, the lifetime and alpha factors, the debug line mode, and the whole
 * {@link Trail} data object (progress, cached alpha, colour caching, expired check).</p>
 *
 * <p><b>Difficulties:</b> the original keeps the trail list inside a mixin-added field of
 * {@code Entity} ({@code IEntity#getTrails()}), which SilkVis cannot do without mixins
 * (ADR-0002). The points therefore live here in a map keyed by entity id, filled by the
 * client layer from {@link EntitySnapshot}s each frame. The ribbon of the original is built
 * with an immediate-mode {@code POSITION_COLOR} buffer at a custom line width and its
 * {@code Tail} mode uses a {@code POSITION_TEX_COLOR} buffer with
 * {@code SRC_ALPHA / ONE} blending; both are replaced by the queued quads and textured quads
 * of {@link SoupRender}, so a "tail" is a plain alpha-blended billboard here rather than an
 * additive one, and debug lines are 1 px wide instead of {@code Line Width}.</p>
 *
 * <p><b>To verify in game:</b> that the ribbon follows a running player smoothly, that a
 * standing player's trail fades out instead of freezing, both styles on a curve, and that F5
 * with "Only F5" shows the own trail only in third person.</p>
 */
public class Trails extends SoupModule implements ParticleModule {
    private static final float CROUCH_HEIGHT = 1.5F;
    /** Point spacing of the original: a new point every 0.005 blocks of movement. */
    private static final double MIN_DISTANCE = 0.005;
    private static final int MAX_POINTS = 400;

    private final Map<Integer, List<Trail>> trails = new HashMap<>();
    private long lastTrailUpdateTime;
    private boolean clearedForNoneMode;

    public final SelectSetting colorMode = new SelectSetting(
            "setting.trails.colormode.name", "setting.trails.colormode.desc")
            .value("Sync", "Custom")
            .selected("Sync");
    public final SelectSetting customColorsCount = new SelectSetting(
            "setting.trails.colorcount.name", "setting.trails.colorcount.desc")
            .value("Solo", "Duo", "Triple", "Quartet")
            .selected("Solo")
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final MultiColorSetting customColors = new MultiColorSetting(
            "setting.trails.gradientcolors.name", "setting.trails.gradientcolors.desc")
            .colors("Color 1", "Color 2", "Color 3", "Color 4")
            .defaultColors(-1499549, -13273872, -6596170, -409301)
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final SelectSetting colorAnimation = new SelectSetting(
            "setting.trails.coloranimation.name", "setting.trails.coloranimation.desc")
            .value("Wave", "Vertex")
            .selected("Wave")
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final SelectSetting players = new SelectSetting(
            "setting.trails.players.name", "setting.trails.players.desc")
            .value("Trail", "Tail", "None")
            .selected("Trail");
    public final BooleanSetting friends = new BooleanSetting(
            "setting.trails.friends.name", "setting.trails.friends.desc")
            .setValue(false)
            .visible(() -> !this.players.isSelected("None"));
    public final BooleanSetting onlyF5 = new BooleanSetting(
            "setting.trails.onlyf5.name", "setting.trails.onlyf5.desc")
            .setValue(false)
            .visible(() -> this.players.isSelected("Trail"));
    public final SelectSetting style = new SelectSetting(
            "setting.trails.style.name", "setting.trails.style.desc")
            .value("Solid", "Faded", "Invert")
            .selected("Solid")
            .visible(() -> this.players.isSelected("Trail"));
    public final BooleanSetting renderHalf = new BooleanSetting(
            "setting.trails.renderhalf.name", "setting.trails.renderhalf.desc")
            .setValue(false)
            .visible(() -> this.players.isSelected("Trail"));
    public final ValueSetting alphaFactor = new ValueSetting(
            "setting.trails.alphafactor.name", "setting.trails.alphafactor.desc")
            .setValue(50.0F)
            .range(0.0F, 100.0F)
            .visible(() -> this.players.isSelected("Trail") && this.style.isSelected("Invert"));
    public final ValueSetting trailAlpha = new ValueSetting(
            "setting.trails.alpha.name", "setting.trails.alpha.desc")
            .setValue(100.0F)
            .range(0.0F, 100.0F)
            .visible(() -> this.players.isSelected("Trail"));
    public final ValueSetting down = new ValueSetting(
            "setting.trails.down.name", "setting.trails.down.desc")
            .setValue(0.0F)
            .range(-1.0F, 1.0F);
    public final ValueSetting trailDuration = new ValueSetting(
            "setting.trails.duration.name", "setting.trails.duration.desc")
            .setValue(1.5F)
            .range(1.0F, 4.0F)
            .visible(() -> this.players.isSelected("Trail"));
    public final BooleanSetting debugLines = new BooleanSetting(
            "setting.trails.debuglines.name", "setting.trails.debuglines.desc")
            .setValue(false);
    public final ValueSetting lineWidth = new ValueSetting(
            "setting.trails.linewidth.name", "setting.trails.linewidth.desc")
            .setValue(3.0F)
            .range(1.5F, 5.0F)
            .visible(this.debugLines::isValue);

    public Trails() {
        super("module.trails.name", SoupModuleCategory.VISUALS);
        GroupSetting colorGroup = new GroupSetting("group.trails.colors.name",
                "group.trails.colors.desc", false)
                .settings(this.colorMode, this.customColorsCount, this.customColors, this.colorAnimation);
        GroupSetting renderGroup = new GroupSetting("group.trails.render.name",
                "group.trails.render.desc", false)
                .settings(this.players, this.friends, this.onlyF5);
        GroupSetting styleGroup = new GroupSetting("group.trails.style.name",
                "group.trails.style.desc", false)
                .settings(this.style, this.renderHalf, this.alphaFactor, this.trailAlpha)
                .visible(() -> this.players.isSelected("Trail"));
        GroupSetting dimensionGroup = new GroupSetting("group.trails.dimensions.name",
                "group.trails.dimensions.desc", false)
                .settings(this.down, this.trailDuration, this.debugLines, this.lineWidth)
                .visible(() -> this.players.isSelected("Trail"));
        this.setup(colorGroup, renderGroup, styleGroup, dimensionGroup);
    }

    @Override
    protected void deactivate() {
        this.trails.clear();
        this.clearedForNoneMode = false;
        this.lastTrailUpdateTime = 0L;
    }

    // ------------------------------------------------------------------ per tick

    /**
     * Port of the original's {@code TickEvent} handler: in {@code None} mode everything is
     * dropped once, in {@code Tail} mode the points are recorded and old ones are removed.
     */
    public void tick(List<EntitySnapshot> entities, float tickProgress, boolean selfSneaking) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.players.isSelected("None")) {
            if (!this.clearedForNoneMode) {
                this.trails.clear();
                this.clearedForNoneMode = true;
            }
            return;
        }
        this.clearedForNoneMode = false;
        this.forgetMissing(entities);
        if (this.players.isSelected("Tail")) {
            this.updateTrails(entities, tickProgress, selfSneaking);
        }
        this.cleanupExpiredTrails(entities);
    }

    // ------------------------------------------------------------------ per frame

    /**
     * Port of the original's {@code WorldRenderEvent} handler: {@code Trail} records points on
     * the render thread (so the ribbon is smooth between ticks) and queues the ribbon,
     * {@code Tail} only queues the billboards.
     */
    public void render(Matrix4f cameraMatrix, List<EntitySnapshot> entities, float tickProgress,
                       double camX, double camY, double camZ, boolean selfSneaking) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.players.isSelected("Trail")) {
            long currentTime = System.currentTimeMillis();
            long frameDuration = currentTime - this.lastTrailUpdateTime;
            if (frameDuration > 0L) {
                this.lastTrailUpdateTime = currentTime;
                this.updateTrails(entities, tickProgress, selfSneaking);
            }
            this.queuePlayerTrails(cameraMatrix, entities, camX, camY, camZ);
        } else if (this.players.isSelected("Tail")) {
            this.queuePlayerTails(cameraMatrix, entities, tickProgress, camX, camY, camZ, selfSneaking);
        }
    }

    /**
     * Which entities get a trail: the local player (unless "Only F5" and the camera is in
     * third person) and, when enabled, friends. The original additionally filters on its
     * server-limit config and on the invisibility level; the invisibility filter is kept in
     * the client layer, which only passes visible entities in.
     */
    public boolean shouldTrack(EntitySnapshot entity, boolean self, boolean thirdPerson) {
        if (self) {
            return !this.onlyF5.isValue() || thirdPerson;
        }
        return this.friends.isValue() && entity.friend();
    }

    private void queuePlayerTrails(Matrix4f cameraMatrix, List<EntitySnapshot> entities,
                                   double camX, double camY, double camZ) {
        boolean isFaded = this.style.isSelected("Faded");
        boolean isInvert = this.style.isSelected("Invert");
        boolean half = this.renderHalf.isValue();
        float alphaFactorValue = this.alphaFactor.getValue();
        float downOffset = this.down.getValue();
        float trailHeight = 1.8F;
        float opacity = 1.0F;
        for (EntitySnapshot entity : entities) {
            List<Trail> list = this.trails.get(entity.id());
            if (list == null || list.size() < 2) {
                continue;
            }
            for (int i = 0; i < list.size() - 1; i++) {
                Trail current = list.get(i);
                Trail next = list.get(i + 1);
                float currentAlpha = current.getAlpha() * opacity;
                float nextAlpha = next.getAlpha() * opacity;
                int currentColor = current.cachedColor;
                int nextColor = next.cachedColor;
                float x1 = (float) (current.currentX - camX);
                float y1 = (float) (current.currentY - camY) + downOffset;
                float z1 = (float) (current.currentZ - camZ);
                float x2 = (float) (next.currentX - camX);
                float y2 = (float) (next.currentY - camY) + downOffset;
                float z2 = (float) (next.currentZ - camZ);

                int currentBottom;
                int currentMid;
                int currentTop;
                int nextBottom;
                int nextMid;
                int nextTop;
                if (isFaded) {
                    currentBottom = (int) (computeFadedAlpha(0.0F, trailHeight) * currentAlpha);
                    currentMid = (int) (computeFadedAlpha(trailHeight / 2.0F, trailHeight) * currentAlpha);
                    currentTop = half ? 0 : (int) (computeFadedAlpha(trailHeight, trailHeight) * currentAlpha);
                    nextBottom = (int) (computeFadedAlpha(0.0F, trailHeight) * nextAlpha);
                    nextMid = (int) (computeFadedAlpha(trailHeight / 2.0F, trailHeight) * nextAlpha);
                    nextTop = half ? 0 : (int) (computeFadedAlpha(trailHeight, trailHeight) * nextAlpha);
                } else if (isInvert) {
                    currentBottom = (int) (computeFadedAlphaInvert(0.0F, trailHeight, alphaFactorValue) * currentAlpha);
                    currentMid = (int) (computeFadedAlphaInvert(trailHeight / 2.0F, trailHeight, alphaFactorValue) * currentAlpha);
                    currentTop = half ? 0 : (int) (computeFadedAlphaInvert(trailHeight, trailHeight, alphaFactorValue) * currentAlpha);
                    nextBottom = (int) (computeFadedAlphaInvert(0.0F, trailHeight, alphaFactorValue) * nextAlpha);
                    nextMid = (int) (computeFadedAlphaInvert(trailHeight / 2.0F, trailHeight, alphaFactorValue) * nextAlpha);
                    nextTop = half ? 0 : (int) (computeFadedAlphaInvert(trailHeight, trailHeight, alphaFactorValue) * nextAlpha);
                } else {
                    currentBottom = (int) (currentAlpha * 255.0F);
                    currentMid = currentBottom;
                    currentTop = half ? 0 : currentBottom;
                    nextBottom = (int) (nextAlpha * 255.0F);
                    nextMid = nextBottom;
                    nextTop = half ? 0 : nextBottom;
                }

                int cR = (currentColor >> 16) & 0xFF;
                int cG = (currentColor >> 8) & 0xFF;
                int cB = currentColor & 0xFF;
                int nR = (nextColor >> 16) & 0xFF;
                int nG = (nextColor >> 8) & 0xFF;
                int nB = nextColor & 0xFF;

                // Bottom half of the rib, then the top half unless "Render Half" is on. Each
                // strip is a quad of the original's vertex order.
                SoupRender.drawQuad(cameraMatrix,
                        new Vector3f(x1, y1, z1),
                        new Vector3f(x2, y2, z2),
                        new Vector3f(x2, y2 + trailHeight / 2.0F, z2),
                        new Vector3f(x1, y1 + trailHeight / 2.0F, z1),
                        bottomColor(cR, cG, cB, currentBottom, nR, nG, nB, nextBottom), true);
                if (!half) {
                    SoupRender.drawQuad(cameraMatrix,
                            new Vector3f(x1, y1 + trailHeight / 2.0F, z1),
                            new Vector3f(x2, y2 + trailHeight / 2.0F, z2),
                            new Vector3f(x2, y2 + trailHeight, z2),
                            new Vector3f(x1, y1 + trailHeight, z1),
                            bottomColor(cR, cG, cB, currentTop, nR, nG, nB, nextTop), true);
                }
                if (this.debugLines.isValue()) {
                    queueDebugLines(cameraMatrix, x1, y1, z1, x2, y2, z2, trailHeight, half,
                            currentColor, nextColor, currentAlpha, nextAlpha);
                }
            }
        }
    }

    /**
     * One quad has a single colour in this batched layer, so a strip whose two ends differ is
     * painted with the average of both, which is what the original's per-vertex colours look
     * like at this scale (0.005 blocks between points).
     */
    private static int bottomColor(int cR, int cG, int cB, int cA, int nR, int nG, int nB, int nA) {
        return ((cA + nA) / 2 & 0xFF) << 24
                | ((cR + nR) / 2 & 0xFF) << 16
                | ((cG + nG) / 2 & 0xFF) << 8
                | ((cB + nB) / 2 & 0xFF);
    }

    /** Port of {@code TrailRenderer.queueDebugLines} / its line batch. */
    private void queueDebugLines(Matrix4f cameraMatrix, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float trailHeight, boolean half,
                                 int currentColor, int nextColor, float currentAlpha,
                                 float nextAlpha) {
        float avgAlpha = (currentAlpha + nextAlpha) / 2.0F;
        int avgR = (((currentColor >> 16) & 0xFF) + ((nextColor >> 16) & 0xFF)) / 2;
        int avgG = (((currentColor >> 8) & 0xFF) + ((nextColor >> 8) & 0xFF)) / 2;
        int avgB = ((currentColor & 0xFF) + (nextColor & 0xFF)) / 2;
        int color = ((int) (avgAlpha * 255.0F) & 0xFF) << 24 | avgR << 16 | avgG << 8 | avgB;
        SoupRender.drawLine(cameraMatrix, new Vector3f(x1, y1, z1), new Vector3f(x2, y2, z2),
                color, color, true);
        if (!half) {
            SoupRender.drawLine(cameraMatrix,
                    new Vector3f(x1, y1 + trailHeight, z1),
                    new Vector3f(x2, y2 + trailHeight, z2), color, color, true);
        }
    }

    /** Port of {@code TrailRenderer.queueTails} / {@code renderTailBatch}: billboards. */
    private void queuePlayerTails(Matrix4f cameraMatrix, List<EntitySnapshot> entities,
                                  float tickProgress, double camX, double camY, double camZ,
                                  boolean selfSneaking) {
        float scale = 0.6F;
        for (EntitySnapshot entity : entities) {
            List<Trail> list = this.trails.get(entity.id());
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (Trail trail : list) {
                Matrix4f matrix = new Matrix4f(cameraMatrix);
                matrix.translate((float) (trail.currentX - camX),
                        (float) (trail.currentY - camY + 0.9F),
                        (float) (trail.currentZ - camZ));
                float animAlpha = trail.getAlpha();
                int color;
                if (this.colorMode.isSelected("Custom")) {
                    int[] colors = this.getCustomColors();
                    if (colors != null && colors.length > 0) {
                        color = this.colorAnimation.isSelected("Wave")
                                ? getWaveColor(colors, animAlpha)
                                : getVertexGradientColor(0, colors, animAlpha);
                    } else {
                        color = ColorUtil.replAlpha(trail.colorArgb, (int) (animAlpha * 255.0F));
                    }
                } else {
                    // Sync mode of the original: the owned colour decays to white with age.
                    float colorFactor = trail.getProgress();
                    int interpolated = interpolateColorRgb(trail.colorArgb, 0xFFFFFF,
                            (float) Math.pow(1.0F - colorFactor, 2.0));
                    color = ColorUtil.replAlpha(interpolated, (int) (animAlpha * 255.0F));
                }
                SoupRender.drawTexture(matrix, TAIL_TEXTURE, -scale, -scale, scale * 2.0F,
                        scale * 2.0F, color, false);
            }
        }
    }

    private void updateTrails(List<EntitySnapshot> entities, float tickProgress, boolean selfSneaking) {
        long lifetime = (long) (this.trailDuration.getValue() * 1000.0F);
        float fadeStrength = this.trailDuration.getValue();
        float opacity = this.trailAlpha.getValue() / 100.0F;
        boolean isCustomColor = this.colorMode.isSelected("Custom");
        boolean isWaveAnimation = this.colorAnimation.isSelected("Wave");
        int[] customColors = isCustomColor ? this.getCustomColors() : null;
        long currentTime = System.currentTimeMillis();
        int baseColor = baseColor();

        for (EntitySnapshot entity : entities) {
            List<Trail> list = this.trails.computeIfAbsent(entity.id(), id -> new ArrayList<>());
            float height = entity.colliderHeight(false, selfSneaking);
            if (entity.movedSquared() > 0.0) {
                double x = entity.interpolatedX(tickProgress);
                double y = entity.interpolatedY(tickProgress);
                double z = entity.interpolatedZ(tickProgress);
                if (list.isEmpty()) {
                    Trail trail = new Trail(entity.prevX(), entity.prevY(), entity.prevZ(), x, y, z,
                            baseColor, height, lifetime);
                    trail.setAlphaParams(fadeStrength, opacity);
                    trail.update(currentTime);
                    if (isCustomColor) {
                        trail.updateColor(customColors, isWaveAnimation, currentTime);
                    }
                    list.add(trail);
                } else {
                    Trail last = list.get(list.size() - 1);
                    double dx = last.currentX - x;
                    double dy = last.currentY - y;
                    double dz = last.currentZ - z;
                    if (Math.sqrt(dx * dx + dy * dy + dz * dz) >= MIN_DISTANCE) {
                        Trail trail = new Trail(last.currentX, last.currentY, last.currentZ, x, y, z,
                                baseColor, height, lifetime);
                        trail.setAlphaParams(fadeStrength, opacity);
                        trail.update(currentTime);
                        if (isCustomColor) {
                            trail.updateColor(customColors, isWaveAnimation, currentTime);
                        }
                        if (list.size() < MAX_POINTS) {
                            list.add(trail);
                        }
                    }
                }
            }
            for (Trail trail : list) {
                trail.setEntityHeight(height);
                trail.update(currentTime);
                if (isCustomColor) {
                    trail.updateColor(customColors, isWaveAnimation, currentTime);
                }
            }
        }
    }

    private void cleanupExpiredTrails(List<EntitySnapshot> entities) {
        long currentTime = System.currentTimeMillis();
        for (EntitySnapshot entity : entities) {
            List<Trail> list = this.trails.get(entity.id());
            if (list != null) {
                list.removeIf(trail -> trail.shouldRemove(currentTime));
            }
        }
    }

    private void forgetMissing(List<EntitySnapshot> entities) {
        if (this.trails.isEmpty()) {
            return;
        }
        java.util.Set<Integer> live = new java.util.HashSet<>();
        for (EntitySnapshot entity : entities) {
            live.add(entity.id());
        }
        this.trails.keySet().removeIf(id -> !live.contains(id));
    }

    /** Colour of new points: the first custom colour, or the client colour in Sync mode. */
    private int baseColor() {
        if (this.colorMode.isSelected("Custom")) {
            int[] colors = this.getCustomColors();
            return colors != null && colors.length > 0 ? colors[0] : -1;
        }
        return ColorUtil.getClientColor();
    }

    /** Port of {@code TrailRenderer.computeFadedAlpha}: brightest in the middle. */
    private static int computeFadedAlpha(float yOffset, float height) {
        float yRelative = yOffset / height;
        return yRelative <= 0.5F
                ? (int) ((1.0F - yRelative / 0.5F) * 255.0F)
                : (int) ((yRelative - 0.5F) / 0.5F * 255.0F);
    }

    /** Port of {@code TrailRenderer.computeFadedAlphaInvert}: dimmest in the middle. */
    private static int computeFadedAlphaInvert(float yOffset, float height, float alphaFactorPercent) {
        float yRelative = yOffset / height;
        int alphaFactor = (int) (255.0F * (alphaFactorPercent / 100.0F));
        return yRelative <= 0.5F
                ? (int) (alphaFactor + yRelative / 0.5F * (255 - alphaFactor))
                : (int) (255.0F - (yRelative - 0.5F) / 0.5F * (255 - alphaFactor));
    }

    private static int interpolateColorRgb(int color1, int color2, float percent) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * percent);
        int g = (int) (g1 + (g2 - g1) * percent);
        int b = (int) (b1 + (b2 - b1) * percent);
        return 0xFF000000 | r << 16 | g << 8 | b;
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

    public List<Trail> getTrails(int entityId) {
        return this.trails.getOrDefault(entityId, List.of());
    }

    /** Billboard texture of the original's {@code Tail} mode, moved under {@code silkvis}. */
    private static final Identifier TAIL_TEXTURE =
            Identifier.of("silkvis", "textures/soup/particles/firefly.png");

    /**
     * One recorded point of a trail -- a direct port of {@code Trails$Trail}. The original
     * stores two {@code Vec3d} (prev/current); here the six coordinates are plain doubles, so
     * no {@code net.minecraft} type is needed for a data holder.
     */
    public static final class Trail {
        public final double prevX;
        public final double prevY;
        public final double prevZ;
        public double currentX;
        public double currentY;
        public double currentZ;
        private final int colorArgb;
        public float entityHeight;
        private final long creationTime;
        private final long maxLifetime;
        private long lastCacheTime = -1L;
        private float cachedProgress;
        private float cachedAlpha = 1.0F;
        private float cachedRenderAlpha = 1.0F;
        public int cachedColor;
        private float fadeStrength = 1.5F;
        private float opacity = 1.0F;

        public Trail(double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                     int colorArgb, float entityHeight, long maxLifetimeMs) {
            this.prevX = fromX;
            this.prevY = fromY;
            this.prevZ = fromZ;
            this.currentX = toX;
            this.currentY = toY;
            this.currentZ = toZ;
            this.colorArgb = colorArgb;
            this.entityHeight = entityHeight;
            this.creationTime = System.currentTimeMillis();
            this.maxLifetime = maxLifetimeMs;
            this.cachedColor = colorArgb;
        }

        public void setAlphaParams(float fadeStrength, float opacity) {
            this.fadeStrength = fadeStrength;
            this.opacity = opacity;
        }

        public void setEntityHeight(float entityHeight) {
            this.entityHeight = entityHeight;
        }

        public void updateColor(int[] customColors, boolean isWave, long currentTime) {
            float progress = this.getProgress();
            this.cachedColor = this.computeColor(customColors, isWave, progress, currentTime);
        }

        private int computeColor(int[] colors, boolean isWave, float progress, long currentTime) {
            if (colors == null || colors.length <= 0) {
                return this.colorArgb;
            }
            if (isWave) {
                return getWaveColorStatic(colors, currentTime);
            }
            float colorIndex = progress * colors.length;
            int index1 = Math.min((int) colorIndex, colors.length - 1);
            int index2 = Math.min(index1 + 1, colors.length - 1);
            float lerp = colorIndex - (int) colorIndex;
            return ColorUtil.overCol(colors[index1], colors[index2], lerp);
        }

        private static int getWaveColorStatic(int[] colors, long time) {
            if (colors.length == 1) {
                return colors[0];
            }
            int angle = (int) (time / 8L % 360L);
            if (colors.length == 2) {
                angle = angle >= 180 ? 360 - angle : angle;
                return ColorUtil.overCol(colors[0], colors[1], angle / 180.0F);
            }
            float timeProgress = (float) (time / 10L % (colors.length * 360L)) / 360.0F;
            int index1 = (int) Math.floor(timeProgress) % colors.length;
            int index2 = (index1 + 1) % colors.length;
            float lerp = timeProgress - (int) Math.floor(timeProgress);
            return ColorUtil.overCol(colors[index1], colors[index2], lerp);
        }

        private void updateCache(long currentTime) {
            if (currentTime != this.lastCacheTime) {
                this.lastCacheTime = currentTime;
                long age = currentTime - this.creationTime;
                this.cachedProgress = Math.min(1.0F, (float) age / (float) this.maxLifetime);
                this.cachedAlpha = 1.0F - this.cachedProgress;
                float smoothAlpha = (float) Math.pow(this.cachedAlpha, this.fadeStrength);
                this.cachedRenderAlpha = Math.max(0.0F, Math.min(1.0F, smoothAlpha * this.opacity));
            }
        }

        public float getProgress() {
            return this.cachedProgress;
        }

        public float getAlpha() {
            return this.cachedRenderAlpha;
        }

        public void update(long currentTime) {
            this.updateCache(currentTime);
        }

        public int color() {
            return this.colorArgb;
        }

        public boolean shouldRemove(long currentTime) {
            return currentTime - this.creationTime >= this.maxLifetime;
        }
    }
}

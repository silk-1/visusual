package silkvis.visual.soup.modules;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import silkvis.visual.soup.module.ParticleModule;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.module.SoupModuleCategory;
import silkvis.visual.soup.particles.ParticleData;
import silkvis.visual.soup.particles.ParticleUpdateExecutor;
import silkvis.visual.soup.particles.WorldParticle;
import silkvis.visual.soup.render.SoupRender;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.MultiColorSetting;
import silkvis.visual.soup.setting.MultiSelectSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.ValueSetting;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.SpatialGrid3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AmbientParticles -- the ambient particle cloud around the player.
 *
 * <p>Ported from SoupVisuals 1.21.11
 * {@code padej.soup.implement.features.modules.particles.AmbientParticles} (Padej_,
 * SOUP-1.0, used by written permission). Settings, groups, defaults and the whole spawn /
 * update / render cycle are the original's.</p>
 *
 * <p><b>What was ported:</b> all four spawn pools (regular, geometric, firefly, network)
 * with their target counts, the mode filters (regular modes exclude Firefly/Cube/Pyramid;
 * geometric counts only Cube/Pyramid), the spawn box (radius around the player, height
 * above, "Emerge" spawning below), colour resolution, the spatial grid for Network links
 * and the per-particle visibility culling.</p>
 *
 * <p><b>Difficulties:</b> (1) the firefly pool is not ported yet -- it needs
 * {@code FireFlyParticle} with its trail buffer, and the trail texture is not in the ported
 * asset set; selecting "Firefly" alone therefore spawns nothing and the module comment says
 * so. (2) Culling used the original's {@code ProjectionUtil.canSee} (frustum test against
 * the previous frame's projection matrix); here the caller passes a
 * {@link Visibility} callback so the frustum maths lives in the client layer next to the
 * matrices it needs. (3) The original reads the player position directly from
 * {@code MinecraftClient}; here {@link #tick(double, double, double, Visibility)} receives
 * it, which also makes the module unit-testable.</p>
 *
 * <p><b>To verify in game:</b> particle count stays at the configured value, no particles
 * behind the player (culling), Network links appear only between particles inside the link
 * distance, and switching the mode multi-select to "only Cube" clears the regular pool
 * immediately.</p>
 */
public class AmbientParticles extends SoupModule implements ParticleModule {
    /** Frustum test supplied by the client layer (original: {@code ProjectionUtil.canSee}). */
    public interface Visibility {
        boolean canSee(float x, float y, float z, float size);
    }

    private final SelectSetting colorMode = new SelectSetting(
            "setting.ambientparticles.colormode.name", "setting.ambientparticles.colormode.desc")
            .value("Sync", "Custom")
            .selected("Sync");
    private final SelectSetting customColorsCount = new SelectSetting(
            "setting.ambientparticles.colorcount.name", "setting.ambientparticles.colorcount.desc")
            .value("Solo", "Duo", "Triple", "Quartet")
            .selected("Solo")
            .visible(() -> this.colorMode.isSelected("Custom"));
    private final MultiColorSetting customColors = new MultiColorSetting(
            "setting.ambientparticles.gradientcolors.name", "setting.ambientparticles.gradientcolors.desc")
            .colors("Color 1", "Color 2", "Color 3", "Color 4")
            .defaultColors(-1499549, -13273872, -6596170, -409301)
            .visible(() -> this.colorMode.isSelected("Custom"));
    private final SelectSetting colorAnimation = new SelectSetting(
            "setting.ambientparticles.coloranimation.name", "setting.ambientparticles.coloranimation.desc")
            .value("Wave", "Vertexes")
            .selected("Wave")
            .visible(() -> this.colorMode.isSelected("Custom"));
    private final MultiSelectSetting mode = new MultiSelectSetting(
            "setting.ambientparticles.mode.name", "setting.ambientparticles.mode.desc")
            .value("Stars", "Hearts", "Bloom", "Glyph", "Things", "Blink", "Coron", "Dollar",
                    "Flame", "Geometric", "Snowflake", "Logo", "Virus", "SoupAPI Old", "Sword",
                    "Firefly", "Network", "Cube", "Pyramid")
            .selected("Stars");
    private final MultiSelectSetting physics = new MultiSelectSetting(
            "setting.ambientparticles.physics.name", "setting.ambientparticles.physics.desc")
            .value("Fall", "Fly", "Emerge")
            .selected("Fly");
    private final ValueSetting regularCount = new ValueSetting(
            "setting.ambientparticles.regularcount.name", "setting.ambientparticles.regularcount.desc")
            .setValue(100.0F)
            .range(20, 500);
    private final ValueSetting regularScale = new ValueSetting(
            "setting.ambientparticles.regularscale.name", "setting.ambientparticles.regularscale.desc")
            .setValue(2.5F)
            .range(0.1F, 15.0F);
    private final ValueSetting fireflyCount = new ValueSetting(
            "setting.ambientparticles.fireflycount.name", "setting.ambientparticles.fireflycount.desc")
            .setValue(30.0F)
            .range(10, 100);
    private final ValueSetting fireflyScale = new ValueSetting(
            "setting.ambientparticles.fireflyscale.name", "setting.ambientparticles.fireflyscale.desc")
            .setValue(1.0F)
            .range(0.3F, 3.0F);
    private final ValueSetting fireflyTrailLength = new ValueSetting(
            "setting.ambientparticles.firefly.traillength.name", "setting.ambientparticles.firefly.traillength.desc")
            .setValue(20.0F)
            .range(5, 60);
    private final ValueSetting geometricsCount = new ValueSetting(
            "setting.ambientparticles.geometricscount.name", "setting.ambientparticles.geometricscount.desc")
            .setValue(50.0F)
            .range(10, 200);
    private final ValueSetting geometricsScale = new ValueSetting(
            "setting.ambientparticles.geometricsscale.name", "setting.ambientparticles.geometricsscale.desc")
            .setValue(2.0F)
            .range(0.5F, 15.0F);
    private final ValueSetting linkDistance = new ValueSetting(
            "setting.ambientparticles.linkdistance.name", "setting.ambientparticles.linkdistance.desc")
            .setValue(5.0F)
            .range(2, 8);
    private final ValueSetting maxLinksPerParticle = new ValueSetting(
            "setting.ambientparticles.maxlinks.name", "setting.ambientparticles.maxlinks.desc")
            .setValue(5.0F)
            .range(1, 10);
    private final ValueSetting spawnRadius = new ValueSetting(
            "setting.ambientparticles.spawnradius.name", "setting.ambientparticles.spawnradius.desc")
            .setValue(25.0F)
            .range(10, 40);
    private final ValueSetting spawnHeight = new ValueSetting(
            "setting.ambientparticles.spawnheight.name", "setting.ambientparticles.spawnheight.desc")
            .setValue(4.0F)
            .range(2, 20);

    private final List<WorldParticle> particles = new ArrayList<>();
    private final List<WorldParticle> networkParticles = new ArrayList<>();
    private final List<WorldParticle> geometricsParticles = new ArrayList<>();
    private SpatialGrid3D<WorldParticle> spatialGrid;

    public AmbientParticles() {
        super("module.ambientparticles.name", SoupModuleCategory.PARTICLES);
        GroupSetting colorGroup = new GroupSetting("group.ambientparticles.colors.name",
                "group.ambientparticles.colors.desc", false)
                .settings(this.colorMode, this.customColorsCount, this.customColors, this.colorAnimation);
        GroupSetting appearanceGroup = new GroupSetting("group.ambientparticles.appearance.name",
                "group.ambientparticles.appearance.desc", false)
                .settings(this.mode, this.physics);
        GroupSetting regularGroup = new GroupSetting("group.ambientparticles.regular.name",
                "group.ambientparticles.regular.desc", false)
                .settings(this.regularCount, this.regularScale)
                .visible(() -> hasRegularMode());
        GroupSetting fireflyGroup = new GroupSetting("group.ambientparticles.firefly.name",
                "group.ambientparticles.firefly.desc", false)
                .settings(this.fireflyCount, this.fireflyScale, this.fireflyTrailLength)
                .visible(() -> this.mode.getSelected().contains("Firefly"));
        GroupSetting geometricsGroup = new GroupSetting("group.ambientparticles.geometrics.name",
                "group.ambientparticles.geometrics.desc", false)
                .settings(this.geometricsCount, this.geometricsScale)
                .visible(() -> this.mode.isSelected("Cube") || this.mode.isSelected("Pyramid"));
        GroupSetting networkGroup = new GroupSetting("group.ambientparticles.network.name",
                "group.ambientparticles.network.desc", false)
                .settings(this.linkDistance, this.maxLinksPerParticle)
                .visible(() -> this.mode.getSelected().contains("Network"));
        GroupSetting spawnGroup = new GroupSetting("group.ambientparticles.spawn.name",
                "group.ambientparticles.spawn.desc", false)
                .settings(this.spawnRadius, this.spawnHeight);
        this.setup(colorGroup, appearanceGroup, regularGroup, fireflyGroup, geometricsGroup,
                networkGroup, spawnGroup);
    }

    private static boolean isRegularMode(String particleMode) {
        return !"Firefly".equals(particleMode) && !"Cube".equals(particleMode)
                && !"Pyramid".equals(particleMode);
    }

    private boolean hasRegularMode() {
        for (String selectedMode : this.mode.getSelected()) {
            if (isRegularMode(selectedMode)) {
                return true;
            }
        }
        return false;
    }

    private String getRandomMode() {
        List<String> selected = this.mode.getSelected();
        int allowedCount = 0;
        for (String particleMode : selected) {
            if (isRegularMode(particleMode)) {
                allowedCount++;
            }
        }
        if (allowedCount == 0) {
            return null;
        }
        int targetIndex = ThreadLocalRandom.current().nextInt(allowedCount);
        for (String particleMode : selected) {
            if (isRegularMode(particleMode) && targetIndex-- == 0) {
                return particleMode;
            }
        }
        return null;
    }

    private String getRandomGeometricMode() {
        List<String> selected = this.mode.getSelected();
        int allowedCount = 0;
        for (String particleMode : selected) {
            if ("Cube".equals(particleMode) || "Pyramid".equals(particleMode)) {
                allowedCount++;
            }
        }
        if (allowedCount == 0) {
            return "Cube";
        }
        int targetIndex = ThreadLocalRandom.current().nextInt(allowedCount);
        for (String particleMode : selected) {
            if (("Cube".equals(particleMode) || "Pyramid".equals(particleMode)) && targetIndex-- == 0) {
                return particleMode;
            }
        }
        return "Cube";
    }

    private String getRandomPhysics() {
        List<String> selected = this.physics.getSelected();
        return selected.isEmpty() ? "Fall"
                : selected.get(ThreadLocalRandom.current().nextInt(selected.size()));
    }

    // ------------------------------------------------------------------ tick

    /**
     * One client tick: prune dead particles, top every pool up to its target count and keep
     * the spatial grid in sync with the network particles.
     *
     * @param playerX/playerY/playerZ the player's feet position (original: {@code mc.player})
     * @param visibility frustum test for culling; {@code null} keeps everything visible
     */
    public void tick(double playerX, double playerY, double playerZ, Visibility visibility) {
        if (!isEnabled()) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        ParticleUpdateExecutor.updateParticlesInPlace(this.particles, currentTime,
                particle -> visibility == null || visibility.canSee(particle.getX(), particle.getY(),
                        particle.getZ(), particle.getScale() * 0.07F),
                null,
                particle -> {
                    if ("Network".equals(particle.getParticleMode())) {
                        if (this.spatialGrid != null) {
                            this.spatialGrid.remove(particle, particle.getX(), particle.getY(), particle.getZ());
                        }
                        this.networkParticles.remove(particle);
                    }
                });
        ParticleUpdateExecutor.updateParticlesInPlace(this.geometricsParticles, currentTime,
                particle -> visibility == null || visibility.canSee(particle.getX(), particle.getY(),
                        particle.getZ(), particle.getScale() * 0.07F));
        if (this.spatialGrid != null && !this.networkParticles.isEmpty()) {
            for (WorldParticle particle : this.networkParticles) {
                this.spatialGrid.update(particle, particle.getPx(), particle.getPy(), particle.getPz(),
                        particle.getX(), particle.getY(), particle.getZ());
            }
        }

        List<String> selectedModes = this.mode.getSelected();
        List<String> selectedPhysics = this.physics.getSelected();
        if (selectedModes.isEmpty() || selectedPhysics.isEmpty()) {
            clearAll();
            return;
        }
        if (hasRegularMode()) {
            int targetRegularCount = (int) this.regularCount.getValue();
            for (int i = this.particles.size(); i < targetRegularCount; i++) {
                spawnParticle(playerX, playerY, playerZ);
            }
        } else {
            this.particles.clear();
            this.networkParticles.clear();
            this.spatialGrid = null;
        }
        if (selectedModes.contains("Cube") || selectedModes.contains("Pyramid")) {
            int targetGeometricsCount = (int) this.geometricsCount.getValue();
            for (int i = this.geometricsParticles.size(); i < targetGeometricsCount; i++) {
                spawnGeometric(playerX, playerY, playerZ);
            }
        } else {
            this.geometricsParticles.clear();
        }
        // Firefly pool: not ported yet (see the class comment), so it is kept empty.
    }

    private void spawnParticle(double playerX, double playerY, double playerZ) {
        String selectedMode = getRandomMode();
        if (selectedMode == null) {
            return;
        }
        String selectedPhysics = "Network".equals(selectedMode) ? "Fly" : getRandomPhysics();
        boolean emerge = "Emerge".equals(selectedPhysics);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float radius = this.spawnRadius.getValue();
        float height = this.spawnHeight.getValue();
        float x = (float) (playerX + (random.nextDouble() * 2.0 - 1.0) * radius);
        float y;
        if (emerge) {
            y = (float) (playerY - 1.0 - random.nextDouble() * 2.0);
        } else {
            y = (float) (playerY + 2.0 + random.nextDouble() * (height - 2.0F));
        }
        float z = (float) (playerZ + (random.nextDouble() * 2.0 - 1.0) * radius);
        int colorInt;
        if (this.colorMode.isSelected("Sync")) {
            colorInt = ColorUtil.getClientColor();
        } else {
            int[] colors = getCustomColors();
            colorInt = colors != null && colors.length > 0 ? colors[0] : -1;
        }
        WorldParticle particle = new WorldParticle(x, y, z, colorInt,
                (float) (random.nextDouble() * 180.0),
                (float) (random.nextDouble() * 5.0 + 5.0),
                0.0F,
                selectedMode,
                selectedPhysics,
                "Glyph".equals(selectedMode) ? ParticleData.getRandomGlyphTexture()
                        : ("Things".equals(selectedMode) ? ParticleData.getRandomGlyphAltTexture() : null),
                0.0F,
                this.regularScale.getValue(),
                0.0F,
                this);
        this.particles.add(particle);
        if ("Network".equals(selectedMode)) {
            if (this.spatialGrid == null) {
                this.spatialGrid = new SpatialGrid3D<>(this.linkDistance.getValue());
            }
            this.networkParticles.add(particle);
            this.spatialGrid.insert(particle, x, y, z);
        }
    }

    private void spawnGeometric(double playerX, double playerY, double playerZ) {
        String selectedMode = getRandomGeometricMode();
        String selectedPhysics = getRandomPhysics();
        boolean emerge = "Emerge".equals(selectedPhysics);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float radius = this.spawnRadius.getValue();
        float height = this.spawnHeight.getValue();
        float x = (float) (playerX + (random.nextDouble() * 2.0 - 1.0) * radius);
        float y;
        if (emerge) {
            y = (float) (playerY - 1.0 - random.nextDouble() * 2.0);
        } else {
            y = (float) (playerY + 2.0 + random.nextDouble() * (height - 2.0F));
        }
        float z = (float) (playerZ + (random.nextDouble() * 2.0 - 1.0) * radius);
        int colorInt;
        if (this.colorMode.isSelected("Sync")) {
            colorInt = ColorUtil.getClientColor();
        } else {
            int[] colors = getCustomColors();
            colorInt = colors != null && colors.length > 0 ? colors[0] : -1;
        }
        this.geometricsParticles.add(new WorldParticle(x, y, z, colorInt,
                (float) (random.nextDouble() * 180.0),
                (float) (random.nextDouble() * 5.0 + 5.0),
                0.0F,
                selectedMode,
                selectedPhysics,
                null,
                0.0F,
                this.geometricsScale.getValue(),
                0.0F,
                this));
    }

    private void clearAll() {
        this.particles.clear();
        this.geometricsParticles.clear();
        this.networkParticles.clear();
        this.spatialGrid = null;
    }

    // ------------------------------------------------------------------ render

    public void render(Matrix4f cameraMatrix, double camX, double camY, double camZ,
                       float tickProgress, float cameraYaw, float cameraPitch) {
        if (!isEnabled()) {
            return;
        }
        for (WorldParticle particle : this.particles) {
            if (particle.isVisible()) {
                particle.render(cameraMatrix, camX, camY, camZ, tickProgress, cameraYaw, cameraPitch);
            }
        }
        for (WorldParticle particle : this.geometricsParticles) {
            if (particle.isVisible()) {
                particle.render(cameraMatrix, camX, camY, camZ, tickProgress, cameraYaw, cameraPitch);
            }
        }
        if (this.mode.isSelected("Network") && this.spatialGrid != null && !this.networkParticles.isEmpty()) {
            renderNetworkLinks(cameraMatrix);
        }
    }

    /**
     * Links every network particle with its neighbours inside the link distance, at most
     * {@code Max Links} per particle, skipping pairs already drawn (the original walks the
     * spatial grid the same way and uses a visited set for the same reason).
     */
    private void renderNetworkLinks(Matrix4f cameraMatrix) {
        int particleCount = this.networkParticles.size();
        if (particleCount < 2) {
            return;
        }
        float maxLinkDistance = this.linkDistance.getValue();
        int maxLinks = Math.max(1, (int) this.maxLinksPerParticle.getValue());
        Set<Long> visited = this.spatialGrid.newVisitedSet();
        int color = ColorUtil.multAlpha(ColorUtil.getClientColor(), 0.6F);
        for (WorldParticle particle : this.networkParticles) {
            List<WorldParticle> neighbours = this.spatialGrid.query(particle.getX(), particle.getY(),
                    particle.getZ(), maxLinkDistance);
            int links = 0;
            int ax = this.spatialGrid.cellX(particle.getX());
            int ay = this.spatialGrid.cellY(particle.getY());
            int az = this.spatialGrid.cellZ(particle.getZ());
            for (WorldParticle neighbour : neighbours) {
                if (neighbour == particle || links >= maxLinks) {
                    continue;
                }
                long key = SpatialGrid3D.pairKey(ax, ay, az,
                        this.spatialGrid.cellX(neighbour.getX()),
                        this.spatialGrid.cellY(neighbour.getY()),
                        this.spatialGrid.cellZ(neighbour.getZ()));
                if (!visited.add(key)) {
                    continue;
                }
                links++;
                SoupRender.drawLine(cameraMatrix,
                        new Vector3f(particle.getX(), particle.getY(), particle.getZ()),
                        new Vector3f(neighbour.getX(), neighbour.getY(), neighbour.getZ()),
                        color, color, false);
            }
        }
    }

    // ------------------------------------------------------------------ settings access

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

    @Override
    protected void deactivate() {
        clearAll();
    }

    public MultiSelectSetting modeSetting() {
        return this.mode;
    }

    public MultiSelectSetting physicsSetting() {
        return this.physics;
    }

    public ValueSetting regularCountSetting() {
        return this.regularCount;
    }

    public ValueSetting geometricsCountSetting() {
        return this.geometricsCount;
    }

    public ValueSetting spawnRadiusSetting() {
        return this.spawnRadius;
    }

    public List<WorldParticle> getParticles() {
        return this.particles;
    }

    public List<WorldParticle> getNetworkParticles() {
        return this.networkParticles;
    }

    public List<WorldParticle> getGeometricsParticles() {
        return this.geometricsParticles;
    }
}

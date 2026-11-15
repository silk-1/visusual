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
import java.util.concurrent.ThreadLocalRandom;

/**
 * TotemParticles -- particle burst when a totem pops.
 *
 * <p>Ported from SoupVisuals 1.21.11
 * {@code padej.soup.implement.features.modules.particles.TotemParticles} (Padej_, SOUP-1.0,
 * used by written permission). Settings, groups, defaults and the emitter logic are the
 * original's.</p>
 *
 * <p><b>What was ported:</b> the two-stage spawn (an instant burst of
 * {@code Particle Count} particles plus a trickle of {@code count / duration} per tick for
 * {@code Emitter Duration} ticks), per-particle mode/physics picks, the "Vanilla" colour
 * mode with its exact green/yellow random distribution, the network pool with the spatial
 * grid, and the original's rule that a {@code Network} particle is always {@code Fly}.</p>
 *
 * <p><b>Difficulties:</b> the original is called from its own mixin on
 * {@code ClientPlayNetworkHandler} (the {@code EntityStatuses.USE_TOTEM_OF_UNDYING} entity
 * status). SilkVis has no mixins, so the client layer detects the pop in a tick poll
 * (totem vanished from hand/inventory while the player was damaged) and calls
 * {@link #onTotemPop} with the entity position. Network links reuse the ported
 * {@code NetworkRenderer} path, which lives in this module (see {@code renderNetworkLinks}).</p>
 *
 * <p><b>To verify in game:</b> burst size on a real pop, that the emitter stops after
 * {@code Emitter Duration} ticks, that a second pop during the first one adds a second
 * emitter instead of resetting the first, and that particles are cleared on world change.</p>
 */
public class TotemParticles extends SoupModule implements ParticleModule {
    public final SelectSetting colorMode = new SelectSetting(
            "setting.totemparticles.colormode.name", "setting.totemparticles.colormode.desc")
            .value("Sync", "Vanilla", "Custom")
            .selected("Vanilla");
    private final SelectSetting customColorsCount = new SelectSetting(
            "setting.totemparticles.colorcount.name", "setting.totemparticles.colorcount.desc")
            .value("Solo", "Duo", "Triple", "Quartet")
            .selected("Solo")
            .visible(() -> this.colorMode.isSelected("Custom"));
    private final MultiColorSetting customColors = new MultiColorSetting(
            "setting.totemparticles.gradientcolors.name", "setting.totemparticles.gradientcolors.desc")
            .colors("Color 1", "Color 2", "Color 3", "Color 4")
            .defaultColors(-1499549, -13273872, -6596170, -409301)
            .visible(() -> this.colorMode.isSelected("Custom"));
    public final SelectSetting colorAnimation = new SelectSetting(
            "setting.totemparticles.coloranimation.name", "setting.totemparticles.coloranimation.desc")
            .value("Wave", "Vertexes")
            .selected("Wave")
            .visible(() -> this.colorMode.isSelected("Custom"));
    private final MultiSelectSetting mode = new MultiSelectSetting(
            "setting.totemparticles.mode.name", "setting.totemparticles.mode.desc")
            .value("Stars", "Hearts", "Bloom", "Glyph", "Things", "Blink", "Coron", "Dollar",
                    "Flame", "Geometric", "Snowflake", "Logo", "Virus", "SoupAPI Old", "Sword",
                    "Network", "Cube", "Pyramid")
            .selected("Bloom");
    private final MultiSelectSetting physics = new MultiSelectSetting(
            "setting.totemparticles.physics.name", "setting.totemparticles.physics.desc")
            .value("Fall", "Fly")
            .selected("Fly");
    private final ValueSetting particleCount = new ValueSetting(
            "setting.totemparticles.particlecount.name", "setting.totemparticles.particlecount.desc")
            .setValue(30.0F)
            .range(10, 70);
    private final ValueSetting emitterDuration = new ValueSetting(
            "setting.totemparticles.emitterduration.name", "setting.totemparticles.emitterduration.desc")
            .setValue(30.0F)
            .range(10, 60);
    private final ValueSetting lifeTime = new ValueSetting(
            "setting.totemparticles.lifetime.name", "setting.totemparticles.lifetime.desc")
            .setValue(3.0F)
            .range(1, 10);
    private final ValueSetting speed = new ValueSetting(
            "setting.totemparticles.speed.name", "setting.totemparticles.speed.desc")
            .setValue(0.5F)
            .range(0.1F, 3.0F);
    private final ValueSetting scale = new ValueSetting(
            "setting.totemparticles.scale.name", "setting.totemparticles.scale.desc")
            .setValue(1.5F)
            .range(0.5F, 3.0F);
    public final ValueSetting linkDistance = new ValueSetting(
            "setting.totemparticles.linkdistance.name", "setting.totemparticles.linkdistance.desc")
            .setValue(1.0F)
            .range(0.5F, 2.0F)
            .visible(() -> this.mode.getSelected().contains("Network"));

    private final List<WorldParticle> particles = new ArrayList<>();
    private final List<WorldParticle> networkParticles = new ArrayList<>();
    private final List<Emitter> emitters = new ArrayList<>();
    private SpatialGrid3D<WorldParticle> spatialGrid;

    public TotemParticles() {
        super("module.totemparticles.name", SoupModuleCategory.PARTICLES);
        GroupSetting colorGroup = new GroupSetting("group.totemparticles.colors.name",
                "group.totemparticles.colors.desc", false)
                .settings(this.colorMode, this.customColorsCount, this.customColors, this.colorAnimation);
        GroupSetting appearanceGroup = new GroupSetting("group.totemparticles.appearance.name",
                "group.totemparticles.appearance.desc", false)
                .settings(this.mode, this.physics);
        GroupSetting behaviorGroup = new GroupSetting("group.totemparticles.behavior.name",
                "group.totemparticles.behavior.desc", false)
                .settings(this.particleCount, this.emitterDuration, this.lifeTime, this.speed, this.scale);
        this.setup(colorGroup, appearanceGroup, behaviorGroup, this.linkDistance);
    }

    // ------------------------------------------------------------------ spawn

    /**
     * Starts an emitter and the initial burst at the popping entity, exactly like the
     * original {@code onTotemPop(Entity)}. Called from the client-side totem detector.
     */
    public void onTotemPop(double entityX, double entityY, double entityZ, float entityHeight) {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        this.emitters.add(new Emitter(entityX, entityY, entityZ, entityHeight, now,
                (int) this.emitterDuration.getValue()));
        int burst = (int) this.particleCount.getValue();
        for (int i = 0; i < burst; i++) {
            spawnParticle(entityX, entityY, entityZ, entityHeight);
        }
    }

    /** Convenience overload for the detector, which has the player's bounding box. */
    public void onTotemPop(double entityX, double entityY, double entityZ) {
        onTotemPop(entityX, entityY, entityZ, 1.8F);
    }

    private void spawnParticle(double entityX, double entityY, double entityZ, float entityHeight) {
        String selectedMode = getRandomMode();
        String selectedPhysics = "Network".equals(selectedMode) ? "Fly" : getRandomPhysics();
        float x = (float) entityX;
        float y = (float) (entityY + entityHeight / 2.0F);
        float z = (float) entityZ;
        int colorInt = getParticleColor();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        WorldParticle particle = new WorldParticle(x, y, z, colorInt,
                (float) (random.nextDouble() * 180.0),
                (float) (random.nextDouble() * 25.0 + 5.0),
                0.0F,
                selectedMode,
                selectedPhysics,
                "Glyph".equals(selectedMode) ? ParticleData.getRandomGlyphTexture()
                        : ("Things".equals(selectedMode) ? ParticleData.getRandomGlyphAltTexture() : null),
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
            this.spatialGrid.insert(particle, x, y, z);
        }
    }

    private int getParticleColor() {
        if (this.colorMode.isSelected("Sync")) {
            return ColorUtil.getClientColor();
        }
        if (this.colorMode.isSelected("Vanilla")) {
            // The exact totem distribution of the original: 25 % yellow-ish sparks,
            // 75 % green-ish sparks.
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (random.nextDouble() < 0.25) {
                float r = (float) random.nextDouble(0.6, 0.8);
                float g = (float) random.nextDouble(0.6, 0.9);
                float b = (float) random.nextDouble(0.0, 0.2);
                int rgb = (int) (r * 255.0F) << 16 | (int) (g * 255.0F) << 8 | (int) (b * 255.0F);
                return 0xFF000000 | rgb;
            }
            float r = (float) random.nextDouble(0.1, 0.3);
            float g = (float) random.nextDouble(0.4, 0.7);
            float b = (float) random.nextDouble(0.0, 0.2);
            int rgb = (int) (r * 255.0F) << 16 | (int) (g * 255.0F) << 8 | (int) (b * 255.0F);
            return 0xFF000000 | rgb;
        }
        int[] colors = getCustomColors();
        return colors != null && colors.length > 0 ? colors[0] : -1;
    }

    private String getRandomMode() {
        List<String> selected = this.mode.getSelected();
        return selected.isEmpty() ? "Stars"
                : selected.get(ThreadLocalRandom.current().nextInt(selected.size()));
    }

    private String getRandomPhysics() {
        List<String> selected = this.physics.getSelected();
        return selected.isEmpty() ? "Fall"
                : selected.get(ThreadLocalRandom.current().nextInt(selected.size()));
    }

    // ------------------------------------------------------------------ tick / render

    /** Advances emitters and particles; one call per client tick. */
    public void tick() {
        if (!isEnabled()) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        this.emitters.removeIf(emitter -> {
            if (emitter.tick(currentTime)) {
                return true;
            }
            int perTick = Math.max(1, (int) this.particleCount.getValue() / (int) this.emitterDuration.getValue());
            for (int i = 0; i < perTick; i++) {
                spawnParticle(emitter.x, emitter.y, emitter.z, emitter.height);
            }
            return false;
        });
        ParticleUpdateExecutor.updateParticlesInPlace(this.particles, currentTime, particle -> true, null,
                particle -> {
                    if ("Network".equals(particle.getParticleMode())) {
                        if (this.spatialGrid != null) {
                            this.spatialGrid.remove(particle, particle.getX(), particle.getY(), particle.getZ());
                        }
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

    public void render(Matrix4f cameraMatrix, double camX, double camY, double camZ,
                       float tickProgress, float cameraYaw, float cameraPitch) {
        if (!isEnabled()) {
            return;
        }
        for (WorldParticle particle : this.particles) {
            particle.render(cameraMatrix, camX, camY, camZ, tickProgress, cameraYaw, cameraPitch);
        }
        if (this.mode.isSelected("Network") && this.spatialGrid != null && !this.networkParticles.isEmpty()) {
            renderNetworkLinks(cameraMatrix);
        }
    }

    /** Same link walk as {@code NetworkRenderer.renderNetworkLinks} of the original. */
    private void renderNetworkLinks(Matrix4f cameraMatrix) {
        if (this.networkParticles.size() < 2) {
            return;
        }
        float maxDistance = this.linkDistance.getValue();
        int color = ColorUtil.multAlpha(ColorUtil.getClientColor(), 0.6F);
        java.util.Set<Long> visited = this.spatialGrid.newVisitedSet();
        for (WorldParticle particle : this.networkParticles) {
            List<WorldParticle> neighbours = this.spatialGrid.query(particle.getX(), particle.getY(),
                    particle.getZ(), maxDistance);
            for (WorldParticle neighbour : neighbours) {
                if (neighbour == particle) {
                    continue;
                }
                long key = SpatialGrid3D.pairKey(
                        this.spatialGrid.cellX(particle.getX()), this.spatialGrid.cellY(particle.getY()),
                        this.spatialGrid.cellZ(particle.getZ()),
                        this.spatialGrid.cellX(neighbour.getX()), this.spatialGrid.cellY(neighbour.getY()),
                        this.spatialGrid.cellZ(neighbour.getZ()));
                if (!visited.add(key)) {
                    continue;
                }
                SoupRender.drawLine(cameraMatrix,
                        new Vector3f(particle.getX(), particle.getY(), particle.getZ()),
                        new Vector3f(neighbour.getX(), neighbour.getY(), neighbour.getZ()),
                        color, color, false);
            }
        }
    }

    // ------------------------------------------------------------------ settings

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
        this.particles.clear();
        this.networkParticles.clear();
        this.emitters.clear();
        this.spatialGrid = null;
    }

    public MultiSelectSetting modeSetting() {
        return this.mode;
    }

    public ValueSetting particleCountSetting() {
        return this.particleCount;
    }

    public List<WorldParticle> getParticles() {
        return this.particles;
    }

    public List<Emitter> getEmitters() {
        return this.emitters;
    }

    /**
     * One totem pop being emitted over several ticks. The original keeps the entity and
     * reads its position every tick; the port keeps the last sampled position so the
     * emitter still works when the entity is gone (a player who popped a totem in the void).
     */
    public static final class Emitter {
        private final double x;
        private final double y;
        private final double z;
        private final float height;
        private final long startTime;
        private final int durationTicks;

        public Emitter(double x, double y, double z, float height, long startTime, int durationTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.height = height;
            this.startTime = startTime;
            this.durationTicks = durationTicks;
        }

        /** True once the emitter is done ({@code durationTicks} ticks of 50 ms). */
        public boolean tick(long currentTime) {
            return currentTime - this.startTime > this.durationTicks * 50L;
        }
    }
}

package silkvis.client;

import net.fabricmc.loader.api.FabricLoader;
import silkvis.config.ConfigException;
import silkvis.config.ConfigPaths;
import silkvis.config.Debouncer;
import silkvis.config.GlobalConfig;
import silkvis.config.JsonConfigFile;
import silkvis.config.LoadOutcome;
import silkvis.config.ProfileIdentity;
import silkvis.config.UiConfig;
import silkvis.core.BoundedExecutors;
import silkvis.core.Capability;
import silkvis.core.Clock;
import silkvis.core.ModuleCategory;
import silkvis.core.ModuleDescriptor;
import silkvis.core.ModuleRegistry;
import silkvis.core.WorldSessionToken;
import silkvis.destinations.DestinationsConfig;
import silkvis.diagnostics.BoundedLog;
import silkvis.hud.RateLimiter;
import silkvis.integration.IntegrationRegistry;
import silkvis.map.BoundedTileCache;
import silkvis.map.MapIdentity;
import silkvis.map.MapService;
import silkvis.map.MarkerSet;
import silkvis.map.TileStore;
import silkvis.media.MediaService;
import silkvis.media.MediaProvider;
import silkvis.media.ProviderStatus;
import silkvis.ui.Localizer;
import silkvis.ui.MainScreenModel;
import silkvis.ui.ThemeTokens;
import silkvis.ui.hud.HudLayoutConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Composition root for the client (ARCH-01/ARCH-02).
 *
 * <p>Owns the clock, executors, config stores, module registry and the media/map
 * services. No screen is referenced from here beyond the factory injected by the
 * entrypoint, and no service knows about concrete screens.</p>
 */
public final class SilkVisRuntime {
    private static SilkVisRuntime instance;

    private final Clock clock = Clock.system();
    private final BoundedExecutors executors = BoundedExecutors.defaults();
    private final BoundedLog log = new BoundedLog(clock, 512, 5000);
    private final IntegrationRegistry integrations = new IntegrationRegistry();
    private final ModuleRegistry modules = new ModuleRegistry();
    private final ConfigPaths paths;
    private final JsonConfigFile<GlobalConfig> globalFile;
    private final Debouncer<GlobalConfig> globalDebouncer;

    private final MainScreenModel screenModel;
    private ThemeTokens theme = ThemeTokens.graphiteTurquoise();

    private GlobalConfig global = GlobalConfig.defaults();
    private UiConfig ui = UiConfig.defaults();
    private DestinationsConfig destinations = new DestinationsConfig("default", DestinationsConfig.defaultSlots());
    private MarkerSet markers = MarkerSet.empty();

    private String profileId = "default";
    private WorldSessionToken token = WorldSessionToken.none();
    private MediaService media;
    private MapService map;
    private TileStore tileStore;
    private BoundedTileCache tileCache = new BoundedTileCache(64L * 1024 * 1024);
    private MapIdentity mapIdentity;
    private boolean mediaStarted;
    private boolean hudInteractionMode;

    private final RateLimiter mediaPollLimiter = new RateLimiter(clock, 1);

    private SilkVisRuntime() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path gameDir = FabricLoader.getInstance().getGameDir();
        this.paths = new ConfigPaths(configDir, gameDir.resolve("silkvis-data"));
        this.globalFile = new JsonConfigFile<>(paths.globalFile(), new GlobalConfig.Codec(), clock);
        this.globalDebouncer = new Debouncer<>(500, clock, this::saveGlobalAsync);
        this.screenModel = new MainScreenModel(modules);
        registerModules();
        loadGlobal();
    }

    public static synchronized SilkVisRuntime get() {
        if (instance == null) {
            instance = new SilkVisRuntime();
        }
        return instance;
    }

    private void registerModules() {
        // P1 modules are registered with a functional vertical slice; P2 stays planned.
        register(new ModuleDescriptor("visual.camera", ModuleCategory.VISUAL,
                "silkvis.module.visual.camera", "silkvis.module.visual.camera.desc", 1,
                Set.of(Capability.CLIENT_RENDER), java.util.List.of()),
                noop());
        register(new ModuleDescriptor("visual.world", ModuleCategory.VISUAL,
                "silkvis.module.visual.world", "silkvis.module.visual.world.desc", 1,
                Set.of(Capability.CLIENT_RENDER), java.util.List.of()),
                noop());
        register(new ModuleDescriptor("hud.overlay", ModuleCategory.HUD,
                "silkvis.module.hud.overlay", "silkvis.module.hud.overlay.desc", 1,
                Set.of(Capability.CLIENT_RENDER), java.util.List.of()),
                noop());
        register(new ModuleDescriptor("hud.editor", ModuleCategory.HUD,
                "silkvis.module.hud.editor", "silkvis.module.hud.editor.desc", 1,
                Set.of(Capability.CLIENT_RENDER), java.util.List.of()),
                noop());
        register(new ModuleDescriptor("map.core", ModuleCategory.MAP,
                "silkvis.module.map.core", "silkvis.module.map.core.desc", 1,
                Set.of(Capability.MAP_SAMPLING), java.util.List.of()),
                noop());
        register(new ModuleDescriptor("map.markers", ModuleCategory.MAP,
                "silkvis.module.map.markers", "silkvis.module.map.markers.desc", 1,
                Set.of(), java.util.List.of()),
                noop());
        register(new ModuleDescriptor("media.overlay", ModuleCategory.MUSIC,
                "silkvis.module.media.overlay", "silkvis.module.media.overlay.desc", 1,
                Set.of(Capability.MEDIA_PROVIDER), java.util.List.of()),
                noop());
        register(new ModuleDescriptor("theme.local", ModuleCategory.THEME,
                "silkvis.module.theme.local", "silkvis.module.theme.local.desc", 1,
                Set.of(), java.util.List.of()),
                noop());
        register(new ModuleDescriptor("integrations.probe", ModuleCategory.INTEGRATIONS,
                "silkvis.module.integrations.probe", "silkvis.module.integrations.probe.desc", 1,
                Set.of(), java.util.List.of()),
                noop());
    }

    private void register(ModuleDescriptor descriptor, ModuleRegistry.Module module) {
        modules.register(descriptor, module);
    }

    private static ModuleRegistry.Module noop() {
        return new ModuleRegistry.Module() {
            @Override
            public void enable() {
            }

            @Override
            public void disable() {
            }
        };
    }

    // ---------------------------------------------------------------- config

    private void loadGlobal() {
        LoadOutcome<GlobalConfig> outcome = globalFile.load();
        global = outcome.value();
        if (outcome.status() != LoadOutcome.Status.LOADED && outcome.status() != LoadOutcome.Status.FRESH) {
            outcome.error().ifPresent(e -> log.log(e.code().name(), e.fieldPath()));
        }
        applyTheme();
    }

    public void loadProfile(ProfileIdentity identity, String dimensionId) {
        this.profileId = identity.profileId();
        JsonConfigFile<UiConfig> uiFile = new JsonConfigFile<>(paths.profileUiFile(profileId),
                new UiConfig.Codec(), clock);
        LoadOutcome<UiConfig> outcome = uiFile.load();
        this.ui = outcome.value();
        outcome.error().ifPresent(e -> log.log(e.code().name(), e.fieldPath()));

        JsonConfigFile<DestinationsConfig> destFile = new JsonConfigFile<>(paths.profileDestinationsFile(profileId),
                new DestinationsConfig.Codec(profileId), clock);
        this.destinations = destFile.load().value();

        JsonConfigFile<MarkerSet> markersFile = new JsonConfigFile<>(paths.profileMarkersFile(profileId),
                new MarkerSet.Codec(), clock);
        this.markers = markersFile.load().value();

        this.tileCache = new BoundedTileCache(ui.map().terrainCacheBytes());
        this.tileStore = new TileStore(paths.mapDir(identity.profileId()), clock);
        this.mapIdentity = new MapIdentity(identity.profileId(), dimensionId,
                MapIdentity.FORMAT_VERSION, ui.map().mode(), ui.map().sliceY());
        this.token = WorldSessionToken.mint(identity.profileId());
        startMapService();
        updateMediaCapability();
    }

    private void startMapService() {
        if (tileStore == null || mapIdentity == null) {
            return;
        }
        TileSamplerHolder holder = new TileSamplerHolder();
        this.map = new MapService(clock, tileCache, tileStore, executors.io(), holder.sampler(), tile -> {
            // Publication already filtered by token inside MapService.
        });
        this.map.onSessionChanged(token, mapIdentity);
        holder.bind(map);
    }

    private void updateMediaCapability() {
        Optional<ModuleRegistry.Entry> entry = modules.get("media.overlay");
        entry.ifPresent(e -> {
            if (media == null || media.status() == ProviderStatus.UNSUPPORTED) {
                e.markUnavailable(silkvis.core.ErrorCode.PLATFORM_UNSUPPORTED, "windows_gsmtc_only");
            } else {
                e.clearUnavailable();
            }
        });
    }

    private void applyTheme() {
        ThemeTokens base = ThemeTokens.graphiteTurquoise();
        if (!global.effectiveBlur()) {
            theme = base;
        }
        if (global.reducedMotion()) {
            theme = new ThemeTokens(base.id(), base.background(), base.surface(), base.surfaceHover(),
                    base.border(), base.accent(), base.accentHover(), base.textPrimary(), base.textSecondary(),
                    base.success(), base.warning(), base.error(), base.spacingXs(), base.spacingSm(),
                    base.spacingMd(), base.spacingLg(), base.spacingXl(), base.radiusPanel(),
                    base.radiusControl(), base.controlHeight(), base.minHitSize(), 0, base.tooltipDelayMs());
        }
    }

    public void requestGlobalSave(GlobalConfig updated) {
        this.global = updated;
        applyTheme();
        globalDebouncer.submit(updated);
    }

    public void requestUiSave(UiConfig updated) {
        this.ui = updated;
        saveUiAsync(updated);
    }

    private void saveGlobalAsync(GlobalConfig value) {
        executors.io().execute(() -> saveQuietly(globalFile, value));
    }

    private void saveUiAsync(UiConfig value) {
        if (profileId == null) {
            return;
        }
        JsonConfigFile<UiConfig> file = new JsonConfigFile<>(paths.profileUiFile(profileId),
                new UiConfig.Codec(), clock);
        executors.io().execute(() -> saveQuietly(file, value));
    }

    public void saveDestinations() {
        JsonConfigFile<DestinationsConfig> file = new JsonConfigFile<>(
                paths.profileDestinationsFile(profileId), new DestinationsConfig.Codec(profileId), clock);
        executors.io().execute(() -> saveQuietly(file, destinations));
    }

    public void saveMarkers() {
        JsonConfigFile<MarkerSet> file = new JsonConfigFile<>(paths.profileMarkersFile(profileId),
                new MarkerSet.Codec(), clock);
        executors.io().execute(() -> saveQuietly(file, markers));
    }

    private <T> void saveQuietly(JsonConfigFile<T> file, T value) {
        try {
            file.save(value);
        } catch (ConfigException | IOException e) {
            log.log("CONFIG_SAVE_FAILED", e.getClass().getSimpleName());
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /** Called once on the client tick; safe to call when no world is loaded. */
    public void tick() {
        globalDebouncer.tick();
        mediaPollLimiter.tryAcquire();
        if (media != null) {
            media.tick();
        }
        if (map != null) {
            map.tick();
            map.endTick();
        }
    }

    /** Dimension change inside the same world: map identity is rebuilt, token is kept. */
    public void switchDimension(String dimensionId) {
        if (mapIdentity != null && mapIdentity.dimensionId().equals(dimensionId)) {
            return;
        }
        this.mapIdentity = new MapIdentity(profileId, dimensionId, MapIdentity.FORMAT_VERSION,
                ui.map().mode(), ui.map().sliceY());
        if (map != null) {
            map.onSessionChanged(token, mapIdentity);
        }
    }

    public void onWorldLeft() {
        modules.disableAll();
        token = WorldSessionToken.none();
        if (map != null) {
            map.onSessionChanged(token, null);
        }
        if (media != null && global.media().pauseSourceOnWorldExit()) {
            media.command(silkvis.media.MediaAction.PAUSE);
        }
    }

    public void shutdown() {
        globalDebouncer.flush();
        modules.disableAll();
        if (media != null) {
            media.close();
        }
        executors.close();
    }

    public void startMediaIfEnabled(Supplier<MediaProvider> factory) {
        if (mediaStarted || !global.media().enabled()) {
            return;
        }
        MediaProvider provider;
        try {
            provider = factory.get();
        } catch (RuntimeException | LinkageError e) {
            log.log("PROVIDER_UNAVAILABLE", e.getClass().getSimpleName());
            return;
        }
        media = new MediaService(provider, clock, snapshot -> { }, result -> { });
        media.start(global.media().selection(), global.media().pinnedAppId());
        mediaStarted = true;
        updateMediaCapability();
    }

    // ---------------------------------------------------------------- accessors

    public Clock clock() {
        return clock;
    }

    public BoundedExecutors executors() {
        return executors;
    }

    public BoundedLog log() {
        return log;
    }

    public IntegrationRegistry integrations() {
        return integrations;
    }

    public ModuleRegistry modules() {
        return modules;
    }

    public MainScreenModel screenModel() {
        return screenModel;
    }

    public ThemeTokens theme() {
        return theme;
    }

    public GlobalConfig global() {
        return global;
    }

    public UiConfig ui() {
        return ui;
    }

    public HudLayoutConfig hudLayout() {
        return ui.hud();
    }

    public void setHudLayout(HudLayoutConfig layout) {
        requestUiSave(new UiConfig(layout, ui.map(), ui.visual()));
    }

    public DestinationsConfig destinations() {
        return destinations;
    }

    public void setDestinations(DestinationsConfig value) {
        this.destinations = value;
        saveDestinations();
    }

    public MarkerSet markers() {
        return markers;
    }

    public void setMarkers(MarkerSet value) {
        this.markers = value;
        saveMarkers();
    }

    public ProfileIdentity currentProfile() {
        return new ProfileIdentity("profile", profileId, profileId, profileId);
    }

    public WorldSessionToken token() {
        return token;
    }

    public Optional<MediaService> mediaService() {
        return Optional.ofNullable(media);
    }

    public Optional<MapService> mapService() {
        return Optional.ofNullable(map);
    }

    public Optional<MapIdentity> mapIdentity() {
        return Optional.ofNullable(mapIdentity);
    }

    public int queuedIo() {
        return executors.queuedIoTasks();
    }

    public boolean hudInteractionMode() {
        return hudInteractionMode;
    }

    public void setHudInteractionMode(boolean value) {
        this.hudInteractionMode = value;
    }

    public Localizer localizer() {
        return key -> net.minecraft.text.Text.translatable(key).getString();
    }

    /**
     * Binds the client-side sampler to the map service once the Minecraft adapter exists
     * (no game classes are referenced here).
     */
    private static final class TileSamplerHolder {
        private MapService service;
        private silkvis.map.TileSampler sampler;

        silkvis.map.TileSampler sampler() {
            return (key, revision) -> {
                if (sampler != null) {
                    return sampler.sample(key, revision);
                }
                return null;
            };
        }

        void bind(MapService service) {
            this.service = service;
            this.sampler = ClientTileSampler.create();
        }
    }
}

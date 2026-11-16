package silkvis.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import silkvis.assets.TotemDownloader;
import silkvis.automation.AutomationEngine;
import silkvis.automation.AutomationFile;
import silkvis.automation.AutomationSettings;
import silkvis.automation.BlockPos;
import silkvis.automation.GoalRegistry;
import silkvis.automation.GoalState;
import silkvis.automation.Snapshot;
import silkvis.automation.StopReason;
import silkvis.assets.TotemLibrary;
import silkvis.assets.TotemPackBuilder;
import silkvis.config.ConfigException;
import silkvis.config.ConfigPaths;
import silkvis.config.Debouncer;
import silkvis.config.GlobalConfig;
import silkvis.config.JsonConfigFile;
import silkvis.config.LoadOutcome;
import silkvis.config.MediaConfig;
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
import silkvis.ui.hud.HudWidgetConfig;
import silkvis.ui.hud.HudWidgetId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
public final class SilkVisRuntime implements AutomationHost {
    private static SilkVisRuntime instance;

    private final Clock clock = Clock.system();
    private final BoundedExecutors executors = BoundedExecutors.defaults();
    private final BoundedLog log = new BoundedLog(clock, 512, 5000);
    private final IntegrationRegistry integrations = new IntegrationRegistry();
    private final ModuleRegistry modules = new ModuleRegistry();
    private final ConfigPaths paths;
    private final JsonConfigFile<GlobalConfig> globalFile;
    private final Debouncer<GlobalConfig> globalDebouncer;
    /**
     * UI saves are debounced too: the HUD editor writes the layout on every mouse move, and
     * an unbounced save meant one file write per frame (0.1.15).
     */
    private final Debouncer<UiConfig> uiDebouncer;

    private final MainScreenModel screenModel;
    /** Automation: the engine and its own file, next to ui.json in the profile folder. */
    private final GoalRegistry automationGoals = new GoalRegistry();
    /**
     * Driver for the player's own Baritone install. Reflection-based, so nothing here fails to
     * link when Baritone is absent: the tab simply reports that it is not installed.
     */
    private final silkvis.client.baritone.BaritoneBridge baritone =
            new silkvis.client.baritone.BaritoneBridge();
    private final AutomationEngine automation;
    private AutomationFile automationFile;
    /** Steps are executed on the client thread only; set by the client entrypoint. */
    private AutomationEngine.Executor automationExecutor;
    /** Last stop reason that was written to the log, so one stop is logged once. */
    private String automationLoggedStop = "";
    private ThemeTokens theme = ThemeTokens.graphiteTurquoise();

    private GlobalConfig global = GlobalConfig.defaults();
    private UiConfig ui = UiConfig.defaults();
    private DestinationsConfig destinations = new DestinationsConfig("default", DestinationsConfig.defaultSlots());
    private MarkerSet markers = MarkerSet.empty();
    /** Empty unless a third-party map is installed (ADR-0007). */
    private String externalMapId = "";
    private String externalMapLabel = "";
    /** Why the media back end was refused, e.g. {@code powershell_missing}; empty if none. */
    private String mediaUnavailableReason = "";
    /** Custom totem (ASSET-04): drop folder, pack destination and the last result. */
    private Path totemSourceDir;
    private Path resourcePacksDir;
    private volatile List<TotemLibrary.Entry> totemEntries = List.of();
    private volatile String totemReason = "";
    private volatile String totemAppliedName = "";
    /** The entry that produced the current pack, so a rebuild does not have to re-resolve it. */
    private volatile TotemLibrary.Entry totemAppliedEntry;
    private volatile boolean totemBusy;
    /** Which "working" line to show: building a local file or downloading a link. */
    private volatile String totemBusyKey = "silkvis.totems.status.busy";
    /** Bumped after a background job so the open tab can rebuild its file list. */
    private volatile long totemVersion;
    /** Last link the player typed, kept for this session so it survives a tab rebuild. */
    private volatile String totemUrlDraft = "";
    /**
     * Called after a pack was written (on the IO thread). The client wires it to the resource
     * pack applier, which must run on the render thread; the runtime stays Minecraft-free.
     */
    private volatile Runnable totemPackHook;
    /** Outcome of the last "enable/disable the pack" click: a reason code, "" when none. */
    private volatile String totemPackNotice = "";
    /** SilkVis' own translation table for the configured language (see LangTable). */
    private silkvis.ui.LangTable langTable = silkvis.ui.LangTable.empty();

    private String profileId = "default";
    private WorldSessionToken token = WorldSessionToken.none();
    private MediaService media;
    private MapService map;
    private TileStore tileStore;
    private BoundedTileCache tileCache = new BoundedTileCache(64L * 1024 * 1024);
    private MapIdentity mapIdentity;
    private boolean mediaStarted;
    /** Built once at client init; lets the runtime start the bridge later, on demand. */
    private Supplier<MediaProvider> mediaFactory;
    /** Throttle for retries: a refused bridge must not spawn a process every tick. */
    private long mediaNextAttemptNanos;
    private static final long MEDIA_RETRY_MS = 15_000L;
    private boolean hudInteractionMode;
    /** True when {@code soupvisuals.json} existed and was applied over the defaults. */
    private boolean soupLoaded;

    private final RateLimiter mediaPollLimiter = new RateLimiter(clock, 1);
    /** Pseudo-profile for HUD/UI edits made before any world is loaded. */
    private static final String SHARED_UI_PROFILE = "defaults";

    private SilkVisRuntime() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path gameDir = FabricLoader.getInstance().getGameDir();
        this.paths = new ConfigPaths(configDir, gameDir.resolve("silkvis-data"));
        this.globalFile = new JsonConfigFile<>(paths.globalFile(), new GlobalConfig.Codec(), clock);
        this.globalDebouncer = new Debouncer<>(500, clock, this::saveGlobalAsync);
        this.uiDebouncer = new Debouncer<>(500, clock, this::saveUiAsync);
        this.screenModel = new MainScreenModel(modules);
        this.automation = new AutomationEngine(this.automationGoals, this.clock);
        registerModules();
        loadGlobal();
        // The ported modules keep their own file: it is a verbatim port of the original
        // settings tree, and mixing it into global.json would tie two unrelated schemas.
        this.soupLoaded = silkvis.visual.soup.setting.SoupSettingsFile.load(
                paths.soupFile(), SoupVisualsClient.get().modules());
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
        // The ten ported SoupVisuals modules were fully wired but missing from this list, so
        // the module page could not show them and there was no on/off switch anywhere except
        // the dedicated screen. They appear here in the same order as in the original and are
        // driven by the module objects themselves: no second source of truth for "is it on".
        registerSoupModules();
    }

    /**
     * Registers one descriptor per ported module. Everything (labels, defaults, order) comes
     * from {@link SoupVisualsClient#modules()}, so a newly ported module shows up in the
     * module list automatically.
     */
    private void registerSoupModules() {
        for (silkvis.visual.soup.module.SoupModule module : SoupVisualsClient.get().modules()) {
            String id = "soup." + silkvis.visual.soup.setting.SoupSettingsFile.keyOf(module);
            register(new ModuleDescriptor(id, ModuleCategory.VISUAL,
                    module.getNameKey(),
                    // Settings keys are {@code setting.<id>.<name>.name}, the module
                    // description the port shipped is {@code module.<id>.desc}.
                    "module." + silkvis.visual.soup.setting.SoupSettingsFile.keyOf(module) + ".desc", 1,
                    Set.of(Capability.CLIENT_RENDER), java.util.List.of()),
                    new ModuleRegistry.Module() {
                        @Override
                        public void enable() {
                            module.setEnabled(true);
                            saveSoupSettings();
                        }

                        @Override
                        public void disable() {
                            module.setEnabled(false);
                        }
                    });
        }
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
        if (outcome.status() == LoadOutcome.Status.FRESH) {
            // Fresh install: start in the language of the system, not in English. The user
            // asked for Russian and "the UI is half English" was the complaint.
            String detected = detectLanguage();
            if (!detected.equals(global.language())) {
                global = new GlobalConfig(detected, global.preset(), global.reducedMotion(),
                        global.blurEnabled(), global.verboseDiagnostics(), global.media());
            }
        }
        alignLanguageOnce();
        langTable = silkvis.ui.LangTable.load(effectiveLanguage());
        if (outcome.status() != LoadOutcome.Status.LOADED && outcome.status() != LoadOutcome.Status.FRESH) {
            outcome.error().ifPresent(e -> log.log(e.code().name(), e.fieldPath()));
        }
        applyTheme();
    }

    public void loadProfile(ProfileIdentity identity, String dimensionId) {
        // Pending UI edits belong to the profile that is being left.
        uiDebouncer.flush();
        this.profileId = identity.profileId();
        JsonConfigFile<UiConfig> uiFile = new JsonConfigFile<>(paths.profileUiFile(profileId),
                new UiConfig.Codec(), clock);
        LoadOutcome<UiConfig> outcome = uiFile.load();
        if (outcome.status() == LoadOutcome.Status.FRESH && !SHARED_UI_PROFILE.equals(profileId)) {
            // First visit: start from the layout edited in the main menu, if there is one.
            JsonConfigFile<UiConfig> shared = new JsonConfigFile<>(
                    paths.profileUiFile(SHARED_UI_PROFILE), new UiConfig.Codec(), clock);
            LoadOutcome<UiConfig> base = shared.load();
            if (base.status() == LoadOutcome.Status.LOADED) {
                outcome = base;
            }
        }
        this.ui = outcome.value();
        outcome.error().ifPresent(e -> log.log(e.code().name(), e.fieldPath()));

        JsonConfigFile<DestinationsConfig> destFile = new JsonConfigFile<>(paths.profileDestinationsFile(profileId),
                new DestinationsConfig.Codec(profileId), clock);
        this.destinations = destFile.load().value();

        JsonConfigFile<MarkerSet> markersFile = new JsonConfigFile<>(paths.profileMarkersFile(profileId),
                new MarkerSet.Codec(), clock);
        this.markers = markersFile.load().value();

        this.automationFile = new AutomationFile(paths.profileAutomationFile(profileId), clock);
        AutomationSettings loadedAutomation = automationFile.load();
        this.automation.applySettings(loadedAutomation);
        this.ui = new UiConfig(ui.hud(), ui.map(), ui.visual(), loadedAutomation);
        this.automationLoggedStop = "";

        this.tileCache = new BoundedTileCache(ui.map().terrainCacheBytes());
        this.tileStore = new TileStore(paths.mapDir(identity.profileId()), clock);
        this.mapIdentity = new MapIdentity(identity.profileId(), dimensionId,
                MapIdentity.FORMAT_VERSION, ui.map().mode(), ui.map().sliceY());
        this.token = WorldSessionToken.mint(identity.profileId());
        startMapService();
        enforceExternalMapPolicy();
        updateMediaCapability();
    }

    /**
     * Re-applies the ADR-0007 decision after a profile is loaded. Necessary because the
     * policy first runs during client init, when there is no profile yet and therefore no
     * {@code hud.json}; a minimap that the user enabled earlier would otherwise come back
     * and paint over the external map.
     */
    private void enforceExternalMapPolicy() {
        if (externalMapId.isEmpty()) {
            return;
        }
        // SilkVis has no minimap of its own since 0.1.14: there is nothing left to switch
        // off, only the tile pipeline to pause while an external map owns the screen.
        if (map != null) {
            map.setPaused(true);
        }
    }

    private void startMapService() {
        if (tileStore == null || mapIdentity == null) {
            return;
        }
        if (!externalMapId.isEmpty()) {
            // The external map owns terrain on this client: do not build a single tile.
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
        // Visual presets are density presets: LOW packs the interface, QUALITY gives it air.
        ThemeTokens preset = switch (global.preset()) {
            case LOW -> base.withDensity(2, 2, -2);
            case BALANCED -> base;
            case QUALITY -> base.withDensity(8, 10, 2);
        };
        theme = global.reducedMotion() ? preset.withMotion(0) : preset;
    }

    public void requestGlobalSave(GlobalConfig updated) {
        if (!updated.language().equals(global.language())) {
            langTable = silkvis.ui.LangTable.load(
                    "auto".equals(updated.language()) ? detectLanguage() : updated.language());
        }
        MediaConfig previousMedia = this.global.media();
        this.global = updated;
        applyTheme();
        applyMediaSettings(previousMedia, updated.media());
        globalDebouncer.submit(updated);
    }

    public void requestUiSave(UiConfig updated) {
        this.ui = updated;
        uiDebouncer.submit(updated);
    }

    private void saveGlobalAsync(GlobalConfig value) {
        executors.io().execute(() -> saveQuietly(globalFile, value));
    }

    /**
     * Edits made from the main menu have no profile yet; they used to be dropped without a
     * word. They now go to a shared file that every new profile inherits on first join.
     */
    private void saveUiAsync(UiConfig value) {
        String id = profileId == null ? SHARED_UI_PROFILE : profileId;
        JsonConfigFile<UiConfig> file = new JsonConfigFile<>(paths.profileUiFile(id),
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
        uiDebouncer.tick();
        // Retry with a throttle: a refused bridge keeps its reason visible in the Music tab
        // and the HUD instead of silently doing nothing until the next launch.
        tryStartMedia();
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

    /**
     * One line with what this build actually loaded: the module count, how many ported modules
     * are on, and where their config lives. Logged at start-up, because "the fix did nothing"
     * is almost always the previous jar still sitting in {@code mods/} after a failed build.
     */
    public void logStartupSummary() {
        int soupOn = 0;
        for (silkvis.visual.soup.module.SoupModule module : SoupVisualsClient.get().modules()) {
            if (module.isEnabled()) {
                soupOn++;
            }
        }
        String summary = "modules=" + modules.all().size()
                + " soupvisuals=" + soupOn + "/" + SoupVisualsClient.get().modules().size()
                + " soupfile=" + paths.soupFile();
        log.event("STARTUP", summary);
        // Also on stdout: the diagnostics log is only visible inside the mod, and this line is
        // what a player is asked to check in latest.log when something looks stale.
        java.util.logging.Logger.getLogger("silkvis").info("SilkVis start-up: " + summary);
    }

    /**
     * Turns the modules the player had switched on back on at start-up.
     *
     * <p>It has to happen here and not only in the screen: modules are the thing that draws, and
     * "the effect does not work after a restart" is exactly what happens when only the config is
     * read and the registry is left thinking everything is off. Also re-runs the mod load, so a
     * jar swapped under the same mods folder picks up the change.</p>
     */
    public void startSoupModules() {
        for (silkvis.visual.soup.module.SoupModule module : SoupVisualsClient.get().modules()) {
            if (module.isEnabled()) {
                module.setEnabled(true);
                log.event("SOUP_MODULE", "enabled at start: " + module.getNameKey());
            }
        }
    }

    /**
     * Pushes the state loaded from {@code soupvisuals.json} into the module registry, so the
     * list and the module objects agree from the first frame. Registered modules are the only
     * ones the list can switch, so an unregistered id is skipped instead of throwing.
     */
    public void syncSoupModulesIntoRegistry() {
        for (silkvis.visual.soup.module.SoupModule module : SoupVisualsClient.get().modules()) {
            String id = soupModuleId(module);
            if (module.isEnabled() && modules.get(id).isPresent()) {
                modules.enable(id);
            }
        }
    }

    /** Registry id of a ported module ({@code soup.hitbubbles}). */
    public static String soupModuleId(silkvis.visual.soup.module.SoupModule module) {
        return "soup." + silkvis.visual.soup.setting.SoupSettingsFile.keyOf(module);
    }

    public void onWorldLeft() {
        // disableAll() is about the world session, not about the player's choice: the ported
        // modules that were on are switched back on after the world state is dropped, exactly
        // like the original (which keeps its module switches across worlds). Without this a
        // portal or a reconnect silently turned every visual module off.
        java.util.List<String> soupOn = new java.util.ArrayList<>();
        for (silkvis.visual.soup.module.SoupModule module : SoupVisualsClient.get().modules()) {
            if (module.isEnabled()) {
                soupOn.add(soupModuleId(module));
            }
        }
        modules.disableAll();
        token = WorldSessionToken.none();
        if (map != null) {
            map.onSessionChanged(token, null);
        }
        if (media != null && global.media().pauseSourceOnWorldExit()) {
            media.command(silkvis.media.MediaAction.PAUSE);
        }
        for (String id : soupOn) {
            modules.enable(id);
        }
    }

    public void shutdown() {
        globalDebouncer.flush();
        uiDebouncer.flush();
        saveSoupSettings();
        modules.disableAll();
        if (media != null) {
            media.close();
        }
        executors.close();
    }

    /**
     * Registers the bridge factory. Separate from starting on purpose: the tile/media
     * adoption order at client init must not be able to throw before the factory exists.
     */
    public void setMediaFactory(Supplier<MediaProvider> factory) {
        this.mediaFactory = factory;
        this.mediaNextAttemptNanos = 0L;
    }

    /**
     * Starts the bridge if music is enabled. Called at client init and every tick, so
     * enabling music in the Music tab takes effect immediately instead of after a restart
     * (that was the whole "музыка не робит" report: the provider was created once at
     * launch, when the flag was still off).
     */
    public void startMediaIfEnabled() {
        tryStartMedia();
    }

    public void setMediaEnabled(boolean enabled) {
        if (!enabled) {
            stopMedia();
            return;
        }
        mediaNextAttemptNanos = 0L;
        tryStartMedia();
    }

    /** Music tab button: drop the current bridge and build a new one right now. */
    public void restartMedia() {
        stopMedia();
        mediaNextAttemptNanos = 0L;
        tryStartMedia();
        log.log("MEDIA_RESTART", global.media().enabled() ? "enabled" : "disabled");
    }

    /** Last refusal reason; empty when the bridge started or music is off. */
    public String mediaUnavailableReason() {
        return mediaUnavailableReason;
    }

    private void stopMedia() {
        MediaService current = media;
        media = null;
        mediaStarted = false;
        mediaUnavailableReason = "";
        if (current != null) {
            // close() waits for the child process: never on the render/UI thread.
            executors.io().execute(current::close);
            log.log("MEDIA_STOPPED", "user");
        }
        updateMediaCapability();
    }

    private void tryStartMedia() {
        if (mediaStarted || mediaFactory == null || !global.media().enabled()) {
            return;
        }
        long now = clock.monotonicNanos();
        if (now < mediaNextAttemptNanos) {
            return;
        }
        mediaNextAttemptNanos = now + MEDIA_RETRY_MS * 1_000_000L;
        MediaProvider provider;
        try {
            provider = mediaFactory.get();
        } catch (RuntimeException | LinkageError e) {
            // Keep the reason: "music does nothing" is not an acceptable status message.
            String reason = e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage();
            if (!reason.equals(mediaUnavailableReason)) {
                log.log("PROVIDER_UNAVAILABLE", reason);
            }
            mediaUnavailableReason = reason;
            return;
        }
        MediaService service = new MediaService(provider, clock, snapshot -> { }, result -> { });
        service.start(global.media().selection(), global.media().pinnedAppId());
        media = service;
        mediaStarted = true;
        mediaUnavailableReason = "";
        log.log("MEDIA_STARTED", provider.id());
        updateMediaCapability();
    }

    /**
     * Applies media settings that used to be written to the config and never read: the
     * enable flag (no start/stop), the selection and the pinned app (no re-subscribe).
     */
    private void applyMediaSettings(MediaConfig before, MediaConfig after) {
        if (before.enabled() != after.enabled()) {
            setMediaEnabled(after.enabled());
        }
        if (before.selection() != after.selection()
                || !before.pinnedAppId().equals(after.pinnedAppId())) {
            mediaService().ifPresent(service ->
                    service.setSelection(after.selection(), after.pinnedAppId()));
        }
    }

    // ---------------------------------------------------------------- totems

    /** Called once at client init with the instance paths. */
    public void setTotemPaths(Path sourceDir, Path resourcePacksDir) {
        this.totemSourceDir = sourceDir;
        this.resourcePacksDir = resourcePacksDir;
        refreshTotems();
    }

    /** Re-lists the drop folder; also called by the refresh button on the tab. */
    public void refreshTotems() {
        if (totemSourceDir == null) {
            totemEntries = List.of();
            return;
        }
        TotemLibrary.ensureDirectory(totemSourceDir);
        totemEntries = TotemLibrary.scan(totemSourceDir);
    }

    public List<TotemLibrary.Entry> totemEntries() {
        return totemEntries;
    }

    public Path totemSourceDir() {
        return totemSourceDir;
    }

    /**
     * Builds the resource pack off the client thread: decoding a large picture must never
     * stall a frame, and the pack is plain files on disk (no mixin, no texture injection).
     * The edge length comes from the saved setting ({@code visual.totemTextureSize}).
     */
    public void applyTotem(TotemLibrary.Entry entry) {
        if (entry == null || resourcePacksDir == null) {
            return;
        }
        totemBusy = true;
        totemBusyKey = "silkvis.totems.status.busy";
        totemReason = "";
        totemAppliedName = entry.displayName();
        totemAppliedEntry = entry;
        executors.io().execute(() -> {
            TotemPackBuilder.Result result = TotemPackBuilder.apply(entry.path(), resourcePacksDir,
                    ui().visual().totemTextureSize(), ui().visual().totemTrimBackground());
            totemReason = result.reason();
            totemBusy = false;
            log.event("TOTEM_PACK", entry.name() + " size=" + ui().visual().totemTextureSize()
                    + " trim=" + ui().visual().totemTrimBackground() + " reason=" + result.reason()
                    + " src=" + result.sourceWidth() + "x" + result.sourceHeight()
                    + " -> " + totemAppliedSize(result));
            if (result.ok()) {
                notifyTotemPack();
            }
        });
    }

    /** Size of the texture that was actually written, read back from disk. */
    private static String totemAppliedSize(TotemPackBuilder.Result result) {
        if (result == null || !result.ok() || result.texture() == null) {
            return "no texture";
        }
        try {
            java.awt.image.BufferedImage written =
                    javax.imageio.ImageIO.read(result.texture().toFile());
            return written == null ? "unreadable"
                    : written.getWidth() + "x" + written.getHeight();
        } catch (java.io.IOException e) {
            return "unreadable";
        }
    }

    /**
     * Rebuilds the generated pack from the entry that was applied last, so a change of the
     * texture size or of the background trimming takes effect on the picture already in use
     * instead of waiting for the player to pick it again.
     */
    public void rebuildTotemWithCurrentSize() {
        TotemLibrary.Entry entry = totemAppliedEntry;
        if (entry == null || !java.nio.file.Files.isRegularFile(entry.path())) {
            return;
        }
        applyTotem(entry);
    }

    public long totemVersion() {
        return totemVersion;
    }

    public void setTotemPackHook(Runnable hook) {
        this.totemPackHook = hook;
    }

    /** Records the outcome of a manual enable/disable so the tab can state it in words. */
    public void setTotemPackResult(boolean enable, String result) {
        totemPackNotice = TotemPackApplier.OK.equals(result)
                ? (enable ? "enabled" : "disabled") : result;
        totemVersion++;
        log.log(enable ? "TOTEM_PACK_APPLY" : "TOTEM_PACK_DISABLE", result);
    }

    /** One line under the buttons; empty when nothing has been switched yet. */
    public String totemPackLine() {
        if (totemPackNotice.isEmpty()) {
            return "";
        }
        String key = "silkvis.totems.packresult." + totemPackNotice;
        String text = localizer().get(key);
        return text.equals(key) ? totemPackNotice : text;
    }

    /** Runs the pack hook if one is wired; a failure here must not break the build task. */
    private void notifyTotemPack() {
        Runnable hook = totemPackHook;
        if (hook == null) {
            return;
        }
        try {
            hook.run();
        } catch (RuntimeException e) {
            log.log("TOTEM_PACK_HOOK_FAILED", e.getClass().getSimpleName());
        }
    }

    public String totemUrlDraft() {
        return totemUrlDraft;
    }

    /**
     * Downloads the picture behind a link, then builds the pack from it. The URL is not
     * trusted: only http/https, a size cap and a timeout, and the bytes still have to decode
     * as an image before anything reaches the resource pack.
     */
    public void downloadTotem(String url) {
        if (url == null || url.isBlank() || totemSourceDir == null || resourcePacksDir == null) {
            return;
        }
        String link = url.trim();
        totemUrlDraft = link;
        totemBusy = true;
        totemBusyKey = "silkvis.totems.status.downloading";
        totemReason = "";
        totemAppliedName = link;
        executors.io().execute(() -> {
            TotemDownloader.Downloaded download = TotemDownloader.download(link, totemSourceDir);
            if (!download.ok()) {
                totemReason = download.reason();
                totemBusy = false;
                totemVersion++;
                log.log("TOTEM_URL", download.reason());
                return;
            }
            refreshTotems();
            TotemPackBuilder.Result pack = TotemPackBuilder.apply(download.file(), resourcePacksDir,
                    ui().visual().totemTextureSize(), ui().visual().totemTrimBackground());
            totemAppliedName = download.file().getFileName().toString();
            totemReason = pack.reason();
            totemBusy = false;
            totemVersion++;
            log.log("TOTEM_PACK", totemAppliedName + ":" + TotemPackBuilder.describe(pack));
            if (pack.ok()) {
                notifyTotemPack();
            } else {
                // A link to an HTML page leaves a useless ".png" behind: drop the file the
                // mod itself created, but never a picture that merely failed to copy.
                boolean sourceIsBad = "image_unreadable".equals(pack.reason())
                        || "image_too_large".equals(pack.reason())
                        || "file_too_large".equals(pack.reason());
                if (sourceIsBad) {
                    try {
                        java.nio.file.Files.deleteIfExists(download.file());
                        refreshTotems();
                    } catch (java.io.IOException ignored) {
                        // Best effort: a leftover file is not worth an error path.
                    }
                }
            }
        });
    }

    /** One localized line for the tab; reason codes never reach the screen. */
    public String totemStatusLine() {
        Localizer l = localizer();
        if (totemBusy) {
            return l.get(totemBusyKey);
        }
        if (totemReason.isEmpty()) {
            return l.get("silkvis.totems.status.none");
        }
        if ("ok".equals(totemReason)) {
            return l.get("silkvis.totems.status.ok", totemAppliedName);
        }
        String key = "silkvis.totems.reason." + totemReason;
        String text = l.get(key);
        return l.get("silkvis.totems.status.failed", text.equals(key) ? totemReason : text);
    }

    // ---------------------------------------------------------------- accessors


    // ---------------------------------------------------------------- automation

    public AutomationEngine automation() {
        return this.automation;
    }

    public GoalRegistry automationGoals() {
        return this.automationGoals;
    }

    /**
     * Reads the game into the planner's snapshot, or returns null when there is nothing to read.
     *
     * <p>Deliberately here and not in the client entrypoint: {@code AutomationSelector} needs the
     * same capture, and two captures would be two lists of entities per tick. Only this method
     * references the Minecraft adapter, and it is called from the tick loop, never from a screen.</p>
     */
    /** The Baritone bridge; the automation tab and the HUD status widget read it. */
    public silkvis.client.baritone.BaritoneBridge baritone() {
        return this.baritone;
    }

    public Snapshot automationSnapshot(MinecraftClient client) {
        if (client == null) {
            return null;
        }
        long tick = client.world == null ? 0L : client.world.getTime();
        return AutomationSnapshot.capture(client, tick, this.automation.settings().reachRadius());
    }

    /** The block the run would aim at: what the camera looks at, else the block under the feet. */
    public BlockPos automationTarget(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return GoalRegistry.NO_TARGET;
        }
        var eye = client.player.getEyePos();
        java.util.Optional<BlockPos> looked = silkvis.automation.Aim.firstSolid(
                eye.x, eye.y, eye.z, client.player.getYaw(), client.player.getPitch(), 5.0,
                cell -> !client.world.getBlockState(
                        new net.minecraft.util.math.BlockPos(cell.x(), cell.y(), cell.z())).isAir());
        if (looked.isPresent()) {
            return looked.get();
        }
        net.minecraft.util.math.BlockPos feet = client.player.getBlockPos();
        return new BlockPos(feet.getX(), feet.getY(), feet.getZ());
    }

    /** Wired by the client entrypoint to the only class that may press keys. */
    public void setAutomationExecutor(AutomationEngine.Executor executor) {
        this.automationExecutor = executor;
    }

    public AutomationEngine.Executor automationExecutor() {
        return this.automationExecutor;
    }

    /**
     * Applies new automation settings and saves them next to the profile's {@code ui.json}.
     *
     * <p>Saving is immediate and not debounced like the HUD layout: the automation switches are
     * the thing a player changes in a hurry, and a switch that is still in memory when the game
     * closes is exactly the "I turned it off and it was on again" bug.</p>
     */
    public void setAutomationSettings(AutomationSettings next) {
        AutomationSettings applied = next == null ? AutomationSettings.defaults() : next;
        this.automation.applySettings(applied);
        this.ui = new UiConfig(this.ui.hud(), this.ui.map(), this.ui.visual(), applied);
        if (this.automationFile != null) {
            try {
                this.automationFile.save(applied);
            } catch (ConfigException | IOException e) {
                this.log.log("AUTOMATION_SAVE_FAILED", e.getClass().getSimpleName());
            }
        }
        this.log.event("AUTOMATION_SETTINGS", applied.mode().name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Starts a goal by catalogue id on the block the player is looking at (or the player). */
    public boolean startAutomation(String id, BlockPos target) {
        boolean started = this.automation.start(id, target);
        this.automationLoggedStop = "";
        this.log.event("AUTOMATION_START", id + (started ? "" : " refused"));
        return started;
    }

    /** The panic path: the screen button, the keybind and the safety gate all end up here. */
    public void stopAutomation(StopReason why) {
        this.automation.stop(why == null ? StopReason.BY_PLAYER : why);
        if (this.automationExecutor != null) {
            this.automationExecutor.abort();
        }
        this.logStop(why == null ? StopReason.BY_PLAYER : why);
    }

    /**
     * Logs a stop exactly once. The engine keeps its last reason as status, so a screen rebuild or
     * a second query would otherwise write the same line on every tick.
     */
    private void logStop(StopReason why) {
        String key = why.code() + "/" + this.automation.status().goalId();
        if (key.equals(this.automationLoggedStop)) {
            return;
        }
        this.automationLoggedStop = key;
        this.log.event("AUTOMATION_STOP", why.code());
    }

    /**
     * One automation tick. Kept out of the client entrypoint so the entrypoint stays a thin
     * composition layer and so the tick has exactly one input: the current game state.
     *
     * @param playerBusy true when the player is holding a movement key or the mouse
     */
    public void tickAutomation(Snapshot snapshot, boolean playerBusy) {
        AutomationSettings settings = this.automation.settings();
        if (!settings.enabled()) {
            return;
        }
        this.automation.tick(snapshot, this.automationExecutor, playerBusy);
        if (!this.automation.active()) {
            StopReason reason = this.automation.status().reason();
            if (reason != StopReason.NONE) {
                this.logStop(reason);
            }
            if (this.automationExecutor != null) {
                this.automationExecutor.abort();
            }
        }
    }

    /** One line for the HUD and the tab: what is running, what step, why it stopped. */
    public String automationStatusLine() {
        AutomationEngine.Status status = this.automation.status();
        if (status.state() == GoalState.IDLE && status.reason() == StopReason.NONE) {
            return "";
        }
        String goal = status.goalId().isEmpty() ? "-" : status.goalId();
        String step = status.step().isEmpty() ? "-" : status.step();
        String reason = status.reason() == StopReason.NONE ? "" : "  " + status.reason().code();
        String progress = status.progress().isEmpty() ? "" : "  " + status.progress();
        return goal + "  " + step + progress + reason;
    }

    public Clock clock() {
        return clock;
    }

    /** Paths of this profile's config/data files; used by the bug report and diagnostics. */
    public ConfigPaths paths() {
        return paths;
    }

    /**
     * Persists the ported module states and values. Called on shutdown and after every change
     * made in {@link SoupSettingsScreen}; a failed write is logged, never thrown, because
     * losing a config write must not take the game down.
     */
    public void saveSoupSettings() {
        try {
            silkvis.visual.soup.setting.SoupSettingsFile.save(
                    paths.soupFile(), SoupVisualsClient.get().modules());
        } catch (java.io.IOException e) {
            // BoundedLog takes a code and a detail, not a log level.
            log.log("SOUP_CONFIG", "not saved: " + e.getMessage());
        }
    }

    /** Whether {@code soupvisuals.json} was present at start-up; shown in the settings screen. */
    public boolean soupSettingsLoaded() {
        return soupLoaded;
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

    /**
     * ADR-0007: AtlasMap / AtlasMapExtended wins over our own map.
     *
     * <p>SilkVis then stops sampling terrain (a whole pipeline whose output nothing draws)
     * and switches its minimap widget off once, because two minimaps overlap and the
     * external one is the better map. Waypoints and the rest of the HUD are untouched.</p>
     */
    public void applyExternalMapPolicy(ExternalMapIntegration.Info info) {
        boolean present = info != null;
        externalMapId = present ? info.modId() : "";
        externalMapLabel = present ? info.describe() : "";
        modules.get("map.core").ifPresent(entry -> {
            if (present) {
                entry.markUnavailable(silkvis.core.ErrorCode.INTEGRATION_CONFLICT,
                        "external_map:" + info.modId());
            } else {
                entry.clearUnavailable();
            }
        });
        if (!present) {
            return;
        }
        mapService().ifPresent(service -> service.setPaused(true));
    }

    /**
     * Before 0.1.12 the language field was written to the config and never read: the UI always
     * followed Minecraft's own language. Such a stored value is not a choice, so it is aligned
     * with the system language exactly once, guarded by a marker file.
     */
    private void alignLanguageOnce() {
        try {
            Path marker = paths.languageAlignedMarker();
            if (java.nio.file.Files.exists(marker)) {
                return;
            }
            String detected = detectLanguage();
            if (!"auto".equals(global.language()) && !detected.equals(global.language())) {
                global = new GlobalConfig(detected, global.preset(), global.reducedMotion(),
                        global.blurEnabled(), global.verboseDiagnostics(), global.media());
                saveGlobalQuietly();
                log.log("LANGUAGE_ALIGNED", detected);
            }
            if (marker.getParent() != null) {
                java.nio.file.Files.createDirectories(marker.getParent());
            }
            java.nio.file.Files.writeString(marker, "v1");
        } catch (java.io.IOException | RuntimeException e) {
            log.log("LANGUAGE_ALIGN_MARKER_FAILED", e.getClass().getSimpleName());
        }
    }

    /** Language actually used by the UI: {@code auto} resolves against the OS locale. */
    public String effectiveLanguage() {
        String configured = global.language();
        return "auto".equals(configured) ? detectLanguage() : configured;
    }

    private void saveGlobalQuietly() {
        try {
            globalFile.save(global);
        } catch (ConfigException | IOException e) {
            log.log("CONFIG_SAVE_FAILED", e.getClass().getSimpleName());
        }
    }

    /** Bottleneck-free language detection: system locale first, never the game object. */
    public static String detectLanguage() {
        try {
            String tag = java.util.Locale.getDefault().getLanguage();
            return "ru".equalsIgnoreCase(tag) ? "ru" : "en";
        } catch (RuntimeException e) {
            return "en";
        }
    }

    /** Applies a language choice from the settings tab and reloads the table immediately. */
    public void setLanguage(String language) {
        langTable = silkvis.ui.LangTable.load("auto".equals(language) ? detectLanguage() : language);
        requestGlobalSave(new GlobalConfig(language, global.preset(), global.reducedMotion(),
                global.blurEnabled(), global.verboseDiagnostics(), global.media()));
    }

    /** Human-readable dimension name for the map header and the HUD. */
    public String dimensionLabel(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "—";
        }
        String code = dimensionId.substring(dimensionId.indexOf(':') + 1);
        String key = "silkvis.dimension." + code;
        String label = localizer().get(key);
        return label.equals(key) ? code : label;
    }

    /**
     * One-line media state for the Music tab: provider status and, when the provider never
     * started, the reason it was refused (helper_missing, powershell_missing, ...).
     */
    public String mediaStatusLine() {
        Localizer l = localizer();
        return mediaService()
                .map(service -> {
                    String status = localizedStatus(l, service.status());
                    String reason = service.statusReason();
                    return reason.isEmpty() ? status
                            : status + " · " + localizedReason(l, reason);
                })
                .orElseGet(() -> {
                    if (!mediaUnavailableReason.isEmpty()) {
                        return l.get("silkvis.media.state.unavailable") + " · "
                                + localizedReason(l, mediaUnavailableReason);
                    }
                    return global.media().enabled()
                            ? l.get("silkvis.media.state.not_started")
                            : l.get("silkvis.media.state.disabled");
                });
    }

    private static String localizedStatus(Localizer localizer, ProviderStatus status) {
        String key = "silkvis.media.status." + status.name().toLowerCase(java.util.Locale.ROOT);
        String text = localizer.get(key);
        return text.equals(key) ? status.name() : text;
    }

    /** Reason codes are ours, so they have translations; unknown codes are shown verbatim. */
    public static String localizedReason(Localizer localizer, String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        String code = reason.trim().toLowerCase(java.util.Locale.ROOT);
        String key = "silkvis.media.reason." + code;
        String text = localizer.get(key);
        return text.equals(key) ? reason : text;
    }

    public boolean externalMapPresent() {
        return !externalMapId.isEmpty();
    }

    /** Display string of the detected external map, or empty. */
    public String externalMapLabel() {
        return externalMapLabel;
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

    /**
     * Resolves keys against the language chosen in the settings tab, falling back to the
     * game language. Before this, "Язык" only wrote a string into the config: the UI always
     * followed Minecraft's language, so switching it changed nothing.
     */
    public Localizer localizer() {
        return new Localizer() {
            @Override
            public String get(String key) {
                String own = langTable.get(key);
                if (own != null && !own.isBlank()) {
                    return own;
                }
                return net.minecraft.text.Text.translatable(key).getString();
            }
        };
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

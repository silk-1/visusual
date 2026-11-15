package silkvis.client;

import net.fabricmc.loader.api.FabricLoader;
import silkvis.assets.TotemDownloader;
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
    /**
     * UI saves are debounced too: the HUD editor writes the layout on every mouse move, and
     * an unbounced save meant one file write per frame (0.1.15).
     */
    private final Debouncer<UiConfig> uiDebouncer;

    private final MainScreenModel screenModel;
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
        uiDebouncer.flush();
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
     * Builds the resource pack off the client thread: decoding a 4 MB picture must never
     * stall a frame, and the pack is plain files on disk (no mixin, no texture injection).
     */
    public void applyTotem(TotemLibrary.Entry entry) {
        if (entry == null || resourcePacksDir == null) {
            return;
        }
        totemBusy = true;
        totemBusyKey = "silkvis.totems.status.busy";
        totemReason = "";
        totemAppliedName = entry.displayName();
        executors.io().execute(() -> {
            TotemPackBuilder.Result result = TotemPackBuilder.apply(entry.path(), resourcePacksDir);
            totemReason = result.reason();
            totemBusy = false;
            log.log("TOTEM_PACK", entry.name() + ":" + TotemPackBuilder.describe(result));
            if (result.ok()) {
                notifyTotemPack();
            }
        });
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
            TotemPackBuilder.Result pack = TotemPackBuilder.apply(download.file(), resourcePacksDir);
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

    public Clock clock() {
        return clock;
    }

    /** Paths of this profile's config/data files; used by the bug report and diagnostics. */
    public ConfigPaths paths() {
        return paths;
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

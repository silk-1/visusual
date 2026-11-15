package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import silkvis.assets.TotemDownloader;
import silkvis.automation.AutomationCatalog;
import silkvis.automation.AutomationMode;
import silkvis.automation.AutomationSettings;
import silkvis.automation.BlockPos;
import silkvis.automation.GoalRegistry;
import silkvis.automation.StopReason;
import silkvis.assets.TotemLibrary;
import silkvis.assets.TotemPackBuilder;
import silkvis.config.GlobalConfig;
import silkvis.config.VisualPreset;
import silkvis.core.ModuleCategory;
import silkvis.core.ModuleDescriptor;
import silkvis.core.ModuleRegistry;
import silkvis.map.MapSettings;
import silkvis.media.MediaAction;
import silkvis.media.MediaCommandResult;
import silkvis.media.MediaSnapshot;
import silkvis.media.MediaTimeline;
import silkvis.render.VanillaPainter;
import silkvis.ui.Animation;
import silkvis.ui.Color;
import silkvis.ui.FocusManager;
import silkvis.ui.Insets;
import silkvis.ui.Localizer;
import silkvis.ui.MainScreenLayout;
import silkvis.ui.MainScreenModel;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ScrollModel;
import silkvis.ui.ThemeTokens;
import silkvis.ui.TooltipController;
import silkvis.ui.hud.HudLayoutConfig;
import silkvis.ui.hud.HudWidgetConfig;
import silkvis.ui.hud.HudWidgetId;
import silkvis.ui.widget.ButtonWidget;
import silkvis.ui.widget.DynamicTextWidget;
import silkvis.ui.widget.Keys;
import silkvis.ui.widget.SliderWidget;
import silkvis.ui.widget.TextInputModel;
import silkvis.ui.widget.TextInputWidget;
import silkvis.ui.widget.ToggleWidget;
import silkvis.ui.widget.Widget;
import silkvis.visual.VisualSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main SilkVis window (UI-03..UI-06).
 *
 * <p>The screen only reads service models and dispatches user intent; it owns no game
 * logic. Layout is recomputed in {@link #init()} so a resize or GUI-scale change always
 * re-clamps everything, and the footer/close button stay reachable on small screens.</p>
 */
public final class SilkVisScreen extends Screen {
    private static final int ROW_HEIGHT = 22;

    private final SilkVisRuntime runtime = SilkVisRuntime.get();
    private final FocusManager focus = new FocusManager();
    private final TooltipController tooltips = new TooltipController(runtime.clock(), 350);
    /** Recreated in init(): a ScrollModel bakes its viewport height at construction. */
    private ScrollModel listScroll = new ScrollModel(0, 24);
    private ScrollModel settingsScroll = new ScrollModel(0, 28);
    private final List<Widget> chrome = new ArrayList<>();
    private final List<Widget> settingsWidgets = new ArrayList<>();
    private final List<ButtonWidget> navButtons = new ArrayList<>();

    /** Fade-in of the backdrop only: panels appearing from zero look like a glitch. */
    private final long openedAtNanos = System.nanoTime();
    private MainScreenLayout layout;
    private TextInputWidget search;
    /** Link field of the Totems tab; kept across rebuilds so typing survives a scroll tick. */
    private TextInputModel totemUrlModel;
    private long totemVersionSeen;
    private int hoveredRow = -1;

    public SilkVisScreen() {
        super(Text.translatable("silkvis.screen.title"));
    }

    private Localizer localizer() {
        // Was Text.translatable directly, which ignored the language setting entirely.
        return runtime.localizer();
    }


    /**
     * The Automation tab: mode, the safety numbers, the goal list and the run controls.
     *
     * <p>Everything here writes through {@link SilkVisRuntime#setAutomationSettings}, which applies
     * and saves in one step, so the switch the player sees and the switch the engine reads cannot
     * drift. Goals that exist in the catalogue but have no implementation are listed with the
     * "planned" note and no button, which is the honest version of a planned tab.</p>
     */
    private int buildAutomationTab(Rect area, int y, int width) {
        SilkVisRuntime runtime = SilkVisRuntime.get();
        AutomationSettings settings = runtime.automation().settings();

        // Mode first: it is the one switch that decides whether anything at all may be pressed.
        settingsWidgets.add(new FixedText(tr("silkvis.automation.mode"),
                new Rect(area.x(), y, width, 12)));
        y += 14;
        AutomationMode[] modes = AutomationMode.values();
        int modeWidth = Math.max(40, Math.min(width, 320) / modes.length - 4);
        for (int i = 0; i < modes.length; i++) {
            AutomationMode mode = modes[i];
            ButtonWidget button = new ButtonWidget(
                    new Rect(area.x() + i * (modeWidth + 4), y, modeWidth, 20),
                    tr("silkvis.automation.mode." + mode.name().toLowerCase(Locale.ROOT)),
                    () -> applyAutomation(withMode(settings, mode)));
            if (settings.mode() == mode) {
                button.style(ButtonWidget.Style.PRIMARY);
            }
            settingsWidgets.add(button);
        }
        y += 24;
        settingsWidgets.add(new FixedText(tr("silkvis.automation.mode."
                + settings.mode().name().toLowerCase(Locale.ROOT) + ".desc"),
                new Rect(area.x(), y, width, 30)));
        y += 32;

        // Status line and the stop button: a running goal must always be one click from stopping.
        settingsWidgets.add(new DynamicTextWidget(new Rect(area.x(), y, width, 12),
                runtime::automationStatusLine));
        y += 16;
        int buttonWidth = Math.max(80, Math.min(150, (width - 6) / 2));
        settingsWidgets.add(new ButtonWidget(
                new Rect(area.x(), y, buttonWidth, ROW_HEIGHT),
                tr("silkvis.automation.stop"), () -> {
            runtime.stopAutomation(StopReason.BY_PLAYER);
            rebuildSettings();
        }).style(ButtonWidget.Style.PRIMARY));
        settingsWidgets.add(new ButtonWidget(
                new Rect(area.x() + buttonWidth + 6, y, buttonWidth, ROW_HEIGHT),
                tr("silkvis.automation.stop_all"), () -> {
            runtime.setAutomationSettings(withMode(settings, AutomationMode.OFF));
            runtime.stopAutomation(StopReason.BY_PLAYER);
            rebuildSettings();
        }));
        y += ROW_HEIGHT + 8;

        // Safety numbers. These are the settings a player is most likely to want to change, and
        // the ones that make the difference between "helpful" and "walked me into lava".
        y = addSlider(area, y, width, tr("silkvis.automation.health_floor"), " hp",
                0, 20, 1, settings.healthFloor(), value ->
                        applyAutomation(withHealthFloor(settings, (float) value)));
        y = addSlider(area, y, width, tr("silkvis.automation.food_floor"), "",
                0, 20, 1, settings.foodFloor(), value ->
                        applyAutomation(withFoodFloor(settings, (int) Math.round(value))));
        y = addSlider(area, y, width, tr("silkvis.automation.danger_radius"), " " + tr("silkvis.unit.blocks"),
                0, 32, 1, settings.dangerRadius(), value ->
                        applyAutomation(withDangerRadius(settings, value)));
        y = addSlider(area, y, width, tr("silkvis.automation.search_nodes"), "",
                64, 8192, 64, settings.searchNodes(), value ->
                        applyAutomation(withSearchNodes(settings, (int) Math.round(value))));
        y = addToggle(area, y, width, tr("silkvis.automation.allow_break"),
                tr("silkvis.automation.allow_break.desc"), settings.allowBreak(), value ->
                        applyAutomation(withAllowBreak(settings, value)));
        y = addToggle(area, y, width, tr("silkvis.automation.allow_place"),
                tr("silkvis.automation.allow_place.desc"), settings.allowPlace(), value ->
                        applyAutomation(withAllowPlace(settings, value)));

        // Goal list. A READY goal gets a start button; the rest say why they are not there.
        settingsWidgets.add(new FixedText(tr("silkvis.automation.goals"),
                new Rect(area.x(), y, width, 12)));
        y += 16;
        // The target block is resolved at the moment of the click, not when the tab was built:
        // the player aims, then presses start.
        for (AutomationCatalog.Entry entry : AutomationCatalog.entries()) {
            boolean ready = runtime.automation().allGoals().stream()
                    .anyMatch(goal -> goal.id().equals(entry.id()))
                    && entry.status() == AutomationCatalog.Status.READY;
            String label = tr(entry.titleKey()) + "  " + entry.id();
            if (!ready) {
                settingsWidgets.add(new FixedText(label + "  -  " + tr("silkvis.automation.planned"),
                        new Rect(area.x(), y, width, 12)));
                y += 14;
                continue;
            }
            ButtonWidget start = new ButtonWidget(
                    new Rect(area.x(), y, Math.min(width, 260), ROW_HEIGHT), label,
                    () -> {
                        BlockPos target = runtime.automationTarget(client);
                        if (!runtime.startAutomation(entry.id(), target)) {
                            // The start was refused (mode too low or an unknown id): the status
                            // line already carries the reason, but the tab must show it too.
                            rebuildSettings();
                        }
                    });
            settingsWidgets.add(start);
            String description = tr(entry.descriptionKey());
            if (!description.equals(entry.descriptionKey())) {
                settingsWidgets.add(new FixedText(description,
                        new Rect(area.x() + Math.min(width, 260) + 6, y + 2,
                                Math.max(40, width - Math.min(width, 260) - 6), ROW_HEIGHT)));
            }
            y += ROW_HEIGHT + 4;
        }
        settingsWidgets.add(new FixedText(tr("silkvis.automation.target_note"),
                new Rect(area.x(), y + 2, width, 30)));
        y += 34;
        return y;
    }

    private void applyAutomation(AutomationSettings next) {
        SilkVisRuntime.get().setAutomationSettings(next);
        rebuildSettings();
    }

    private static AutomationSettings withMode(AutomationSettings s, AutomationMode mode) {
        return new AutomationSettings(mode, s.reachRadius(), s.searchNodes(), s.healthFloor(),
                s.foodFloor(), s.dangerRadius(), s.replanDistance(), s.allowBreak(),
                s.allowPlace(), s.showPath(), s.hotbarOnly());
    }

    private static AutomationSettings withHealthFloor(AutomationSettings s, float value) {
        return new AutomationSettings(s.mode(), s.reachRadius(), s.searchNodes(), value,
                s.foodFloor(), s.dangerRadius(), s.replanDistance(), s.allowBreak(),
                s.allowPlace(), s.showPath(), s.hotbarOnly());
    }

    private static AutomationSettings withFoodFloor(AutomationSettings s, int value) {
        return new AutomationSettings(s.mode(), s.reachRadius(), s.searchNodes(), s.healthFloor(),
                value, s.dangerRadius(), s.replanDistance(), s.allowBreak(),
                s.allowPlace(), s.showPath(), s.hotbarOnly());
    }

    private static AutomationSettings withDangerRadius(AutomationSettings s, double value) {
        return new AutomationSettings(s.mode(), s.reachRadius(), s.searchNodes(), s.healthFloor(),
                s.foodFloor(), value, s.replanDistance(), s.allowBreak(),
                s.allowPlace(), s.showPath(), s.hotbarOnly());
    }

    private static AutomationSettings withSearchNodes(AutomationSettings s, int value) {
        return new AutomationSettings(s.mode(), s.reachRadius(), value, s.healthFloor(),
                s.foodFloor(), s.dangerRadius(), s.replanDistance(), s.allowBreak(),
                s.allowPlace(), s.showPath(), s.hotbarOnly());
    }

    private static AutomationSettings withAllowBreak(AutomationSettings s, boolean value) {
        return new AutomationSettings(s.mode(), s.reachRadius(), s.searchNodes(), s.healthFloor(),
                s.foodFloor(), s.dangerRadius(), s.replanDistance(), value,
                s.allowPlace(), s.showPath(), s.hotbarOnly());
    }

    private static AutomationSettings withAllowPlace(AutomationSettings s, boolean value) {
        return new AutomationSettings(s.mode(), s.reachRadius(), s.searchNodes(), s.healthFloor(),
                s.foodFloor(), s.dangerRadius(), s.replanDistance(), s.allowBreak(),
                value, s.showPath(), s.hotbarOnly());
    }

    private String tr(String key) {
        return localizer().get(key);
    }

    /** Formatted variant; a broken placeholder returns the raw text, never an exception. */
    private String tr(String key, Object... args) {
        return localizer().get(key, args);
    }

    @Override
    protected void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        // Toggle state words come from the language table, not from the widget's fallback.
        ToggleWidget.setStateLabels(tr("silkvis.state.on"), tr("silkvis.state.off"));
        layout = MainScreenLayout.compute(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        chrome.clear();
        navButtons.clear();
        settingsWidgets.clear();
        // A link that is obviously wrong (ftp:, a bare word) is marked while typing; the
        // empty field stays valid so Enter does nothing instead of shouting at the player.
        // init() also runs on a window resize, so an existing field keeps what was typed.
        if (totemUrlModel == null) {
            totemUrlModel = new TextInputModel(runtime.totemUrlDraft(),
                    TotemDownloader.MAX_URL_LENGTH,
                    value -> value.isBlank() || TotemDownloader.validate(value) == null);
        }
        totemVersionSeen = runtime.totemVersion();

        List<ModuleCategory> tabs = MainScreenModel.p1Tabs();
        Rect nav = layout.navigation();
        int navY = nav.y() + 4;
        int navItemWidth = Math.max(0, nav.width() - 8);
        // Nine tabs do not fit at a fixed 28 px stride on a 1080p screen at GUI scale 4:
        // the rows shrink to the panel instead of the last tabs falling out of the window.
        int[] rows = MainScreenLayout.navRows(nav, tabs.size());
        int itemHeight = rows[0];
        int stride = rows[1];
        for (ModuleCategory tab : tabs) {
            Rect row = new Rect(nav.x() + 4, navY, navItemWidth, itemHeight);
            ButtonWidget button = new ButtonWidget(row, tr(tab.langKey()), () -> {
                runtime.screenModel().select(tab);
                settingsScroll.setOffset(0);
                rebuildSettings();
            });
            if (runtime.screenModel().selectedCategory() == tab) {
                button.style(ButtonWidget.Style.PRIMARY);
            }
            button.setEnabled(navItemWidth >= 40);
            chrome.add(button);
            navButtons.add(button);
            navY += stride;
        }

        Rect closeBounds = layout.closeButton();
        chrome.add(new ButtonWidget(closeBounds, "×", this::close)
                .style(ButtonWidget.Style.SECONDARY));

        TextInputModel searchModel = new TextInputModel(runtime.screenModel().query(), 48, s -> true);
        search = new TextInputWidget(layout.search(), searchModel, tr("silkvis.search.hint"), value -> {
            runtime.screenModel().setQuery(value);
            rebuildSettings();
        });
        chrome.add(search);

        // Viewport heights come from the layout, not from 0: with a 0-height viewport
        // maxOffset() equals the whole content height, so the list could scroll past its
        // end, and the settings pane (whose content height was never set at all) could not
        // scroll even though its controls were clipped off the panel.
        listScroll = new ScrollModel(Math.max(0, layout.moduleList().height() - 8), 24);
        settingsScroll = new ScrollModel(
                Math.max(0, layout.settings().height() - 54), 28);
        rebuildSettings();
        focus.setScope(collectFocusable());
    }

    private List<Widget> collectFocusable() {
        List<Widget> all = new ArrayList<>(chrome);
        all.addAll(settingsWidgets);
        return all;
    }

    private void rebuildSettings() {
        settingsWidgets.clear();
        ModuleCategory category = runtime.screenModel().selectedCategory();
        Rect area = layout.settings().inset(8, 8);
        // Below the title, the caption, the accent rule and the hairline (renderSettings).
        int contentTop = area.y() + 42;
        int y = contentTop - settingsScroll.offset();
        int width = Math.max(80, area.width() - 8);

        // Selecting a module row in the list must do something: this puts that module's
        // switch (and the link to its full settings) above the tab's own controls.
        y = addSoupModuleSwitch(area, y, width);

        switch (category) {
            case HUD -> {
                // The editor is the place where position, scale and opacity live; the tab
                // itself only switches widgets on and off (owner: «полноценное редактирование»).
                settingsWidgets.add(new ButtonWidget(
                        new Rect(area.x(), y + 4, Math.min(width, 220), ROW_HEIGHT),
                        tr("silkvis.hud.editor.open"), () -> {
                    if (client != null) {
                        client.setScreen(new HudEditorScreen());
                    }
                }).style(ButtonWidget.Style.PRIMARY));
                y += ROW_HEIGHT + 12;
                for (HudWidgetId id : HudWidgetId.values()) {
                    if (!id.available()) {
                        continue;
                    }
                    HudWidgetConfig cfg = runtime.hudLayout().get(id);
                    y = addToggle(area, y, width, tr(id.langKey()), null, cfg.enabled(), value -> {
                        HudLayoutConfig updated = runtime.hudLayout()
                                .with(cfg.withEnabled(value));
                        runtime.setHudLayout(updated);
                    });
                }
            }
            case MAP -> {
                MapSettings map = runtime.ui().map();
                y = addToggle(area, y, width, tr("silkvis.map.north_up"), tr("silkvis.map.north_up.desc"),
                        map.northUp(), value -> {
                            MapSettings updated = new MapSettings(map.diskQuotaMiB(), map.terrainCacheMiB(),
                                    map.minimapSize(), map.shape(), value, map.zoom(), map.showCardinals(),
                                    map.showCoordinates(), map.mode(), map.sliceY());
                            runtime.requestUiSave(new silkvis.config.UiConfig(runtime.hudLayout(), updated,
                                    runtime.ui().visual()));
                        });
                y = addSlider(area, y, width, tr("silkvis.map.zoom"), "x",
                        1, 8, 1, map.zoom(), value -> {
                            MapSettings updated = new MapSettings(map.diskQuotaMiB(), map.terrainCacheMiB(),
                                    map.minimapSize(), map.shape(), map.northUp(), (int) Math.round(value),
                                    map.showCardinals(), map.showCoordinates(), map.mode(), map.sliceY());
                            runtime.requestUiSave(new silkvis.config.UiConfig(runtime.hudLayout(), updated,
                                    runtime.ui().visual()));
                        });
                // Live tile pipeline state: otherwise "нет тайлов" looks like a broken
                // widget instead of work in progress (MAP-06 diagnostics).
                y += 8;
                settingsWidgets.add(new DynamicTextWidget(
                        new Rect(area.x(), y, width, 12), this::mapStatsText));
                if (runtime.externalMapPresent()) {
                    // ADR-0007: the external map wins. Say so instead of hiding the tab.
                    settingsWidgets.add(new FixedText(
                            tr("silkvis.map.external_found") + ": " + runtime.externalMapLabel(),
                            new Rect(area.x(), y + 16, width, 16)));
                    settingsWidgets.add(new FixedText(tr("silkvis.map.external_hint"),
                            new Rect(area.x(), y + 34, width, 34)));
                } else {
                    settingsWidgets.add(new ButtonWidget(new Rect(area.x(), y + 16, width, ROW_HEIGHT),
                            tr("silkvis.map.open"),
                            () -> MinecraftClient.getInstance().setScreen(new MapScreen())));
                }
            }
            case MUSIC -> {
                GlobalConfig global = runtime.global();
                settingsWidgets.add(new DynamicTextWidget(new Rect(area.x(), y, width, 12),
                        () -> tr("silkvis.media.status") + ": " + runtime.mediaStatusLine()));
                y += 14;
                // What is playing, from the cached snapshot: no provider call in the UI.
                settingsWidgets.add(new DynamicTextWidget(new Rect(area.x(), y, width, 12),
                        this::nowPlayingLine));
                y += 16;
                // Transport. The HUD card needs interaction mode; the menu is always one
                // keypress away, so playback control lives here too (owner report: "не могу
                // переключать треки, ставить на паузу").
                int transportWidth = Math.max(52, width / 3 - 4);
                String[] transportKeys = {"silkvis.media.prev", "silkvis.media.toggle", "silkvis.media.next"};
                MediaAction[] transportActions = {MediaAction.PREVIOUS, MediaAction.TOGGLE, MediaAction.NEXT};
                for (int i = 0; i < transportActions.length; i++) {
                    MediaAction action = transportActions[i];
                    settingsWidgets.add(new ButtonWidget(
                            new Rect(area.x() + i * (transportWidth + 4), y, transportWidth, 20),
                            tr(transportKeys[i]), () -> mediaCommand(action)));
                }
                y += 26;
                y = addToggle(area, y, width, tr("silkvis.media.enabled"), tr("silkvis.media.enabled.desc"),
                        global.media().enabled(), value -> {
                            var media = global.media();
                            runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                                    global.reducedMotion(), global.blurEnabled(), global.verboseDiagnostics(),
                                    new silkvis.config.MediaConfig(value, media.selection(), media.pinnedAppId(),
                                            media.hudWidgetEnabled(), media.pauseSourceOnWorldExit(),
                                            media.variant(), media.showArtwork())));
                        });
                y = addToggle(area, y, width, tr("silkvis.media.hud_widget"),
                        tr("silkvis.media.hud_widget.desc"), global.media().hudWidgetEnabled(), value -> {
                            var media = global.media();
                            runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                                    global.reducedMotion(), global.blurEnabled(), global.verboseDiagnostics(),
                                    new silkvis.config.MediaConfig(media.enabled(), media.selection(),
                                            media.pinnedAppId(), value, media.pauseSourceOnWorldExit(),
                                            media.variant(), media.showArtwork())));
                        });
                // Appearance: the three variants the owner asked for, plus artwork.
                y += 4;
                int segmentWidth = Math.max(48, width / 3 - 4);
                silkvis.config.NowPlayingVariant[] variants = silkvis.config.NowPlayingVariant.values();
                for (int i = 0; i < variants.length; i++) {
                    silkvis.config.NowPlayingVariant variant = variants[i];
                    ButtonWidget button = new ButtonWidget(
                            new Rect(area.x() + i * (segmentWidth + 4), y, segmentWidth, 20),
                            tr("silkvis.media.variant." + variant.name().toLowerCase(Locale.ROOT)),
                            () -> saveMediaVariant(variant));
                    if (global.media().variant() == variant) {
                        button.style(ButtonWidget.Style.PRIMARY);
                    }
                    settingsWidgets.add(button);
                }
                y += 26;
                y = addToggle(area, y, width, tr("silkvis.media.artwork"),
                        tr("silkvis.media.artwork.desc"), global.media().showArtwork(),
                        value -> saveMediaArtwork(value));
                // Cover state, in words: "загружается" forever with no reason is what the
                // owner saw; the helper now reports why it came back empty.
                settingsWidgets.add(new DynamicTextWidget(new Rect(area.x(), y, width, 12),
                        this::artworkStateText));
                y += 16;
                // Manual retry: if the bridge was refused (PowerShell blocked by policy, no
                // media session yet), fixing the cause should not require restarting the game.
                settingsWidgets.add(new ButtonWidget(new Rect(area.x(), y + 6, Math.min(width, 220), ROW_HEIGHT),
                        tr("silkvis.media.restart"), () -> runtime.restartMedia()));
                y += ROW_HEIGHT + 10;
            }
            case VISUAL -> {
                settingsWidgets.add(new ButtonWidget(
                        new Rect(area.x(), y + 4, Math.min(width, 240), ROW_HEIGHT),
                        tr("silkvis.soup.open"), () -> {
                    if (client != null) {
                        client.setScreen(new SoupSettingsScreen(this));
                    }
                }).style(ButtonWidget.Style.PRIMARY));
                y += ROW_HEIGHT + 14;
                VisualSettings visual = runtime.ui().visual();
                y = addToggle(area, y, width, tr("silkvis.visual.fov"), tr("silkvis.visual.fov.desc"),
                        visual.fovEnabled(), value -> {
                            VisualSettings updated = new VisualSettings(value, visual.fovDegrees(),
                                    visual.viewBobbing(), visual.hurtCameraIntensity(),
                                    visual.screenEffectIntensity(), visual.fogEnabled(), visual.fogStartRatio(),
                                    visual.fogEndRatio(), visual.particleCap(), visual.rainVisualIntensity(),
                                    visual.cloudsVisible(), visual.entityMarkersEnabled(),
                                    visual.depthAwareMarkers());
                            runtime.requestUiSave(new silkvis.config.UiConfig(runtime.hudLayout(),
                                    runtime.ui().map(), updated));
                        });
                y = addSlider(area, y, width, tr("silkvis.visual.particles"), "",
                        0, 8000, 100, visual.particleCap(), value -> {
                            VisualSettings updated = new VisualSettings(visual.fovEnabled(),
                                    visual.fovDegrees(), visual.viewBobbing(), visual.hurtCameraIntensity(),
                                    visual.screenEffectIntensity(), visual.fogEnabled(), visual.fogStartRatio(),
                                    visual.fogEndRatio(), (int) Math.round(value), visual.rainVisualIntensity(),
                                    visual.cloudsVisible(), visual.entityMarkersEnabled(),
                                    visual.depthAwareMarkers());
                            runtime.requestUiSave(new silkvis.config.UiConfig(
                                    runtime.hudLayout(), runtime.ui().map(), updated));
                        });
            }
            case THEME -> {
                GlobalConfig global = runtime.global();
                y = addToggle(area, y, width, tr("silkvis.theme.reduced_motion"),
                        tr("silkvis.theme.reduced_motion.desc"), global.reducedMotion(), value ->
                                runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                                        value, global.blurEnabled(), global.verboseDiagnostics(), global.media())));
                y = addToggle(area, y, width, tr("silkvis.theme.blur"), tr("silkvis.theme.blur.desc"),
                        global.blurEnabled(), value ->
                                runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                                        global.reducedMotion(), value, global.verboseDiagnostics(), global.media())));
                y = addToggle(area, y, width, tr("silkvis.settings.verbose"), tr("silkvis.settings.verbose.desc"),
                        global.verboseDiagnostics(), value ->
                                runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                                        global.reducedMotion(), global.blurEnabled(), value, global.media())));
                ButtonWidget preset = new ButtonWidget(new Rect(area.x(), y + 6, Math.min(width, 180), 22),
                        tr("silkvis.preset." + global.preset().id()),
                        () -> runtime.requestGlobalSave(new GlobalConfig(global.language(),
                                nextPreset(global.preset()), global.reducedMotion(), global.blurEnabled(),
                                global.verboseDiagnostics(), global.media())));
                settingsWidgets.add(preset);
                y += 34;
                // Language was a config field nobody could change: there was no control at
                // all. Auto follows the OS language, which is what a Russian user expects.
                settingsWidgets.add(new FixedText(tr("silkvis.language.title"),
                        new Rect(area.x(), y + 2, width, 16)));
                y += 20;
                String[] languages = {"auto", "ru", "en"};
                int languageWidth = Math.max(50, width / 3 - 4);
                for (int i = 0; i < languages.length; i++) {
                    String code = languages[i];
                    ButtonWidget button = new ButtonWidget(
                            new Rect(area.x() + i * (languageWidth + 4), y, languageWidth, 20),
                            tr("silkvis.language." + code), () -> saveLanguage(code));
                    if (global.language().equals(code)) {
                        button.style(ButtonWidget.Style.PRIMARY);
                    }
                    settingsWidgets.add(button);
                }
            }
            case TOTEMS -> {
                // Texture size: the setting that decides how sharp the picture looks. It used
                // to be hard-wired to the vanilla 16x16, which is why a photo came out as a
                // blurry smear. One row, four segments: holding four stacked full-width rows
                // here would push the actual totem controls below the fold.
                settingsWidgets.add(new FixedText(tr("silkvis.totems.size"),
                        new Rect(area.x(), y, width, 14)));
                y += 16;
                VisualSettings totemVisual = runtime.ui().visual();
                int segmentWidth = Math.max(24,
                        Math.min(width, 320) / VisualSettings.TOTEM_TEXTURE_SIZES.length);
                int segmentX = area.x();
                for (int size : VisualSettings.TOTEM_TEXTURE_SIZES) {
                    boolean active = totemVisual.totemTextureSize() == size;
                    ButtonWidget sizeButton = new ButtonWidget(
                            new Rect(segmentX, y, segmentWidth - 2, ROW_HEIGHT),
                            tr("silkvis.totems.size." + size), () -> {
                        if (runtime.ui().visual().totemTextureSize() == size) {
                            return;
                        }
                        // Copy the record with the new edge (records have no wither), save it,
                        // then rebuild the pack from the picture already applied: without the
                        // rebuild the player changes the setting and sees nothing.
                        VisualSettings current = runtime.ui().visual();
                        VisualSettings updated = new VisualSettings(current.fovEnabled(),
                                current.fovDegrees(), current.viewBobbing(),
                                current.hurtCameraIntensity(), current.screenEffectIntensity(),
                                current.fogEnabled(), current.fogStartRatio(),
                                current.fogEndRatio(), current.particleCap(),
                                current.rainVisualIntensity(), current.cloudsVisible(),
                                current.entityMarkersEnabled(), current.depthAwareMarkers(), size,
                                current.totemTrimBackground());
                        runtime.requestUiSave(new silkvis.config.UiConfig(runtime.hudLayout(),
                                runtime.ui().map(), updated));
                        runtime.rebuildTotemWithCurrentSize();
                        rebuildSettings();
                    });
                    if (active) {
                        sizeButton.style(ButtonWidget.Style.PRIMARY);
                    }
                    settingsWidgets.add(sizeButton);
                    segmentX += segmentWidth;
                }
                y += ROW_HEIGHT + 2;
                settingsWidgets.add(new FixedText(tr("silkvis.totems.size.desc"),
                        new Rect(area.x(), y, width, 22)));
                y += 26;
                // Background trim: one switch, because the player's complaint was "a white
                // square instead of my picture" and the fix has to be one click.
                boolean trim = totemVisual.totemTrimBackground();
                settingsWidgets.add(new ButtonWidget(
                        new Rect(area.x(), y, Math.min(width, 320), ROW_HEIGHT),
                        tr("silkvis.totems.trim") + ": "
                                + tr(trim ? "silkvis.state.on" : "silkvis.state.off"), () -> {
                    VisualSettings current = runtime.ui().visual();
                    VisualSettings updated = new VisualSettings(current.fovEnabled(),
                            current.fovDegrees(), current.viewBobbing(),
                            current.hurtCameraIntensity(), current.screenEffectIntensity(),
                            current.fogEnabled(), current.fogStartRatio(), current.fogEndRatio(),
                            current.particleCap(), current.rainVisualIntensity(),
                            current.cloudsVisible(), current.entityMarkersEnabled(),
                            current.depthAwareMarkers(), current.totemTextureSize(), !trim);
                    runtime.requestUiSave(new silkvis.config.UiConfig(runtime.hudLayout(),
                            runtime.ui().map(), updated));
                    runtime.rebuildTotemWithCurrentSize();
                    rebuildSettings();
                }).style(trim ? ButtonWidget.Style.PRIMARY : ButtonWidget.Style.SECONDARY));
                y += ROW_HEIGHT + 2;
                settingsWidgets.add(new FixedText(tr("silkvis.totems.trim.desc"),
                        new Rect(area.x(), y, width, 22)));
                y += 26;

                // ASSET-04: a picture in a folder becomes a real resource pack. No file
                // dialog (a mod cannot open one without native code) and no typed paths.
                settingsWidgets.add(new FixedText(tr("silkvis.totems.hint"),
                        new Rect(area.x(), y + 2, width, 34)));
                y += 38;
                settingsWidgets.add(new DynamicTextWidget(new Rect(area.x(), y, width, 12),
                        this::totemFolderText));
                y += 18;
                // Add by link (player request): a text field rather than a second folder, and
                // the same picture pipeline behind it — download, then the pack builder.
                settingsWidgets.add(new FixedText(tr("silkvis.totems.url.label"),
                        new Rect(area.x(), y, width, 12)));
                y += 14;
                settingsWidgets.add(new TextInputWidget(
                        new Rect(area.x(), y, Math.min(width, 320), ROW_HEIGHT),
                        totemUrlModel, tr("silkvis.totems.url.hint"), runtime::downloadTotem));
                y += ROW_HEIGHT + 4;
                settingsWidgets.add(new ButtonWidget(
                        new Rect(area.x(), y, Math.min(width, 220), ROW_HEIGHT),
                        tr("silkvis.totems.url.add"),
                        () -> runtime.downloadTotem(totemUrlModel.text())));
                y += ROW_HEIGHT + 4;
                settingsWidgets.add(new FixedText(tr("silkvis.totems.url.note"),
                        new Rect(area.x(), y, width, 22)));
                y += 26;
                settingsWidgets.add(new FixedText(tr("silkvis.totems.list.label"),
                        new Rect(area.x(), y, width, 12)));
                y += 14;
                List<TotemLibrary.Entry> entries = runtime.totemEntries();
                if (entries.isEmpty()) {
                    settingsWidgets.add(new FixedText(tr("silkvis.totems.empty"),
                            new Rect(area.x(), y + 2, width, 30)));
                    y += 34;
                } else {
                    for (TotemLibrary.Entry entry : entries) {
                        String label = entry.displayName() + "   " + (entry.bytes() / 1024L) + " KiB";
                        settingsWidgets.add(new ButtonWidget(
                                new Rect(area.x(), y, Math.min(width, 280), ROW_HEIGHT),
                                label, () -> runtime.applyTotem(entry)));
                        y += ROW_HEIGHT + 4;
                    }
                }
                settingsWidgets.add(new ButtonWidget(
                        new Rect(area.x(), y + 4, Math.min(width, 200), ROW_HEIGHT),
                        tr("silkvis.totems.refresh"), () -> {
                    runtime.refreshTotems();
                    rebuildSettings();
                }));
                y += ROW_HEIGHT + 14;
                settingsWidgets.add(new DynamicTextWidget(new Rect(area.x(), y, width, 12),
                        runtime::totemStatusLine));
                y += 16;
                // The pack is switched on here, not only in the vanilla screen: that list is
                // scanned at startup, so a pack written a second ago is not in it at all.
                settingsWidgets.add(new DynamicTextWidget(new Rect(area.x(), y, width, 12),
                        this::totemPackStateText));
                y += 16;
                int packButtonWidth = Math.max(70, Math.min(150, (width - 6) / 2));
                settingsWidgets.add(new ButtonWidget(
                        new Rect(area.x(), y, packButtonWidth, ROW_HEIGHT),
                        tr("silkvis.totems.pack.enable"),
                        () -> applyTotemPack(true)).style(ButtonWidget.Style.PRIMARY));
                settingsWidgets.add(new ButtonWidget(
                        new Rect(area.x() + packButtonWidth + 6, y, packButtonWidth, ROW_HEIGHT),
                        tr("silkvis.totems.pack.disable"), () -> applyTotemPack(false)));
                y += ROW_HEIGHT + 6;
                settingsWidgets.add(new DynamicTextWidget(new Rect(area.x(), y, width, 12),
                        runtime::totemPackLine));
                y += 16;
                settingsWidgets.add(new FixedText(tr("silkvis.totems.enable"),
                        new Rect(area.x(), y, width, 34)));
            }
            case SETTINGS -> {
                ButtonWidget editor = new ButtonWidget(new Rect(area.x(), y + 6, Math.min(width, 200), 22),
                        tr("silkvis.hud.editor.open"), () -> {
                    if (client != null) {
                        client.setScreen(new HudEditorScreen());
                    }
                });
                settingsWidgets.add(editor);
                y += 34;
                ButtonWidget reset = new ButtonWidget(new Rect(area.x(), y + 6, Math.min(width, 200), 22),
                        tr("silkvis.hud.reset"), () -> runtime.setHudLayout(HudLayoutConfig.defaults()));
                settingsWidgets.add(reset);
                y += 34;
                // The interaction key is unbound on purpose (no extra keybinds), so the mode
                // was unreachable in game: the music card buttons could never be clicked.
                ButtonWidget interaction = new ButtonWidget(new Rect(area.x(), y + 6, Math.min(width, 200), 22),
                        tr("silkvis.hud.interaction_open"), () -> {
                    runtime.setHudInteractionMode(true);
                    if (client != null) {
                        client.setScreen(new HudInteractionScreen());
                    }
                });
                settingsWidgets.add(interaction);
                y += 34;
                settingsWidgets.add(new FixedText(tr("silkvis.hud.interaction_why"),
                        new Rect(area.x(), y, width, 24)));
                y += 28;
                // Support flow: one file with versions, environment, module states and the
                // log tail, written to the desktop.
                ButtonWidget report = new ButtonWidget(new Rect(area.x(), y + 6, Math.min(width, 200), 22),
                        tr("silkvis.settings.bug_report"),
                        () -> BugReportService.createAndNotify(MinecraftClient.getInstance()));
                settingsWidgets.add(report);
            }
            case INTEGRATIONS -> {
                // English enum names ("iris_no_pack") were leaking straight into the UI.
                String mode = tr("silkvis.integrations.shader."
                        + runtime.integrations().shaderMode(false).name().toLowerCase(Locale.ROOT));
                settingsWidgets.add(new FixedText(tr("silkvis.integrations.mode") + ": " + mode,
                        new Rect(area.x(), y + 6, width, 16)));
                settingsWidgets.add(new FixedText(integrationLine("sodium"), new Rect(area.x(), y + 24, width, 16)));
                settingsWidgets.add(new FixedText(integrationLine("iris"), new Rect(area.x(), y + 40, width, 16)));
                settingsWidgets.add(new FixedText(integrationLine("baritone"), new Rect(area.x(), y + 56, width, 16)));
                String map = runtime.externalMapPresent()
                        ? tr("silkvis.map.external_found") + ": " + runtime.externalMapLabel()
                        : tr("silkvis.map.external_missing");
                settingsWidgets.add(new FixedText(map, new Rect(area.x(), y + 76, width, 16)));
                settingsWidgets.add(new FixedText(tr("silkvis.map.external_why"),
                        new Rect(area.x(), y + 94, width, 34)));
            }
            case AUTOMATION_P2 -> y = buildAutomationTab(area, y, width);
            default -> {
                // VISUAL handled above; nothing extra.
            }
        }
        // Real content height, so the wheel has somewhere to go. Without it the pane
        // reported an empty content and refused to scroll while clipping its controls.
        int contentBottom = contentTop - settingsScroll.offset();
        for (Widget widget : settingsWidgets) {
            contentBottom = Math.max(contentBottom, widget.bounds().bottom());
        }
        settingsScroll.setContentHeight(Math.max(0, contentBottom - contentTop + 8));
        focus.setScope(collectFocusable());
    }

    /** One line about the album cover: cached, waiting, or the reason it is missing. */
    private String artworkStateText() {
        var service = runtime.mediaService();
        if (service.isEmpty()) {
            return "";
        }
        if (service.get().currentArtworkHash().isPresent()) {
            return tr("silkvis.media.artwork.state.ok");
        }
        String notice = service.get().artworkNotice();
        if (notice == null || notice.isEmpty()) {
            return tr("silkvis.media.artwork.state.waiting");
        }
        String key = "silkvis.media.artwork.reason." + notice;
        String translated = tr(key);
        return tr("silkvis.media.artwork.state.missing",
                translated.equals(key) ? notice : translated);
    }

    /** Live pack state: read from the manager, never cached (it changes outside this tab). */
    private String totemPackStateText() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean on = TotemPackApplier.isEnabled(client, TotemPackBuilder.PACK_DIR_NAME);
        return tr(on ? "silkvis.totems.pack.on" : "silkvis.totems.pack.off");
    }

    /** Enables or disables the generated pack and records the outcome in the log. */
    private void applyTotemPack(boolean enable) {
        MinecraftClient client = MinecraftClient.getInstance();
        String result = enable
                ? TotemPackApplier.enable(client, TotemPackBuilder.PACK_DIR_NAME)
                : TotemPackApplier.disable(client, TotemPackBuilder.PACK_DIR_NAME);
        runtime.setTotemPackResult(enable, result);
    }

    /** Where the pictures go; the absolute path is what the player has to open. */
    private String totemFolderText() {
        var dir = runtime.totemSourceDir();
        return tr("silkvis.totems.folder") + ": " + (dir == null ? "silkvis-data/totems" : dir);
    }

    /** One line: title — artist, position / length. Unknown values are shown, not invented. */
    private String nowPlayingLine() {
        var service = runtime.mediaService();
        if (service.isEmpty()) {
            return tr("silkvis.media.now.none");
        }
        var snapshotOpt = service.get().snapshot();
        if (snapshotOpt.isEmpty()) {
            return tr("silkvis.media.now.none");
        }
        MediaSnapshot snapshot = snapshotOpt.get();
        String title = snapshot.titleOpt().orElse(tr("silkvis.media.now.unknown"));
        String artist = snapshot.artistOpt().orElse("");
        long position = MediaTimeline
                .projectPosition(snapshot, runtime.clock().monotonicNanos(), runtime.clock())
                .orElse(-1L);
        String positionText = position < 0 ? "--:--" : MediaTimeline.formatPosition(position);
        String line = artist.isBlank() ? title : title + " \u2014 " + artist;
        if (snapshot.playbackState() != silkvis.media.PlaybackState.UNKNOWN) {
            line = line + " \u00b7 " + tr("silkvis.media.playback."
                    + snapshot.playbackState().name().toLowerCase(Locale.ROOT));
        }
        return line + "   " + positionText + " / " + mediaLength(snapshot);
    }

    /** LIVE is a word, so it is translated here rather than in the media layer. */
    private String mediaLength(MediaSnapshot snapshot) {
        if (snapshot == null) {
            return "--:--";
        }
        if (!MediaTimeline.isLiveOrUnknownLength(snapshot)) {
            return MediaTimeline.formatPosition(snapshot.endMs());
        }
        return snapshot.playbackState() == silkvis.media.PlaybackState.PLAYING
                ? tr("silkvis.media.live") : "--:--";
    }

    /** Commands are addressed to the current session; a rejection is logged, never silent. */
    private void mediaCommand(MediaAction action) {
        var service = runtime.mediaService();
        if (service.isEmpty()) {
            runtime.log().log("MEDIA_COMMAND_NO_PROVIDER", action.name());
            return;
        }
        MediaCommandResult result = service.get().command(action);
        if (result.status() != MediaCommandResult.Status.ACCEPTED) {
            runtime.log().log("MEDIA_COMMAND_REJECTED",
                    action.name() + ":" + result.status().name());
        }
    }

    /** «Очередь 3 · тайлов 12 · 480 KiB · попаданий 82 %» — built from localized words. */
    private String mapStatsText() {
        return runtime.mapService().map(service -> {
            silkvis.map.MapService.Stats stats = service.stats();
            return tr("silkvis.map.diag.queued") + " " + stats.pendingJobs()
                    + " · " + tr("silkvis.map.diag.tiles") + " " + stats.cachedTiles()
                    + " · " + (stats.cacheBytes() / 1024) + " " + tr("silkvis.map.diag.kib")
                    + " · " + tr("silkvis.map.diag.hits") + " "
                    + Math.round(stats.hitRate() * 100) + " %";
        }).orElse(tr("silkvis.map.inactive"));
    }

    private String integrationLine(String modId) {
        String state = runtime.integrations().isPresent(modId)
                ? tr("silkvis.integrations.present")
                : tr("silkvis.integrations.absent");
        return modId + " — " + state;
    }

    /**
     * Rebuilding the screen is the honest way to relabel everything: tab names, footer and
     * captions are created in init(), and a half-relabelled window looks broken.
     */
    private void saveLanguage(String code) {
        runtime.setLanguage(code);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new SilkVisScreen());
        }
    }

    private void saveMediaVariant(silkvis.config.NowPlayingVariant variant) {
        GlobalConfig global = runtime.global();
        runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                global.reducedMotion(), global.blurEnabled(), global.verboseDiagnostics(),
                global.media().withVariant(variant)));
        rebuildSettings();
    }

    private void saveMediaArtwork(boolean value) {
        GlobalConfig global = runtime.global();
        runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                global.reducedMotion(), global.blurEnabled(), global.verboseDiagnostics(),
                global.media().withArtwork(value)));
    }

    private int addToggle(Rect area, int y, int width, String label, String description,
                          boolean initial, silkvis.ui.widget.BooleanConsumer onChange) {
        ToggleWidget toggle = new ToggleWidget(new Rect(area.x(), y, width, 20), label, description,
                initial, onChange);
        settingsWidgets.add(toggle);
        return y + 24;
    }

    private int addSlider(Rect area, int y, int width, String label, String unit,
                          double min, double max, double step, double initial,
                          java.util.function.DoubleConsumer onCommit) {
        SliderWidget slider = new SliderWidget(new Rect(area.x(), y + 10, width, 14), label, unit,
                min, max, step, initial, preview -> { }, onCommit);
        settingsWidgets.add(slider);
        return y + 34;
    }

    private static VisualPreset nextPreset(VisualPreset current) {
        return switch (current) {
            case LOW -> VisualPreset.BALANCED;
            case BALANCED -> VisualPreset.QUALITY;
            case QUALITY -> VisualPreset.LOW;
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // A download finishes on the IO thread; the open tab must show the new file without
        // asking the player to reopen it. Rebuilding before any widget is touched keeps the
        // list consistent for this frame.
        if (totemVersionSeen != runtime.totemVersion()) {
            totemVersionSeen = runtime.totemVersion();
            rebuildSettings();
        }
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();
        float open = Animation.progress(openedAtNanos, theme.motionMs());
        painter.fill(new Rect(0, 0, width, height),
                theme.background().withAlpha(0.55f * open).argb());
        Rect window = layout.window();
        // Depth without blur: three expanding translucent layers behind the window (UI-08
        // allows a bounded number of passes, not a per-panel effect).
        for (int layer = 3; layer >= 1; layer--) {
            painter.roundRect(window.inset(-layer * 2, -layer * 2), theme.radiusPanel() + layer * 2,
                    Color.argb(theme.background().withAlpha(0.10f)));
        }
        painter.roundPanel(window, theme.radiusPanel(), Color.argb(theme.background()),
                Color.argb(theme.border()));

        renderNavigation(painter, theme);
        renderModuleList(painter, theme, mouseX, mouseY);
        renderSettings(painter, theme);
        syncNavigationStyles();
        for (Widget widget : chrome) {
            widget.render(painter, theme);
        }
        Rect footer = layout.footer();
        painter.roundPanel(footer, theme.radiusControl() + 2,
                Color.argb(theme.surface().withAlpha(0.45f)), Color.argb(theme.border()));
        painter.text(tr("silkvis.footer.hint"), footer.x() + 6,
                footer.centerY() - painter.lineHeight() / 2, Color.argb(theme.textSecondary()), false);

        updateHover(mouseX, mouseY);
        String tooltipKey = tooltipAt(mouseX, mouseY);
        if (tooltipKey != null) {
            tooltips.hover(tooltipKey, mouseX, mouseY);
        } else {
            tooltips.clear();
        }
        if (tooltips.visible()) {
            renderTooltip(painter, theme, tr(tooltips.key()));
        }
    }

    private void renderNavigation(Painter painter, ThemeTokens theme) {
        Rect nav = layout.navigation();
        // Card, not a slab: the window already has its own radius.
        painter.roundPanel(nav, theme.radiusPanel(),
                Color.argb(theme.surface().withAlpha(0.55f)),
                Color.argb(theme.border()));
        Rect window = layout.window();
        painter.text(tr("silkvis.screen.title"), nav.x() + 2, window.y() + 10,
                Color.argb(theme.accent()), true);
        // Accent underline ties the wordmark to the navigation column.
        int wordmarkWidth = painter.textWidth(tr("silkvis.screen.title"));
        painter.fill(new Rect(nav.x() + 2, window.y() + 21, Math.min(wordmarkWidth, nav.width() - 8), 1),
                Color.argb(theme.accent().withAlpha(0.65f)));
    }

    /**
     * Navigation buttons must show the *current* tab, not the tab that happened to be
     * selected when {@link #init()} last ran. Styles are re-derived every frame instead of
     * only on build: without this the accent outline stayed on the previously built tab
     * (seen in the first in-game run, 2026-11-12).
     */
    private void syncNavigationStyles() {
        ModuleCategory selected = runtime.screenModel().selectedCategory();
        List<ModuleCategory> tabs = MainScreenModel.p1Tabs();
        for (int i = 0; i < navButtons.size() && i < tabs.size(); i++) {
            navButtons.get(i).style(tabs.get(i) == selected
                    ? ButtonWidget.Style.PRIMARY
                    : ButtonWidget.Style.SECONDARY);
        }
    }

    private void renderModuleList(Painter painter, ThemeTokens theme, int mouseX, int mouseY) {
        if (layout.mode() == MainScreenLayout.Mode.NARROW) {
            return;
        }
        Rect list = layout.moduleList();
        if (list.width() <= 0) {
            return;
        }
        painter.roundPanel(list, theme.radiusPanel(), Color.argb(theme.surface().withAlpha(0.35f)),
                Color.argb(theme.border().multiplyAlpha(0.6f)));
        List<ModuleDescriptor> modules = runtime.screenModel().visibleModules(localizer());
        int rowY = list.y() + 4 - listScroll.offset();
        hoveredRow = -1;
        for (int i = 0; i < modules.size(); i++) {
            ModuleDescriptor module = modules.get(i);
            Rect row = new Rect(list.x() + 4, rowY, list.width() - 8, ROW_HEIGHT);
            if (row.bottom() > list.y() && row.y() < list.bottom() && row.contains(mouseX, mouseY)) {
                hoveredRow = i;
                painter.roundRect(row, theme.radiusControl(), Color.argb(theme.surfaceHover()));
            }
            String name = localizer().get(module.displayKey());
            if (name.equals(module.displayKey())) {
                name = module.id();
            }
            painter.text(name, row.x() + 4, row.centerY() - painter.lineHeight() / 2,
                    Color.argb(theme.textPrimary()), false);
            ModuleRegistry.Entry entry = runtime.modules().get(module.id()).orElse(null);
            if (entry != null) {
                String status = localizer().get(runtime.screenModel().statusKeyFor(entry));
                painter.text(status, row.right() - painter.textWidth(status) - 4,
                        row.centerY() - painter.lineHeight() / 2,
                        Color.argb(entry.state() == silkvis.core.ModuleState.ENABLED
                                ? theme.success() : theme.textSecondary()), false);
            }
            rowY += ROW_HEIGHT;
        }
        listScroll.setContentHeight(modules.size() * ROW_HEIGHT + 8);
        if (listScroll.needsScrollbar()) {
            int trackHeight = list.height() - 8;
            int thumbY = listScroll.thumbY(list.y() + 4, trackHeight);
            painter.fill(new Rect(list.right() - 4, thumbY, 2, listScroll.thumbHeight(trackHeight)),
                    Color.argb(theme.accent().withAlpha(0.7f)));
        }
    }

    private void renderSettings(Painter painter, ThemeTokens theme) {
        Rect area = layout.settings();
        if (area.width() <= 0) {
            return;
        }
        painter.roundPanel(area, theme.radiusPanel(), Color.argb(theme.surface().withAlpha(0.55f)),
                Color.argb(theme.border()));
        ModuleCategory category = runtime.screenModel().selectedCategory();
        String title = tr(category.langKey());
        painter.text(title, area.x() + 10, area.y() + 8, Color.argb(theme.textPrimary()), true);
        // One caption line per section: it explains what the pane is for and keeps a
        // two-control tab from looking broken (missing keys simply render nothing).
        String hintKey = category.langKey() + ".hint";
        String hint = tr(hintKey);
        if (!hint.equals(hintKey)) {
            painter.text(fit(painter, hint, area.width() - 20), area.x() + 10, area.y() + 20,
                    Color.argb(theme.textSecondary()), false);
        }
        // Short accent rule under the title block, full-width hairline below it.
        painter.fill(new Rect(area.x() + 10, area.y() + 32, Math.min(28, painter.textWidth(title)), 2),
                Color.argb(theme.accent().withAlpha(0.8f)));
        painter.fill(new Rect(area.x() + 10, area.y() + 36, area.width() - 20, 1),
                Color.argb(theme.border()));
        painter.pushClip(area.inset(0, 38, 0, 0));
        for (Widget widget : settingsWidgets) {
            widget.render(painter, theme);
        }
        painter.popClip();
        // A scrollable pane without a scrollbar looks broken: nothing tells the user that
        // controls continue below the fold.
        if (settingsScroll.needsScrollbar()) {
            int trackHeight = area.height() - 46;
            int thumbY = settingsScroll.thumbY(area.y() + 42, trackHeight);
            painter.fill(new Rect(area.right() - 5, thumbY, 2,
                    settingsScroll.thumbHeight(trackHeight)),
                    theme.accent().withAlpha(0.7f).argb());
        }
    }

    /** Truncates with an ellipsis without needing text metrics outside the painter. */
    private static String fit(Painter painter, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (painter.textWidth(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (painter.textWidth(sb.toString() + c + "…") > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + "…";
    }

    private void renderTooltip(Painter painter, ThemeTokens theme, String text) {
        List<String> lines = TooltipController.wrap(text, 48);
        int lineHeight = painter.lineHeight();
        int boxWidth = 0;
        for (String line : lines) {
            boxWidth = Math.max(boxWidth, painter.textWidth(line));
        }
        int boxHeight = lines.size() * lineHeight + 6;
        int x = Math.min(tooltips.x() + 8, Math.max(4, width - boxWidth - 12));
        int y = Math.min(tooltips.y() + 10, Math.max(4, height - boxHeight - 8));
        Rect box = new Rect(x, y, boxWidth + 8, boxHeight);
        painter.roundPanel(box, theme.radiusControl(), Color.argb(theme.surface().withAlpha(0.98f)),
                Color.argb(theme.border()));
        int textY = box.y() + 3;
        for (String line : lines) {
            painter.text(line, box.x() + 4, textY, Color.argb(theme.textPrimary()), false);
            textY += lineHeight;
        }
    }

    private void updateHover(int mouseX, int mouseY) {
        for (Widget widget : chrome) {
            if (widget instanceof ButtonWidget button) {
                button.setHovered(button.hit(mouseX, mouseY));
            } else if (widget instanceof ToggleWidget toggle) {
                toggle.setHovered(toggle.hit(mouseX, mouseY));
            }
        }
    }

    private String tooltipAt(int mouseX, int mouseY) {
        for (Widget widget : settingsWidgets) {
            if (widget.tooltipKey() != null && widget.hit(mouseX, mouseY)) {
                return widget.tooltipKey();
            }
            if (widget instanceof ToggleWidget toggle && !toggle.enabled() 
                    && toggle.disabledReasonKey() != null && toggle.hit(mouseX, mouseY)) {
                return toggle.disabledReasonKey();
            }
        }
        return null;
    }

    /**
     * A "selected module" pane for the ported modules: the list row selects, this switch
     * turns it on. Without it the module list was read-only and the only switch lived on a
     * screen the player had to know about.
     */
    private int addSoupModuleSwitch(Rect area, int y, int width) {
        String id = runtime.screenModel().selectedModuleId();
        if (id == null || !id.startsWith("soup.")) {
            return y;
        }
        ModuleRegistry.Entry entry = runtime.modules().get(id).orElse(null);
        if (entry == null) {
            return y;
        }
        boolean enabled = entry.state() == silkvis.core.ModuleState.ENABLED;
        String key = id.substring("soup.".length());
        String label = tr(entry.descriptor().displayKey());
        settingsWidgets.add(new ButtonWidget(
                new Rect(area.x(), y, Math.min(width, 260), ROW_HEIGHT),
                tr("silkvis.soup.power") + ": " + label + " ("
                        + tr(enabled ? "silkvis.soup.on" : "silkvis.soup.off") + ")", () -> {
            if (enabled) {
                runtime.modules().disable(id);
            } else {
                runtime.modules().enable(id);
            }
            rebuildSettings();
        }).style(enabled ? ButtonWidget.Style.PRIMARY : ButtonWidget.Style.SECONDARY));
        y += ROW_HEIGHT + 6;
        // The per-setting menu is reachable from the row too: selecting a module and then
        // hunting for its settings in a different tab was the reason the whole port looked
        // unfinished.
        settingsWidgets.add(new ButtonWidget(new Rect(area.x(), y, Math.min(width, 260),
                ROW_HEIGHT), tr("silkvis.soup.open"), () -> {
            if (client != null) {
                // Open on this module, not on the first one in the list: the player clicked
                // this row and expects that module's settings.
                client.setScreen(new SoupSettingsScreen(this, id));
            }
        }));
        y += ROW_HEIGHT + 10;
        String hintKey = "module." + key + ".desc";
        String hint = tr(hintKey);
        if (!hint.equals(hintKey)) {
            settingsWidgets.add(new FixedText(hint, new Rect(area.x(), y, width, 24)));
            y += 28;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        // Clicking a control hands it keyboard focus (UI-06). Without this the search field
        // accepted clicks but typing went nowhere: only Tab ever set focus.
        focusOnHit(settingsWidgets, mouseX, mouseY);
        focusOnHit(chrome, mouseX, mouseY);
        for (Widget widget : settingsWidgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (Widget widget : chrome) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        Rect list = layout.moduleList();
        if (button == 0 && list.contains((int) mouseX, (int) mouseY) && hoveredRow >= 0) {
            List<ModuleDescriptor> modules = runtime.screenModel().visibleModules(localizer());
            if (hoveredRow < modules.size()) {
                runtime.screenModel().selectModule(modules.get(hoveredRow).id());
                return true;
            }
        }
        if (button == 0) {
            focus.clearFocus();
        }
        return super.mouseClicked(click, doubled);
    }

    private void focusOnHit(List<Widget> widgets, double mouseX, double mouseY) {
        for (Widget widget : widgets) {
            if (widget.focusable() && widget.hit(mouseX, mouseY)) {
                focus.focus(widget);
                return;
            }
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        double deltaX = offsetX;
        double deltaY = offsetY;
        for (Widget widget : settingsWidgets) {
            if (widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        for (Widget widget : settingsWidgets) {
            if (widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (Widget widget : chrome) {
            if (widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layout.moduleList().contains((int) mouseX, (int) mouseY)) {
            return listScroll.scroll(verticalAmount);
        }
        if (layout.settings().contains((int) mouseX, (int) mouseY)) {
            if (!settingsScroll.scroll(verticalAmount)) {
                return false;
            }
            // Positions are absolute, so the pane is rebuilt at the new offset. A wheel tick
            // costs a few dozen allocations; a per-frame translation of every widget would
            // have to be mirrored in hit testing as well.
            rebuildSettings();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int modifiers = input.modifiers();
        if (keyCode == Keys.ESCAPE) {
            if (focus.focused() != null) {
                focus.clearFocus();
                return true;
            }
            close();
            return true;
        }
        if (keyCode == Keys.TAB) {
            return focus.tab((modifiers & Keys.MOD_SHIFT) != 0);
        }
        Widget focused = focus.focused();
        if (focused instanceof TextInputWidget target && (modifiers & Keys.MOD_CONTROL) != 0
                && keyCode == Keys.V) {
            // The custom text model has no clipboard of its own; paste is wired here because
            // only the screen can reach the client clipboard. Retyping a long link by hand
            // was the obvious way to make "add by URL" useless.
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            String clean = clip == null ? "" : clip.replaceAll("\\s+", "");
            if (!clean.isEmpty()) {
                target.model().insert(clean);
                return true;
            }
        }
        if (focused != null && focused.keyPressed(keyCode, modifiers)) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        Widget focused = focus.focused();
        String typed = input == null ? "" : input.asString();
        if (focused != null && !typed.isEmpty()) {
            boolean handled = false;
            for (int i = 0; i < typed.length(); i++) {
                handled |= focused.charTyped(typed.charAt(i));
            }
            if (handled) {
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Non-interactive text block used for informational rows. */
    private static final class FixedText extends Widget {
        private final String text;

        FixedText(String text, Rect bounds) {
            super(bounds);
            this.text = text;
        }

        @Override
        public void render(Painter painter, ThemeTokens theme) {
            List<String> lines = TooltipController.wrap(text, 40);
            int y = bounds.y();
            for (String line : lines) {
                painter.text(line, bounds.x(), y, Color.argb(theme.textSecondary()), false);
                y += painter.lineHeight() + 2;
            }
        }
    }

    /** Unused helper kept for symmetry with the settings pane insets. */
    private static Insets settingsInsets() {
        return Insets.all(8);
    }
}

package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import silkvis.config.GlobalConfig;
import silkvis.config.VisualPreset;
import silkvis.core.ModuleCategory;
import silkvis.core.ModuleDescriptor;
import silkvis.core.ModuleRegistry;
import silkvis.map.MapSettings;
import silkvis.render.VanillaPainter;
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
    private final ScrollModel listScroll = new ScrollModel(0, 24);
    private final ScrollModel settingsScroll = new ScrollModel(0, 28);
    private final List<Widget> chrome = new ArrayList<>();
    private final List<Widget> settingsWidgets = new ArrayList<>();
    private final List<ButtonWidget> navButtons = new ArrayList<>();

    private MainScreenLayout layout;
    private TextInputWidget search;
    private int hoveredRow = -1;

    public SilkVisScreen() {
        super(Text.translatable("silkvis.screen.title"));
    }

    private Localizer localizer() {
        return key -> Text.translatable(key).getString();
    }

    private String tr(String key) {
        return localizer().get(key);
    }

    @Override
    protected void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        layout = MainScreenLayout.compute(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        chrome.clear();
        navButtons.clear();
        settingsWidgets.clear();

        List<ModuleCategory> tabs = MainScreenModel.p1Tabs();
        Rect nav = layout.navigation();
        int navY = nav.y() + 4;
        int navItemWidth = Math.max(0, nav.width() - 8);
        for (ModuleCategory tab : tabs) {
            Rect row = new Rect(nav.x() + 4, navY, navItemWidth, 24);
            ButtonWidget button = new ButtonWidget(row, tr(tab.langKey()), () -> {
                runtime.screenModel().select(tab);
                rebuildSettings();
            });
            if (runtime.screenModel().selectedCategory() == tab) {
                button.style(ButtonWidget.Style.PRIMARY);
            }
            button.setEnabled(navItemWidth >= 40);
            chrome.add(button);
            navButtons.add(button);
            navY += 28;
        }

        Rect closeBounds = layout.closeButton();
        chrome.add(new ButtonWidget(closeBounds, "×", this::close)
                .style(ButtonWidget.Style.SECONDARY));

        TextInputModel searchModel = new TextInputModel(runtime.screenModel().query(), 48, s -> true);
        search = new TextInputWidget(layout.search(), searchModel, "silkvis.search.hint", value -> {
            runtime.screenModel().setQuery(value);
            rebuildSettings();
        });
        chrome.add(search);

        listScroll.setContentHeight(0);
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
        int y = area.y() + 22;
        int width = Math.max(80, area.width() - 8);

        switch (category) {
            case HUD -> {
                for (HudWidgetId id : HudWidgetId.values()) {
                    if (!id.p1()) {
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
            }
            case MUSIC -> {
                GlobalConfig global = runtime.global();
                y = addToggle(area, y, width, tr("silkvis.media.enabled"), tr("silkvis.media.enabled.desc"),
                        global.media().enabled(), value -> {
                            var media = global.media();
                            runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                                    global.reducedMotion(), global.blurEnabled(), global.verboseDiagnostics(),
                                    new silkvis.config.MediaConfig(value, media.selection(), media.pinnedAppId(),
                                            media.hudWidgetEnabled(), media.pauseSourceOnWorldExit())));
                        });
                y = addToggle(area, y, width, tr("silkvis.media.hud_widget"),
                        tr("silkvis.media.hud_widget.desc"), global.media().hudWidgetEnabled(), value -> {
                            var media = global.media();
                            runtime.requestGlobalSave(new GlobalConfig(global.language(), global.preset(),
                                    global.reducedMotion(), global.blurEnabled(), global.verboseDiagnostics(),
                                    new silkvis.config.MediaConfig(media.enabled(), media.selection(),
                                            media.pinnedAppId(), value, media.pauseSourceOnWorldExit())));
                        });
            }
            case VISUAL -> {
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
            }
            case INTEGRATIONS -> {
                String mode = runtime.integrations()
                        .shaderMode(false).name().toLowerCase(Locale.ROOT);
                settingsWidgets.add(new FixedText(tr("silkvis.integrations.mode") + ": " + mode,
                        new Rect(area.x(), y + 6, width, 16)));
            }
            case AUTOMATION_P2 -> settingsWidgets.add(new FixedText(
                    tr("silkvis.automation.planned_note"), new Rect(area.x(), y + 6, width, 32)));
            default -> {
                // VISUAL handled above; nothing extra.
            }
        }
        focus.setScope(collectFocusable());
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
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();
        painter.fill(new Rect(0, 0, width, height), Color.argb(theme.background().withAlpha(0.55f)));
        Rect window = layout.window();
        painter.fill(window, Color.argb(theme.background()));
        painter.outline(window, Color.argb(theme.border()), 1);

        renderNavigation(painter, theme);
        renderModuleList(painter, theme, mouseX, mouseY);
        renderSettings(painter, theme);
        for (Widget widget : chrome) {
            widget.render(painter, theme);
        }
        painter.fill(layout.footer(), Color.argb(theme.surface().withAlpha(0.35f)));
        painter.text(tr("silkvis.footer.hint"), layout.footer().x() + 4,
                layout.footer().centerY() - painter.lineHeight() / 2, Color.argb(theme.textSecondary()), false);

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
        painter.fill(nav, Color.argb(theme.surface().withAlpha(0.5f)));
        painter.text(tr("silkvis.screen.title"), nav.x() + 4, layout.window().y() + 10,
                Color.argb(theme.accent()), true);
    }

    private void renderModuleList(Painter painter, ThemeTokens theme, int mouseX, int mouseY) {
        if (layout.mode() == MainScreenLayout.Mode.NARROW) {
            return;
        }
        Rect list = layout.moduleList();
        if (list.width() <= 0) {
            return;
        }
        painter.fill(list, Color.argb(theme.surface().withAlpha(0.35f)));
        List<ModuleDescriptor> modules = runtime.screenModel().visibleModules(localizer());
        int rowY = list.y() + 4 - listScroll.offset();
        hoveredRow = -1;
        for (int i = 0; i < modules.size(); i++) {
            ModuleDescriptor module = modules.get(i);
            Rect row = new Rect(list.x() + 4, rowY, list.width() - 8, ROW_HEIGHT);
            if (row.bottom() > list.y() && row.y() < list.bottom() && row.contains(mouseX, mouseY)) {
                hoveredRow = i;
                painter.fill(row, Color.argb(theme.surfaceHover()));
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
        painter.fill(area, Color.argb(theme.surface().withAlpha(0.55f)));
        ModuleCategory category = runtime.screenModel().selectedCategory();
        painter.text(tr(category.langKey()), area.x() + 8, area.y() + 6, Color.argb(theme.textPrimary()), true);
        painter.fill(new Rect(area.x() + 8, area.y() + 18, area.width() - 16, 1),
                Color.argb(theme.border()));
        painter.pushClip(area.inset(0, 20, 0, 0));
        for (Widget widget : settingsWidgets) {
            widget.render(painter, theme);
        }
        painter.popClip();
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
        painter.fill(box, Color.argb(theme.surface().withAlpha(0.98f)));
        painter.outline(box, Color.argb(theme.border()), 1);
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

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
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
        return super.mouseClicked(click, doubled);
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
            return settingsScroll.scroll(verticalAmount);
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

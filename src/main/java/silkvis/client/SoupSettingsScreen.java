package silkvis.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import silkvis.render.VanillaPainter;
import silkvis.ui.Color;
import silkvis.ui.MainScreenLayout;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ScrollModel;
import silkvis.ui.ThemeTokens;
import silkvis.ui.widget.ButtonWidget;
import silkvis.ui.widget.Keys;
import silkvis.ui.widget.TextInputModel;
import silkvis.ui.widget.TextInputWidget;
import silkvis.ui.widget.Widget;
import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.setting.BooleanSetting;
import silkvis.visual.soup.setting.ButtonSetting;
import silkvis.visual.soup.setting.ColorSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.MultiColorSetting;
import silkvis.visual.soup.setting.MultiSelectSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.Setting;
import silkvis.visual.soup.setting.SoupSettingsFile;
import silkvis.visual.soup.setting.ValueSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Settings screen for the ported SoupVisuals modules.
 *
 * <p>Why it exists: the modules were ported with their whole settings tree, but nothing in
 * the interface read it. Every module therefore stayed off, and the only way to switch one on
 * was a line of Java compiled into the mod. The owner's report was exactly "the whole visual
 * is invisible, where is the menu". This is that menu: the module list on the left, the
 * selected module's own settings on the right, built from the settings objects themselves.</p>
 *
 * <p>It reuses SilkVis' layout, painter, widgets and theme, so the ported settings look like
 * the rest of the client instead of introducing a second UI style, and they follow the
 * keyboard/contrast rules the rest of the client already follows.</p>
 *
 * <p>Scrolling moves the widgets themselves ({@link Widget#setBounds}), not just the drawing
 * origin: with a drawing-only offset a scrolled button keeps its old hit box and clicks land
 * on the wrong row, which is a bug this project has already shipped once.</p>
 */
public final class SoupSettingsScreen extends Screen {
    private static final int MODULE_ROW = 22;
    private static final int CONTROL_HEIGHT = 20;
    /** Height of the title/power block at the top of the settings pane. */
    private static final int CONTENT_TOP = 62;

    private final SilkVisRuntime runtime = SilkVisRuntime.get();
    private final SoupVisualsClient soup = SoupVisualsClient.get();
    private final Screen parent;

    private MainScreenLayout layout;
    private final List<Widget> chrome = new ArrayList<>();
    private final List<Widget> settingWidgets = new ArrayList<>();
    private final List<Widget> powerWidgets = new ArrayList<>();
    private ScrollModel listScroll = new ScrollModel(1, MODULE_ROW);
    private ScrollModel settingsScroll = new ScrollModel(1, 24);
    /**
     * Un-scrolled top edge of every control. Without it each scroll event would shift the
     * widgets from their already-shifted position and the list would drift away from the
     * content height it is clamped against.
     */
    private final java.util.Map<Widget, Integer> restingTop = new java.util.IdentityHashMap<>();
    private TextInputWidget search;
    private SoupModule selected;
    private String query = "";
    private int hoveredModule = -1;
    /** Module id requested by the caller (the module list link), applied on first init. */
    private String initialModuleId;

    public SoupSettingsScreen(Screen parent) {
        // Literal, not Text.translatable("silkvis.soup.title"): Screen.title is only used for
        // narration here, and translatable() would have to resolve a key this screen resolves
        // itself through the mod's own language table.
        super(Text.literal("SoupVisuals"));
        this.parent = parent;
    }

    /** Opens the screen on a specific module, by registry id or by module key. */
    public SoupSettingsScreen(Screen parent, String moduleId) {
        this(parent);
        this.initialModuleId = moduleId;
    }

    private String tr(String key) {
        if (key == null) {
            return "";
        }
        String text = this.runtime.localizer().get(key);
        return text == null ? "" : text;
    }

    private static String moduleName(SoupModule module) {
        String text = module.getName();
        return text == null || text.isEmpty() ? module.getNameKey() : text;
    }

    /**
     * Label through the setting's own resolver: {@link Setting#getName()} is what the original
     * menu prints, and it also handles the auto-generated keys and the group prefix. Only when
     * it falls back to the raw key (no translation installed) is the bare option name used, so
     * a row is never blank.
     */
    private static String settingLabel(Setting setting) {
        String text = setting.getName();
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (!text.equals(setting.getNameKey())) {
            return text;
        }
        String tail = setting.getNameKey();
        if (tail.startsWith("setting.")) {
            tail = tail.substring("setting.".length());
        }
        if (tail.endsWith(".name")) {
            tail = tail.substring(0, tail.length() - ".name".length());
        }
        int dot = tail.indexOf('.');
        return dot >= 0 ? tail.substring(dot + 1) : tail;
    }

    /** Option labels are localized by the select itself ({@code select.option.<name>}). */
    private static String optionLabel(SelectSetting select, String option) {
        int index = select.getList().indexOf(option);
        List<String> localized = select.getListLocalized();
        if (index >= 0 && localized != null && index < localized.size()) {
            return localized.get(index);
        }
        return option;
    }

    @Override
    protected void init() {
        this.layout = MainScreenLayout.compute(this.width, this.height);
        this.chrome.clear();
        if (this.initialModuleId != null) {
            String wanted = this.initialModuleId.startsWith("soup.")
                    ? this.initialModuleId.substring("soup.".length()) : this.initialModuleId;
            for (SoupModule module : this.soup.modules()) {
                if (SoupSettingsFile.keyOf(module).equals(wanted)) {
                    this.selected = module;
                    break;
                }
            }
            this.initialModuleId = null;
        }
        if (this.selected == null && !this.soup.modules().isEmpty()) {
            this.selected = this.soup.modules().get(0);
        }
        Rect list = moduleListArea();
        this.listScroll = new ScrollModel(Math.max(1, list.height() - 8), MODULE_ROW);
        this.chrome.add(new ButtonWidget(this.layout.closeButton(), "\u00d7", this::close)
                .style(ButtonWidget.Style.SECONDARY));
        // The third argument is a validator, not a change listener: a statement lambda made
        // the constructor call uncompilable ("missing return value"). Filtering happens in
        // rebuildSettings()/visibleModules() from the field the commit callback writes.
        TextInputModel searchModel = new TextInputModel("", 32, value -> true);
        this.search = new TextInputWidget(this.layout.search(), searchModel,
                tr("silkvis.search.hint"), value -> {
            this.query = value == null ? "" : value;
            rebuildSettings();
        });
        this.chrome.add(this.search);
        rebuildSettings();
    }

    /** The module list pane; the compact and narrow layouts borrow the navigation column. */
    private Rect moduleListArea() {
        Rect list = this.layout.moduleList();
        if (list.width() > 0) {
            return list;
        }
        Rect nav = this.layout.navigation();
        return new Rect(nav.x(), nav.y() + 26, nav.width(), Math.max(0, nav.height() - 30));
    }

    /** Modules matching the search box; the list, the arrows and the wheel all use this order. */
    private List<SoupModule> visibleModules() {
        String needle = this.query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return this.soup.modules();
        }
        List<SoupModule> out = new ArrayList<>();
        for (SoupModule module : this.soup.modules()) {
            if (moduleName(module).toLowerCase(Locale.ROOT).contains(needle)
                    || module.getNameKey().contains(needle)) {
                out.add(module);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ settings tree

    /**
     * Builds the controls of the selected module from its own settings tree. Groups become
     * captions (the original indents them; a caption carries the same information in one
     * scrollable column), hidden settings are skipped, which is what makes dependent options
     * behave.
     */
    private void rebuildSettings() {
        this.settingWidgets.clear();
        this.powerWidgets.clear();
        this.restingTop.clear();
        if (this.selected == null) {
            return;
        }
        Rect area = this.layout.settings();
        if (area.width() <= 0) {
            return;
        }
        buildPowerSwitch();
        Rect content = area.inset(8, 8);
        int width = Math.max(80, content.width() - 8);
        int y = area.y() + CONTENT_TOP;
        for (Setting setting : this.selected.getRootSettings()) {
            y = addSetting(setting, content, y, width);
        }
        // Content height is measured from the children themselves: a guessed height leaves
        // either a dead scroll area or a clipped tail.
        int bottom = y;
        for (Widget widget : this.settingWidgets) {
            bottom = Math.max(bottom, widget.bounds().bottom());
        }
        this.settingsScroll.setContentHeight(Math.max(0, bottom - (area.y() + CONTENT_TOP) + 8));
        applySettingOffset();
        this.hoveredModule = -1;
    }

    /** Moves every setting control by the current scroll offset. */
    private void applySettingOffset() {
        int shift = this.layout.settings().y() + CONTENT_TOP - this.settingsScroll.offset();
        for (Widget widget : this.settingWidgets) {
            Rect b = widget.bounds();
            widget.setBounds(new Rect(b.x(), originFor(widget) + shift, b.width(), b.height()));
        }
    }

    private int originFor(Widget widget) {
        return restingTop.getOrDefault(widget, widget.bounds().y());
    }

    private void place(Widget widget) {
        restingTop.put(widget, widget.bounds().y());
        this.settingWidgets.add(widget);
    }

    private void buildPowerSwitch() {
        Rect area = this.layout.settings();
        boolean enabled = this.selected.isEnabled();
        // HandTweaks has no injection point in 1.21.10 (ADR-0010): its settings are real and
        // editable, but switching it on cannot draw anything, and saying so is better than
        // letting a player wonder why nothing happens.
        String suffix = this.selected.getNameKey().equals("module.handtweaks.name")
                ? " \u00b7 " + tr("silkvis.soup.settings_only") : "";
        String label = tr("silkvis.soup.power") + ": " + moduleName(this.selected) + " ("
                + tr(enabled ? "silkvis.soup.on" : "silkvis.soup.off") + ")" + suffix;
        ButtonWidget power = new ButtonWidget(
                new Rect(area.x() + 8, area.y() + 8, Math.max(80, area.width() - 16), 20), label,
                () -> {
            setModuleEnabled(this.selected, !this.selected.isEnabled());
            rebuildSettings();
        });
        power.style(enabled ? ButtonWidget.Style.PRIMARY : ButtonWidget.Style.SECONDARY);
        this.powerWidgets.add(power);
    }

    private void setModuleEnabled(SoupModule module, boolean enabled) {
        String id = "soup." + SoupSettingsFile.keyOf(module);
        if (this.runtime.modules().get(id).isPresent()) {
            if (enabled) {
                this.runtime.modules().enable(id);
            } else {
                this.runtime.modules().disable(id);
            }
        } else {
            module.setEnabled(enabled);
        }
        this.runtime.saveSoupSettings();
    }

    private int addSetting(Setting setting, Rect area, int y, int width) {
        if (!setting.isVisible()) {
            return y;
        }
        if (setting instanceof GroupSetting group) {
            // A group header with a checkbox keeps its own on/off value in the original menu;
            // a plain header is only a caption.
            if (group.isCheckbox()) {
                final boolean current = group.isValue();
                y = addToggle(area, y, width, settingLabel(setting), current, updated -> {
                    group.setValue(updated);
                    rebuildAfterChange();
                });
            } else {
                y = addCaption(area, y, width, settingLabel(setting));
            }
            for (Setting child : group.getSubSettings()) {
                y = addSetting(child, area, y, width);
            }
            return y + 4;
        }
        String label = settingLabel(setting);
        if (setting instanceof BooleanSetting bool) {
            final boolean current = bool.isValue();
            return addToggle(area, y, width, label, current, updated -> {
                bool.setValue(updated);
                rebuildAfterChange();
            });
        }
        if (setting instanceof ValueSetting value) {
            double min = value.getMin();
            double max = value.getMax();
            if (max <= min) {
                max = min + 1.0;
            }
            double step = Math.max((max - min) / 100.0, value.isInteger() ? 1.0 : 0.01);
            return addStepper(area, y, width, label, min, max, step, value.getValue(), updated -> {
                value.setValue((float) updated);
                rebuildAfterChange();
            });
        }
        if (setting instanceof SelectSetting select) {
            List<String> options = select.getList();
            if (options.isEmpty()) {
                return y;
            }
            List<String> labels = new ArrayList<>();
            for (String option : options) {
                labels.add(optionLabel(select, option));
            }
            int index = options.indexOf(select.getSelected());
            return addCycler(area, y, width, label, labels,
                    index >= 0 ? labels.get(index) : String.valueOf(select.getSelected()),
                    chosen -> {
                        int target = labels.indexOf(chosen);
                        select.setSelected(options.get(target < 0 ? 0 : target));
                        rebuildAfterChange();
                    });
        }
        if (setting instanceof MultiSelectSetting multi) {
            for (String option : multi.getList()) {
                final String entry = option;
                y = addToggle(area, y, width, label + ": " + option, multi.isSelected(option),
                        updated -> {
                            multi.toggle(entry);
                            rebuildAfterChange();
                        });
            }
            return y + 4;
        }
        if (setting instanceof MultiColorSetting palette) {
            for (ColorSetting slot : List.of(palette.getColor1(), palette.getColor2(),
                    palette.getColor3(), palette.getColor4())) {
                if (slot != null && slot.isVisible()) {
                    y = addColor(area, y, width, settingLabel(slot), slot);
                }
            }
            return y;
        }
        if (setting instanceof ColorSetting color) {
            return addColor(area, y, width, label, color);
        }
        if (setting instanceof ButtonSetting button) {
            // The original draws these as buttons ("Test sound", "Reset"); pressing one runs
            // its action, which is why they are not persisted as values.
            ButtonWidget widget = new ButtonWidget(
                    new Rect(area.x(), y, Math.min(width, 220), CONTROL_HEIGHT), label, () -> {
                button.press();
                rebuildAfterChange();
            });
            place(widget);
            return y + CONTROL_HEIGHT + 4;
        }
        return y;
    }

    private int addCaption(Rect area, int y, int width, String text) {
        if (text == null || text.isEmpty()) {
            return y;
        }
        place(new Caption(text, new Rect(area.x(), y, width, 14)));
        return y + 18;
    }

    private int addToggle(Rect area, int y, int width, String label, boolean initial,
                          Consumer<Boolean> onChange) {
        ButtonWidget toggle = new ButtonWidget(new Rect(area.x(), y, width, CONTROL_HEIGHT),
                label + "   " + tr(initial ? "silkvis.soup.on" : "silkvis.soup.off"),
                () -> onChange.accept(!initial));
        toggle.style(initial ? ButtonWidget.Style.PRIMARY : ButtonWidget.Style.SECONDARY);
        place(toggle);
        return y + CONTROL_HEIGHT + 4;
    }

    /**
     * Two buttons instead of a drag slider: ported values step by 0.01..100 and a click is
     * exact where dragging a 2 px thumb on a 300 px track is not. Bounds are clamped, so a step
     * at the edge cannot write an out-of-range value.
     */
    private int addStepper(Rect area, int y, int width, String label, double min, double max,
                           double step, double initial, DoubleConsumer onChange) {
        int buttonWidth = Math.max(20, width / 6);
        double down = Math.max(min, initial - step);
        double up = Math.min(max, initial + step);
        place(new ButtonWidget(new Rect(area.x(), y, buttonWidth, CONTROL_HEIGHT),
                "-", () -> onChange.accept(down)));
        place(new ButtonWidget(new Rect(area.x() + buttonWidth + 4, y,
                Math.max(40, width - buttonWidth * 2 - 8), CONTROL_HEIGHT),
                label + ": " + trim(initial), null));
        place(new ButtonWidget(new Rect(area.right() - buttonWidth, y, buttonWidth, CONTROL_HEIGHT),
                "+", () -> onChange.accept(up)));
        return y + CONTROL_HEIGHT + 4;
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-4) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** Fixed palette cycle: a full colour picker is a separate widget and a separate task. */
    private static final int[] PRESET_COLORS = {
            0xFFFFFFFF, 0xFF00FF00, 0xFFFF0000, 0xFF00BFFF, 0xFFFFD700, 0xFFFF69B4,
            0xFF8A2BE2, 0xFFFF8C00
    };

    private int addColor(Rect area, int y, int width, String label, ColorSetting color) {
        int index = 0;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (PRESET_COLORS[i] == color.getColor()) {
                index = i;
                break;
            }
        }
        int next = PRESET_COLORS[(index + 1) % PRESET_COLORS.length];
        String current = String.format(Locale.ROOT, "#%08X", color.getColor());
        String nextText = String.format(Locale.ROOT, "#%08X", next);
        return addCycler(area, y, width, label, List.of(current, nextText), current, chosen -> {
            color.setColor((int) Long.parseLong(chosen.substring(1), 16));
            rebuildAfterChange();
        });
    }

    /** Cycles forward through the options; the original uses a dropdown, a click is quicker. */
    private int addCycler(Rect area, int y, int width, String label, List<String> options,
                          String current, Consumer<String> onChange) {
        if (options.isEmpty()) {
            return y;
        }
        int index = Math.max(0, options.indexOf(current));
        String next = options.get((index + 1) % options.size());
        String previous = options.get((index - 1 + options.size()) % options.size());
        int buttonWidth = Math.max(20, width / 7);
        place(new ButtonWidget(new Rect(area.x(), y, buttonWidth, CONTROL_HEIGHT),
                "<", () -> onChange.accept(previous)));
        place(new ButtonWidget(new Rect(area.x() + buttonWidth + 4, y,
                Math.max(60, width - buttonWidth - 4), CONTROL_HEIGHT),
                label + ": " + current, () -> onChange.accept(next)));
        return y + CONTROL_HEIGHT + 4;
    }

    /** A value changed: dependent visibility may have changed, and the file is written now. */
    private void rebuildAfterChange() {
        this.runtime.saveSoupSettings();
        int offset = this.settingsScroll.offset();
        rebuildSettings();
        this.settingsScroll.setOffset(offset);
        applySettingOffset();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = this.runtime.theme();
        painter.fill(new Rect(0, 0, this.width, this.height),
                theme.background().withAlpha(0.55f).argb());
        Rect window = this.layout.window();
        for (int layer = 3; layer >= 1; layer--) {
            painter.roundRect(window.inset(-layer * 2, -layer * 2), theme.radiusPanel() + layer * 2,
                    Color.argb(theme.background().withAlpha(0.10f)));
        }
        painter.roundPanel(window, theme.radiusPanel(), Color.argb(theme.background()),
                Color.argb(theme.border()));
        painter.text(tr("silkvis.soup.title"), window.x() + 14, window.y() + 10,
                Color.argb(theme.accent()), true);
        renderModuleList(painter, theme, mouseX, mouseY);
        renderSettings(painter, theme);
        for (Widget widget : this.powerWidgets) {
            widget.render(painter, theme);
        }
        for (Widget widget : this.chrome) {
            widget.render(painter, theme);
        }
        Rect footer = this.layout.footer();
        painter.roundPanel(footer, theme.radiusControl() + 2,
                Color.argb(theme.surface().withAlpha(0.45f)), Color.argb(theme.border()));
        painter.text(tr("silkvis.soup.footer"), footer.x() + 6,
                footer.centerY() - painter.lineHeight() / 2,
                Color.argb(theme.textSecondary()), false);
    }

    private void renderModuleList(Painter painter, ThemeTokens theme, int mouseX, int mouseY) {
        Rect list = moduleListArea();
        if (list.width() <= 0) {
            return;
        }
        painter.roundPanel(list, theme.radiusPanel(),
                Color.argb(theme.surface().withAlpha(0.35f)),
                Color.argb(theme.border().multiplyAlpha(0.6f)));
        List<SoupModule> modules = visibleModules();
        painter.pushClip(list.inset(0, 4, 0, 4));
        int y = list.y() + 4 - this.listScroll.offset();
        this.hoveredModule = -1;
        for (int i = 0; i < modules.size(); i++) {
            SoupModule module = modules.get(i);
            Rect row = new Rect(list.x() + 4, y, Math.max(0, list.width() - 8), 18);
            boolean inside = row.y() >= list.y() && row.bottom() <= list.bottom();
            if (inside && row.contains(mouseX, mouseY)) {
                this.hoveredModule = i;
                painter.roundRect(row, theme.radiusControl(), Color.argb(theme.surfaceHover()));
            }
            boolean current = module == this.selected;
            boolean enabled = module.isEnabled();
            painter.text(moduleName(module), row.x() + 4,
                    row.centerY() - painter.lineHeight() / 2,
                    Color.argb(current ? theme.accent() : theme.textPrimary()), false);
            String state = tr(enabled ? "silkvis.soup.on" : "silkvis.soup.off");
            painter.text(state, row.right() - painter.textWidth(state) - 4,
                    row.centerY() - painter.lineHeight() / 2,
                    Color.argb(enabled ? theme.success() : theme.textSecondary()), false);
            y += MODULE_ROW;
        }
        painter.popClip();
        this.listScroll.setContentHeight(modules.size() * MODULE_ROW + 8);
        if (this.listScroll.needsScrollbar()) {
            int trackHeight = list.height() - 8;
            painter.fill(new Rect(list.right() - 4,
                    this.listScroll.thumbY(list.y() + 4, trackHeight), 2,
                    this.listScroll.thumbHeight(trackHeight)),
                    theme.accent().withAlpha(0.7f).argb());
        }
    }

    private void renderSettings(Painter painter, ThemeTokens theme) {
        Rect area = this.layout.settings();
        if (area.width() <= 0) {
            return;
        }
        painter.roundPanel(area, theme.radiusPanel(),
                Color.argb(theme.surface().withAlpha(0.55f)), Color.argb(theme.border()));
        String title = this.selected == null ? "" : moduleName(this.selected);
        String hint = tr("silkvis.soup.hint");
        painter.text(title, area.x() + 10, area.y() + 30, Color.argb(theme.textPrimary()), true);
        painter.text(hint, area.x() + 10, area.y() + 42, Color.argb(theme.textSecondary()), false);
        painter.fill(new Rect(area.x() + 10, area.y() + 56,
                Math.min(28, Math.max(1, painter.textWidth(title))), 2),
                Color.argb(theme.accent().withAlpha(0.8f)));
        painter.pushClip(new Rect(area.x(), area.y() + CONTENT_TOP, area.width(),
                Math.max(0, area.height() - CONTENT_TOP - 4)));
        for (Widget widget : this.settingWidgets) {
            widget.render(painter, theme);
        }
        painter.popClip();
        if (this.settingsScroll.needsScrollbar()) {
            int trackHeight = area.height() - CONTENT_TOP - 8;
            painter.fill(new Rect(area.right() - 5,
                    this.settingsScroll.thumbY(area.y() + CONTENT_TOP, trackHeight), 2,
                    this.settingsScroll.thumbHeight(trackHeight)),
                    theme.accent().withAlpha(0.7f).argb());
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        // Clicking the field must give it keyboard focus, otherwise typing goes nowhere:
        // the same defect the main window had before focusOnHit() was added.
        if (this.search != null) {
            this.search.setFocused(this.search.hit(mouseX, mouseY));
        }
        for (Widget widget : this.powerWidgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (Widget widget : this.settingWidgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (Widget widget : this.chrome) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        Rect list = moduleListArea();
        if (button == 0 && list.contains((int) mouseX, (int) mouseY) && this.hoveredModule >= 0) {
            List<SoupModule> modules = visibleModules();
            if (this.hoveredModule < modules.size()) {
                selectModule(modules.get(this.hoveredModule));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        // ButtonWidget fires its action on release, so clicks have to be routed here too;
        // without this every button in this screen looked pressed and did nothing.
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        boolean handled = false;
        for (Widget widget : this.powerWidgets) {
            handled |= widget.mouseReleased(mouseX, mouseY, button);
        }
        for (Widget widget : this.settingWidgets) {
            handled |= widget.mouseReleased(mouseX, mouseY, button);
        }
        for (Widget widget : this.chrome) {
            handled |= widget.mouseReleased(mouseX, mouseY, button);
        }
        return handled || super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (moduleListArea().contains((int) mouseX, (int) mouseY)) {
            this.listScroll.scroll(vertical);
            return true;
        }
        if (this.layout.settings().contains((int) mouseX, (int) mouseY)) {
            this.settingsScroll.scroll(vertical);
            applySettingOffset();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean charTyped(CharInput input) {
        // Same shape as SilkVisScreen: CharInput exposes the typed text, the widget API takes
        // one char at a time.
        String typed = input == null ? "" : input.asString();
        if (this.search != null && !typed.isEmpty()) {
            boolean handled = false;
            for (int i = 0; i < typed.length(); i++) {
                handled |= this.search.charTyped(typed.charAt(i));
            }
            if (handled) {
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == Keys.ESCAPE) {
            if (this.search != null && this.search.focused()) {
                // First Escape leaves the field, second closes: closing the screen while the
                // player is still editing a search box is the wrong guess.
                this.search.setFocused(false);
                return true;
            }
            close();
            return true;
        }
        if (this.search != null && this.search.focused() && this.search.keyPressed(key, input.modifiers())) {
            return true;
        }
        List<SoupModule> modules = visibleModules();
        if (modules.isEmpty()) {
            return super.keyPressed(input);
        }
        int current = modules.indexOf(this.selected);
        if (key == Keys.TAB || key == Keys.DOWN) {
            selectModule(modules.get((Math.max(0, current) + 1) % modules.size()));
            return true;
        }
        if (key == Keys.UP) {
            selectModule(modules.get((current <= 0 ? modules.size() : current) - 1));
            return true;
        }
        if (key == Keys.ENTER && this.selected != null) {
            // Enter is the keyboard equivalent of the power switch, so the screen is usable
            // without a mouse (UI-05/UI-06).
            setModuleEnabled(this.selected, !this.selected.isEnabled());
            rebuildSettings();
            return true;
        }
        return super.keyPressed(input);
    }

    private void selectModule(SoupModule module) {
        this.selected = module;
        this.settingsScroll.setOffset(0);
        rebuildSettings();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /** Non-interactive caption for a group header. */
    private static final class Caption extends Widget {
        private final String text;

        Caption(String text, Rect bounds) {
            super(bounds);
            this.text = text;
        }

        @Override
        public void render(Painter painter, ThemeTokens theme) {
            painter.text(text, bounds.x(), bounds.y(), Color.argb(theme.accent().withAlpha(0.9f)),
                    true);
        }
    }
}

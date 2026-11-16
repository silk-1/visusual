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

public final class SoupSettingsScreen extends Screen {
    private static final int MODULE_ROW = 22;
    private static final int CONTROL_HEIGHT = 20;
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
    private final java.util.Map<Widget, Integer> restingTop = new java.util.IdentityHashMap<>();
    private TextInputWidget search;
    private SoupModule selected;
    private String query = "";
    private int hoveredModule = -1;
    private String initialModuleId;

    public SoupSettingsScreen(Screen parent) {
        super(Text.literal("SoupVisuals"));
        this.parent = parent;
    }

    public SoupSettingsScreen(Screen parent, String moduleId) {
        this(parent);
        this.initialModuleId = moduleId;
    }

    private String tr(String key) {
        if (key == null) return "";
        String text = this.runtime.localizer().get(key);
        return text == null ? "" : text;
    }

    private static String moduleName(SoupModule module) {
        String text = module.getName();
        return text == null || text.isEmpty() ? module.getNameKey() : text;
    }

    private static String settingLabel(Setting setting) {
        String text = setting.getName();
        if (text == null || text.isEmpty()) return "";
        if (!text.equals(setting.getNameKey())) return text;
        String tail = setting.getNameKey();
        if (tail.startsWith("setting.")) tail = tail.substring("setting.".length());
        if (tail.endsWith(".name")) tail = tail.substring(0, tail.length() - ".name".length());
        int dot = tail.indexOf('.');
        return dot >= 0 ? tail.substring(dot + 1) : tail;
    }

    private static String optionLabel(SelectSetting select, String option) {
        int index = select.getList().indexOf(option);
        List<String> localized = select.getListLocalized();
        if (index >= 0 && localized != null && index < localized.size()) return localized.get(index);
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
        TextInputModel searchModel = new TextInputModel("", 32, value -> true);
        this.search = new TextInputWidget(this.layout.search(), searchModel,
                tr("silkvis.search.hint"), value -> {
            this.query = value == null ? "" : value;
            rebuildSettings();
        });
        this.chrome.add(this.search);
        rebuildSettings();
    }

    private Rect moduleListArea() {
        Rect list = this.layout.moduleList();
        if (list.width() > 0) return list;
        Rect nav = this.layout.navigation();
        return new Rect(nav.x(), nav.y() + 26, nav.width(), Math.max(0, nav.height() - 30));
    }

    private List<SoupModule> visibleModules() {
        String needle = this.query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return this.soup.modules();
        List<SoupModule> out = new ArrayList<>();
        for (SoupModule module : this.soup.modules()) {
            if (moduleName(module).toLowerCase(Locale.ROOT).contains(needle)
                    || module.getNameKey().contains(needle)) {
                out.add(module);
            }
        }
        return out;
    }

    private void rebuildSettings() {
        this.settingWidgets.clear();
        this.powerWidgets.clear();
        this.restingTop.clear();
        if (this.selected == null) return;
        Rect area = this.layout.settings();
        if (area.width() <= 0) return;
        buildPowerSwitch();
        Rect content = area.inset(8, 8);
        int width = Math.max(80, content.width() - 8);
        int y = area.y() + CONTENT_TOP;
        for (Setting setting : this.selected.getRootSettings()) {
            y = addSetting(setting, content, y, width);
        }
        int bottom = y;
        for (Widget widget : this.settingWidgets) {
            bottom = Math.max(bottom, widget.bounds().bottom());
        }
        this.settingsScroll.setContentHeight(Math.max(0, bottom - (area.y() + CONTENT_TOP) + 8));
        applySettingOffset();
        this.hoveredModule = -1;
    }

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
            if (enabled) this.runtime.modules().enable(id);
            else this.runtime.modules().disable(id);
        } else {
            module.setEnabled(enabled);
        }
        this.runtime.saveSoupSettings();
    }

    private void rebuildAfterChange() {
        this.runtime.saveSoupSettings();
        rebuildSettings();
    }

    private int addSetting(Setting setting, Rect area, int y, int width) {
        if (!setting.isVisible()) return y;
        if (setting instanceof GroupSetting group) {
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
            if (max <= min) max = min + 1.0;
            double step = Math.max((max - min) / 100.0, value.isInteger() ? 1.0 : 0.01);
            return addStepper(area, y, width, label, min, max, step, value.getValue(), updated -> {
                value.setValue((float) updated);
                rebuildAfterChange();
            });
        }
        if (setting instanceof SelectSetting select) {
            List<String> options = select.getList();
            if (options.isEmpty()) return y;
            List<String> labels = new ArrayList<>();
            for (String option : options) labels.add(optionLabel(select, option));
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
        if (text == null || text.isEmpty()) return y;
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
        if (Math.abs(value - Math.rint(value)) < 1.0E-4) return String.valueOf((long) Math.rint(value));
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private int addCycler(Rect area, int y, int width, String label, List<String> options,
                          String current, Consumer<String> onChange) {
        if (options.isEmpty()) return y;
        int index = Math.max(0, options.indexOf(current));
        int buttonWidth = Math.max(20, width / 6);
        String shown = options.get(index);
        place(new ButtonWidget(new Rect(area.x(), y, buttonWidth, CONTROL_HEIGHT), "<",
                () -> onChange.accept(options.get((index - 1 + options.size()) % options.size()))));
        place(new ButtonWidget(new Rect(area.x() + buttonWidth + 4, y,
                Math.max(40, width - buttonWidth * 2 - 8), CONTROL_HEIGHT),
                label + ": " + shown, null));
        place(new ButtonWidget(new Rect(area.right() - buttonWidth, y, buttonWidth, CONTROL_HEIGHT), ">",
                () -> onChange.accept(options.get((index + 1) % options.size()))));
        return y + CONTROL_HEIGHT + 4;
    }

    private int addColor(Rect area, int y, int width, String label, ColorSetting color) {
        int argb = color.getColor();
        place(new ButtonWidget(new Rect(area.x(), y, width, CONTROL_HEIGHT),
                label + "  #" + String.format("%06X", argb & 0xFFFFFF), null));
        return y + CONTROL_HEIGHT + 4;
    }

    private void selectModule(SoupModule module) {
        this.selected = module;
        this.settingsScroll = new ScrollModel(Math.max(1, this.layout.settings().height() - CONTENT_TOP), 24);
        rebuildSettings();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = this.runtime.theme();
        painter.fill(new Rect(0, 0, this.width, this.height), Color.argb(theme.background()));
        painter.fill(this.layout.window(), Color.argb(theme.surface()));
        painter.outline(this.layout.window(), Color.argb(theme.border()), 1);

        Rect list = moduleListArea();
        painter.fill(list, Color.argb(theme.surfaceHover()));
        List<SoupModule> modules = visibleModules();
        this.listScroll.setContentHeight(modules.size() * MODULE_ROW);
        int listTop = list.y() + 4 - this.listScroll.offset();
        this.hoveredModule = -1;
        for (int i = 0; i < modules.size(); i++) {
            int rowY = listTop + i * MODULE_ROW;
            if (rowY + MODULE_ROW < list.y() || rowY > list.bottom()) continue;
            Rect row = new Rect(list.x() + 4, rowY, list.width() - 8, MODULE_ROW - 2);
            boolean hover = row.contains(mouseX, mouseY);
            boolean sel = modules.get(i) == this.selected;
            if (hover) this.hoveredModule = i;
            if (sel) painter.fill(row, Color.argb(theme.accent().withAlpha(0.25f)));
            else if (hover) painter.fill(row, Color.argb(theme.surfaceHover()));
            String name = moduleName(modules.get(i));
            if (modules.get(i).isEnabled()) name = "\u25cf " + name;
            painter.text(name, row.x() + 6, row.y() + 5,
                    sel || modules.get(i).isEnabled() ? Color.argb(theme.accent()) : Color.argb(theme.textPrimary()), false);
        }

        for (Widget widget : this.powerWidgets) widget.render(painter, theme);
        Rect settings = this.layout.settings();
        painter.fill(settings, Color.argb(theme.surfaceHover()));
        for (Widget widget : this.settingWidgets) {
            Rect b = widget.bounds();
            if (b.bottom() < settings.y() + CONTENT_TOP || b.y() > settings.bottom()) continue;
            widget.render(painter, theme);
        }
        for (Widget widget : this.chrome) widget.render(painter, theme);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (this.search != null) {
            this.search.setFocused(this.search.hit(mouseX, mouseY));
        }
        for (Widget widget : new ArrayList<>(this.powerWidgets)) {
            if (widget.mouseClicked(mouseX, mouseY, button)) return true;
        }
        for (Widget widget : new ArrayList<>(this.settingWidgets)) {
            if (widget.mouseClicked(mouseX, mouseY, button)) return true;
        }
        for (Widget widget : new ArrayList<>(this.chrome)) {
            if (widget.mouseClicked(mouseX, mouseY, button)) return true;
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
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        boolean handled = false;
        for (Widget widget : new ArrayList<>(this.powerWidgets)) {
            handled |= widget.mouseReleased(mouseX, mouseY, button);
        }
        for (Widget widget : new ArrayList<>(this.settingWidgets)) {
            handled |= widget.mouseReleased(mouseX, mouseY, button);
        }
        for (Widget widget : new ArrayList<>(this.chrome)) {
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
        String typed = input == null ? "" : input.asString();
        if (this.search != null && this.search.focused() && typed != null && !typed.isEmpty()) {
            for (int i = 0; i < typed.length(); i++) {
                this.search.charTyped(typed.charAt(i));
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (this.search != null && this.search.focused()) {
            if (this.search.keyPressed(key, input.modifiers())) return true;
        }
        if (key == Keys.ESCAPE) {
            this.close();
            return true;
        }
        if (key == Keys.ENTER && this.selected != null) {
            setModuleEnabled(this.selected, !this.selected.isEnabled());
            rebuildSettings();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    private static final class Caption extends Widget {
        private final String text;
        Caption(String text, Rect bounds) {
            super(bounds);
            this.text = text;
        }
        @Override
        public void render(Painter painter, ThemeTokens theme) {
            painter.text(this.text, bounds().x(), bounds().y() + 2, Color.argb(theme.textSecondary()), false);
        }
    }
}

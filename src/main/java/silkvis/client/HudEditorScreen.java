package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import silkvis.render.VanillaPainter;
import silkvis.ui.Color;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;
import silkvis.ui.hud.Anchor;
import silkvis.ui.hud.HudLayoutConfig;
import silkvis.ui.hud.HudLayoutEngine;
import silkvis.ui.hud.HudWidgetConfig;
import silkvis.ui.hud.HudWidgetId;
import silkvis.ui.widget.ButtonWidget;
import silkvis.ui.widget.Keys;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * HUD editor (HUD-02), rewritten in 0.1.19 after «хочу редактирование худ полноценное».
 *
 * <p>What it does now, in the owner's words: you click an element, it is highlighted, the
 * panel on the right edits <em>that</em> element — scale, opacity, hide/show, reset — and the
 * arrow keys move it by 1 (Shift: 10) so two widgets can be aligned exactly. Ctrl+Z undoes the
 * last change, the grid button cycles 4 / 8 / off, and every change is written through to the
 * config immediately, so closing the screen cannot lose anything.</p>
 *
 * <p>Hidden widgets are listed too, marked «скрыт»: without that the editor could hide a
 * widget and then never bring it back. The boxes of hidden widgets come from the layout engine
 * because the HUD does not paint them.</p>
 *
 * <p>The old version moved boxes with a guessed 110x22 size and dragged them in a way that
 * jumped on release; it now drags the rectangles the overlay actually painted
 * ({@link HudOverlayRenderer#lastRects()}), which is also why the drag preview matches the
 * saved result.</p>
 */
public final class HudEditorScreen extends Screen {
    private static final int SNAP_THRESHOLD = 4;
    private static final int FALLBACK_WIDTH = 110;
    private static final int FALLBACK_HEIGHT = 22;
    private static final float SCALE_STEP = 0.1f;
    private static final float ALPHA_STEP = 0.05f;
    private static final int HISTORY_LIMIT = 20;
    private static final int PANEL_WIDTH = 184;

    private final SilkVisRuntime runtime = SilkVisRuntime.get();
    private final Map<HudWidgetId, Rect> boxes = new EnumMap<>(HudWidgetId.class);
    private final List<ButtonWidget> barButtons = new ArrayList<>();
    private final List<ButtonWidget> panelButtons = new ArrayList<>();
    private final Deque<HudLayoutConfig> history = new ArrayDeque<>();
    private final List<HudWidgetId> editable = new ArrayList<>();

    private HudWidgetId selected;
    private HudWidgetId dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private int grid = 4;
    private HudLayoutConfig layout;

    /** Localized text through the language chosen in the settings tab, not the game's. */
    private silkvis.ui.Localizer localizer() {
        return runtime.localizer();
    }

    private String tr(String key) {
        return localizer().get(key);
    }

    public HudEditorScreen() {
        super(Text.translatable("silkvis.hud.editor.title"));
        this.layout = runtime.hudLayout();
    }

    @Override
    protected void init() {
        editable.clear();
        for (HudWidgetConfig cfg : layout.byId().values()) {
            if (cfg.id().available()) {
                editable.add(cfg.id());
            }
        }
        if (selected != null && !editable.contains(selected)) {
            selected = null;
        }
        syncBoxes();
        rebuildButtons();
    }

    /** Everything the screen owns is rebuilt here; nothing keeps a stale label. */
    private void rebuildButtons() {
        barButtons.clear();
        panelButtons.clear();
        int barY = height - 28;
        barButtons.add(new ButtonWidget(new Rect(8, barY, 80, 20), tr("silkvis.hud.editor.done"),
                this::close).style(ButtonWidget.Style.PRIMARY));
        barButtons.add(new ButtonWidget(new Rect(94, barY, 96, 20), gridLabel(), this::cycleGrid));
        ButtonWidget undo = new ButtonWidget(new Rect(196, barY, 110, 20),
                tr("silkvis.hud.editor.undo"), this::undo);
        undo.setEnabled(!history.isEmpty());
        barButtons.add(undo);
        barButtons.add(new ButtonWidget(new Rect(312, barY, 120, 20),
                tr("silkvis.hud.editor.reset_all"), () -> {
            pushHistory();
            layout = HudLayoutConfig.defaults();
            runtime.setHudLayout(layout);
            selected = null;
            init();
        }));
        if (selected == null) {
            return;
        }
        HudWidgetConfig cfg = layout.get(selected);
        Rect panel = panelRect();
        int x = panel.x() + 8;
        int innerWidth = panel.width() - 16;
        int y = panel.y() + 76;
        panelButtons.add(new ButtonWidget(new Rect(x, y, 36, 20), "\u2212",
                () -> changeScale(-SCALE_STEP)));
        panelButtons.add(new ButtonWidget(new Rect(x + 42, y, 36, 20), "+",
                () -> changeScale(SCALE_STEP)));
        y += 34;
        panelButtons.add(new ButtonWidget(new Rect(x, y, 36, 20), "\u2212",
                () -> changeAlpha(-ALPHA_STEP)));
        panelButtons.add(new ButtonWidget(new Rect(x + 42, y, 36, 20), "+",
                () -> changeAlpha(ALPHA_STEP)));
        y += 32;
        panelButtons.add(new ButtonWidget(new Rect(x, y, innerWidth, 20),
                tr(cfg.enabled() ? "silkvis.hud.editor.hide" : "silkvis.hud.editor.show"),
                this::toggleSelectedVisible));
        y += 26;
        panelButtons.add(new ButtonWidget(new Rect(x, y, innerWidth, 20),
                tr("silkvis.hud.editor.reset_one"), () -> {
            pushHistory();
            layout = layout.with(HudWidgetConfig.defaults(selected));
            runtime.setHudLayout(layout);
            init();
        }));
    }

    private String gridLabel() {
        return tr("silkvis.hud.editor.grid") + ": "
                + (grid <= 0 ? tr("silkvis.state.off") : String.valueOf(grid));
    }

    private void cycleGrid() {
        grid = grid == 4 ? 8 : grid == 8 ? 0 : 4;
        rebuildButtons();
    }

    private Rect panelRect() {
        int width = Math.min(PANEL_WIDTH, Math.max(140, this.width / 3));
        return new Rect(this.width - width - 8, 30, width, Math.min(222, height - 70));
    }

    /**
     * Copies the boxes the HUD actually painted. A widget that is switched off is not painted,
     * so its box comes from the layout engine — that keeps hidden widgets visible to the user
     * instead of making them unreachable.
     */
    private void syncBoxes() {
        Rect screen = new Rect(0, 0, width, height);
        Rect safe = HudLayoutEngine.safeArea(screen);
        Map<HudWidgetId, Rect> painted = HudOverlayRenderer.lastRects();
        for (HudWidgetId id : editable) {
            if (id == dragging) {
                continue;
            }
            HudWidgetConfig cfg = layout.get(id);
            Rect rect = cfg.enabled() ? painted.get(id) : null;
            boxes.put(id, rect != null ? rect
                    : HudLayoutEngine.resolve(screen, safe, cfg, FALLBACK_WIDTH, FALLBACK_HEIGHT));
        }
    }

    private void pushHistory() {
        history.push(layout);
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    private void undo() {
        if (history.isEmpty()) {
            return;
        }
        layout = history.pop();
        runtime.setHudLayout(layout);
        init();
    }

    private void changeScale(float delta) {
        if (selected == null) {
            return;
        }
        HudWidgetConfig cfg = layout.get(selected);
        float next = Math.round((cfg.scale() + delta) * 100f) / 100f;
        pushHistory();
        layout = layout.with(cfg.withScale(next));
        runtime.setHudLayout(layout);
        init();
    }

    private void changeAlpha(float delta) {
        if (selected == null) {
            return;
        }
        HudWidgetConfig cfg = layout.get(selected);
        float next = Math.round((cfg.alpha() + delta) * 100f) / 100f;
        pushHistory();
        layout = layout.with(cfg.withAlpha(next));
        runtime.setHudLayout(layout);
        init();
    }

    private void toggleSelectedVisible() {
        if (selected == null) {
            return;
        }
        HudWidgetConfig cfg = layout.get(selected);
        pushHistory();
        layout = layout.with(cfg.withEnabled(!cfg.enabled()));
        runtime.setHudLayout(layout);
        init();
    }

    private void nudge(int dx, int dy) {
        if (selected == null) {
            return;
        }
        Rect box = boxes.get(selected);
        if (box == null) {
            return;
        }
        Rect safe = HudLayoutEngine.safeArea(new Rect(0, 0, width, height));
        Rect moved = HudLayoutEngine.nudge(box, dx, dy, safe);
        if (moved.x() == box.x() && moved.y() == box.y()) {
            return;
        }
        pushHistory();
        boxes.put(selected, moved);
        applyPosition(selected, moved, safe);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // The HUD itself paints the widgets (HudOverlayRenderer stays active); the editor
        // only marks, highlights and edits them.
        syncBoxes();
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();

        Rect header = new Rect(0, 0, width, 24);
        painter.fill(header, Color.argb(theme.background().withAlpha(0.9f)));
        painter.text(tr("silkvis.hud.editor.title") + " — " + tr("silkvis.hud.editor.preview"),
                8, 8, Color.argb(theme.textPrimary()), true);

        Rect screen = new Rect(0, 0, width, height);
        if (grid > 0) {
            int line = Color.argb(theme.border().withAlpha(0.12f));
            for (int x = 0; x < width; x += grid) {
                painter.fill(new Rect(x, 24, 1, height - 52), line);
            }
            for (int y = 24; y < height - 28; y += grid) {
                painter.fill(new Rect(0, y, width, 1), line);
            }
        }

        for (Map.Entry<HudWidgetId, Rect> entry : boxes.entrySet()) {
            HudWidgetId id = entry.getKey();
            Rect box = entry.getValue();
            HudWidgetConfig cfg = layout.get(id);
            boolean warning = HudLayoutEngine.overlapsProtected(box, screen);
            boolean isSelected = id == selected;
            Color border = warning ? theme.warning()
                    : id == dragging ? theme.accent()
                    : isSelected ? theme.accent() : theme.border();
            if (!cfg.enabled() && !isSelected) {
                border = theme.textSecondary().withAlpha(0.45f);
            }
            painter.outline(box, border.argb(), isSelected || id == dragging ? 2 : 1);
            String label = tr(id.langKey()) + (cfg.enabled() ? "" : " \u00b7 " + tr("silkvis.hud.editor.hidden"));
            int labelY = Math.max(26, box.y() - painter.lineHeight() - 2);
            int labelWidth = painter.textWidth(label) + 6;
            painter.fill(new Rect(box.x(), labelY - 1, labelWidth, painter.lineHeight() + 2),
                    Color.argb(theme.background().withAlpha(0.85f)));
            painter.text(label, box.x() + 3, labelY,
                    Color.argb(cfg.enabled() ? theme.textPrimary() : theme.textSecondary()), false);
        }

        Rect current = dragging != null ? boxes.get(dragging) : null;
        if (current != null) {
            for (Rect other : boxes.values()) {
                if (other == current) {
                    continue;
                }
                HudLayoutEngine.Guide guide = HudLayoutEngine.alignmentGuide(current, other, SNAP_THRESHOLD);
                if (guide == null) {
                    continue;
                }
                if (guide.x() != null) {
                    painter.fill(new Rect(guide.x(), 0, 1, height), Color.argb(theme.accent().withAlpha(0.5f)));
                }
                if (guide.y() != null) {
                    painter.fill(new Rect(0, guide.y(), width, 1), Color.argb(theme.accent().withAlpha(0.5f)));
                }
            }
        }

        renderPanel(painter, theme);
        for (ButtonWidget button : barButtons) {
            button.render(painter, theme);
        }
        for (ButtonWidget button : panelButtons) {
            button.render(painter, theme);
        }
        painter.text(tr("silkvis.hud.editor.hint"), 8, height - 40,
                Color.argb(theme.textSecondary()), false);
    }

    private void renderPanel(Painter painter, ThemeTokens theme) {
        Rect panel = panelRect();
        painter.roundPanel(panel, theme.radiusPanel(),
                Color.argb(theme.surface().withAlpha(0.85f)), Color.argb(theme.border()));
        int x = panel.x() + 8;
        if (selected == null) {
            painter.text(tr("silkvis.hud.editor.none"), x, panel.y() + 8,
                    Color.argb(theme.textSecondary()), false);
            painter.text(tr("silkvis.hud.editor.none.desc"), x, panel.y() + 22,
                    Color.argb(theme.textSecondary()), false);
            return;
        }
        HudWidgetConfig cfg = layout.get(selected);
        painter.text(tr(selected.langKey()), x, panel.y() + 8, Color.argb(theme.textPrimary()), true);
        String position = "x " + cfg.offsetX() + "  y " + cfg.offsetY() + "  \u00b7  " + cfg.anchor().name();
        painter.text(position, x, panel.y() + 22, Color.argb(theme.textSecondary()), false);
        int y = panel.y() + 40;
        painter.text(tr("silkvis.hud.editor.scale") + ": " + String.format(java.util.Locale.ROOT, "%.2f", cfg.scale()),
                x, y, Color.argb(theme.textSecondary()), false);
        y += 34;
        painter.text(tr("silkvis.hud.editor.alpha") + ": " + String.format(java.util.Locale.ROOT, "%.2f", cfg.alpha()),
                x, y, Color.argb(theme.textSecondary()), false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button != 0) {
            return false;
        }
        // Index-based loops: a handler rebuilds these lists, and a for-each would keep an
        // iterator over a list that just changed.
        for (int i = 0; i < barButtons.size(); i++) {
            if (barButtons.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (int i = 0; i < panelButtons.size(); i++) {
            if (panelButtons.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (panelRect().contains((int) mouseX, (int) mouseY)) {
            return true;
        }
        // Topmost widget wins, matching render order by zIndex.
        HudWidgetId best = null;
        int bestZ = Integer.MIN_VALUE;
        for (Map.Entry<HudWidgetId, Rect> entry : boxes.entrySet()) {
            if (!entry.getValue().contains((int) mouseX, (int) mouseY)) {
                continue;
            }
            int z = layout.get(entry.getKey()).zIndex();
            if (z > bestZ) {
                bestZ = z;
                best = entry.getKey();
            }
        }
        if (best == null) {
            selected = null;
            rebuildButtons();
            return true;
        }
        if (selected != best) {
            selected = best;
            rebuildButtons();
        }
        dragging = best;
        Rect box = boxes.get(best);
        dragOffsetX = (int) mouseX - box.x();
        dragOffsetY = (int) mouseY - box.y();
        pushHistory();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (dragging == null) {
            return super.mouseDragged(click, offsetX, offsetY);
        }
        Rect screen = new Rect(0, 0, width, height);
        Rect safe = HudLayoutEngine.safeArea(screen);
        Rect box = boxes.get(dragging);
        int nx = HudLayoutEngine.snap((int) mouseX - dragOffsetX, grid);
        int ny = HudLayoutEngine.snap((int) mouseY - dragOffsetY, grid);
        Rect moved = Rect.clampInto(box.withPosition(nx, ny), safe);
        boxes.put(dragging, moved);
        // Written through on every move: the widget under the cursor is the widget being
        // edited, and the debounced config write keeps this cheap.
        applyPosition(dragging, moved, safe);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        for (int i = 0; i < barButtons.size(); i++) {
            if (barButtons.get(i).mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (int i = 0; i < panelButtons.size(); i++) {
            if (panelButtons.get(i).mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (dragging == null) {
            return super.mouseReleased(click);
        }
        Rect box = boxes.get(dragging);
        applyPosition(dragging, box, HudLayoutEngine.safeArea(new Rect(0, 0, width, height)));
        dragging = null;
        return true;
    }

    /** Converts a dropped box into anchor + logical offset, using the box's real size. */
    private void applyPosition(HudWidgetId id, Rect box, Rect safe) {
        if (box == null) {
            return;
        }
        HudWidgetConfig cfg = layout.get(id);
        Anchor anchor = nearestAnchor(box, safe, cfg.anchor());
        int anchorX = safe.x() + Math.round((safe.width() - box.width()) * anchor.fractionX());
        int anchorY = safe.y() + Math.round((safe.height() - box.height()) * anchor.fractionY());
        int offsetX = (box.x() - anchorX) * anchor.offsetSignX();
        int offsetY = (box.y() - anchorY) * anchor.offsetSignY();
        layout = layout.with(cfg.withPosition(anchor, offsetX, offsetY));
        runtime.setHudLayout(layout);
    }

    private static Anchor nearestAnchor(Rect box, Rect safe, Anchor fallback) {
        double cx = (box.centerX() - safe.x()) / (double) Math.max(1, safe.width());
        double cy = (box.centerY() - safe.y()) / (double) Math.max(1, safe.height());
        Anchor best = fallback;
        double bestDistance = Double.MAX_VALUE;
        for (Anchor anchor : Anchor.values()) {
            double dx = cx - (anchor.fractionX() == 0 ? 0.15 : anchor.fractionX() == 1 ? 0.85 : 0.5);
            double dy = cy - (anchor.fractionY() == 0 ? 0.15 : anchor.fractionY() == 1 ? 0.85 : 0.5);
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = anchor;
            }
        }
        return best;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int modifiers = input.modifiers();
        boolean shift = (modifiers & Keys.MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & Keys.MOD_CONTROL) != 0;
        if (keyCode == Keys.ESCAPE) {
            close();
            return true;
        }
        if (ctrl && keyCode == Keys.Z) {
            undo();
            return true;
        }
        int step = shift ? 10 : 1;
        switch (keyCode) {
            case Keys.LEFT -> {
                nudge(-step, 0);
                return true;
            }
            case Keys.RIGHT -> {
                nudge(step, 0);
                return true;
            }
            case Keys.UP -> {
                nudge(0, -step);
                return true;
            }
            case Keys.DOWN -> {
                nudge(0, step);
                return true;
            }
            default -> {
                return super.keyPressed(input);
            }
        }
    }

    @Override
    public void close() {
        runtime.setHudLayout(layout);
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new SilkVisScreen());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * HUD editor (HUD-02).
 *
 * <p>Rewritten in 0.1.15 after the owner reported that it "edits nothing". The old version
 * laid every widget out at a guessed 110x22 box and converted the dropped box back into an
 * anchor offset using that wrong width, so a widget jumped as soon as the editor closed.
 * Now the real HUD keeps drawing underneath: the overlay publishes the boxes it actually
 * painted ({@link HudOverlayRenderer#lastRects()}), the editor drags those boxes, and every
 * move is written through immediately, so what the user sees while dragging is what is
 * saved.</p>
 */
public final class HudEditorScreen extends Screen {
    private static final int GRID = 4;
    private static final int SNAP_THRESHOLD = 4;
    private static final int FALLBACK_WIDTH = 110;
    private static final int FALLBACK_HEIGHT = 22;

    private final SilkVisRuntime runtime = SilkVisRuntime.get();
    private final Map<HudWidgetId, Rect> boxes = new EnumMap<>(HudWidgetId.class);
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private HudWidgetId dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private int grid = GRID;
    private HudLayoutConfig layout;
    /** Widgets this build renders; recomputed only when the layout is replaced wholesale. */
    private final List<HudWidgetId> editable = new ArrayList<>();

    /** Localized text through the language chosen in the settings tab, not the game's. */
    private silkvis.ui.Localizer localizer() {
        return runtime.localizer();
    }

    public HudEditorScreen() {
        super(Text.translatable("silkvis.hud.editor.title"));
        this.layout = runtime.hudLayout();
    }

    @Override
    protected void init() {
        boxes.clear();
        buttons.clear();
        editable.clear();
        for (HudWidgetConfig cfg : layout.byId().values()) {
            if (cfg.enabled() && cfg.id().available()) {
                editable.add(cfg.id());
            }
        }
        syncBoxes();
        int y = height - 28;
        buttons.add(new ButtonWidget(new Rect(8, y, 90, 20),
                localizer().get("silkvis.hud.editor.done"), this::close)
                .style(ButtonWidget.Style.PRIMARY));
        buttons.add(new ButtonWidget(new Rect(104, y, 90, 20),
                localizer().get("silkvis.hud.editor.grid") + " " + grid,
                () -> grid = grid == 4 ? 8 : 4));
        buttons.add(new ButtonWidget(new Rect(200, y, 110, 20),
                localizer().get("silkvis.hud.editor.reset"), () -> {
                    layout = HudLayoutConfig.defaults();
                    runtime.setHudLayout(layout);
                    init();
                }));
    }

    /**
     * Copies the boxes the HUD actually painted. Falls back to a placeholder box for a
     * widget that is not on screen (main menu: no world, so nothing is drawn).
     */
    private void syncBoxes() {
        Rect screen = new Rect(0, 0, width, height);
        Rect safe = HudLayoutEngine.safeArea(screen);
        Map<HudWidgetId, Rect> painted = HudOverlayRenderer.lastRects();
        for (HudWidgetId id : editable) {
            if (id == dragging) {
                continue;
            }
            Rect rect = painted.get(id);
            boxes.put(id, rect != null ? rect
                    : HudLayoutEngine.resolve(screen, safe, layout.get(id), FALLBACK_WIDTH, FALLBACK_HEIGHT));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // The HUD itself draws the widgets (HudOverlayRenderer stays active while this
        // screen is open); the editor only marks the draggable areas.
        syncBoxes();
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();

        Rect header = new Rect(0, 0, width, 24);
        painter.fill(header, Color.argb(theme.background().withAlpha(0.9f)));
        String title = localizer().get("silkvis.hud.editor.title")
                + " — " + localizer().get("silkvis.hud.editor.preview");
        painter.text(title, 8, 8, Color.argb(theme.textPrimary()), true);

        Rect screen = new Rect(0, 0, width, height);
        for (Map.Entry<HudWidgetId, Rect> entry : boxes.entrySet()) {
            HudWidgetId id = entry.getKey();
            Rect box = entry.getValue();
            boolean warning = HudLayoutEngine.overlapsProtected(box, screen);
            // Theme accessors return silkvis.ui.Color; the painter wants an ARGB int. Mixing
            // the two was a compile break (owner build, 2026-11-15).
            Color border = warning ? theme.warning() : id == dragging ? theme.accent() : theme.border();
            painter.outline(box, border.argb(), id == dragging ? 2 : 1);
            String label = localizer().get(id.langKey());
            int labelY = Math.max(14, box.y() - painter.lineHeight() - 2);
            int labelWidth = painter.textWidth(label) + 6;
            painter.fill(new Rect(box.x(), labelY - 1, labelWidth, painter.lineHeight() + 2),
                    Color.argb(theme.background().withAlpha(0.85f)));
            painter.text(label, box.x() + 3, labelY, Color.argb(theme.textPrimary()), false);
        }
        if (dragging != null) {
            Rect current = boxes.get(dragging);
            for (Rect other : boxes.values()) {
                if (current == null || other == current) {
                    continue;
                }
                HudLayoutEngine.Guide guide = HudLayoutEngine.alignmentGuide(current, other, SNAP_THRESHOLD);
                if (guide != null) {
                    if (guide.x() != null) {
                        painter.fill(new Rect(guide.x(), 0, 1, height), Color.argb(theme.accent().withAlpha(0.5f)));
                    }
                    if (guide.y() != null) {
                        painter.fill(new Rect(0, guide.y(), width, 1), Color.argb(theme.accent().withAlpha(0.5f)));
                    }
                }
            }
        }
        for (ButtonWidget button : buttons) {
            button.render(painter, theme);
        }
        painter.text(localizer().get("silkvis.hud.editor.hint"), 8, height - 42,
                Color.argb(theme.textSecondary()), false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        for (ButtonWidget widget : buttons) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button != 0) {
            return false;
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
        if (best != null) {
            dragging = best;
            Rect box = boxes.get(best);
            dragOffsetX = (int) mouseX - box.x();
            dragOffsetY = (int) mouseY - box.y();
            return true;
        }
        return super.mouseClicked(click, doubled);
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
        // Written through on every move: the widget under the cursor is the widget that is
        // being edited, and the debounced config write keeps this cheap.
        applyPosition(dragging, moved, safe);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        for (ButtonWidget widget : buttons) {
            if (widget.mouseReleased(mouseX, mouseY, button)) {
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
        HudWidgetConfig cfg = layout.get(id);
        Anchor anchor = nearestAnchor(box, safe, cfg.anchor());
        int anchorX = safe.x() + Math.round((safe.width() - box.width()) * anchor.fractionX());
        int anchorY = safe.y() + Math.round((safe.height() - box.height()) * anchor.fractionY());
        int offsetX = (box.x() - anchorX) * (anchor.offsetSignX() == 0 ? 1 : anchor.offsetSignX());
        int offsetY = (box.y() - anchorY) * (anchor.offsetSignY() == 0 ? 1 : anchor.offsetSignY());
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
        if (input.key() == Keys.ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
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

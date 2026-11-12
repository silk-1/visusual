package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
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
 * <p>Drag with snapping, alignment guides, per-widget anchors and a reset. Layout is
 * stored as anchor + logical offsets, never as absolute screen pixels. Preview mode is
 * explicit: the panel header says "preview" and demo text is used when a value is
 * unavailable, so a demo string is never mistaken for real data.</p>
 */
public final class HudEditorScreen extends Screen {
    private static final int GRID = 4;
    private static final int SNAP_THRESHOLD = 4;

    private final SilkVisRuntime runtime = SilkVisRuntime.get();
    private final Map<HudWidgetId, Rect> boxes = new EnumMap<>(HudWidgetId.class);
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private HudWidgetId dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private int grid = GRID;
    private HudLayoutConfig layout;

    public HudEditorScreen() {
        super(Text.translatable("silkvis.hud.editor.title"));
        this.layout = runtime.hudLayout();
    }

    @Override
    protected void init() {
        boxes.clear();
        buttons.clear();
        Rect screen = new Rect(0, 0, width, height);
        Rect safe = HudLayoutEngine.safeArea(screen);
        for (HudWidgetConfig cfg : layout.byId().values()) {
            if (!cfg.enabled() || !cfg.id().p1()) {
                continue;
            }
            boxes.put(cfg.id(), HudLayoutEngine.resolve(screen, safe, cfg, 110, 22));
        }
        int y = height - 28;
        buttons.add(new ButtonWidget(new Rect(8, y, 90, 20),
                Text.translatable("silkvis.hud.editor.done").getString(), this::close)
                .style(ButtonWidget.Style.PRIMARY));
        buttons.add(new ButtonWidget(new Rect(104, y, 90, 20),
                Text.translatable("silkvis.hud.editor.grid").getString() + " " + grid,
                () -> grid = grid == 4 ? 8 : 4));
        buttons.add(new ButtonWidget(new Rect(200, y, 110, 20),
                Text.translatable("silkvis.hud.reset").getString(), () -> {
                    layout = HudLayoutConfig.defaults();
                    init();
                }));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();
        Rect screen = new Rect(0, 0, width, height);
        painter.fill(screen, Color.argb(theme.background().withAlpha(0.55f)));

        Rect header = new Rect(0, 0, width, 24);
        painter.fill(header, Color.argb(theme.background().withAlpha(0.9f)));
        String title = Text.translatable("silkvis.hud.editor.title").getString()
                + " — " + Text.translatable("silkvis.hud.editor.preview").getString();
        painter.text(title, 8, 8, Color.argb(theme.textPrimary()), true);

        for (Map.Entry<HudWidgetId, Rect> entry : boxes.entrySet()) {
            HudWidgetId id = entry.getKey();
            Rect box = entry.getValue();
            boolean warning = HudLayoutEngine.overlapsProtected(box, screen);
            painter.fill(box, Color.argb(theme.surface().withAlpha(0.55f)));
            painter.outline(box, Color.argb(warning ? theme.warning()
                    : id == dragging ? theme.accent() : theme.border()), 1);
            String label = Text.translatable(id.langKey()).getString();
            painter.text(label, box.x() + 4, box.centerY() - painter.lineHeight() / 2,
                    Color.argb(theme.textPrimary()), false);
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
        painter.text(Text.translatable("silkvis.hud.editor.hint").getString(), 8, height - 42,
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
        for (HudWidgetId id : boxes.keySet()) {
            Rect box = boxes.get(id);
            if (box.contains((int) mouseX, (int) mouseY)) {
                int z = layout.get(id).zIndex();
                if (z > bestZ) {
                    bestZ = z;
                    best = id;
                }
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
        HudWidgetConfig cfg = layout.get(dragging);
        Rect box = boxes.get(dragging);
        Rect screen = new Rect(0, 0, width, height);
        Rect safe = HudLayoutEngine.safeArea(screen);
        // Convert the dropped box back into anchor + logical offset.
        Anchor anchor = nearestAnchor(box, safe, cfg.anchor());
        int anchorX = safe.x() + Math.round((safe.width() - box.width()) * anchor.fractionX());
        int anchorY = safe.y() + Math.round((safe.height() - box.height()) * anchor.fractionY());
        int offsetX = (box.x() - anchorX) * (anchor.offsetSignX() == 0 ? 1 : anchor.offsetSignX());
        int offsetY = (box.y() - anchorY) * (anchor.offsetSignY() == 0 ? 1 : anchor.offsetSignY());
        layout = layout.with(cfg.withPosition(anchor, offsetX, offsetY));
        runtime.setHudLayout(layout);
        dragging = null;
        return true;
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

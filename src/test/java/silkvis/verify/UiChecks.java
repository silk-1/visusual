package silkvis.verify;

import silkvis.core.Clock;
import silkvis.ui.FocusManager;
import silkvis.ui.MainScreenLayout;
import silkvis.ui.Rect;
import silkvis.ui.ScrollModel;
import silkvis.ui.ThemeTokens;
import silkvis.ui.hud.Anchor;
import silkvis.ui.hud.HudLayoutEngine;
import silkvis.ui.hud.HudWidgetConfig;
import silkvis.ui.hud.HudWidgetId;
import silkvis.ui.hud.VisibilityContext;
import silkvis.ui.hud.VisibilityPolicy;
import silkvis.ui.widget.TextInputModel;
import silkvis.ui.widget.Widget;
import silkvis.ui.widget.Keys;
import silkvis.ui.widget.ToggleWidget;
import silkvis.ui.Painter;

import static silkvis.verify.SelfCheck.assertEquals;
import static silkvis.verify.SelfCheck.assertTrue;
import static silkvis.verify.SelfCheck.check;

final class UiChecks {
    private UiChecks() {
    }

    static void run() {
        check("theme tokens carry the approved palette", () -> {
            ThemeTokens theme = ThemeTokens.graphiteTurquoise();
            assertEquals(0xFF59D8C5, theme.accent().argb(), "accent colour");
            assertEquals(26, theme.controlHeight(), "control height inside 24..28");
            assertEquals(20, theme.minHitSize(), "minimum hit area");
            assertEquals(8, theme.radiusPanel(), "panel radius");
            assertEquals(4, theme.radiusControl(), "control radius");
            assertEquals(0, theme.motionDuration(true), "reduced motion disables animation");
            assertEquals(1.0f, theme.background().alpha(), 0.001, "opaque fallback");
        });

        check("wide layout keeps navigation, list and settings columns", () -> {
            MainScreenLayout layout = MainScreenLayout.compute(1280, 720);
            assertEquals(MainScreenLayout.Mode.WIDE, layout.mode(), "mode");
            assertEquals(144, layout.navigation().width(), "navigation width");
            assertTrue(layout.moduleList().width() >= 200, "list usable");
            assertTrue(layout.settings().width() >= 240, "settings usable");
            assertTrue(layout.window().width() <= 960 && layout.window().height() <= 620, "max size");
            assertTrue(layout.closeButton().right() <= layout.window().right(), "close reachable");
            assertTrue(layout.footer().bottom() <= layout.window().bottom(), "footer reachable");
    });

        check("compact and narrow layouts degrade instead of clipping", () -> {
            MainScreenLayout compact = MainScreenLayout.compute(640, 480);
            assertEquals(MainScreenLayout.Mode.COMPACT, compact.mode(), "compact mode");
            assertTrue(compact.showsSettingsInContent(), "settings replace list");
            MainScreenLayout narrow = MainScreenLayout.compute(360, 480);
            assertEquals(MainScreenLayout.Mode.NARROW, narrow.mode(), "narrow mode");
            assertTrue(narrow.settings().width() > 0, "single column exists");
        });

        check("hud anchor resolution and safe-area clamp", () -> {
            Rect screen = new Rect(0, 0, 1920, 1080);
            Rect safe = HudLayoutEngine.safeArea(screen);
            HudWidgetConfig cfg = new HudWidgetConfig(HudWidgetId.COORDINATES, true, Anchor.BOTTOM_RIGHT,
                    6, 6, 1f, 1f, 10, VisibilityPolicy.defaults());
            Rect box = HudLayoutEngine.resolve(screen, safe, cfg, 100, 20);
            assertTrue(box.right() <= safe.right(), "clamped horizontally");
            assertTrue(box.bottom() <= safe.bottom(), "clamped vertically");
            assertEquals(6, HudLayoutEngine.snap(7, 4), "snap to grid");
            assertEquals(8, HudLayoutEngine.snap(9, 8), "snap to 8");
        });

        check("protected vanilla areas are detected", () -> {
            Rect screen = new Rect(0, 0, 640, 360);
            Rect hotbar = HudLayoutEngine.protectedAreas(screen)[0];
            assertTrue(HudLayoutEngine.overlapsProtected(hotbar, screen), "hotbar overlaps itself");
            Rect topLeft = new Rect(4, 4, 40, 12);
            assertTrue(!HudLayoutEngine.overlapsProtected(topLeft, screen), "top-left is free");
        });

        check("visibility policy hides decorative widgets with F1 and screens", () -> {
            VisibilityPolicy policy = VisibilityPolicy.defaults();
            assertTrue(!policy.permits(new VisibilityContext(true, false, false, false, false, false)),
                    "hidden on F1");
            assertTrue(!policy.permits(new VisibilityContext(false, false, false, true, false, false)),
                    "hidden in inventory");
            assertTrue(policy.permits(VisibilityContext.gameplay()), "visible in gameplay");
            assertTrue(VisibilityPolicy.alwaysVisible().permits(
                    new VisibilityContext(true, true, true, true, true, true)), "explicit override");
        });

        check("scroll model clamps and reveals items", () -> {
            ScrollModel scroll = new ScrollModel(100, 24);
            scroll.setContentHeight(400);
            assertEquals(300, scroll.maxOffset(), "max offset");
            scroll.scroll(-100);
            assertEquals(300, scroll.offset(), "clamped at bottom");
            scroll.setOffset(0);
            scroll.ensureVisible(250, 20);
            assertTrue(scroll.offset() > 0, "scrolled to item");
            assertTrue(scroll.needsScrollbar(), "scrollbar shown");
        });

        check("focus manager cycles forward and backward", () -> {
            FocusManager focus = new FocusManager();
            ToggleWidget a = new ToggleWidget(new Rect(0, 0, 100, 20), "a", null, false, v -> { });
            ToggleWidget b = new ToggleWidget(new Rect(0, 24, 100, 20), "b", null, false, v -> { });
            focus.setScope(java.util.List.of(a, b));
            focus.tab(false);
            assertTrue(a.focused(), "first focused");
            focus.tab(false);
            assertTrue(b.focused(), "second focused");
            focus.tab(true);
            assertTrue(a.focused(), "backwards");
        });

        check("text input model edits within limits", () -> {
            TextInputModel model = new TextInputModel("", 5, s -> s.matches("[a-z]*"));
            model.insert("abc");
            assertEquals("abc", model.text(), "insert");
            model.backspace();
            assertEquals("ab", model.text(), "backspace");
            model.insert("xyz");
            assertEquals("abxyz", model.text(), "fills to limit");
            model.insert("q");
            assertEquals("abxyz", model.text(), "limit respected");
            assertTrue(model.isValid(), "validator passes");
            model.selectAll();
            model.insert("Q");
            assertTrue(!model.isValid(), "uppercase invalid but not silently dropped");
            assertEquals(Keys.LEFT, 263, "key codes match GLFW");
        });

        check("disable/stop invariant: widgets release focus on disable", () -> {
            ToggleWidget toggle = new ToggleWidget(new Rect(0, 0, 10, 10), "t", null, false, v -> { });
            toggle.setEnabled(false);
            assertTrue(!toggle.focusable(), "disabled widget is not focusable");
            assertTrue(!toggle.mouseClicked(1, 1, 0), "disabled widget ignores clicks");
        });
    }

    /** Keeps a reference to Widget/Painter so the checks compile against the public API. */
    @SuppressWarnings("unused")
    private static void apiSmoke(Widget widget, Painter painter, ThemeTokens theme, Clock clock) {
        widget.render(painter, theme);
        clock.monotonicNanos();
    }
}

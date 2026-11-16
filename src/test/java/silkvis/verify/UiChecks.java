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
import silkvis.ui.widget.WidgetDispatch;
import silkvis.ui.widget.ButtonWidget;
import silkvis.ui.widget.Keys;
import silkvis.ui.widget.ToggleWidget;
import silkvis.ui.Painter;

import java.util.ArrayList;
import java.util.List;

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
            assertEquals(1.0f, theme.opaqueFallback().background().alpha(), 0.001, "opaque fallback");
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
            assertEquals(8, HudLayoutEngine.snap(7, 4), "snap to grid");
            assertEquals(8, HudLayoutEngine.snap(9, 8), "snap to 8");
        });

        check("editor nudges, clamps and keeps centred anchors honest", () -> {
            Rect screen = new Rect(0, 0, 640, 360);
            Rect safe = HudLayoutEngine.safeArea(screen);
            // A centred anchor used to return offsetSign 0, so its offset was multiplied
            // away and the widget could not be moved at all.
            HudWidgetConfig centred = new HudWidgetConfig(HudWidgetId.TASK_STATUS, true,
                    Anchor.BOTTOM_CENTER, 0, 40, 1f, 1f, 22, VisibilityPolicy.defaults());
            Rect moved = HudLayoutEngine.resolve(screen, safe, centred, 100, 20);
            HudWidgetConfig flush = new HudWidgetConfig(HudWidgetId.TASK_STATUS, true,
                    Anchor.BOTTOM_CENTER, 0, 0, 1f, 1f, 22, VisibilityPolicy.defaults());
            Rect movedNone = HudLayoutEngine.resolve(screen, safe, flush, 100, 20);
            assertEquals(movedNone.y() - 40, moved.y(), "centred offset is applied upwards");

            Rect box = new Rect(10, 10, 60, 20);
            Rect nudged = HudLayoutEngine.nudge(box, 5, 3, safe);
            assertEquals(15, nudged.x(), "nudge x");
            assertEquals(13, nudged.y(), "nudge y");
            Rect stuck = HudLayoutEngine.nudge(new Rect(0, 0, 60, 20), -20, -20, safe);
            assertEquals(safe.x(), stuck.x(), "clamped to the safe area");
        });

        check("scale and opacity can only leave the editor in range", () -> {
            HudWidgetConfig cfg = HudWidgetConfig.defaults(HudWidgetId.FPS);
            assertEquals(HudWidgetConfig.MAX_SCALE, cfg.withScale(9f).scale(), "scale capped high");
            assertEquals(HudWidgetConfig.MIN_SCALE, cfg.withScale(0.1f).scale(), "scale capped low");
            assertEquals(1.5f, cfg.withScale(1.5f).scale(), "scale kept in range");
            assertEquals(0f, cfg.withAlpha(-1f).alpha(), "alpha capped low");
            assertEquals(1f, cfg.withAlpha(4f).alpha(), "alpha capped high");
            assertEquals(0.5f, cfg.withAlpha(0.5f).alpha(), "alpha kept in range");
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

        // The crash that prompted this: a settings button runs its action on release, the
        // action rebuilds the pane, and the release was still walking the list it had just
        // cleared. In game that was ConcurrentModificationException inside mouseReleased.
        check("a widget action may rebuild the list it was dispatched from", () -> {
            List<Widget> widgets = new ArrayList<>();
            int[] rebuilt = {0};
            ButtonWidget rebuilds = new ButtonWidget(new Rect(0, 0, 40, 20), "rebuild", () -> {
                // Same shape as rebuildSettings(): clear, then refill with SEVERAL rows.
                // The count matters. Refilling with fewer rows than the cursor's position
                // leaves the iterator with nothing to advance to, so it would never notice
                // the modification and a live-list walk would silently survive -- which is
                // exactly how a first version of this check passed against the broken code.
                // A real settings pane refills with many rows, and the loop then advances:
                // that is what threw in the crash report.
                widgets.clear();
                for (int i = 0; i < 3; i++) {
                    widgets.add(new ButtonWidget(new Rect(0, i * 24, 40, 20), "fresh" + i, () -> { }));
                }
                rebuilt[0]++;
            });
            widgets.add(rebuilds);
            widgets.add(new ButtonWidget(new Rect(0, 30, 40, 20), "other", () -> { }));

            assertTrue(rebuilds.mouseClicked(5, 5, 0), "button pressed");
            // Must not throw, and the action must have run exactly once.
            boolean handled = WidgetDispatch.release(widgets, 5, 5, 0);
            assertTrue(handled, "release claimed");
            assertEquals(1, rebuilt[0], "action ran once despite the rebuild");

            // The control the rebuild created was never pressed, so it must not see the
            // release: a second delivery would let it double-apply its own action.
            List<Widget> onlyFresh = new ArrayList<>(widgets);
            assertTrue(!WidgetDispatch.release(onlyFresh, 5, 5, 0), "fresh control ignores the release");

            // The old code path, kept as the proof the guard is load-bearing: iterating the
            // live list over the same mutation is what threw in the report.
            List<Widget> live = new ArrayList<>();
            ButtonWidget mutator = new ButtonWidget(new Rect(0, 0, 40, 20), "mutate", () -> live.clear());
            live.add(mutator);
            live.add(new ButtonWidget(new Rect(0, 30, 40, 20), "tail", () -> { }));
            assertTrue(mutator.mouseClicked(5, 5, 0), "mutator pressed");
            boolean threw = false;
            try {
                for (Widget widget : live) {
                    widget.mouseReleased(5, 5, 0);
                }
            } catch (java.util.ConcurrentModificationException expected) {
                threw = true;
            }
            assertTrue(threw, "walking the live list still throws, so the copy is required");
        });

        check("widget dispatch tolerates an empty or missing list", () -> {
            assertTrue(!WidgetDispatch.release(null, 0, 0, 0), "null list");
            assertTrue(!WidgetDispatch.release(new ArrayList<>(), 0, 0, 0), "empty list");
        });

        // Two callers, two policies: the settings pane accumulates, the main screen stops at
        // the first handler. Both snapshot; only one stops early. Checked so the split cannot
        // be "simplified" back into a single wrong method.
        check("dispatch stops at the first handler only when asked", () -> {
            // Both share one rect on purpose: the dispatcher hands the SAME cursor position
            // to every widget, so two buttons in different places could never both be hit by
            // a single call. Overlapping controls are also the honest case here -- the
            // question is "does the walk stop or continue", not "which one was under the
            // cursor".
            int[] runs = {0, 0};
            ButtonWidget first = new ButtonWidget(new Rect(0, 0, 40, 20), "first", () -> runs[0]++);
            ButtonWidget second = new ButtonWidget(new Rect(0, 0, 40, 20), "second", () -> runs[1]++);
            List<Widget> both = new ArrayList<>();
            both.add(first);
            both.add(second);

            assertTrue(first.mouseClicked(5, 5, 0), "first pressed");
            assertTrue(second.mouseClicked(5, 5, 0), "second pressed");

            WidgetDispatch.releaseFirst(both, 5, 5, 0);
            assertEquals(1, runs[0], "first handler ran");
            assertEquals(0, runs[1], "second skipped by releaseFirst");

            runs[0] = 0;
            runs[1] = 0;
            assertTrue(first.mouseClicked(5, 5, 0), "first re-pressed");
            assertTrue(second.mouseClicked(5, 5, 0), "second re-pressed");
            WidgetDispatch.release(both, 5, 5, 0);
            assertEquals(1, runs[0], "release reaches the first");
            assertEquals(1, runs[1], "release also reaches the second");
        });
    }

    /** Keeps a reference to Widget/Painter so the checks compile against the public API. */
    @SuppressWarnings("unused")
    private static void apiSmoke(Widget widget, Painter painter, ThemeTokens theme, Clock clock) {
        widget.render(painter, theme);
        clock.monotonicNanos();
    }
}

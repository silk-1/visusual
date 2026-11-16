package silkvis.ui.widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Delivers input to a group of widgets without letting a widget's own action break the walk.
 *
 * <p>Why this class exists: a button runs its action on release, and the action often rebuilds
 * the very list being iterated -- a settings pane clears its controls and recreates them when a
 * value changes, because dependent options appear and disappear. Iterating the live list then
 * threw {@code ConcurrentModificationException} from inside {@code mouseReleased} and crashed
 * the game on the first setting clicked. The release is therefore delivered to a copy taken
 * before the first widget runs.</p>
 *
 * <p>Iterating a copy also settles a question the live list answered by accident: a control
 * created by the rebuild must not receive the release that created it. It was never pressed,
 * so letting it see the event is meaningless at best and could double-apply a click.</p>
 *
 * <p>Minecraft-free by design, so the self-check can exercise the mutation instead of trusting
 * a comment (UI-01).</p>
 */
public final class WidgetDispatch {
    private WidgetDispatch() {
    }

    /**
     * Releases the mouse over each widget in turn.
     *
     * @param widgets  live list; may be rebuilt by any widget's action while this runs
     * @param mouseX   logical cursor x
     * @param mouseY   logical cursor y
     * @param button   mouse button index
     * @return true when any widget claimed the event
     */
    public static boolean release(List<Widget> widgets, double mouseX, double mouseY, int button) {
        if (widgets == null || widgets.isEmpty()) {
            return false;
        }
        boolean handled = false;
        // Snapshot first: an action may clear and refill the caller's list.
        for (Widget widget : new ArrayList<>(widgets)) {
            handled |= widget.mouseReleased(mouseX, mouseY, button);
        }
        return handled;
    }

    /**
     * Same delivery, but stops at the first widget that claims the event.
     *
     * <p>Kept separate from {@link #release} because the two callers genuinely differ: a
     * settings pane accumulates the result over all its controls, while the main screen stops
     * at the first one that handles the click. Both need the snapshot; only one stops early.
     * </p>
     *
     * @param widgets live list; may be rebuilt by any widget's action while this runs
     * @param mouseX  logical cursor x
     * @param mouseY  logical cursor y
     * @param button  mouse button index
     * @return true when a widget claimed the event
     */
    public static boolean releaseFirst(List<Widget> widgets, double mouseX, double mouseY,
                                       int button) {
        if (widgets == null || widgets.isEmpty()) {
            return false;
        }
        for (Widget widget : new ArrayList<>(widgets)) {
            if (widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }
}

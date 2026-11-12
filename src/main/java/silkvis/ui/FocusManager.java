package silkvis.ui;

import silkvis.ui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Focus routing (UI-06): modal > focused field > screen > HUD > game. Tab/Shift+Tab
 * move focus inside the current focus scope only.
 */
public final class FocusManager {
    private final List<Widget> scope = new ArrayList<>();
    private int index = -1;

    public void setScope(List<? extends Widget> widgets) {
        scope.clear();
        index = -1;
        for (Widget w : widgets) {
            if (w.focusable()) {
                scope.add(w);
            }
        }
    }

    public List<Widget> scope() {
        return List.copyOf(scope);
    }

    public void clearFocus() {
        for (Widget w : scope) {
            w.setFocused(false);
        }
        index = -1;
    }

    public void focus(Widget widget) {
        clearFocus();
        int i = scope.indexOf(widget);
        if (i >= 0) {
            index = i;
            widget.setFocused(true);
        }
    }

    public Widget focused() {
        return index < 0 || index >= scope.size() ? null : scope.get(index);
    }

    public boolean tab(boolean backwards) {
        if (scope.isEmpty()) {
            return false;
        }
        clearFocus();
        index = backwards
                ? (index <= 0 ? scope.size() - 1 : index - 1)
                : (index + 1) % scope.size();
        scope.get(index).setFocused(true);
        return true;
    }
}

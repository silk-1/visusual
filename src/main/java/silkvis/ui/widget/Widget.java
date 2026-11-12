package silkvis.ui.widget;

import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;

/**
 * Base control. Coordinates are logical GUI units; hit testing uses the same space
 * (UI-01). Widgets never touch Minecraft classes directly.
 */
public abstract class Widget {
    protected Rect bounds;
    protected boolean enabled = true;
    protected boolean visible = true;
    protected boolean focused;
    protected String tooltipKey;
    protected String disabledReasonKey;

    protected Widget(Rect bounds) {
        this.bounds = bounds;
    }

    public Rect bounds() {
        return bounds;
    }

    public void setBounds(Rect newBounds) {
        this.bounds = newBounds;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean value) {
        this.visible = value;
    }

    public boolean focused() {
        return focused;
    }

    public void setFocused(boolean value) {
        this.focused = value;
    }

    public String tooltipKey() {
        return tooltipKey;
    }

    public String disabledReasonKey() {
        return disabledReasonKey;
    }

    public boolean focusable() {
        return enabled && visible;
    }

    /** Minimum clickable area is 20x20 logical units (UI-02). */
    public boolean hit(double mouseX, double mouseY) {
        return visible && bounds.contains((int) Math.floor(mouseX), (int) Math.floor(mouseY));
    }

    public abstract void render(Painter painter, ThemeTokens theme);

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr) {
        return false;
    }
}

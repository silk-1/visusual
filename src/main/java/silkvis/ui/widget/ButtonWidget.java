package silkvis.ui.widget;

import silkvis.ui.Color;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;

/** Text button with an explicit action; states are also labelled, not colour-only (UI-01). */
public final class ButtonWidget extends Widget {
    public enum Style {
        PRIMARY,
        SECONDARY,
        DANGER
    }

    private final String label;
    private final Runnable action;
    private Style style = Style.SECONDARY;
    private boolean pressed;
    private boolean hovered;

    public ButtonWidget(Rect bounds, String label, Runnable action) {
        super(bounds);
        this.label = label;
        this.action = action;
    }

    public ButtonWidget style(Style value) {
        this.style = value;
        return this;
    }

    public String label() {
        return label;
    }

    @Override
    public void render(Painter painter, ThemeTokens theme) {
        if (!visible) {
            return;
        }
        Color fill = !enabled ? theme.surface().withAlpha(theme.surface().alpha() * 0.6f)
                : hovered ? theme.surfaceHover()
                : style == Style.PRIMARY ? theme.accent().withAlpha(0.22f)
                : theme.surface();
        painter.fill(bounds, Color.argb(fill));
        painter.outline(bounds, Color.argb(style == Style.PRIMARY ? theme.accent() : theme.border()), 1);
        int textColor = Color.argb(!enabled ? theme.textSecondary()
                : style == Style.DANGER ? theme.error() : theme.textPrimary());
        int width = painter.textWidth(label);
        painter.text(label, bounds.centerX() - width / 2,
                bounds.centerY() - painter.lineHeight() / 2, textColor, false);
        if (focused) {
            painter.outline(bounds.inset(-1, -1), Color.argb(theme.accent()), 1);
        }
    }

    public void setHovered(boolean value) {
        this.hovered = value && enabled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || !hit(mouseX, mouseY) || button != 0) {
            return false;
        }
        pressed = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!pressed) {
            return false;
        }
        pressed = false;
        if (hit(mouseX, mouseY) && button == 0 && action != null) {
            action.run();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!focusable() || (keyCode != Keys.ENTER && keyCode != Keys.SPACE)) {
            return false;
        }
        if (action != null) {
            action.run();
        }
        return true;
    }
}

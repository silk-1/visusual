package silkvis.ui.widget;

import silkvis.ui.Color;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;

/** Toggle with label, description and a disabled reason (UI-04). */
public final class ToggleWidget extends Widget {
    private final String label;
    private final String descriptionKey;
    private boolean value;
    private final BooleanConsumer onChange;
    private boolean hovered;

    public ToggleWidget(Rect bounds, String label, String descriptionKey, boolean initial, BooleanConsumer onChange) {
        super(bounds);
        this.label = label;
        this.descriptionKey = descriptionKey;
        this.value = initial;
        this.onChange = onChange;
    }

    public boolean value() {
        return value;
    }

    public void setValue(boolean newValue) {
        this.value = newValue;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public String label() {
        return label;
    }

    @Override
    public void render(Painter painter, ThemeTokens theme) {
        int toggleWidth = 28;
        int toggleHeight = 14;
        Rect track = new Rect(bounds.right() - toggleWidth, bounds.centerY() - toggleHeight / 2,
                toggleWidth, toggleHeight);
        painter.text(label, bounds.x(), bounds.centerY() - painter.lineHeight() / 2,
                Color.argb(enabled ? theme.textPrimary() : theme.textSecondary()), false);
        painter.fill(track, Color.argb(!enabled ? theme.surface()
                : value ? theme.accent().withAlpha(0.85f) : theme.surfaceHover()));
        painter.outline(track, Color.argb(theme.border()), 1);
        int knobSize = toggleHeight - 4;
        int knobX = value ? track.right() - knobSize - 2 : track.x() + 2;
        painter.fill(new Rect(knobX, track.y() + 2, knobSize, knobSize),
                Color.argb(enabled ? theme.textPrimary() : theme.textSecondary()));
        // Text state so the meaning does not depend on colour alone (UI-01).
        String stateKey = !enabled ? "off" : value ? "on" : "off";
        int stateWidth = painter.textWidth(stateKey);
        painter.text(stateKey, track.x() - stateWidth - 6, bounds.centerY() - painter.lineHeight() / 2,
                Color.argb(theme.textSecondary()), false);
        if (hovered) {
            painter.outline(bounds, Color.argb(theme.border()), 1);
        }
        if (focused) {
            painter.outline(bounds.inset(-1, -1), Color.argb(theme.accent()), 1);
        }
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    private void toggle() {
        if (!enabled) {
            return;
        }
        value = !value;
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0 || !hit(mouseX, mouseY)) {
            return false;
        }
        toggle();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!focusable() || (keyCode != Keys.ENTER && keyCode != Keys.SPACE)) {
            return false;
        }
        toggle();
        return true;
    }
}

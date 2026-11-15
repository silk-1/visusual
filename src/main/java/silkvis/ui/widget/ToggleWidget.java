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
        // Focus halo goes first: the switch panel is drawn on top of it, which leaves a
        // clean ring without an alpha-0 "erase" pass.
        if (focused) {
            painter.roundRect(track.inset(-2, -2), toggleHeight / 2 + 2, Color.argb(theme.accent()));
        }
        // Row highlight, then the label, so the text stays readable.
        if (hovered) {
            painter.roundRect(bounds, theme.radiusControl(),
                    Color.argb(theme.surfaceHover().withAlpha(0.18f)));
        }
        painter.text(label, bounds.x(), bounds.centerY() - painter.lineHeight() / 2,
                Color.argb(enabled ? theme.textPrimary() : theme.textSecondary()), false);
        // Pill switch: radius is half the height, which reads as a switch, not a checkbox.
        painter.roundPanel(track, toggleHeight / 2,
                Color.argb(!enabled ? theme.surface()
                        : value ? theme.accent().withAlpha(0.85f) : theme.surfaceHover()),
                Color.argb(theme.border()));
        int knobSize = toggleHeight - 6;
        int knobX = value ? track.right() - knobSize - 4 : track.x() + 4;
        Rect knob = new Rect(knobX, track.centerY() - knobSize / 2, knobSize, knobSize);
        painter.roundRect(knob, knobSize / 2,
                Color.argb(enabled ? theme.textPrimary() : theme.textSecondary()));
        // Text state so the meaning does not depend on colour alone (UI-01).
        String stateKey = !enabled ? "off" : value ? "on" : "off";
        int stateWidth = painter.textWidth(stateKey);
        painter.text(stateKey, track.x() - stateWidth - 6, bounds.centerY() - painter.lineHeight() / 2,
                Color.argb(theme.textSecondary()), false);
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

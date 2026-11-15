package silkvis.ui.widget;

import silkvis.ui.Animation;
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
    private final Animation.Fade hoverFade = new Animation.Fade();

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
        // Hover is a 140 ms fade, not a jump (UI-07). The reduced-motion theme reports
        // motionMs() == 0, which makes the same call snap to the target.
        float hover = hoverFade.update(hovered, theme.motionMs());
        int fillArgb;
        if (!enabled) {
            fillArgb = theme.surface().withAlpha(theme.surface().alpha() * 0.6f).argb();
        } else if (style == Style.PRIMARY) {
            fillArgb = Animation.mix(theme.accent().withAlpha(0.18f).argb(),
                    theme.accent().withAlpha(0.30f).argb(), hover);
        } else {
            fillArgb = Animation.mix(theme.surface().argb(), theme.surfaceHover().argb(), hover);
        }
        // Border and fill are two rounded shapes (UI-02 radius). The fill is inset by one
        // unit so the border stays a ring; drawing both on the same rect made a translucent
        // PRIMARY fill sit on top of an opaque accent border, i.e. a solid accent plate.
        int radius = theme.radiusControl();
        Rect outer = focused ? bounds.inset(-1, -1) : bounds;
        int outerRadius = focused ? radius + 1 : radius;
        painter.roundRect(outer, outerRadius,
                Color.argb(style == Style.PRIMARY || focused ? theme.accent() : theme.border()));
        painter.roundRect(outer.inset(1, 1), Math.max(0, outerRadius - 1), fillArgb);
        int textColor = Color.argb(!enabled ? theme.textSecondary()
                : style == Style.DANGER ? theme.error() : theme.textPrimary());
        int width = painter.textWidth(label);
        painter.text(label, bounds.centerX() - width / 2,
                bounds.centerY() - painter.lineHeight() / 2, textColor, false);
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

package silkvis.ui.widget;

import silkvis.ui.Color;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;

import java.util.function.DoubleConsumer;

/**
 * Slider with numeric readout (UI-05): dragging only previews; the value is committed
 * on release or Enter, so the config is not written on every mouse move.
 */
public final class SliderWidget extends Widget {
    private final double min;
    private final double max;
    private final double step;
    private final String label;
    private final String unitKey;
    private final DoubleConsumer onPreview;
    private final DoubleConsumer onCommit;
    private double value;
    private double preview;
    private boolean dragging;

    public SliderWidget(Rect bounds, String label, String unitKey, double min, double max, double step,
                        double initial, DoubleConsumer onPreview, DoubleConsumer onCommit) {
        super(bounds);
        if (max <= min) {
            throw new IllegalArgumentException("max must be > min");
        }
        this.min = min;
        this.max = max;
        this.step = step <= 0 ? (max - min) / 100.0 : step;
        this.label = label;
        this.unitKey = unitKey;
        this.value = clamp(initial);
        this.preview = value;
        this.onPreview = onPreview;
        this.onCommit = onCommit;
    }

    public double value() {
        return value;
    }

    public void setValue(double newValue) {
        this.value = clamp(newValue);
        this.preview = this.value;
    }

    private double clamp(double v) {
        double snapped = Math.round((v - min) / step) * step + min;
        return Math.max(min, Math.min(max, snapped));
    }

    private Rect track() {
        return new Rect(bounds.x(), bounds.centerY() - 2, Math.max(0, bounds.width() - 56), 4);
    }

    private void updateFromMouse(double mouseX) {
        Rect track = track();
        double ratio = track.width() == 0 ? 0 : Math.max(0, Math.min(1, (mouseX - track.x()) / track.width()));
        preview = clamp(min + (max - min) * ratio);
        if (onPreview != null) {
            onPreview.accept(preview);
        }
    }

    @Override
    public void render(Painter painter, ThemeTokens theme) {
        Rect track = track();
        painter.text(label, bounds.x(), bounds.y() - painter.lineHeight() - 1,
                Color.argb(theme.textSecondary()), false);
        painter.fill(track, Color.argb(theme.surfaceHover()));
        int fillWidth = (int) Math.round(track.width() * ((preview - min) / (max - min)));
        painter.fill(new Rect(track.x(), track.y(), fillWidth, track.height()), Color.argb(theme.accent()));
        int knobX = track.x() + fillWidth - 3;
        painter.fill(new Rect(knobX, track.y() - 3, 6, track.height() + 6),
                Color.argb(enabled ? theme.textPrimary() : theme.textSecondary()));
        String display = formatValue(preview) + (unitKey == null ? "" : " " + unitKey);
        painter.text(display, bounds.right() - painter.textWidth(display),
                bounds.centerY() - painter.lineHeight() / 2, Color.argb(theme.textPrimary()), false);
        if (focused) {
            painter.outline(bounds, Color.argb(theme.accent()), 1);
        }
    }

    private String formatValue(double v) {
        if (step >= 1) {
            return String.valueOf(Math.round(v));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0 || !hit(mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        updateFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) {
            return false;
        }
        updateFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        value = preview;
        if (onCommit != null) {
            onCommit.accept(value);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!focusable()) {
            return false;
        }
        double delta = (modifiers & Keys.MOD_SHIFT) != 0 ? step * 10 : step;
        switch (keyCode) {
            case Keys.LEFT -> {
                preview = clamp(preview - delta);
                return true;
            }
            case Keys.RIGHT -> {
                preview = clamp(preview + delta);
                return true;
            }
            case Keys.ENTER -> {
                value = preview;
                if (onCommit != null) {
                    onCommit.accept(value);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}

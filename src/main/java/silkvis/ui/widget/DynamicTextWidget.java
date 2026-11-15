package silkvis.ui.widget;

import silkvis.ui.Color;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;

import java.util.function.Supplier;

/**
 * One-line, read-only text that re-reads its value from a supplier (diagnostics rows).
 *
 * <p>The supplier is polled at most a few times per second: a render loop must not turn a
 * cheap status string into per-frame allocation, and a slow supplier must not be able to
 * stall the frame. Failures are shown as text, never thrown into the render path.</p>
 */
public final class DynamicTextWidget extends Widget {
    private static final long REFRESH_NANOS = 250_000_000L;

    private final Supplier<String> supplier;
    private String cached = "";
    private long nextRefreshAt;

    public DynamicTextWidget(Rect bounds, Supplier<String> supplier) {
        super(bounds);
        this.supplier = supplier;
    }

    @Override
    public void render(Painter painter, ThemeTokens theme) {
        long now = System.nanoTime();
        if (now - nextRefreshAt >= 0) {
            nextRefreshAt = now + REFRESH_NANOS;
            cached = read();
        }
        painter.text(cached, bounds.x(), bounds.y(), Color.argb(theme.textSecondary()), false);
    }

    private String read() {
        if (supplier == null) {
            return "";
        }
        try {
            String value = supplier.get();
            return value == null ? "" : value;
        } catch (RuntimeException e) {
            return "<" + e.getClass().getSimpleName() + ">";
        }
    }
}

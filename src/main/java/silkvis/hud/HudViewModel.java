package silkvis.hud;

import silkvis.ui.hud.HudWidgetId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cached, immutable text model rendered by the HUD (HUD-04). Nothing here performs I/O,
 * native calls or network access during rendering.
 */
public final class HudViewModel {
    private final Map<HudWidgetId, List<String>> lines;
    private final long capturedAtNanos;

    public HudViewModel(Map<HudWidgetId, List<String>> lines, long capturedAtNanos) {
        this.lines = Collections.unmodifiableMap(new EnumMap<>(lines));
        this.capturedAtNanos = capturedAtNanos;
    }

    public static HudViewModel empty() {
        return new HudViewModel(new EnumMap<>(HudWidgetId.class), 0L);
    }

    public List<String> lines(HudWidgetId id) {
        return lines.getOrDefault(id, List.of());
    }

    public Map<HudWidgetId, List<String>> all() {
        return lines;
    }

    public long capturedAtNanos() {
        return capturedAtNanos;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Content size used for layout, in logical units, given a monospace-ish line height. */
    public int width(HudWidgetId id, int textWidthOfLongestLine) {
        return textWidthOfLongestLine;
    }
}

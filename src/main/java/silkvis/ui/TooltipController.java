package silkvis.ui;

import silkvis.core.Clock;

/**
 * Tooltip with a 350 ms delay (UI-07) and a monotonic timer, so hovering does not
 * flicker when the mouse moves between widgets.
 */
public final class TooltipController {
    private final Clock clock;
    private final long delayNanos;
    private String key;
    private long hoverStart;
    private int x;
    private int y;

    public TooltipController(Clock clock, long delayMillis) {
        this.clock = clock;
        this.delayNanos = delayMillis * 1_000_000L;
    }

    public void hover(String newKey, int mouseX, int mouseY) {
        if (newKey == null || !newKey.equals(key)) {
            key = newKey;
            hoverStart = clock.monotonicNanos();
        }
        x = mouseX;
        y = mouseY;
    }

    public void clear() {
        key = null;
    }

    public boolean visible() {
        return key != null && clock.monotonicNanos() - hoverStart >= delayNanos;
    }

    public String key() {
        return key;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /** Wraps long text instead of clipping (UI-07: long text expands in the tooltip). */
    public static java.util.List<String> wrap(String text, int maxChars) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        int limit = Math.max(8, maxChars);
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() + word.length() + 1 > limit && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}

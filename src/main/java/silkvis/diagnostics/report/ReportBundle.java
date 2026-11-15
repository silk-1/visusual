package silkvis.diagnostics.report;

/**
 * Ordered, line-oriented body of a bug report (TEST-06 / support requirement).
 *
 * <p>Pure Java on purpose: the report layout is unit-testable without Minecraft and the
 * size cap is enforced in one place. Nothing here formats anything privacy-sensitive on
 * its own — the caller decides what goes in.</p>
 */
public final class ReportBundle {
    private final StringBuilder body = new StringBuilder(8192);
    private final int maxChars;
    private boolean truncated;

    public ReportBundle(int maxChars) {
        this.maxChars = Math.max(1024, maxChars);
    }

    public ReportBundle section(String title) {
        return append("\n== " + (title == null ? "" : title) + " ==\n");
    }

    public ReportBundle line(String text) {
        return append((text == null ? "" : text) + "\n");
    }

    public ReportBundle keyValue(String key, Object value) {
        return line(key + ": " + (value == null ? "-" : value));
    }

    /** Multi-line block (log tail, config dump); a trailing newline is added if missing. */
    public ReportBundle text(String block) {
        if (block == null || block.isEmpty()) {
            return this;
        }
        return append(block.endsWith("\n") ? block : block + "\n");
    }

    private ReportBundle append(String chunk) {
        if (chunk == null || chunk.isEmpty() || truncated) {
            return this;
        }
        int room = maxChars - body.length();
        if (room <= 0) {
            truncated = true;
            return this;
        }
        if (chunk.length() <= room) {
            body.append(chunk);
        } else {
            body.append(chunk, 0, room);
            body.append("\n[report truncated at ").append(maxChars).append(" characters]\n");
            truncated = true;
        }
        return this;
    }

    public boolean truncated() {
        return truncated;
    }

    public int length() {
        return body.length();
    }

    @Override
    public String toString() {
        return body.toString();
    }
}

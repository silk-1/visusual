package silkvis.ui;

/** Axis-aligned rectangle in logical GUI units (UI-01), not physical pixels. */
public record Rect(int x, int y, int width, int height) {
    public Rect {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("negative size: " + width + "x" + height);
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(int px, int py) {
        return px >= x && py >= y && px < right() && py < bottom();
    }

    public Rect inset(int dx, int dy) {
        int w = Math.max(0, width - dx * 2);
        int h = Math.max(0, height - dy * 2);
        return new Rect(x + dx, y + dy, w, h);
    }

    public Rect inset(int left, int top, int right, int bottom) {
        return new Rect(x + left, y + top,
                Math.max(0, width - left - right), Math.max(0, height - top - bottom));
    }

    public Rect withPosition(int nx, int ny) {
        return new Rect(nx, ny, width, height);
    }

    public Rect withSize(int w, int h) {
        return new Rect(x, y, Math.max(0, w), Math.max(0, h));
    }

    public boolean intersects(Rect other) {
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public Rect translate(int dx, int dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    /** Clamps a rectangle so it stays inside {@code bounds}; shrinks it when larger. */
    public static Rect clampInto(Rect r, Rect bounds) {
        int w = Math.min(r.width(), bounds.width());
        int h = Math.min(r.height(), bounds.height());
        int nx = Math.max(bounds.x(), Math.min(r.x(), bounds.right() - w));
        int ny = Math.max(bounds.y(), Math.min(r.y(), bounds.bottom() - h));
        return new Rect(nx, ny, w, h);
    }
}

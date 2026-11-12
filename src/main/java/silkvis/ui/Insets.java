package silkvis.ui;

/** Uniform spacing insets in logical GUI units (spacing scale 4/8/12/16/24, UI-02). */
public record Insets(int left, int top, int right, int bottom) {
    public static Insets all(int v) {
        return new Insets(v, v, v, v);
    }

    public int horizontal() {
        return left + right;
    }

    public int vertical() {
        return top + bottom;
    }
}

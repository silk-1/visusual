package silkvis.ui;

/** Scroll state for long panels (UI-03): bounded, clamped, and keyboard reachable. */
public final class ScrollModel {
    private final int viewportHeight;
    private final int stepPixels;
    private int contentHeight;
    private int offset;

       public ScrollModel(int viewportHeight, int stepPixels) {
        this.viewportHeight = Math.max(0, viewportHeight);
        this.stepPixels = Math.max(8, stepPixels);
    }

    public void setContentHeight(int height) {
        this.contentHeight = Math.max(0, height);
        this.offset = Math.max(0, Math.min(offset, maxOffset()));
    }

    public int maxOffset() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    public int offset() {
        return offset;
    }

    public void setOffset(int newOffset) {
        this.offset = Math.max(0, Math.min(newOffset, maxOffset()));
    }

    public boolean scroll(double amount) {
        int before = offset;
        setOffset(offset - (int) Math.round(amount * stepPixels));
        return offset != before;
    }

    public void scrollBy(int delta) {
        setOffset(offset + delta);
    }

    public void ensureVisible(int itemTop, int itemHeight) {
        if (itemTop < offset) {
            setOffset(itemTop);
        } else if (itemTop + itemHeight > offset + viewportHeight) {
            setOffset(itemTop + itemHeight - viewportHeight);
        }
    }

    public boolean needsScrollbar() {
        return maxOffset() > 0;
    }

    public int thumbY(int trackY, int trackHeight) {
        if (!needsScrollbar()) {
            return trackY;
        }
        return trackY + (int) ((long) offset * (trackHeight - thumbHeight(trackHeight)) / maxOffset());
    }

    public int thumbHeight(int trackHeight) {
        if (contentHeight <= 0) {
            return trackHeight;
        }
        int h = Math.max(12, (int) ((long) trackHeight * viewportHeight / contentHeight));
        return Math.min(trackHeight, h);
    }

    public int viewportHeight() {
        return viewportHeight;
    }

    public int contentHeight() {
        return contentHeight;
    }
}

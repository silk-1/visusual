package silkvis.visual.soup.util;

/** Ported from SoupVisuals {@code padej.soup.base.util.other.StopWatch} (Padej_). */
public class StopWatch {
    private long startTime;

    public StopWatch() {
        this.reset();
    }

    public boolean finished(double delay) {
        return System.currentTimeMillis() - delay >= this.startTime;
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Milliseconds since start. The original uses {@code Math.toIntExact}; that throws once
     * a bubble lives longer than ~24 days, which cannot happen here, but a plain cast keeps
     * a paused game from crashing the render thread.
     */
    public int elapsedTime() {
        return (int) (System.currentTimeMillis() - this.startTime);
    }

    public StopWatch setMs(long ms) {
        this.startTime = System.currentTimeMillis() - ms;
        return this;
    }

    public long getStartTime() {
        return this.startTime;
    }
}

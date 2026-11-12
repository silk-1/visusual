package silkvis.core;

enum SystemClock implements Clock {
    INSTANCE;

    @Override
    public long monotonicNanos() {
        return System.nanoTime();
    }

    @Override
    public long epochMillis() {
        return System.currentTimeMillis();
    }
}

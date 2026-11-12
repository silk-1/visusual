package silkvis.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Identity of a client world session (ARCH-04). Background results tagged with an
 * outdated token are discarded instead of being published.
 */
public final class WorldSessionToken {
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final long id;
    private final String worldProfileId;

    private WorldSessionToken(long id, String worldProfileId) {
        this.id = id;
        this.worldProfileId = worldProfileId;
    }

    public static WorldSessionToken mint(String worldProfileId) {
        return new WorldSessionToken(SEQUENCE.incrementAndGet(), worldProfileId);
    }

    /** Token that never matches a live session; used before the first world join. */
    public static WorldSessionToken none() {
        return new WorldSessionToken(0L, "");
    }

    public long id() {
        return id;
    }

    public String worldProfileId() {
        return worldProfileId;
    }

    public boolean isNone() {
        return id == 0L;
    }

    public boolean matches(WorldSessionToken other) {
        return other != null && other.id == id && id != 0L;
    }

    @Override
    public String toString() {
        return "WorldSessionToken[" + id + "," + worldProfileId + "]";
    }
}

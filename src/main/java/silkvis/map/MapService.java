package silkvis.map;

import silkvis.core.Clock;
import silkvis.core.WorldSessionToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Orchestrates dirty tracking, budgeted sampling and publication of immutable tiles
 * (MAP-06, ARCH-03/05, PERF-02).
 *
 * <p>Sampling happens in the client thread within a per-tick budget; disk writes go to
 * the I/O executor. Results tagged with an outdated world token are dropped.</p>
 */
public final class MapService {
    public static final long DEFAULT_SAMPLE_BUDGET_NANOS = 1_000_000L; // 1 ms/tick
    public static final int MAX_PENDING_JOBS = 512;

    private final Clock clock;
    private final BoundedTileCache cache;
    private final TileStore store;
    private final ExecutorService io;
    private final TileSampler sampler;
    private final Consumer<MapTile> publisher;

    private final LinkedHashMap<String, TileKey> dirty = new LinkedHashMap<>();
    private final Map<String, Long> revisions = new HashMap<>();

    private WorldSessionToken token = WorldSessionToken.none();
    private MapIdentity identity;
    private long sampleBudgetNanos = DEFAULT_SAMPLE_BUDGET_NANOS;
    private long sampledThisTick;
    private long droppedStale;
    private long failedSamples;
    private boolean paused;

    public MapService(Clock clock, BoundedTileCache cache, TileStore store, ExecutorService io,
                      TileSampler sampler, Consumer<MapTile> publisher) {
        this.clock = clock;
        this.cache = cache;
        this.store = store;
        this.io = io;
        this.sampler = sampler;
        this.publisher = publisher;
    }

    public synchronized void onSessionChanged(WorldSessionToken newToken, MapIdentity newIdentity) {
        this.token = newToken == null ? WorldSessionToken.none() : newToken;
        this.identity = newIdentity;
        dirty.clear();
        revisions.clear();
    }

    public synchronized Optional<MapIdentity> identity() {
        return Optional.ofNullable(identity);
    }

    public synchronized int pendingJobs() {
        return dirty.size();
    }

    public synchronized void setPaused(boolean value) {
        this.paused = value;
    }

    public synchronized boolean paused() {
        return paused;
    }

    public void setSampleBudgetNanos(long budget) {
        this.sampleBudgetNanos = Math.max(100_000L, Math.min(budget, 8_000_000L));
    }

    /** Marks a tile dirty, coalescing duplicates and adding the LOD parents (ARCH-05). */
    public synchronized void markDirty(TileKey key) {
        if (key == null) {
            return;
        }
        for (TileKey k = key; k != null; k = k.parent()) {
            if (dirty.size() >= MAX_PENDING_JOBS) {
                return;
            }
            dirty.putIfAbsent(k.dirtyKey(), k);
        }
    }

    /** Cached tile lookup for rendering; never blocks. */
    public Optional<MapTile> view(TileKey key) {
        return cache.get(key);
    }

    /** Loads a tile from disk into the cache (I/O executor; safe to call off-thread). */
    public void preload(TileKey key) {
        store.read(key).ifPresent(cache::put);
    }

    /**
     * Processes dirty tiles until the per-tick budget is exhausted. Returns the number
     * of tiles sampled in this call.
     */
    public synchronized int tick() {
        if (paused || identity == null || token.isNone()) {
            return 0;
        }
        long start = clock.monotonicNanos();
        int sampled = 0;
        List<String> processed = new ArrayList<>();
        for (Map.Entry<String, TileKey> entry : dirty.entrySet()) {
            if (clock.monotonicNanos() - start >= sampleBudgetNanos) {
                break;
            }
            TileKey key = entry.getValue();
            if (!key.identity().equals(identity)) {
                droppedStale++;
                processed.add(entry.getKey());
                continue;
            }
            long revision = revisions.merge(key.dirtyKey(), 1L, Long::sum);
            try {
                MapTile tile = sampler.sample(key, revision);
                processed.add(entry.getKey());
                if (tile == null) {
                    // Area not loaded yet: keep it queued for a later tick without burning budget.
                    continue;
                }
                cache.put(tile);
                sampled++;
                sampledThisTick++;
                publisher.accept(tile);
                submitWrite(tile);
            } catch (TileSampler.SamplingException e) {
                failedSamples++;
                processed.add(entry.getKey());
            } catch (RuntimeException e) {
                failedSamples++;
                processed.add(entry.getKey());
            }
        }
        for (String key : processed) {
            dirty.remove(key);
        }
        return sampled;
    }

    private void submitWrite(MapTile tile) {
        WorldSessionToken writerToken = token;
        io.execute(() -> {
            if (!tokenMatches(writerToken)) {
                return;
            }
            try {
                store.write(tile);
            } catch (java.io.IOException ignored) {
                // A failed cache write must never break gameplay; the tile is rebuilt later.
            }
        });
    }

    private synchronized boolean tokenMatches(WorldSessionToken other) {
        return token.matches(other);
    }

    /** Diagnostics counters for the performance overlay and the acceptance report. */
    public synchronized String describe() {
        return "map jobs=" + dirty.size() + " sampledTick=" + sampledThisTick
                + " stale=" + droppedStale + " failed=" + failedSamples
                + " cache=" + cache.size() + "/" + (cache.bytes() / 1024) + "KiB"
                + " hit=" + String.format(java.util.Locale.ROOT, "%.2f", cache.hitRate());
    }

    /** Called at the end of the tick to reset per-tick counters. */
    public synchronized void endTick() {
        sampledThisTick = 0;
    }
}

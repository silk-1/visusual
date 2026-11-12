package silkvis.map;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded LRU cache for immutable tiles (MAP-07). Separate instances are used for the
 * CPU copy and for the GPU upload mirror, with independent limits.
 */
public final class BoundedTileCache {
    private final long maxBytes;
    private final LinkedHashMap<TileKey, MapTile> map = new LinkedHashMap<>(256, 0.75f, true);
    private long bytes;
    private long hits;
    private long misses;
    private long evictions;

    public BoundedTileCache(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    public synchronized Optional<MapTile> get(TileKey key) {
        MapTile tile = map.get(key);
        if (tile == null) {
            misses++;
            return Optional.empty();
        }
        hits++;
        return Optional.of(tile);
    }

    public synchronized void put(MapTile tile) {
        MapTile previous = map.put(tile.key(), tile);
        if (previous != null) {
            bytes -= previous.byteSize();
        }
        bytes += tile.byteSize();
        evictIfNeeded();
    }

    private void evictIfNeeded() {
        Iterator<Map.Entry<TileKey, MapTile>> it = map.entrySet().iterator();
        while (bytes > maxBytes && it.hasNext()) {
            Map.Entry<TileKey, MapTile> eldest = it.next();
            bytes -= eldest.getValue().byteSize();
            it.remove();
            evictions++;
        }
    }

    public synchronized void remove(TileKey key) {
        MapTile removed = map.remove(key);
        if (removed != null) {
            bytes -= removed.byteSize();
        }
    }

    /** Drops every tile of one map identity (world/dimension switch, MAP-07 reset). */
    public synchronized int clearIdentity(MapIdentity identity) {
        int removed = 0;
        Iterator<Map.Entry<TileKey, MapTile>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TileKey, MapTile> e = it.next();
            if (e.getKey().identity().equals(identity)) {
                bytes -= e.getValue().byteSize();
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    public synchronized void clear() {
        map.clear();
        bytes = 0;
    }

    public synchronized int size() {
        return map.size();
    }

    public synchronized long bytes() {
        return bytes;
    }

    public synchronized long maxBytes() {
        return maxBytes;
    }

    public synchronized long evictions() {
        return evictions;
    }

    public synchronized double hitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}

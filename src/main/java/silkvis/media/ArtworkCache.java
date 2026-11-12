package silkvis.media;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded RAM artwork cache (MEDIA-17): 32 MiB by default, no disk cache, entries are
 * keyed by content hash so late artwork for a previous track cannot be shown (MEDIA-14).
 */
public final class ArtworkCache {
    public static final long DEFAULT_MAX_BYTES = 32L * 1024 * 1024;

    private final long maxBytes;
    private final LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>(64, 0.75f, true);
    private long bytes;

    public ArtworkCache(long maxBytes) {
        this.maxBytes = Math.max(1, maxBytes);
    }

    public synchronized Optional<byte[]> get(String hash) {
        byte[] data = entries.get(hash);
        return data == null ? Optional.empty() : Optional.of(data.clone());
    }

    public synchronized boolean put(String hash, byte[] png) {
        if (hash == null || png == null || png.length == 0 || png.length > IpcProtocol.MAX_ARTWORK_BYTES) {
            return false;
        }
        byte[] previous = entries.put(hash, png);
        if (previous != null) {
            bytes -= previous.length;
        }
        bytes += png.length;
        evict();
        return previous == null;
    }

    private void evict() {
        Iterator<Map.Entry<String, byte[]>> it = entries.entrySet().iterator();
        while (bytes > maxBytes && it.hasNext()) {
            Map.Entry<String, byte[]> eldest = it.next();
            bytes -= eldest.getValue().length;
            it.remove();
        }
    }

    public synchronized void clear() {
        entries.clear();
        bytes = 0;
    }

    public synchronized long bytes() {
        return bytes;
    }

    public synchronized int size() {
        return entries.size();
    }

    /** PNG signature + 512 KiB cap enforced before any decode (MEDIA-07). */
    public static boolean looksLikePng(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        return (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }
}

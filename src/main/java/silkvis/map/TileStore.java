package silkvis.map;

import silkvis.core.Clock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Disk tile store with atomic writes and quota enforcement (MAP-07, MAP-08).
 * Quota eviction only removes terrain tiles; markers and zones live in independent
 * files and are never touched by cache maintenance.
 */
public final class TileStore {
    private final Path mapDir;
    private final Clock clock;

    public TileStore(Path mapDir, Clock clock) {
        this.mapDir = mapDir;
        this.clock = clock;
    }

    public Path mapDir() {
        return mapDir;
    }

    private Path tileFile(TileKey key) {
        return mapDir.resolve("tiles").resolve("l" + key.lod()).resolve(key.storageName());
    }

    public Optional<MapTile> read(TileKey key) {
        Path file = tileFile(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            long size = Files.size(file);
            if (size <= 0 || size > 1024 * 1024) {
                deleteQuietly(file);
                return Optional.empty();
            }
            MapTile tile = TileCodec.decode(Files.readAllBytes(file));
            if (!tile.key().equals(key)) {
                // File content belongs to a different key: quarantine by deletion and rebuild.
                deleteQuietly(file);
                return Optional.empty();
            }
            return Optional.of(tile);
        } catch (IOException e) {
            deleteQuietly(file);
            return Optional.empty();
        }
    }

    /** Writes through a temp file and an atomic rename; never leaves a half tile. */
    public void write(MapTile tile) throws IOException {
        Path file = tileFile(tile.key());
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(temp, TileCodec.encode(tile));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Total cached bytes for the profile. */
    public long usedBytes() throws IOException {
        Path tiles = mapDir.resolve("tiles");
        if (!Files.isDirectory(tiles)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(tiles)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }
    }

    /**
     * Enforces the quota by deleting the oldest derived tiles. User markers are stored
     * elsewhere and are therefore preserved (MAP-07).
     *
     * @return number of deleted tile files
     */
    public int enforceQuota(long maxBytes) throws IOException {
        if (maxBytes <= 0) {
            return 0;
        }
        Path tiles = mapDir.resolve("tiles");
        if (!Files.isDirectory(tiles)) {
            return 0;
        }
        List<Path> files = new ArrayList<>();
        long total = 0L;
        try (Stream<Path> walk = Files.walk(tiles)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                long size = Files.size(p);
                total += size;
                files.add(p);
            }
        }
        if (total <= maxBytes) {
            return 0;
        }
        files.sort(Comparator.comparingLong(this::lastModified));
        int deleted = 0;
        for (Path p : files) {
            if (total <= maxBytes) {
                break;
            }
            long size = Files.size(p);
            if (Files.deleteIfExists(p)) {
                total -= size;
                deleted++;
            }
        }
        return deleted;
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best effort: a corrupt tile is simply reported as missing next time.
        }
    }

    /** Human-readable cache snapshot for the settings screen (MAP-07). */
    public String describe() {
        try {
            return usedBytes() + " B in " + mapDir.getFileName();
        } catch (IOException e) {
            return "unavailable";
        }
    }

    public Clock clock() {
        return clock;
    }
}

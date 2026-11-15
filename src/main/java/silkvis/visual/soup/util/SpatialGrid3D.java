package silkvis.visual.soup.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ported from SoupVisuals {@code padej.soup.base.util.spatial.SpatialGrid3D} (Padej_).
 *
 * <p>Bucketed 3D grid used by the "Network" particle mode to find neighbours without an
 * O(n^2) pass. Cell size is the link distance, so a neighbour search only has to look at
 * the 27 surrounding cells -- same design as the original.</p>
 */
public final class SpatialGrid3D<T> {
    private final float cellSize;
    private final Map<GridKey, List<GridEntry<T>>> cells = new HashMap<>();

    public SpatialGrid3D(float cellSize) {
        this.cellSize = Math.max(0.5F, cellSize);
    }

    public void insert(T item, float x, float y, float z) {
        GridKey key = key(x, y, z);
        this.cells.computeIfAbsent(key, k -> new ArrayList<>(4)).add(new GridEntry<>(item, x, y, z));
    }

    public void remove(T item, float x, float y, float z) {
        GridKey key = key(x, y, z);
        List<GridEntry<T>> bucket = this.cells.get(key);
        if (bucket == null) {
            return;
        }
        bucket.removeIf(entry -> entry.item() == item);
        if (bucket.isEmpty()) {
            this.cells.remove(key);
        }
    }

    /** Moves an item between cells; falls back to remove+insert when the cell changed. */
    public void update(T item, float oldX, float oldY, float oldZ, float newX, float newY, float newZ) {
        GridKey from = key(oldX, oldY, oldZ);
        GridKey to = key(newX, newY, newZ);
        if (from.equals(to)) {
            List<GridEntry<T>> bucket = this.cells.get(from);
            if (bucket != null) {
                for (int i = 0; i < bucket.size(); i++) {
                    if (bucket.get(i).item() == item) {
                        bucket.set(i, new GridEntry<>(item, newX, newY, newZ));
                        return;
                    }
                }
            }
            insert(item, newX, newY, newZ);
            return;
        }
        remove(item, oldX, oldY, oldZ);
        insert(item, newX, newY, newZ);
    }

    /**
     * Every item within {@code radius} of the point, in the original's order (cell by cell).
     * The distance check uses squared distances, like the caller in the modules does.
     */
    public List<T> query(float x, float y, float z, float radius) {
        List<T> out = new ArrayList<>();
        int range = Math.max(1, (int) Math.ceil(radius / this.cellSize));
        int cx = cellX(x);
        int cy = cellY(y);
        int cz = cellZ(z);
        float radiusSq = radius * radius;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    List<GridEntry<T>> bucket = this.cells.get(new GridKey(cx + dx, cy + dy, cz + dz));
                    if (bucket == null) {
                        continue;
                    }
                    for (GridEntry<T> entry : bucket) {
                        float ddx = entry.x() - x;
                        float ddy = entry.y() - y;
                        float ddz = entry.z() - z;
                        if (ddx * ddx + ddy * ddy + ddz * ddz <= radiusSq) {
                            out.add(entry.item());
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Marks a cell as visited so the network renderer does not draw the same link twice. */
    public Set<Long> newVisitedSet() {
        return new HashSet<>();
    }

    /** Stable key of a pair of cells, used together with {@link #newVisitedSet()}. */
    public static long pairKey(int ax, int ay, int az, int bx, int by, int bz) {
        long hash = 1125899906842597L;
        for (int value : new int[]{ax, ay, az, bx, by, bz}) {
            hash = 31L * hash + value;
        }
        return hash;
    }

    public int cellX(float x) {
        return (int) Math.floor(x / this.cellSize);
    }

    public int cellY(float y) {
        return (int) Math.floor(y / this.cellSize);
    }

    public int cellZ(float z) {
        return (int) Math.floor(z / this.cellSize);
    }

    public int size() {
        int total = 0;
        for (List<GridEntry<T>> bucket : this.cells.values()) {
            total += bucket.size();
        }
        return total;
    }

    public void clear() {
        this.cells.clear();
    }

    private GridKey key(float x, float y, float z) {
        return new GridKey(cellX(x), cellY(y), cellZ(z));
    }

    private record GridKey(int x, int y, int z) {
    }

    private record GridEntry<V>(V item, float x, float y, float z) {
    }
}

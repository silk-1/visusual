package silkvis.client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

/**
 * Album art as a coarse colour grid (MEDIA-16/17, known-limitations).
 *
 * <p>There is no GPU texture upload path in SilkVis yet, so the cover is drawn as a grid of
 * axis-aligned fills, the same way the map draws terrain. Decoding happens on the IO
 * executor and never on the render thread: {@link ImageIO} is plain JDK code, so this does
 * not depend on any Minecraft renderer API that could differ between versions.</p>
 *
 * <p>Nothing is cached on disk (MEDIA-17): the grid lives in a small RAM map keyed by the
 * artwork content hash, and {@code ArtworkCache} already deduplicates the bytes.</p>
 */
final class ArtworkPixels {
    /** Cells per side; a card shows it at 2 px per cell, so 26 cells = 52 px of cover. */
    static final int GRID = 26;
    private static final int MAX_ENTRIES = 8;

    private static final Map<String, int[]> CACHE =
            new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true);
    private static final Set<String> PENDING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private ArtworkPixels() {
    }

    /**
     * @return the grid when it is already decoded, otherwise null and a decode is started.
     *         Callers draw a placeholder for this frame; art appears a frame later instead
     *         of stalling the render thread.
     */
    static int[] grid(String hash, byte[] png, ExecutorService io) {
        if (hash == null || hash.isBlank() || png == null || png.length == 0 || io == null) {
            return null;
        }
        synchronized (CACHE) {
            int[] cached = CACHE.get(hash);
            if (cached != null) {
                return cached;
            }
            if (FAILED.contains(hash) || !PENDING.add(hash)) {
                return null;
            }
        }
        byte[] copy = png.clone();
        io.execute(() -> {
            int[] grid = null;
            try {
                grid = decode(copy);
            } catch (RuntimeException | LinkageError e) {
                // LinkageError too: a jlink image without java.desktop would fail to load
                // ImageIO, and that must degrade to a placeholder, not crash the game.
                grid = null;
            }
            synchronized (CACHE) {
                PENDING.remove(hash);
                if (grid == null) {
                    FAILED.add(hash);
                    return;
                }
                CACHE.put(hash, grid);
                while (CACHE.size() > MAX_ENTRIES) {
                    var it = CACHE.keySet().iterator();
                    if (!it.hasNext()) {
                        break;
                    }
                    it.next();
                    it.remove();
                }
            }
        });
        return null;
    }

    /** Average colour of each cell, packed ARGB. */
    static int[] decode(byte[] png) {
        BufferedImage image;
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            image = ImageIO.read(in);
        } catch (java.io.IOException e) {
            return null;
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return null;
        }
        int[] grid = new int[GRID * GRID];
        for (int gy = 0; gy < GRID; gy++) {
            int y0 = gy * image.getHeight() / GRID;
            int y1 = Math.max(y0 + 1, (gy + 1) * image.getHeight() / GRID);
            for (int gx = 0; gx < GRID; gx++) {
                int x0 = gx * image.getWidth() / GRID;
                int x1 = Math.max(x0 + 1, (gx + 1) * image.getWidth() / GRID);
                long r = 0, g = 0, b = 0;
                int count = 0;
                for (int y = y0; y < y1 && y < image.getHeight(); y++) {
                    for (int x = x0; x < x1 && x < image.getWidth(); x++) {
                        int rgb = image.getRGB(x, y);
                        r += (rgb >> 16) & 0xFF;
                        g += (rgb >> 8) & 0xFF;
                        b += rgb & 0xFF;
                        count++;
                    }
                }
                if (count == 0) {
                    grid[gy * GRID + gx] = 0;
                    continue;
                }
                grid[gy * GRID + gx] = 0xFF000000
                        | ((int) (r / count) << 16)
                        | ((int) (g / count) << 8)
                        | (int) (b / count);
            }
        }
        return grid;
    }

    /** Diagnostics for the Music tab: how many covers are decoded, how many failed. */
    static String describe() {
        synchronized (CACHE) {
            return "art=" + CACHE.size() + " pending=" + PENDING.size() + " failed=" + FAILED.size();
        }
    }

    static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
            PENDING.clear();
            FAILED.clear();
        }
    }
}

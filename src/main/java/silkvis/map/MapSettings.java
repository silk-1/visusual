package silkvis.map;

import silkvis.config.ConfigException;
import silkvis.config.Validators;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

/**
 * Map settings (MAP-03..07). The disk quota never removes user markers; the terrain
 * cache limits are separate from the map quota.
 */
public record MapSettings(
        int diskQuotaMiB,
        int terrainCacheMiB,
        int minimapSize,
        MinimapShape shape,
        boolean northUp,
        int zoom,
        boolean showCardinals,
        boolean showCoordinates,
        MapMode mode,
        int sliceY
) {
    public enum MinimapShape {
        CIRCLE,
        SQUARE
    }

    public static final int HARD_MAX_QUOTA_MIB = 4096;
    public static final int HARD_MAX_CACHE_MIB = 512;

    public static MapSettings defaults() {
        return new MapSettings(512, 64, 128, MinimapShape.CIRCLE, true, 1, true, true,
                MapMode.SURFACE, 64);
    }

    public long diskQuotaBytes() {
        return (long) diskQuotaMiB * 1024 * 1024;
    }

    public long terrainCacheBytes() {
        return (long) terrainCacheMiB * 1024 * 1024;
    }

    public JsonObject encode() {
        return JsonValue.newObject()
                .put("diskQuotaMiB", diskQuotaMiB)
                .put("terrainCacheMiB", terrainCacheMiB)
                .put("minimapSize", minimapSize)
                .put("shape", shape.name())
                .put("northUp", northUp)
                .put("zoom", zoom)
                .put("showCardinals", showCardinals)
                .put("showCoordinates", showCoordinates)
                .put("mode", mode.name())
                .put("sliceY", sliceY);
    }

    public static MapSettings decode(JsonObject o) throws ConfigException {
        MapSettings d = defaults();
        MinimapShape shape = d.shape();
        if (o.has("shape")) {
            try {
                shape = MinimapShape.valueOf(o.get("shape").string());
            } catch (IllegalArgumentException e) {
                throw ConfigException.of("map.shape", "unknown value");
            }
        }
        MapMode mode = d.mode();
        if (o.has("mode")) {
            try {
                mode = MapMode.valueOf(o.get("mode").string());
            } catch (IllegalArgumentException e) {
                throw ConfigException.of("map.mode", "unknown value");
            }
        }
        return new MapSettings(
                o.has("diskQuotaMiB") ? o.get("diskQuotaMiB").number().intValueExact() : d.diskQuotaMiB(),
                o.has("terrainCacheMiB") ? o.get("terrainCacheMiB").number().intValueExact() : d.terrainCacheMiB(),
                o.has("minimapSize") ? o.get("minimapSize").number().intValueExact() : d.minimapSize(),
                shape,
                o.has("northUp") ? o.get("northUp").bool() : d.northUp(),
                o.has("zoom") ? o.get("zoom").number().intValueExact() : d.zoom(),
                o.has("showCardinals") ? o.get("showCardinals").bool() : d.showCardinals(),
                o.has("showCoordinates") ? o.get("showCoordinates").bool() : d.showCoordinates(),
                mode,
                o.has("sliceY") ? o.get("sliceY").number().intValueExact() : d.sliceY());
    }

    public void validate() throws ConfigException {
        Validators.intRange("map.diskQuotaMiB", diskQuotaMiB, 32, HARD_MAX_QUOTA_MIB);
        Validators.intRange("map.terrainCacheMiB", terrainCacheMiB, 8, HARD_MAX_CACHE_MIB);
        Validators.intRange("map.minimapSize", minimapSize, 64, 256);
        Validators.intRange("map.zoom", zoom, 1, 8);
        Validators.intRange("map.sliceY", sliceY, -64, 320);
        if (shape == null || mode == null) {
            throw ConfigException.of("map", "shape/mode required");
        }
    }
}

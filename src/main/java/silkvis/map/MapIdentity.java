package silkvis.map;

import silkvis.config.ConfigPaths;

/**
 * MapIdentity = world profile + dimension + format version (+ mode/slice because those
 * change the tile content). See MAP-02/MAP-05/ADR-0005.
 */
public record MapIdentity(
        String worldProfileId,
        String dimensionId,
        int formatVersion,
        MapMode mode,
        int sliceY
) {
    public static final int FORMAT_VERSION = 1;

    public MapIdentity {
        if (worldProfileId == null || worldProfileId.isBlank()) {
            throw new IllegalArgumentException("worldProfileId required");
        }
        if (dimensionId == null || !dimensionId.contains(":")) {
            throw new IllegalArgumentException("dimensionId must be a registry id");
        }
        if (mode == null) {
            mode = MapMode.SURFACE;
        }
        if (mode == MapMode.SURFACE) {
            sliceY = 0;
        }
    }

    public static MapIdentity surface(String worldProfileId, String dimensionId) {
        return new MapIdentity(worldProfileId, dimensionId, FORMAT_VERSION, MapMode.SURFACE, 0);
    }

    public static MapIdentity slice(String worldProfileId, String dimensionId, int y) {
        return new MapIdentity(worldProfileId, dimensionId, FORMAT_VERSION, MapMode.SLICE, y);
    }

    /** Directory name inside {@code silkvis-data/maps}; sanitized, collision-free. */
    public String storageKey() {
        String raw = worldProfileId + "__" + dimensionId.replace(':', '_') + "__v" + formatVersion
                + "__" + mode.id() + (mode == MapMode.SLICE ? ("_y" + sliceY) : "");
        return ConfigPaths.safeSegment(raw) + "-" + shortHash();
    }

    public String displayKey() {
        return worldProfileId + "|" + dimensionId + "|" + mode.id() + (mode == MapMode.SLICE ? "@" + sliceY : "");
    }

    private String shortHash() {
        return silkvis.config.ProfileIdentity.shortHash(displayKey());
    }
}

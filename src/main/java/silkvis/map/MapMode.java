package silkvis.map;

import java.util.Locale;

/**
 * MAP-05: the Nether needs an explicit slice mode, otherwise the roof surface is
 * useless. The mode and slice level are part of the tile key.
 */
public enum MapMode {
    SURFACE,
    SLICE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}

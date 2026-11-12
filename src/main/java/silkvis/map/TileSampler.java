package silkvis.map;

/**
 * Samples terrain colours/height for one tile. Called on the client thread in small
 * slices (MAP-06); an implementation must not read disk, network or unrelated chunks.
 */
public interface TileSampler {
    /**
     * @param revision monotonically increasing revision of the requested tile
     * @return immutable tile, or {@code null} when the area is not loaded yet
     * @throws SamplingException when the tile cannot be produced for a known reason
     */
    MapTile sample(TileKey key, long revision) throws SamplingException;

    /** Thrown when sampling is impossible; the scheduler backs off instead of spinning. */
    final class SamplingException extends Exception {
        public SamplingException(String message) {
            super(message);
        }
    }
}

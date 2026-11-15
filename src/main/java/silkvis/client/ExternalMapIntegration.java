package silkvis.client;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Optional;

/**
 * Detects an installed third-party map (ADR-0007: AtlasMap / AtlasMapExtended).
 *
 * <p>Integration is presence-only, exactly as ADR-0006 requires: nothing from the other
 * mod is bundled, linked or loaded. Two consequences follow from detection, and both are
 * deliberate:</p>
 *
 * <ul>
 *   <li>SilkVis stops building its own tiles — sampling terrain that will never be drawn is
 *       pure waste, and two map pipelines in one client is how players get 40 fps;</li>
 *   <li>the SilkVis minimap widget is turned off once, because two minimaps overlap and the
 *       external one is the better map.</li>
 * </ul>
 *
 * <p>The version string is read from the mod metadata, which is plain data available before
 * any class of that mod is touched.</p>
 */
public final class ExternalMapIntegration {
    /** Fork first: it is the maintained one, and it wins when both are somehow present. */
    private static final String[] CANDIDATES = {"atlasmapextended", "atlasmap"};

    /**
     * @param modId  Fabric mod id
     * @param version friendly version from the mod metadata, may be empty
     * @param name   display name for the UI
     */
    public record Info(String modId, String version, String name) {
        public String describe() {
            return version.isEmpty() ? name : name + " " + version;
        }
    }

    private ExternalMapIntegration() {
    }

    public static Optional<Info> detect() {
        FabricLoader loader;
        try {
            loader = FabricLoader.getInstance();
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
        for (String id : CANDIDATES) {
            boolean present;
            try {
                present = loader.isModLoaded(id);
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            if (!present) {
                continue;
            }
            String version = "";
            String name = id;
            try {
                var container = loader.getModContainer(id);
                if (container.isPresent()) {
                    version = container.get().getMetadata().getVersion().getFriendlyString();
                    name = container.get().getMetadata().getName();
                }
            } catch (RuntimeException | LinkageError e) {
                // Metadata is optional for display purposes only; presence already decided.
            }
            return Optional.of(new Info(id, version == null ? "" : version,
                    name == null || name.isBlank() ? id : name));
        }
        return Optional.empty();
    }
}

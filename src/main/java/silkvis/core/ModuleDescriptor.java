package silkvis.core;

import java.util.List;
import java.util.Set;

/**
 * Static module metadata (ARCH-02). Display strings are lang keys, never literal text,
 * so RU/EN stays in resources (UI-09).
 *
 * @param id          stable dot-free id used in config and logs
 * @param category    navigation category
 * @param displayKey  lang key of the localized name
 * @param descKey     lang key of the localized description
 * @param schemaVersion config schema version of this module's settings
 * @param capabilities capabilities the module needs; missing ones map to CAPABILITY_UNSUPPORTED
 * @param conflicts   module ids that must not run at the same time
 */
public record ModuleDescriptor(
        String id,
        ModuleCategory category,
        String displayKey,
        String descKey,
        int schemaVersion,
        Set<Capability> capabilities,
        List<String> conflicts
) {
    public ModuleDescriptor {
        if (id == null || id.isBlank() || !id.matches("[a-z0-9_.]+")) {
            throw new IllegalArgumentException("invalid module id: " + id);
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public static ModuleDescriptor of(String id, ModuleCategory category, String displayKey, String descKey) {
        return new ModuleDescriptor(id, category, displayKey, descKey, 1, Set.of(), List.of());
    }
}

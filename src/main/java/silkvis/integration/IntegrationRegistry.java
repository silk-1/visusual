package silkvis.integration;

import silkvis.core.Capability;
import silkvis.core.ErrorCode;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Capability discovery for optional mods (ARCH-07). Detection never loads classes from
 * an absent mod: the probe is a supplier that only reports presence, and any error is
 * swallowed into "absent" so the main UI cannot be broken by an adapter.
 */
public final class IntegrationRegistry {
    public enum ShaderMode {
        VANILLA,
        SODIUM,
        IRIS_NO_PACK,
        IRIS_WITH_PACK
    }

    private final Map<String, Boolean> presence = new ConcurrentHashMap<>();
    private final Map<String, String> failures = new ConcurrentHashMap<>();

    public void probe(String modId, BooleanSupplier isLoaded) {
        try {
            presence.put(modId, isLoaded != null && isLoaded.getAsBoolean());
        } catch (RuntimeException | LinkageError e) {
            presence.put(modId, false);
            failures.put(modId, e.getClass().getSimpleName());
        }
    }

    public boolean isPresent(String modId) {
        return presence.getOrDefault(modId, false);
    }

    public Optional<String> failure(String modId) {
        return Optional.ofNullable(failures.get(modId));
    }

    public Set<Capability> capabilities(boolean mediaPlatformSupported, boolean hudHookAvailable) {
        EnumSet<Capability> caps = EnumSet.noneOf(Capability.class);
        caps.add(Capability.CLIENT_RENDER);
        if (hudHookAvailable) {
            caps.add(Capability.HUD_LAYERS);
        }
        if (mediaPlatformSupported) {
            caps.add(Capability.MEDIA_PROVIDER);
        }
        if (isPresent("sodium")) {
            caps.add(Capability.MOD_SODIUM);
        }
        if (isPresent("iris")) {
            caps.add(Capability.MOD_IRIS);
        }
        if (isPresent("baritone")) {
            caps.add(Capability.MOD_BARITONE);
        }
        return caps;
    }

    /**
     * AUTO visual mode (VIS-04): chooses the least conflicting path. Custom sky is not
     * promised with an arbitrary shader pack, so a loaded pack downgrades the claim.
     */
    public ShaderMode shaderMode(boolean shaderPackActive) {
        if (isPresent("iris")) {
            return shaderPackActive ? ShaderMode.IRIS_WITH_PACK : ShaderMode.IRIS_NO_PACK;
        }
        return isPresent("sodium") ? ShaderMode.SODIUM : ShaderMode.VANILLA;
    }

    /** Reason shown in the Integrations tab when a capability is missing. */
    public Optional<ErrorCode> unavailableReason(Capability capability) {
        return switch (capability) {
            case MEDIA_PROVIDER -> Optional.of(ErrorCode.PLATFORM_UNSUPPORTED);
            case HUD_LAYERS -> Optional.of(ErrorCode.CAPABILITY_UNSUPPORTED);
            default -> Optional.empty();
        };
    }
}

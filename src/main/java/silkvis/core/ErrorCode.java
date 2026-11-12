package silkvis.core;

/**
 * Stable error classification (ARCH-06). Codes are part of logs and the
 * diagnostics export; localized user text lives in the lang files under
 * {@code silkvis.error.<lowercase code>}.
 */
public enum ErrorCode {
    CONFIG_INVALID,
    RESOURCE_INVALID,
    PROVIDER_UNAVAILABLE,
    CAPABILITY_UNSUPPORTED,
    TIMEOUT,
    WORLD_CHANGED,
    PERMISSION_DENIED,
    PATH_UNREACHABLE,
    INVENTORY_DESYNC,
    INTEGRATION_CONFLICT,
    PLATFORM_UNSUPPORTED,
    CANCELLED,
    INTERNAL
}

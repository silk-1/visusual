package silkvis.core;

/** Lifecycle state kept separate from availability (ARCH-02). */
public enum ModuleState {
    DISABLED,
    ENABLED,
    RUNNING,
    DEGRADED,
    FAILED
}

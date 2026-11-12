package silkvis.media;

/** Provider lifecycle (7.2). NO_SESSION is a normal state, not an error. */
public enum ProviderStatus {
    DISABLED,
    STARTING,
    READY,
    NO_SESSION,
    UNSUPPORTED,
    ERROR
}

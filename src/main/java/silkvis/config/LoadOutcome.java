package silkvis.config;

import java.util.Optional;

/**
 * Result of a config load (CFG-04). {@code writable == false} means the file on disk must
 * never be replaced: it was written by a newer build or could not be understood.
 */
public final class LoadOutcome<T> {
    public enum Status {
        /** No file yet: defaults are in memory and may be written. */
        FRESH,
        /** File parsed and validated. */
        LOADED,
        /** File was damaged: defaults are used, the damaged copy was preserved. */
        SAFE_DEFAULT,
        /** File has a newer schema version: defaults are used, nothing may be written. */
        READ_ONLY_NEWER
    }

    private final Status status;
    private final T value;
    private final ConfigException error;
    private final boolean writable;

    private LoadOutcome(Status status, T value, ConfigException error, boolean writable) {
        this.status = status;
        this.value = value;
        this.error = error;
        this.writable = writable;
    }

    public static <T> LoadOutcome<T> fresh(T value) {
        return new LoadOutcome<>(Status.FRESH, value, null, true);
    }

    public static <T> LoadOutcome<T> loaded(T value) {
        return new LoadOutcome<>(Status.LOADED, value, null, true);
    }

    public static <T> LoadOutcome<T> safeDefault(T value, ConfigException error) {
        return new LoadOutcome<>(Status.SAFE_DEFAULT, value, error, true);
    }

    public static <T> LoadOutcome<T> readOnlyNewer(T value, ConfigException error) {
        return new LoadOutcome<>(Status.READ_ONLY_NEWER, value, error, false);
    }

    public Status status() {
        return status;
    }

    public T value() {
        return value;
    }

    public Optional<ConfigException> error() {
        return Optional.ofNullable(error);
    }

    public boolean writable() {
        return writable;
    }
}

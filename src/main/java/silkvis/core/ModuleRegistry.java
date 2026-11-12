package silkvis.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of modules with explicit state and reason codes. A module that cannot run
 * reports a reason instead of breaking the UI (ARCH-02).
 */
public final class ModuleRegistry {
    /** Runtime entry point owned by a module; implementations are expected to be cheap. */
    public interface Module {
        void enable();

        void disable();

        default void close() {
            disable();
        }
    }

    /** Module runtime state plus the reason it is not available. */
    public static final class Entry {
        private final ModuleDescriptor descriptor;
        private final Module module;
        private ModuleState state = ModuleState.DISABLED;
        private ErrorCode reasonCode;
        private String reasonDetail = "";

        Entry(ModuleDescriptor descriptor, Module module) {
            this.descriptor = descriptor;
            this.module = module;
        }

        public ModuleDescriptor descriptor() {
            return descriptor;
        }

        public ModuleState state() {
            return state;
        }

        public Optional<ErrorCode> reasonCode() {
            return Optional.ofNullable(reasonCode);
        }

        public String reasonDetail() {
            return reasonDetail;
        }

        public boolean available() {
            return reasonCode == null;
        }

        public void markUnavailable(ErrorCode code, String detail) {
            this.reasonCode = code;
            this.reasonDetail = detail == null ? "" : detail;
            this.state = ModuleState.DISABLED;
        }

        public void clearUnavailable() {
            this.reasonCode = null;
            this.reasonDetail = "";
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public void register(ModuleDescriptor descriptor, Module module) {
        if (entries.containsKey(descriptor.id())) {
            throw new IllegalStateException("duplicate module id: " + descriptor.id());
        }
        entries.put(descriptor.id(), new Entry(descriptor, module));
    }

    public Optional<Entry> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Collection<Entry> all() {
        return List.copyOf(entries.values());
    }

    public List<Entry> byCategory(ModuleCategory category) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.descriptor().category() == category) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Enables a module after checking availability and conflicts. Never throws:
     * a failing module degrades to FAILED with a reason code.
     */
    public synchronized EnableResult enable(String id) {
        Entry entry = entries.get(id);
        if (entry == null) {
            return EnableResult.rejected(ErrorCode.CONFIG_INVALID, "unknown module " + id);
        }
        if (!entry.available()) {
            return EnableResult.rejected(entry.reasonCode().orElse(ErrorCode.CAPABILITY_UNSUPPORTED),
                    entry.reasonDetail());
        }
        for (String other : entry.descriptor.conflicts()) {
            Entry o = entries.get(other);
            if (o != null && o.state != ModuleState.DISABLED) {
                return EnableResult.rejected(ErrorCode.INTEGRATION_CONFLICT, other);
            }
        }
        try {
            entry.module.enable();
            entry.state = ModuleState.ENABLED;
            return EnableResult.ok();
        } catch (RuntimeException e) {
            entry.state = ModuleState.FAILED;
            return EnableResult.rejected(ErrorCode.INTERNAL, e.getClass().getSimpleName());
        }
    }

    public synchronized void disable(String id) {
        Entry entry = entries.get(id);
        if (entry == null) {
            return;
        }
        try {
            entry.module.disable();
        } catch (RuntimeException ignored) {
            // Disable must always release resources; the module is marked FAILED below.
            entry.state = ModuleState.FAILED;
            return;
        }
        entry.state = ModuleState.DISABLED;
    }

    public synchronized void disableAll() {
        for (Entry e : entries.values()) {
            if (e.state != ModuleState.DISABLED) {
                disable(e.descriptor().id());
            }
        }
    }

    /** Result of an enable attempt, localized by the caller. */
    public record EnableResult(boolean enabled, Optional<ErrorCode> reason, String detail) {
        static EnableResult ok() {
            return new EnableResult(true, Optional.empty(), "");
        }

        static EnableResult rejected(ErrorCode code, String detail) {
            return new EnableResult(false, Optional.of(code), detail == null ? "" : detail);
        }
    }
}

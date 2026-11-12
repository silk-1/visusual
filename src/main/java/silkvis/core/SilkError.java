package silkvis.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable error value object. User-facing text is derived from {@link #code()};
 * {@link #context()} is for diagnostics only and must never contain command text,
 * credentials or absolute user paths (ARCH-06).
 */
public final class SilkError {
    private final ErrorCode code;
    private final Map<String, String> context;
    private final Throwable cause;

    private SilkError(ErrorCode code, Map<String, String> context, Throwable cause) {
        this.code = code;
        this.context = Map.copyOf(context);
        this.cause = cause;
    }

    public static SilkError of(ErrorCode code) {
        return new SilkError(code, Map.of(), null);
    }

    public static SilkError of(ErrorCode code, String key, Object value) {
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put(key, String.valueOf(value));
        return new SilkError(code, ctx, null);
    }

    public static SilkError wrap(ErrorCode code, Throwable cause) {
        Throwable c = cause;
        Map<String, String> ctx = new LinkedHashMap<>();
        if (c != null) {
            ctx.put("cause", c.getClass().getSimpleName());
        }
        return new SilkError(code, ctx, c);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> context() {
        return context;
    }

    public Throwable cause() {
        return cause;
    }

    public String langKey() {
        return "silkvis.error." + code.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return context.isEmpty() ? code.name() : code.name() + context;
    }
}

package silkvis.ui;

/** Resolves a lang key to localized text; the client adapter supplies the Minecraft implementation. */
@FunctionalInterface
public interface Localizer {
    String get(String key);

    /**
     * Formatted variant. Translations that use placeholders are formatted with
     * {@link String#format}; a broken format string returns the raw text rather than an
     * exception in the render path.
     */
    default String get(String key, Object... args) {
        String template = get(key);
        if (args == null || args.length == 0 || template == null || !template.contains("%")) {
            return template;
        }
        try {
            return String.format(java.util.Locale.ROOT, template, args);
        } catch (RuntimeException e) {
            return template;
        }
    }
}

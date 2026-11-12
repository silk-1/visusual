package silkvis.ui;

/** Resolves a lang key to localized text; the client adapter supplies the Minecraft implementation. */
@FunctionalInterface
public interface Localizer {
    String get(String key);

    default String get(String key, Object... args) {
        return get(key);
    }
}

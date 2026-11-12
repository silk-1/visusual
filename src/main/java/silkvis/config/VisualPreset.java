package silkvis.config;

/** Visual quality preset (P1). Everything that costs frame time is expressed through it. */
public enum VisualPreset {
    LOW,
    BALANCED,
    QUALITY;

    /** Stable lowercase id used in lang keys ({@code silkvis.preset.&lt;id&gt;}). */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

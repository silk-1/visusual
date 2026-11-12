package silkvis.config;

import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

import java.util.Locale;
import java.util.Set;

/**
 * Global (per-installation) settings: language, visual preset, motion/flash guards and the
 * media provider block. Everything that changes frame time or reads the OS lives here, and
 * every field defaults to "off" (0.0 / P1 requirement: nothing enabled by default).
 */
public record GlobalConfig(
        String language,
        VisualPreset preset,
        boolean reducedMotion,
        boolean blurEnabled,
        boolean verboseDiagnostics,
        MediaConfig media
) {
    public static final Set<String> LANGUAGES = Set.of("en", "ru");

    public GlobalConfig {
        if (language == null || !LANGUAGES.contains(language)) {
            language = "en";
        }
        if (preset == null) {
            preset = VisualPreset.BALANCED;
        }
        if (media == null) {
            media = MediaConfig.defaults();
        }
    }

    public static GlobalConfig defaults() {
        return new GlobalConfig("en", VisualPreset.BALANCED, false, false, false, MediaConfig.defaults());
    }

    /** Blur is an expensive effect and is also suppressed when reduced motion is on. */
    public boolean effectiveBlur() {
        return blurEnabled && !reducedMotion;
    }

    public static final class Codec implements ConfigCodec<GlobalConfig> {
        public static final int SCHEMA_VERSION = 1;

        @Override
        public int currentSchemaVersion() {
            return SCHEMA_VERSION;
        }

        @Override
        public GlobalConfig defaults() {
            return GlobalConfig.defaults();
        }

        @Override
        public GlobalConfig decode(JsonObject root) throws ConfigException {
            GlobalConfig d = defaults();
            String language = root.has("language")
                    ? Validators.plainText("global.language", root.get("language").string(), 8)
                    : d.language();
            if (!LANGUAGES.contains(language)) {
                throw ConfigException.of("global.language", "unsupported language");
            }
            VisualPreset preset = d.preset();
            if (root.has("preset")) {
                String raw = root.get("preset").string();
                try {
                    preset = VisualPreset.valueOf(raw.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw ConfigException.of("global.preset", "unknown preset");
                }
            }
            boolean reducedMotion = root.has("reducedMotion") ? root.get("reducedMotion").bool() : d.reducedMotion();
            boolean blur = root.has("blurEnabled") ? root.get("blurEnabled").bool() : d.blurEnabled();
            boolean verbose = root.has("verboseDiagnostics")
                    ? root.get("verboseDiagnostics").bool()
                    : d.verboseDiagnostics();
            MediaConfig media = root.has("media") && root.get("media").object().size() >= 0
                    ? MediaConfig.decode(root.get("media").object())
                    : d.media();
            return new GlobalConfig(language, preset, reducedMotion, blur, verbose, media);
        }

        @Override
        public JsonObject encode(GlobalConfig value) {
            return JsonValue.newObject()
                    .put("language", value.language())
                    .put("preset", value.preset().name())
                    .put("reducedMotion", value.reducedMotion())
                    .put("blurEnabled", value.blurEnabled())
                    .put("verboseDiagnostics", value.verboseDiagnostics())
                    .put("media", value.media().encode());
        }

        @Override
        public void validate(GlobalConfig value) throws ConfigException {
            if (!LANGUAGES.contains(value.language())) {
                throw ConfigException.of("global.language", "unsupported language");
            }
            value.media().validate();
        }
    }
}

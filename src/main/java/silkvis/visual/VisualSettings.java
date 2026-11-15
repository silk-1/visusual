package silkvis.visual;

import silkvis.config.ConfigException;
import silkvis.config.Validators;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

/**
 * Camera/world visual settings (VIS-01, VIS-02). These change presentation only:
 * speed, reach, hitbox, damage and server state are never touched, and disabling a
 * module restores the effective vanilla behaviour instead of rewriting options.txt.
 */
public record VisualSettings(
        boolean fovEnabled,
        double fovDegrees,
        double viewBobbing,
        double hurtCameraIntensity,
        double screenEffectIntensity,
        boolean fogEnabled,
        double fogStartRatio,
        double fogEndRatio,
        int particleCap,
        double rainVisualIntensity,
        boolean cloudsVisible,
        boolean entityMarkersEnabled,
        boolean depthAwareMarkers,
        // Edge of the generated totem texture: 16, 32, 64 or 128 (see TotemPackBuilder).
        int totemTextureSize,
        // Cut the plain background out of the totem picture (white, black, grey and the
        // corners) so only the drawing is left. On by default: the owner asked for it.
        boolean totemTrimBackground
) {
    /** Valid totem edges; the generated pack uses one of these, never an arbitrary number. */
    public static final int[] TOTEM_TEXTURE_SIZES = {16, 32, 64, 128};

    public static VisualSettings defaults() {
        return new VisualSettings(false, 70, 1.0, 1.0, 1.0, false, 0.35, 1.0, 4000, 1.0, true, false,
                true, 128, true);
    }

    public VisualSettings(boolean fovEnabled, double fovDegrees, double viewBobbing,
                          double hurtCameraIntensity, double screenEffectIntensity, boolean fogEnabled,
                          double fogStartRatio, double fogEndRatio, int particleCap,
                          double rainVisualIntensity, boolean cloudsVisible,
                          boolean entityMarkersEnabled, boolean depthAwareMarkers) {
        this(fovEnabled, fovDegrees, viewBobbing, hurtCameraIntensity, screenEffectIntensity,
                fogEnabled, fogStartRatio, fogEndRatio, particleCap, rainVisualIntensity,
                cloudsVisible, entityMarkersEnabled, depthAwareMarkers, 128, true);
    }

    public JsonObject encode() {
        return JsonValue.newObject()
                .put("fovEnabled", fovEnabled)
                .put("fovDegrees", fovDegrees)
                .put("viewBobbing", viewBobbing)
                .put("hurtCameraIntensity", hurtCameraIntensity)
                .put("screenEffectIntensity", screenEffectIntensity)
                .put("fogEnabled", fogEnabled)
                .put("fogStartRatio", fogStartRatio)
                .put("fogEndRatio", fogEndRatio)
                .put("particleCap", particleCap)
                .put("rainVisualIntensity", rainVisualIntensity)
                .put("cloudsVisible", cloudsVisible)
                .put("entityMarkersEnabled", entityMarkersEnabled)
                .put("depthAwareMarkers", depthAwareMarkers)
                .put("totemTextureSize", totemTextureSize)
                .put("totemTrimBackground", totemTrimBackground);
    }

    public static VisualSettings decode(JsonObject o) throws ConfigException {
        VisualSettings d = defaults();
        return new VisualSettings(
                o.has("fovEnabled") ? o.get("fovEnabled").bool() : d.fovEnabled(),
                o.has("fovDegrees") ? o.get("fovDegrees").number().doubleValue() : d.fovDegrees(),
                o.has("viewBobbing") ? o.get("viewBobbing").number().doubleValue() : d.viewBobbing(),
                o.has("hurtCameraIntensity") ? o.get("hurtCameraIntensity").number().doubleValue()
                        : d.hurtCameraIntensity(),
                o.has("screenEffectIntensity") ? o.get("screenEffectIntensity").number().doubleValue()
                        : d.screenEffectIntensity(),
                o.has("fogEnabled") ? o.get("fogEnabled").bool() : d.fogEnabled(),
                o.has("fogStartRatio") ? o.get("fogStartRatio").number().doubleValue() : d.fogStartRatio(),
                o.has("fogEndRatio") ? o.get("fogEndRatio").number().doubleValue() : d.fogEndRatio(),
                o.has("particleCap") ? o.get("particleCap").number().intValueExact() : d.particleCap(),
                o.has("rainVisualIntensity") ? o.get("rainVisualIntensity").number().doubleValue()
                        : d.rainVisualIntensity(),
                o.has("cloudsVisible") ? o.get("cloudsVisible").bool() : d.cloudsVisible(),
                o.has("entityMarkersEnabled") ? o.get("entityMarkersEnabled").bool() : d.entityMarkersEnabled(),
                o.has("depthAwareMarkers") ? o.get("depthAwareMarkers").bool() : d.depthAwareMarkers(),
                o.has("totemTextureSize") ? o.get("totemTextureSize").number().intValueExact()
                        : d.totemTextureSize(),
                // Configs written before this option existed must get the default (on), not
                // "false because the key was missing".
                o.has("totemTrimBackground") ? o.get("totemTrimBackground").bool()
                        : d.totemTrimBackground());
    }

    public void validate() throws ConfigException {
        Validators.finite("visual.fovDegrees", fovDegrees, 30, 110);
        Validators.finite("visual.viewBobbing", viewBobbing, 0, 1);
        Validators.finite("visual.hurtCameraIntensity", hurtCameraIntensity, 0, 1);
        Validators.finite("visual.screenEffectIntensity", screenEffectIntensity, 0, 1);
        Validators.finite("visual.fogStartRatio", fogStartRatio, 0.05, 0.95);
        Validators.finite("visual.fogEndRatio", fogEndRatio, 0.2, 1.0);
        if (fogEndRatio <= fogStartRatio) {
            throw ConfigException.of("visual.fogEndRatio", "must be greater than fogStartRatio");
        }
        Validators.intRange("visual.particleCap", particleCap, 0, 8000);
        Validators.finite("visual.rainVisualIntensity", rainVisualIntensity, 0, 1);
        if (entityMarkersEnabled && !depthAwareMarkers) {
            throw ConfigException.of("visual.depthAwareMarkers",
                    "through-wall markers are not supported; markers stay depth-aware");
        }
        boolean knownSize = false;
        for (int size : TOTEM_TEXTURE_SIZES) {
            if (totemTextureSize == size) {
                knownSize = true;
            }
        }
        if (!knownSize) {
            throw ConfigException.of("visual.totemTextureSize", "must be 16, 32, 64 or 128");
        }
    }
}

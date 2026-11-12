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
        boolean depthAwareMarkers
) {
    public static VisualSettings defaults() {
        return new VisualSettings(false, 70, 1.0, 1.0, 1.0, false, 0.35, 1.0, 4000, 1.0, true, false, true);
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
                .put("depthAwareMarkers", depthAwareMarkers);
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
                o.has("depthAwareMarkers") ? o.get("depthAwareMarkers").bool() : d.depthAwareMarkers());
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
    }
}

package silkvis.map;

import silkvis.config.ConfigException;
import silkvis.config.Validators;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

import java.util.UUID;

/**
 * User marker (MAP-03). Coordinates are stored as doubles and support negative values
 * and large distances; a marker is never a live entity position.
 */
public record Waypoint(
        String id,
        String label,
        String dimensionId,
        double x,
        double y,
        double z,
        int color,
        String icon,
        boolean visible
) {
    public static final int MAX_LABEL = 64;

    public static Waypoint create(String label, String dimensionId, double x, double y, double z, int color) {
        return new Waypoint(UUID.randomUUID().toString(), label, dimensionId, x, y, z, color, "poi", true);
    }

    public JsonObject encode() {
        return JsonValue.newObject()
                .put("id", id)
                .put("label", label)
                .put("dimensionId", dimensionId)
                .put("x", x)
                .put("y", y)
                .put("z", z)
                .put("color", color)
                .put("icon", icon)
                .put("visible", visible);
    }

    public static Waypoint decode(JsonObject o) throws ConfigException {
        String id = Validators.plainText("marker.id", o.get("id").string(), 64);
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ConfigException.of("marker.id", "not a UUID");
        }
        String label = Validators.plainText("marker.label", o.get("label").string(), MAX_LABEL);
        String dimension = Validators.registryId("marker.dimensionId", o.get("dimensionId").string());
        double x = Validators.finite("marker.x", o.get("x").number().doubleValue(), -30_000_000, 30_000_000);
        double y = Validators.finite("marker.y", o.get("y").number().doubleValue(), -4096, 4096);
        double z = Validators.finite("marker.z", o.get("z").number().doubleValue(), -30_000_000, 30_000_000);
        int color = o.has("color") ? o.get("color").number().intValueExact() : 0xFF59D8C5;
        String icon = o.has("icon") ? Validators.plainText("marker.icon", o.get("icon").string(), 32) : "poi";
        boolean visible = !o.has("visible") || o.get("visible").bool();
        return new Waypoint(id, label, dimension, x, y, z, color, icon, visible);
    }
}

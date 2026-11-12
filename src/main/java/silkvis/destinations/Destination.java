package silkvis.destinations;

import silkvis.config.ConfigException;
import silkvis.config.Validators;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

/**
 * Named slot ("Дом/База/Спавн" or a user slot). Default command text is empty: the
 * hints {@code /home}, {@code /spawn}, {@code /warp base} are examples only (DEST-01).
 */
public record Destination(String id, String label, DestinationTarget target) {
    public static final int MAX_LABEL = 48;

    public JsonObject encode() {
        JsonObject o = JsonValue.newObject()
                .put("id", id)
                .put("label", label);
        if (target != null) {
            o.put("target", target.encode());
        }
        return o;
    }

    public static Destination decode(JsonObject o) throws ConfigException {
        String id = Validators.moduleId("destination.id", o.get("id").string());
        String label = Validators.plainText("destination.label", o.get("label").string(), MAX_LABEL);
        DestinationTarget target = o.has("target") ? DestinationTarget.decode(o.get("target").object()) : null;
        return new Destination(id, label, target);
    }

    public Destination withLabel(String newLabel) {
        return new Destination(id, newLabel, target);
    }

    public Destination withTarget(DestinationTarget newTarget) {
        return new Destination(id, label, newTarget);
    }
}

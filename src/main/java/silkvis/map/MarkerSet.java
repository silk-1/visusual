package silkvis.map;

import silkvis.config.ConfigException;
import silkvis.config.ConfigCodec;
import silkvis.config.json.JsonArray;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/** Independent markers file (MAP-08): cache eviction never touches these entries. */
public record MarkerSet(List<Waypoint> markers) {
    public MarkerSet {
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public static MarkerSet empty() {
        return new MarkerSet(List.of());
    }

    public MarkerSet with(Waypoint waypoint) {
        List<Waypoint> copy = new ArrayList<>(markers);
        copy.removeIf(w -> w.id().equals(waypoint.id()));
        copy.add(waypoint);
        return new MarkerSet(copy);
    }

    public MarkerSet without(String id) {
        List<Waypoint> copy = new ArrayList<>(markers);
        copy.removeIf(w -> w.id().equals(id));
        return new MarkerSet(copy);
    }

    public List<Waypoint> inDimension(String dimensionId) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : markers) {
            if (w.dimensionId().equals(dimensionId)) {
                out.add(w);
            }
        }
        return out;
    }

    public static final class Codec implements ConfigCodec<MarkerSet> {
        public static final int SCHEMA_VERSION = 1;
        private static final int MAX_MARKERS = 10_000;

        @Override
        public int currentSchemaVersion() {
            return SCHEMA_VERSION;
        }

        @Override
        public MarkerSet defaults() {
            return MarkerSet.empty();
        }

        @Override
        public MarkerSet decode(JsonObject root) throws ConfigException {
            if (!root.has("markers")) {
                return MarkerSet.empty();
            }
            JsonArray array = root.get("markers").array();
            if (array.size() > MAX_MARKERS) {
                throw ConfigException.of("markers", "too many entries");
            }
            List<Waypoint> out = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                out.add(Waypoint.decode(array.get(i).object()));
            }
            return new MarkerSet(out);
        }

        @Override
        public JsonObject encode(MarkerSet value) {
            JsonArray array = JsonValue.newArray();
            for (Waypoint w : value.markers()) {
                array.add(w.encode());
            }
            return JsonValue.newObject().put("markers", array);
        }

        @Override
        public void validate(MarkerSet value) throws ConfigException {
            if (value.markers().size() > MAX_MARKERS) {
                throw ConfigException.of("markers", "too many entries");
            }
        }
    }
}

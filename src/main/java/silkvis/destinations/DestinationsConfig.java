package silkvis.destinations;

import silkvis.config.ConfigCodec;
import silkvis.config.ConfigException;
import silkvis.config.Validators;
import silkvis.config.json.JsonArray;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-profile destinations file (DEST-03). Defaults are the three empty user slots;
 * importing a profile never executes a command (DEST-04, I8).
 */
public record DestinationsConfig(String profileId, List<Destination> destinations) {
    public static final int MAX_DESTINATIONS = 32;

    public DestinationsConfig {
        destinations = destinations == null ? List.of() : List.copyOf(destinations);
    }

    public static List<Destination> defaultSlots() {
        return List.of(
                new Destination("home", "Дом", null),
                new Destination("base", "База", null),
                new Destination("spawn", "Спавн", null));
    }

    public DestinationsConfig with(Destination destination) {
        List<Destination> copy = new ArrayList<>(destinations);
        copy.removeIf(d -> d.id().equals(destination.id()));
        copy.add(destination);
        return new DestinationsConfig(profileId, copy);
    }

    public static final class Codec implements ConfigCodec<DestinationsConfig> {
        public static final int SCHEMA_VERSION = 1;
        private final String profileId;

        public Codec(String profileId) {
            this.profileId = profileId;
        }

        @Override
        public int currentSchemaVersion() {
            return SCHEMA_VERSION;
        }

        @Override
        public DestinationsConfig defaults() {
            return new DestinationsConfig(profileId, defaultSlots());
        }

        @Override
        public DestinationsConfig decode(JsonObject root) throws ConfigException {
            String pid = root.has("profileId")
                    ? Validators.plainText("profileId", root.get("profileId").string(), 64)
                    : profileId;
            List<Destination> list = new ArrayList<>();
            if (root.has("destinations")) {
                JsonArray array = root.get("destinations").array();
                if (array.size() > MAX_DESTINATIONS) {
                    throw ConfigException.of("destinations", "too many entries");
                }
                for (int i = 0; i < array.size(); i++) {
                    list.add(Destination.decode(array.get(i).object()));
                }
            }
            return new DestinationsConfig(pid, list.isEmpty() ? defaultSlots() : list);
        }

        @Override
        public JsonObject encode(DestinationsConfig value) {
            JsonArray array = JsonValue.newArray();
            for (Destination d : value.destinations()) {
                array.add(d.encode());
            }
            return JsonValue.newObject()
                    .put("profileId", value.profileId())
                    .put("destinations", array);
        }

        @Override
        public void validate(DestinationsConfig value) throws ConfigException {
            if (value.destinations().size() > MAX_DESTINATIONS) {
                throw ConfigException.of("destinations", "too many entries");
            }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Destination d : value.destinations()) {
                if (!ids.add(d.id())) {
                    throw ConfigException.of("destinations", "duplicate id " + d.id());
                }
                if (d.target() instanceof DestinationTarget.ServerCommand cmd) {
                    if (!CommandTextValidator.isValid(cmd.commandText())) {
                        throw ConfigException.of("destination.commandText", "invalid command");
                    }
                }
            }
        }
    }
}

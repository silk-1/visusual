package silkvis.destinations;

import silkvis.config.ConfigException;
import silkvis.config.Validators;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

/**
 * Tagged union of destination kinds (DEST-02). Coordinates alone never grant a
 * teleport: without an available command they only produce a marker/route.
 */
public sealed interface DestinationTarget permits DestinationTarget.Coordinates, DestinationTarget.ServerCommand {

    String type();

    JsonObject encode();

    static DestinationTarget decode(JsonObject o) throws ConfigException {
        String type = o.has("type") ? o.get("type").string() : "";
        return switch (type) {
            case "COORDINATES" -> Coordinates.decode(o);
            case "SERVER_COMMAND" -> ServerCommand.decode(o);
            default -> throw ConfigException.of("destination.type", "unsupported type");
        };
    }

    /** Solo navigation target; never implies a hidden position change (DEST-08). */
    record Coordinates(String dimensionId, double x, double y, double z, double arrivalRadius, String travelMode)
            implements DestinationTarget {

        public Coordinates {
            if (travelMode == null || travelMode.isBlank()) {
                travelMode = "WALK";
            }
        }

        @Override
        public String type() {
            return "COORDINATES";
        }

        @Override
        public JsonObject encode() {
            return JsonValue.newObject()
                    .put("type", type())
                    .put("dimensionId", dimensionId)
                    .put("x", x)
                    .put("y", y)
                    .put("z", z)
                    .put("arrivalRadius", arrivalRadius)
                    .put("travelMode", travelMode);
        }

        static Coordinates decode(JsonObject o) throws ConfigException {
            String dimension = Validators.registryId("destination.dimensionId", o.get("dimensionId").string());
            double x = Validators.finite("destination.x", o.get("x").number().doubleValue(), -30_000_000, 30_000_000);
            double y = Validators.finite("destination.y", o.get("y").number().doubleValue(), -4096, 4096);
            double z = Validators.finite("destination.z", o.get("z").number().doubleValue(), -30_000_000, 30_000_000);
            double radius = o.has("arrivalRadius")
                    ? Validators.finite("destination.arrivalRadius", o.get("arrivalRadius").number().doubleValue(), 0.5, 256)
                    : 2.0;
            String mode = o.has("travelMode") ? o.get("travelMode").string() : "WALK";
            if (!mode.equals("WALK")) {
                throw ConfigException.of("destination.travelMode", "only WALK is supported in P1");
            }
            return new Coordinates(dimension, x, y, z, radius, mode);
        }
    }

    /** User-authored server command; executed only by an explicit manual action (DEST-04). */
    record ServerCommand(
            String commandText,
            int warmupSeconds,
            int cooldownSeconds,
            int timeoutSeconds,
            boolean stationaryRequired,
            String confirmationPolicy,
            Coordinates expectedPosition
    ) implements DestinationTarget {

        public static final int COOLDOWN_HARD_CAP_SECONDS = 3600;

        public ServerCommand {
            if (confirmationPolicy == null || confirmationPolicy.isBlank()) {
                confirmationPolicy = expectedPosition == null ? "USER_CONFIRM" : "OBSERVED_POSITION";
            }
        }

        @Override
        public String type() {
            return "SERVER_COMMAND";
        }

        @Override
        public JsonObject encode() {
            JsonObject o = JsonValue.newObject()
                    .put("type", type())
                    .put("commandText", commandText)
                    .put("warmupSeconds", warmupSeconds)
                    .put("cooldownSeconds", cooldownSeconds)
                    .put("timeoutSeconds", timeoutSeconds)
                    .put("stationaryRequired", stationaryRequired)
                    .put("confirmationPolicy", confirmationPolicy);
            if (expectedPosition != null) {
                o.put("expectedPosition", expectedPosition.encode());
            }
            return o;
        }

        static ServerCommand decode(JsonObject o) throws ConfigException {
            String command = CommandTextValidator.normalize(o.get("commandText").string());
            int warmup = o.has("warmupSeconds") ? o.get("warmupSeconds").number().intValueExact() : 5;
            int cooldown = o.has("cooldownSeconds") ? o.get("cooldownSeconds").number().intValueExact() : 30;
            int timeout = o.has("timeoutSeconds") ? o.get("timeoutSeconds").number().intValueExact() : 15;
            Validators.intRange("destination.warmupSeconds", warmup, 0, 60);
            Validators.intRange("destination.cooldownSeconds", cooldown, 0, COOLDOWN_HARD_CAP_SECONDS);
            Validators.intRange("destination.timeoutSeconds", timeout, 1, 120);
            boolean stationary = !o.has("stationaryRequired") || o.get("stationaryRequired").bool();
            String policy = o.has("confirmationPolicy") ? o.get("confirmationPolicy").string() : "";
            if (!policy.isEmpty() && !policy.equals("OBSERVED_POSITION") && !policy.equals("USER_CONFIRM")
                    && !policy.equals("CHAT_SIGNAL")) {
                throw ConfigException.of("destination.confirmationPolicy", "unknown policy");
            }
            Coordinates expected = null;
            if (o.has("expectedPosition")) {
                expected = Coordinates.decode(o.get("expectedPosition").object());
            }
            return new ServerCommand(command, warmup, cooldown, timeout, stationary, policy, expected);
        }
    }
}

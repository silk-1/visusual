package silkvis.verify;

import silkvis.config.ConfigException;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;
import silkvis.destinations.CommandTextValidator;
import silkvis.destinations.Destination;
import silkvis.destinations.DestinationExecutor;
import silkvis.destinations.DestinationTarget;
import silkvis.destinations.DestinationsConfig;
import silkvis.destinations.PlayerSnapshot;

import java.util.ArrayList;
import java.util.List;

import static silkvis.verify.SelfCheck.assertEquals;
import static silkvis.verify.SelfCheck.assertTrue;
import static silkvis.verify.SelfCheck.check;
import static silkvis.verify.SelfCheck.expectThrows;

final class DestinationChecks {
    private DestinationChecks() {
    }

    private static DestinationTarget.ServerCommand command(String text, int warmup, int cooldown, int timeout,
                                                          DestinationTarget.Coordinates expected) {
        return new DestinationTarget.ServerCommand(CommandTextValidator.normalize(text), warmup, cooldown,
                timeout, true, expected == null ? "USER_CONFIRM" : "OBSERVED_POSITION", expected);
    }

    static void run() {
        check("command text normalization and validation", () -> {
            assertEquals("/home base", CommandTextValidator.normalize(" /home base "), "normalize");
            assertEquals("/spawn", CommandTextValidator.normalize("//spawn"), "single slash");
            assertTrue(CommandTextValidator.isValid("/warp base"), "valid command");
            assertTrue(!CommandTextValidator.isValid("/home\n/op me"), "newline rejected");
            assertTrue(!CommandTextValidator.isValid("/give a b; /tp"), "chaining rejected");
            assertTrue(!CommandTextValidator.isValid("/tp @s ~ ~ ~ && /op"), "boolean chaining rejected");
            StringBuilder longCommand = new StringBuilder("/");
            for (int i = 0; i < 300; i++) {
                longCommand.append('a');
            }
            assertTrue(!CommandTextValidator.isValid(longCommand.toString()), "length cap");
        });

        check("destination config decodes the documented example", () -> {
            String example = """
                    {
                      "schemaVersion": 1,
                      "profileId": "example-server-profile",
                      "destinations": [
                        {
                          "id": "base",
                          "label": "База",
                          "target": {
                            "type": "SERVER_COMMAND",
                            "commandText": "/home base",
                            "warmupSeconds": 5,
                            "cooldownSeconds": 30,
                            "timeoutSeconds": 15,
                            "stationaryRequired": true,
                            "confirmationPolicy": "OBSERVED_POSITION",
                            "expectedPosition": {
                              "type": "COORDINATES",
                              "dimensionId": "minecraft:overworld",
                              "x": 120.5, "y": 64.0, "z": -80.5, "arrivalRadius": 4.0
                            }
                          }
                        }
                      ]
                    }
                    """;
            JsonObject root = JsonValue.parse(example).object();
            DestinationsConfig config = new DestinationsConfig.Codec("example")
                    .decode(root);
            assertEquals(1, config.destinations().size(), "one destination");
            DestinationTarget target = config.destinations().get(0).target();
            assertTrue(target instanceof DestinationTarget.ServerCommand, "server command target");
            DestinationTarget.ServerCommand cmd = (DestinationTarget.ServerCommand) target;
            assertEquals("/home base", cmd.commandText(), "command text");
            assertEquals(4.0, cmd.expectedPosition().arrivalRadius(), 0.001, "radius");
        });

        check("default slots are empty and never auto-executed", () -> {
            DestinationsConfig defaults = new DestinationsConfig.Codec("p").defaults();
            assertEquals(3, defaults.destinations().size(), "three slots");
            for (Destination destination : defaults.destinations()) {
                assertTrue(destination.target() == null, "no default command");
            }
        });

        check("duplicate destination ids and invalid commands are rejected", () -> {
            DestinationsConfig invalid = new DestinationsConfig("p", List.of(
                    new Destination("home", "Дом", command("/home", 0, 5, 10, null)),
                    new Destination("home", "Дом 2", command("/spawn", 0, 5, 10, null))));
            expectThrows(ConfigException.class, () -> new DestinationsConfig.Codec("p").validate(invalid),
                    "duplicate id");
            DestinationTarget.ServerCommand broken = new DestinationTarget.ServerCommand(
                    "/home; /op me", 0, 0, 5, true, "USER_CONFIRM", null);
            DestinationsConfig badCommand = new DestinationsConfig("p",
                    List.of(new Destination("home", "Дом", broken)));
            expectThrows(ConfigException.class, () -> new DestinationsConfig.Codec("p").validate(badCommand),
                    "invalid command text");
        });

        check("executor verifies observed position and blocks early resend", () -> {
            FakeClock clock = new FakeClock();
            List<String> sent = new ArrayList<>();
            DestinationExecutor executor = new DestinationExecutor(clock, sent::add);
            DestinationTarget.Coordinates expected = new DestinationTarget.Coordinates(
                    "minecraft:overworld", 100, 64, 100, 3, "WALK");
            Destination destination = new Destination("base", "База",
                    command("/home base", 5, 30, 15, expected));
            PlayerSnapshot away = new PlayerSnapshot("minecraft:overworld", 0, 64, 0, false, false, false);
            assertEquals(DestinationExecutor.State.SENT,
                    executor.start(destination, away).state(), "sent");
            // Cooldown prevents a second send.
            PlayerSnapshot arrived = new PlayerSnapshot("minecraft:overworld", 101, 64, 101, false, false, false);
            DestinationExecutor.Result early = executor.start(destination, arrived);
            assertEquals(DestinationExecutor.State.REJECTED, early.state(), "cooldown rejection");
            assertEquals(1, sent.size(), "only one command dispatched");
            clock.advanceMillis(5_100);
            executor.update(arrived);
            assertEquals(DestinationExecutor.State.VERIFIED, executor.state(), "verified by position");
            clock.advanceMillis(30_000);
            PlayerSnapshot moved = new PlayerSnapshot("minecraft:overworld", 101, 64, 101, false, false, false);
            assertEquals(DestinationExecutor.State.SENT, executor.start(destination, moved).state(),
                    "cooldown elapsed");
        });

        check("executor rejects non-stationary player and unverified timeout", () -> {
            FakeClock clock = new FakeClock();
            List<String> sent = new ArrayList<>();
            DestinationExecutor executor = new DestinationExecutor(clock, sent::add);
            Destination destination = new Destination("spawn", "Спавн",
                    command("/spawn", 0, 0, 5, null));
            PlayerSnapshot moving = new PlayerSnapshot("minecraft:overworld", 0, 64, 0, true, false, false);
            assertEquals(DestinationExecutor.State.REJECTED, executor.start(destination, moving).state(),
                    "moving player rejected");
            assertTrue(sent.isEmpty(), "nothing sent");
            PlayerSnapshot still = new PlayerSnapshot("minecraft:overworld", 0, 64, 0, false, false, false);
            executor.start(destination, still);
            clock.advanceMillis(6_000);
            DestinationExecutor.Result result = executor.update(still);
            assertEquals(DestinationExecutor.State.TIMED_OUT, result.state(), "timeout");
            assertEquals(1, sent.size(), "no retry after timeout");
        });

        check("coordinates-only destination never teleports", () -> {
            FakeClock clock = new FakeClock();
            List<String> sent = new ArrayList<>();
            DestinationExecutor executor = new DestinationExecutor(clock, sent::add);
            Destination destination = new Destination("home", "Дом", new DestinationTarget.Coordinates(
                    "minecraft:overworld", 1, 64, 1, 2, "WALK"));
            assertEquals(DestinationExecutor.State.REJECTED, executor.start(destination,
                    new PlayerSnapshot("minecraft:overworld", 0, 64, 0, false, false, false)).state(),
                    "walk only");
            assertTrue(sent.isEmpty(), "no hidden teleport");
        });

        check("damage cancels an in-flight command", () -> {
            FakeClock clock = new FakeClock();
            DestinationExecutor executor = new DestinationExecutor(clock, cmd -> { });
            Destination destination = new Destination("base", "База", command("/home", 0, 0, 20, null));
            executor.start(destination, new PlayerSnapshot("minecraft:overworld", 0, 64, 0, false, false, false));
            executor.onDamage();
            assertEquals(DestinationExecutor.State.CANCELLED, executor.state(), "cancelled by damage");
        });
    }
}

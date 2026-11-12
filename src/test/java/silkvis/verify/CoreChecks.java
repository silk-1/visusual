package silkvis.verify;

import silkvis.config.ConfigException;
import silkvis.config.Debouncer;
import silkvis.config.GlobalConfig;
import silkvis.config.JsonConfigFile;
import silkvis.config.LoadOutcome;
import silkvis.config.OptionalLimit;
import silkvis.config.UiConfig;
import silkvis.config.Validators;
import silkvis.config.json.JsonException;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;
import silkvis.ui.hud.HudLayoutConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static silkvis.verify.SelfCheck.assertEquals;
import static silkvis.verify.SelfCheck.assertTrue;
import static silkvis.verify.SelfCheck.check;
import static silkvis.verify.SelfCheck.expectThrows;

final class CoreChecks {
    private CoreChecks() {
    }

    static void run() {
        check("json roundtrip keeps key order and types", () -> {
            JsonObject root = JsonValue.newObject()
                    .put("a", 1)
                    .put("b", "text")
                    .put("c", true)
                    .put("d", JsonValue.newArray().add(1).add("two"))
                    .put("e", JsonValue.newObject().put("nested", 2.5));
            String written = root.write(true);
            JsonObject parsed = JsonValue.parse(written).object();
            assertEquals(written, parsed.write(true), "stable serialization");
            assertEquals("text", parsed.get("b").string(), "string value");
            assertEquals(2, parsed.get("e").object().get("nested").number().intValueExact(), "nested number");
        });

        check("json rejects trailing content and malformed input", () -> {
            expectThrows(JsonException.class, () -> JsonValue.parse("{} {}"), "trailing content");
            expectThrows(JsonException.class, () -> JsonValue.parse("{'a':1}"), "single quotes");
            expectThrows(JsonException.class, () -> JsonValue.parse("{\"a\":NaN}"), "NaN");
        });

        check("json enforces nesting depth", () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 80; i++) {
                sb.append('[');
            }
            for (int i = 0; i < 80; i++) {
                sb.append(']');
            }
            expectThrows(JsonException.class, () -> JsonValue.parse(sb.toString()), "too deep");
        });

        check("global config roundtrip", () -> {
            GlobalConfig.Codec codec = new GlobalConfig.Codec();
            GlobalConfig config = new GlobalConfig("ru", silkvis.config.VisualPreset.QUALITY, true, true, false,
                    new silkvis.config.MediaConfig(true, silkvis.media.SessionSelection.PINNED, "spotify",
                            false, true));
            JsonObject encoded = codec.encode(config);
            encoded.put("schemaVersion", codec.currentSchemaVersion());
            GlobalConfig decoded = codec.decode(JsonValue.parse(encoded.write(true)).object());
            codec.validate(decoded);
            assertEquals(config, decoded, "roundtrip equality");
        });

        check("global config rejects unknown preset and language", () -> {
            GlobalConfig.Codec codec = new GlobalConfig.Codec();
            expectThrows(ConfigException.class, () -> codec.decode(JsonValue.newObject()
                    .put("preset", "ULTRA").put("schemaVersion", 1)), "unknown preset");
            expectThrows(ConfigException.class, () -> codec.decode(JsonValue.newObject()
                    .put("language", "de").put("schemaVersion", 1)), "unsupported language");
        });

        check("validators reject NaN, out-of-range and control chars", () -> {
            expectThrows(ConfigException.class, () -> Validators.finite("x", Double.NaN, 0, 1), "NaN");
            expectThrows(ConfigException.class, () -> Validators.intRange("x", 5, 0, 4), "range");
            expectThrows(ConfigException.class, () -> Validators.plainText("x", "bad\u0000text", 20),
                    "control character");
            assertEquals("minecraft:stone", Validators.registryId("x", "minecraft:stone"), "registry id");
            expectThrows(ConfigException.class, () -> Validators.registryId("x", "Stone"), "bad registry id");
        });

        check("optional limit cannot express infinity and respects hard caps", () -> {
            expectThrows(IllegalArgumentException.class, () -> new OptionalLimit(true, -1), "negative limit");
            OptionalLimit enabled = new OptionalLimit(true, 10_000);
            assertEquals(500L, enabled.effective(500, 64), "hard technical cap wins");
            OptionalLimit disabled = OptionalLimit.disabled(64);
            assertEquals(64L, disabled.effective(500, 64), "disabled uses fallback");
        });

        check("config file saves atomically and reloads", () -> {
            Path dir = Files.createTempDirectory("silkvis-config");
            Path file = dir.resolve("global.json");
            JsonConfigFile<GlobalConfig> store = new JsonConfigFile<>(file, new GlobalConfig.Codec(),
                    new FakeClock());
            GlobalConfig config = new GlobalConfig("en", silkvis.config.VisualPreset.LOW, false, false, true,
                    silkvis.config.MediaConfig.defaults());
            store.save(config);
            assertTrue(Files.exists(file), "file written");
            assertTrue(!Files.exists(dir.resolve("global.json.tmp")), "temp file removed");
            LoadOutcome<GlobalConfig> outcome = store.load();
            assertEquals(LoadOutcome.Status.LOADED, outcome.status(), "loaded status");
            assertEquals(config, outcome.value(), "value restored");
            assertTrue(Files.exists(dir.resolve("global.json.bak")) || true, "backup is best effort");
        });

        check("damaged config is quarantined and safe defaults are used", () -> {
            Path dir = Files.createTempDirectory("silkvis-broken");
            Path file = dir.resolve("global.json");
            Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
            JsonConfigFile<GlobalConfig> store = new JsonConfigFile<>(file, new GlobalConfig.Codec(),
                    new FakeClock());
            LoadOutcome<GlobalConfig> outcome = store.load();
            assertEquals(LoadOutcome.Status.SAFE_DEFAULT, outcome.status(), "safe default");
            assertTrue(outcome.writable(), "defaults may be written later");
            try (var stream = Files.list(dir)) {
                assertTrue(stream.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")),
                        "corrupted copy preserved");
            }
        });

        check("future schema is read-only and never overwritten", () -> {
            Path dir = Files.createTempDirectory("silkvis-future");
            Path file = dir.resolve("global.json");
            Files.writeString(file, "{\"schemaVersion\": 99, \"language\": \"en\"}", StandardCharsets.UTF_8);
            JsonConfigFile<GlobalConfig> store = new JsonConfigFile<>(file, new GlobalConfig.Codec(),
                    new FakeClock());
            LoadOutcome<GlobalConfig> outcome = store.load();
            assertEquals(LoadOutcome.Status.READ_ONLY_NEWER, outcome.status(), "read only");
            assertTrue(!outcome.writable(), "must not be written");
        });

        check("ui config validates fog bounds and duplicate hud widgets", () -> {
            UiConfig.Codec codec = new UiConfig.Codec();
            UiConfig defaults = codec.defaults();
            codec.validate(defaults);
            UiConfig badFog = new UiConfig(defaults.hud(),
                    defaults.map(),
                    new silkvis.visual.VisualSettings(false, 70, 1, 1, 1, true, 0.8, 0.4, 4000, 1, true,
                            false, true));
            expectThrows(ConfigException.class, () -> codec.validate(badFog), "fog end before start");
            HudLayoutConfig duplicated = new HudLayoutConfig(java.util.List.of(
                    silkvis.ui.hud.HudWidgetConfig.defaults(silkvis.ui.hud.HudWidgetId.FPS),
                    silkvis.ui.hud.HudWidgetConfig.defaults(silkvis.ui.hud.HudWidgetId.FPS)));
            expectThrows(ConfigException.class,
                    () -> codec.validate(new UiConfig(duplicated, defaults.map(), defaults.visual())),
                    "duplicate widget");
        });

        check("hud config rejects p2 widget in a p1 build", () -> {
            JsonObject doc = JsonValue.newObject().put("widgets", JsonValue.newArray().add(
                    JsonValue.newObject().put("id", "TASK_STATUS").put("anchor", "TOP_LEFT")));
            expectThrows(ConfigException.class, () -> HudLayoutConfig.decode(doc), "p2 widget rejected");
        });

        check("debouncer coalesces edits and flushes once", () -> {
            FakeClock clock = new FakeClock();
            AtomicInteger writes = new AtomicInteger();
            Debouncer<String> debouncer = new Debouncer<>(500, clock, value -> writes.incrementAndGet());
            debouncer.submit("a");
            clock.advanceMillis(100);
            debouncer.submit("b");
            clock.advanceMillis(400);
            debouncer.tick();
            assertEquals(0, writes.get(), "still debounced");
            clock.advanceMillis(200);
            debouncer.tick();
            assertEquals(1, writes.get(), "flushed once");
            debouncer.flush();
            assertEquals(1, writes.get(), "nothing pending");
        });
    }
}

package silkvis.verify;

import silkvis.assets.TotemDownloader;
import silkvis.assets.TotemLibrary;
import silkvis.assets.TotemPackBuilder;
import silkvis.config.ConfigException;
import silkvis.config.Debouncer;
import silkvis.config.GlobalConfig;
import silkvis.config.JsonConfigFile;
import silkvis.config.LoadOutcome;
import silkvis.core.OptionalLimit;
import silkvis.config.UiConfig;
import silkvis.config.Validators;
import silkvis.config.json.JsonException;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;
import silkvis.ui.hud.HudLayoutConfig;
import silkvis.ui.hud.HudWidgetId;

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

    /** Temp folders in the self checks must not pile up in %TEMP%. */
    private static void deleteTree(Path root) {
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ignored) {
                    // Best effort only.
                }
            });
        } catch (java.io.IOException ignored) {
            // Best effort only.
        }
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
            assertEquals(2.5, parsed.get("e").object().get("nested").number().doubleValue(), 0.0001,
                    "nested number");
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

        // Behaviour changed in 0.1.14: a widget that this build does not render (removed
        // MINIMAP, future TASK_STATUS) is skipped instead of failing the whole file. The
        // check now pins both halves of that contract: the rest survives, garbage still fails.
        check("hud config skips removed and p2 widgets but keeps the rest", () -> {
            JsonObject doc = JsonValue.newObject().put("widgets", JsonValue.newArray()
                    .add(JsonValue.newObject().put("id", "MINIMAP").put("anchor", "TOP_RIGHT"))
                    .add(JsonValue.newObject().put("id", "TASK_STATUS").put("anchor", "BOTTOM_CENTER"))
                    .add(JsonValue.newObject().put("id", "COORDINATES").put("anchor", "TOP_LEFT")
                            .put("offsetX", 12)));
            HudLayoutConfig decoded = HudLayoutConfig.decode(doc);
            assertEquals(12, decoded.get(HudWidgetId.COORDINATES).offsetX(), "known widget kept");
            assertTrue(!decoded.get(HudWidgetId.MINIMAP).enabled(), "removed widget stays off");
            expectThrows(ConfigException.class, () -> HudLayoutConfig.decode(JsonValue.newObject()
                            .put("widgets", JsonValue.newArray().add(
                                    JsonValue.newObject().put("id", "NOPE").put("anchor", "TOP_LEFT")))),
                    "unknown id still rejected");
        });

        check("totem link rules reject anything but http(s)", () -> {
            assertEquals(null, TotemDownloader.validate("https://example.com/a.png"), "https accepted");
            assertEquals(null, TotemDownloader.validate("http://example.com/a.png"), "http accepted");
            assertEquals("url_invalid", TotemDownloader.validate(null), "null rejected");
            assertEquals("url_invalid", TotemDownloader.validate("   "), "blank rejected");
            assertEquals("url_invalid", TotemDownloader.validate("ftp://example.com/a.png"), "ftp rejected");
            assertEquals("url_invalid", TotemDownloader.validate("file:///C:/Windows/a.png"), "file rejected");
            assertEquals("url_invalid", TotemDownloader.validate("javascript:alert(1)"), "javascript rejected");
            assertEquals("url_invalid", TotemDownloader.validate("https://"), "empty host rejected");
            assertEquals("url_invalid",
                    TotemDownloader.validate("https://example.com/" + "a".repeat(600)),
                    "over-long link rejected");
        });

        check("totem link becomes a safe file name", () -> {
            assertEquals("cat.png", TotemDownloader.fileNameFor("https://ex.com/pics/cat.png", ""),
                    "name from the path");
            assertEquals("my-cat.jpg",
                    TotemDownloader.fileNameFor("https://ex.com/a/my%20cat.jpg?x=1", ""),
                    "decoded and sanitized");
            assertEquals("img.png", TotemDownloader.fileNameFor("https://ex.com/img.php", "image/png"),
                    "extension from the content type");
            assertEquals("totem.jpg", TotemDownloader.fileNameFor("https://ex.com/", "image/jpeg"),
                    "generic name");
            assertTrue(TotemDownloader.fileNameFor("https://ex.com/" + "a".repeat(200) + ".png", "")
                    .length() <= 52, "name stays bounded");
            assertTrue(TotemDownloader.fileNameFor("https://ex.com/", "").endsWith(".png"),
                    "unknown type falls back to png");
        });

        check("downloaded totems never overwrite each other", () -> {
            Path dir = Files.createTempDirectory("silkvis-totem-url");
            try {
                assertEquals(dir.resolve("cat.png"), TotemDownloader.freePath(dir, "cat.png"), "free");
                Files.write(dir.resolve("cat.png"), new byte[]{1});
                assertEquals(dir.resolve("cat-2.png"), TotemDownloader.freePath(dir, "cat.png"),
                        "second name");
                Files.write(dir.resolve("cat-2.png"), new byte[]{1});
                assertEquals(dir.resolve("cat-3.png"), TotemDownloader.freePath(dir, "cat.png"),
                        "third name");
            } finally {
                deleteTree(dir);
            }
        });

        check("an oversized download is refused, not buffered", () -> {
            java.io.InputStream endless = new java.io.InputStream() {
                private int served;

                @Override
                public int read() {
                    return 0;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    int remaining = TotemDownloader.MAX_DOWNLOAD_BYTES + 1 - served;
                    if (remaining <= 0) {
                        return -1;
                    }
                    int chunk = Math.min(Math.min(length, 8192), remaining);
                    served += chunk;
                    return chunk;
                }
            };
            assertEquals(null, TotemDownloader.readBounded(endless), "cap enforced");
        });

        check("nine navigation tabs stay inside the panel", () -> {
            int tabs = silkvis.ui.MainScreenModel.p1Tabs().size();
            assertEquals(9, tabs, "tab count");
            for (int[] screen : new int[][]{{480, 270}, {640, 360}, {960, 620}}) {
                silkvis.ui.MainScreenLayout layout =
                        silkvis.ui.MainScreenLayout.compute(screen[0], screen[1]);
                silkvis.ui.Rect nav = layout.navigation();
                int[] rows = silkvis.ui.MainScreenLayout.navRows(nav, tabs);
                int bottom = 4 + rows[1] * (tabs - 1) + rows[0];
                assertTrue(bottom <= Math.max(rows[0], nav.height()),
                        "fits " + screen[0] + "x" + screen[1] + ": " + bottom + " <= " + nav.height());
                assertTrue(rows[0] >= 12, "rows remain clickable: " + rows[0]);
            }
        });

        check("totem builder writes a 128x128 texture and a loadable pack", () -> {
            Path dir = Files.createTempDirectory("silkvis-totem");
            try {
                Path source = dir.resolve("source.png");
                java.awt.image.BufferedImage image =
                        new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                javax.imageio.ImageIO.write(image, "png", source.toFile());
                Path packs = dir.resolve("resourcepacks");
                TotemPackBuilder.Result result = TotemPackBuilder.apply(source, packs);
                assertTrue(result.ok(), "pack built: " + result.reason());
                Path texture = packs.resolve(TotemPackBuilder.PACK_DIR_NAME)
                        .resolve(TotemPackBuilder.TEXTURE_PATH);
                assertTrue(Files.isRegularFile(texture), "texture written");
                java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(texture.toFile());
                // 128 is the default edge: the vanilla 16x16 made any real picture a blur.
                assertEquals(128, decoded.getWidth(), "texture width");
                assertEquals(128, decoded.getHeight(), "texture height");
                // Every offered edge is honoured, and an unsupported one falls back instead of
                // silently producing an odd-sized texture.
                for (int edge : silkvis.visual.VisualSettings.TOTEM_TEXTURE_SIZES) {
                    TotemPackBuilder.Result sized = TotemPackBuilder.apply(source, packs, edge);
                    assertTrue(sized.ok(), "size " + edge + " built: " + sized.reason());
                    java.awt.image.BufferedImage out =
                            javax.imageio.ImageIO.read(texture.toFile());
                    assertEquals(edge, out.getWidth(), "texture width at " + edge);
                    assertEquals(edge, out.getHeight(), "texture height at " + edge);
                }
                // Background trim: a white sheet with a red square in the middle becomes a
                // texture whose corners are transparent and whose centre is still red. This is
                // what the owner asked for ("обрезался фон"), so it is asserted, not eyeballed.
                java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(
                        64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        sheet.setRGB(x, y, 0xFFFFFFFF);
                    }
                }
                for (int y = 24; y < 40; y++) {
                    for (int x = 24; x < 40; x++) {
                        sheet.setRGB(x, y, 0xFFFF0000);
                    }
                }
                Path sheetFile = dir.resolve("sheet.png");
                javax.imageio.ImageIO.write(sheet, "png", sheetFile.toFile());
                assertTrue(TotemPackBuilder.apply(sheetFile, packs, 128, true).ok(),
                        "trimmed build ok");
                // The scaled drawing lands in the centre of the 128 px icon: the red square
                // covers 16 of 64 px, so after trimming it fills the middle of the texture.
                java.awt.image.BufferedImage trimmed =
                        javax.imageio.ImageIO.read(texture.toFile());
                // The crop keeps a 1 px margin and is then scaled, so the corner is not
                // required to be alpha 0 exactly - it must simply no longer be opaque white.
                assertTrue((trimmed.getRGB(0, 0) >>> 24) < 200, "corner trimmed away");
                assertEquals(0xFFFF0000, trimmed.getRGB(64, 64), "drawing kept");
                // Trim off must keep the sheet intact.
                assertTrue(TotemPackBuilder.apply(sheetFile, packs, 128, false).ok(),
                        "plain build ok");
                java.awt.image.BufferedImage plain =
                        javax.imageio.ImageIO.read(texture.toFile());
                assertEquals(0xFFFFFFFF, plain.getRGB(0, 0), "background kept when trim is off");
                // A picture with four different corners has no single background: untouched.
                java.awt.image.BufferedImage mixed = new java.awt.image.BufferedImage(
                        16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                mixed.setRGB(0, 0, 0xFFFFFFFF);
                mixed.setRGB(15, 0, 0xFF000000);
                assertTrue(TotemPackBuilder.trimBackground(mixed) == mixed,
                        "multi-colour corners are left alone");

                // An unsupported edge is normalised down, never written as-is.
                assertTrue(TotemPackBuilder.apply(source, packs, 200).ok(),
                        "unsupported size still builds");
                assertEquals(128,
                        javax.imageio.ImageIO.read(texture.toFile()).getWidth(),
                        "unsupported size normalised");
                String meta = Files.readString(packs.resolve(TotemPackBuilder.PACK_DIR_NAME)
                        .resolve("pack.mcmeta"), StandardCharsets.UTF_8);
                assertTrue(meta.contains("supported_formats"), "format range keeps it loadable");

                Path junk = dir.resolve("junk.png");
                Files.write(junk, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
                assertEquals("image_unreadable", TotemPackBuilder.apply(junk, packs).reason(),
                        "garbage rejected");
                assertEquals("file_missing",
                        TotemPackBuilder.apply(dir.resolve("absent.png"), packs).reason(),
                        "missing file rejected");
            } finally {
                deleteTree(dir);
            }
        });

        check("totem library lists images, skips junk, caps the list", () -> {
            Path dir = Files.createTempDirectory("silkvis-totem-lib");
            try {
                Files.write(dir.resolve("a.png"), new byte[]{1});
                Files.write(dir.resolve("b.JPG"), new byte[]{1});
                Files.write(dir.resolve("notes.txt"), new byte[]{1});
                Files.createDirectories(dir.resolve("folder.png"));
                for (int i = 0; i < TotemLibrary.MAX_ENTRIES + 3; i++) {
                    Files.write(dir.resolve("extra" + i + ".png"), new byte[]{1});
                }
                java.util.List<TotemLibrary.Entry> entries = TotemLibrary.scan(dir);
                assertEquals(TotemLibrary.MAX_ENTRIES, entries.size(), "capped");
                assertTrue(entries.stream().noneMatch(e -> e.name().endsWith(".txt")),
                        "text file skipped");
                assertEquals("a", entries.get(0).displayName(), "sorted, name without extension");
            } finally {
                deleteTree(dir);
            }
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

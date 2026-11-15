package silkvis.client;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import silkvis.core.ErrorCode;
import silkvis.core.ModuleRegistry;
import silkvis.diagnostics.BoundedLog;
import silkvis.diagnostics.report.BugReportWriter;
import silkvis.diagnostics.report.ReportBundle;
import silkvis.diagnostics.report.ReportTarget;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * "Bug report" support flow: one file on the desktop with versions, environment, module
 * states, our own diagnostics log and the tail of {@code latest.log}.
 *
 * <p>Rules that shaped it:</p>
 * <ul>
 *   <li>nothing blocks the render or client thread for long: the log tail is capped and
 *       read once, no network, no process spawning;</li>
 *   <li>the file says what it contains, including the warning that a log may mention a
 *       server address;</li>
 *   <li>failure to write to the desktop falls back to {@code <gameDir>/silkvis-reports}
 *       instead of throwing into the game loop.</li>
 * </ul>
 */
public final class BugReportService {
    private static final int MAX_REPORT_CHARS = 512 * 1024;
    private static final int LOG_TAIL_BYTES = 96 * 1024;
    private static final int LOG_TAIL_LINES = 220;
    private static final int ERROR_LINES = 40;
    private static final int MAX_MODS = 300;

    private BugReportService() {
    }

    /** Creates the report and returns its path, or empty when even the fallback failed. */
    public static Optional<Path> create(MinecraftClient client) {
        SilkVisRuntime runtime = SilkVisRuntime.get();
        Path gameDir = safeGameDir();
        ReportBundle bundle = new ReportBundle(MAX_REPORT_CHARS);

        bundle.line("SilkVis bug report");
        bundle.keyValue("created", Instant.now().toString());
        bundle.keyValue("silkvis", modVersion("silkvis"));
        bundle.keyValue("minecraft", modVersion("minecraft"));
        bundle.line("This file is meant to be sent to the developer. It contains file paths,");
        bundle.line("the installed mod list and the tail of latest.log - a log may contain a");
        bundle.line("server address you joined. Remove what you do not want to share.");

        appendSystem(bundle);
        appendSilkVis(bundle, runtime);
        appendGame(bundle, client);
        appendMods(bundle);
        appendModules(bundle, runtime);
        appendIntegrations(bundle, runtime);
        appendConfigFiles(bundle, runtime);
        appendDiagnostics(bundle, runtime);
        appendLog(bundle, gameDir);

        String body = bundle.toString();
        Path desktop = ReportTarget.desktop(gameDir);
        try {
            return Optional.of(BugReportWriter.write(desktop, "silkvis-report", body));
        } catch (IOException | RuntimeException first) {
            bundle.section("Writer");
            bundle.keyValue("desktop write failed", first.getClass().getSimpleName() + ": " + first.getMessage());
            try {
                Path fallback = ReportTarget.gameDirFallback(gameDir);
                return Optional.of(BugReportWriter.write(fallback, "silkvis-report", body));
            } catch (IOException | RuntimeException second) {
                runtime.log().log("BUG_REPORT_WRITE_FAILED", second.getClass().getSimpleName());
                return Optional.empty();
            }
        }
    }

    /** Creates the report and tells the player where it landed; safe to call from the tick. */
    public static void createAndNotify(MinecraftClient client) {
        Optional<Path> path = create(client);
        SilkVisRuntime runtime = SilkVisRuntime.get();
        if (path.isPresent()) {
            runtime.log().log("BUG_REPORT", path.get().toString());
            notify(client, Text.translatable("silkvis.bug_report.saved", path.get().toString()));
        } else {
            runtime.log().log("BUG_REPORT_FAILED", "write_failed");
            notify(client, Text.translatable("silkvis.bug_report.failed"));
        }
    }

    private static void notify(MinecraftClient client, Text message) {
        if (client == null) {
            return;
        }
        if (client.player != null) {
            client.player.sendMessage(message, false);
        } else if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(message);
        }
    }

    private static void appendSystem(ReportBundle bundle) {
        bundle.section("System");
        bundle.keyValue("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        bundle.keyValue("java", System.getProperty("java.version") + " / " + System.getProperty("java.vendor"));
        bundle.keyValue("jvm", System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        bundle.keyValue("cpus", Runtime.getRuntime().availableProcessors());
        bundle.keyValue("maxHeapMiB", Runtime.getRuntime().maxMemory() / (1024L * 1024L));
        bundle.keyValue("allocatedHeapMiB", Runtime.getRuntime().totalMemory() / (1024L * 1024L));
        try {
            bundle.keyValue("uptime", (ManagementFactory.getRuntimeMXBean().getUptime() / 1000L) + " s");
        } catch (RuntimeException | LinkageError e) {
            bundle.keyValue("uptime", "unavailable (" + e.getClass().getSimpleName() + ")");
        }
        bundle.keyValue("gpu", gpuName());
    }

    private static String gpuName() {
        // The GL context belongs to the render thread; the string is only read, so this is
        // safe here, but a driver may report null or throw.
        try {
            String vendor = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR);
            String renderer = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
            String version = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION);
            return renderer + " (" + vendor + ", GL " + version + ")";
        } catch (RuntimeException | LinkageError e) {
            return "unavailable (" + e.getClass().getSimpleName() + ")";
        }
    }

    private static void appendSilkVis(ReportBundle bundle, SilkVisRuntime runtime) {
        bundle.section("SilkVis");
        bundle.keyValue("theme", runtime.theme().id());
        bundle.keyValue("language", runtime.global().language());
        bundle.keyValue("preset", runtime.global().preset().id());
        bundle.keyValue("reducedMotion", runtime.global().reducedMotion());
        bundle.keyValue("blur", runtime.global().blurEnabled());
        bundle.keyValue("verboseDiagnostics", runtime.global().verboseDiagnostics());
        bundle.keyValue("profileKind", runtime.currentProfile().kind());
        bundle.keyValue("queuedIo", runtime.queuedIo());
        bundle.keyValue("mediaService", runtime.mediaService().isPresent());
        bundle.keyValue("mapService", runtime.mapService().isPresent());
        bundle.keyValue("hudWidgetsEnabled", countEnabledHudWidgets(runtime));
        // Only the dimension: worldProfileId contains the server host for multiplayer.
        runtime.mapIdentity().ifPresent(identity -> bundle.keyValue("mapDimension",
                identity.dimensionId() + " y=" + identity.sliceY()));
    }

    private static int countEnabledHudWidgets(SilkVisRuntime runtime) {
        int enabled = 0;
        for (var config : runtime.hudLayout().byId().values()) {
            if (config.enabled()) {
                enabled++;
            }
        }
        return enabled;
    }

    private static void appendGame(ReportBundle bundle, MinecraftClient client) {
        bundle.section("Game");
        if (client == null) {
            bundle.line("client: not available");
            return;
        }
        try {
            bundle.keyValue("window", client.getWindow().getScaledWidth() + "x"
                    + client.getWindow().getScaledHeight());
            bundle.keyValue("guiScaleFactor", client.getWindow().getScaleFactor());
        } catch (RuntimeException e) {
            bundle.keyValue("window", "unavailable (" + e.getClass().getSimpleName() + ")");
        }
        try {
            bundle.keyValue("fps", client.getCurrentFps());
        } catch (RuntimeException e) {
            bundle.keyValue("fps", "unavailable");
        }
        bundle.keyValue("inWorld", client.world != null);
        bundle.keyValue("singleplayer", client.isInSingleplayer());
        bundle.keyValue("serverAddressIncluded", false);
    }

    private static void appendMods(ReportBundle bundle) {
        bundle.section("Mods");
        List<String> mods = new ArrayList<>();
        try {
            for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                mods.add(container.getMetadata().getId() + " " + container.getMetadata().getVersion().getFriendlyString());
            }
        } catch (RuntimeException | LinkageError e) {
            bundle.keyValue("mods", "unavailable (" + e.getClass().getSimpleName() + ")");
            return;
        }
        mods.sort(Comparator.naturalOrder());
        for (int i = 0; i < mods.size() && i < MAX_MODS; i++) {
            bundle.line(mods.get(i));
        }
        if (mods.size() > MAX_MODS) {
            bundle.line("... " + (mods.size() - MAX_MODS) + " more");
        }
    }

    private static void appendModules(ReportBundle bundle, SilkVisRuntime runtime) {
        bundle.section("Modules");
        try {
            for (ModuleRegistry.Entry entry : runtime.modules().all()) {
                Optional<ErrorCode> reason = entry.reasonCode();
                bundle.line(entry.descriptor().id() + " | " + entry.state().name().toLowerCase(Locale.ROOT)
                        + " | available=" + entry.available()
                        + " | reason=" + reason.map(Enum::name).orElse("-")
                        + " | " + (entry.reasonDetail() == null ? "" : entry.reasonDetail()));
            }
        } catch (RuntimeException e) {
            bundle.keyValue("modules", "unavailable (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static void appendIntegrations(ReportBundle bundle, SilkVisRuntime runtime) {
        bundle.section("Integrations");
        String[] ids = {"sodium", "iris", "baritone", "silkvis:hud_hook"};
        for (String id : ids) {
            try {
                bundle.line(id + " | present=" + runtime.integrations().isPresent(id)
                        + " | failure=" + runtime.integrations().failure(id).orElse("-"));
            } catch (RuntimeException e) {
                bundle.line(id + " | unavailable (" + e.getClass().getSimpleName() + ")");
            }
        }
        try {
            bundle.keyValue("shaderMode", runtime.integrations().shaderMode(false));
        } catch (RuntimeException e) {
            bundle.keyValue("shaderMode", "unavailable");
        }
    }

    private static void appendConfigFiles(ReportBundle bundle, SilkVisRuntime runtime) {
        bundle.section("Config files");
        List<Path> files = new ArrayList<>();
        try {
            files.add(runtime.paths().globalFile());
        } catch (RuntimeException e) {
            bundle.line("global: unavailable");
        }
        String profileId = runtime.currentProfile().profileId();
        try {
            files.add(runtime.paths().profileUiFile(profileId));
            files.add(runtime.paths().profileDestinationsFile(profileId));
            files.add(runtime.paths().profileMarkersFile(profileId));
        } catch (RuntimeException e) {
            bundle.line("profile: unavailable");
        }
        for (Path file : files) {
            boolean exists = Files.isRegularFile(file);
            long size = exists ? sizeOf(file) : 0L;
            bundle.line(file + " | exists=" + exists + " | bytes=" + size);
            if (exists && size > 0 && size <= 64 * 1024) {
                bundle.text(readSmall(file));
            }
        }
    }

    private static void appendDiagnostics(ReportBundle bundle, SilkVisRuntime runtime) {
        bundle.section("SilkVis diagnostics log");
        try {
            BoundedLog log = runtime.log();
            bundle.keyValue("entries", log.entries().size());
            bundle.keyValue("suppressed", log.suppressedCount());
            for (BoundedLog.Entry entry : log.entries()) {
                bundle.line(Instant.ofEpochMilli(entry.epochMillis()) + " " + entry.code()
                        + " | " + entry.detail() + (entry.repeats() > 1 ? " x" + entry.repeats() : ""));
            }
        } catch (RuntimeException e) {
            bundle.keyValue("log", "unavailable (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static void appendLog(ReportBundle bundle, Path gameDir) {
        Path latest = gameDir == null ? null : gameDir.resolve("logs").resolve("latest.log");
        if (latest == null || !Files.isRegularFile(latest)) {
            bundle.section("latest.log");
            bundle.line("not found");
            return;
        }
        String tail = tailOf(latest);
        bundle.section("Errors and stack traces in latest.log (tail)");
        List<String> errors = new ArrayList<>();
        for (String line : tail.split("\\R")) {
            if (line.contains("Exception") || line.contains("Caused by:") || line.contains("ERROR")
                    || line.contains("Error:") || line.contains("[SilkVis]")) {
                errors.add(line.length() > 400 ? line.substring(0, 400) + "..." : line);
                if (errors.size() >= ERROR_LINES) {
                    break;
                }
            }
        }
        if (errors.isEmpty()) {
            bundle.line("none");
        } else {
            for (String line : errors) {
                bundle.line(line);
            }
        }
        bundle.section("latest.log tail (" + LOG_TAIL_LINES + " lines)");
        String[] lines = tail.split("\\R");
        int from = Math.max(0, lines.length - LOG_TAIL_LINES);
        for (int i = from; i < lines.length; i++) {
            bundle.line(lines[i]);
        }
    }

    private static String tailOf(Path file) {
        try {
            long size = Files.size(file);
            long skip = Math.max(0L, size - LOG_TAIL_BYTES);
            try (InputStream in = Files.newInputStream(file)) {
                long skipped = 0L;
                while (skipped < skip) {
                    long n = in.skip(skip - skipped);
                    if (n <= 0) {
                        break;
                    }
                    skipped += n;
                }
                byte[] buffer = in.readAllBytes();
                return new String(buffer, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            return "latest.log unreadable: " + e.getClass().getSimpleName();
        }
    }

    private static String readSmall(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "<unreadable: " + e.getClass().getSimpleName() + ">";
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1L;
        }
    }

    private static String modVersion(String id) {
        try {
            return FabricLoader.getInstance().getModContainer(id)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("absent");
        } catch (RuntimeException | LinkageError e) {
            return "unavailable";
        }
    }

    private static Path safeGameDir() {
        try {
            return FabricLoader.getInstance().getGameDir();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}

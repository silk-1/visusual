package silkvis.media;

import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;
import silkvis.core.Clock;
import silkvis.core.ErrorCode;
import silkvis.core.SilkError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java side of the Windows media bridge (MEDIA-04..08).
 *
 * <p>One child process, stdout = NDJSON protocol only, stderr = bounded diagnostics.
 * Runs in its own process so a native failure cannot take the JVM down. Startup and IPC
 * never block the render thread; all reads happen on dedicated threads.</p>
 */
public final class WindowsMediaProvider implements MediaProvider {
    public static final int MAX_RESTARTS = 3;
    private static final int STDERR_MAX_LINES = 64;
    private static final long SHUTDOWN_TIMEOUT_MS = 2000L;

    private final MediaHelperBinary binary;
    private final Clock clock;
    private final int protocolVersion;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger restarts = new AtomicInteger();
    private final List<String> stderrTail = new ArrayList<>();
    private final Map<String, MediaSessionRef> sessions = new LinkedHashMap<>();

    private volatile ProviderStatus status = ProviderStatus.DISABLED;
    private volatile String statusReason = "";
    private volatile Process process;
    private volatile Writer stdin;
    private volatile Thread stdoutThread;
    private volatile Thread stderrThread;
    private volatile MediaProviderListener listener;
    private ExecutorService dispatcher;

    public WindowsMediaProvider(MediaHelperBinary binary, Clock clock) {
        this.binary = binary;
        this.clock = clock;
        this.protocolVersion = IpcProtocol.PROTOCOL_VERSION;
    }

    @Override
    public String id() {
        return "windows-gsmtc";
    }

    @Override
    public ProviderStatus status() {
        return status;
    }

    @Override
    public void start(MediaProviderListener newListener) {
        this.listener = newListener;
        String problem = binary.verify();
        if (problem != null) {
            status = problem.equals("unsupported_platform") ? ProviderStatus.UNSUPPORTED : ProviderStatus.ERROR;
            statusReason = problem;
            emitStatus();
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        dispatcher = Executors.newSingleThreadExecutor(named("silkvis-media-ipc"));
        status = ProviderStatus.STARTING;
        emitStatus();
        dispatcher.execute(this::spawnAndHandshake);
    }

    private void spawnAndHandshake() {
        try {
            ProcessBuilder builder = new ProcessBuilder(binary.binaryPath().toString(), "--protocol", "1");
            builder.redirectErrorStream(false);
            Path dir = binary.binaryPath().getParent();
            if (dir != null) {
                builder.directory(dir.toFile());
            }
            Process proc = builder.start();
            process = proc;
            stdin = new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8);
            startStderrPump(proc);

            IpcEnvelope hello = IpcEnvelope.of(IpcProtocol.HELLO, JsonValue.newObject()
                    .put("protocolVersion", protocolVersion)
                    .put("client", "silkvis")
                    .put("mode", "interactive"));
            send(hello);
            startStdoutPump(proc);
        } catch (IOException e) {
            fail("spawn_failed");
        }
    }

    private void startStdoutPump(Process proc) {
        stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleLine(line);
                }
            } catch (IOException e) {
                // Stream closed: handled by onProcessExit.
            }
            onProcessExit(proc);
        }, "silkvis-media-stdout");
        stdoutThread.setDaemon(true);
        stdoutThread.start();
    }

    private void startStderrPump(Process proc) {
        stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (stderrTail) {
                        stderrTail.add(line.length() > 300 ? line.substring(0, 300) : line);
                        while (stderrTail.size() > STDERR_MAX_LINES) {
                            stderrTail.remove(0);
                        }
                    }
                }
            } catch (IOException ignored) {
                // Diagnostics only.
            }
        }, "silkvis-media-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    private void handleLine(String line) {
        IpcEnvelope envelope;
        try {
            envelope = IpcCodec.decodeLine(line);
        } catch (IpcCodec.IpcException e) {
            // Malformed input is ignored; repeated garbage triggers a restart limit.
            return;
        }
        switch (envelope.type()) {
            case IpcProtocol.READY -> {
                status = ProviderStatus.READY;
                statusReason = "";
                emitStatus();
            }
            case IpcProtocol.SESSIONS -> applySessions(envelope.payload());
            case IpcProtocol.SNAPSHOT -> {
                MediaSnapshot snapshot = parseSnapshot(envelope.payload());
                if (snapshot != null && listener != null) {
                    listener.onSnapshot(snapshot);
                }
            }
            case IpcProtocol.COMMAND_RESULT -> {
                MediaCommandResult result = parseCommandResult(envelope.payload());
                if (result != null && listener != null) {
                    listener.onCommandResult(result);
                }
            }
            case IpcProtocol.ARTWORK -> {
                String ref = envelope.payload().has("artworkRef")
                        ? envelope.payload().get("artworkRef").string() : "";
                byte[] data = envelope.payload().has("pngBase64")
                        ? java.util.Base64.getDecoder().decode(envelope.payload().get("pngBase64").string())
                        : new byte[0];
                if (listener != null) {
                    listener.onArtwork(ref, data);
                }
            }
            case IpcProtocol.ERROR -> {
                status = ProviderStatus.ERROR;
                statusReason = envelope.payload().has("code")
                        ? envelope.payload().get("code").string() : "helper_error";
                emitStatus();
            }
            default -> {
                // Unknown types are forward-compatible noise, not failures.
            }
        }
    }

    private void applySessions(JsonObject payload) {
        sessions.clear();
        if (payload.has("sessions")) {
            for (var entry : payload.get("sessions").array().asList()) {
                JsonObject o = entry.object();
                String sessionId = o.has("sessionId") ? o.get("sessionId").string() : "";
                if (sessionId.isEmpty()) {
                    continue;
                }
                sessions.put(sessionId, new MediaSessionRef(sessionId,
                        o.has("appId") ? o.get("appId").string() : "",
                        o.has("displayName") ? o.get("displayName").string() : "",
                        o.has("current") && o.get("current").bool()));
            }
        }
        if (status == ProviderStatus.READY && sessions.isEmpty()) {
            status = ProviderStatus.NO_SESSION;
        }
        emitStatus();
    }

    private MediaSnapshot parseSnapshot(JsonObject p) {
        try {
            String sessionId = p.get("sessionId").string();
            MediaCapabilities caps = MediaCapabilities.of(parseActions(p));
            return new MediaSnapshot(
                    id(),
                    sessionId,
                    p.has("sessionGeneration") ? p.get("sessionGeneration").number().longValueExact() : 1L,
                    p.has("revision") ? p.get("revision").number().longValueExact() : 0L,
                    optString(p, "sourceAppId"),
                    optString(p, "sourceDisplayName"),
                    optString(p, "title"),
                    optString(p, "artist"),
                    optString(p, "album"),
                    optString(p, "artworkRef"),
                    parsePlaybackState(p),
                    optLong(p, "positionMs"),
                    optLong(p, "startMs"),
                    optLong(p, "endMs"),
                    optLong(p, "minSeekMs"),
                    optLong(p, "maxSeekMs"),
                    optDouble(p, "playbackRate"),
                    clock.monotonicNanos(),
                    caps,
                    optString(p, "availabilityReason"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static MediaAction[] parseActions(JsonObject p) {
        List<MediaAction> actions = new ArrayList<>();
        if (p.has("capabilities")) {
            for (var entry : p.get("capabilities").array().asList()) {
                try {
                    actions.add(MediaAction.valueOf(entry.string()));
                } catch (IllegalArgumentException ignored) {
                    // Unknown capability names are ignored, not invented.
                }
            }
        }
        return actions.toArray(new MediaAction[0]);
    }

    private static PlaybackState parsePlaybackState(JsonObject p) {
        String raw = optString(p, "playbackState");
        try {
            return PlaybackState.valueOf(raw == null ? "UNKNOWN" : raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PlaybackState.UNKNOWN;
        }
    }

    private MediaCommandResult parseCommandResult(JsonObject p) {
        try {
            long requestId = p.get("requestId").number().longValueExact();
            String raw = p.has("status") ? p.get("status").string().toUpperCase(Locale.ROOT) : "REJECTED";
            MediaCommandResult.Status statusValue;
            try {
                statusValue = MediaCommandResult.Status.valueOf(raw);
            } catch (IllegalArgumentException e) {
                statusValue = MediaCommandResult.Status.REJECTED;
            }
            Long revision = optLong(p, "resultingRevision");
            return new MediaCommandResult(requestId, statusValue, optString(p, "errorCode"), revision);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String optString(JsonObject p, String key) {
        return p.has(key) && !p.get(key).isNull() ? p.get(key).string() : null;
    }

    private static Long optLong(JsonObject p, String key) {
        return p.has(key) && !p.get(key).isNull() ? p.get(key).number().longValueExact() : null;
    }

    private static Double optDouble(JsonObject p, String key) {
        return p.has(key) && !p.get(key).isNull() ? p.get(key).number().doubleValue() : null;
    }

    private void onProcessExit(Process proc) {
        if (proc != process) {
            return;
        }
        process = null;
        stdin = null;
        if (!running.get()) {
            return;
        }
        status = ProviderStatus.ERROR;
        statusReason = "helper_exited";
        emitStatus();
        if (restarts.get() < MAX_RESTARTS) {
            restarts.incrementAndGet();
            long backoffMs = 250L * (1L << restarts.get());
            scheduleRestart(backoffMs);
        } else {
            statusReason = "restart_limit";
            running.set(false);
        }
    }

    private void scheduleRestart(long backoffMs) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.execute(() -> {
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (running.get() && process == null) {
                spawnAndHandshake();
            }
        });
    }

    private void fail(String reason) {
        status = ProviderStatus.ERROR;
        statusReason = reason;
        emitStatus();
        running.set(false);
    }

    private void emitStatus() {
        MediaProviderListener target = listener;
        if (target != null) {
            target.onStatus(status, statusReason);
        }
    }

    private synchronized void send(IpcEnvelope envelope) {
        Writer out = stdin;
        if (out == null) {
            throw new IllegalStateException("helper not running");
        }
        try {
            out.write(IpcCodec.encode(envelope));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            fail("write_failed");
        }
    }

    @Override
    public List<MediaSessionRef> listSessions() {
        return List.copyOf(sessions.values());
    }

    @Override
    public void selectSession(String sessionId) {
        if (sessionId == null || status != ProviderStatus.READY && status != ProviderStatus.NO_SESSION) {
            return;
        }
        send(IpcEnvelope.of(IpcProtocol.SELECT_SESSION,
                JsonValue.newObject().put("sessionId", sessionId)));
    }

    @Override
    public void execute(MediaCommand command) {
        JsonObject payload = JsonValue.newObject()
                .put("requestId", command.requestId())
                .put("sessionId", command.sessionId())
                .put("sessionGeneration", command.sessionGeneration())
                .put("action", command.action().name());
        if (command.expectedRevision() != null) {
            payload.put("expectedRevision", command.expectedRevision());
        }
        if (command.seekPositionMs() != null) {
            payload.put("seekPositionMs", command.seekPositionMs());
        }
        send(IpcEnvelope.request(command.requestId(), IpcProtocol.COMMAND, payload));
    }

    @Override
    public void requestArtwork(String sessionId, long sessionGeneration, String artworkRef) {
        if (artworkRef == null || artworkRef.isEmpty()) {
            return;
        }
        send(IpcEnvelope.of(IpcProtocol.ARTWORK_REQUEST, JsonValue.newObject()
                .put("sessionId", sessionId)
                .put("sessionGeneration", sessionGeneration)
                .put("artworkRef", artworkRef)));
    }

    /** Bounded stderr tail for the diagnostics export (no metadata, no paths). */
    public List<String> diagnosticsTail() {
        synchronized (stderrTail) {
            return List.copyOf(stderrTail);
        }
    }

    public int restartCount() {
        return restarts.get();
    }

    public OptionalSilkError lastError() {
        return new OptionalSilkError(status == ProviderStatus.ERROR
                ? SilkError.of(ErrorCode.PROVIDER_UNAVAILABLE, "reason", statusReason) : null);
    }

    /** Shutdown sequence: SHUTDOWN line, 2 s grace, then only the child process is killed. */
    @Override
    public void close() {
        running.set(false);
        Process proc = process;
        if (proc != null) {
            try {
                send(IpcEnvelope.of(IpcProtocol.SHUTDOWN, JsonValue.newObject()));
            } catch (RuntimeException ignored) {
                // Already gone.
            }
            try {
                if (!proc.waitFor(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    proc.destroy();
                    if (!proc.waitFor(1000, TimeUnit.MILLISECONDS)) {
                        proc.destroyForcibly();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                proc.destroyForcibly();
            }
        }
        process = null;
        stdin = null;
        if (dispatcher != null) {
            dispatcher.shutdownNow();
        }
        status = ProviderStatus.DISABLED;
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** Small nullable wrapper kept for API symmetry in the UI layer. */
    public record OptionalSilkError(SilkError error) {
        public boolean present() {
            return error != null;
        }
    }
}

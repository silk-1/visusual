package silkvis.media;

import silkvis.core.Clock;
import silkvis.core.ErrorCode;
import silkvis.core.SilkError;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Client-side media orchestration (MEDIA-09, MEDIA-11..14).
 *
 * <p>Owns selection (AUTO/PINNED), generation/revision filtering, command addressing,
 * command deadlines and artwork invalidation. The UI only reads the cached view model,
 * so no native call ever happens inside rendering (HUD-04).</p>
 */
public final class MediaService implements MediaProviderListener, AutoCloseable {
    public static final long COMMAND_TIMEOUT_MS = 2000L;

    private final MediaProvider provider;
    private final Clock clock;
    private final ArtworkCache artwork = new ArtworkCache(ArtworkCache.DEFAULT_MAX_BYTES);
    private final Map<Long, Pending> pendingById = new ConcurrentHashMap<>();

    private final Consumer<MediaSnapshot> snapshotSink;
    private final Consumer<MediaCommandResult> resultSink;

    private volatile ProviderStatus status = ProviderStatus.DISABLED;
    private volatile String statusReason = "";
    private SessionSelection selection = SessionSelection.AUTO;
    private String pinnedAppId = "";
    private String pinnedSessionId;
    private MediaSnapshot current;
    private long sessionGeneration;
    private long requestCounter;
    private String artworkHash = "";
    private boolean artworkRequested;
    private Long seekIntentMs;

    public MediaService(MediaProvider provider, Clock clock,
                        Consumer<MediaSnapshot> snapshotSink, Consumer<MediaCommandResult> resultSink) {
        this.provider = provider;
        this.clock = clock;
        this.snapshotSink = snapshotSink == null ? s -> { } : snapshotSink;
        this.resultSink = resultSink == null ? r -> { } : resultSink;
    }

    public void start(SessionSelection selection, String pinnedAppId) {
        this.selection = selection == null ? SessionSelection.AUTO : selection;
        this.pinnedAppId = pinnedAppId == null ? "" : pinnedAppId;
        status = ProviderStatus.STARTING;
        provider.start(this);
    }

    public ProviderStatus status() {
        return status;
    }

    public String statusReason() {
        return statusReason;
    }

    public Optional<MediaSnapshot> snapshot() {
        return Optional.ofNullable(current);
    }

    public List<MediaSessionRef> sessions() {
        try {
            return provider.listSessions();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public void setSelection(SessionSelection newSelection, String newPinnedAppId) {
        this.selection = newSelection == null ? SessionSelection.AUTO : newSelection;
        this.pinnedAppId = newPinnedAppId == null ? "" : newPinnedAppId;
        if (this.selection == SessionSelection.AUTO) {
            pinnedSessionId = null;
        }
        artworkHash = "";
        artworkRequested = false;
    }

    public void selectSession(String sessionId) {
        try {
            provider.selectSession(sessionId);
        } catch (RuntimeException e) {
            status = ProviderStatus.ERROR;
            statusReason = "select_failed";
        }
    }

    /**
     * Dispatches an addressed command. Unsupported capabilities are rejected locally and
     * never turned into a global media key (MEDIA-11).
     */
    public MediaCommandResult command(MediaAction action) {
        MediaSnapshot snapshot = current;
        if (snapshot == null) {
            return MediaCommandResult.rejected(0L, MediaCommandResult.Status.STALE_SESSION, "no_session");
        }
        if (!snapshot.capabilities().supports(action)) {
            return MediaCommandResult.rejected(0L, MediaCommandResult.Status.UNSUPPORTED, action.name());
        }
        long id = ++requestCounter;
        MediaCommand cmd = MediaCommand.of(id, snapshot, action);
        pendingById.put(id, new Pending(cmd, clock.epochMillis() + COMMAND_TIMEOUT_MS));
        try {
            provider.execute(cmd);
        } catch (RuntimeException e) {
            pendingById.remove(id);
            return MediaCommandResult.rejected(id, MediaCommandResult.Status.REJECTED, "provider_error");
        }
        return MediaCommandResult.accepted(id, snapshot.revision());
    }

    /** Seek request; the intent is shown separately until the source confirms (MEDIA-14). */
    public MediaCommandResult seekTo(long positionMs) {
        MediaSnapshot snapshot = current;
        if (snapshot == null) {
            return MediaCommandResult.rejected(0L, MediaCommandResult.Status.STALE_SESSION, "no_session");
        }
        if (!MediaTimeline.canSeek(snapshot)) {
            return MediaCommandResult.rejected(0L, MediaCommandResult.Status.UNSUPPORTED, "seek_not_available");
        }
        long id = ++requestCounter;
        MediaCommand cmd = MediaCommand.seek(id, snapshot, positionMs);
        seekIntentMs = cmd.seekPositionMs();
        pendingById.put(id, new Pending(cmd, clock.epochMillis() + COMMAND_TIMEOUT_MS));
        try {
            provider.execute(cmd);
        } catch (RuntimeException e) {
            pendingById.remove(id);
            seekIntentMs = null;
            return MediaCommandResult.rejected(id, MediaCommandResult.Status.REJECTED, "provider_error");
        }
        return MediaCommandResult.accepted(id, snapshot.revision());
    }

    public OptionalLongIntent seekIntent() {
        return new OptionalLongIntent(seekIntentMs);
    }

    public ArtworkCache artwork() {
        return artwork;
    }

    /** Periodic reconciliation: command deadlines, stale artwork and session loss. */
    public void tick() {
        long now = clock.epochMillis();
        for (Map.Entry<Long, Pending> e : pendingById.entrySet()) {
            if (now > e.getValue().deadlineMs) {
                MediaCommand cmd = e.getValue().command;
                pendingById.remove(e.getKey());
                MediaCommandResult result = MediaCommandResult.rejected(cmd.requestId(),
                        MediaCommandResult.Status.TIMEOUT, "timeout");
                if (cmd.action() == MediaAction.SEEK) {
                    seekIntentMs = null;
                }
                resultSink.accept(result);
            }
        }
        MediaSnapshot snapshot = current;
        if (snapshot != null && !snapshot.artworkOpt().isPresent()) {
            artworkHash = "";
        }
    }

    /** Requests artwork at most once per (session, revision, ref) (MEDIA-14). */
    public void requestArtworkIfNeeded() {
        MediaSnapshot snapshot = current;
        if (snapshot == null || artworkRequested) {
            return;
        }
        Optional<String> ref = snapshot.artworkOpt();
        if (ref.isEmpty()) {
            return;
        }
        artworkRequested = true;
        try {
            provider.requestArtwork(snapshot.sessionId(), snapshot.sessionGeneration(), ref.get());
        } catch (RuntimeException e) {
            artworkRequested = false;
        }
    }

    @Override
    public void onStatus(ProviderStatus newStatus, String reason) {
        this.status = newStatus;
        this.statusReason = reason == null ? "" : reason;
        if (newStatus == ProviderStatus.NO_SESSION || newStatus == ProviderStatus.DISABLED
                || newStatus == ProviderStatus.ERROR || newStatus == ProviderStatus.UNSUPPORTED) {
            clearSession("status_" + newStatus.name().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public void onSnapshot(MediaSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (selection == SessionSelection.PINNED) {
            boolean matchesApp = pinnedAppId.isEmpty() || pinnedAppId.equals(snapshot.sourceAppId());
            boolean matchesSession = pinnedSessionId == null || pinnedSessionId.equals(snapshot.sessionId());
            if (!matchesApp || !matchesSession) {
                return;
            }
            if (pinnedSessionId == null) {
                pinnedSessionId = snapshot.sessionId();
            }
        }
        MediaSnapshot previous = current;
        if (previous != null && previous.sameSessionAs(snapshot)) {
            if (snapshot.revision() < previous.revision()) {
                return;
            }
        } else {
            sessionGeneration++;
            artworkHash = "";
            artworkRequested = false;
            seekIntentMs = null;
        }
        current = snapshot;
        if (snapshot.playbackState() == PlaybackState.PLAYING || snapshot.revision() > 0) {
            status = ProviderStatus.READY;
        }
        snapshotSink.accept(snapshot);
    }

    @Override
    public void onCommandResult(MediaCommandResult result) {
        Pending pending = pendingById.remove(result.requestId());
        if (result.status() == MediaCommandResult.Status.ACCEPTED && pending != null
                && pending.command.action() == MediaAction.SEEK) {
            // Keep the intent until the next snapshot reports the real position.
        } else if (result.status() != MediaCommandResult.Status.ACCEPTED) {
            seekIntentMs = null;
        }
        if (pending != null && result.status() == MediaCommandResult.Status.STALE_SESSION) {
            clearSession("stale_session");
        }
        resultSink.accept(result);
    }

    @Override
    public void onArtwork(String artworkRef, byte[] pngBytes) {
        MediaSnapshot snapshot = current;
        if (snapshot == null || artworkRef == null || !snapshot.artworkOpt().isPresent()
                || !artworkRef.equals(snapshot.artworkOpt().get())) {
            return;
        }
        if (!ArtworkCache.looksLikePng(pngBytes) || pngBytes.length > IpcProtocol.MAX_ARTWORK_BYTES) {
            return;
        }
        String hash = hash(pngBytes);
        artwork.put(hash, pngBytes);
        artworkHash = hash;
    }

    public Optional<String> currentArtworkHash() {
        return artworkHash.isEmpty() ? Optional.empty() : Optional.of(artworkHash);
    }

    private void clearSession(String reason) {
        current = null;
        artworkHash = "";
        artworkRequested = false;
        seekIntentMs = null;
        statusReason = reason;
    }

    private static String hash(byte[] data) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", digest[i]));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "sha256-unavailable";
        }
    }

    public Optional<SilkError> lastError() {
        return status == ProviderStatus.ERROR
                ? Optional.of(SilkError.of(ErrorCode.PROVIDER_UNAVAILABLE, "status", statusReason))
                : Optional.empty();
    }

    /** True when the UI should render the "source unavailable" state instead of stale data. */
    public boolean sourceUnavailable() {
        return selection == SessionSelection.PINNED && current == null
                && (status == ProviderStatus.NO_SESSION || status == ProviderStatus.READY);
    }

    @Override
    public void close() {
        try {
            provider.close();
        } catch (RuntimeException ignored) {
            // Closing must never throw into the UI path.
        }
        pendingById.clear();
        artwork.clear();
        status = ProviderStatus.DISABLED;
        current = null;
    }

    private record Pending(MediaCommand command, long deadlineMs) {
    }

    /** Simple nullable-long carrier so the UI does not need bare nullable boxes. */
    public record OptionalLongIntent(Long valueMs) {
        public boolean present() {
            return valueMs != null;
        }
    }
}

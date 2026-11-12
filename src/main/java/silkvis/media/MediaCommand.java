package silkvis.media;

/** Addressed command for one session (7.2); never a global media key. */
public record MediaCommand(
        long requestId,
        String sessionId,
        long sessionGeneration,
        Long expectedRevision,
        MediaAction action,
        Long seekPositionMs
) {
    public MediaCommand {
        if (action == null) {
            throw new IllegalArgumentException("action required");
        }
        if (action == MediaAction.SEEK && seekPositionMs == null) {
            throw new IllegalArgumentException("SEEK requires seekPositionMs");
        }
    }

    public static MediaCommand of(long requestId, MediaSnapshot snapshot, MediaAction action) {
        return new MediaCommand(requestId, snapshot.sessionId(), snapshot.sessionGeneration(),
                snapshot.revision(), action, null);
    }

    public static MediaCommand seek(long requestId, MediaSnapshot snapshot, long positionMs) {
        long clamped = positionMs;
        if (snapshot.minSeekMs() != null) {
            clamped = Math.max(clamped, snapshot.minSeekMs());
        }
        if (snapshot.maxSeekMs() != null) {
            clamped = Math.min(clamped, snapshot.maxSeekMs());
        }
        return new MediaCommand(requestId, snapshot.sessionId(), snapshot.sessionGeneration(),
                snapshot.revision(), MediaAction.SEEK, Math.max(0L, clamped));
    }
}

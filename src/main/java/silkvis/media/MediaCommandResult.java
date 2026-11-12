package silkvis.media;

/**
 * Result of a command dispatch. ACCEPTED means the source accepted the request, not
 * that the track changed; the UI confirms through the next snapshot (MEDIA-07).
 */
public record MediaCommandResult(long requestId, Status status, String errorCode, Long resultingRevision) {
    public enum Status {
        ACCEPTED,
        REJECTED,
        UNSUPPORTED,
        TIMEOUT,
        STALE_SESSION
    }

    public static MediaCommandResult accepted(long requestId, Long revision) {
        return new MediaCommandResult(requestId, Status.ACCEPTED, null, revision);
    }

    public static MediaCommandResult rejected(long requestId, Status status, String errorCode) {
        return new MediaCommandResult(requestId, status, errorCode, null);
    }
}

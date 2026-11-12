package silkvis.media;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Immutable view of a session (7.2). Unknown values are empty optionals, never zeros
 * and never an invented title.
 *
 * @param sessionGeneration incremented for every new session id seen by the provider
 * @param revision          monotonic revision inside one generation
 */
public record MediaSnapshot(
        String providerId,
        String sessionId,
        long sessionGeneration,
        long revision,
        String sourceAppId,
        String sourceDisplayName,
        String title,
        String artist,
        String album,
        String artworkRef,
        PlaybackState playbackState,
        Long positionMs,
        Long startMs,
        Long endMs,
        Long minSeekMs,
        Long maxSeekMs,
        Double playbackRate,
        long capturedAtMonotonicNanos,
        MediaCapabilities capabilities,
        String availabilityReason
) {
    public MediaSnapshot {
        if (providerId == null || sessionId == null) {
            throw new IllegalArgumentException("providerId/sessionId required");
        }
        if (playbackState == null) {
            playbackState = PlaybackState.UNKNOWN;
        }
        if (capabilities == null) {
            capabilities = MediaCapabilities.none();
        }
    }

    public Optional<String> titleOpt() {
        return Optional.ofNullable(blankToNull(title));
    }

    public Optional<String> artistOpt() {
        return Optional.ofNullable(blankToNull(artist));
    }

    public Optional<String> albumOpt() {
        return Optional.ofNullable(blankToNull(album));
    }

    public Optional<String> artworkOpt() {
        return Optional.ofNullable(blankToNull(artworkRef));
    }

    public OptionalLong position() {
        return positionMs == null ? OptionalLong.empty() : OptionalLong.of(positionMs);
    }

    public OptionalLong end() {
        return endMs == null || endMs <= 0 ? OptionalLong.empty() : OptionalLong.of(endMs);
    }

    public OptionalDouble rate() {
        return playbackRate == null ? OptionalDouble.empty() : OptionalDouble.of(playbackRate);
    }

    public String displaySource() {
        return blankToNull(sourceDisplayName) != null ? sourceDisplayName
                : blankToNull(sourceAppId) != null ? sourceAppId : "—";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Same session identity check used to reject stale UI updates (I2). */
    public boolean sameSessionAs(MediaSnapshot other) {
        return other != null
                && providerId.equals(other.providerId)
                && sessionId.equals(other.sessionId)
                && sessionGeneration == other.sessionGeneration;
    }
}

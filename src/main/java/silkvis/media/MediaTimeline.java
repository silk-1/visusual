package silkvis.media;

import silkvis.core.Clock;

import java.util.OptionalLong;

/**
 * Position projection from start/end/position and seek bounds (MEDIA-13).
 * Extrapolation uses the monotonic clock and only when playing.
 */
public final class MediaTimeline {
    private MediaTimeline() {
    }

    /** Estimated position, or empty when the source is stopped/paused without data. */
    public static OptionalLong projectPosition(MediaSnapshot snapshot, long nowNanos, Clock clock) {
        if (snapshot == null || snapshot.positionMs() == null) {
            return OptionalLong.empty();
        }
        long base = snapshot.positionMs();
        if (snapshot.playbackState() == PlaybackState.PLAYING) {
            long elapsedMs = Math.max(0L, (nowNanos - snapshot.capturedAtMonotonicNanos()) / 1_000_000L);
            double rate = snapshot.playbackRate() == null ? 1.0 : snapshot.playbackRate();
            if (!Double.isFinite(rate) || rate <= 0 || rate > 4.0) {
                rate = 1.0;
            }
            base += Math.round(elapsedMs * rate);
        }
        long min = snapshot.minSeekMs() == null ? 0L : snapshot.minSeekMs();
        long max = snapshot.maxSeekMs() != null ? snapshot.maxSeekMs()
                : snapshot.endMs() != null && snapshot.endMs() > 0 ? snapshot.endMs() : Long.MAX_VALUE;
        if (base < min) {
            base = min;
        }
        if (base > max) {
            base = max;
        }
        return base < 0 ? OptionalLong.empty() : OptionalLong.of(base);
    }

    /** A live stream or unknown length must not be shown as 0:00 / 0:00. */
    public static boolean isLiveOrUnknownLength(MediaSnapshot snapshot) {
        return snapshot == null || snapshot.endMs() == null || snapshot.endMs() <= 0;
    }

    /** Seek is only offered when the source declares both bounds and a finite length. */
    public static boolean canSeek(MediaSnapshot snapshot) {
        if (snapshot == null || !snapshot.capabilities().supports(MediaAction.SEEK)) {
            return false;
        }
        if (isLiveOrUnknownLength(snapshot)) {
            return false;
        }
        long min = snapshot.minSeekMs() == null ? 0L : snapshot.minSeekMs();
        long max = snapshot.maxSeekMs() == null ? snapshot.endMs() : snapshot.maxSeekMs();
        return max > min;
    }

    /** mm:ss for durations, h:mm:ss above one hour. Locale-independent digits. */
    public static String formatPosition(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    /** Text for the progress row: LIVE / --:-- never a fake 0:00 duration. */
    public static String describeLength(MediaSnapshot snapshot) {
        if (isLiveOrUnknownLength(snapshot)) {
            return snapshot != null && snapshot.playbackState() == PlaybackState.PLAYING ? "LIVE" : "--:--";
        }
        return formatPosition(snapshot.endMs());
    }
}

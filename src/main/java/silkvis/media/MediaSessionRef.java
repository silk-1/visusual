package silkvis.media;

/** Session list entry for the source selector (MEDIA-10). */
public record MediaSessionRef(String sessionId, String appId, String displayName, boolean isCurrent) {
}

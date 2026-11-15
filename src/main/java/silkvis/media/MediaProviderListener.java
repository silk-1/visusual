package silkvis.media;

/** Callbacks delivered on the client thread by the provider bridge (MEDIA-08). */
public interface MediaProviderListener {
    void onStatus(ProviderStatus status, String reason);

    void onSnapshot(MediaSnapshot snapshot);

    void onCommandResult(MediaCommandResult result);

    void onArtwork(String artworkRef, byte[] pngBytes);

    /**
     * Why the last cover request produced nothing ({@code no_thumbnail}, {@code too_large},
     * {@code not_an_image}, {@code exception}, ...). Defaulted so an older provider stays
     * source-compatible; the UI turns it into one Russian line instead of silence.
     */
    default void onArtworkNotice(String reason) {
    }
}

package silkvis.media;

/** Callbacks delivered on the client thread by the provider bridge (MEDIA-08). */
public interface MediaProviderListener {
    void onStatus(ProviderStatus status, String reason);

    void onSnapshot(MediaSnapshot snapshot);

    void onCommandResult(MediaCommandResult result);

    void onArtwork(String artworkRef, byte[] pngBytes);
}

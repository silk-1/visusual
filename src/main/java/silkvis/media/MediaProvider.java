package silkvis.media;

import java.util.List;

/**
 * Internal SilkVis provider contract (7.2). Not a Fabric API name.
 * Implementations must never block the render thread in {@link #listSessions()}.
 */
public interface MediaProvider extends AutoCloseable {
    String id();

    void start(MediaProviderListener listener);

    ProviderStatus status();

    List<MediaSessionRef> listSessions();

    void selectSession(String sessionId);

    void execute(MediaCommand command);

    void requestArtwork(String sessionId, long sessionGeneration, String artworkRef);

    @Override
    void close();
}

package silkvis.media;

/** Message type names shared with the native helper (MEDIA-07). */
public final class IpcProtocol {
    public static final String HELLO = "HELLO";
    public static final String READY = "READY";
    public static final String SESSIONS = "SESSIONS";
    public static final String SNAPSHOT = "SNAPSHOT";
    public static final String COMMAND = "COMMAND";
    public static final String COMMAND_RESULT = "COMMAND_RESULT";
    public static final String ARTWORK = "ARTWORK";
    public static final String ARTWORK_REQUEST = "ARTWORK_REQUEST";
    public static final String SELECT_SESSION = "SELECT_SESSION";
    public static final String ERROR = "ERROR";
    public static final String SHUTDOWN = "SHUTDOWN";
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_LINE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_METADATA_CHARS = 4096;
    public static final int MAX_ARTWORK_BYTES = 512 * 1024;

    private IpcProtocol() {
    }
}

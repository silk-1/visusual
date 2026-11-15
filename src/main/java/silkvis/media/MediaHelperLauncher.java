package silkvis.media;

import java.io.IOException;

/**
 * Where the media bridge process comes from (MEDIA-05).
 *
 * <p>Two implementations exist with identical wire protocols: the native helper executable
 * (fast, needs a Windows build) and the PowerShell bridge (slower start, needs no compiler
 * and therefore works on any Windows install). {@link WindowsMediaProvider} does not care
 * which one it got.</p>
 */
public interface MediaHelperLauncher {
    /** Provider id reported to the UI; differs between back ends. */
    String providerId();

    /**
     * @return null when the helper is present and verified, otherwise a short reason code
     *         such as {@code helper_missing}, {@code unsupported_platform} or
     *         {@code hash_mismatch}
     */
    String verify();

    /** Process to spawn; the caller owns stderr/stdout handling. */
    ProcessBuilder newProcessBuilder() throws IOException;

    /**
     * True when the bridge answers requests instead of pushing snapshots on its own. A
     * line-oriented script cannot run a timer while it waits on stdin, so the Java side
     * polls it.
     */
    default boolean polling() {
        return false;
    }
}

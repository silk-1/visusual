package silkvis.diagnostics.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where a bug report is written: the user's desktop when it exists, otherwise the game
 * directory.
 *
 * <p>Windows desktops are localized ("Рабочий стол") and often redirected into OneDrive,
 * so several well-known names are tried before falling back. Nothing is created here: an
 * existing, writable directory is returned only when it really is one.</p>
 */
public final class ReportTarget {
    /** Desktop folder names worth trying, in order: plain, localized, OneDrive-redirected. */
    private static final String[] DESKTOP_NAMES = {
            "Desktop",
            "Рабочий стол",
            "Рабочий_стол",
            "OneDrive/Desktop",
            "OneDrive/Рабочий стол"
    };

    private ReportTarget() {
    }

    public static Path desktop() {
        return desktop(null);
    }

    public static Path desktop(Path fallback) {
        String home = System.getProperty("user.home");
        if (home != null && !home.isEmpty()) {
            Path base = Paths.get(home);
            for (String name : DESKTOP_NAMES) {
                Path candidate = base.resolve(name);
                if (Files.isDirectory(candidate) && Files.isWritable(candidate)) {
                    return candidate;
                }
            }
        }
        return fallback == null ? Paths.get(".") : fallback;
    }

    /** Fallback used when writing next to the desktop failed. */
    public static Path gameDirFallback(Path gameDir) {
        if (gameDir == null) {
            return Paths.get("silkvis-reports");
        }
        try {
            return Files.createDirectories(gameDir.resolve("silkvis-reports"));
        } catch (IOException e) {
            return gameDir;
        }
    }
}

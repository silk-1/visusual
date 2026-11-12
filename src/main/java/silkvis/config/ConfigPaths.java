package silkvis.config;

import java.nio.file.Path;

/**
 * All files the mod may write, spelled out in one place (CFG-04). Nothing else in the code
 * base builds paths, so a path audit is a one-file read.
 */
public final class ConfigPaths {
    private static final String ROOT = "silkvis";

    private final Path configDir;
    private final Path dataDir;

    public ConfigPaths(Path configDir, Path dataDir) {
        this.configDir = configDir;
        this.dataDir = dataDir;
    }

    public Path globalFile() {
        return configDir.resolve(ROOT).resolve("global.json");
    }

    public Path profileDir(String profileId) {
        return configDir.resolve(ROOT).resolve("profiles").resolve(safeSegment(profileId));
    }

    public Path profileUiFile(String profileId) {
        return profileDir(profileId).resolve("ui.json");
    }

    public Path profileDestinationsFile(String profileId) {
        return profileDir(profileId).resolve("destinations.json");
    }

    public Path profileMarkersFile(String profileId) {
        return profileDir(profileId).resolve("markers.json");
    }

    public Path mapDir(String profileId) {
        return dataDir.resolve("maps").resolve(safeSegment(profileId));
    }

    public Path diagnosticsDir() {
        return dataDir.resolve("diagnostics");
    }

    /**
     * Filesystem-safe, collision-resistant-enough segment. Only lowercase {@code [a-z0-9_-]}
     * survives; everything else becomes {@code _}. Empty input becomes {@code unnamed}.
     */
    public static String safeSegment(String raw) {
        if (raw == null) {
            return "unnamed";
        }
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length() && out.length() < 48; i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            char mapped = ok ? c : '_';
            if (mapped == '_' && out.length() > 0 && out.charAt(out.length() - 1) == '_') {
                continue;
            }
            out.append(mapped);
        }
        String result = out.toString().replaceAll("^_+|_+$", "");
        return result.isEmpty() ? "unnamed" : result;
    }
}

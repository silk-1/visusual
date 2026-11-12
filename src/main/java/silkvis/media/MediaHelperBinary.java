package silkvis.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Locates and verifies the bundled native helper (MEDIA-05).
 *
 * <p>Rules: fixed file name inside the versioned extraction directory, OS architecture
 * check, SHA-256 comparison against the shipped manifest, no shell, no elevation, no
 * network download.</p>
 */
public final class MediaHelperBinary {
    public static final String HELPER_NAME = "silkvis-media-helper.exe";
    public static final String MANIFEST_NAME = "manifest.properties";

    private final Path directory;
    private final String osName;
    private final String osArch;

    public MediaHelperBinary(Path directory, String osName, String osArch) {
        this.directory = directory;
        this.osName = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        this.osArch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
    }

    public static MediaHelperBinary system(Path directory) {
        return new MediaHelperBinary(directory, System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    public boolean platformSupported() {
        return osName.contains("win") && (osArch.equals("amd64") || osArch.equals("x86_64"));
    }

    public Path binaryPath() {
        return directory.resolve(HELPER_NAME);
    }

    public Path manifestPath() {
        return directory.resolve(MANIFEST_NAME);
    }

    /**
     * @return null when the helper is present and matches the manifest, otherwise a
     *         short reason code used for the provider status
     */
    public String verify() {
        if (!platformSupported()) {
            return "unsupported_platform";
        }
        Path binary = binaryPath();
        if (!Files.isRegularFile(binary)) {
            return "helper_missing";
        }
        try {
            String expected = readExpectedHash();
            if (expected == null || expected.isBlank()) {
                return "manifest_missing_hash";
            }
            String actual = sha256(binary);
            if (!actual.equalsIgnoreCase(expected.trim())) {
                return "hash_mismatch";
            }
            return null;
        } catch (IOException e) {
            return "helper_unreadable";
        }
    }

    private String readExpectedHash() throws IOException {
        Path manifest = manifestPath();
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        for (String line : Files.readAllLines(manifest)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("sha256=")) {
                return trimmed.substring("sha256=".length()).trim();
            }
        }
        return null;
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest.digest()) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }
}

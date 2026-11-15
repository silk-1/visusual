package silkvis.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * PowerShell bridge (ADR-0008): the WinRT media APIs reachable from {@code powershell.exe}.
 *
 * <p>Deliberate trade-offs. Start costs a few hundred milliseconds and the process uses
 * ~50 MB, but nothing has to be compiled for Windows and there is no native binary in the
 * jar to sign, hash or explain to an antivirus. The script is extracted once into
 * {@code silkvis-data/native/media-helper-1}, and its SHA-256 must match the manifest that
 * ships inside the jar — an unverified script is never executed, it is reported as
 * {@code hash_mismatch} instead.</p>
 *
 * <p>Arguments are fixed: {@code -NoProfile -NonInteractive -ExecutionPolicy Bypass -File}.
 * No user input ever reaches the command line.</p>
 */
public final class PowerShellHelperLauncher implements MediaHelperLauncher {
    public static final String SCRIPT_NAME = "silkvis-media-helper.ps1";
    private static final String MANIFEST_RESOURCE = "/silkvis/native/media-helper.properties";
    private static final String SCRIPT_RESOURCE = "/silkvis/native/" + SCRIPT_NAME;

    private final Path directory;
    private final String osName;
    private final String osArch;

    public PowerShellHelperLauncher(Path directory) {
        this(directory, System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    public PowerShellHelperLauncher(Path directory, String osName, String osArch) {
        this.directory = directory;
        this.osName = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        this.osArch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
    }

    @Override
    public String providerId() {
        return "windows-powershell";
    }

    @Override
    public boolean polling() {
        return true;
    }

    @Override
    public String verify() {
        if (!osName.contains("win")) {
            return "unsupported_platform";
        }
        if (!osArch.equals("amd64") && !osArch.equals("x86_64")) {
            // WinRT projections in Windows PowerShell are only reliable on x64 here; an
            // untested architecture must not be reported as working.
            return "unsupported_arch";
        }
        String expected = expectedHash();
        if (expected == null) {
            return "manifest_missing_hash";
        }
        Path script = directory.resolve(SCRIPT_NAME);
        try {
            if (!Files.isRegularFile(script) || !expected.equalsIgnoreCase(scriptHash(script))) {
                extract();
            }
            if (!Files.isRegularFile(script)) {
                return "helper_resource_missing";
            }
            if (!expected.equalsIgnoreCase(scriptHash(script))) {
                return "hash_mismatch";
            }
        } catch (IOException e) {
            return "helper_unreadable";
        }
        if (!powershell().isAbsolute()) {
            return "powershell_missing";
        }
        return null;
    }

    @Override
    public ProcessBuilder newProcessBuilder() {
        List<String> command = new ArrayList<>();
        command.add(powershell().toString());
        command.add("-NoProfile");
        command.add("-NonInteractive");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(directory.resolve(SCRIPT_NAME).toString());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        return builder;
    }

    private Path powershell() {
        String root = System.getenv("SystemRoot");
        if (root != null && !root.isBlank()) {
            Path candidate = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(entry, "powershell.exe");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        // Relative marker: verify() reports "powershell_missing" rather than spawning.
        return Path.of("powershell.exe");
    }

    private void extract() throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(SCRIPT_NAME);
        try (InputStream in = PowerShellHelperLauncher.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IOException("resource " + SCRIPT_RESOURCE + " not found");
            }
            byte[] normalized = normalizeLineEndings(in.readAllBytes());
            String expected = expectedHash();
            if (expected != null && !expected.equalsIgnoreCase(MediaHelperBinary.sha256(normalized))) {
                // The packaged script does not match its own manifest: never run it.
                throw new IOException("packaged script hash mismatch");
            }
            Path temp = directory.resolve(SCRIPT_NAME + ".tmp");
            Files.write(temp, normalized);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * The manifest hash is computed over LF text. A Windows checkout with
     * {@code core.autocrlf=true} turns the script into CRLF, so the extracted copy no longer
     * matched and the provider was refused with {@code hash_mismatch} (owner report,
     * 2026-11-15). Line endings are not security-relevant, the content is, so both the file
     * on disk and the packaged resource are normalised before hashing.
     */
    private static byte[] normalizeLineEndings(byte[] data) {
        int carriageReturns = 0;
        for (byte b : data) {
            if (b == '\r') {
                carriageReturns++;
            }
        }
        if (carriageReturns == 0) {
            return data;
        }
        byte[] out = new byte[data.length - carriageReturns];
        int index = 0;
        for (byte b : data) {
            if (b != '\r') {
                out[index++] = b;
            }
        }
        return out;
    }

    private static String expectedHash() {
        try (InputStream in = PowerShellHelperLauncher.class.getResourceAsStream(MANIFEST_RESOURCE)) {
            if (in == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(in);
            String value = properties.getProperty("sha256");
            return value == null || value.isBlank() ? null : value.trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String scriptHash(Path file) throws IOException {
        return MediaHelperBinary.sha256(normalizeLineEndings(Files.readAllBytes(file)));
    }
}

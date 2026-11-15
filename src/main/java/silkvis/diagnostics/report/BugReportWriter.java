package silkvis.diagnostics.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Writes a report file with a timestamped, collision-free name (support flow). */
public final class BugReportWriter {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BugReportWriter() {
    }

    public static Path write(Path directory, String prefix, String body) throws IOException {
        if (directory == null) {
            throw new IOException("no report directory");
        }
        Files.createDirectories(directory);
        String name = sanitize(prefix) + "-" + LocalDateTime.now().format(STAMP) + ".txt";
        Path file = directory.resolve(name);
        int attempt = 2;
        while (Files.exists(file) && attempt <= 9) {
            file = directory.resolve(sanitize(prefix) + "-" + LocalDateTime.now().format(STAMP)
                    + "-" + attempt + ".txt");
            attempt++;
        }
        Files.writeString(file, body == null ? "" : body, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    private static String sanitize(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "silkvis-report";
        }
        return prefix.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}

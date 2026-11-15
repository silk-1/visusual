package silkvis.assets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * Fetches a totem picture from a link the player pasted (ASSET-04, "add by URL").
 *
 * <p>Only {@code http} and {@code https} are accepted: a {@code file:} or {@code jar:} URL
 * would turn a text field into a local file reader. The transfer is capped by size and by
 * timeout, because a link to a 200 MB file or a server that never answers must not hang the
 * client — the whole call runs on the IO executor, never on the render thread. Whatever
 * arrives is still validated by {@link TotemPackBuilder} before it becomes a texture, so a
 * link to an HTML page ends as "not a picture", not as a broken pack.</p>
 *
 * <p>Pure JDK (no Minecraft classes), so the rules below are covered by the self checks.</p>
 */
public final class TotemDownloader {
    /** A 16x16 icon never needs more; this is a guard rail, not a target. */
    public static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    public static final int TIMEOUT_SECONDS = 20;
    public static final int MAX_URL_LENGTH = 512;
    private static final int MAX_NAME_LENGTH = 48;
    private static final String[] IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};

    private TotemDownloader() {
    }

    /**
     * Result of one attempt.
     *
     * @param reason {@code ok}, {@code url_invalid}, {@code download_failed},
     *               {@code file_too_large} or {@code folder_full}
     */
    public record Downloaded(String reason, Path file, long bytes) {
        public boolean ok() {
            return "ok".equals(reason);
        }

        static Downloaded failure(String reason) {
            return new Downloaded(reason, null, 0);
        }
    }

    /**
     * Null when the link is usable, otherwise the reason code the UI translates. Checked while
     * the player types, so an obviously wrong link never starts a download.
     */
    public static String validate(String url) {
        if (url == null || url.isBlank()) {
            return "url_invalid";
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            return "url_invalid";
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return "url_invalid";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "url_invalid";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "url_invalid";
        }
        return null;
    }

    /**
     * File name for a downloaded picture: the last path segment, stripped to safe characters,
     * with an extension the drop folder accepts. The content type is only a fallback hint —
     * the actual bytes decide whether it is an image.
     */
    public static String fileNameFor(String url, String contentType) {
        String base = "";
        String extension = "";
        if (url != null) {
            try {
                String path = new URI(url.trim()).getPath();
                if (path != null && !path.isBlank()) {
                    String segment = path.substring(path.lastIndexOf('/') + 1);
                    segment = URLDecoder.decode(segment, StandardCharsets.UTF_8);
                    int dot = segment.lastIndexOf('.');
                    if (dot > 0) {
                        extension = segment.substring(dot).toLowerCase(Locale.ROOT);
                        segment = segment.substring(0, dot);
                    }
                    base = sanitize(segment);
                }
            } catch (URISyntaxException | IllegalArgumentException ignored) {
                // validate() already rejected malformed links; fall through to a generic name.
            }
        }
        if (base.isEmpty()) {
            base = "totem";
        }
        if (base.length() > MAX_NAME_LENGTH) {
            base = base.substring(0, MAX_NAME_LENGTH);
        }
        if (!isImageExtension(extension)) {
            extension = extensionFor(contentType);
        }
        return base + extension;
    }

    /** Characters a file name may keep; everything else becomes a dash. */
    static String sanitize(String name) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char chr = name.charAt(i);
            boolean allowed = (chr >= 'a' && chr <= 'z') || (chr >= 'A' && chr <= 'Z')
                    || (chr >= '0' && chr <= '9') || chr == '-' || chr == '_';
            // Non-ASCII is dropped on purpose: Windows and the resource pack loader both
            // treat it inconsistently, and a totem called "тотем.png" is not worth the bugs.
            builder.append(allowed ? chr : '-');
        }
        String cleaned = builder.toString().replaceAll("-{2,}", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        return cleaned.toLowerCase(Locale.ROOT);
    }

    static boolean isImageExtension(String extension) {
        for (String known : IMAGE_EXTENSIONS) {
            if (known.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    static String extensionFor(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("jpeg") || type.contains("jpg")) {
            return ".jpg";
        }
        if (type.contains("gif")) {
            return ".gif";
        }
        if (type.contains("bmp")) {
            return ".bmp";
        }
        // Default to PNG: the builder re-encodes whatever it can decode anyway.
        return ".png";
    }

    /** First free name: {@code totem.png}, then {@code totem-2.png}, ... */
    public static Path freePath(Path directory, String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        Path candidate = directory.resolve(fileName);
        for (int index = 2; index < 100; index++) {
            if (!Files.exists(candidate)) {
                return candidate;
            }
            candidate = directory.resolve(base + "-" + index + extension);
        }
        return null;
    }

    /**
     * Blocking download; call it from the IO executor. Never throws: every failure is a
     * reason code.
     */
    public static Downloaded download(String url, Path targetDirectory) {
        String invalid = validate(url);
        if (invalid != null) {
            return Downloaded.failure(invalid);
        }
        if (targetDirectory == null) {
            return Downloaded.failure("download_failed");
        }
        if (TotemLibrary.scan(targetDirectory).size() >= TotemLibrary.MAX_ENTRIES) {
            return Downloaded.failure("folder_full");
        }
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            return Downloaded.failure("download_failed");
        }
        URI uri = URI.create(url.trim());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", "SilkVis (Minecraft mod, custom totem)")
                .header("Accept", "image/*,*/*;q=0.5")
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        try {
            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                return Downloaded.failure("download_failed");
            }
            byte[] data;
            try (InputStream body = response.body()) {
                data = readBounded(body);
            }
            if (data == null) {
                return Downloaded.failure("file_too_large");
            }
            if (data.length == 0) {
                return Downloaded.failure("download_failed");
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            Path target = freePath(targetDirectory, fileNameFor(url, contentType));
            if (target == null) {
                return Downloaded.failure("folder_full");
            }
            Files.write(target, data);
            return new Downloaded("ok", target, data.length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Downloaded.failure("download_failed");
        } catch (IOException | RuntimeException e) {
            return Downloaded.failure("download_failed");
        } finally {
            client.close();
        }
    }

    /** Null when the stream is larger than the cap; otherwise all of its bytes. */
    public static byte[] readBounded(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            if (out.size() + read > MAX_DOWNLOAD_BYTES) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}

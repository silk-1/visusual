package silkvis.config;

import silkvis.config.json.JsonException;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;
import silkvis.core.Clock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One JSON config file: atomic write (tmp + move), best-effort backup, and a load path that
 * never throws at the caller (CFG-04).
 *
 * <ul>
 *   <li>missing file -&gt; {@code FRESH} with defaults;</li>
 *   <li>damaged file -&gt; {@code SAFE_DEFAULT}, damaged copy kept as {@code *.corrupt-&lt;epoch&gt;};</li>
 *   <li>newer {@code schemaVersion} -&gt; {@code READ_ONLY_NEWER}, the file is never rewritten.</li>
 * </ul>
 */
public final class JsonConfigFile<T> {
    private static final long MAX_BYTES = 1L << 20;

    private final Path file;
    private final ConfigCodec<T> codec;
    private final Clock clock;

    public JsonConfigFile(Path file, ConfigCodec<T> codec, Clock clock) {
        this.file = file;
        this.codec = codec;
        this.clock = clock;
    }

    public Path file() {
        return file;
    }

    public LoadOutcome<T> load() {
        if (file == null || !Files.exists(file)) {
            return LoadOutcome.fresh(codec.defaults());
        }
        try {
            if (Files.size(file) > MAX_BYTES) {
                return quarantine(ConfigException.of("file", "larger than 1 MiB"));
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root;
            try {
                root = JsonValue.parse(text).object();
            } catch (JsonException | IllegalArgumentException e) {
                return quarantine(ConfigException.of("file", "malformed json: " + e.getMessage()));
            }
            int schema = root.has("schemaVersion") ? root.get("schemaVersion").number().intValueExact() : 0;
            if (schema > codec.currentSchemaVersion()) {
                return LoadOutcome.readOnlyNewer(codec.defaults(), ConfigException.of("schemaVersion",
                        "file schema " + schema + " is newer than " + codec.currentSchemaVersion()));
            }
            T value = codec.decode(root);
            codec.validate(value);
            return LoadOutcome.loaded(value);
        } catch (ConfigException e) {
            return quarantine(e);
        } catch (IOException e) {
            return LoadOutcome.safeDefault(codec.defaults(),
                    ConfigException.of("file", "unreadable: " + e.getClass().getSimpleName()));
        } catch (RuntimeException e) {
            return quarantine(ConfigException.of("file", "invalid content"));
        }
    }

    public void save(T value) throws ConfigException, IOException {
        codec.validate(value);
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonObject root = codec.encode(value);
        root.put("schemaVersion", codec.currentSchemaVersion());
        Path tmp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.writeString(tmp, root.write(true) + "\n", StandardCharsets.UTF_8);
        Path backup = absolute.resolveSibling(absolute.getFileName() + ".bak");
        if (Files.exists(absolute)) {
            try {
                Files.copy(absolute, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Backup is best effort; the atomic move below is the real guarantee.
            }
        }
        try {
            Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private LoadOutcome<T> quarantine(ConfigException error) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (Files.exists(file) && parent != null) {
                Path copy = parent.resolve(file.getFileName() + ".corrupt-" + clock.epochMillis());
                Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Preserving the damaged file is best effort; the caller still gets safe defaults.
        }
        return LoadOutcome.safeDefault(codec.defaults(), error);
    }
}

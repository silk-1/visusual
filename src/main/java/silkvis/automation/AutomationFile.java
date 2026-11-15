package silkvis.automation;

import silkvis.config.ConfigCodec;
import silkvis.config.ConfigException;
import silkvis.config.JsonConfigFile;
import silkvis.config.json.JsonObject;
import silkvis.core.Clock;

import java.nio.file.Path;

/**
 * The {@code automation.json} file of one profile (AUTO-01, CFG-04).
 *
 * <p>Deliberately a thin shell over {@link JsonConfigFile}: the codec is the whole logic, so the
 * atomic write, the corrupt-file quarantine and the "newer schema is read-only" rule are shared
 * with every other config instead of being re-implemented here. The file lives next to
 * {@code ui.json} in the profile folder, so a profile carries its automation switches with it.</p>
 *
 * <p>Nothing in this file reads the game: the self checks build it against a temporary directory
 * and assert that a damaged file still yields the safe defaults.</p>
 */
public final class AutomationFile {
    /** Bumped only when the meaning of an existing field changes, never for a new field. */
    public static final int SCHEMA_VERSION = 1;

    private final JsonConfigFile<AutomationSettings> file;
    private final Path path;

    public AutomationFile(Path path, Clock clock) {
        this.path = path;
        this.file = new JsonConfigFile<>(path, new Codec(), clock);
    }

    public Path path() {
        return this.path;
    }

    /** Never throws: a missing or damaged file yields {@link AutomationSettings#defaults()}. */
    public AutomationSettings load() {
        return this.file.load().value();
    }

    /** Writes the settings; the caller is expected to swallow a failure and log it. */
    public void save(AutomationSettings settings) throws ConfigException, java.io.IOException {
        this.file.save(settings);
    }

    /** The JSON shape: the settings record itself, wrapped by the schema version. */
    static final class Codec implements ConfigCodec<AutomationSettings> {
        @Override
        public int currentSchemaVersion() {
            return SCHEMA_VERSION;
        }

        @Override
        public AutomationSettings defaults() {
            return AutomationSettings.defaults();
        }

        @Override
        public AutomationSettings decode(JsonObject root) throws ConfigException {
            return AutomationSettings.decode(root);
        }

        @Override
        public JsonObject encode(AutomationSettings value) {
            return value.encode();
        }

        @Override
        public void validate(AutomationSettings value) throws ConfigException {
            value.validate();
        }
    }
}

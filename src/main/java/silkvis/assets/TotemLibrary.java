package silkvis.assets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The folder the player drops totem pictures into: {@code silkvis-data/totems}.
 *
 * <p>Chosen over a typed path on purpose — a file dialog is not available to a mod without
 * native code, and typing {@code C:\Users\...} into a Minecraft text field is a bad trade.
 * Drop a PNG, open the tab, click the name.</p>
 */
public final class TotemLibrary {
    /** The tab shows one button per file; eight already fills it. */
    public static final int MAX_ENTRIES = 8;
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};

    private TotemLibrary() {
    }

    public record Entry(String name, Path path, long bytes) {
        /** Name without extension, for the button label. */
        public String displayName() {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
    }

    public static Path directory(Path dataDir) {
        return dataDir.resolve("totems");
    }

    /** Creates the folder so the player has somewhere to drop files; failure is not fatal. */
    public static void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ignored) {
            // A read-only instance must not break the UI: the tab simply lists nothing.
        }
    }

    /**
     * Sorted, bounded listing. Hidden files, directories and non-images are skipped; a
     * missing folder is not an error, it is "nothing dropped yet".
     */
    public static List<Entry> scan(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(TotemLibrary::hasImageExtension)
                    .forEach(path -> {
                        try {
                            entries.add(new Entry(path.getFileName().toString(), path,
                                    Files.size(path)));
                        } catch (IOException ignored) {
                            // Vanished between listing and stat: skip it.
                        }
                    });
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return entries.size() > MAX_ENTRIES ? List.copyOf(entries.subList(0, MAX_ENTRIES))
                : List.copyOf(entries);
    }

    static boolean hasImageExtension(Path path) {
        String name = path.getFileName() == null ? ""
                : path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}

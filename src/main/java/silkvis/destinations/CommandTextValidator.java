package silkvis.destinations;

import java.util.List;

/**
 * DEST-04: one command, no CR/LF/NUL, no chaining, no interpolation. The text is sent
 * to the client network API only; it is never passed to a shell or evaluated.
 */
public final class CommandTextValidator {
    public static final int MAX_LENGTH = 256;
    private static final List<String> FORBIDDEN = List.of("\n", "\r", "\u0000", ";", "&&", "||", "`", "$(", "${");

    private CommandTextValidator() {
    }

    /** Adds exactly one leading slash and trims surrounding whitespace. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("command is empty");
        }
        String trimmed = raw.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("command is empty");
        }
        return "/" + trimmed;
    }

    public static void validate(String normalized) {
        if (normalized == null || !normalized.startsWith("/")) {
            throw new IllegalArgumentException("command must start with '/'");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("command is longer than " + MAX_LENGTH + " chars");
        }
        for (String bad : FORBIDDEN) {
            if (normalized.contains(bad)) {
                throw new IllegalArgumentException("command must be a single command without chaining");
            }
        }
        if (normalized.chars().anyMatch(c -> c < 0x20 && c != '\t')) {
            throw new IllegalArgumentException("control characters are not allowed");
        }
    }

    public static boolean isValid(String normalized) {
        try {
            validate(normalized);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Shown in the confirmation dialog and never logged (DEST-04). */
    public static String previewForDisplay(String normalized) {
        return normalized.length() <= 96 ? normalized : normalized.substring(0, 96) + "…";
    }
}

package silkvis.config;

import java.util.regex.Pattern;

/** Shared field validators (CFG-03). Every failure names the exact field path. */
public final class Validators {
    private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern MODULE_ID = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*");

    private Validators() {
    }

    public static double finite(String field, double value, double min, double max) throws ConfigException {
        if (!Double.isFinite(value)) {
            throw ConfigException.of(field, "must be a finite number");
        }
        if (value < min || value > max) {
            throw ConfigException.of(field, "must be within " + min + ".." + max);
        }
        return value;
    }

    public static float finite(String field, float value, float min, float max) throws ConfigException {
        return (float) finite(field, (double) value, min, max);
    }

    public static int intRange(String field, int value, int min, int max) throws ConfigException {
        if (value < min || value > max) {
            throw ConfigException.of(field, "must be within " + min + ".." + max);
        }
        return value;
    }

    public static long longRange(String field, long value, long min, long max) throws ConfigException {
        if (value < min || value > max) {
            throw ConfigException.of(field, "must be within " + min + ".." + max);
        }
        return value;
    }

    /**
     * Single-line human text: no control characters, no leading/trailing whitespace,
     * non-empty and within the length cap. Keeps labels safe for the HUD and map.
     */
    public static String plainText(String field, String value, int maxLength) throws ConfigException {
        if (value == null) {
            throw ConfigException.of(field, "required");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw ConfigException.of(field, "must not be empty");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw ConfigException.of(field, "control characters are not allowed");
            }
        }
        if (trimmed.length() > maxLength) {
            throw ConfigException.of(field, "longer than " + maxLength + " characters");
        }
        return trimmed;
    }

    /** Registry id such as {@code minecraft:overworld}; namespaces and paths stay lowercase. */
    public static String registryId(String field, String value) throws ConfigException {
        if (value == null || value.length() > 128 || !REGISTRY_ID.matcher(value).matches()) {
            throw ConfigException.of(field, "must look like namespace:path");
        }
        return value;
    }

    /** Module id such as {@code hud.overlay}. */
    public static String moduleId(String field, String value) throws ConfigException {
        if (value == null || value.length() > 64 || !MODULE_ID.matcher(value).matches()) {
            throw ConfigException.of(field, "must be a lowercase dotted id");
        }
        return value;
    }

    /** Colour as 0xAARRGGBB. */
    public static int argb(String field, int value) throws ConfigException {
        return value;
    }
}

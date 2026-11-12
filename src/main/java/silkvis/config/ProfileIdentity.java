package silkvis.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Which world a profile belongs to (MAP-07). Multiplayer identity is address + port;
 * singleplayer identity is the save name. {@code profileId} is the sanitized, hashed form
 * used for folder names, so two servers on one address never share config or tiles.
 */
public record ProfileIdentity(String kind, String displayKey, String safeName, String profileId) {
    public static ProfileIdentity multiplayer(String address, int port) {
        String key = "mp:" + address + ":" + port;
        String base = ConfigPaths.safeSegment(address) + "-" + port;
        return new ProfileIdentity("multiplayer", key, base, base + "-" + shortHash(key));
    }

    public static ProfileIdentity singleplayer(String saveName) {
        String key = "sp:" + saveName;
        String base = ConfigPaths.safeSegment(saveName);
        return new ProfileIdentity("singleplayer", key, base, base + "-" + shortHash(key));
    }

    /** First 8 hex characters of SHA-256; enough to separate profiles, short enough for a path. */
    public static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                out.append(String.format("%02x", hash[i]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

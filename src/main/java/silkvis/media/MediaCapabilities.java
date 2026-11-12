package silkvis.media;

import java.util.EnumSet;
import java.util.Set;

/**
 * Runtime capability flags of one session (MEDIA-11). An absent flag disables the
 * matching control with a localized reason; nothing is inferred from a brand name.
 */
public record MediaCapabilities(Set<MediaAction> supported) {
    public MediaCapabilities {
        supported = supported == null ? Set.of() : Set.copyOf(supported);
    }

    public static MediaCapabilities none() {
        return new MediaCapabilities(Set.of());
    }

    public static MediaCapabilities of(MediaAction... actions) {
        EnumSet<MediaAction> set = EnumSet.noneOf(MediaAction.class);
        if (actions != null) {
            for (MediaAction a : actions) {
                set.add(a);
            }
        }
        return new MediaCapabilities(set);
    }

    public boolean supports(MediaAction action) {
        return supported.contains(action);
    }
}

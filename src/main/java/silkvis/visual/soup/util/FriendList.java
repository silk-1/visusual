package silkvis.visual.soup.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of SoupVisuals {@code padej.soup.api.repository.friend.FriendUtils} (Padej_).
 *
 * <p>The original keeps a flat name list and asks it by name, not by UUID: a friend is
 * matched on the display name, exactly like a "friends" file of a PvP client. The port keeps
 * that rule unchanged -- {@link #isFriend(String)} is case-sensitive, and the list is empty
 * on a fresh install, so no module treats anybody as a friend until the player adds one.</p>
 *
 * <p>Deviation: the original's {@code Friend} wrapper object is dropped; only the names are
 * stored, because nothing in the ported modules reads the rest of it. The list is
 * {@code synchronized} because the settings screen may edit it from the render thread while
 * a module iterates it in the world render pass.</p>
 */
public final class FriendList {
    private static final List<String> FRIENDS = new ArrayList<>();

    private FriendList() {
    }

    public static void addFriend(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        synchronized (FRIENDS) {
            if (!FRIENDS.contains(name)) {
                FRIENDS.add(name);
            }
        }
    }

    public static void removeFriend(String name) {
        if (name == null) {
            return;
        }
        synchronized (FRIENDS) {
            FRIENDS.removeIf(friend -> friend.equalsIgnoreCase(name));
        }
    }

    /** Original {@code FriendUtils.isFriend(String)}: exact name match. */
    public static boolean isFriend(String name) {
        if (name == null) {
            return false;
        }
        synchronized (FRIENDS) {
            return FRIENDS.contains(name);
        }
    }

    public static void clear() {
        synchronized (FRIENDS) {
            FRIENDS.clear();
        }
    }

    public static List<String> getFriends() {
        synchronized (FRIENDS) {
            return new ArrayList<>(FRIENDS);
        }
    }
}

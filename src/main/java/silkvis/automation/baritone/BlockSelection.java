package silkvis.automation.baritone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The blocks the player picked for "mine blocks", in pick order.
 *
 * <p>Minecraft-free on purpose: {@code mine} takes a list of block ids, the screen needs a
 * filter box, and both are easier to get right when they can be checked without a game. The
 * registry of real block ids is passed in by the caller ({@code BuiltinRegistries.BLOCK}), so
 * this class never touches {@code net.minecraft}.</p>
 *
 * <p>Order is the order the player picked, not alphabetical: the first block is the one they
 * wanted, and Baritone mines in the order it finds them, so the list is the useful record.</p>
 */
public final class BlockSelection {
    /** Baritone's {@code mine} takes a quantity first; 0 means "until stopped". */
    public static final int UNLIMITED = 0;

    private final Set<String> picked = new LinkedHashSet<>();

    /** Adds one block id, ignoring blanks and duplicates. Returns true when it was new. */
    public boolean add(String id) {
        String clean = clean(id);
        return clean != null && this.picked.add(clean);
    }

    /** Adds a whole filter result. */
    public int addAll(Collection<String> ids) {
        int added = 0;
        for (String id : ids) {
            if (add(id)) {
                added++;
            }
        }
        return added;
    }

    /** Removes one block id. */
    public boolean remove(String id) {
        String clean = clean(id);
        return clean != null && this.picked.remove(clean);
    }

    /** Empties the selection. */
    public void clear() {
        this.picked.clear();
    }

    public boolean contains(String id) {
        String clean = clean(id);
        return clean != null && this.picked.contains(clean);
    }

    public boolean isEmpty() {
        return this.picked.isEmpty();
    }

    public int size() {
        return this.picked.size();
    }

    /** The picked ids, in pick order. */
    public List<String> ids() {
        return new ArrayList<>(this.picked);
    }

    /**
     * The {@code mine} command for this selection, or {@code null} when nothing is picked.
     *
     * <p>Baritone's syntax is {@code mine <quantity> <block> [<block> ...]}; the quantity comes
     * first, so an empty argument list would be read as a quantity and fail. Returning null here
     * is what keeps the button disabled instead of sending {@code mine 0}.</p>
     */
    public String mineCommand(int quantity) {
        if (this.picked.isEmpty()) {
            return null;
        }
        StringBuilder line = new StringBuilder("mine ");
        line.append(Math.max(UNLIMITED, quantity));
        for (String id : this.picked) {
            line.append(' ').append(id);
        }
        return line.toString();
    }

    /** Strips whitespace, lowercases and drops a blank; ids are namespace:path. */
    private static String clean(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Filters block ids for the search box.
     *
     * <p>Matches anywhere in the id, so "ore" finds every ore and "minecraft:iron" finds the
     * iron family, which is how a player thinks about blocks. A query that starts with the
     * namespace is kept whole; otherwise the minecraft prefix is hidden in the results the
     * screen shows, so the player types "stone", not "minecraft:stone".</p>
     *
     * @param all   every block id the game knows, in registry order
     * @param query what the player typed; blank returns the first {@code limit} ids
     * @param limit how many to return, so the list stays a list and not a wall
     */
    public static List<String> filter(Collection<String> all, String query, int limit) {
        List<String> out = new ArrayList<>();
        if (all == null || limit <= 0) {
            return out;
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (String id : all) {
            if (id == null) {
                continue;
            }
            String lower = id.toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !lower.contains(needle)) {
                continue;
            }
            out.add(id);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** The short form shown on screen: the namespace is dropped when it is minecraft. */
    public static String display(String id) {
        if (id == null) {
            return "";
        }
        String prefix = "minecraft:";
        return id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }

    /** The full id for a short form typed by the player. */
    public static String qualify(String typed) {
        String clean = clean(typed);
        if (clean == null) {
            return null;
        }
        return clean.indexOf(':') < 0 ? "minecraft:" + clean : clean;
    }
}

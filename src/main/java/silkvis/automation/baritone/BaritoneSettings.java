package silkvis.automation.baritone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A short list of Baritone settings worth exposing in the UI, with their accepted values.
 *
 * <p>Why a fixed list and not all 400+: Baritone's own {@code set list} already shows every
 * setting, and copying that into our screen would be a worse version of what already exists.
 * The list here is the handful a player actually reaches for while automating -- may I break
 * blocks, may I place them, may I sprint, how far can I reach -- plus the knobs whose wrong
 * default surprises people (item saver, free look).</p>
 *
 * <p>Every name is Baritone's exact setting name, verified against the {@code 1.21.10} branch
 * sources, because {@code set <name> <value>} silently does nothing useful for a name Baritone
 * does not know. The screen sends the name verbatim; this class never renames or abbreviates.</p>
 */
public final class BaritoneSettings {
    /** The value type, which decides how the screen renders the control. */
    public enum Kind {
        /** True/false. Rendered as a toggle, sent as {@code true}/{@code false}. */
        BOOLEAN,
        /** A whole number in a fixed range. Rendered as a slider, sent as a decimal string. */
        INTEGER
    }

    /**
     * One exposed setting.
     *
     * @param name     Baritone's exact setting name, e.g. {@code allowBreak}
     * @param kind     boolean or integer
     * @param min      lowest accepted value for an integer, ignored for booleans
     * @param max      highest accepted value for an integer, ignored for booleans
     * @param langKey  description key for the RU/EN lang files
     */
    public record Spec(String name, Kind kind, int min, int max, String langKey) {
        /** The value as Baritone expects it on the command line. */
        public String encode(boolean bool) {
            return bool ? "true" : "false";
        }

        /** Clamps an integer into the accepted range before it is sent. */
        public String encode(int value) {
            int clamped = Math.max(this.min, Math.min(this.max, value));
            return Integer.toString(clamped);
        }
    }

    /**
     * The exposed settings, in the order the screen shows them.
     *
     * <p>Ordering is deliberate: the three "what am I allowed to do" switches first, because
     * they are the ones a player changes before a task, then the reach and safety numbers.</p>
     */
    private static final List<Spec> SPECS = List.of(
            new Spec("allowBreak", Kind.BOOLEAN, 0, 0, "silkvis.baritone.set.allowBreak"),
            new Spec("allowPlace", Kind.BOOLEAN, 0, 0, "silkvis.baritone.set.allowPlace"),
            new Spec("allowSprint", Kind.BOOLEAN, 0, 0, "silkvis.baritone.set.allowSprint"),
            new Spec("allowParkour", Kind.BOOLEAN, 0, 0, "silkvis.baritone.set.allowParkour"),
            new Spec("autoTool", Kind.BOOLEAN, 0, 0, "silkvis.baritone.set.autoTool"),
            new Spec("itemSaver", Kind.BOOLEAN, 0, 0, "silkvis.baritone.set.itemSaver"),
            new Spec("freeLook", Kind.BOOLEAN, 0, 0, "silkvis.baritone.set.freeLook"),
            new Spec("renderPath", Kind.BOOLEAN, 0, 0, "silkvis.baritone.set.renderPath"),
            new Spec("blockReachDistance", Kind.INTEGER, 1, 10,
                    "silkvis.baritone.set.blockReachDistance"),
            new Spec("maxFallHeightNoWater", Kind.INTEGER, 0, 10,
                    "silkvis.baritone.set.maxFallHeightNoWater"));

    private BaritoneSettings() {
    }

    /** All exposed settings, in display order. */
    public static List<Spec> specs() {
        return SPECS;
    }

    /** The spec with this Baritone name, or {@code null} when it is not one we expose. */
    public static Spec byName(String name) {
        if (name == null) {
            return null;
        }
        for (Spec spec : SPECS) {
            if (spec.name().equals(name)) {
                return spec;
            }
        }
        return null;
    }

    /** The {@code set} command line for one value. */
    public static String command(Spec spec, String value) {
        return spec == null || value == null ? null : "set " + spec.name() + " " + value;
    }

    /**
     * Values as last read back from Baritone, keyed by setting name.
     *
     * <p>Baritone is the source of truth: the player can change a setting from chat, so this
     * map starts empty and is filled from a {@code set} reply when one is available. Until then
     * the screen shows the value it sent, which is the honest thing to show -- not a guess at
     * Baritone's default.</p>
     */
    public static final class Values {
        private final Map<String, String> known = new LinkedHashMap<>();

        /** Records a value the user set or Baritone reported. */
        public void put(String name, String value) {
            if (name != null && value != null) {
                this.known.put(name, value);
            }
        }

        /** The known value, or {@code null} when nothing is known yet. */
        public String get(String name) {
            return this.known.get(name);
        }

        /** True when the known value is a boolean that is on. */
        public boolean isOn(String name) {
            String value = this.known.get(name);
            return value != null && value.toLowerCase(Locale.ROOT).equals("true");
        }

        /** The known value as an integer, or the fallback when nothing usable is stored. */
        public int asInt(String name, int fallback) {
            String value = this.known.get(name);
            if (value == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        /** Names currently known, for the checks and the log. */
        public List<String> names() {
            return new ArrayList<>(this.known.keySet());
        }
    }
}

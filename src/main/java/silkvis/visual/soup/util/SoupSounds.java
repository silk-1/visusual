package silkvis.visual.soup.util;

/**
 * Port of the sound half of SoupVisuals {@code padej.soup.api.system.sound.SoundManager}
 * (Padej_).
 *
 * <p>The original registers every custom sound as {@code minecraft:&lt;name&gt;} in the
 * {@code SOUND_EVENT} registry and plays it with
 * {@code client.getSoundManager().play(PositionedSoundInstance.master(event, pitch, volume))}.
 * The port keeps the same two steps -- the ids live in {@link SoundRegistry}, which the client
 * layer registers once, and {@link #play(String, float, float)} does the same master play.
 * The class only holds <em>ids</em> so it stays free of game imports; the client layer turns
 * them into {@code SoundEvent}s.</p>
 *
 * <p>As in the original, playback is scheduled on the client thread when called from another
 * thread, volume is clamped to {@code >= 0} and pitch to {@code >= 0.01}, both from the same
 * reasoning: a {@code 0} pitch would silence the sound instead of playing it slowly.</p>
 */
public final class SoupSounds {
    /** Suffix of every custom hit sound, exactly as the original names them. */
    public static final String[] HIT_TYPES = {
            "trinket_hit", "8bit", "glass", "drum_bass", "aimbooster_hit", "plastic_ball",
            "blip", "pop", "bell", "bonk", "bubble", "get", "magic_pok", "off", "on", "squish"
    };

    public static final String KILL_ABMISS = "kill_sounds/abmiss";
    public static final String KILL_CRITOW = "kill_sounds/critow";
    public static final String KILL_FINAL_BLINK = "kill_sounds/final_blink";
    public static final String KILL_FINAL_POK = "kill_sounds/final_pok";
    public static final String KILL_NEPTUNE = "kill_sounds/neptune";
    public static final String KILL_RUST_HEADSHOT = "kill_sounds/rust_headshot";
    public static final String KILL_WHII = "kill_sounds/whii";

    /** The seven kill sounds in the option order of the original's settings screen. */
    public static final String[] KILL_SOUNDS = {
            KILL_ABMISS, KILL_CRITOW, KILL_FINAL_BLINK, KILL_FINAL_POK, KILL_NEPTUNE,
            KILL_RUST_HEADSHOT, KILL_WHII
    };

    /** Every registered path: the sixteen hit sounds plus the seven kill sounds. */
    public static final String[] ALL_PATHS = {
            "trinket_hit", "8bit", "glass", "drum_bass", "aimbooster_hit", "plastic_ball",
            "blip", "pop", "bell", "bonk", "bubble", "get", "magic_pok", "off", "on", "squish",
            KILL_ABMISS, KILL_CRITOW, KILL_FINAL_BLINK, KILL_FINAL_POK, KILL_NEPTUNE,
            KILL_RUST_HEADSHOT, KILL_WHII
    };

    public static final String TRINKET_HIT = "trinket_hit";
    public static final String TRINKET_8BIT = "8bit";
    public static final String TRINKET_GLASS = "glass";
    public static final String TRINKET_DRUM_BASS = "drum_bass";
    public static final String TRINKET_AIMBOOSTER = "aimbooster_hit";
    public static final String TRINKET_PLASTIC = "plastic_ball";
    public static final String TRINKET_BLIP = "blip";
    public static final String TRINKET_POP = "pop";
    public static final String TRINKET_BELL = "bell";
    public static final String TRINKET_BONK = "bonk";
    public static final String TRINKET_BUBBLE = "bubble";
    public static final String TRINKET_GET = "get";
    public static final String TRINKET_MAGIC_POK = "magic_pok";
    public static final String TRINKET_OFF = "off";
    public static final String TRINKET_ON = "on";
    public static final String TRINKET_SQUISH = "squish";

    /** Where playback goes. Installed by the client layer, which owns the sound manager. */
    public interface Player {
        void play(String soundPath, float volume, float pitch);
    }

    private static volatile Player player;

    private SoupSounds() {
    }

    public static void setPlayer(Player soundPlayer) {
        player = soundPlayer;
    }

    /** Original {@code SoundManager.playSound(sound, volume, pitch)}. */
    public static void play(String soundPath, float volume, float pitch) {
        Player sink = player;
        if (sink == null || soundPath == null) {
            return;
        }
        sink.play(soundPath, Math.max(0.0F, volume), Math.max(0.01F, pitch));
    }

    /** Original {@code SoundManager.playSound(sound)}: full volume, default pitch. */
    public static void play(String soundPath) {
        play(soundPath, 1.0F, 1.0F);
    }
}

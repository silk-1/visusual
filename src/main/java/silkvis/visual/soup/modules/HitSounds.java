package silkvis.visual.soup.modules;

import silkvis.visual.soup.module.SoupModule;
import silkvis.visual.soup.module.SoupModuleCategory;
import silkvis.visual.soup.setting.BooleanSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.ValueSetting;
import silkvis.visual.soup.util.SoupSounds;

/**
 * HitSounds -- a custom click/trinket sound on every hit, plus volume control for the vanilla
 * sweep / crit / knockback sounds.
 *
 * <p>Ported from SoupVisuals 1.21.11
 * {@code padej.soup.implement.features.modules.other.HitSounds} (Padej_, SOUP-1.0, used by
 * written permission). Settings, groups and defaults are the original's.</p>
 *
 * <p><b>What was ported:</b> the whole module -- the sixteen trinket sounds, the
 * {@code Only Crits} filter with the vanilla crit check (cooldown at 84.8 %, falling, not on
 * ground, not climbing, not in water, no blindness, not sprinting, no vehicle), and the
 * three vanilla-sound volume multipliers.</p>
 *
 * <p><b>Difficulties:</b> two parts of the original are mixin-based and therefore rebuilt on
 * public hooks (ADR-0002):</p>
 * <ul>
 *   <li>the pre-attack half of its {@code EventAttack} -- SilkVis only has the post-attack
 *       {@code AttackEntityCallback}, so the crit flag is evaluated in the same callback right
 *       before the hit is reported, which is the closest public equivalent of "pre";</li>
 *   <li>the vanilla volume multipliers, which the original applies by modifying the volume
 *       argument of {@code ClientWorld#playSound}. There is no public hook for that, so the
 *       three sliders are stored and shown but <em>not applied</em> yet -- this is recorded in
 *       {@code docs/known-limitations.md} instead of faking it.</li>
 * </ul>
 *
 * <p><b>To verify in game:</b> that every one of the sixteen sounds plays at the right volume
 * and pitch, that {@code Only Crits} stays silent on a non-crit hit, and that the module does
 * nothing while disabled.</p>
 */
public class HitSounds extends SoupModule {
    public final BooleanSetting enableCustomSound = new BooleanSetting(
            "setting.hitsounds.enablecustomsound.name", "setting.hitsounds.enablecustomsound.desc")
            .setValue(false);
    public final SelectSetting soundType = new SelectSetting(
            "setting.hitsounds.soundtype.name", "setting.hitsounds.soundtype.desc")
            .value("Hit", "8Bit", "Glass", "Drum Bass", "AimBooster", "Plastic", "Blip", "Pop",
                    "Bell", "Bonk", "Bubble", "Get", "Magic Pok", "Off", "On", "Squish")
            .selected("Hit")
            .visible(this.enableCustomSound::isValue);
    public final ValueSetting volume = new ValueSetting(
            "setting.hitsounds.volume.name", "setting.hitsounds.volume.desc")
            .setValue(1.0F)
            .range(0.1F, 2.0F)
            .visible(this.enableCustomSound::isValue);
    public final ValueSetting pitch = new ValueSetting(
            "setting.hitsounds.pitch.name", "setting.hitsounds.pitch.desc")
            .setValue(1.0F)
            .range(0.5F, 2.0F)
            .visible(this.enableCustomSound::isValue);
    public final BooleanSetting onlyCrits = new BooleanSetting(
            "setting.hitsounds.onlycrits.name", "setting.hitsounds.onlycrits.desc")
            .setValue(false)
            .visible(this.enableCustomSound::isValue);
    public final ValueSetting sweepVolume = new ValueSetting(
            "setting.hitsounds.sweepvolume.name", "setting.hitsounds.sweepvolume.desc")
            .setValue(1.0F)
            .range(0.0F, 2.0F);
    public final ValueSetting critVolume = new ValueSetting(
            "setting.hitsounds.critvolume.name", "setting.hitsounds.critvolume.desc")
            .setValue(1.0F)
            .range(0.0F, 2.0F);
    public final ValueSetting knockbackVolume = new ValueSetting(
            "setting.hitsounds.knockbackvolume.name", "setting.hitsounds.knockbackvolume.desc")
            .setValue(1.0F)
            .range(0.0F, 2.0F);

    private boolean wasLastAttackCrit;

    public HitSounds() {
        super("module.hitsounds.name", SoupModuleCategory.OTHER);
        GroupSetting customSoundGroup = new GroupSetting("group.hitsounds.customsound.name",
                "group.hitsounds.customsound.desc", false)
                .settings(this.enableCustomSound, this.soundType, this.volume, this.pitch,
                        this.onlyCrits);
        GroupSetting vanillaGroup = new GroupSetting("group.hitsounds.vanilla.name",
                "group.hitsounds.vanilla.desc", false)
                .settings(this.sweepVolume, this.critVolume, this.knockbackVolume);
        this.setup(customSoundGroup, vanillaGroup);
    }

    /**
     * Vanilla crit rule of the original {@code isCrit()}.
     *
     * @param attackCooldownProgress {@code player.getAttackCooldownProgress(0.5F)}
     * @param fallDistance           {@code player.fallDistance}
     */
    public static boolean isCrit(float attackCooldownProgress, double fallDistance, boolean onGround,
                                 boolean climbing, boolean touchingWater, boolean blindness,
                                 boolean sprinting, boolean hasVehicle) {
        boolean hasEnoughCooldown = attackCooldownProgress >= 0.848F;
        return hasEnoughCooldown
                && fallDistance > 0.0
                && !onGround
                && !climbing
                && !touchingWater
                && !blindness
                && !sprinting
                && !hasVehicle;
    }

    /**
     * Attack hook. The original evaluates the crit in the pre half of its event and plays the
     * sound in the post half; both halves are called here in that order from the one public
     * callback.
     *
     * @param hit {@code true} when the player actually hit an entity (not a miss)
     */
    public void onAttack(boolean hit, boolean crit) {
        if (!this.isEnabled()) {
            return;
        }
        this.wasLastAttackCrit = crit;
        if (!hit || !this.enableCustomSound.isValue()) {
            return;
        }
        if (this.onlyCrits.isValue() && !this.wasLastAttackCrit) {
            return;
        }
        SoupSounds.play(soundPath(this.soundType.getSelected()), this.volume.getValue(),
                this.pitch.getValue());
    }

    /** Option name -> sound file, exactly the original's switch. */
    static String soundPath(String selected) {
        return switch (selected) {
            case "8Bit" -> SoupSounds.TRINKET_8BIT;
            case "Glass" -> SoupSounds.TRINKET_GLASS;
            case "Drum Bass" -> SoupSounds.TRINKET_DRUM_BASS;
            case "AimBooster" -> SoupSounds.TRINKET_AIMBOOSTER;
            case "Plastic" -> SoupSounds.TRINKET_PLASTIC;
            case "Blip" -> SoupSounds.TRINKET_BLIP;
            case "Pop" -> SoupSounds.TRINKET_POP;
            case "Bell" -> SoupSounds.TRINKET_BELL;
            case "Bonk" -> SoupSounds.TRINKET_BONK;
            case "Bubble" -> SoupSounds.TRINKET_BUBBLE;
            case "Get" -> SoupSounds.TRINKET_GET;
            case "Magic Pok" -> SoupSounds.TRINKET_MAGIC_POK;
            case "Off" -> SoupSounds.TRINKET_OFF;
            case "On" -> SoupSounds.TRINKET_ON;
            case "Squish" -> SoupSounds.TRINKET_SQUISH;
            default -> SoupSounds.TRINKET_HIT;
        };
    }

    public boolean wasLastAttackCrit() {
        return this.wasLastAttackCrit;
    }
}

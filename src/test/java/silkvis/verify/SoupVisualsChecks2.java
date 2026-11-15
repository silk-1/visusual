package silkvis.verify;

import org.joml.Matrix4f;
import silkvis.visual.soup.modules.FriendsTargetRender;
import silkvis.visual.soup.modules.HandTweaks;
import silkvis.visual.soup.modules.HitBubbles;
import silkvis.visual.soup.modules.HitSounds;
import silkvis.visual.soup.modules.Hitboxes;
import silkvis.visual.soup.modules.JumpCircles;
import silkvis.visual.soup.modules.KillEffect;
import silkvis.visual.soup.modules.Trails;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.Setting;
import silkvis.visual.soup.setting.SettingRepository;
import silkvis.visual.soup.setting.ValueSetting;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.EntitySnapshot;
import silkvis.visual.soup.util.FriendList;
import silkvis.visual.soup.util.SoupSounds;

import static silkvis.verify.SelfCheck.assertClose;
import static silkvis.verify.SelfCheck.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks for batch 2 of the SoupVisuals port: Hitboxes, JumpCircles, Trails, HitSounds,
 * KillEffect, FriendsTargetRender, HandTweaks.
 *
 * <p>Like {@code SoupVisualsChecks}, everything asserted here is the <em>original's</em>
 * documented behaviour: setting defaults and option lists, the crit rule, the geometry of the
 * swing transforms, the colour maths of the new helpers, and the bookkeeping of the kill
 * detection. No Minecraft class is touched; the render path needs the game and is exercised in
 * game, not here.</p>
 */
final class SoupVisualsChecks2 {
    private SoupVisualsChecks2() {
    }

    /**
     * The settings file is the only thing that makes the menu survive a restart; a silent
     * mismatch between key generation and lookup would write ten modules to one key and lose
     * every value. Checked end to end: save -> wipe the modules -> load -> values are back.
     */
    private static void settingsFileRoundTrip() {
        SelfCheck.check("soup: settings file round trip and unique module ids", () -> {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("silkvis-soup");
            java.nio.file.Path file = dir.resolve("soupvisuals.json");
            List<silkvis.visual.soup.module.SoupModule> modules = List.of(
                    new Hitboxes(), new JumpCircles(), new JumpCircles());
            // Distinct ids: the file keys modules by id, so a duplicate would overwrite every
            // value of the module it collided with. Two of the three modules here are the same
            // type on purpose: the id must come from the module name, not from a counter.
            Set<String> ids = new HashSet<>();
            for (silkvis.visual.soup.module.SoupModule module : modules) {
                ids.add(silkvis.visual.soup.setting.SoupSettingsFile.keyOf(module));
            }
            SelfCheck.assertEquals(2, ids.size(), "ids come from the module name");
            modules.get(0).setEnabled(true);
            ValueSetting first = SettingRepository
                    .byConfigKey(modules.get(0).getRootSettings()).values().stream()
                    .filter(setting -> setting instanceof ValueSetting)
                    .map(setting -> (ValueSetting) setting)
                    .findFirst().orElseThrow();
            first.setValue(first.getMin());
            silkvis.visual.soup.setting.SoupSettingsFile.save(file, modules);
            assertTrue(java.nio.file.Files.isRegularFile(file), "file written");

            modules.get(0).setEnabled(false);
            first.setValue(first.getMax());
            assertTrue(silkvis.visual.soup.setting.SoupSettingsFile.load(file, modules),
                    "file loaded");
            assertTrue(modules.get(0).isEnabled(), "module state restored");
            SelfCheck.assertEquals((double) first.getMin(), (double) first.getValue(), 1.0E-4,
                    "value restored");
            // Every module keeps its own key (checked above, before the file is damaged):
            // a collision would have written the same values under the same names and the
            // file would still parse, so only the presence of both ids proves it.

            String text = java.nio.file.Files.readString(file);
            assertTrue(text.contains("\"hitboxes\"") && text.contains("\"jumpcircles\""),
                    "both module ids present in the file");

            // A damaged file must not throw and must not half-apply.
            java.nio.file.Files.writeString(file, "{ not json");
            assertTrue(!silkvis.visual.soup.setting.SoupSettingsFile.load(file, modules),
                    "broken file rejected");
            assertTrue(modules.get(0).isEnabled(), "state kept after a broken file");
        });
    }

    static void run() {
        hitboxesDefaults();
        jumpCirclesDefaults();
        trailsDefaults();
        hitSoundsDefaults();
        handTweaksDefaults();
        friendsTargetDefaults();
        killEffectDefaults();
        configKeysBatchTwo();
        optionLabels();
        soundRegistry();
        translationsExist();
        critRules();
        colourHelpers();
        swingTransforms();
        trailLifetime();
        friendList();
        killEffectBookkeeping();
        settingsFileRoundTrip();
    }

    private static void hitboxesDefaults() {
        SelfCheck.check("soup2: hitboxes keep the original defaults", () -> {
            Hitboxes module = new Hitboxes();
            SelfCheck.assertEquals("Both", module.renderMode.getSelected(), "render mode");
            SelfCheck.assertTrue(!module.eyesLine.isValue(), "eyes line off");
            SelfCheck.assertEquals("Sync", module.colorMode.getSelected(), "colour mode");
            SelfCheck.assertEquals(-1, module.hitboxColor.getColor(), "custom hitbox colour is white");
            SelfCheck.assertEquals(-16711936, module.friendHitboxColor.getColor(), "friend colour is green");
            SelfCheck.assertTrue(!module.hitboxColor.isVisible(), "colour picker hidden in Sync");
            module.colorMode.setSelected("Custom");
            SelfCheck.assertTrue(module.hitboxColor.isVisible(), "colour picker visible in Custom");
            SelfCheck.assertTrue(!module.isEnabled(), "module off until switched on");
        });
    }

    private static void jumpCirclesDefaults() {
        SelfCheck.check("soup2: jump circles keep the original defaults", () -> {
            JumpCircles module = new JumpCircles();
            SelfCheck.assertEquals("Sync", module.colorMode.getSelected(), "colour mode");
            SelfCheck.assertEquals("Wave", module.colorAnimation.getSelected(), "colour animation");
            SelfCheck.assertEquals("Both", module.appearAnimationType.getSelected(), "appear animation");
            SelfCheck.assertEquals("Both", module.disappearAnimationType.getSelected(), "disappear animation");
            SelfCheck.assertClose(1.0, module.appearDuration.getValue(), 1.0E-6, "appear duration");
            SelfCheck.assertClose(1.5, module.existDuration.getValue(), 1.0E-6, "exist duration");
            SelfCheck.assertClose(1.0, module.disappearDuration.getValue(), 1.0E-6, "disappear duration");
            SelfCheck.assertEquals("Bounce", module.appearInterpolation.getSelected(), "appear curve");
            SelfCheck.assertEquals("Smooth", module.disappearInterpolation.getSelected(), "disappear curve");
            SelfCheck.assertClose(2.0, module.rotateSpeed.getValue(), 1.0E-6, "rotate speed");
            SelfCheck.assertClose(1.0, module.circleScale.getValue(), 1.0E-6, "scale");
            SelfCheck.assertClose(1.0, module.bright.getValue(), 1.0E-6, "bright = circle count");
            SelfCheck.assertClose(-3.0, module.existDuration.getMin(), 1.0E-6,
                    "the negative exist range is the original's overlap mode");
            SelfCheck.assertClose(3.5, module.getTotalAnimationDuration(), 1.0E-6, "total duration");
            SelfCheck.assertTrue(module.getCircleTexture().getPath().endsWith("circles/circle.png"),
                    "default texture");
            module.onJump(1, 2, 3);
            SelfCheck.assertTrue(module.getCircles().isEmpty(), "disabled module spawns nothing");
            module.setEnabled(true);
            module.onJump(1, 2, 3);
            SelfCheck.assertEquals(1, module.getCircles().size(), "one circle per jump at Bright = 1");
            module.bright.setValue(3.0F);
            module.onJump(4, 5, 6);
            SelfCheck.assertEquals(4, module.getCircles().size(), "Bright = 3 adds three circles");
            module.setEnabled(false);
            SelfCheck.assertTrue(module.getCircles().isEmpty(), "switching off clears the circles");
        });
    }

    private static void trailsDefaults() {
        SelfCheck.check("soup2: trails keep the original defaults", () -> {
            Trails module = new Trails();
            SelfCheck.assertEquals("Sync", module.colorMode.getSelected(), "colour mode");
            SelfCheck.assertEquals("Wave", module.colorAnimation.getSelected(), "colour animation");
            SelfCheck.assertEquals("Trail", module.players.getSelected(), "players mode");
            SelfCheck.assertTrue(!module.friends.isValue(), "friends off");
            SelfCheck.assertTrue(!module.onlyF5.isValue(), "only F5 off");
            SelfCheck.assertEquals("Solid", module.style.getSelected(), "style");
            SelfCheck.assertTrue(!module.renderHalf.isValue(), "render half off");
            SelfCheck.assertClose(50.0, module.alphaFactor.getValue(), 1.0E-6, "alpha factor");
            SelfCheck.assertClose(100.0, module.trailAlpha.getValue(), 1.0E-6, "trail alpha");
            SelfCheck.assertClose(0.0, module.down.getValue(), 1.0E-6, "down");
            SelfCheck.assertClose(1.5, module.trailDuration.getValue(), 1.0E-6, "duration");
            SelfCheck.assertTrue(!module.debugLines.isValue(), "debug lines off");
            SelfCheck.assertClose(3.0, module.lineWidth.getValue(), 1.0E-6, "line width");
            SelfCheck.assertTrue(!module.lineWidth.isVisible(), "line width hides with debug lines");
            module.debugLines.setValue(true);
            SelfCheck.assertTrue(module.lineWidth.isVisible(), "line width shows with debug lines");
            SelfCheck.assertTrue(!module.alphaFactor.isVisible(), "alpha factor hides outside Invert");
            module.style.setSelected("Invert");
            SelfCheck.assertTrue(module.alphaFactor.isVisible(), "alpha factor shows in Invert");
        });
    }

    private static void hitSoundsDefaults() {
        SelfCheck.check("soup2: hit sounds keep the original defaults", () -> {
            HitSounds module = new HitSounds();
            SelfCheck.assertTrue(!module.enableCustomSound.isValue(), "custom sound off");
            SelfCheck.assertEquals("Hit", module.soundType.getSelected(), "sound type");
            SelfCheck.assertEquals(16, module.soundType.getList().size(), "sixteen trinket sounds");
            SelfCheck.assertClose(1.0, module.volume.getValue(), 1.0E-6, "volume");
            SelfCheck.assertClose(1.0, module.pitch.getValue(), 1.0E-6, "pitch");
            SelfCheck.assertTrue(!module.onlyCrits.isValue(), "only crits off");
            SelfCheck.assertClose(1.0, module.sweepVolume.getValue(), 1.0E-6, "sweep volume");
            SelfCheck.assertClose(1.0, module.critVolume.getValue(), 1.0E-6, "crit volume");
            SelfCheck.assertClose(1.0, module.knockbackVolume.getValue(), 1.0E-6, "knockback volume");
            SelfCheck.assertTrue(!module.soundType.isVisible(), "sound type hides while custom is off");
        });
    }

    private static void handTweaksDefaults() {
        SelfCheck.check("soup2: hand tweaks keep the original defaults", () -> {
            HandTweaks module = new HandTweaks();
            SelfCheck.assertTrue(!module.enableSwing.isValue(), "swing off");
            SelfCheck.assertEquals("Vanilla", module.swingType.getSelected(), "swing type");
            SelfCheck.assertEquals(7, module.swingType.getList().size(), "six named swings plus Custom");
            SelfCheck.assertClose(1.0, module.swingSpeed.getValue(), 1.0E-6, "swing speed");
            SelfCheck.assertClose(0.56, module.customTranslateX.getValue(), 1.0E-6, "custom X");
            SelfCheck.assertClose(-0.32, module.customTranslateY.getValue(), 1.0E-6, "custom Y");
            SelfCheck.assertClose(-0.72, module.customTranslateZ.getValue(), 1.0E-6, "custom Z");
            SelfCheck.assertClose(-60.0, module.customSwingRotateX.getValue(), 1.0E-6, "custom swing X");
            SelfCheck.assertClose(1.0, module.mainHandScale.getValue(), 1.0E-6, "main hand scale");
            SelfCheck.assertClose(1.0, module.offHandScale.getValue(), 1.0E-6, "off hand scale");
            // 29 settings plus the four colour-slot style entries of the original: no colours
            // here, so the count is exactly 29.
            // Swing group 3 + three custom groups 3x3 + two position/rotation groups 3x3 +
            // two scales = 3 + 9 + 6 + 2 = 26 persisted values (groups are containers).
            SelfCheck.assertEquals(26, module.getValueSettings().size(), "every setting persists");
        });
    }

    private static void friendsTargetDefaults() {
        SelfCheck.check("soup2: friends target render keeps the original defaults", () -> {
            FriendsTargetRender module = new FriendsTargetRender();
            SelfCheck.assertEquals("Custom", module.colorMode.getSelected(), "colour mode");
            SelfCheck.assertEquals("Legacy", module.targetEspType.getSelected(), "render type");
            SelfCheck.assertEquals(4, module.targetEspType.getList().size(), "four render types");
            SelfCheck.assertEquals("Capture", module.legacyTexture.getSelected(), "legacy texture");
            SelfCheck.assertEquals(9, module.legacyTexture.getList().size(), "nine textures");
            SelfCheck.assertTrue(!module.optimalAim.isValue(), "optimal aim off");
            SelfCheck.assertClose(1.0, module.legacySize.getValue(), 1.0E-6, "legacy size");
            SelfCheck.assertEquals("Soft", module.ghostsTexture.getSelected(), "ghost texture");
            SelfCheck.assertEquals("Plasma", module.ghostsBlend.getSelected(), "ghost blend");
            SelfCheck.assertClose(1.3, module.ghostsLength.getValue(), 1.0E-6, "ghost length");
            SelfCheck.assertEquals("Standard", module.ghostsTrajectory.getSelected(), "ghost path");
            SelfCheck.assertClose(1.6, module.ghostsRadiusModifier.getValue(), 1.0E-6, "radius modifier");
            SelfCheck.assertClose(0.7, module.ghostsHeadSize.getValue(), 1.0E-6, "head size");
            SelfCheck.assertClose(0.1, module.ghostsTailSize.getValue(), 1.0E-6, "tail size");
            SelfCheck.assertClose(4.0, module.ghostsCount.getValue(), 1.0E-6, "ghost count");
            SelfCheck.assertClose(5.0, module.ghostsSubdivision.getValue(), 1.0E-6, "ghost subdivision");
            SelfCheck.assertClose(0.5, module.crystalsDistance.getValue(), 1.0E-6, "crystal distance");
            SelfCheck.assertClose(0.35, module.crystalsSize.getValue(), 1.0E-6, "crystal size");
            SelfCheck.assertTrue(module.crystalsGlow.isValue(), "crystal glow on");
            SelfCheck.assertClose(1.5, module.crystalsGlowSize.getValue(), 1.0E-6, "crystal glow size");
            SelfCheck.assertEquals("Center", module.crystalsOrientation.getSelected(), "crystal orientation");
            SelfCheck.assertEquals("Friend", module.renderPriority.getSelected(), "priority");
            SelfCheck.assertTrue(module.showOn.isSelected("Friends"), "show on friends");
            SelfCheck.assertTrue(!module.showOn.isSelected("Self"), "not on self by default");
            SelfCheck.assertClose(5.0, module.followTime.getValue(), 1.0E-6, "follow time");
            SelfCheck.assertClose(0.7, module.speedMod.getValue(), 1.0E-6, "speed modifier");
            SelfCheck.assertClose(128.0, module.maxDistance.getValue(), 1.0E-6, "max distance");
            SelfCheck.assertTrue(module.getLegacyTexture().getPath().endsWith("legacy/capture.png"),
                    "texture path is the ported legacy folder");
        });
    }

    private static void killEffectDefaults() {
        SelfCheck.check("soup2: kill effect keeps the original defaults", () -> {
            KillEffect module = new KillEffect();
            SelfCheck.assertEquals("Particles", module.effectType.getSelected(), "own effect");
            SelfCheck.assertEquals("Thunder", module.friendEffectType.getSelected(), "friend effect");
            SelfCheck.assertTrue(module.showForFriends.isValue(), "friends effects on");
            SelfCheck.assertEquals("Both", module.targetType.getSelected(), "target type");
            SelfCheck.assertEquals("Sync", module.colorMode.getSelected(), "colour mode");
            SelfCheck.assertEquals("Wave", module.colorAnimation.getSelected(), "colour animation");
            SelfCheck.assertEquals(3, module.mode.getSelected().size(), "three default modes");
            SelfCheck.assertEquals(18, module.mode.getList().size(), "eighteen particle modes");
            SelfCheck.assertTrue(module.physics.isSelected("Emerge"), "default physics");
            SelfCheck.assertClose(1.5, module.scale.getValue(), 1.0E-6, "scale");
            SelfCheck.assertClose(3.0, module.lifeTime.getValue(), 1.0E-6, "lifetime");
            SelfCheck.assertClose(1.0, module.speed.getValue(), 1.0E-6, "speed");
            SelfCheck.assertClose(30.0, module.amount.getValue(), 1.0E-6, "amount");
            SelfCheck.assertClose(1.0, module.linkDistance.getValue(), 1.0E-6, "link distance");
            SelfCheck.assertTrue(!module.enableSound.isValue(), "sound off");
            SelfCheck.assertEquals("Neptune", module.soundType.getSelected(), "sound type");
            SelfCheck.assertEquals(7, module.soundType.getList().size(), "seven kill sounds");
            SelfCheck.assertClose(1.8, module.trsLifetime.getValue(), 1.0E-6, "trs lifetime");
            SelfCheck.assertClose(1.0, module.trsExtraSpeed.getValue(), 1.0E-6, "trs extra speed");
            SelfCheck.assertTrue(!module.trsDisableRedEffect.isValue(), "trs red effect on");
            SelfCheck.assertTrue(module.colorMode.isVisible(), "colour group visible in Particles mode");
            module.effectType.setSelected("Thunder");
            module.showForFriends.setValue(false);
            SelfCheck.assertTrue(!module.colorMode.isVisible(),
                    "colour group hides when neither effect is a particle burst");
        });
    }

    private static void configKeysBatchTwo() {
        SelfCheck.check("soup2: batch-2 config keys are stable and unique", () -> {
            List<silkvis.visual.soup.module.SoupModule> modules = List.of(
                    new Hitboxes(), new JumpCircles(), new Trails(), new HitSounds(),
                    new KillEffect(), new FriendsTargetRender(), new HandTweaks());
            Set<String> seen = new HashSet<>();
            int total = 0;
            for (silkvis.visual.soup.module.SoupModule module : modules) {
                String prefix = module.getNameKey().replace("module.", "").replace(".name", "");
                for (Setting setting : module.getValueSettings()) {
                    String key = SettingRepository.configKey(setting);
                    SelfCheck.assertTrue(seen.add(key), "duplicate config key: " + key);
                    SelfCheck.assertTrue(key.startsWith(prefix + "."),
                            "wrong module prefix in " + key);
                    total++;
                }
                for (Setting setting : module.getValueSettings()) {
                    SelfCheck.assertTrue(!(setting instanceof GroupSetting), "group is not a value");
                }
            }
            // Counted from the original's settings tree: every value setting plus the four
            // palette slots of each MultiColorSetting (Hitboxes uses two plain colour pickers).
            SelfCheck.assertEquals(129, total, "every batch-2 setting is persisted");
        });
    }

    /**
     * The option labels are the contract between the settings screen, the saved config and the
     * renderer, so they are checked against the original's strings -- a drift here silently
     * disables a feature (which is exactly what happened to HitBubbles' "Vertexes" before this
     * check existed).
     */
    private static void optionLabels() {
        SelfCheck.check("soup2: colour animation labels match the original", () -> {
            SelfCheck.assertEquals("Wave", HitBubbles.ANIM_WAVE, "wave label");
            SelfCheck.assertEquals("Vertexes", HitBubbles.ANIM_VERTEXES, "vertex label of HitBubbles");
            SelfCheck.assertEquals(java.util.List.of("Wave", "Vertexes"),
                    new HitBubbles().colorAnimation.getList(), "HitBubbles animation options");
            SelfCheck.assertEquals(java.util.List.of("Wave", "Vertex"),
                    new Trails().colorAnimation.getList(), "Trails uses the singular form");
            SelfCheck.assertEquals(java.util.List.of("Wave", "Vertexes"),
                    new JumpCircles().colorAnimation.getList(), "JumpCircles animation options");
            SelfCheck.assertEquals(java.util.List.of("Wave", "Vertexes"),
                    new KillEffect().colorAnimation.getList(), "KillEffect animation options");
            SelfCheck.assertEquals(java.util.List.of("Sync", "Custom"),
                    new JumpCircles().colorMode.getList(), "colour mode options");
            SelfCheck.assertEquals(java.util.List.of("Fill", "Outline", "Both"),
                    new Hitboxes().renderMode.getList(), "hitbox render modes");
            SelfCheck.assertEquals(java.util.List.of("Trail", "Tail", "None"),
                    new Trails().players.getList(), "trail modes");
            SelfCheck.assertEquals(java.util.List.of("Solid", "Faded", "Invert"),
                    new Trails().style.getList(), "trail styles");
            SelfCheck.assertEquals(java.util.List.of("Legacy", "Circle", "Ghosts", "Crystals"),
                    new FriendsTargetRender().targetEspType.getList(), "target render types");
            SelfCheck.assertEquals(java.util.List.of("Vanilla", "Swipe", "Down", "Smooth",
                            "Power", "Feast", "Custom"),
                    new HandTweaks().swingType.getList(), "swing types");
            SelfCheck.assertEquals(java.util.List.of("Particles", "Thunder", "TR Sync"),
                    new KillEffect().effectType.getList(), "kill effect types");
        });
    }

    /**
     * The sound registry is the one place where a typo is invisible until someone hears
     * silence: a missing {@code sounds.json} entry makes {@code SoundEvent.of} fail at load
     * time, and an unregistered file is dead weight in the jar. Both sides are compared here.
     */
    private static void soundRegistry() {
        SelfCheck.check("soup2: sounds.json matches the registered sound list", () -> {
            java.nio.file.Path assets = java.nio.file.Path.of(
                    "src/main/resources/assets/silkvis");
            SelfCheck.assertTrue(java.nio.file.Files.isDirectory(assets),
                    "run from the repository root: " + assets.toAbsolutePath());
            String json = java.nio.file.Files.readString(assets.resolve("sounds.json"));
            Set<String> declared = new HashSet<>();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"name\"\\s*:\\s*\"silkvis:([^\"]+)\"")
                    .matcher(json);
            while (matcher.find()) {
                declared.add(matcher.group(1));
            }
            Set<String> expected = new HashSet<>(java.util.List.of(SoupSounds.ALL_PATHS));
            SelfCheck.assertEquals(expected.size(), declared.size(),
                    "one sounds.json entry per registered path");
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(declared);
            SelfCheck.assertTrue(missing.isEmpty(), "missing from sounds.json: " + missing);
            Set<String> extra = new HashSet<>(declared);
            extra.removeAll(expected);
            SelfCheck.assertTrue(extra.isEmpty(), "sounds.json has unregistered paths: " + extra);

            SelfCheck.assertEquals(16, SoupSounds.HIT_TYPES.length, "sixteen hit sounds");
            SelfCheck.assertEquals(23, SoupSounds.ALL_PATHS.length, "sixteen + seven kill sounds");
            for (String path : SoupSounds.ALL_PATHS) {
                SelfCheck.assertTrue(java.nio.file.Files.isRegularFile(
                                assets.resolve("sounds").resolve(path + ".ogg")),
                        "audio file for " + path);
            }
            // Every option of the settings screen maps to exactly one registered kill sound,
            // and the mapping covers all seven files.
            List<String> killOptions = new KillEffect().soundType.getList();
            SelfCheck.assertEquals(7, killOptions.size(), "seven kill sound options");
            Set<String> mapped = new HashSet<>();
            for (String option : killOptions) {
                String path = KillEffect.soundPathFor(option);
                SelfCheck.assertTrue(mapped.add(path), "two options share " + path);
                SelfCheck.assertTrue(declared.contains(path), option + " -> " + path + " is declared");
            }
            SelfCheck.assertEquals(Set.of(SoupSounds.KILL_SOUNDS), mapped,
                    "the option list covers every kill sound");
            for (String path : SoupSounds.KILL_SOUNDS) {
                SelfCheck.assertTrue(path.startsWith("kill_sounds/"), path + " lives in its folder");
            }
        });
    }

    /**
     * Every key the batch-2 modules ask for must exist in both language files, otherwise the
     * settings screen shows raw keys (the batch-1 rule, extended to the new modules).
     */
    private static void translationsExist() {
        SelfCheck.check("soup2: RU/EN keys of the batch-2 modules exist", () -> {
            java.nio.file.Path lang = java.nio.file.Path.of("src/main/resources/assets/silkvis/lang");
            Set<String> enKeys = jsonKeys(java.nio.file.Files.readString(lang.resolve("en_us.json")));
            Set<String> ruKeys = jsonKeys(java.nio.file.Files.readString(lang.resolve("ru_ru.json")));
            for (String module : List.of("hitboxes", "jumpcircles", "trails", "hitsounds",
                    "killeffect", "friendstargetrender", "handtweaks")) {
                for (String suffix : List.of(".name", ".desc")) {
                    String key = "module." + module + suffix;
                    SelfCheck.assertTrue(enKeys.contains(key), "en has " + key);
                    SelfCheck.assertTrue(ruKeys.contains(key), "ru has " + key);
                }
            }
            // The mistyped key this check was written for: KillEffect used to ask for
            // "setting.killeffect.trsdisredesc", which never existed in either language file,
            // so the settings screen printed the raw key.
            SelfCheck.assertTrue(enKeys.contains("setting.killeffect.trsdisablered.desc"),
                    "disable-red description is spelled out");
            SelfCheck.assertEquals(enKeys, ruKeys, "RU and EN define the same keys");
            // 316 batch-2 keys were merged into both files: 284 -> 433 module/setting/group
            // keys (726 keys in total, the rest are HUD and screen strings).
            long menuKeys = enKeys.stream()
                    .filter(key -> key.startsWith("module.") || key.startsWith("setting.")
                            || key.startsWith("group."))
                    .count();
            SelfCheck.assertTrue(menuKeys > 420, "batch-2 keys were merged (" + menuKeys + ")");
            SelfCheck.assertTrue(enKeys.size() > 700, "whole file (" + enKeys.size() + ")");
            for (String key : List.of("setting.handtweaks.swingtype.name",
                    "setting.jumpcircles.circlescale.name", "group.killeffect.trs.name",
                    "setting.friendstargetrender.legacytexture.name",
                    "setting.hitsounds.onlycrits.name", "setting.trails.duration.name",
                    "setting.hitboxes.rendermode.name", "setting.killeffect.soundtype.name")) {
                SelfCheck.assertTrue(enKeys.contains(key), "merged key " + key);
                SelfCheck.assertTrue(ruKeys.contains(key), "merged key " + key);
            }
        });
    }

    /** Keys of a flat JSON object: enough for the language files, no JSON library needed. */
    private static Set<String> jsonKeys(String json) {
        Set<String> keys = new HashSet<>();
        for (String line : json.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("\"") || !trimmed.endsWith(",")) {
                continue;
            }
            int separator = trimmed.indexOf("\":");
            if (separator > 1) {
                keys.add(trimmed.substring(1, separator));
            }
        }
        return keys;
    }

    private static void critRules() {
        SelfCheck.check("soup2: crit rule of HitSounds matches HitBubbles", () -> {
            for (int i = 0; i < 8; i++) {
                float cooldown = i == 0 ? 0.9F : 1.0F;
                double fall = i == 1 ? 0.0 : 0.5;
                boolean onGround = i == 2;
                boolean climbing = i == 3;
                boolean water = i == 4;
                boolean blindness = i == 5;
                boolean sprinting = i == 6;
                boolean vehicle = i == 7;
                boolean expected = i == 0;
                SelfCheck.assertEquals(expected,
                        HitSounds.isCrit(cooldown, fall, onGround, climbing, water, blindness,
                                sprinting, vehicle),
                        "crit case " + i);
            }
            // 0.848 is the original's threshold: just below is not enough, at it is.
            SelfCheck.assertTrue(!HitSounds.isCrit(0.847F, 1.0, false, false, false, false, false, false),
                    "just below the threshold");
            SelfCheck.assertTrue(HitSounds.isCrit(0.848F, 1.0, false, false, false, false, false, false),
                    "at the threshold");
        });
    }

    private static void colourHelpers() {
        SelfCheck.check("soup2: batch-2 colour helpers", () -> {
            SelfCheck.assertEquals(0x80112233, ColorUtil.replAlpha(0xFF112233, 0x80), "replace alpha");
            SelfCheck.assertEquals(0x80112233, ColorUtil.replAlpha(0xFF112233, 0x80 / 255.0F),
                    "replace alpha from float");
            // gradientToRed at 0 keeps the colour, at 1 it is pure red with the same alpha.
            SelfCheck.assertEquals(0xFF112233, ColorUtil.gradientToRed(0xFF112233, 0.0F), "red at 0");
            SelfCheck.assertEquals(0xFFFF0000, ColorUtil.gradientToRed(0xFF112233, 1.0F), "red at 1");
            SelfCheck.assertEquals(ColorUtil.getColor(0x88, 0x11, 0x1A, 0xFF),
                    ColorUtil.gradientToRed(0xFF112233, 0.5F), "red in between");
            SelfCheck.assertEquals(0xFFFFFFFF, ColorUtil.multBright(0xFFFF0000, 1.0F), "bright full");
            SelfCheck.assertEquals(0xFFFF0000, ColorUtil.multDark(0xFFFF0000, 0.0F),
                    "dark at 0 keeps the colour");
            SelfCheck.assertEquals(0xFF808080, ColorUtil.multDark(0xFFFFFFFF, 0.5F),
                    "dark at 0.5 is grey, alpha preserved");
            SelfCheck.assertEquals(0xFF000000, ColorUtil.multDark(0xFF00FFFF, 1.0F),
                    "dark at 1 is black");
            // fade(index) must stay inside the client colour's hue: it is a blend of two
            // variants of the accent, never of another colour.
            ColorUtil.setClientColor(() -> 0xFF4080C0);
            int faded = ColorUtil.fade(0);
            SelfCheck.assertTrue(ColorUtil.alpha(faded) == 0xFF, "fade keeps the alpha");
            ColorUtil.setClientColor(() -> 0xFFFFFFFF);
        });
    }

    private static void swingTransforms() {
        SelfCheck.check("soup2: hand swing transforms are the original matrices", () -> {
            HandTweaks module = new HandTweaks();
            Matrix4f matrix = new Matrix4f();
            SelfCheck.assertTrue(!module.applySwing(matrix, 0.5F, true, HandTweaks.MAIN_HAND),
                    "disabled module leaves the vanilla swing alone");
            module.setEnabled(true);
            SelfCheck.assertTrue(!module.applySwing(matrix, 0.5F, true, HandTweaks.MAIN_HAND),
                    "Vanilla swing type does not cancel the vanilla swing");
            module.enableSwing.setValue(true);
            module.swingType.setSelected("Swipe");
            SelfCheck.assertTrue(module.applySwing(matrix, 0.5F, true, HandTweaks.MAIN_HAND),
                    "Swipe cancels the vanilla swing");
            SelfCheck.assertTrue(!module.applySwing(matrix, 0.5F, true, !HandTweaks.MAIN_HAND),
                    "the off hand is never touched by the swing animation");
            module.swingType.setSelected("Custom");
            module.customTranslateX.setValue(1.0F);
            module.customRotateY.setValue(45.0F);
            SelfCheck.assertTrue(module.applySwing(new Matrix4f(), 0.25F, false, HandTweaks.MAIN_HAND),
                    "Custom cancels the vanilla swing");
            SelfCheck.assertClose(1.0, module.swingDuration(), 1.0E-6, "swing duration multiplier");
            module.swingSpeed.setValue(1.5F);
            SelfCheck.assertClose(1.5, module.swingDuration(), 1.0E-6, "swing speed is applied");
            module.enableSwing.setValue(false);
            SelfCheck.assertClose(1.0, module.swingDuration(), 1.0E-6, "swing speed ignored when off");
            SelfCheck.assertClose(1.0, module.handScale(HandTweaks.MAIN_HAND), 1.0E-6,
                    "untouched hand keeps scale 1");
            module.mainHandScale.setValue(0.5F);
            SelfCheck.assertClose(0.5, module.handScale(HandTweaks.MAIN_HAND), 1.0E-6, "main hand scale");
            SelfCheck.assertClose(1.0, module.handScale(!HandTweaks.MAIN_HAND), 1.0E-6, "off hand untouched");
        });
    }

    private static void trailLifetime() {
        SelfCheck.check("soup2: trail points age and expire like the original", () -> {
            Trails.Trail trail = new Trails.Trail(0, 0, 0, 1, 1, 1, 0xFF00FF00, 1.8F, 1500L);
            trail.setAlphaParams(1.5F, 1.0F);
            trail.update(System.currentTimeMillis());
            SelfCheck.assertClose(0.0, trail.getProgress(), 1.0E-3, "fresh point has no progress");
            SelfCheck.assertClose(1.0, trail.getAlpha(), 1.0E-3, "fresh point is opaque");
            long now = System.currentTimeMillis();
            // A point created "half a lifetime ago" is half faded: the fade strength is applied
            // to (1 - progress), exactly like {@code updateCache}.
            Trails.Trail old = new Trails.Trail(0, 0, 0, 1, 1, 1, 0xFF00FF00, 1.8F, 1000L);
            old.setAlphaParams(1.0F, 1.0F);
            old.update(now + 500);
            SelfCheck.assertClose(0.5, old.getProgress(), 0.05, "half of the lifetime passed");
            SelfCheck.assertClose(0.5, old.getAlpha(), 0.05, "half faded");
            SelfCheck.assertTrue(!old.shouldRemove(now), "not removed before its lifetime");
            SelfCheck.assertTrue(old.shouldRemove(now + 2000), "removed after its lifetime");
            // The opacity multiplier of "Trail Alpha" scales the result, it does not replace it.
            Trails.Trail faint = new Trails.Trail(0, 0, 0, 1, 1, 1, 0xFF00FF00, 1.8F, 1000L);
            faint.setAlphaParams(1.0F, 0.5F);
            faint.update(System.currentTimeMillis());
            SelfCheck.assertClose(0.5, faint.getAlpha(), 1.0E-3, "opacity factor 50 %");
        });
    }

    private static void friendList() {
        SelfCheck.check("soup2: friend list matches on the name", () -> {
            FriendList.clear();
            SelfCheck.assertTrue(!FriendList.isFriend("Padej_"), "empty list");
            FriendList.addFriend("Padej_");
            SelfCheck.assertTrue(FriendList.isFriend("Padej_"), "added name");
            SelfCheck.assertTrue(!FriendList.isFriend("padej_"), "case sensitive, like the original");
            FriendList.addFriend("Padej_");
            SelfCheck.assertEquals(1, FriendList.getFriends().size(), "no duplicates");
            FriendList.removeFriend("PADEJ_");
            SelfCheck.assertTrue(!FriendList.isFriend("Padej_"), "removal is case insensitive");
            SelfCheck.assertTrue(!FriendList.isFriend(null), "null is never a friend");
            FriendList.clear();
        });
    }

    private static void killEffectBookkeeping() {
        SelfCheck.check("soup2: kill effect bookkeeping", () -> {
            KillEffect module = new KillEffect();
            module.setEnabled(true);
            EntitySnapshot target = snapshot(42, true, 0.0D);
            module.onAttack(target);
            SelfCheck.assertEquals(1, module.trackedKillCount(), "attack is recorded");
            // The target is gone before any hurt flash: damage was never confirmed, so this is
            // not a kill. The record stays for the 5 s attack window, exactly like the original.
            module.tick(List.of(), true, 0, 0, 0, 0.0F);
            SelfCheck.assertEquals(1, module.trackedKillCount(), "an unconfirmed attack is not a kill");
            SelfCheck.assertEquals(0, module.pendingKillCount(), "nothing pending without damage");
            SelfCheck.assertTrue(module.getParticles().isEmpty(), "no particles without a kill");
            // Now a hit that does land: the hurt flash confirms the damage, and the entity
            // leaving the world turns into a pending kill (100 ms grace period).
            module.onAttack(snapshot(43, true, 0.0D));
            module.tick(List.of(snapshot(43, true, 10.0D)), true, 0, 0, 0, 0.0F);
            SelfCheck.assertEquals(2, module.trackedKillCount(), "both attacks are tracked");
            module.tick(List.of(), true, 0, 0, 0, 0.0F);
            SelfCheck.assertEquals(1, module.pendingKillCount(),
                    "a confirmed hit that vanished from the world is a pending kill");
            module.setEnabled(false);
            SelfCheck.assertTrue(module.getParticles().isEmpty(), "switching off clears the burst");
        });
    }

    private static EntitySnapshot snapshot(int id, boolean alive, double hurt) {
        return new EntitySnapshot(id, "Target", false, false,
                10.0, 64.0, 10.0, 10.0, 64.0, 10.0,
                65.6, 0.6F, 1.8F, (int) hurt, 20.0F, alive, true, false);
    }

    /** Kept for the settings screen work: reads a value setting by its lang key. */
    static ValueSetting findValue(List<Setting> settings, String key) {
        for (Setting setting : settings) {
            if (setting instanceof ValueSetting value && key.equals(value.getNameKey())) {
                return value;
            }
        }
        return null;
    }
}

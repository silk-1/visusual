package silkvis.verify;

import silkvis.visual.soup.animation.Interpolation;
import silkvis.visual.soup.animation.Interpolations;
import silkvis.visual.soup.animation.ThreeStageAnimation;
import silkvis.visual.soup.modules.HitBubbles;
import silkvis.visual.soup.setting.BooleanSetting;
import silkvis.visual.soup.setting.ColorSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.MultiColorSetting;
import silkvis.visual.soup.setting.MultiSelectSetting;
import silkvis.visual.soup.setting.SelectSetting;
import silkvis.visual.soup.setting.Setting;
import silkvis.visual.soup.setting.SettingRepository;
import silkvis.visual.soup.setting.ValueSetting;
import silkvis.visual.soup.util.ColorUtil;
import silkvis.visual.soup.util.ParticleColorUtil;
import silkvis.visual.soup.util.SpatialGrid3D;
import silkvis.visual.soup.util.StopWatch;

import java.util.List;

/**
 * Checks for the ported SoupVisuals particle core (HitBubbles / TotemParticles /
 * AmbientParticles).
 *
 * <p>Everything checked here is the original logic, so the checks are written against the
 * original's documented behaviour (interpolation maths, three-stage envelope, setting
 * defaults and option names, gradient maths, grid neighbourhood) rather than against the
 * port's implementation details. No Minecraft class is touched: the render path is the only
 * part that needs the game and it is exercised in game, not here.</p>
 */
final class SoupVisualsChecks {
    private SoupVisualsChecks() {
    }

    static void run() {
        interpolation();
        threeStageAnimation();
        settingsDefaults();
        settingsBehaviour();
        configKeys();
        critRule();
        colors();
        spatialGrid();
        stopWatch();
    }

    private static void interpolation() {
        SelfCheck.check("soup: interpolation curve endpoints and names", () -> {
            for (String name : Interpolations.getAllNames()) {
                Interpolation interpolation = Interpolations.getByName(name);
                SelfCheck.assertEquals(name, interpolation.getName(), "name round-trip of " + name);
            }
            // "Spring" is the one curve of the original that does not start at 0: its damping
            // term is -0.1 at progress 0. That is the original's maths, kept on purpose.
            for (String name : Interpolations.getAllNames()) {
                if (!name.equals("Spring")) {
                    SelfCheck.assertClose(0.0, Interpolations.getByName(name).interpolate(0.0), 1.0E-9, name + " at 0");
                }
            }
            SelfCheck.assertClose(-0.1, Interpolations.getByName("Spring").interpolate(0.0), 1.0E-6,
                    "spring starts below zero (original maths)");
            // Every curve ends at 1.0 except the two "overshoot" families, whose value at
            // exactly 1.0 is 1.0 as well; "Back"/"Overshoot" are only above 1 in between.
            SelfCheck.assertClose(1.0, Interpolations.getByName("Bounce").interpolate(1.0), 1.0E-6, "bounce end");
            // "Spring" overshoots its target at the end (1.035 at progress 1) -- original maths.
            SelfCheck.assertClose(1.0349937749111155, Interpolations.getByName("Spring").interpolate(1.0),
                    1.0E-9, "spring end");
            SelfCheck.assertClose(0.25, Interpolations.getByName("Smooth").interpolate(0.5), 1.0E-9, "smooth 0.5");
            SelfCheck.assertEquals(Interpolations.LINEAR, Interpolations.getByName("nonsense"),
                    "unknown name falls back to linear");
        });
    }

    private static void threeStageAnimation() {
        SelfCheck.check("soup: three-stage animation envelope", () -> {
            ThreeStageAnimation animation = new ThreeStageAnimation(1.0, 1.0, 1.0,
                    Interpolations.LINEAR, Interpolations.LINEAR);
            SelfCheck.assertClose(0.0, animation.getValue(0.0), 1.0E-9, "start");
            SelfCheck.assertClose(0.5, animation.getValue(0.5), 1.0E-9, "appear");
            SelfCheck.assertClose(1.0, animation.getValue(1.5), 1.0E-9, "exist");
            SelfCheck.assertClose(0.5, animation.getValue(2.5), 1.0E-9, "disappear");
            SelfCheck.assertClose(0.0, animation.getValue(3.5), 1.0E-9, "after end");
            SelfCheck.assertTrue(animation.isFinished(3.0), "finished at total duration");
            SelfCheck.assertEquals(ThreeStageAnimation.AnimationStage.EXIST, animation.getStage(1.5),
                    "exist stage");
            SelfCheck.assertEquals(3.0, animation.getTotalDuration(), 1.0E-9, "total duration");

            // Negative "exist" is the original's overlapping appear/disappear mode.
            ThreeStageAnimation overlap = new ThreeStageAnimation(1.0, -0.5, 1.0,
                    Interpolations.LINEAR, Interpolations.LINEAR);
            SelfCheck.assertClose(0.5, overlap.getValue(0.5), 1.0E-9, "overlap: appear half");
            SelfCheck.assertClose(0.75, overlap.getValue(0.75), 1.0E-9, "overlap: min(appear, disappear)");
        });
    }

    private static void settingsDefaults() {
        SelfCheck.check("soup: module settings keep the original defaults", () -> {
            HitBubbles hitBubbles = new HitBubbles();
            SelfCheck.assertEquals("Sync", hitBubbles.colorMode.getSelected(), "hitbubbles color mode");
            SelfCheck.assertEquals("Portal", bubbleStyle(hitBubbles), "hitbubbles bubble style");
            SelfCheck.assertEquals("Both", hitBubbles.animationType.getSelected(), "hitbubbles animation type");
            SelfCheck.assertEquals("Bounce", hitBubbles.appearInterpolation.getSelected(), "appear interpolation");
            SelfCheck.assertEquals("Smooth", hitBubbles.disappearInterpolation.getSelected(), "disappear interpolation");
            SelfCheck.assertClose(0.3, hitBubbles.appearDuration.getValue(), 1.0E-6, "appear duration");
            SelfCheck.assertClose(0.5, hitBubbles.existDuration.getValue(), 1.0E-6, "exist duration");
            SelfCheck.assertClose(0.5, hitBubbles.disappearDuration.getValue(), 1.0E-6, "disappear duration");
            SelfCheck.assertClose(0.75, hitBubbles.scale.getValue(), 1.0E-6, "scale");
            SelfCheck.assertClose(2.0, hitBubbles.rotateSpeed.getValue(), 1.0E-6, "rotate speed");
            SelfCheck.assertTrue(!hitBubbles.onlyCrits.isValue(), "only crits is off by default");
            SelfCheck.assertTrue(!hitBubbles.isEnabled(), "module is off until switched on");
            // The gradient defaults of the original: #E91E23-ish palette, four slots.
            int[] colors = hitBubbles.customColors.getColorValues();
            SelfCheck.assertEquals(4, colors.length, "four gradient slots");
            SelfCheck.assertEquals(-1499549, colors[0], "default color 1");
            SelfCheck.assertEquals(-409301, colors[3], "default color 4");
            // Hidden until "Custom" is chosen, exactly like the original's visibility supplier.
            SelfCheck.assertTrue(!hitBubbles.customColors.isVisible(), "gradient hidden in Sync mode");
            hitBubbles.colorMode.setSelected("Custom");
            SelfCheck.assertTrue(hitBubbles.customColors.isVisible(), "gradient visible in Custom mode");
        });
    }

    private static String bubbleStyle(HitBubbles module) {
        for (Setting setting : module.getSettings()) {
            if (setting instanceof SelectSetting select
                    && "setting.hitbubbles.bubblestyle.name".equals(select.getNameKey())) {
                return select.getSelected();
            }
        }
        return "";
    }

    private static void settingsBehaviour() {
        SelfCheck.check("soup: setting value semantics", () -> {
            BooleanSetting flag = new BooleanSetting("flag.name", "flag.desc").setValue(false);
            SelfCheck.assertTrue(!flag.isModified(), "default is not a modification");
            flag.setValue(true);
            SelfCheck.assertTrue(flag.isModified(), "change is a modification");
            flag.reset();
            SelfCheck.assertTrue(!flag.isValue(), "reset restores the default");

            ValueSetting value = new ValueSetting("value.name", "value.desc").setValue(1.5F).range(0.0F, 3.0F);
            SelfCheck.assertClose(0.0, value.getMin(), 1.0E-6, "min");
            SelfCheck.assertClose(3.0, value.getMax(), 1.0E-6, "max");
            SelfCheck.assertTrue(!value.isInteger(), "float range");
            ValueSetting count = new ValueSetting("count.name", "count.desc").setValue(30.0F).range(10, 70);
            SelfCheck.assertTrue(count.isInteger(), "int range keeps the integer marker");
            SelfCheck.assertEquals(30, count.getInt(), "int value");

            SelectSetting select = new SelectSetting("select.name", "select.desc")
                    .value("A", "B").selected("A");
            SelfCheck.assertEquals(2, select.getList().size(), "option count");
            SelfCheck.assertTrue(select.isSelected("a"), "selection is case insensitive");
            select.setSelected("B");
            SelfCheck.assertTrue(select.isModified(), "selection change is a modification");
            select.reset();
            SelfCheck.assertEquals("A", select.getSelected(), "reset restores first selection");

            MultiSelectSetting multi = new MultiSelectSetting("multi.name", "multi.desc")
                    .value("X", "Y", "Z").selected("X");
            multi.toggle("Y");
            SelfCheck.assertTrue(multi.isSelected("X") && multi.isSelected("Y"), "toggle adds");
            multi.toggle("X");
            SelfCheck.assertTrue(!multi.isSelected("X"), "toggle removes");
            multi.reset();
            SelfCheck.assertTrue(multi.isSelected("X") && !multi.isSelected("Y"), "reset restores selection");

            ColorSetting color = new ColorSetting("color.name", "color.desc").setColor(0xFF102030);
            SelfCheck.assertEquals(0xFF102030, color.getColor(), "argb value");
            SelfCheck.assertClose(0x10 / 255.0, color.getColorArray()[0], 1.0E-6, "red component");
            color.reset();
            SelfCheck.assertEquals(0xFF102030, color.getColor(), "color reset");

            MultiColorSetting gradient = new MultiColorSetting("gradient.name", "gradient.desc")
                    .defaultColors(1, 2, 3, 4);
            gradient.setColorValue(2, 99);
            SelfCheck.assertEquals(99, gradient.getColorValue(2), "single slot write");
            SelfCheck.assertEquals(4, gradient.getColorValue(3), "other slots untouched");
        });
    }

    private static void configKeys() {
        SelfCheck.check("soup: config keys are stable and unique", () -> {
            HitBubbles module = new HitBubbles();
            List<Setting> values = module.getValueSettings();
            // 13 settings of the module plus the four colour slots of its palette.
            SelfCheck.assertEquals(17, values.size(), "every setting and colour slot is persisted");
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Setting setting : values) {
                String key = SettingRepository.configKey(setting);
                SelfCheck.assertTrue(seen.add(key), "duplicate config key: " + key);
                SelfCheck.assertTrue(key.startsWith("hitbubbles."), "module prefix: " + key);
            }
            // Groups are containers, buttons are actions: neither is written to the config.
            for (Setting setting : values) {
                SelfCheck.assertTrue(!(setting instanceof GroupSetting), "group is not a value");
            }
            SelfCheck.assertEquals("hitbubbles.appearduration",
                    SettingRepository.configKey(module.appearDuration), "stable key of a value setting");
        });
    }

    private static void critRule() {
        SelfCheck.check("soup: vanilla crit rule of HitBubbles", () -> {
            SelfCheck.assertTrue(HitBubbles.isCrit(1.0F, 0.5, false, false, false, false, false, false),
                    "full cooldown, falling, no debuffs -> crit");
            SelfCheck.assertTrue(!HitBubbles.isCrit(0.5F, 0.5, false, false, false, false, false, false),
                    "half cooldown -> no crit");
            SelfCheck.assertTrue(!HitBubbles.isCrit(1.0F, 0.0, false, false, false, false, false, false),
                    "no fall -> no crit");
            SelfCheck.assertTrue(!HitBubbles.isCrit(1.0F, 0.5, true, false, false, false, false, false),
                    "on ground -> no crit");
            SelfCheck.assertTrue(!HitBubbles.isCrit(1.0F, 0.5, false, true, false, false, false, false),
                    "climbing -> no crit");
            SelfCheck.assertTrue(!HitBubbles.isCrit(1.0F, 0.5, false, false, true, false, false, false),
                    "in water -> no crit");
            SelfCheck.assertTrue(!HitBubbles.isCrit(1.0F, 0.5, false, false, false, true, false, false),
                    "blindness -> no crit");
            SelfCheck.assertTrue(!HitBubbles.isCrit(1.0F, 0.5, false, false, false, false, true, false),
                    "sprinting -> no crit");
            SelfCheck.assertTrue(!HitBubbles.isCrit(1.0F, 0.5, false, false, false, false, false, true),
                    "in a vehicle -> no crit");
        });
    }

    private static void colors() {
        SelfCheck.check("soup: particle colour maths", () -> {
            // getColor(r, g, b, a): the first argument is red, as in the original.
            SelfCheck.assertEquals(0x44112233, ColorUtil.getColor(0x11, 0x22, 0x33, 0x44), "argb packing");
            SelfCheck.assertEquals(0x22, ColorUtil.red(0x11223344), "red");
            SelfCheck.assertEquals(0x33, ColorUtil.green(0x11223344), "green");
            SelfCheck.assertEquals(0x44, ColorUtil.blue(0x11223344), "blue");
            SelfCheck.assertEquals(0x11, ColorUtil.alpha(0x11223344), "alpha");
            SelfCheck.assertEquals(0x80FFFFFF, ColorUtil.multAlpha(0xFFFFFFFF, 0.5F), "alpha multiply");
            SelfCheck.assertEquals(0x00FF0000, ColorUtil.overCol(0x00FF0000, 0x000000FF, 0.0F), "blend at 0");
            SelfCheck.assertEquals(0x000000FF, ColorUtil.overCol(0x00FF0000, 0x000000FF, 1.0F), "blend at 1");
            SelfCheck.assertEquals(0x00808000, ColorUtil.overCol(0x00FF0000, 0x0000FF00, 0.5F), "blend middle");

            int[] single = {0xFFFF0000};
            SelfCheck.assertEquals(0x80FF0000, ParticleColorUtil.getWaveColor(single, 0.5F), "single colour wave");
            int[] two = {0xFFFF0000, 0xFF0000FF};
            int wave = ParticleColorUtil.getWaveColor(two, 1.0F, 0);
            SelfCheck.assertEquals(0xFF, ColorUtil.alpha(wave), "wave keeps the alpha");
            int vertex = ParticleColorUtil.getVertexGradientColor(90, two, 1.0F);
            SelfCheck.assertEquals(0xFF, ColorUtil.alpha(vertex), "vertex gradient keeps the alpha");
            SelfCheck.assertEquals(0x80FFFFFF, ParticleColorUtil.getWaveColor(null, 0.5F), "no colours -> white");
        });
    }

    private static void spatialGrid() {
        SelfCheck.check("soup: spatial grid neighbourhood", () -> {
            SpatialGrid3D<String> grid = new SpatialGrid3D<>(2.0F);
            grid.insert("a", 0, 0, 0);
            grid.insert("b", 1, 0, 0);
            grid.insert("c", 10, 0, 0);
            List<String> near = grid.query(0, 0, 0, 2.0F);
            SelfCheck.assertTrue(near.contains("a") && near.contains("b"), "close neighbours found");
            SelfCheck.assertTrue(!near.contains("c"), "far item not returned");
            SelfCheck.assertEquals(3, grid.size(), "three items stored");
            grid.update("b", 1, 0, 0, 10, 0, 0);
            SelfCheck.assertTrue(!grid.query(0, 0, 0, 2.0F).contains("b"), "moved item left its cell");
            SelfCheck.assertTrue(grid.query(10, 0, 0, 2.0F).contains("b"), "moved item is in the new cell");
            grid.remove("a", 0, 0, 0);
            SelfCheck.assertEquals(2, grid.size(), "removal");
            SelfCheck.assertEquals(SpatialGrid3D.pairKey(1, 2, 3, 4, 5, 6),
                    SpatialGrid3D.pairKey(1, 2, 3, 4, 5, 6), "pair key is stable");
        });
    }

    private static void stopWatch() {
        SelfCheck.check("soup: stop watch", () -> {
            StopWatch watch = new StopWatch();
            SelfCheck.assertTrue(watch.elapsedTime() >= 0, "fresh watch is non-negative");
            watch.setMs(1000);
            SelfCheck.assertTrue(watch.elapsedTime() >= 1000, "setMs shifts the start");
            // finished(delay) is the original's "start + delay has passed"; a delay far in the
            // past is finished, a delay in the future is not.
            SelfCheck.assertTrue(watch.finished(-5000), "old delay is finished");
            SelfCheck.assertTrue(!watch.finished(60_000), "future delay is not finished");
        });
    }
}

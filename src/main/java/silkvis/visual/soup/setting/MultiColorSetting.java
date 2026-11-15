package silkvis.visual.soup.setting;

import java.util.function.Supplier;

/**
 * Ported from SoupVisuals {@code ...setting.implement.MultiColorSetting} (Padej_).
 *
 * <p>Four colour slots with the original default gradient
 * ({@code -1499549, -13273872, -6596170, -409301}); the modules below only read as many
 * slots as their {@code Color Count} select allows (Solo / Duo / Triple / Quartet), which
 * is exactly what the original does with {@code getCustomColors()}.</p>
 */
public class MultiColorSetting extends Setting {
    private final ColorSetting color1;
    private final ColorSetting color2;
    private final ColorSetting color3;
    private final ColorSetting color4;
    private String[] colorNames;

    public MultiColorSetting(String name, String description) {
        super(name, description);
        // Slot keys stay under the parent key (setting.<module>.<name>.color<N>.name) so the
        // config file of the fork can be mapped onto SoupVisuals' own saved colours.
        String base = name.endsWith(".name") ? name.substring(0, name.length() - ".name".length()) : name;
        this.color1 = new ColorSetting(base + ".color1.name", base + ".color1.desc");
        this.color2 = new ColorSetting(base + ".color2.name", base + ".color2.desc");
        this.color3 = new ColorSetting(base + ".color3.name", base + ".color3.desc");
        this.color4 = new ColorSetting(base + ".color4.name", base + ".color4.desc");
    }

    public MultiColorSetting colors(String... names) {
        this.colorNames = names;
        return this;
    }

    public MultiColorSetting defaultColors(int... colors) {
        if (colors.length > 0) {
            this.color1.setColor(colors[0]);
        }
        if (colors.length > 1) {
            this.color2.setColor(colors[1]);
        }
        if (colors.length > 2) {
            this.color3.setColor(colors[2]);
        }
        if (colors.length > 3) {
            this.color4.setColor(colors[3]);
        }
        return this;
    }

    public MultiColorSetting visible(Supplier<Boolean> visible) {
        this.setVisible(visible);
        return this;
    }

    public ColorSetting getColor1() {
        return this.color1;
    }

    public ColorSetting getColor2() {
        return this.color2;
    }

    public ColorSetting getColor3() {
        return this.color3;
    }

    public ColorSetting getColor4() {
        return this.color4;
    }

    public int[] getColorValues() {
        return new int[]{this.color1.getColor(), this.color2.getColor(),
                this.color3.getColor(), this.color4.getColor()};
    }

    public String[] getColorNames() {
        return this.colorNames;
    }

    /** Sets slot {@code index} (0..3) from a single ARGB value; used by the colour window. */
    public void setColorValue(int index, int argb) {
        switch (index) {
            case 0 -> this.color1.setColor(argb);
            case 1 -> this.color2.setColor(argb);
            case 2 -> this.color3.setColor(argb);
            case 3 -> this.color4.setColor(argb);
            default -> {
                // Out of range: the original has exactly four slots, ignore like it does.
            }
        }
    }

    public int getColorValue(int index) {
        return switch (index) {
            case 1 -> this.color2.getColor();
            case 2 -> this.color3.getColor();
            case 3 -> this.color4.getColor();
            default -> this.color1.getColor();
        };
    }

    @Override
    public boolean isModified() {
        return this.color1.isModified() || this.color2.isModified()
                || this.color3.isModified() || this.color4.isModified();
    }

    @Override
    public void reset() {
        this.color1.reset();
        this.color2.reset();
        this.color3.reset();
        this.color4.reset();
    }
}

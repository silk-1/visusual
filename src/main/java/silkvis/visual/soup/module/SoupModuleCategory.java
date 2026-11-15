package silkvis.visual.soup.module;

/**
 * Ported from SoupVisuals {@code padej.soup.api.feature.module.ModuleCategory} (Padej_).
 *
 * <p>Only the categories the ported modules belong to are kept; the client menu of SilkVis
 * keeps its own seven tabs, so this enum exists to preserve the original module metadata
 * (and to keep the ported constructors unchanged).</p>
 */
public enum SoupModuleCategory {
    PARTICLES("particles"),
    VISUALS("visuals"),
    HUD("hud"),
    OTHER("other"),
    WORLD("world"),
    CLIENT("client");

    private final String id;

    SoupModuleCategory(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String langKey() {
        return "silkvis.tab." + this.id;
    }
}

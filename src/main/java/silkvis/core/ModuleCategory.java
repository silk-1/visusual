package silkvis.core;

/** P1 navigation categories (UI-04); automation stays a separate, explicitly P2 tab. */
public enum ModuleCategory {
    VISUAL("visual"),
    HUD("hud"),
    MAP("map"),
    MUSIC("music"),
    THEME("theme"),
    /** Custom totem texture (ASSET-04): the only asset override this build ships. */
    TOTEMS("totems"),
    INTEGRATIONS("integrations"),
    SETTINGS("settings"),
    AUTOMATION_P2("automation"),
    /**
     * Driver for the player's own Baritone install. A separate tab rather than a mode of the
     * automation tab: the two are different engines with different rules, and the player must
     * be able to see which one is in charge before pressing anything.
     */
    BARITONE("baritone");

    private final String id;

    ModuleCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String langKey() {
        return "silkvis.tab." + id;
    }
}

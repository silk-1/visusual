package silkvis.core;

/** P1 navigation categories (UI-04); automation stays a separate, explicitly P2 tab. */
public enum ModuleCategory {
    VISUAL("visual"),
    HUD("hud"),
    MAP("map"),
    MUSIC("music"),
    THEME("theme"),
    INTEGRATIONS("integrations"),
    SETTINGS("settings"),
    AUTOMATION_P2("automation");

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

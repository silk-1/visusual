package silkvis.visual.soup.setting;

/** Ported from SoupVisuals {@code ...setting.implement.ButtonSetting} (Padej_). */
public class ButtonSetting extends Setting {
    private final Runnable action;

    public ButtonSetting(String name, String description, Runnable action) {
        super(name, description);
        this.action = action;
    }

    public void press() {
        if (this.action != null) {
            this.action.run();
        }
    }

    public Runnable getAction() {
        return this.action;
    }
}

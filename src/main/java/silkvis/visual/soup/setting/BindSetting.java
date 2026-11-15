package silkvis.visual.soup.setting;

/**
 * Ported from SoupVisuals {@code ...setting.implement.BindSetting} (Padej_).
 *
 * <p>Only the binding data is kept: the key code is a GLFW code, {@code -1} means
 * unbound. The client layer reads it; nothing here registers a keybind.</p>
 */
public class BindSetting extends Setting {
    private int key = -1;

    public BindSetting(String name, String description) {
        super(name, description);
    }

    public int getKey() {
        return this.key;
    }

    public BindSetting setKey(int key) {
        boolean changed = this.key != key;
        this.key = key;
        if (changed) {
            this.notifyChange();
        }
        return this;
    }

    public boolean isBound() {
        return this.key != -1;
    }

    @Override
    public boolean isModified() {
        return this.key != -1;
    }

    @Override
    public void reset() {
        setKey(-1);
    }
}

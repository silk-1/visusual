package silkvis.visual.soup.setting;

/** Ported from SoupVisuals {@code ...setting.implement.TextSetting} (Padej_). */
public class TextSetting extends Setting {
    private String text;
    private String defaultText;
    private int maxLength = 64;

    public TextSetting(String name, String description) {
        super(name, description);
    }

    public TextSetting maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public String getText() {
        return this.text == null ? "" : this.text;
    }

    public TextSetting setText(String text) {
        String clipped = text == null ? "" : text;
        if (clipped.length() > this.maxLength) {
            clipped = clipped.substring(0, this.maxLength);
        }
        if (this.defaultText == null) {
            this.defaultText = clipped;
            this.text = clipped;
            return this;
        }
        boolean changed = !clipped.equals(this.text);
        this.text = clipped;
        if (changed) {
            this.notifyChange();
        }
        return this;
    }

    public int getMaxLength() {
        return this.maxLength;
    }

    @Override
    public boolean isModified() {
        return this.defaultText != null && !getText().equals(this.defaultText);
    }

    @Override
    public void reset() {
        if (this.defaultText != null) {
            setText(this.defaultText);
        }
    }

    public String getDefaultText() {
        return this.defaultText;
    }
}

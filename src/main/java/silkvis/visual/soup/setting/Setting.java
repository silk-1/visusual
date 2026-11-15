package silkvis.visual.soup.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ported from SoupVisuals 1.21.11 ({@code padej.soup.api.feature.module.setting.Setting}),
 * adapted to Minecraft 1.21.10.
 *
 * <p>Original author: Padej_ (SoupVisuals / soupapi), SOUP-1.0 licence, fork and
 * adaptation to 1.21.10 by written permission of the author. The original structure is
 * kept deliberately: a setting owns a <em>lang key</em>, an optional description key, a
 * visibility supplier and a change listener, so the ported modules below declare their
 * settings exactly as the original does (same names, same order, same defaults).</p>
 *
 * <p>What changed: the original resolves its keys through its own
 * {@code LocalizationManager}; here a {@link TextSink} is installed once by the client
 * layer, so this package stays free of {@code net.minecraft.*} (AGENTS.md rule 10) and is
 * covered by {@code selfCheck}. Auto-generated keys ({@code setting.<module>.<name>.name})
 * are resolved exactly like the original when the key has no dot.</p>
 */
public abstract class Setting {
    private final String nameKey;
    private final String descriptionKey;
    private String moduleContext;
    private Supplier<Boolean> visible;
    private static Consumer<Setting> globalChangeListener;
    private boolean saveToConfig = true;
    private transient String cachedAutoKeyName;
    private transient String cachedAutoKeyDesc;

    /**
     * Localization hook. Set by the client bootstrap to
     * {@code key -> LangTable/vanilla translation}. The default returns the key unchanged,
     * which makes every getter fall back to the raw key instead of throwing.
     */
    public interface TextSink {
        String get(String key);
    }

    private static volatile TextSink textSink = key -> key;

    public static void setTextSink(TextSink sink) {
        textSink = sink == null ? key -> key : sink;
    }

    /** Read-only access for subclasses that use their own key prefixes (GroupSetting). */
    /**
     * Public localization for code outside this package (the module list prints module names
     * through it). Returns the key unchanged when no translation is installed, so callers can
     * compare and fall back instead of printing an empty label.
     */
    public static String translate(String key) {
        String translated = textSink.get(key);
        return translated == null ? key : translated;
    }

    protected static String text(String key) {
        return textSink.get(key);
    }

    protected Setting(String nameKey) {
        this.nameKey = nameKey;
        this.descriptionKey = null;
    }

    protected Setting(String nameKey, String descriptionKey) {
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
    }

    public boolean isVisible() {
        return this.visible == null || this.visible.get();
    }

    public String getName() {
        if (this.nameKey.contains(".")) {
            String translated = textSink.get(this.nameKey);
            return translated.equals(this.nameKey) ? this.nameKey : translated;
        }
        if (this.moduleContext != null && !this.moduleContext.isEmpty()) {
            if (this.cachedAutoKeyName == null) {
                this.cachedAutoKeyName = "setting."
                        + this.moduleContext.toLowerCase().replace(" ", "")
                        + "."
                        + this.nameKey.toLowerCase().replace(" ", "")
                        + ".name";
            }
            String translated = textSink.get(this.cachedAutoKeyName);
            return translated.equals(this.cachedAutoKeyName) ? this.nameKey : translated;
        }
        String translated = textSink.get(this.nameKey);
        return translated.equals(this.nameKey) ? this.nameKey : translated;
    }

    public String getDescription() {
        if (this.descriptionKey == null || this.descriptionKey.isEmpty()) {
            return "";
        }
        if (this.descriptionKey.contains(".")) {
            String translated = textSink.get(this.descriptionKey);
            return translated.equals(this.descriptionKey) ? this.descriptionKey : translated;
        }
        if (this.moduleContext != null && !this.moduleContext.isEmpty()) {
            if (this.cachedAutoKeyDesc == null) {
                this.cachedAutoKeyDesc = "setting."
                        + this.moduleContext.toLowerCase().replace(" ", "")
                        + "."
                        + this.descriptionKey.toLowerCase().replace(" ", "")
                        + ".desc";
            }
            String translated = textSink.get(this.cachedAutoKeyDesc);
            return translated.equals(this.cachedAutoKeyDesc) ? this.descriptionKey : translated;
        }
        String translated = textSink.get(this.descriptionKey);
        return translated.equals(this.descriptionKey) ? this.descriptionKey : translated;
    }

    /** Option label of a select/multi-select entry ({@code select.option.<name>}). */
    static String localizeOption(String option) {
        String key = "select.option." + option.toLowerCase().replace(" ", "");
        String localized = textSink.get(key);
        return localized.equals(key) ? option : localized;
    }

    public boolean isModified() {
        return false;
    }

    public void reset() {
    }

    /** Fires the global change listener; modules use it to mark their config dirty. */
    protected void notifyChange() {
        Consumer<Setting> listener = globalChangeListener;
        if (listener != null) {
            listener.accept(this);
        }
    }

    public String getNameKey() {
        return this.nameKey;
    }

    public String getDescriptionKey() {
        return this.descriptionKey;
    }

    public String getModuleContext() {
        return this.moduleContext;
    }

    public boolean isSaveToConfig() {
        return this.saveToConfig;
    }

    public void setModuleContext(String moduleContext) {
        this.moduleContext = moduleContext;
    }

    public void setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
    }

    public static void setGlobalChangeListener(Consumer<Setting> listener) {
        Setting.globalChangeListener = listener;
    }
}

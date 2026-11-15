package silkvis.visual.soup.module;

import silkvis.visual.soup.setting.ButtonSetting;
import silkvis.visual.soup.setting.GroupSetting;
import silkvis.visual.soup.setting.Setting;
import silkvis.visual.soup.setting.SettingRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ported from SoupVisuals {@code padej.soup.api.feature.module.Module} (Padej_).
 *
 * <p>Kept minimal on purpose: the original base class also owns a bind, a HUD position and
 * animation state, none of which the ported modules need. What is preserved is the part the
 * modules actually use -- {@code super("module.x.name", ModuleCategory.PARTICLES)},
 * the ordered {@code setup(...)} list and the settings tree, because that is what
 * determines the shape of the settings screen and the config file.</p>
 *
 * <p>Enable/disable is explicit ({@link #toggle()}, {@link #setEnabled(boolean)}) and does
 * not go through SilkVis' {@code ModuleRegistry} yet: these modules are new and stay off
 * until wired in a follow-up step. The deactivate hook is the original's
 * {@code deactivate()} -- it clears particle lists so re-enabling never shows stale state.</p>
 */
public abstract class SoupModule {
    private final String nameKey;
    private final SoupModuleCategory category;
    /** Group headers and loose settings in declaration order (original {@code setup}). */
    private final List<Setting> rootSettings = new ArrayList<>();
    private final List<Setting> allSettings = new ArrayList<>();
    private boolean enabled;

    protected SoupModule(String nameKey, SoupModuleCategory category) {
        this.nameKey = nameKey;
        this.category = category;
    }

    /** Registers the settings tree; called once from the constructor like in the original. */
    protected void setup(Setting... settings) {
        Collections.addAll(this.rootSettings, settings);
        for (Setting setting : settings) {
            setting.setModuleContext(this.nameKey.replace("module.", "").replace(".name", ""));
        }
        if (this.allSettings.isEmpty()) {
            this.allSettings.addAll(SettingRepository.flatten(this.rootSettings));
        }
    }

    public String getNameKey() {
        return this.nameKey;
    }

    public SoupModuleCategory getCategory() {
        return this.category;
    }

    public List<Setting> getSettings() {
        return Collections.unmodifiableList(this.allSettings);
    }

    public List<Setting> getRootSettings() {
        return Collections.unmodifiableList(this.rootSettings);
    }

    public List<Setting> getValueSettings() {
        return SettingRepository.valueSettings(this.rootSettings);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void toggle() {
        setEnabled(!this.enabled);
    }

    public void setEnabled(boolean value) {
        if (this.enabled == value) {
            return;
        }
        this.enabled = value;
        if (value) {
            activate();
        } else {
            deactivate();
        }
    }

    /** Original {@code activate()}: called when the module is switched on. */
    protected void activate() {
    }

    /** Original {@code deactivate()}: must clear per-world state. */
    protected void deactivate() {
    }

    /**
     * Public reset for the client glue: a world change has to clear the pools without
     * flipping the module's on/off state.
     */
    public void clearWorldState() {
        deactivate();
    }

    /** Button settings of the module, used by the settings screen. */
    public List<ButtonSetting> getButtons() {
        List<ButtonSetting> out = new ArrayList<>();
        for (Setting setting : this.allSettings) {
            if (setting instanceof ButtonSetting button) {
                out.add(button);
            }
        }
        return out;
    }

    /** Group headers only, for a compact settings screen. */
    public List<GroupSetting> getGroups() {
        List<GroupSetting> out = new ArrayList<>();
        for (Setting setting : this.rootSettings) {
            if (setting instanceof GroupSetting group) {
                out.add(group);
            }
        }
        return out;
    }
}

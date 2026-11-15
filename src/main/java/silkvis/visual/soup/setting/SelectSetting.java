package silkvis.visual.soup.setting;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Ported from SoupVisuals {@code ...setting.implement.SelectSetting} (Padej_). */
public class SelectSetting extends Setting {
    private String selected;
    private List<String> list;
    private Consumer<String> onChangeCallback;
    private String defaultSelected;

    public SelectSetting(String name, String description) {
        super(name, description);
    }

    public SelectSetting value(String... values) {
        List<String> values1 = Arrays.asList(values);
        this.selected = values1.get(0);
        this.list = values1;
        return this;
    }

    public SelectSetting visible(Supplier<Boolean> visible) {
        this.setVisible(visible);
        return this;
    }

    public SelectSetting selected(String string) {
        if (this.defaultSelected == null) {
            this.defaultSelected = string;
        }
        this.selected = string;
        return this;
    }

    public boolean isSelected(String name) {
        return this.selected != null && this.selected.equalsIgnoreCase(name);
    }

    public SelectSetting onChange(Consumer<String> callback) {
        this.onChangeCallback = callback;
        return this;
    }

    public void setSelected(String newSelected) {
        boolean changed = this.selected == null || !this.selected.equals(newSelected);
        this.selected = newSelected;
        if (changed) {
            this.notifyChange();
            if (this.onChangeCallback != null) {
                this.onChangeCallback.accept(newSelected);
            }
        }
    }

    public String getSelectedLocalized() {
        return this.selected == null ? null : localizeOption(this.selected);
    }

    public List<String> getListLocalized() {
        return this.list == null ? null
                : this.list.stream().map(Setting::localizeOption).collect(Collectors.toList());
    }

    @Override
    public boolean isModified() {
        if (this.defaultSelected == null) {
            return false;
        }
        return this.selected == null || !this.selected.equals(this.defaultSelected);
    }

    @Override
    public void reset() {
        if (this.defaultSelected != null) {
            this.setSelected(this.defaultSelected);
        }
    }

    public String getSelected() {
        return this.selected;
    }

    public List<String> getList() {
        return this.list;
    }

    public String getDefaultSelected() {
        return this.defaultSelected;
    }
}

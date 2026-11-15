package silkvis.visual.soup.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Ported from SoupVisuals {@code ...setting.implement.MultiSelectSetting} (Padej_). */
public class MultiSelectSetting extends Setting {
    private List<String> list;
    private List<String> selected = new ArrayList<>();
    private List<String> defaultSelected;

    public MultiSelectSetting(String name, String description) {
        super(name, description);
    }

    public MultiSelectSetting value(String... settings) {
        this.list = Arrays.asList(settings);
        return this;
    }

    public MultiSelectSetting selected(String... settings) {
        if (this.defaultSelected == null) {
            this.defaultSelected = new ArrayList<>(Arrays.asList(settings));
        }
        this.selected = new ArrayList<>(Arrays.asList(settings));
        this.notifyChange();
        return this;
    }

    public MultiSelectSetting visible(Supplier<Boolean> visible) {
        this.setVisible(visible);
        return this;
    }

    public boolean isSelected(String name) {
        return this.selected.contains(name);
    }

    public List<String> getListLocalized() {
        return this.list == null ? null
                : this.list.stream().map(Setting::localizeOption).collect(Collectors.toList());
    }

    public List<String> getSelectedLocalized() {
        return this.selected == null ? null
                : this.selected.stream().map(Setting::localizeOption).collect(Collectors.toList());
    }

    @Override
    public boolean isModified() {
        if (this.defaultSelected == null) {
            return false;
        }
        if (this.selected == null) {
            return true;
        }
        return this.selected.size() != this.defaultSelected.size()
                || !this.selected.containsAll(this.defaultSelected)
                || !this.defaultSelected.containsAll(this.selected);
    }

    @Override
    public void reset() {
        if (this.defaultSelected != null) {
            this.selected = new ArrayList<>(this.defaultSelected);
        }
    }

    public List<String> getList() {
        return this.list;
    }

    public List<String> getSelected() {
        return this.selected;
    }

    public List<String> getDefaultSelected() {
        return this.defaultSelected;
    }

    public void setSelected(List<String> selected) {
        this.selected = selected == null ? new ArrayList<>() : new ArrayList<>(selected);
        this.notifyChange();
    }

    /** Toggle used by the multi-select dropdown of the original menu. */
    public void toggle(String option) {
        List<String> next = new ArrayList<>(this.selected);
        if (!next.remove(option)) {
            next.add(option);
        }
        this.setSelected(next);
    }
}

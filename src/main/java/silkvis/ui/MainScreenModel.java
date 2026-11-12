package silkvis.ui;

import silkvis.core.ModuleCategory;
import silkvis.core.ModuleDescriptor;
import silkvis.core.ModuleRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Presentation model of the main window (UI-04): tab selection, search over localized
 * name/description and stable module id, favourites and "modified" markers.
 */
public final class MainScreenModel {
    private final ModuleRegistry registry;
    private ModuleCategory selected = ModuleCategory.VISUAL;
    private String query = "";
    private String selectedModuleId;
    private final List<String> favorites = new ArrayList<>();

    public MainScreenModel(ModuleRegistry registry) {
        this.registry = registry;
    }

    public static List<ModuleCategory> p1Tabs() {
        return List.of(ModuleCategory.VISUAL, ModuleCategory.HUD, ModuleCategory.MAP,
                ModuleCategory.MUSIC, ModuleCategory.THEME, ModuleCategory.INTEGRATIONS,
                ModuleCategory.SETTINGS, ModuleCategory.AUTOMATION_P2);
    }

    public ModuleCategory selectedCategory() {
        return selected;
    }

    public void select(ModuleCategory category) {
        this.selected = category == null ? ModuleCategory.VISUAL : category;
        this.selectedModuleId = null;
    }

    public String query() {
        return query;
    }

    public void setQuery(String newQuery) {
        this.query = newQuery == null ? "" : newQuery;
    }

    public String selectedModuleId() {
        return selectedModuleId;
    }

    public void selectModule(String moduleId) {
        this.selectedModuleId = moduleId;
    }

    public List<String> favorites() {
        return List.copyOf(favorites);
    }

    public void toggleFavorite(String moduleId) {
        if (favorites.contains(moduleId)) {
            favorites.remove(moduleId);
        } else {
            favorites.add(moduleId);
        }
    }

    /**
     * Visible module descriptors for the current tab and query. Search matches the
     * localized name, the localized description and the stable id (UI-04).
     */
    public List<ModuleDescriptor> visibleModules(Localizer localizer) {
        List<ModuleDescriptor> out = new ArrayList<>();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (ModuleRegistry.Entry entry : registry.byCategory(selected)) {
            ModuleDescriptor d = entry.descriptor();
            if (needle.isEmpty()) {
                out.add(d);
                continue;
            }
            String name = localizer.get(d.displayKey()).toLowerCase(Locale.ROOT);
            String desc = localizer.get(d.descKey()).toLowerCase(Locale.ROOT);
            if (d.id().contains(needle) || name.contains(needle) || desc.contains(needle)) {
                out.add(d);
            }
        }
        out.sort((a, b) -> {
            boolean favA = favorites.contains(a.id());
            boolean favB = favorites.contains(b.id());
            if (favA != favB) {
                return favA ? -1 : 1;
            }
            return localizer.get(a.displayKey()).compareToIgnoreCase(localizer.get(b.displayKey()));
        });
        return out;
    }

    /** Automation tab is P2: it reports "planned" and must not fake execution (0.2). */
    public boolean showsAutomationPlaceholder() {
        return selected == ModuleCategory.AUTOMATION_P2;
    }

    public String statusKeyFor(ModuleRegistry.Entry entry) {
        if (!entry.available()) {
            return "silkvis.status." + entry.reasonCode().map(c -> c.name().toLowerCase(Locale.ROOT))
                    .orElse("unsupported");
        }
        return "silkvis.status." + entry.state().name().toLowerCase(Locale.ROOT);
    }
}

package silkvis.automation;

import silkvis.config.ConfigException;
import silkvis.config.Validators;
import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;

/**
 * Automation settings (AUTO-01). Stored per profile next to the HUD and visual settings, and
 * validated on read: a hand-edited value that would make the engine unsafe is refused, not
 * clamped silently.
 *
 * <p>Defaults are the "nothing happens" set: {@link AutomationMode#OFF}, nothing enabled. Rule 7
 * of AGENTS.md applies to a whole subsystem, not only to drawing.</p>
 */
public record AutomationSettings(
        AutomationMode mode,
        /** Blocks of search radius around the player for "what can I reach". */
        int reachRadius,
        /** Hard cap of A* nodes per tick-slice, so one search cannot eat the frame. */
        int searchNodes,
        /** Stop everything below this health (half a heart = 1.0). */
        float healthFloor,
        /** Stop everything below this food level. */
        int foodFloor,
        /** Hand control back when a hostile mob is this close, in blocks (0 = never). */
        double dangerRadius,
        /** Re-plan when the player drifts more than this many blocks from the plan. */
        double replanDistance,
        /** Break blocks that are in the way (only for goals that allow it). */
        boolean allowBreak,
        /** Place blocks (bridging/build goals). */
        boolean allowPlace,
        /** Show the current plan in the world (a line through the steps). */
        boolean showPath,
        /** Limit the engine to the hotbar: no inventory swapping while walking. */
        boolean hotbarOnly
) {
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 128;
    public static final int MIN_NODES = 64;
    public static final int MAX_NODES = 8192;

    public static AutomationSettings defaults() {
        return new AutomationSettings(AutomationMode.OFF, 48, 1024, 6.0f, 6, 0.0, 3.0,
                false, false, true, true);
    }

    public boolean enabled() {
        return mode != AutomationMode.OFF;
    }

    public JsonObject encode() {
        return JsonValue.newObject()
                .put("mode", mode.name())
                .put("reachRadius", reachRadius)
                .put("searchNodes", searchNodes)
                .put("healthFloor", healthFloor)
                .put("foodFloor", foodFloor)
                .put("dangerRadius", dangerRadius)
                .put("replanDistance", replanDistance)
                .put("allowBreak", allowBreak)
                .put("allowPlace", allowPlace)
                .put("showPath", showPath)
                .put("hotbarOnly", hotbarOnly);
    }

    public static AutomationSettings decode(JsonObject o) throws ConfigException {
        AutomationSettings d = defaults();
        AutomationMode mode = d.mode();
        if (o.has("mode")) {
            try {
                mode = AutomationMode.valueOf(o.get("mode").string());
            } catch (IllegalArgumentException e) {
                throw ConfigException.of("automation.mode", "unknown value");
            }
        }
        return new AutomationSettings(
                mode,
                o.has("reachRadius") ? o.get("reachRadius").number().intValueExact() : d.reachRadius(),
                o.has("searchNodes") ? o.get("searchNodes").number().intValueExact() : d.searchNodes(),
                o.has("healthFloor") ? o.get("healthFloor").number().floatValue() : d.healthFloor(),
                o.has("foodFloor") ? o.get("foodFloor").number().intValueExact() : d.foodFloor(),
                o.has("dangerRadius") ? o.get("dangerRadius").number().doubleValue() : d.dangerRadius(),
                o.has("replanDistance") ? o.get("replanDistance").number().doubleValue()
                        : d.replanDistance(),
                o.has("allowBreak") ? o.get("allowBreak").bool() : d.allowBreak(),
                o.has("allowPlace") ? o.get("allowPlace").bool() : d.allowPlace(),
                o.has("showPath") ? o.get("showPath").bool() : d.showPath(),
                o.has("hotbarOnly") ? o.get("hotbarOnly").bool() : d.hotbarOnly());
    }

    public void validate() throws ConfigException {
        if (mode == null) {
            throw ConfigException.of("automation.mode", "required");
        }
        Validators.intRange("automation.reachRadius", reachRadius, MIN_RADIUS, MAX_RADIUS);
        Validators.intRange("automation.searchNodes", searchNodes, MIN_NODES, MAX_NODES);
        Validators.finite("automation.healthFloor", healthFloor, 0, 20);
        Validators.intRange("automation.foodFloor", foodFloor, 0, 20);
        Validators.finite("automation.dangerRadius", dangerRadius, 0, 32);
        Validators.finite("automation.replanDistance", replanDistance, 0.5, 8);
    }
}

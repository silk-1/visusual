package silkvis.config;

import silkvis.config.json.JsonObject;
import silkvis.media.SessionSelection;

/** Media provider settings (MEDIA-*): disabled by default, nothing is polled until enabled. */
public record MediaConfig(
        boolean enabled,
        SessionSelection selection,
        String pinnedAppId,
        boolean hudWidgetEnabled,
        boolean pauseSourceOnWorldExit,
        NowPlayingVariant variant,
        boolean showArtwork
) {
    /**
     * Backward-compatible constructor: keeps existing call sites (and configs written by
     * 0.1.10 and earlier) working, with the defaults a new user gets.
     */
    public MediaConfig(boolean enabled, SessionSelection selection, String pinnedAppId,
                       boolean hudWidgetEnabled, boolean pauseSourceOnWorldExit) {
        this(enabled, selection, pinnedAppId, hudWidgetEnabled, pauseSourceOnWorldExit,
                NowPlayingVariant.CARD, true);
    }

    public MediaConfig {
        if (selection == null) {
            selection = SessionSelection.AUTO;
        }
        if (pinnedAppId == null) {
            pinnedAppId = "";
        }
        if (variant == null) {
            variant = NowPlayingVariant.CARD;
        }
    }

    public MediaConfig withVariant(NowPlayingVariant value) {
        return new MediaConfig(enabled, selection, pinnedAppId, hudWidgetEnabled,
                pauseSourceOnWorldExit, value, showArtwork);
    }

    public MediaConfig withArtwork(boolean value) {
        return new MediaConfig(enabled, selection, pinnedAppId, hudWidgetEnabled,
                pauseSourceOnWorldExit, variant, value);
    }

    public static MediaConfig defaults() {
        return new MediaConfig(false, SessionSelection.AUTO, "", false, true,
                NowPlayingVariant.CARD, true);
    }

    public JsonObject encode() {
        return silkvis.config.json.JsonValue.newObject()
                .put("enabled", enabled)
                .put("selection", selection.name())
                .put("pinnedAppId", pinnedAppId)
                .put("hudWidgetEnabled", hudWidgetEnabled)
                .put("pauseSourceOnWorldExit", pauseSourceOnWorldExit)
                .put("variant", variant.name())
                .put("showArtwork", showArtwork);
    }

    public static MediaConfig decode(JsonObject o) throws ConfigException {
        MediaConfig d = defaults();
        boolean enabled = o.has("enabled") ? o.get("enabled").bool() : d.enabled();
        SessionSelection selection = d.selection();
        if (o.has("selection")) {
            String raw = o.get("selection").string();
            try {
                selection = SessionSelection.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ConfigException.of("media.selection", "unknown value");
            }
        }
        String pinned = o.has("pinnedAppId") ? o.get("pinnedAppId").string() : d.pinnedAppId();
        pinned = pinned == null ? "" : pinned.trim();
        if (!pinned.isEmpty()) {
            Validators.plainText("media.pinnedAppId", pinned, 64);
        }
        boolean hudWidget = o.has("hudWidgetEnabled") ? o.get("hudWidgetEnabled").bool() : d.hudWidgetEnabled();
        boolean pauseOnExit = o.has("pauseSourceOnWorldExit")
                ? o.get("pauseSourceOnWorldExit").bool()
                : d.pauseSourceOnWorldExit();
        NowPlayingVariant variant = d.variant();
        if (o.has("variant")) {
            String raw = o.get("variant").string();
            try {
                variant = NowPlayingVariant.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // Unknown value from a newer build: fall back instead of rejecting the file.
                variant = d.variant();
            }
        }
        boolean showArtwork = o.has("showArtwork") ? o.get("showArtwork").bool() : d.showArtwork();
        return new MediaConfig(enabled, selection, pinned, hudWidget, pauseOnExit, variant, showArtwork);
    }

    public void validate() throws ConfigException {
        if (!pinnedAppId.isEmpty()) {
            Validators.plainText("media.pinnedAppId", pinnedAppId, 64);
        }
    }
}

package silkvis.ui;

/**
 * Design tokens for the approved Graphite/Turquoise theme (UI-01, UI-02, UI-07).
 * Values are logical GUI units and sRGB colours with explicit alpha.
 */
public record ThemeTokens(
        String id,
        Color background,
        Color surface,
        Color surfaceHover,
        Color border,
        Color accent,
        Color accentHover,
        Color textPrimary,
        Color textSecondary,
        Color success,
        Color warning,
        Color error,
        int spacingXs,
        int spacingSm,
        int spacingMd,
        int spacingLg,
        int spacingXl,
        int radiusPanel,
        int radiusControl,
        int controlHeight,
        int minHitSize,
        int motionMs,
        int tooltipDelayMs
) {
    /**
     * Density variant used by the visual presets. LOW packs the interface (smaller radii,
     * tighter spacing, less rounded controls), QUALITY gives it more air. Before 0.1.15 the
     * preset was stored, shown in the footer and applied to nothing at all.
     */
    public ThemeTokens withDensity(int newRadiusPanel, int newRadiusControl, int spacingDelta) {
        return new ThemeTokens(id, background, surface, surfaceHover, border, accent, accentHover,
                textPrimary, textSecondary, success, warning, error,
                Math.max(0, spacingXs + spacingDelta), Math.max(0, spacingSm + spacingDelta),
                Math.max(0, spacingMd + spacingDelta), Math.max(0, spacingLg + spacingDelta),
                Math.max(0, spacingXl + spacingDelta), Math.max(0, newRadiusPanel),
                Math.max(0, newRadiusControl), controlHeight, minHitSize, motionMs, tooltipDelayMs);
    }

    /** Reduced motion: every transition collapses to zero duration. */
    public ThemeTokens withMotion(int durationMs) {
        return new ThemeTokens(id, background, surface, surfaceHover, border, accent, accentHover,
                textPrimary, textSecondary, success, warning, error, spacingXs, spacingSm,
                spacingMd, spacingLg, spacingXl, radiusPanel, radiusControl, controlHeight,
                minHitSize, Math.max(0, durationMs), tooltipDelayMs);
    }

    /** Graphite / Turquoise, dark matte glass (0.3). */
    public static ThemeTokens graphiteTurquoise() {
        return new ThemeTokens(
                "graphite_turquoise",
                Color.hex("#10141C").withAlpha(0.92f),
                Color.hex("#1B2330").withAlpha(0.96f),
                Color.hex("#253244").withAlpha(0.96f),
                Color.hex("#FFFFFF").withAlpha(0.10f),
                Color.hex("#59D8C5"),
                Color.hex("#7AE5D5"),
                Color.hex("#EDF2F7"),
                Color.hex("#98A6B8"),
                Color.hex("#68D391"),
                Color.hex("#F4C46B"),
                Color.hex("#F07878"),
                4, 8, 12, 16, 24,
                8, 4, 26, 20,
                160, 350);
    }

    /** Contrast-safe variant used when blur/transparency is unavailable (UI-08). */
    public ThemeTokens opaqueFallback() {
        return new ThemeTokens(id + "_opaque", background.withAlpha(1f), surface.withAlpha(1f),
                surfaceHover.withAlpha(1f), border, accent, accentHover, textPrimary, textSecondary,
                success, warning, error, spacingXs, spacingSm, spacingMd, spacingLg, spacingXl,
                radiusPanel, radiusControl, controlHeight, minHitSize, motionMs, tooltipDelayMs);
    }

    /** Motion duration respects the reduced-motion setting (UI-07). */
    public int motionDuration(boolean reducedMotion) {
        return reducedMotion ? 0 : motionMs;
    }
}

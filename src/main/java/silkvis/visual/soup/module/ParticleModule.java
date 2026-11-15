package silkvis.visual.soup.module;

import silkvis.visual.soup.setting.MultiColorSetting;
import silkvis.visual.soup.setting.SelectSetting;

/**
 * Ported from SoupVisuals {@code padej.soup.api.feature.module.IParticleModule} (Padej_).
 *
 * <p>Every particle module of the original exposes the same three things to its renderers:
 * the colour mode select, the colour animation select and the resolved custom colours. The
 * renderers below take this interface, so HitBubbles does not have to know about
 * TotemParticles and vice versa.</p>
 */
public interface ParticleModule {
    SelectSetting getColorMode();

    SelectSetting getColorAnimation();

    /**
     * ARGB colours of the custom gradient, or {@code null} when the module runs in "Sync"
     * mode -- the original returns {@code null} here as well and the renderer falls back to
     * the client colour.
     */
    int[] getCustomColors();

    /** Palette backing {@link #getCustomColors()}; used by the colour settings screen. */
    MultiColorSetting getMultiColorSetting();
}

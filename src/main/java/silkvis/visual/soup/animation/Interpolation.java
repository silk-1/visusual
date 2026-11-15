package silkvis.visual.soup.animation;

/** Ported from SoupVisuals {@code padej.soup.base.util.animation.Interpolation} (Padej_). */
public interface Interpolation {
    double interpolate(double progress);

    String getName();
}

package silkvis.ui.hud;

/**
 * Anchor + logical offsets instead of absolute screen pixels (HUD-02).
 * {@code fx}/{@code fy} are the fractions of the free space used to place the widget.
 */
public enum Anchor {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    MIDDLE_LEFT(0f, 0.5f),
    CENTER(0.5f, 0.5f),
    MIDDLE_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f);

    private final float fx;
    private final float fy;

    Anchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }

    public float fractionX() {
        return fx;
    }

    public float fractionY() {
        return fy;
    }

    /**
     * Offset direction: +1 means "away from the anchored edge". A centred anchor used to
     * return 0, which silently threw its offset away: a BOTTOM_CENTER widget with offsetY 80
     * was drawn at the bottom edge and the editor could not move it.
     */
    public int offsetSignX() {
        return fx == 1f ? -1 : 1;
    }

    public int offsetSignY() {
        return fy == 1f ? -1 : 1;
    }
}

package silkvis.automation;

/**
 * Movement cost model, ported in spirit from Baritone's {@code MovementCosts} but written from
 * scratch and much smaller.
 *
 * <p>Three numbers decide everything a walking goal needs: what a flat step costs, what going up
 * or down costs, and what a diagonal costs. The interesting part is not the arithmetic but the
 * ordering: breaking a block must always be more expensive than any pure-movement alternative,
 * or a "go to" goal happily tunnels through a house instead of walking around it. That invariant
 * is asserted in the self checks, not left to memory.</p>
 */
public final class PathCost {
    /** A flat step to an adjacent cell. */
    public static final double WALK = 1.0;
    /** Diagonal step on the same level (real distance, but the player moves on one axis first). */
    public static final double DIAGONAL = 1.4142;
    /** Step up one block: needs a jump, costs a bit more than walking. */
    public static final double STEP_UP = 1.5;
    /** Step down one block: cheap, but not free (fall damage and control). */
    public static final double STEP_DOWN = 1.2;
    /** Falling two or three blocks. */
    public static final double FALL_2 = 2.0;
    public static final double FALL_3 = 3.0;
    /** Walking through water: slower, and the player may be pushed. */
    public static final double SWIM = 3.0;
    /** Opening a passable door. */
    public static final double DOOR = 1.0;
    /** Breaking one block: deliberately above any movement alternative. */
    public static final double BREAK = 8.0;
    /** Placing one block (bridging, pillaring). */
    public static final double PLACE = 6.0;
    /** Jumping a gap of one block (parkour); the executor only attempts this on flat ground. */
    public static final double GAP_JUMP = 4.0;

    /** Everything above this is never considered by the search. */
    public static final double IMPOSSIBLE = Double.POSITIVE_INFINITY;

    private PathCost() {
    }

    /**
     * Cheapest way to get from one cell to a neighbour, given what the neighbour's cell and the
     * cell above it look like. The caller has already decided that the move is possible; this
     * only prices it.
     */
    public static double of(int dy, boolean diagonal, boolean occupied, boolean water,
                            boolean door, boolean needsBreak) {
        if (needsBreak) {
            return BREAK;
        }
        if (occupied) {
            return DOOR;
        }
        double base;
        if (dy == 0) {
            base = diagonal ? DIAGONAL : WALK;
        } else if (dy == 1) {
            base = STEP_UP;
        } else if (dy == -1) {
            base = STEP_DOWN;
        } else if (dy == -2) {
            base = FALL_2;
        } else if (dy == -3) {
            base = FALL_3;
        } else {
            return IMPOSSIBLE;
        }
        return water ? base + SWIM : base;
    }
}

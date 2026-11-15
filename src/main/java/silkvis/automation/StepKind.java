package silkvis.automation;

/**
 * The primitive actions a plan step can be made of. A goal never walks, looks or digs itself:
 * it produces steps, and the executor is the only code that turns a step into input. That split
 * is what makes the whole engine testable without a client and what makes "stop now" reliable:
 * there is exactly one place that emits input, so stopping is one flag.
 */
public enum StepKind {
    /** Walk to a block position; the executor decides the actual key presses. */
    GOTO,
    /** Break one block. */
    MINE,
    /** Place the held block at a position (used by building goals only). */
    PLACE,
    /** Rotate the camera to look at a position. */
    LOOK,
    /** Attack the entity the goal resolved this tick. */
    ATTACK,
    /** Use the held item (eat, throw, place) once. */
    USE_ITEM,
    /** Select the hotbar slot with the needed tool. */
    SELECT_SLOT,
    /** Wait for the given number of ticks (block update, item pickup, cooldown). */
    WAIT
}

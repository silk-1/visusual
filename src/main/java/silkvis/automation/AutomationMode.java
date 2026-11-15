package silkvis.automation;

/**
 * How much of the player's input an automation goal is allowed to take over.
 *
 * <p>This is the one thing a player must be able to reason about before switching anything on,
 * so it is an explicit, validated setting rather than a hidden per-feature rule. The values are
 * ordered: a lower mode never does more than a higher one.</p>
 */
public enum AutomationMode {
    /** Off. The goal engine is not ticked at all. */
    OFF,
    /**
     * The engine only suggests: a plan and a status line for the screen, no key presses, no
     * block interaction, no rotation. Safe to leave on for a whole session.
     */
    HINT,
    /**
     * The engine rotates the camera and presses keys, but never breaks or places a block. This
     * is where "walk me there" and "look at the target" live.
     */
    MOVE,
    /**
     * Full control of movement, rotation and block interaction for the enabled goal. The
     * server sees ordinary player input, because that is what it is: the same calls the vanilla
     * input path makes, not packets sent behind its back.
     */
    FULL
}

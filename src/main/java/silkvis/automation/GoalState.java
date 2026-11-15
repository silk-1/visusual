package silkvis.automation;

/**
 * Lifecycle of one goal run. Deliberately small: a goal that wants more states will grow them
 * here, but every state has to be reachable from the tick body or it is dead code.
 */
public enum GoalState {
    /** Never started, or reset after a world change. */
    IDLE,
    /** Building the next plan step; no input is emitted in this state. */
    PLANNING,
    /** Executing the current step (walking, looking, breaking). */
    RUNNING,
    /** Waiting for the world to change (a block to fall, an item to appear). */
    WAITING,
    /** Finished successfully; stops emitting input. */
    DONE,
    /** Stopped because it cannot continue (no path, no tool, no target, safety stop). */
    FAILED
}

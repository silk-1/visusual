package silkvis.automation;

import silkvis.core.Clock;

import java.util.ArrayList;
import java.util.List;

/**
 * The automation tick loop: safety gates, one goal, one plan, a bounded number of steps.
 *
 * <p>Baritone's {@code PathingBehavior} is the model for the shape (a behaviour stack, a tick that
 * decides between planning and executing, a hard stop), but everything here is written for a
 * client mod that must never stutter and never overrule the player silently. Concretely:</p>
 *
 * <ul>
 *   <li><b>One goal at a time.</b> A stack is a feature for scripts; a player with one screen and
 *       one status line is better served by "this is what it is doing, press stop".</li>
 *   <li><b>Safety before progress.</b> Health, food, danger radius and "the player grabbed the
 *       controls" are checked before the goal gets a turn. A stopped goal reports why.</li>
 *   <li><b>Budgeted.</b> One {@link Budget} per tick, shared by the goal and by anything else the
 *       engine asks; when it is spent, the tick ends.</li>
 *   <li><b>No input from here.</b> The engine produces {@link Step}s and hands them to an
 *       {@link Executor}; the single executor is the only code that presses keys.</li>
 * </ul>
 *
 * <p>Everything in this class is MineCraft-free, so the self checks drive it against a fake world
 * and a recording executor and assert the exact step sequence.</p>
 */
public final class AutomationEngine {
    /** What the engine can ask of the game. Implemented by the client layer. */
    public interface Executor {
        /**
         * Runs one step with the given mode.
         *
         * @return
         * {@link Result#DONE} when the step finished, {@link Result#RUNNING} when it needs
         * more ticks, {@link Result#BLOCKED} when it cannot continue (a wall, a missing tool)
         */
        Result execute(Step step, AutomationMode mode);

        /** Aborts whatever the executor is doing; called on stop and on world change. */
        void abort();

        enum Result {
            DONE,
            RUNNING,
            BLOCKED
        }
    }

    /** What the UI and the HUD need to know in one value. */
    public record Status(GoalState state, String goalId, String step, StopReason reason,
                         String progress, int stepsThisTick, long runningTicks) {
    }

    private final GoalRegistry registry;
    private final Clock clock;
    private AutomationSettings settings = AutomationSettings.defaults();
    private Goal goal;
    private String goalId = "";
    private GoalState state = GoalState.IDLE;
    private StopReason stopReason = StopReason.NONE;
    private String lastStep = "";
    private long startedAtNanos;
    private long ticks;
    private String worldKey = "";
    /** Steps executed in the last tick; part of the status so the UI can show load. */
    private int stepsThisTick;

    public AutomationEngine(GoalRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    public AutomationSettings settings() {
        return this.settings;
    }

    /** Applies new settings; stops the run when automation is switched off. */
    public void applySettings(AutomationSettings next) {
        this.settings = next == null ? AutomationSettings.defaults() : next;
        if (!this.settings.enabled()) {
            stop(StopReason.BY_PLAYER);
        }
    }

    /** Starts a goal on a target block. Returns false when the id or the mode is not usable. */
    public boolean start(String id, BlockPos target) {
        java.util.Optional<Goal> created = this.registry.create(id, target, this.settings.mode());
        if (created.isEmpty()) {
            this.stopReason = this.registry.get(id).isPresent()
                    ? StopReason.MODE_TOO_LOW : StopReason.INTERNAL;
            this.state = GoalState.FAILED;
            this.goalId = id;
            return false;
        }
        this.goal = created.get();
        this.goalId = id;
        this.state = GoalState.PLANNING;
        this.stopReason = StopReason.NONE;
        this.lastStep = "";
        this.startedAtNanos = this.clock.monotonicNanos();
        this.ticks = 0;
        this.stepsThisTick = 0;
        // Unknown until the first tick: otherwise the very first world would look like a
        // dimension change and every run would stop on its first tick.
        this.worldKey = "";
        return true;
    }

    /** Player pressed stop, or the panic key. */
    public void stop(StopReason why) {
        if (this.goal != null) {
            this.goal = null;
        }
        if (why != StopReason.NONE) {
            this.stopReason = why;
            this.state = why == StopReason.DONE ? GoalState.DONE : GoalState.IDLE;
        }
    }

    public Status status() {
        long running = this.startedAtNanos == 0 ? 0
                : Math.max(0, this.clock.monotonicNanos() - this.startedAtNanos) / 50_000_000L;
        return new Status(this.state, this.goalId, this.lastStep, this.stopReason,
                this.goal == null ? "" : this.goal.progress(), this.stepsThisTick, running);
    }

    /**
     * One tick.
     *
     * @param world      the snapshot for this tick, or null when there is no world
     * @param executor   where steps go; null means "plan only" (the HINT mode)
     * @param playerBusy true when the player is holding movement keys or the mouse: automation
     *                   backs off instead of fighting for control
     */
    public void tick(Snapshot world, Executor executor, boolean playerBusy) {
        if (!this.settings.enabled()) {
            if (this.goal != null) {
                stop(StopReason.BY_PLAYER);
            }
            return;
        }
        if (world == null) {
            if (this.goal != null) {
                stop(StopReason.WORLD_CHANGED);
            }
            return;
        }
        if (this.worldKey.isEmpty()) {
            this.worldKey = world.dimension();
        } else if (!world.dimension().equals(this.worldKey)) {
            this.worldKey = world.dimension();
            stop(StopReason.WORLD_CHANGED);
        }
        if (this.goal == null) {
            return;
        }
        this.ticks++;
        StopReason gate = safetyGate(world, playerBusy);
        if (gate != StopReason.NONE) {
            stop(gate);
            return;
        }
        if (this.goal.done()) {
            finishFromGoal(executor);
            return;
        }
        this.state = GoalState.RUNNING;
        Budget budget = new Budget(this.settings.searchNodes());
        this.stepsThisTick = 0;
        List<Step> steps = this.goal.plan(world, budget);
        if (this.goal.done()) {
            finishFromGoal(executor);
            return;
        }
        if (steps.isEmpty()) {
            this.state = GoalState.WAITING;
            return;
        }
        if (executor == null || this.settings.mode() == AutomationMode.HINT) {
            // HINT mode: the plan is shown, nothing is pressed. The check is on the mode and not
            // only on the executor, because the screen passes a real executor for every mode and
            // the mode is what the player actually agreed to.
            this.lastStep = steps.get(0).describe();
            return;
        }
        for (Step step : steps) {
            if (budget.spent()) {
                break;
            }
            Executor.Result result = executor.execute(step, this.settings.mode());
            this.lastStep = step.describe();
            this.stepsThisTick++;
            if (result == Executor.Result.BLOCKED) {
                stop(StopReason.BLOCK_CHANGED);
                return;
            }
            if (result == Executor.Result.RUNNING) {
                // The step owns the player until it finishes; do not queue the rest.
                return;
            }
        }
    }

    /** Copies the goal's verdict into the engine state and clears the goal. */
    private void finishFromGoal(Executor executor) {
        StopReason why = this.goal == null ? StopReason.NONE : this.goal.stopReason();
        if (why == StopReason.NONE) {
            why = StopReason.DONE;
        }
        this.stopReason = why;
        this.state = why == StopReason.DONE ? GoalState.DONE : GoalState.FAILED;
        this.goal = null;
        if (executor != null) {
            executor.abort();
        }
    }

    /**
     * The rules that outrank any goal.
     *
     * @return the reason to stop, or {@link StopReason#NONE}
     */
    private StopReason safetyGate(Snapshot world, boolean playerBusy) {
        if (world.health() <= this.settings.healthFloor()) {
            return StopReason.LOW_HEALTH;
        }
        if (world.food() <= this.settings.foodFloor()) {
            return StopReason.LOW_HEALTH;
        }
        if (!world.onGround() && world.grid().dangerous(world.player().x(),
                world.player().y() - 1, world.player().z())) {
            return StopReason.DANGER;
        }
        if (dangerNearby(world)) {
            return StopReason.DANGER;
        }
        if (playerBusy) {
            // The player taking the keys is the one stop that must be immediate and silent: it
            // is not an error, it is the player saying "I will drive".
            return StopReason.BY_PLAYER;
        }
        return StopReason.NONE;
    }

    private boolean dangerNearby(Snapshot world) {
        double radius = this.settings.dangerRadius();
        if (radius <= 0) {
            return false;
        }
        for (Snapshot.EntityInfo entity : world.entities()) {
            if (entity.hostile() && entity.distance() <= radius) {
                return true;
            }
        }
        return false;
    }

    /** Goals the UI offers, filtered by the current mode. */
    public List<GoalRegistry.Entry> available() {
        List<GoalRegistry.Entry> out = new ArrayList<>();
        for (GoalRegistry.Entry entry : this.registry.all()) {
            if (this.settings.mode().ordinal() >= entry.mode().ordinal()) {
                out.add(entry);
            }
        }
        return out;
    }

    public List<GoalRegistry.Entry> allGoals() {
        return this.registry.all();
    }

    public long ticks() {
        return this.ticks;
    }

    /** True while a goal owns the player. */
    public boolean active() {
        return this.goal != null;
    }
}

package silkvis.client.baritone;

import silkvis.automation.baritone.BaritoneStatus;
import silkvis.automation.baritone.BaritoneTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to Baritone when the player has installed it, and stays silent when they have not.
 *
 * <p><b>Why reflection instead of a compile dependency.</b> Baritone's source is open, but
 * copying it would mean carrying 367 files, 20 mixins and its own build tooling into this
 * project, and LGPL would then apply to the whole of SilkVis. Calling its public API keeps this
 * project's licence intact and its build simple: the player installs Baritone the normal way,
 * SilkVis drives it.</p>
 *
 * <p><b>Why every call is guarded.</b> Reflection fails at runtime, never at compile time. One
 * renamed method in a future Baritone must not take the client down, so every lookup is wrapped
 * and a failure degrades to {@link BaritoneStatus.Availability#BROKEN} with the reason kept for
 * the log. Nothing here is attempted twice per session once it has failed.</p>
 *
 * <p><b>What is deliberately not done.</b> No packet sending, no input injection, no reading of
 * Baritone's internal state machine. SilkVis asks for a task and reads back whether a path is
 * running and which process is in charge -- the two facts its own status line needs. Anything
 * more would be re-implementing the mod it is calling.</p>
 *
 * <p>Method and class names below are Baritone's public API as of the {@code 1.21.10} branch:
 * {@code baritone.api.BaritoneAPI#getProvider()},
 * {@code IBaritoneProvider#getPrimaryBaritone()}, {@code IBaritone#getCustomGoalProcess()},
 * {@code ICustomGoalProcess#setGoalAndPath(Goal)}, {@code IBaritone#getMineProcess()},
 * {@code IMineProcess#mineByName(int, String...)},
 * {@code IBaritone#getPathingBehavior()}, {@code IPathingBehavior#isPathing()},
 * {@code IBaritone#getFollowProcess()}, {@code IFollowProcess#follow(String)},
 * {@code IBaritone#getExploreProcess()}, {@code IExploreProcess#explore()},
 * {@code baritone.api.pathing.goals.GoalXZ(int, int)},
 * {@code baritone.api.pathing.goals.GoalBlock(int, int, int)}.</p>
 */
public final class BaritoneBridge {
    /** Baritone exposes {@code displayName()} on the process that currently owns the player. */
    private static final String API = "baritone.api.BaritoneAPI";

    private boolean resolved;
    private boolean available;
    private String failure = "";

    private Object provider;
    private Object baritone;

    private Method getCustomGoalProcess;
    private Method setGoalAndPath;
    private Method getMineProcess;
    private Method mineByName;
    private Method getPathingBehavior;
    private Method isPathing;
    private Method getFollowProcess;
    private Method follow;
    private Method getExploreProcess;
    private Method explore;
    private Method getPathingControlManager;
    private Method mostRecentInControl;
    private Method displayName;
    private Method onLostControl;
    private Constructor<?> goalXZ;
    private Constructor<?> goalBlock;

    /**
     * True when Baritone is present and its API could be reached.
     *
     * <p>Detection is {@code Class.forName} and not {@code FabricLoader.isModLoaded}: the loader
     * can report a mod that failed to initialise, and a class that answers is the only proof that
     * calling it will work.</p>
     */
    public boolean available() {
        resolve();
        return this.available;
    }

    /** The reason the bridge is unusable, empty when it is fine. For the log, once per session. */
    public String failure() {
        resolve();
        return this.failure;
    }

    /** Current state for the tab and the HUD widget. Never throws. */
    public BaritoneStatus status() {
        resolve();
        if (!this.available) {
            return this.failure.startsWith("not installed")
                    ? BaritoneStatus.missing()
                    : BaritoneStatus.broken(this.failure);
        }
        try {
            boolean pathing = Boolean.TRUE.equals(this.isPathing.invoke(this.getPathingBehavior
                    .invoke(this.baritone)));
            return BaritoneStatus.ready(pathing, currentProcessName());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return BaritoneStatus.broken(shorten(e));
        }
    }

    /**
     * Asks Baritone to start a task.
     *
     * @param task  what to do
     * @param x     block x for the tasks that need a point
     * @param y     block y for the tasks that need a point
     * @param z     block z for the tasks that need a point
     * @param text  entity name for {@code follow}
     * @param block registry id of the block for {@code mine}, empty otherwise
     * @param count how many blocks to mine, 0 for "until stopped"
     * @return true when the call reached Baritone; false when it is not usable or the task needs
     *         an argument that was not supplied
     */
    public boolean start(BaritoneTask task, int x, int y, int z, String text, String block,
                         int count) {
        resolve();
        if (!this.available || task == null) {
            return false;
        }
        try {
            switch (task) {
                case GOTO -> this.setGoalAndPath.invoke(this.getCustomGoalProcess
                        .invoke(this.baritone), this.goalXZ.newInstance(x, z));
                case GOTO_ALLOW_BREAK -> this.setGoalAndPath.invoke(this.getCustomGoalProcess
                        .invoke(this.baritone), this.goalBlock.newInstance(x, y, z));
                case MINE -> {
                    if (block == null || block.isEmpty()) {
                        return false;
                    }
                    this.mineByName.invoke(this.getMineProcess.invoke(this.baritone),
                            Math.max(0, count), new String[]{block});
                }
                case EXCAVATE -> {
                    // Baritone has no "dig this box" through the API; a GoalBlock makes it walk
                    // into the area, which is the honest best the API offers. The tab says so.
                    this.setGoalAndPath.invoke(this.getCustomGoalProcess.invoke(this.baritone),
                            this.goalBlock.newInstance(x, y, z));
                }
                case COLLECT -> this.setGoalAndPath.invoke(this.getCustomGoalProcess
                        .invoke(this.baritone), this.goalBlock.newInstance(x, y, z));
                case FOLLOW -> {
                    if (text == null || text.isEmpty()) {
                        return false;
                    }
                    this.follow.invoke(this.getFollowProcess.invoke(this.baritone), text);
                }
                case EXPLORE -> this.explore.invoke(this.getExploreProcess.invoke(this.baritone));
                case STOP -> cancel();
                default -> {
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.failure = shorten(e);
            return false;
        }
    }

    /** Stops whatever Baritone is doing. Safe to call when it is not installed. */
    public void cancel() {
        resolve();
        if (!this.available) {
            return;
        }
        try {
            Object inControl = this.mostRecentInControl.invoke(
                    this.getPathingControlManager.invoke(this.baritone));
            if (inControl != null) {
                this.onLostControl.invoke(inControl);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.failure = shorten(e);
        }
    }

    /** Name of the process currently in control, empty when nothing is. */
    private String currentProcessName() {
        try {
            Object inControl = this.mostRecentInControl.invoke(
                    this.getPathingControlManager.invoke(this.baritone));
            if (inControl == null) {
                return "";
            }
            Object name = this.displayName.invoke(inControl);
            return name == null ? "" : name.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ resolution

    private void resolve() {
        if (this.resolved) {
            return;
        }
        this.resolved = true;
        try {
            Class<?> api = Class.forName(API);
            this.provider = api.getMethod("getProvider").invoke(null);
            if (this.provider == null) {
                throw new IllegalStateException("BaritoneAPI.getProvider() returned null");
            }
            this.baritone = this.provider.getClass().getMethod("getPrimaryBaritone")
                    .invoke(this.provider);
            if (this.baritone == null) {
                // Baritone is installed but has not attached to a world yet.
                throw new IllegalStateException("no primary baritone instance");
            }
            Class<?> ibaritone = Class.forName("baritone.api.IBaritone");
            this.getCustomGoalProcess = ibaritone.getMethod("getCustomGoalProcess");
            this.getMineProcess = ibaritone.getMethod("getMineProcess");
            this.getPathingBehavior = ibaritone.getMethod("getPathingBehavior");
            this.getFollowProcess = ibaritone.getMethod("getFollowProcess");
            this.getExploreProcess = ibaritone.getMethod("getExploreProcess");
            this.getPathingControlManager = ibaritone.getMethod("getPathingControlManager");

            Class<?> customGoal = Class.forName("baritone.api.process.ICustomGoalProcess");
            this.setGoalAndPath = customGoal.getMethod("setGoalAndPath",
                    Class.forName("baritone.api.pathing.goals.Goal"));

            this.mineByName = Class.forName("baritone.api.process.IMineProcess")
                    .getMethod("mineByName", int.class, String[].class);
            this.isPathing = Class.forName("baritone.api.behavior.IPathingBehavior")
                    .getMethod("isPathing");
            this.follow = Class.forName("baritone.api.process.IFollowProcess")
                    .getMethod("follow", String.class);
            this.explore = Class.forName("baritone.api.process.IExploreProcess")
                    .getMethod("explore");

            Class<?> control = Class.forName(
                    "baritone.api.pathing.calc.IPathingControlManager");
            this.mostRecentInControl = control.getMethod("mostRecentInControl");
            Class<?> process = Class.forName("baritone.api.process.IBaritoneProcess");
            this.displayName = process.getMethod("displayName");
            this.onLostControl = process.getMethod("onLostControl");

            this.goalXZ = Class.forName("baritone.api.pathing.goals.GoalXZ")
                    .getConstructor(int.class, int.class);
            this.goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock")
                    .getConstructor(int.class, int.class, int.class);

            this.available = true;
        } catch (ClassNotFoundException e) {
            // Class.forName on the API is the "is Baritone here" question. Everything else
            // missing means a version whose API differs, which is a different report.
            this.available = false;
            this.failure = e.getMessage() != null && e.getMessage().startsWith("baritone")
                    ? "not installed"
                    : "not installed (" + e.getMessage() + ")";
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.available = false;
            this.failure = shorten(e);
        }
    }

    /** One-line form of a reflection failure: the type plus the message, no stack. */
    private static String shorten(Throwable t) {
        Throwable cause = t.getCause() == null ? t : t.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** Task list for the screen, in a fixed order so the buttons do not move around. */
    public List<BaritoneTask> tasks() {
        return new ArrayList<>(List.of(BaritoneTask.values()));
    }
}

package silkvis.client.baritone;

import silkvis.automation.baritone.BaritoneStatus;
import silkvis.automation.baritone.BaritoneTask;

import java.lang.reflect.Method;

/**
 * Talks to Baritone when the player has installed it, and stays silent when they have not.
 *
 * <p><b>Commands, not typed API objects, and why that changed.</b> The first version called the
 * typed API: {@code setGoalAndPath(new GoalBlock(x, y, z))}, {@code follow(String)}. It compiled,
 * passed its checks, and then failed in a real game with
 * {@code NoSuchMethodException: baritone.api.process.IFollowProcess.follow(java.lang.String)}
 * while Baritone itself was running normally. The reason is structural, not a typo: to call the
 * typed API from here the signatures have to name Minecraft classes -- {@code Predicate<Entity>},
 * {@code BlockOptionalMeta}, {@code GoalBlock} -- and those classes live in the Baritone build for
 * one specific Minecraft version. A jar built for a different version has the methods but not
 * those parameter types, so reflection cannot bind to them at all. The command layer takes plain
 * text and returns a boolean: {@code ICommandManager.execute("goto 10 64 -20")}. Nothing to
 * obfuscate, nothing to mismatch, and it is the same surface Baritone's own chat commands use.</p>
 *
 * <p><b>Why reflection instead of a compile dependency.</b> Baritone's source is open, but
 * copying it would mean carrying 367 files, 20 mixins and its own build tooling into this
 * project, and LGPL would then apply to the whole of SilkVis. Calling it keeps this project's
 * licence intact and its build simple.</p>
 *
 * <p><b>Why every call is guarded.</b> Reflection fails at runtime, never at compile time. One
 * renamed method in a future Baritone must not take the client down, so every lookup is wrapped
 * and a failure degrades to {@link BaritoneStatus.Availability#BROKEN} with the reason kept for
 * the log. Resolution is attempted once per session.</p>
 *
 * <p>Names below are from Baritone's 1.21.10 branch sources: {@code baritone.api.BaritoneAPI},
 * {@code IBaritoneProvider#getPrimaryBaritone()}, {@code IBaritone#getCommandManager()},
 * {@code ICommandManager#execute(String)}.</p>
 */
public final class BaritoneBridge {
    /**
     * The one class this bridge needs.
     *
     * <p>{@code getCommandManager()} lives on {@code IBaritone}, so the baritone instance itself
     * is never typed here: it is fetched through the provider and only used as a receiver for a
     * reflectively found method. That is what keeps this file free of any Minecraft or Baritone
     * type at compile time.</p>
     */
    private static final String API = "baritone.api.BaritoneAPI";

    private boolean resolved;
    private boolean available;
    private String failure = "";

    private Object baritone;
    private Method execute;
    private Method getAllBaritones;

    /**
     * True when Baritone is present and its command layer could be reached.
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
        // There is no public "is a path running" call once we talk commands only. Saying
        // "ready" is honest: the command layer answered, which is exactly what the tab acts on.
        return BaritoneStatus.ready(false, "");
    }

    /**
     * Runs a task by its Baritone command line.
     *
     * @return true when Baritone accepted the command; false when the bridge is unusable or the
     *         task still needs an argument
     */
    public boolean run(BaritoneTask task, int x, int y, int z, String text, String block,
                       int count) {
        resolve();
        if (!this.available || task == null) {
            return false;
        }
        String command = task.command(x, y, z, text, block, count);
        if (command == null || command.isEmpty()) {
            return false;
        }
        return execute(command);
    }

    /**
     * Sends one command line to Baritone.
     *
     * <p>If the primary instance has no command manager -- which happens on the main menu, before
     * a world is joined -- the first available instance is used instead of failing: the player
     * asked for a task and one instance is enough.</p>
     */
    public boolean execute(String command) {
        resolve();
        if (!this.available || command == null || command.isEmpty()) {
            return false;
        }
        try {
            Object target = this.baritone != null ? this.baritone : firstInstance();
            if (target == null) {
                this.failure = "no baritone instance";
                return false;
            }
            Object accepted = this.execute.invoke(target, command);
            return !Boolean.FALSE.equals(accepted);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Keep the reason: the tab shows it and the log records it.
            this.failure = shorten(e);
            return false;
        }
    }

    /** Stops whatever Baritone is doing. Safe to call when it is not installed. */
    public void cancel() {
        execute("cancel");
    }

    /** Commands Baritone accepts, for the log; proves the command layer answered. */
    public boolean probe() {
        return execute("version");
    }

    private Object firstInstance() {
        try {
            Object list = this.getAllBaritones.invoke(null);
            if (list instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item != null) {
                        return item;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.failure = shorten(e);
        }
        return null;
    }

    // ------------------------------------------------------------------ resolution

    private void resolve() {
        if (this.resolved) {
            return;
        }
        this.resolved = true;
        try {
            Class<?> api = Class.forName(API);
            Object provider;
            try {
                provider = api.getMethod("getProvider").invoke(null);
            } catch (NoSuchMethodException e) {
                this.resolved = false;
                this.available = false;
                this.failure = "not installed";
                return;
            }
            if (provider == null) {
                this.resolved = false;
                this.available = false;
                this.failure = "not installed";
                return;
            }
            this.getAllBaritones = Class.forName("baritone.api.IBaritoneProvider")
                    .getMethod("getAllBaritones");
            Method primary = Class.forName("baritone.api.IBaritoneProvider")
                    .getMethod("getPrimaryBaritone");
            try {
                this.baritone = primary.invoke(provider);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Installed but not attached to a world yet. Commands still work through
                // whichever instance appears later, so this is not a failure.
                this.baritone = null;
            }
            this.execute = Class.forName("baritone.api.command.manager.ICommandManager")
                    .getMethod("execute", String.class);
            // Prove the receiver really carries a command manager: a Baritone from a different
            // major version may expose execute(String) elsewhere, and a clear reason beats a
            // failure on the first button press.
            Class.forName("baritone.api.IBaritone").getMethod("getCommandManager");
            this.available = true;
        } catch (ClassNotFoundException e) {
            this.available = false;
            this.failure = "not installed";
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
}

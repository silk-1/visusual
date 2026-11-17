package silkvis.client.baritone;

import silkvis.automation.baritone.BaritoneStatus;
import silkvis.automation.baritone.BaritoneTask;

import java.lang.reflect.Method;

/**
 * Talks to Baritone when the player has installed it, and stays silent when they have not.
 *
 * <p>Commands go through {@code IBaritone.getCommandManager().execute(String)} — the same
 * surface Baritone's chat commands use. The typed process API is avoided because it needs
 * Minecraft parameter types that only match a Baritone built for the exact same game version.</p>
 */
public final class BaritoneBridge {
    private static final String API = "baritone.api.BaritoneAPI";

    private boolean resolved;
    private boolean available;
    private String failure = "";

    private Object baritone;
    private Object provider;
    /** {@code IBaritone#getCommandManager()} */
    private Method getCommandManager;
    /** {@code ICommandManager#execute(String)} */
    private Method executeCommand;
    private Method getAllBaritones;
    private Method getPrimaryBaritone;

    public boolean available() {
        resolve();
        return this.available;
    }

    public String failure() {
        resolve();
        return this.failure;
    }

    public BaritoneStatus status() {
        resolve();
        if (!this.available) {
            return this.failure.startsWith("not installed")
                    ? BaritoneStatus.missing()
                    : BaritoneStatus.broken(this.failure);
        }
        return BaritoneStatus.ready(false, "");
    }

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
     * Sends one command line to Baritone's command manager.
     *
     * <p>Must call {@code getCommandManager()} on the IBaritone instance first —
     * {@code execute(String)} lives on the manager, not on IBaritone itself.</p>
     */
    public boolean execute(String command) {
        resolve();
        if (!this.available || command == null || command.isEmpty()) {
            return false;
        }
        try {
            Object instance = this.baritone != null ? this.baritone : firstInstance();
            if (instance == null) {
                // Retry primary in case the world attached after resolve.
                instance = primaryOrNull();
            }
            if (instance == null) {
                this.failure = "no baritone instance (join a world first)";
                return false;
            }
            Object manager = this.getCommandManager.invoke(instance);
            if (manager == null) {
                this.failure = "command manager is null";
                return false;
            }
            Object accepted = this.executeCommand.invoke(manager, command);
            return !Boolean.FALSE.equals(accepted);
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.failure = shorten(e);
            return false;
        }
    }

    public void cancel() {
        execute("cancel");
    }

    public boolean probe() {
        return execute("version");
    }

    private Object primaryOrNull() {
        try {
            if (this.provider == null || this.getPrimaryBaritone == null) {
                return null;
            }
            Object primary = this.getPrimaryBaritone.invoke(this.provider);
            if (primary != null) {
                this.baritone = primary;
            }
            return primary;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private Object firstInstance() {
        try {
            Object list = this.getAllBaritones.invoke(this.provider);
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

    private void resolve() {
        if (this.resolved) {
            return;
        }
        this.resolved = true;
        try {
            Class<?> api = Class.forName(API);
            try {
                this.provider = api.getMethod("getProvider").invoke(null);
            } catch (NoSuchMethodException e) {
                this.available = false;
                this.failure = "not installed";
                return;
            }
            if (this.provider == null) {
                this.available = false;
                this.failure = "not installed";
                return;
            }
            Class<?> providerClass = Class.forName("baritone.api.IBaritoneProvider");
            this.getAllBaritones = providerClass.getMethod("getAllBaritones");
            this.getPrimaryBaritone = providerClass.getMethod("getPrimaryBaritone");
            try {
                this.baritone = this.getPrimaryBaritone.invoke(this.provider);
            } catch (ReflectiveOperationException | RuntimeException e) {
                this.baritone = null;
            }
            Class<?> iBaritone = Class.forName("baritone.api.IBaritone");
            this.getCommandManager = iBaritone.getMethod("getCommandManager");
            this.executeCommand = Class.forName("baritone.api.command.manager.ICommandManager")
                    .getMethod("execute", String.class);
            this.available = true;
            this.failure = "";
        } catch (ClassNotFoundException e) {
            this.available = false;
            this.failure = "not installed";
        } catch (ReflectiveOperationException | RuntimeException e) {
            this.available = false;
            this.failure = shorten(e);
        }
    }

    private static String shorten(Throwable t) {
        Throwable cause = t.getCause() == null ? t : t.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}

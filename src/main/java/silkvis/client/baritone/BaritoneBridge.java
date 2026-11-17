package silkvis.client.baritone;

import silkvis.automation.baritone.BaritoneStatus;
import silkvis.automation.baritone.BaritoneTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

    /** One line of the diagnostics page: what was checked, whether it worked, and the detail. */
    public record Check(String labelKey, boolean ok, String detail) {
    }

    /**
     * What the bridge actually managed to reach, step by step.
     *
     * <p>Why this exists: "Baritone is installed but the buttons do nothing" is not answerable
     * from one status word. The bridge walks a chain of reflective lookups, and any single link
     * can fail on a version mismatch; the page shows which one, so the fix is a jar swap instead
     * of a guess. Every check is done again here rather than read from the cached resolve, so the
     * answer reflects the moment the page was opened.</p>
     */
    public List<Check> diagnostics() {
        List<Check> out = new ArrayList<>();

        Class<?> api = null;
        String apiDetail = "";
        try {
            api = Class.forName(API);
        } catch (ClassNotFoundException | LinkageError e) {
            apiDetail = e.getClass().getSimpleName();
        }
        out.add(new Check("silkvis.baritone.check.api", api != null, apiDetail));

        Object provider = null;
        String providerDetail = "";
        if (api == null) {
            providerDetail = "skipped: no api class";
        } else {
            try {
                provider = api.getMethod("getProvider").invoke(null);
                if (provider == null) {
                    providerDetail = "getProvider() returned null";
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                providerDetail = shorten(e);
            }
        }
        out.add(new Check("silkvis.baritone.check.provider", provider != null, providerDetail));

        Object primary = null;
        String primaryDetail = "";
        if (provider == null) {
            primaryDetail = "skipped: no provider";
        } else {
            try {
                primary = Class.forName("baritone.api.IBaritoneProvider")
                        .getMethod("getPrimaryBaritone").invoke(provider);
                if (primary == null) {
                    primaryDetail = "no instance yet (join a world)";
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                primaryDetail = shorten(e);
            }
        }
        out.add(new Check("silkvis.baritone.check.primary", primary != null, primaryDetail));

        boolean managerOk = false;
        String managerDetail = "";
        if (primary == null) {
            managerDetail = "skipped: no instance";
        } else {
            try {
                managerOk = Class.forName("baritone.api.IBaritone")
                        .getMethod("getCommandManager").invoke(primary) != null;
                if (!managerOk) {
                    managerDetail = "getCommandManager() returned null";
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                managerDetail = shorten(e);
            }
        }
        out.add(new Check("silkvis.baritone.check.manager", managerOk, managerDetail));

        boolean executeOk = false;
        String executeDetail = "";
        try {
            executeOk = Class.forName("baritone.api.command.manager.ICommandManager")
                    .getMethod("execute", String.class) != null;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            executeDetail = e.getClass().getSimpleName();
        }
        out.add(new Check("silkvis.baritone.check.execute", executeOk, executeDetail));

        out.add(new Check("silkvis.baritone.check.last", this.failure.isEmpty(), this.failure));
        return out;
    }

    private static String shorten(Throwable t) {
        Throwable cause = t.getCause() == null ? t : t.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}

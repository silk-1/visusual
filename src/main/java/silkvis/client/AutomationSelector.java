package silkvis.client;

import net.minecraft.client.MinecraftClient;
import silkvis.automation.AutomationMode;
import silkvis.automation.BlockPos;
import silkvis.automation.GoalRegistry;
import silkvis.automation.Snapshot;
import silkvis.automation.StopReason;
import silkvis.ui.Localizer;

/**
 * Decides which goal should run right now, and starts it.
 *
 * <p>This is the piece that makes the subsystem usable without opening a menu: press the panic
 * key and the nearest thing that is obviously wrong wins - you are on fire or in lava, a hostile
 * mob is on top of you, there are dropped items at your feet. All of it is decided from a
 * {@link Snapshot}, so the rules are data, not guesses about the live world.</p>
 *
 * <p>Order matters and is the whole design: danger before loot, loot before walking, and nothing
 * at all when the engine is off or the player is driving. Every start and every refusal is logged,
 * because "I pressed the key and nothing happened" must be answerable from {@code latest.log}.</p>
 */
public final class AutomationSelector {
    /** Hostile mob this close and the emergency goal outranks everything else. */
    static final double THREAT_RADIUS = 6.0;
    /** Dropped items are worth a walk only when they are near and clearly theirs to take. */
    static final double LOOT_RADIUS = 8.0;

    /** What the selector decided, so the caller can log it without asking the engine. */
    public record Decision(String goalId, BlockPos target, StopReason refusal) {
        public boolean started() {
            return goalId != null;
        }
    }

    private final SilkVisRuntime runtime;

    public AutomationSelector(SilkVisRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Picks a goal for the current situation and starts it.
     *
     * @return the decision; {@link Decision#started()} is false when nothing was appropriate,
     *         with the reason filled in
     */
    public Decision pickAndStart(MinecraftClient client, Snapshot snapshot) {
        if (snapshot == null) {
            return new Decision(null, null, StopReason.WORLD_CHANGED);
        }
        AutomationMode mode = this.runtime.automation().settings().mode();
        if (mode == AutomationMode.OFF) {
            return new Decision(null, null, StopReason.MODE_TOO_LOW);
        }
        BlockPos feet = snapshot.player();
        // 1. Danger first: being in lava outranks any amount of loot.
        if (standingInDanger(snapshot, feet)) {
            return start("A22", feet);
        }
        // 2. A hostile mob within reach of the player.
        for (Snapshot.EntityInfo entity : snapshot.entities()) {
            if (entity.hostile() && entity.distance() <= THREAT_RADIUS) {
                return start("A22", feet);
            }
        }
        // 3. Loot at the feet: the cheap, frequent case a player expects a single key to cover.
        if (hasLootNearby(snapshot)) {
            return start("A21", feet);
        }
        // 4. Whatever the camera is looking at, if it is a block worth walking to.
        BlockPos looked = this.runtime.automationTarget(client);
        if (looked != null && !looked.equals(GoalRegistry.NO_TARGET)
                && !looked.equals(feet)) {
            return start("A19", looked);
        }
        return new Decision(null, null, StopReason.TARGET_GONE);
    }

    private Decision start(String id, BlockPos target) {
        boolean started = this.runtime.startAutomation(id, target);
        return started ? new Decision(id, target, StopReason.NONE)
                : new Decision(null, target, StopReason.MODE_TOO_LOW);
    }

    private static boolean standingInDanger(Snapshot snapshot, BlockPos feet) {
        return snapshot.grid().dangerous(feet.x(), feet.y(), feet.z())
                || snapshot.grid().dangerous(feet.x(), feet.y() - 1, feet.z());
    }

    private static boolean hasLootNearby(Snapshot snapshot) {
        for (Snapshot.EntityInfo entity : snapshot.entities()) {
            if (!entity.hostile() && entity.type().endsWith(":item")
                    && entity.distance() <= LOOT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /** One line for the chat/log: what was chosen and why, or why nothing was. */
    public String describe(Decision decision) {
        Localizer tr = this.runtime.localizer();
        if (decision == null) {
            return tr.get("silkvis.automation.selector.none");
        }
        if (!decision.started()) {
            return tr.get("silkvis.automation.selector.refused",
                    tr.get(decision.refusal().langKey()));
        }
        return tr.get("silkvis.automation.selector.chosen", decision.goalId(),
                decision.target() == null ? "-" : decision.target().x() + " "
                        + decision.target().y() + " " + decision.target().z());
    }
}

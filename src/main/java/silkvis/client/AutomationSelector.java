package silkvis.client;

import net.minecraft.client.MinecraftClient;
import silkvis.automation.AutomationMode;
import silkvis.automation.BlockPos;
import silkvis.automation.GoalRegistry;
import silkvis.automation.Selector;
import silkvis.automation.Snapshot;
import silkvis.automation.StopReason;
import silkvis.ui.Localizer;

/**
 * Turns the core {@link Selector} rules into a started goal, or into a reason.
 *
 * <p>The ordering itself lives in {@code silkvis.automation.Selector} so it can be asserted
 * without the game; this class only adds the two things the core cannot know - which block the
 * camera is looking at, and how to start a goal - plus the log line that answers "I pressed the
 * key and nothing happened".</p>
 */
public final class AutomationSelector {
    /** What the selector decided, so the caller can log it without asking the engine. */
    public record Decision(String goalId, BlockPos target, StopReason refusal) {
        public boolean started() {
            return goalId != null;
        }
    }

    private final AutomationHost host;

    public AutomationSelector(AutomationHost host) {
        this.host = host;
    }

    /** Picks a goal for the current situation and starts it. */
    public Decision pickAndStart(MinecraftClient client, Snapshot snapshot) {
        AutomationMode mode = this.host.automation().settings().mode();
        Selector.Pick pick = Selector.urgent(snapshot, mode);
        if (pick.found()) {
            BlockPos feet = snapshot.player();
            return start(pick.goalId(), feet);
        }
        if (pick.reason() != StopReason.NONE) {
            // Automation is off, or there is no world: say so rather than falling through to a walk.
            return new Decision(null, null, pick.reason());
        }
        BlockPos looked = this.host.automationTarget(client);
        if (looked != null && !looked.equals(GoalRegistry.NO_TARGET) && snapshot != null
                && !looked.equals(snapshot.player())) {
            return start("A19", looked);
        }
        return new Decision(null, null, StopReason.TARGET_GONE);
    }

    private Decision start(String id, BlockPos target) {
        boolean started = this.host.startAutomation(id, target);
        return started ? new Decision(id, target, StopReason.NONE)
                : new Decision(null, target, StopReason.MODE_TOO_LOW);
    }

    /** One line for the chat/log: what was chosen and why, or why nothing was. */
    public String describe(Decision decision) {
        Localizer tr = this.host.localizer();
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

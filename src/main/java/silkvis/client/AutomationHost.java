package silkvis.client;

import net.minecraft.client.MinecraftClient;
import silkvis.automation.AutomationEngine;
import silkvis.automation.BlockPos;
import silkvis.ui.Localizer;

/**
 * The narrow view of the mod that {@link AutomationSelector} needs.
 *
 * <p>Not a formality: the selector is the rule table ("danger beats loot beats walking"), and
 * rules are worth checking. Depending on four methods instead of the whole composition root keeps
 * the selector compilable and testable on its own, and stops it from quietly growing a second
 * opinion about how settings are stored.</p>
 */
public interface AutomationHost {
    AutomationEngine automation();

    /** The block the camera is looking at, or the block under the player's feet. */
    BlockPos automationTarget(MinecraftClient client);

    boolean startAutomation(String goalId, BlockPos target);

    Localizer localizer();
}

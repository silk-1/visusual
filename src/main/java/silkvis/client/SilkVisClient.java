package silkvis.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import silkvis.config.ProfileIdentity;
import silkvis.hud.HudViewModel;
import silkvis.media.MediaHelperBinary;
import silkvis.media.WindowsMediaProvider;
import silkvis.render.HudHook;

import java.nio.file.Path;

/**
 * Fabric client entrypoint (BUILD-01).
 *
 * <p>Wires lifecycle events, keybinds, capability probing and the HUD hook. All heavy
 * work lives in {@link SilkVisRuntime}; this class stays a thin composition layer and is
 * never loaded on a dedicated server.</p>
 */
public final class SilkVisClient implements ClientModInitializer {
    /** 1.21.10 keybinding categories are objects, not translation-key strings. */
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("silkvis", "main"));
    private static KeyBinding openMenu;
    private static KeyBinding hudInteraction;
    private static HudDataCollector collector;
    private static String lastDimension = "";

    @Override
    public void onInitializeClient() {
        SilkVisRuntime runtime = SilkVisRuntime.get();
        collector = new HudDataCollector(runtime.clock());
        collector.tick(MinecraftClient.getInstance());

        runtime.integrations().probe("sodium", () -> FabricLoader.getInstance().isModLoaded("sodium"));
        runtime.integrations().probe("iris", () -> FabricLoader.getInstance().isModLoaded("iris"));
        runtime.integrations().probe("baritone", () -> FabricLoader.getInstance().isModLoaded("baritone"));

        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.silkvis.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        // Unbound by default so it cannot collide with vanilla binds (MEDIA-15).
        hudInteraction = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.silkvis.hud_interaction", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> runtime.onWorldLeft());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> runtime.shutdown());
        HudHook.register();

        startMedia(runtime);
    }

    private void startMedia(SilkVisRuntime runtime) {
        Path helperDir = FabricLoader.getInstance().getGameDir()
                .resolve("silkvis-data").resolve("native").resolve("media-helper-1");
        runtime.startMediaIfEnabled(() -> new WindowsMediaProvider(
                MediaHelperBinary.system(helperDir), runtime.clock()));
    }

    private void onClientTick(MinecraftClient client) {
        SilkVisRuntime runtime = SilkVisRuntime.get();
        if (collector != null) {
            collector.tick(client);
        }
        runtime.tick();
        if (client.world != null) {
            String dimension = client.world.getRegistryKey().getValue().toString();
            if (!dimension.equals(lastDimension)) {
                lastDimension = dimension;
                runtime.switchDimension(dimension);
            }
        }
        while (openMenu.wasPressed()) {
            if (!(client.currentScreen instanceof SilkVisScreen)) {
                client.setScreen(new SilkVisScreen());
            }
        }
        while (hudInteraction.wasPressed()) {
            runtime.setHudInteractionMode(!runtime.hudInteractionMode());
        }
    }

    private void onJoin(MinecraftClient client) {
        SilkVisRuntime runtime = SilkVisRuntime.get();
        ProfileIdentity identity;
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null && server.address != null && !server.address.isBlank()) {
            identity = ProfileIdentity.multiplayer(server.address);
        } else {
            identity = ProfileIdentity.singleplayer(saveName(client));
        }
        String dimension = client.world != null
                ? client.world.getRegistryKey().getValue().toString() : "minecraft:overworld";
        lastDimension = dimension;
        runtime.loadProfile(identity, dimension);
    }

    private static String saveName(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return "singleplayer";
        }
        try {
            return server.getSaveProperties().getLevelName();
        } catch (RuntimeException e) {
            return "singleplayer";
        }
    }

    /** Cached HUD model for the renderer; never collects data during rendering. */
    public static HudViewModel hudModel() {
        return collector == null ? HudViewModel.empty() : collector.model();
    }

    public static HudDataCollector collector() {
        return collector;
    }
}

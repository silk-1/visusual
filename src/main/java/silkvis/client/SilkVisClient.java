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
import silkvis.assets.TotemPackBuilder;
import silkvis.config.ProfileIdentity;
import silkvis.hud.HudViewModel;
import silkvis.media.ExeHelperLauncher;
import silkvis.media.MediaHelperBinary;
import silkvis.media.MediaHelperLauncher;
import silkvis.media.PowerShellHelperLauncher;
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
    private static KeyBinding bugReport;
    private static KeyBinding openMap;
    /** Direct shortcut to the ported module settings; the same screen is on the VISUAL tab. */
    private static KeyBinding openSoupModules;
    private static final MapPrefetcher MAP_PREFETCH = new MapPrefetcher();
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("silkvis");
    private static HudDataCollector collector;
    private static String lastDimension = "";

    @Override
    public void onInitializeClient() {
        SilkVisRuntime runtime = SilkVisRuntime.get();
        // The ported modules take part in the registry (their switches live there), so the
        // state the registry shows and the module state start out in agreement: whatever the
        // settings file said on load is pushed into the registry once.
        runtime.syncSoupModulesIntoRegistry();
        // Localizer is a functional interface (String -> String), so the reference has to go
        // through the method, not to the factory method that returns it.
        collector = new HudDataCollector(runtime.clock(),
                key -> runtime.localizer().get(key), runtime::dimensionLabel);
        collector.tick(MinecraftClient.getInstance());

        // ADR-0007: presence-only detection of a third-party map. No class of that mod is
        // touched, no artifact is bundled.
        ExternalMapIntegration.Info externalMap = ExternalMapIntegration.detect().orElse(null);
        for (String id : new String[]{"atlasmapextended", "atlasmap"}) {
            boolean present = externalMap != null && externalMap.modId().equals(id);
            runtime.integrations().probe(id, () -> present);
        }
        runtime.applyExternalMapPolicy(externalMap);

        // Totem pack paths: the source folder players drop pictures into, and the instance
        // resourcepacks folder the generated pack goes to.
        Path gameDir = FabricLoader.getInstance().getGameDir();
        runtime.setTotemPaths(gameDir.resolve("silkvis-data").resolve("totems"),
                gameDir.resolve("resourcepacks"));
        // Writing the pack is half the job: Minecraft scans resourcepacks/ once, at startup,
        // so a pack created afterwards never shows up in the vanilla list («создаёт, но не
        // применяет»). The hook turns it on and reloads resources, on the render thread.
        runtime.setTotemPackHook(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            client.execute(() -> {
                String result = TotemPackApplier.enable(client, TotemPackBuilder.PACK_DIR_NAME);
                runtime.log().log("TOTEM_PACK_APPLY", result);
            });
        });

        runtime.integrations().probe("sodium", () -> FabricLoader.getInstance().isModLoaded("sodium"));
        runtime.integrations().probe("iris", () -> FabricLoader.getInstance().isModLoaded("iris"));
        runtime.integrations().probe("baritone", () -> FabricLoader.getInstance().isModLoaded("baritone"));

        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.silkvis.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        // Unbound by default so it cannot collide with vanilla binds (MEDIA-15).
        hudInteraction = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.silkvis.hud_interaction", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        // Also unbound: the same action is offered as a button in the Settings tab, so a
        // default key is not needed and cannot collide with anything.
        bugReport = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.silkvis.bug_report", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        // Unbound by default: M belongs to AtlasMap (ADR-0007), and our own map is reachable
        // from the Map tab. A default bind that fires alongside another mod's map is worse
        // than no bind at all.
        // Unbound by default like the other extra keys: the screen is also reachable from
        // the SilkVis menu (VISUAL tab), so no default bind can collide with anything.
        openSoupModules = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.silkvis.soup_visuals", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        openMap = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.silkvis.open_map", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

        // One line in latest.log that names the running jar. A failed `build.bat` leaves the
        // previous jar in mods/, so "the fix did nothing" is usually "the old build is still
        // installed": this line answers that without guessing.
        LOGGER.info("SilkVis {} initialised", FabricLoader.getInstance()
                .getModContainer("silkvis")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> runtime.onWorldLeft());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> runtime.shutdown());
        HudHook.register();
        // Ported SoupVisuals particle modules (HitBubbles / TotemParticles / AmbientParticles).
        // Off by default like every other SilkVis feature: the modules are built here, their
        // settings are off until the player enables them in the settings tab.
        SoupVisualsClient.get().register();

        startMedia(runtime);
    }

    /**
     * Picks a media back end (ADR-0008): the native helper when it verifies, otherwise the
     * PowerShell bridge, which needs no compiled artifact at all. The reason a back end was
     * refused is handed to the runtime so the Music tab can show it instead of "nothing
     * happens".
     */
    private void startMedia(SilkVisRuntime runtime) {
        Path helperDir = FabricLoader.getInstance().getGameDir()
                .resolve("silkvis-data").resolve("native").resolve("media-helper-1");
        // Registered, not started: the bridge is built the moment music is switched on in
        // the Music tab, not only at launch when the flag was still off (the reason "музыка
        // не робит" existed at all).
        runtime.setMediaFactory(() -> {
            MediaHelperLauncher exe = new ExeHelperLauncher(MediaHelperBinary.system(helperDir));
            if (exe.verify() == null) {
                return new WindowsMediaProvider(exe, runtime.clock());
            }
            MediaHelperLauncher powershell = new PowerShellHelperLauncher(helperDir);
            String problem = powershell.verify();
            if (problem == null) {
                return new WindowsMediaProvider(powershell, runtime.clock());
            }
            throw new IllegalStateException(problem);
        });
        runtime.startMediaIfEnabled();
    }

    private void onClientTick(MinecraftClient client) {
        SilkVisRuntime runtime = SilkVisRuntime.get();
        if (collector != null) {
            collector.tick(client);
        }
        runtime.tick();
        MAP_PREFETCH.tick(runtime, client);
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
        while (openSoupModules.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new SoupSettingsScreen(null));
            }
        }
        while (openMap.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new MapScreen());
            }
        }
        // Interaction mode is a transparent screen, not a flag alone: it is the only way to
        // receive clicks over the world without a mixin, and it blocks world clicks by
        // construction (HUD-05).
        while (hudInteraction.wasPressed()) {
            if (client.currentScreen instanceof HudInteractionScreen screen) {
                screen.close();
            } else if (client.currentScreen == null && client.player != null) {
                runtime.setHudInteractionMode(true);
                client.setScreen(new HudInteractionScreen());
            }
        }
        if (bugReport != null) {
            while (bugReport.wasPressed()) {
                BugReportService.createAndNotify(client);
            }
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

    /**
     * Once per rendered HUD frame: drives the 2 Hz FPS / frame-time aggregator (HUD-04).
     * {@code HudDataCollector#onFrame} existed but was never called, so the FPS widget
     * always rendered "0 FPS 0.0 ms" (first in-game run, 2026-11-12).
     */
    public static void onFrame() {
        if (collector != null) {
            collector.onFrame();
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

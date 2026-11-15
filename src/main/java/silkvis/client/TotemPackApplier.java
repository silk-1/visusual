package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the generated totem pack on (and off) inside the running client.
 *
 * <p>Why this class exists: until 0.1.18 the mod only wrote
 * {@code resourcepacks/silkvis-totem}. Minecraft scans that folder once, at startup, so a pack
 * created while the game is running never appeared in «Наборы ресурсов» — the owner's report
 * was exactly «он их создаёт куда-то, но не применяет». Here the manager is rescanned, the
 * pack is added to the enabled list, options are written and the resources are reloaded, so
 * the texture applies without a restart.</p>
 *
 * <p>The API surface is deliberately small: {@code getResourcePackManager}, {@code scanPacks},
 * {@code setEnabledProfiles}, {@code options.resourcePacks}, {@code options.write} and
 * {@code reloadResourcesConcurrently}. Those are the calls the vanilla pack screen makes, so
 * they are stable and public. Folder packs are addressed as {@code file/<folder name>}, which
 * is the id Minecraft has used since 1.16; the class never guesses anything else.</p>
 *
 * <p>Every call is wrapped: a resource-pack API change must never take the client down. A
 * failure is reported as a code the Totems tab shows in Russian, not swallowed.</p>
 */
public final class TotemPackApplier {
    /** Status codes; the UI translates them, the log gets the raw code. */
    public static final String OK = "ok";
    public static final String NOT_FOUND = "pack_not_found";
    public static final String FAILED = "pack_apply_failed";

    private TotemPackApplier() {
    }

    /** The id a folder pack gets in {@code options.txt}. */
    public static String packId(String packDirectoryName) {
        return "file/" + packDirectoryName;
    }

    /** Enables the pack and reloads resources. Must run on the client thread. */
    public static String enable(MinecraftClient client, String packDirectoryName) {
        if (client == null || packDirectoryName == null) {
            return FAILED;
        }
        try {
            ResourcePackManager manager = client.getResourcePackManager();
            if (manager == null) {
                return FAILED;
            }
            // A pack written seconds ago is not in the manager's profile map until the
            // providers re-read their folders.
            manager.scanPacks();
            String id = packId(packDirectoryName);
            List<String> ids = new ArrayList<>(client.options.resourcePacks);
            if (ids.isEmpty()) {
                // A fresh options.txt lists the base pack; without it the list would consist
                // of our folder alone and the game would lose every vanilla texture.
                ids.add("vanilla");
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
            manager.setEnabledProfiles(ids);
            syncOptions(client, ids);
            client.options.write();
            client.reloadResourcesConcurrently();
            return OK;
        } catch (Throwable failure) {
            // Throwable, not Exception: a missing method on another Minecraft version shows
            // up as NoSuchMethodError, and that must not kill the game either.
            return FAILED;
        }
    }

    /** Turns the pack off again, so the vanilla texture comes back. Client thread. */
    public static String disable(MinecraftClient client, String packDirectoryName) {
        if (client == null || packDirectoryName == null) {
            return FAILED;
        }
        try {
            ResourcePackManager manager = client.getResourcePackManager();
            if (manager == null) {
                return FAILED;
            }
            String id = packId(packDirectoryName);
            List<String> ids = new ArrayList<>(client.options.resourcePacks);
            if (!ids.remove(id)) {
                return NOT_FOUND;
            }
            manager.setEnabledProfiles(ids);
            syncOptions(client, ids);
            client.options.write();
            client.reloadResourcesConcurrently();
            return OK;
        } catch (Throwable failure) {
            return FAILED;
        }
    }

    /**
     * Whether the pack is enabled right now. Read-only: called from the render loop, so it
     * must not rescan or touch options.
     */
    public static boolean isEnabled(MinecraftClient client, String packDirectoryName) {
        if (client == null || packDirectoryName == null) {
            return false;
        }
        try {
            return client.options.resourcePacks.contains(packId(packDirectoryName));
        } catch (Throwable failure) {
            return false;
        }
    }

    /**
     * Keeps {@code options.txt} in step with what was just applied, so the choice survives
     * the next launch.
     */
    private static void syncOptions(MinecraftClient client, List<String> ids) {
        List<String> target = client.options.resourcePacks;
        target.clear();
        target.addAll(ids);
    }
}

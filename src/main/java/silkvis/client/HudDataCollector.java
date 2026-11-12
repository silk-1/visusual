package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import silkvis.hud.FpsAggregator;
import silkvis.hud.HudViewModel;
import silkvis.hud.RateLimiter;
import silkvis.core.Clock;
import silkvis.ui.hud.HudWidgetId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collects HUD values on the client thread and publishes an immutable view model
 * (HUD-04). Rates: FPS at 2 Hz, coordinates/direction at 10 Hz, inventory/effects on
 * change with a bounded fallback poll.
 */
public final class HudDataCollector {
    private final Clock clock;
    private final FpsAggregator fps;
    private final RateLimiter positionLimiter;
    private final RateLimiter fallbackLimiter;
    private volatile HudViewModel model = HudViewModel.empty();

    public HudDataCollector(Clock clock) {
        this.clock = clock;
        this.fps = new FpsAggregator(clock, 2);
        this.positionLimiter = new RateLimiter(clock, 10);
        this.fallbackLimiter = new RateLimiter(clock, 1);
    }

    public void onFrame() {
        fps.onFrame();
    }

    public HudViewModel model() {
        return model;
    }

    /** Called from the client tick; never from the render phase. */
    public void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            model = HudViewModel.empty();
            return;
        }
        Map<HudWidgetId, List<String>> lines = new EnumMap<>(HudWidgetId.class);
        ClientPlayerEntity player = client.player;
        if (positionLimiter.tryAcquire()) {
            BlockPos pos = player.getBlockPos();
            lines.put(HudWidgetId.COORDINATES, List.of(
                    "XYZ " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                    dimensionName(player.getWorld().getRegistryKey())));
            lines.put(HudWidgetId.DIRECTION, List.of(direction(player.getYaw())));
            lines.put(HudWidgetId.BIOME, List.of(biomeName(player)));
            lines.put(HudWidgetId.WORLD_TIME, List.of(worldTime(player.getWorld())));
        }
        lines.put(HudWidgetId.FPS, List.of(fps.fps() + " FPS  " + String.format(Locale.ROOT, "%.1f ms", fps.frameTimeMs())));
        if (fallbackLimiter.tryAcquire()) {
            lines.put(HudWidgetId.EFFECTS, effects(player));
            lines.put(HudWidgetId.ARMOR, armor(player));
            lines.put(HudWidgetId.ACTIVE_TOOL, tool(player));
        }
        model = new HudViewModel(lines, clock.monotonicNanos());
    }

    private static String direction(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int idx = Math.round(normalized / 45f) % 8;
        return names[idx] + "  " + Math.round(normalized) + "°";
    }

    private static String dimensionName(RegistryKey<World> key) {
        String path = key.getValue().getPath();
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
    }

    private static String biomeName(ClientPlayerEntity player) {
        var entry = player.getWorld().getBiome(player.getBlockPos());
        return entry.getKey().map(k -> k.getValue().getPath().replace('_', ' ')).orElse("unknown");
    }

    private static String worldTime(World world) {
        long dayTime = world.getTimeOfDay() % 24000L;
        long hours = (dayTime / 1000L + 6L) % 24L;
        long minutes = (dayTime % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "Day %d %02d:%02d", world.getTimeOfDay() / 24000L, hours, minutes);
    }

    private static List<String> effects(ClientPlayerEntity player) {
        List<String> out = new ArrayList<>();
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            String name = effect.getEffectType().value().getName().getString();
            int seconds = effect.getDuration() / 20;
            out.add(name + " " + (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60));
            if (out.size() >= 5) {
                break;
            }
        }
        return out;
    }

    private static List<String> armor(ClientPlayerEntity player) {
        List<String> out = new ArrayList<>();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int remaining = stack.getMaxDamage() - stack.getDamage();
            out.add(stack.getName().getString() + "  " + remaining);
        }
        return out;
    }

    private static List<String> tool(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            return List.of();
        }
        int remaining = stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : -1;
        String text = stack.getName().getString();
        return List.of(remaining >= 0 ? text + "  " + remaining : text);
    }
}

package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import silkvis.hud.FpsAggregator;
import silkvis.hud.HudViewModel;
import silkvis.hud.RateLimiter;
import silkvis.core.Clock;
import silkvis.ui.Localizer;
import silkvis.ui.hud.HudWidgetId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Collects HUD values on the client thread and publishes an immutable view model
 * (HUD-04). Rates: FPS at 2 Hz, coordinates/direction at 10 Hz, inventory/effects on
 * change with a bounded fallback poll.
 */
public final class HudDataCollector {
    private final Clock clock;
    /**
     * Everything the HUD prints goes through the language table (round 15 rule: no English
     * in the interface). The dimension name is a separate function because the runtime
     * already owns the {@code silkvis.dimension.*} mapping.
     */
    private final Localizer tr;
    private final Function<String, String> dimensionLabel;
    private final FpsAggregator fps;
    private final RateLimiter positionLimiter;
    private final RateLimiter fallbackLimiter;
    private volatile HudViewModel model = HudViewModel.empty();
    /**
     * Last known lines per widget. Rate-limited sections are merged into this map instead of
     * rebuilding the model from scratch: rebuilding dropped every key that was not refreshed
     * on this tick, so coordinates blinked at 10 Hz and effects/armor/tool blinked at 1 Hz
     * (reported after the first in-game run, 2026-11-15).
     */
    private final EnumMap<HudWidgetId, List<String>> cached = new EnumMap<>(HudWidgetId.class);
    /**
     * Structured armor rows. The HUD draws the real item icon, so the text-only model is
     * not enough; the snapshot is rebuilt at 1 Hz on the tick thread and never during
     * rendering (HUD-04).
     */
    private volatile ArmorSnapshot armorSnapshot = ArmorSnapshot.EMPTY;

    public HudDataCollector(Clock clock, Localizer tr, Function<String, String> dimensionLabel) {
        this.clock = clock;
        this.tr = tr;
        this.dimensionLabel = dimensionLabel;
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
            cached.clear();
            armorSnapshot = ArmorSnapshot.EMPTY;
            model = HudViewModel.empty();
            return;
        }
        ClientPlayerEntity player = client.player;
        if (positionLimiter.tryAcquire()) {
            BlockPos pos = player.getBlockPos();
            cached.put(HudWidgetId.COORDINATES, List.of(
                    "XYZ " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                    dimensionName(player.getEntityWorld().getRegistryKey())));
            cached.put(HudWidgetId.DIRECTION, List.of(direction(player.getYaw())));
            cached.put(HudWidgetId.BIOME, List.of(biomeName(player)));
            cached.put(HudWidgetId.WORLD_TIME, List.of(worldTime(player.getEntityWorld())));
        }
        cached.put(HudWidgetId.FPS, List.of(fps.fps() + " FPS  "
                + String.format(Locale.ROOT, "%.1f ms", fps.frameTimeMs())));
        if (fallbackLimiter.tryAcquire()) {
            cached.put(HudWidgetId.EFFECTS, effects(player));
            ArmorSnapshot armor = armor(player);
            armorSnapshot = armor;
            cached.put(HudWidgetId.ARMOR, armorLines(armor));
            cached.put(HudWidgetId.ACTIVE_TOOL, tool(player));
        }
        model = new HudViewModel(cached, clock.monotonicNanos());
    }

    /** Compass letters follow the selected interface language (C / ЮЗ instead of S / SW). */
    private String direction(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        String[] codes = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
        String[] latin = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int idx = Math.round(normalized / 45f) % 8;
        String key = "silkvis.hud.dir." + codes[idx];
        String text = tr.get(key);
        if (text == null || text.equals(key)) {
            text = latin[idx];
        }
        return text + "  " + Math.round(normalized) + "°";
    }

    /**
     * The registry path alone printed "Overworld" in a Russian interface (owner screenshot,
     * 2026-11-15); the runtime owns the translated names, so it answers here.
     */
    private String dimensionName(RegistryKey<World> key) {
        String label = dimensionLabel.apply(key.getValue().toString());
        if (label != null && !label.isBlank()) {
            return label;
        }
        String path = key.getValue().getPath();
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
    }

    /**
     * Biome name through the game's own lang table: the raw registry path printed "plains"
     * in a Russian interface. A modded biome without a translation falls back to its id.
     */
    private static String biomeName(ClientPlayerEntity player) {
        var entry = player.getEntityWorld().getBiome(player.getBlockPos());
        var key = entry.getKey();
        if (key.isEmpty()) {
            return "—";
        }
        String id = key.get().getValue().toString();
        String langKey = "biome." + id.replace(':', '.').replace('/', '.');
        String text = net.minecraft.text.Text.translatable(langKey).getString();
        if (!text.equals(langKey)) {
            return text;
        }
        return id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    private String worldTime(World world) {
        long dayTime = world.getTimeOfDay() % 24000L;
        long hours = (dayTime / 1000L + 6L) % 24L;
        long minutes = (dayTime % 1000L) * 60L / 1000L;
        String day = tr.get("silkvis.hud.day");
        if (day == null || day.equals("silkvis.hud.day")) {
            day = "Day";
        }
        return day + " " + (world.getTimeOfDay() / 24000L) + String.format(Locale.ROOT, " %02d:%02d", hours, minutes);
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

    /** Whatever is equipped in the four armor slots, in head-to-feet order. */
    public ArmorSnapshot armorSnapshot() {
        return armorSnapshot;
    }

    private static ArmorSnapshot armor(ClientPlayerEntity player) {
        List<ArmorSnapshot.Slot> slots = new ArrayList<>();
        EquipmentSlot[] order = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : order) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String durability = "";
            float ratio = 1f;
            int max = stack.getMaxDamage();
            if (max > 0) {
                int remaining = Math.max(0, max - stack.getDamage());
                durability = remaining + "/" + max;
                ratio = remaining / (float) max;
            }
            slots.add(new ArmorSnapshot.Slot(stack.copy(), stack.getName().getString(),
                    durability, ratio, enchantments(stack)));
        }
        return slots.isEmpty() ? ArmorSnapshot.EMPTY : new ArmorSnapshot(List.copyOf(slots));
    }

    /**
     * Up to two enchantments with their level, as the item tooltip would name them. Kept
     * short on purpose: the row is one line next to a 16px icon, and a third enchantment
     * turns "прочность" into a wall of text.
     */
    private static String enchantments(ItemStack stack) {
        var component = EnchantmentHelper.getEnchantments(stack);
        if (component.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (var entry : component.getEnchantmentEntries()) {
            // Entries are fastutil Object2IntMap.Entry: getIntValue() instead of the
            // deprecated getValue(), and Enchantment#getName is static, so it is qualified
            // by the type rather than by an instance (both reported on 0.1.13-p0).
            names.add(Enchantment.getName(entry.getKey(), entry.getIntValue()).getString());
            if (names.size() >= 2) {
                break;
            }
        }
        String joined = String.join(", ", names);
        if (joined.length() > 26 && names.size() > 1) {
            joined = names.get(0);
        }
        return component.getSize() > names.size() ? joined + " +" + (component.getSize() - names.size()) : joined;
    }

    /** Text fallback for the editor preview and for the case where icons cannot be drawn. */
    private static List<String> armorLines(ArmorSnapshot snapshot) {
        List<String> out = new ArrayList<>();
        for (ArmorSnapshot.Slot slot : snapshot.slots()) {
            String line = slot.line();
            if (!slot.enchantments().isEmpty()) {
                line = line + " · " + slot.enchantments();
            }
            out.add(line);
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

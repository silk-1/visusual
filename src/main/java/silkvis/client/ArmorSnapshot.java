package silkvis.client;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Armor rows for the HUD, collected on the client tick and read by the renderer (HUD-04).
 *
 * <p>Why a record instead of the old plain text lines: the owner asked for the item icon
 * ("алмазный нагрудник" as a picture, not a sentence), so the renderer needs the stack
 * itself. The texts are formatted here, on the tick thread, because item and enchantment
 * names resolve through the registry and must never be looked up while drawing.</p>
 *
 * <p>The stack is a defensive copy: the equipment slot can change under us on the next
 * tick and a shared mutable stack in a rendered frame is a data race.</p>
 */
public record ArmorSnapshot(List<Slot> slots) {
    public static final ArmorSnapshot EMPTY = new ArmorSnapshot(List.of());

    /**
     * One equipped slot.
     *
     * @param stack          copy of the equipped stack, used to draw the 16x16 icon
     * @param itemName       localized item name, kept as the editor/preview fallback
     * @param durability     {@code "320/528"}, or empty for unbreakable pieces
     * @param durabilityRatio 0..1 remaining fraction, used for the bar and its colour
     * @param enchantments   up to two localized enchantment names with levels, e.g. "Защита IV"
     */
    public record Slot(ItemStack stack, String itemName, String durability,
                       float durabilityRatio, String enchantments) {
        public boolean hasDurability() {
            return !durability.isEmpty();
        }

        /** Text row used by the editor preview and as a fallback when icons are unavailable. */
        public String line() {
            if (!hasDurability()) {
                return itemName.isEmpty() ? durability : itemName;
            }
            return itemName + " " + durability;
        }
    }
}

package silkvis.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import silkvis.ui.Painter;
import silkvis.ui.Rect;

/**
 * Minecraft implementation of the {@link Painter} port (ARCH-01, BUILD-04).
 *
 * <p>Version-specific calls are confined to this class and {@link HudHook}; the resize,
 * GUI-scale and 1.21.9+ renderer changes are tracked in ADR-0002. Scale/clip state is
 * always restored, so no render-state leaks into the vanilla HUD.</p>
 */
public final class VanillaPainter implements Painter {
    private final DrawContext context;
    private final TextRenderer textRenderer;

    public VanillaPainter(DrawContext context) {
        this.context = context;
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
    }

    @Override
    public void fill(Rect rect, int argb) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), argb);
    }

    @Override
    public void outline(Rect rect, int argb, int thickness) {
        int t = Math.max(1, thickness);
        fill(new Rect(rect.x(), rect.y(), rect.width(), t), argb);
        fill(new Rect(rect.x(), rect.bottom() - t, rect.width(), t), argb);
        fill(new Rect(rect.x(), rect.y(), t, rect.height()), argb);
        fill(new Rect(rect.right() - t, rect.y(), t, rect.height()), argb);
    }

    @Override
    public int text(String text, int x, int y, int argb, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (shadow) {
            context.drawTextWithShadow(textRenderer, text, x, y, argb);
        } else {
            context.drawText(textRenderer, text, x, y, argb, false);
        }
        return textRenderer.getWidth(text);
    }

    @Override
    public int textWidth(String text) {
        return text == null ? 0 : textRenderer.getWidth(text);
    }

    @Override
    public int lineHeight() {
        return textRenderer.fontHeight;
    }

    @Override
    public void pushClip(Rect rect) {
        context.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
    }

    @Override
    public void popClip() {
        context.disableScissor();
    }

    /** Cleared after the first failure, so one unsupported call cannot spam the log. */
    private static boolean itemIconsSupported = true;

    /**
     * An item icon, so a menu can show the ore a player is picking instead of its name only.
     *
     * <p>Two version-sensitive calls live here and nowhere else: the registry lookup and
     * {@code DrawContext#drawItem(ItemStack, int, int)}. Both are wrapped, and a failure turns
     * icons off for the session instead of throwing into the render loop: the caller has already
     * drawn a plate and the name, so the menu stays readable without pictures.</p>
     */
    @Override
    public void itemIcon(String itemId, Rect rect) {
        if (!itemIconsSupported || itemId == null || itemId.isEmpty() || rect == null) {
            return;
        }
        try {
            Identifier id = itemId.indexOf(':') >= 0
                    ? Identifier.tryParse(itemId) : Identifier.of("minecraft", itemId);
            if (id == null) {
                return;
            }
            Item item = Registries.ITEM.get(id);
            if (item == null || item == Items.AIR) {
                return;
            }
            context.drawItem(new ItemStack(item), rect.x(), rect.y());
        } catch (RuntimeException | LinkageError e) {
            itemIconsSupported = false;
        }
    }
}

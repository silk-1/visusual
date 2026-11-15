package silkvis.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import silkvis.media.MediaAction;
import silkvis.render.VanillaPainter;
import silkvis.ui.Color;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;
import silkvis.ui.widget.Keys;

/**
 * Transparent screen used as the HUD interaction mode (HUD-05).
 *
 * <p>The overlay is click-through by default: a click on a HUD panel must not also swing a
 * sword or place a block. Instead of a mixin on the mouse handler, this screen exists only
 * while the mode is on — it draws nothing but a hint, forwards clicks to the music card's
 * buttons and blocks world input by construction. Closing it restores normal mouse capture
 * because the screen is simply gone.</p>
 */
public final class HudInteractionScreen extends Screen {
    private final SilkVisRuntime runtime = SilkVisRuntime.get();

    public HudInteractionScreen() {
        super(Text.translatable("silkvis.hud.interaction_title"));
    }

    private String tr(String key) {
        return runtime.localizer().get(key);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // The HUD element paints the widgets itself (it renders while interaction mode is
        // on), so only the escape hint is drawn here.
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();
        String hint = tr("silkvis.hud.interaction_hint");
        int textWidth = painter.textWidth(hint);
        Rect box = new Rect((width - textWidth) / 2 - 6, height - 26, textWidth + 12, 18);
        painter.roundPanel(box, theme.radiusControl(),
                theme.surface().withAlpha(0.75f).argb(), Color.argb(theme.border()));
        painter.text(hint, box.x() + 6, box.centerY() - painter.lineHeight() / 2,
                Color.argb(theme.textSecondary()), false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        HudOverlayRenderer.NowPlayingControl control =
                HudOverlayRenderer.controlAt(click.x(), click.y());
        if (control != null) {
            MediaAction action = switch (control) {
                case PREVIOUS -> MediaAction.PREVIOUS;
                case PLAY_PAUSE -> MediaAction.TOGGLE;
                case NEXT -> MediaAction.NEXT;
            };
            runtime.mediaService().ifPresent(service -> service.command(action));
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == Keys.ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        runtime.setHudInteractionMode(false);
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

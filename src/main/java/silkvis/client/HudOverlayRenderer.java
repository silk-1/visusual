package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import silkvis.hud.HudViewModel;
import silkvis.media.MediaService;
import silkvis.media.MediaSnapshot;
import silkvis.media.ProviderStatus;
import silkvis.media.MediaTimeline;
import silkvis.render.VanillaPainter;
import silkvis.ui.Color;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;
import silkvis.ui.hud.Anchor;
import silkvis.ui.hud.HudLayoutConfig;
import silkvis.ui.hud.HudLayoutEngine;
import silkvis.ui.hud.HudWidgetConfig;
import silkvis.ui.hud.HudWidgetId;
import silkvis.ui.hud.VisibilityContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * HUD overlay (HUD-01..HUD-04).
 *
 * <p>Rendering reads only cached data: the immutable {@link HudViewModel}, the media
 * snapshot and the tile cache. Visibility follows the per-widget policy and the overlay
 * never covers the hotbar/health band unless the user overrode it in the editor.</p>
 */
public final class HudOverlayRenderer {
    /**
     * Rectangles drawn in the last frame, published for the HUD editor. The editor used to
     * guess widget sizes (110x22 for everything) and then converted the dropped box back
     * into an anchor offset with the wrong width — widgets jumped as soon as the editor
     * closed, which read as "the editor edits nothing".
     */
    private static volatile Map<HudWidgetId, Rect> lastRects = Map.of();
    private static EnumMap<HudWidgetId, Rect> frameRects = new EnumMap<>(HudWidgetId.class);

    private HudOverlayRenderer() {
    }

    /** Real widget boxes of the last rendered frame; empty outside a world. */
    public static Map<HudWidgetId, Rect> lastRects() {
        return lastRects;
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        SilkVisRuntime runtime = SilkVisRuntime.get();
        // The editor needs the real widgets on screen: without them it can only draw
        // placeholder rectangles and drag those.
        if (client.currentScreen != null && !runtime.hudInteractionMode()
                && !(client.currentScreen instanceof HudEditorScreen)) {
            return;
        }
        frameRects = new EnumMap<>(HudWidgetId.class);
        VanillaPainter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();
        Rect screen = new Rect(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        Rect safe = HudLayoutEngine.safeArea(screen);
        HudLayoutConfig layout = runtime.hudLayout();
        HudViewModel model = SilkVisClient.hudModel();
        VisibilityContext visibility = new VisibilityContext(
                false,
                client.getDebugHud().shouldShowDebugHud(),
                false,
                false,
                client.currentScreen != null,
                client.player.isSpectator());

        // Two switches can turn the music widget on: the HUD editor and the Music tab.
        // The Music tab flag was written to the config and read by nobody until 0.1.13.
        boolean nowPlayingOnHud = runtime.global().media().enabled()
                && runtime.global().media().hudWidgetEnabled();
        List<HudWidgetConfig> enabled = new ArrayList<>();
        for (HudWidgetConfig cfg : layout.byId().values()) {
            boolean on = cfg.enabled()
                    || (cfg.id() == HudWidgetId.NOW_PLAYING && nowPlayingOnHud);
            if (on && cfg.id().available() && cfg.visibility().permits(visibility)) {
                enabled.add(cfg);
            }
        }
        // Top-down instead of z-order: with no overlap the z-order stops mattering, and a
        // widget higher on the screen keeps its slot while the ones below shift down. Saved
        // layouts from before 2026-11-15 carry single-line offsets, so coordinates used to
        // paint on top of FPS; this repairs existing configs without a migration.
        enabled.sort(Comparator.comparingInt(HudWidgetConfig::offsetY)
                .thenComparingInt(HudWidgetConfig::zIndex));

        List<Rect> occupied = new ArrayList<>();
        for (HudWidgetConfig cfg : enabled) {
            List<String> lines = model.lines(cfg.id());
            if (cfg.id() == HudWidgetId.NOW_PLAYING) {
                renderNowPlaying(painter, theme, screen, safe, cfg, runtime, occupied);
                continue;
            }
            if (cfg.id() == HudWidgetId.ARMOR) {
                renderArmor(painter, context, theme, screen, safe, cfg, runtime, occupied);
                continue;
            }
            if (lines.isEmpty()) {
                continue;
            }
            renderPanel(painter, theme, screen, safe, cfg, lines, occupied);
        }
        lastRects = Map.copyOf(frameRects);
    }

    /**
     * Pushes a widget down until it no longer intersects one already placed. Bounded: after
     * a few attempts (or when it would leave the screen) the original box is returned, so a
     * dense layout can never make widgets run away or disappear entirely.
     */
    private static Rect avoidOverlap(Rect box, List<Rect> occupied, Rect screen) {
        Rect current = box;
        for (int guard = 0; guard < 8; guard++) {
            Rect hit = null;
            for (Rect other : occupied) {
                if (current.intersects(other)) {
                    hit = other;
                    break;
                }
            }
            if (hit == null) {
                return current;
            }
            int shift = hit.bottom() + 2 - current.y();
            if (shift <= 0) {
                return current;
            }
            current = current.translate(0, shift);
            if (current.bottom() > screen.bottom()) {
                return box;
            }
        }
        return current;
    }

    private static void renderPanel(VanillaPainter painter, ThemeTokens theme, Rect screen, Rect safe,
                                    HudWidgetConfig cfg, List<String> lines, List<Rect> occupied) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, painter.textWidth(line));
        }
        int height = lines.size() * painter.lineHeight() + Math.max(0, lines.size() - 1) * 2;
        Rect box = avoidOverlap(HudLayoutEngine.resolve(screen, safe, cfg, width + 8, height + 6),
                occupied, screen);
        occupied.add(box);
        frameRects.put(cfg.id(), box);
        painter.roundRect(box, theme.radiusControl(),
                Color.argb(theme.surface().withAlpha(theme.surface().alpha() * 0.55f * cfg.alpha())));
        int y = box.y() + 3;
        for (String line : lines) {
            painter.text(line, box.x() + 4, y, Color.argb(theme.textPrimary().withAlpha(cfg.alpha())), true);
            y += painter.lineHeight() + 2;
        }
    }

    /** Control buttons on the music card; order defines the hit-test index. */
    public enum NowPlayingControl {
        PREVIOUS,
        PLAY_PAUSE,
        NEXT
    }

    /**
     * Hit rectangles of the controls drawn last frame. The interaction screen reads them
     * instead of recomputing the layout: geometry lives in exactly one place.
     */
    private static volatile Rect[] controlRects;

    /** Which control sits under the cursor, or null. Only valid while interaction mode is on. */
    public static NowPlayingControl controlAt(double mouseX, double mouseY) {
        Rect[] rects = controlRects;
        if (rects == null) {
            return null;
        }
        NowPlayingControl[] values = NowPlayingControl.values();
        for (int i = 0; i < values.length && i < rects.length; i++) {
            if (rects[i] != null && rects[i].contains((int) mouseX, (int) mouseY)) {
                return values[i];
            }
        }
        return null;
    }

    private static final int ARMOR_ICON = 16;
    private static final int ARMOR_ROW = 20;
    /** Cleared after the first failure so an unsupported drawItem cannot spam the log. */
    private static boolean itemIconsSupported = true;

    /**
     * Armor as inventory icons (owner request, 2026-11-12): the piece itself, not the words
     * "алмазный нагрудник". Each row is a 16x16 item icon, a durability bar and the numbers;
     * enchantments follow the durability in the secondary colour.
     *
     * <p>Typed API risk is contained: {@code DrawContext#drawItem(ItemStack, int, int)}
     * exists in 1.21.10 yarn, and a failure falls back to a bordered plate of the same size
     * with the row text intact — never to an exception in the render loop.</p>
     */
    private static void renderArmor(VanillaPainter painter, DrawContext context, ThemeTokens theme,
                                    Rect screen, Rect safe, HudWidgetConfig cfg, SilkVisRuntime runtime,
                                    List<Rect> occupied) {
        HudDataCollector collector = SilkVisClient.collector();
        ArmorSnapshot snapshot = collector == null ? ArmorSnapshot.EMPTY : collector.armorSnapshot();
        if (snapshot.slots().isEmpty()) {
            return;
        }
        int textWidth = 0;
        for (ArmorSnapshot.Slot slot : snapshot.slots()) {
            int row = painter.textWidth(slot.durability());
            if (!slot.enchantments().isEmpty()) {
                row += 6 + painter.textWidth(slot.enchantments());
            }
            textWidth = Math.max(textWidth, row);
        }
        int width = 4 + ARMOR_ICON + 5 + textWidth + 5;
        int height = snapshot.slots().size() * ARMOR_ROW + 4;
        Rect box = clampToSafe(HudLayoutEngine.resolve(screen, safe, cfg, width, height), safe);
        box = clampToSafe(avoidOverlap(box, occupied, screen), safe);
        occupied.add(box);
        frameRects.put(cfg.id(), box);
        painter.roundRect(box, theme.radiusControl(),
                Color.argb(theme.surface().withAlpha(theme.surface().alpha() * 0.55f * cfg.alpha())));
        int y = box.y() + 3;
        for (ArmorSnapshot.Slot slot : snapshot.slots()) {
            Rect icon = new Rect(box.x() + 4, y, ARMOR_ICON, ARMOR_ICON);
            drawItemIcon(context, painter, theme, icon, slot, runtime);
            if (slot.hasDurability()) {
                int barWidth = ARMOR_ICON - 2;
                painter.fill(new Rect(icon.x() + 1, icon.bottom() - 2, barWidth, 2),
                        Color.argb(theme.surfaceHover()));
                int filled = Math.round(barWidth * Math.max(0f, Math.min(1f, slot.durabilityRatio())));
                if (filled > 0) {
                    painter.fill(new Rect(icon.x() + 1, icon.bottom() - 2, filled, 2),
                            Color.argb(durabilityColor(theme, slot.durabilityRatio())));
                }
            }
            int textX = icon.right() + 5;
            int textY = icon.y() + (ARMOR_ICON - painter.lineHeight()) / 2;
            int durabilityWidth = painter.text(slot.durability(), textX, textY,
                    Color.argb(slot.hasDurability()
                            ? durabilityColor(theme, slot.durabilityRatio())
                            : theme.textSecondary()), true);
            if (!slot.enchantments().isEmpty()) {
                painter.text(slot.enchantments(), textX + durabilityWidth + 6, textY,
                        Color.argb(theme.textSecondary()), true);
            }
            y += ARMOR_ROW;
        }
    }

    /**
     * Durability colour: green, amber, red. Same thresholds the vanilla bar implies.
     * Returns {@link Color}, not an int: theme tokens are {@code Color}, and mixing the two
     * in a conditional expression does not compile (reported on 0.1.13-p0).
     */
    private static Color durabilityColor(ThemeTokens theme, float ratio) {
        if (ratio <= 0.15f) {
            return theme.error();
        }
        return ratio <= 0.5f ? theme.warning() : theme.success();
    }

    private static void drawItemIcon(DrawContext context, VanillaPainter painter, ThemeTokens theme,
                                     Rect area, ArmorSnapshot.Slot slot, SilkVisRuntime runtime) {
        if (context != null && itemIconsSupported) {
            try {
                context.drawItem(slot.stack(), area.x(), area.y());
                return;
            } catch (RuntimeException | LinkageError e) {
                itemIconsSupported = false;
                runtime.log().log("ARMOR_ICON_UNSUPPORTED", e.getClass().getSimpleName());
            }
        }
        painter.fill(area, Color.argb(theme.surfaceHover()));
        painter.outline(area, Color.argb(slot.hasDurability()
                ? durabilityColor(theme, slot.durabilityRatio()) : theme.border()), 1);
    }

    /**
     * One line explaining the media state on the HUD. Shown only when the user enabled
     * music: a mod that is off by default must not nag about a feature nobody asked for.
     */
    private static void renderMediaHint(VanillaPainter painter, ThemeTokens theme, Rect screen, Rect safe,
                                        HudWidgetConfig cfg, SilkVisRuntime runtime, List<Rect> occupied) {
        if (!runtime.global().media().enabled()) {
            return;
        }
        String text = runtime.localizer().get("silkvis.media.hud.status", runtime.mediaStatusLine());
        int width = Math.min(260, Math.max(120, painter.textWidth(text) + 10));
        Rect box = clampToSafe(HudLayoutEngine.resolve(screen, safe, cfg, width, 16), safe);
        box = clampToSafe(avoidOverlap(box, occupied, screen), safe);
        occupied.add(box);
        frameRects.put(cfg.id(), box);
        painter.roundRect(box, theme.radiusControl(),
                Color.argb(theme.surface().withAlpha(theme.surface().alpha() * 0.55f * cfg.alpha())));
        painter.text(ellipsize(painter, text, width - 8), box.x() + 4,
                box.centerY() - painter.lineHeight() / 2, Color.argb(theme.textSecondary()), false);
    }

    private static Rect clampToSafe(Rect box, Rect safe) {
        int y = box.y();
        if (y < safe.y()) {
            y = safe.y();
        }
        if (y + box.height() > safe.bottom()) {
            y = Math.max(safe.y(), safe.bottom() - box.height());
        }
        return new Rect(box.x(), y, box.width(), box.height());
    }

    private static void renderNowPlaying(VanillaPainter painter, ThemeTokens theme, Rect screen, Rect safe,
                                         HudWidgetConfig cfg, SilkVisRuntime runtime, List<Rect> occupied) {
        Optional<MediaService> serviceOpt = runtime.mediaService();
        if (serviceOpt.isEmpty()) {
            controlRects = null;
            // "Music does nothing" was the whole in-game experience when the bridge was
            // refused: say why on the HUD instead of staying silent.
            renderMediaHint(painter, theme, screen, safe, cfg, runtime, occupied);
            return;
        }
        MediaService service = serviceOpt.get();
        Optional<MediaSnapshot> snapshotOpt = service.snapshot();
        if (snapshotOpt.isEmpty()) {
            // Nothing playing: draw nothing at all. An empty frame with a title placeholder
            // is worse than an absent widget (MEDIA-14) — unless the provider itself failed,
            // which the user needs to see.
            controlRects = null;
            // Only a refusal is worth a line on the HUD. NO_SESSION means "nothing is
            // playing", which must stay silent (MEDIA-14), and STARTING lasts a moment.
            ProviderStatus status = service.status();
            if (status == ProviderStatus.ERROR || status == ProviderStatus.UNSUPPORTED) {
                renderMediaHint(painter, theme, screen, safe, cfg, runtime, occupied);
            }
            return;
        }
        MediaSnapshot snapshot = snapshotOpt.get();
        silkvis.config.MediaConfig media = runtime.global().media();
        silkvis.config.NowPlayingVariant variant = media.variant();
        boolean interaction = runtime.hudInteractionMode();

        int width = variant == silkvis.config.NowPlayingVariant.LINE ? 230 : 250;
        int height = switch (variant) {
            case LINE -> 18;
            case PROGRESS -> 36;
            case CARD -> 64;
        };
        Rect box = avoidOverlap(HudLayoutEngine.resolve(screen, safe, cfg, width, height), occupied, screen);
        occupied.add(box);
        frameRects.put(cfg.id(), box);
        painter.roundPanel(box, theme.radiusControl(),
                Color.argb(theme.surface().withAlpha(theme.surface().alpha() * 0.7f * cfg.alpha())),
                Color.argb(theme.border()));

        String title = snapshot.titleOpt().orElse("—");
        String artist = snapshot.artistOpt().orElse("");
        long now = runtime.clock().monotonicNanos();
        OptionalLong projected = MediaTimeline.projectPosition(snapshot, now, runtime.clock());
        // A fake 0:00 looked like a frozen player (owner report, 2026-11-15): when the
        // source reports no position, say so instead of inventing a start.
        String positionText = projected.isPresent()
                ? MediaTimeline.formatPosition(projected.getAsLong()) : "--:--";
        double fraction = projected.isPresent() ? progressFraction(snapshot, projected.getAsLong()) : 0d;

        switch (variant) {
            case LINE -> {
                int textWidth = box.width() - 10;
                String line = artist.isBlank() ? title : title + " — " + artist;
                painter.text(ellipsize(painter, line, textWidth), box.x() + 5,
                        box.centerY() - painter.lineHeight() / 2, Color.argb(theme.textPrimary()), false);
            }
            case PROGRESS -> {
                int textX = box.x() + 5;
                int textWidth = box.width() - 10;
                painter.text(ellipsize(painter, title, textWidth), textX, box.y() + 4,
                        Color.argb(theme.textPrimary()), false);
                String second = artist.isBlank() ? snapshot.displaySource() : artist;
                painter.text(ellipsize(painter, second, textWidth), textX, box.y() + 15,
                        Color.argb(theme.textSecondary()), false);
                String time = positionText + " / " + lengthText(runtime, snapshot);
                painter.text(time, box.right() - 5 - painter.textWidth(time), box.y() + 15,
                        Color.argb(theme.textSecondary()), false);
                drawProgressBar(painter, theme, new Rect(textX, box.bottom() - 8, textWidth, 3),
                        fraction);
            }
            case CARD -> {
                Rect art = new Rect(box.x() + 4, box.y() + 6, 52, 52);
                drawArtwork(painter, theme, art, service, runtime, media.showArtwork());
                int textX = art.right() + 6;
                int textWidth = box.right() - 5 - textX;
                painter.text(ellipsize(painter, title, textWidth), textX, box.y() + 6,
                        Color.argb(theme.textPrimary()), false);
                if (!artist.isBlank()) {
                    painter.text(ellipsize(painter, artist, textWidth), textX, box.y() + 17,
                            Color.argb(theme.textSecondary()), false);
                }
                painter.text(snapshot.displaySource(), textX, box.y() + 28,
                        Color.argb(theme.textSecondary()), false);
                String time = positionText + " / " + lengthText(runtime, snapshot);
                painter.text(time, textX, box.y() + 39, Color.argb(theme.textSecondary()), false);
                drawProgressBar(painter, theme,
                        new Rect(textX, box.bottom() - 9, textWidth, 3), fraction);
                if (interaction) {
                    controlRects = drawControls(painter, theme, box, snapshot);
                } else {
                    controlRects = null;
                }
            }
        }
        if (variant != silkvis.config.NowPlayingVariant.CARD) {
            controlRects = null;
        }
    }

    /**
     * Length as text. The media layer answers "LIVE"/"--:--" in English, and a Russian
     * interface must not print "LIVE" (round 15 rule), so the word is translated here.
     */
    private static String lengthText(SilkVisRuntime runtime, MediaSnapshot snapshot) {
        if (!MediaTimeline.isLiveOrUnknownLength(snapshot)) {
            return MediaTimeline.formatPosition(snapshot.endMs());
        }
        return snapshot.playbackState() == silkvis.media.PlaybackState.PLAYING
                ? runtime.localizer().get("silkvis.media.live") : "--:--";
    }

    /** 0..1 progress, or 0 for live/unknown length: never a fake half-filled bar. */
    private static double progressFraction(MediaSnapshot snapshot, long positionMs) {
        long end = snapshot.endMs() == null ? 0L : snapshot.endMs();
        if (end <= 0L) {
            return 0d;
        }
        long start = snapshot.startMs() == null ? 0L : snapshot.startMs();
        long span = end - start;
        if (span <= 0L) {
            return 0d;
        }
        double value = (positionMs - start) / (double) span;
        return value < 0 ? 0 : Math.min(1, value);
    }

    private static void drawProgressBar(VanillaPainter painter, ThemeTokens theme, Rect bar,
                                        double fraction) {
        painter.fill(bar, Color.argb(theme.surfaceHover()));
        int filled = (int) Math.round(bar.width() * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            painter.fill(new Rect(bar.x(), bar.y(), filled, bar.height()),
                    Color.argb(theme.accent()));
        }
    }

    /** Cover as a colour grid; a neutral square until the decode finishes (never a stall). */
    private static void drawArtwork(VanillaPainter painter, ThemeTokens theme, Rect area,
                                    MediaService service, SilkVisRuntime runtime, boolean enabled) {
        int[] grid = null;
        if (enabled) {
            String hash = service.currentArtworkHash().orElse(null);
            if (hash != null) {
                byte[] png = service.artwork().get(hash).orElse(null);
                grid = ArtworkPixels.grid(hash, png, runtime.executors().io());
            }
        }
        if (grid == null) {
            painter.fill(area, Color.argb(theme.surfaceHover()));
            painter.outline(area, Color.argb(theme.border()), 1);
            return;
        }
        int cell = Math.max(1, area.width() / ArtworkPixels.GRID);
        for (int gy = 0; gy < ArtworkPixels.GRID; gy++) {
            for (int gx = 0; gx < ArtworkPixels.GRID; gx++) {
                int color = grid[gy * ArtworkPixels.GRID + gx];
                if (color == 0) {
                    continue;
                }
                painter.fill(new Rect(area.x() + gx * cell, area.y() + gy * cell, cell, cell), color);
            }
        }
    }

    /**
     * Three buttons on the card while interaction mode is on (HUD-05). Drawn, not typed:
     * the vanilla font has no reliable media glyphs, so shapes are filled rectangles.
     */
    private static Rect[] drawControls(VanillaPainter painter, ThemeTokens theme, Rect box,
                                       MediaSnapshot snapshot) {
        int size = 14;
        int gap = 4;
        int right = box.right() - 5;
        int bottom = box.bottom() - 5;
        Rect next = new Rect(right - size, bottom - size, size, size);
        Rect play = new Rect(next.x() - gap - size, bottom - size, size, size);
        Rect previous = new Rect(play.x() - gap - size, bottom - size, size, size);
        int fill = Color.argb(theme.surfaceHover());
        int glyph = Color.argb(theme.textPrimary());
        for (Rect rect : new Rect[]{previous, play, next}) {
            painter.roundRect(rect, 3, fill);
            painter.outline(rect, Color.argb(theme.border()), 1);
        }
        drawTriangle(painter, previous, glyph, false, true);
        boolean playing = snapshot.playbackState() == silkvis.media.PlaybackState.PLAYING;
        if (playing) {
            int barWidth = Math.max(1, size / 5);
            painter.fill(new Rect(play.x() + size / 2 - barWidth - 1, play.y() + 4,
                    barWidth, size - 8), glyph);
            painter.fill(new Rect(play.x() + size / 2 + 1, play.y() + 4, barWidth, size - 8), glyph);
        } else {
            drawTriangle(painter, play, glyph, true, false);
        }
        drawTriangle(painter, next, glyph, true, true);
        return new Rect[]{previous, play, next};
    }

    /** Small filled triangle; {@code bar} adds the "skip" tick mark used by prev/next. */
    private static void drawTriangle(VanillaPainter painter, Rect rect, int color,
                                     boolean pointRight, boolean bar) {
        int inner = rect.width() - 6;
        int left = rect.x() + (rect.width() - inner) / 2;
        int top = rect.y() + 3;
        for (int row = 0; row < inner; row++) {
            // Width grows to the middle and shrinks again: that is the horizontal extent of
            // a triangle whose flat edge is vertical.
            int width = Math.min(row, inner - 1 - row) + 1;
            int x = pointRight ? left : left + inner - width;
            painter.fill(new Rect(x, top + row, width, 1), color);
        }
        if (bar) {
            int barX = pointRight ? left + inner - 1 : left;
            painter.fill(new Rect(barX, top, 1, inner), color);
        }
    }

    private static String ellipsize(VanillaPainter painter, String text, int maxWidth) {
        if (painter.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (painter.textWidth(sb.toString() + c + ellipsis) > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }
}

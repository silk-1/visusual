package silkvis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import silkvis.map.MapIdentity;
import silkvis.map.MapService;
import silkvis.map.MapTile;
import silkvis.map.TileKey;
import silkvis.map.TileMath;
import silkvis.map.Waypoint;
import silkvis.render.VanillaPainter;
import silkvis.ui.Animation;
import silkvis.ui.Color;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;
import silkvis.ui.widget.ButtonWidget;
import silkvis.ui.widget.Keys;
import silkvis.ui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Full-screen map of the areas the client has actually explored (MAP-03).
 *
 * <p>North-up; pan by dragging, zoom with the wheel (around the cursor), R centres on the
 * player. Everything is drawn from the tile cache: sampling happens in {@link MapPrefetcher}
 * and {@code MapService.tick()}, so opening the map never stalls a frame on terrain reads.
 * Cells whose colour equals their left neighbour are merged into one quad — without that
 * merge a coarse grid of axis-aligned fills is the only thing available (no texture upload
 * path yet) and it would be too expensive at 60 fps.</p>
 *
 * <p>Waypoints come from the independent marker file (MAP-08), never from the cache, so
 * clearing tiles cannot lose them.</p>
 */
public final class MapScreen extends Screen {
    /** Logical pixels per raster cell. Smaller is sharper and quadratically more expensive. */
    private static final int CELL = 4;
    private static final int MIN_BPP = 1;
    private static final int MAX_BPP = 8;
    private static final int ROW = 18;
    private static final int KEY_EQUAL = 61;
    private static final int KEY_MINUS = 45;
    private static final int KEY_NUMPAD_ADD = 334;
    private static final int KEY_NUMPAD_SUB = 333;

    private final SilkVisRuntime runtime = SilkVisRuntime.get();
    private final List<Widget> chrome = new ArrayList<>();
    private final long openedAtNanos = System.nanoTime();

    private Rect window;
    private Rect map;
    private Rect sidebar;
    private Rect footer;

    private int centerX;
    private int centerZ;
    private int bpp = 2;
    private boolean dragging;
    private double dragX;
    private double dragZ;
    private int waypointScroll;
    private String selectedId;

    private int[] raster;
    private int rasterW;
    private int rasterH;
    private long rasterKey;
    private int framesSinceBuild;
    private boolean rasterFilled;

    public MapScreen() {
        super(Text.translatable("silkvis.map.title"));
        centerOnPlayer();
    }

    private String tr(String key) {
        return runtime.localizer().get(key);
    }

    @Override
    protected void init() {
        chrome.clear();
        int margin = Math.min(28, Math.max(8, Math.min(width, height) / 20));
        int w = Math.max(320, Math.min(width - margin * 2, 1120));
        int h = Math.max(220, Math.min(height - margin * 2, 720));
        window = new Rect((width - w) / 2, (height - h) / 2, w, h);
        int pad = 12;
        int headerHeight = 30;
        int footerHeight = 22;
        int side = Math.max(150, Math.min(230, w / 4));
        footer = new Rect(window.x() + pad, window.bottom() - pad - footerHeight,
                w - pad * 2, footerHeight);
        Rect body = new Rect(window.x() + pad, window.y() + headerHeight, w - pad * 2,
                Math.max(60, footer.y() - (window.y() + headerHeight) - 8));
        map = new Rect(body.x(), body.y(), Math.max(60, body.width() - side - 10), body.height());
        sidebar = new Rect(map.right() + 10, body.y(), side, body.height());

        int buttonY = sidebar.y() + 26;
        chrome.add(new ButtonWidget(new Rect(sidebar.x(), buttonY, sidebar.width(), 20),
                tr("silkvis.map.jump_to_player"), this::centerOnPlayer));
        chrome.add(new ButtonWidget(new Rect(sidebar.x(), buttonY + 24, sidebar.width(), 20),
                tr("silkvis.map.add_waypoint"), () -> addWaypoint(centerX, centerZ)));
        chrome.add(new ButtonWidget(new Rect(sidebar.x(), buttonY + 48, sidebar.width(), 20),
                tr("silkvis.map.remove_waypoint"), this::removeSelected)
                .style(ButtonWidget.Style.DANGER));
        chrome.add(new ButtonWidget(new Rect(window.right() - pad - 22, window.y() + 8, 22, 18),
                "×", this::close).style(ButtonWidget.Style.SECONDARY));

        raster = null;
        rasterKey = 0;
        framesSinceBuild = 100;
    }

    // ------------------------------------------------------------------ waypoints

    private void centerOnPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            centerX = client.player.getBlockX();
            centerZ = client.player.getBlockZ();
        }
    }

    private void addWaypoint(int blockX, int blockZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        String dimension = client != null && client.world != null
                ? client.world.getRegistryKey().getValue().toString() : "minecraft:overworld";
        int count = runtime.markers().inDimension(dimension).size() + 1;
        String label = tr("silkvis.map.waypoint_default") + " " + count;
        double y = client != null && client.player != null ? client.player.getY() : 64;
        Waypoint waypoint = Waypoint.create(label, dimension, blockX + 0.5, y, blockZ + 0.5,
                Color.argb(runtime.theme().accent()));
        runtime.setMarkers(runtime.markers().with(waypoint));
        runtime.saveMarkers();
        selectedId = waypoint.id();
    }

    private void removeSelected() {
        if (selectedId == null) {
            return;
        }
        runtime.setMarkers(runtime.markers().without(selectedId));
        runtime.saveMarkers();
        selectedId = null;
    }

    private List<Waypoint> waypoints() {
        Optional<MapIdentity> identity = runtime.mapIdentity();
        if (identity.isEmpty()) {
            return List.of();
        }
        return runtime.markers().inDimension(identity.get().dimensionId());
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Painter painter = new VanillaPainter(context);
        ThemeTokens theme = runtime.theme();
        float open = Animation.progress(openedAtNanos, theme.motionMs());
        painter.fill(new Rect(0, 0, width, height),
                theme.background().withAlpha(0.55f * open).argb());
        for (int layer = 3; layer >= 1; layer--) {
            painter.roundRect(window.inset(-layer * 2, -layer * 2),
                    theme.radiusPanel() + layer * 2,
                    Color.argb(theme.background().withAlpha(0.10f)));
        }
        painter.roundPanel(window, theme.radiusPanel(), Color.argb(theme.background()),
                Color.argb(theme.border()));

        renderHeader(painter, theme);
        renderMap(painter, theme);
        renderSidebar(painter, theme, mouseX, mouseY);
        for (Widget widget : chrome) {
            widget.render(painter, theme);
        }
        painter.roundPanel(footer, theme.radiusControl() + 2,
                Color.argb(theme.surface().withAlpha(0.45f)), Color.argb(theme.border()));
        painter.text(tr("silkvis.map.hint"), footer.x() + 6,
                footer.centerY() - painter.lineHeight() / 2,
                Color.argb(theme.textSecondary()), false);
    }

    private void renderHeader(Painter painter, ThemeTokens theme) {
        String title = tr("silkvis.map.title");
        painter.text(title, window.x() + 12, window.y() + 9, Color.argb(theme.accent()), true);
        String status = centerX + " / " + centerZ + "  ·  ×" + bpp;
        painter.text(status, window.x() + 12 + painter.textWidth(title) + 12, window.y() + 9,
                Color.argb(theme.textSecondary()), false);
        String dimension = runtime.mapIdentity().map(MapIdentity::dimensionId).orElse("—");
        painter.text(dimension, window.right() - 40 - painter.textWidth(dimension),
                window.y() + 9, Color.argb(theme.textPrimary()), false);
    }

    private void renderMap(Painter painter, ThemeTokens theme) {
        painter.roundPanel(map, theme.radiusControl(),
                Color.argb(theme.background().withAlpha(0.92f)), Color.argb(theme.border()));
        Optional<MapService> service = runtime.mapService();
        Optional<MapIdentity> identity = runtime.mapIdentity();
        if (service.isEmpty() || identity.isEmpty()) {
            drawCentered(painter, theme, tr("silkvis.map.inactive"));
            return;
        }
        buildRasterIfNeeded(service.get(), identity.get());
        painter.pushClip(map);
        drawRaster(painter);
        painter.popClip();
        detectAndRequestMissing(service.get(), identity.get());

        MinecraftClient client = MinecraftClient.getInstance();
        int playerX = client != null && client.player != null ? client.player.getBlockX() : centerX;
        int playerZ = client != null && client.player != null ? client.player.getBlockZ() : centerZ;
        for (Waypoint waypoint : waypoints()) {
            int wx = map.x() + Math.round((float) (waypoint.x() - viewMinX()) / bpp);
            int wz = map.y() + Math.round((float) (waypoint.z() - viewMinZ()) / bpp);
            if (!map.contains(wx, wz)) {
                continue;
            }
            boolean selected = waypoint.id().equals(selectedId);
            painter.fill(new Rect(wx - 3, wz - 3, 7, 7), Color.argb(theme.background()));
            painter.fill(new Rect(wx - 2, wz - 2, 5, 5),
                    selected ? Color.argb(theme.accent()) : (waypoint.color() | 0xFF000000));
            painter.text(waypoint.label(), wx + 6, wz - 4,
                    Color.argb(theme.textPrimary()), true);
        }
        drawPlayerMarker(painter, theme, playerX, playerZ);
        if (!rasterFilled) {
            drawCentered(painter, theme, tr("silkvis.map.building"));
        }
    }

    private void drawCentered(Painter painter, ThemeTokens theme, String text) {
        painter.text(text, map.centerX() - painter.textWidth(text) / 2,
                map.centerY() - painter.lineHeight() / 2,
                Color.argb(theme.textSecondary()), false);
    }

    /**
     * Player marker: a diamond plus a nose in the facing direction. A real rotated polygon
     * is not available from the painter, and a plain dot gives no heading at all.
     */
    private void drawPlayerMarker(Painter painter, ThemeTokens theme, int blockX, int blockZ) {
        int px = map.x() + Math.round((float) (blockX - viewMinX()) / bpp);
        int pz = map.y() + Math.round((float) (blockZ - viewMinZ()) / bpp);
        if (!map.contains(px, pz)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        float yaw = client != null && client.player != null ? client.player.getYaw() : 0f;
        double radians = Math.toRadians(yaw);
        int noseX = px - (int) Math.round(Math.sin(radians) * 6);
        int noseZ = pz + (int) Math.round(Math.cos(radians) * 6);
        painter.fill(new Rect(px - 3, pz - 3, 7, 7), Color.argb(theme.background()));
        painter.fill(new Rect(px - 2, pz - 2, 5, 5), Color.argb(theme.accent()));
        if (Math.abs(noseX - px) >= Math.abs(noseZ - pz)) {
            painter.fill(new Rect(Math.min(px, noseX), pz, Math.abs(noseX - px) + 1, 1),
                    Color.argb(theme.accent()));
        } else {
            painter.fill(new Rect(px, Math.min(pz, noseZ), 1, Math.abs(noseZ - pz) + 1),
                    Color.argb(theme.accent()));
        }
    }

    private void renderSidebar(Painter painter, ThemeTokens theme, int mouseX, int mouseY) {
        painter.roundPanel(sidebar, theme.radiusControl(),
                Color.argb(theme.surface().withAlpha(0.55f)), Color.argb(theme.border()));
        painter.text(tr("silkvis.map.waypoints"), sidebar.x() + 8, sidebar.y() + 8,
                Color.argb(theme.textPrimary()), false);
        List<Waypoint> waypoints = waypoints();
        Rect list = listBounds();
        painter.pushClip(list);
        if (waypoints.isEmpty()) {
            painter.text(tr("silkvis.map.no_waypoints"), list.x() + 4, list.y() + 4,
                    Color.argb(theme.textSecondary()), false);
        }
        int rows = Math.max(1, list.height() / ROW);
        int maxScroll = Math.max(0, waypoints.size() - rows);
        waypointScroll = Math.max(0, Math.min(waypointScroll, maxScroll));
        for (int i = 0; i < rows && waypointScroll + i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(waypointScroll + i);
            Rect row = new Rect(list.x(), list.y() + i * ROW, list.width(), ROW - 2);
            boolean selected = waypoint.id().equals(selectedId);
            if (selected || row.contains(mouseX, mouseY)) {
                painter.roundRect(row, Math.max(0, theme.radiusControl() - 2),
                        Color.argb(theme.surfaceHover()));
            }
            painter.fill(new Rect(row.x() + 3, row.centerY() - 2, 4, 4),
                    waypoint.color() | 0xFF000000);
            painter.text(waypoint.label(), row.x() + 12, row.y() + 4,
                    Color.argb(selected ? theme.accent() : theme.textPrimary()), false);
            String coords = (int) waypoint.x() + " " + (int) waypoint.z();
            painter.text(coords, row.right() - 4 - painter.textWidth(coords), row.y() + 4,
                    Color.argb(theme.textSecondary()), false);
        }
        painter.popClip();
    }

    private Rect listBounds() {
        return new Rect(sidebar.x() + 4, sidebar.y() + 82,
                Math.max(20, sidebar.width() - 8), Math.max(10, sidebar.height() - 90));
    }

    private int viewMinX() {
        return centerX - (map.width() * bpp) / 2;
    }

    private int viewMinZ() {
        return centerZ - (map.height() * bpp) / 2;
    }

    // ------------------------------------------------------------------ raster

    /**
     * Cache lookups happen once per view change, plus a refresh every 20 frames so tiles
     * that finish building appear without the user having to move the map. Drawing only
     * walks the array afterwards.
     */
    private void buildRasterIfNeeded(MapService service, MapIdentity identity) {
        int cellsX = Math.max(1, map.width() / CELL);
        int cellsZ = Math.max(1, map.height() / CELL);
        long key = ((long) centerX * 1_000_003L + centerZ) * 1_000_003L + bpp;
        key = key * 1_000_003L + (cellsX * 31L + cellsZ);
        framesSinceBuild++;
        if (raster != null && key == rasterKey && framesSinceBuild < 20) {
            return;
        }
        framesSinceBuild = 0;
        if (raster == null || raster.length != cellsX * cellsZ) {
            raster = new int[cellsX * cellsZ];
        }
        rasterW = cellsX;
        rasterH = cellsZ;
        rasterKey = key;
        int lod = TileMath.lodForBlocksPerPixel(bpp);
        int minX = viewMinX();
        int minZ = viewMinZ();
        int filled = 0;
        for (int cz = 0; cz < cellsZ; cz++) {
            int blockZ = minZ + (cz * bpp * map.height()) / cellsZ;
            for (int cx = 0; cx < cellsX; cx++) {
                int blockX = minX + (cx * bpp * map.width()) / cellsX;
                TileKey tileKey = TileMath.tileOf(identity, lod, blockX, blockZ);
                Optional<MapTile> tile = service.view(tileKey);
                if (tile.isEmpty()) {
                    raster[cz * cellsX + cx] = 0;
                    continue;
                }
                raster[cz * cellsX + cx] = tile.get().colorAt(
                        TileMath.pixelInTile(blockX, lod), TileMath.pixelInTile(blockZ, lod));
                filled++;
            }
        }
        rasterFilled = filled > 0;
    }

    /** Same rows, merged horizontally: uniform water or plains cost one quad, not 150. */
    private void drawRaster(Painter painter) {
        if (raster == null || rasterW == 0 || rasterH == 0) {
            return;
        }
        for (int cz = 0; cz < rasterH; cz++) {
            int y0 = map.y() + (cz * map.height()) / rasterH;
            int y1 = map.y() + ((cz + 1) * map.height()) / rasterH;
            int height = Math.max(1, y1 - y0);
            int runStart = -1;
            int runColor = 0;
            for (int cx = 0; cx <= rasterW; cx++) {
                int color = cx < rasterW ? raster[cz * rasterW + cx] : 0;
                if (color == runColor) {
                    continue;
                }
                if (runColor != 0 && runStart >= 0) {
                    int x0 = map.x() + (runStart * map.width()) / rasterW;
                    int x1 = map.x() + (cx * map.width()) / rasterW;
                    painter.fill(new Rect(x0, y0, Math.max(1, x1 - x0), height), runColor);
                }
                runStart = cx;
                runColor = color;
            }
        }
    }

    /**
     * Hands the visible area to the prefetcher. Without this the map would be a monoculture
     * of whatever the minimap happened to cache while the player walked.
     */
    private void detectAndRequestMissing(MapService service, MapIdentity identity) {
        MapPrefetcher.requestArea(service, identity, viewMinX(), viewMinZ(),
                Math.max(1, map.width() * bpp), Math.max(1, map.height() * bpp), bpp);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        for (Widget widget : chrome) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (map.contains((int) mouseX, (int) mouseY)) {
            if (button == 0) {
                dragging = true;
                dragX = mouseX;
                dragZ = mouseY;
            } else if (button == 1) {
                addWaypoint(blockXAt(mouseX), blockZAt(mouseY));
            }
            return true;
        }
        if (sidebar.contains((int) mouseX, (int) mouseY) && button == 0) {
            selectRowAt(mouseY);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private int blockXAt(double mouseX) {
        return viewMinX() + (int) ((mouseX - map.x()) * bpp);
    }

    private int blockZAt(double mouseY) {
        return viewMinZ() + (int) ((mouseY - map.y()) * bpp);
    }

    private void selectRowAt(double mouseY) {
        Rect list = listBounds();
        if (mouseY < list.y()) {
            return;
        }
        List<Waypoint> waypoints = waypoints();
        int index = waypointScroll + (int) ((mouseY - list.y()) / ROW);
        if (index < 0 || index >= waypoints.size()) {
            return;
        }
        Waypoint waypoint = waypoints.get(index);
        boolean wasSelected = waypoint.id().equals(selectedId);
        selectedId = waypoint.id();
        // Second click on the same row jumps there: select-then-jump beats a tiny arrow.
        if (wasSelected) {
            centerX = (int) waypoint.x();
            centerZ = (int) waypoint.z();
        }
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (!dragging || click.button() != 0) {
            return super.mouseDragged(click, offsetX, offsetY);
        }
        double mouseX = click.x();
        double mouseY = click.y();
        centerX -= (int) Math.round((mouseX - dragX) * bpp);
        centerZ -= (int) Math.round((mouseY - dragZ) * bpp);
        dragX = mouseX;
        dragZ = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            return true;
        }
        for (Widget widget : chrome) {
            if (widget.mouseReleased(click.x(), click.y(), click.button())) {
                return true;
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        if (map.contains((int) mouseX, (int) mouseY)) {
            int target = verticalAmount > 0 ? bpp - 1 : bpp + 1;
            target = Math.max(MIN_BPP, Math.min(MAX_BPP, target));
            if (target != bpp) {
                // Keep the block under the cursor under the cursor.
                double offsetX = mouseX - map.x() - map.width() / 2.0;
                double offsetZ = mouseY - map.y() - map.height() / 2.0;
                centerX += (int) Math.round(offsetX * (bpp - target));
                centerZ += (int) Math.round(offsetZ * (bpp - target));
                bpp = target;
            }
            return true;
        }
        if (sidebar.contains((int) mouseX, (int) mouseY)) {
            waypointScroll = Math.max(0, waypointScroll + (verticalAmount > 0 ? -1 : 1));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == Keys.ESCAPE) {
            close();
            return true;
        }
        if (keyCode == Keys.R) {
            centerOnPlayer();
            return true;
        }
        if (keyCode == KEY_EQUAL || keyCode == KEY_NUMPAD_ADD) {
            bpp = Math.max(MIN_BPP, bpp - 1);
            return true;
        }
        if (keyCode == KEY_MINUS || keyCode == KEY_NUMPAD_SUB) {
            bpp = Math.min(MAX_BPP, bpp + 1);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

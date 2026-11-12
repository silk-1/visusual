package silkvis.ui;

/**
 * Responsive layout for the main window (UI-03).
 *
 * <ul>
 *   <li>wide: navigation 144 + module list >= 220 + settings >= 260</li>
 *   <li>compact (&lt; 700): navigation + single content column (settings replace the list)</li>
 *   <li>narrow (&lt; 420): one column with a navigation drawer</li>
 * </ul>
 * Target max size is 960x620 with safe margins >= 12.
 */
public record MainScreenLayout(
        Mode mode,
        Rect window,
        Rect navigation,
        Rect moduleList,
        Rect settings,
        Rect footer,
        Rect closeButton,
        Rect search,
        Rect drawerButton
) {
    public enum Mode {
        WIDE,
        COMPACT,
        NARROW
    }

    public static final int MAX_WIDTH = 960;
    public static final int MAX_HEIGHT = 620;
    public static final int SAFE_MARGIN = 12;
    public static final int NAV_WIDTH = 144;
    public static final int LIST_MIN_WIDTH = 220;
    public static final int SETTINGS_MIN_WIDTH = 260;
    public static final int COMPACT_BREAKPOINT = 700;
    public static final int NARROW_BREAKPOINT = 420;
    public static final int HEADER_HEIGHT = 32;
    public static final int FOOTER_HEIGHT = 32;

    public static MainScreenLayout compute(int screenWidth, int screenHeight) {
        int margin = SAFE_MARGIN;
        int width = Math.min(MAX_WIDTH, Math.max(0, screenWidth - margin * 2));
        int height = Math.min(MAX_HEIGHT, Math.max(0, screenHeight - margin * 2));
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        Rect window = new Rect(x, y, width, height);

        Mode mode = width >= COMPACT_BREAKPOINT ? Mode.WIDE
                : width >= NARROW_BREAKPOINT ? Mode.COMPACT : Mode.NARROW;

        int closeSize = 20;
        Rect close = new Rect(window.right() - margin - closeSize, window.y() + margin, closeSize, closeSize);
        Rect footer = new Rect(window.x() + margin, window.bottom() - margin - FOOTER_HEIGHT,
                window.width() - margin * 2, FOOTER_HEIGHT);
        Rect body = new Rect(window.x() + margin, window.y() + margin + HEADER_HEIGHT,
                window.width() - margin * 2, Math.max(0, window.height() - margin * 2 - HEADER_HEIGHT - FOOTER_HEIGHT));
        Rect search = new Rect(body.x() + NAV_WIDTH + 8, window.y() + margin,
                Math.max(0, Math.min(240, body.width() - NAV_WIDTH - 40)), 20);
        Rect drawerButton = new Rect(window.x() + margin, window.y() + margin, 20, 20);

        if (mode == Mode.WIDE) {
            int navWidth = NAV_WIDTH;
            int remaining = body.width() - navWidth;
            int listWidth = Math.max(LIST_MIN_WIDTH, (remaining * 45) / 100);
            int settingsWidth = Math.max(SETTINGS_MIN_WIDTH, remaining - listWidth);
            listWidth = Math.max(0, remaining - settingsWidth);
            Rect nav = new Rect(body.x(), body.y(), navWidth, body.height());
            Rect list = new Rect(nav.right() + 8, body.y(), Math.max(0, listWidth - 8), body.height());
            Rect settings = new Rect(list.right() + 8, body.y(), Math.max(0, settingsWidth - 8), body.height());
            return new MainScreenLayout(mode, window, nav, list, settings, footer, close, search, drawerButton);
        }
        if (mode == Mode.COMPACT) {
            Rect nav = new Rect(body.x(), body.y(), NAV_WIDTH, body.height());
            Rect content = new Rect(nav.right() + 8, body.y(), Math.max(0, body.width() - NAV_WIDTH - 8), body.height());
            // Compact mode shows settings instead of the module list.
            Rect settings = content;
            Rect list = new Rect(content.x(), content.y(), 0, 0);
            return new MainScreenLayout(mode, window, nav, list, settings, footer, close, search, drawerButton);
        }
        // NARROW: single column; module list and settings share the content area, the
        // navigation becomes an overlay drawer toggled by drawerButton.
        Rect content = body;
        Rect nav = new Rect(window.x(), window.y(), Math.min(NAV_WIDTH, window.width()), window.height());
        Rect list = new Rect(content.x(), content.y(), 0, 0);
        return new MainScreenLayout(mode, window, nav, list, content, footer, close, search, drawerButton);
    }

    /** Settings column is either a separate pane or the single content column. */
    public boolean showsSettingsInContent() {
        return mode != Mode.WIDE;
    }
}

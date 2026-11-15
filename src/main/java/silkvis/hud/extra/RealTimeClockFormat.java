package silkvis.hud.extra;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatting only: time and time zone always come from the caller. */
public final class RealTimeClockFormat {
    private static final DateTimeFormatter TIME_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter TIME_12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT);

    private RealTimeClockFormat() {
    }

    /** Returns fixed-width 24-hour time or 12-hour time with English AM/PM. */
    public static String format(long epochMillis, ZoneId zone, boolean twentyFourHour) {
        return (twentyFourHour ? TIME_24 : TIME_12).format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /** Returns a day.month.year date in the supplied zone. */
    public static String formatDate(long epochMillis, ZoneId zone) {
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}

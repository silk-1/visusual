package silkvis.verify;

import silkvis.core.Clock;
import silkvis.hud.extra.CoordinateConverter;
import silkvis.hud.extra.PingSampler;
import silkvis.hud.extra.RealTimeClockFormat;
import silkvis.hud.extra.SessionClock;
import silkvis.hud.extra.SlimeChunk;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import static silkvis.verify.SelfCheck.assertEquals;
import static silkvis.verify.SelfCheck.assertTrue;
import static silkvis.verify.SelfCheck.check;
import static silkvis.verify.SelfCheck.expectThrows;

final class HudExtraChecks {
    private HudExtraChecks() {
    }

    static void run() {
        check("HUD extras slime predicate is deterministic", () -> {
            for (long seed : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                for (int x = -50; x < 50; x++) {
                    assertEquals(SlimeChunk.isSlimeChunk(seed, x, -x),
                            SlimeChunk.isSlimeChunk(seed, x, -x), "repeat result");
                }
            }
        });
        check("HUD extras literal slime formula preserves overflow vectors", () -> {
            // Fixed vectors independently calculated with signed int arithmetic and a 48-bit LCG.
            assertTrue(!SlimeChunk.isSlimeChunk(0, 0, 0), "origin");
            assertTrue(SlimeChunk.isSlimeChunk(0, 46341, 46341), "int square overflow");
            assertTrue(SlimeChunk.isSlimeChunk(0, -129, 7), "negative coordinates");
            assertTrue(!SlimeChunk.isSlimeChunk(0, Integer.MAX_VALUE, Integer.MIN_VALUE), "int extremes");
        });
        check("HUD extras slime density stays near ten percent across 5000 chunks", () -> {
            int count = 0;
            for (int x = -50; x < 50; x++) {
                for (int z = -25; z < 25; z++) {
                    if (SlimeChunk.isSlimeChunk(0, x, z)) {
                        count++;
                    }
                }
            }
            assertTrue(count >= 400 && count <= 600, "expected 400..600, got " + count);
        });
        check("HUD extras chunk packing uses floor on each signed axis", () -> {
            long packed = SlimeChunk.chunkIndexOf(-129, -1);
            assertEquals(-9, (int) packed, "X is low word");
            assertEquals(-1, (int) (packed >> 32), "Z is high word");
            assertEquals(0L, SlimeChunk.chunkIndexOf(15, 15), "last block of origin chunk");
            assertEquals(1L, SlimeChunk.chunkIndexOf(16, 0), "next X chunk");
            assertEquals(-4294967296L, SlimeChunk.chunkIndexOf(0, -16), "exact negative Z boundary");
            assertEquals(-134217728, (int) SlimeChunk.chunkIndexOf(Integer.MIN_VALUE, 0), "minimum int");
        });
        check("HUD extras portal conversion floors negatives and checks multiplication overflow", () -> {
            assertEquals(-17L, CoordinateConverter.toNether(-129), "floor division");
            assertEquals(-128L, CoordinateConverter.toOverworld(-16), "multiply");
            assertEquals(-1L, CoordinateConverter.toNether(-1), "negative fraction");
            assertEquals(15L, CoordinateConverter.toNether(120), "positive conversion");
            assertEquals(Long.MIN_VALUE / 8L, CoordinateConverter.toNether(Long.MIN_VALUE), "long minimum");
            expectThrows(ArithmeticException.class, () -> CoordinateConverter.toOverworld(Long.MAX_VALUE),
                    "do not silently wrap portal coordinate");
        });
        check("HUD extras border clamp and coordinate text match contract", () -> {
            assertEquals(29_999_984L, CoordinateConverter.clampToWorldBorder(Long.MAX_VALUE),
                    "positive border");
            assertEquals(-29_999_984L, CoordinateConverter.clampToWorldBorder(Long.MIN_VALUE),
                    "negative border");
            assertEquals(-29_999_984L, CoordinateConverter.clampToWorldBorder(-29_999_984L), "inclusive");
            assertEquals(120L, CoordinateConverter.clampToWorldBorder(120), "interior unchanged");
            assertEquals("X 120 | Y 64 | Z -340", CoordinateConverter.format(120, 64, -340), "text");
            assertEquals("\u0410\u0434: 15 7 \u00b7 \u0412\u0435\u0440\u0445: 120 56",
                    CoordinateConverter.portals(120, 56), "portal text");
            assertEquals("\u0410\u0434: -17 -1 \u00b7 \u0412\u0435\u0440\u0445: -129 -1",
                    CoordinateConverter.portals(-129, -1), "original Overworld position retained");
        });
        check("HUD extras duration formatting handles zero minutes hours and negatives", () -> {
            assertEquals("00:00", SessionClock.formatDuration(0), "zero");
            assertEquals("01:05", SessionClock.formatDuration(65_000), "minutes");
            assertEquals("1:02:05", SessionClock.formatDuration(3_725_000), "hours");
            assertEquals("59:59", SessionClock.formatDuration(3_599_999), "subsecond truncation");
            assertEquals("1:00:00", SessionClock.formatDuration(3_600_000), "hour boundary");
            assertEquals("25:00:00", SessionClock.formatDuration(90_000_000), "no day wrapping");
            assertEquals("00:00", SessionClock.formatDuration(-1), "negative");
        });
        check("HUD extras session timer uses monotonic time and resets", () -> {
            MutableClock clock = new MutableClock();
            clock.nanos = -90_000_000_000L;
            SessionClock timer = new SessionClock(clock);
            assertEquals(0L, timer.elapsedMillis(), "starts now at arbitrary origin");
            clock.nanos += 65_000_999_999L;
            assertEquals(65_000L, timer.elapsedMillis(), "complete milliseconds");
            assertEquals("01:05", timer.format(), "session format");
            timer.reset();
            assertEquals(0L, timer.elapsedMillis(), "reset");
            clock.nanos -= 1;
            assertEquals(0L, timer.elapsedMillis(), "backwards test clock");
        });
        check("HUD extras real clock formats explicit zone and locale", () -> {
            long time = Instant.parse("2026-11-12T23:05:00Z").toEpochMilli();
            ZoneId utc = ZoneId.of("UTC");
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("ar-EG"));
                assertEquals("23:05", RealTimeClockFormat.format(time, utc, true), "24 hours");
                assertEquals("11:05 PM", RealTimeClockFormat.format(time, utc, false), "12 hours");
                assertEquals("12.11.2026", RealTimeClockFormat.formatDate(time, utc), "date");
                assertEquals("04:05", RealTimeClockFormat.format(time, ZoneId.of("+05:00"), true), "zone");
                assertEquals("13.11.2026", RealTimeClockFormat.formatDate(time, ZoneId.of("+05:00")),
                        "date rollover");
                assertEquals("12:00 AM", RealTimeClockFormat.format(0, utc, false), "midnight");
                assertEquals("12:00 PM", RealTimeClockFormat.format(43_200_000, utc, false), "noon");
            } finally {
                Locale.setDefault(previous);
            }
        });
        check("HUD extras ping empty odd even and overflow-safe median", () -> {
            PingSampler ping = new PingSampler();
            assertEquals(-1, ping.latest(), "empty latest");
            assertEquals(-1, ping.median(), "empty median");
            assertEquals(-1, ping.p95(), "empty p95");
            ping.push(30); ping.push(10); ping.push(20);
            assertEquals(20, ping.median(), "odd median");
            ping.push(41);
            assertEquals(25, ping.median(), "even median floor");
            assertEquals(41, ping.latest(), "sorting does not reorder ring");
            PingSampler extremes = new PingSampler();
            extremes.push(Integer.MAX_VALUE); extremes.push(Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, extremes.median(), "sum uses long");
        });
        check("HUD extras ping p95 nearest rank and bounded ring eviction", () -> {
            PingSampler hundred = new PingSampler(100);
            PingSampler forty = new PingSampler();
            for (int i = 1; i <= 100; i++) {
                hundred.push(i);
                forty.push(i);
            }
            assertEquals(95, hundred.p95(), "100 samples nearest rank");
            assertEquals(80, forty.median(), "retains only 61..100");
            assertEquals(98, forty.p95(), "40 samples nearest rank");
            assertEquals(100, forty.latest(), "latest survives quantiles");
            forty.push(0);
            assertEquals(0, forty.latest(), "wrapped latest");
            assertEquals(80, forty.median(), "oldest was evicted, not smallest");
        });
        check("HUD extras ping rejects invalid input without losing history", () -> {
            expectThrows(IllegalArgumentException.class, () -> new PingSampler(0), "zero capacity");
            PingSampler ping = new PingSampler(1);
            ping.push(15);
            expectThrows(IllegalArgumentException.class, () -> ping.push(-1), "negative ping");
            assertEquals(15, ping.latest(), "failed push leaves history unchanged");
            ping.push(0);
            assertEquals(0, ping.median(), "zero ping valid");
            assertEquals(0, ping.p95(), "single sample p95");
        });
    }

    private static final class MutableClock implements Clock {
        private long nanos;

        @Override
        public long monotonicNanos() {
            return nanos;
        }

        @Override
        public long epochMillis() {
            throw new AssertionError("session timer must not read wall time");
        }
    }
}

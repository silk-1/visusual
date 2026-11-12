package silkvis.verify;

import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free self checks for the Minecraft-independent core.
 *
 * <p>Run with {@code ./gradlew selfCheck} or directly on a plain JDK:
 * {@code javac -d out $(find src/main/java -name '*.java' -not -path '*client*' -not -path '*render*' -not -path '*hud/HudDataCollector*') ...}
 * (see docs/status-report.md for the exact command used in this environment).
 * JUnit is not used yet: the authoring environment has no access to Maven Central.</p>
 */
public final class SelfCheck {
    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    private SelfCheck() {
    }

    public static void main(String[] args) {
        CoreChecks.run();
        UiChecks.run();
        MapChecks.run();
        MediaChecks.run();
        DestinationChecks.run();
        MiscChecks.run();

        System.out.println();
        System.out.println("passed=" + passed + " failed=" + failures.size());
        for (String failure : failures) {
            System.out.println("FAILED: " + failure);
        }
        // Always exit explicitly: a single leaked non-daemon thread would otherwise
        // keep the JVM (and the Gradle build) hanging after the report is printed.
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    interface Body {
        void run() throws Exception;
    }

    static void check(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable t) {
            failures.add(name + " -> " + t);
            System.out.println("FAIL " + name + " -> " + t);
        }
    }

    static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected " + expected + ", actual " + actual + ")");
        }
    }

    /** Float-heavy UI values widen to double here; the tolerance is what matters. */
    static void assertEquals(double expected, double actual, double delta, String message) {
        assertClose(expected, actual, delta, message);
    }

    static void assertClose(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " (expected " + expected + ", actual " + actual + ")");
        }
    }

    static void expectThrows(Class<? extends Throwable> type, Body body, String message) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }
            throw new AssertionError(message + " (wrong exception " + t.getClass().getSimpleName() + ")");
        }
        throw new AssertionError(message + " (nothing thrown)");
    }
}

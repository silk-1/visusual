package silkvis.visual.soup.animation;

/**
 * Ported from SoupVisuals {@code padej.soup.base.util.animation.Interpolations} (Padej_).
 *
 * <p>The original registers eleven anonymous classes; the maths below is byte-for-byte
 * the same, only the anonymous classes became named constants so the file stays readable.
 * Option names ("Linear", "Smooth", ... "Decelerate") are the values stored in the config
 * and must not be renamed.</p>
 */
public final class Interpolations {
    public static final Interpolation LINEAR = named("Linear", progress -> progress);
    public static final Interpolation EASE_IN = named("Smooth", progress -> progress * progress);
    public static final Interpolation EASE_OUT =
            named("Fast", progress -> 1.0 - Math.pow(1.0 - progress, 2.0));
    public static final Interpolation EASE_IN_OUT = named("Balanced", progress -> progress < 0.5
            ? 2.0 * progress * progress
            : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0);
    public static final Interpolation EASE_OUT_BACK = named("Back", progress ->
            1.0 + 2.70158 * Math.pow(progress - 1.0, 3.0) + 1.70158 * Math.pow(progress - 1.0, 2.0));
    public static final Interpolation EASE_IN_BACK = named("Overshoot", progress -> {
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return c3 * progress * progress * progress - c1 * progress * progress;
    });
    public static final Interpolation ELASTIC = named("Elastic", progress -> {
        if (progress == 0.0) {
            return 0.0;
        }
        if (progress == 1.0) {
            return 1.0;
        }
        double c4 = Math.PI * 2.0 / 3.0;
        return Math.pow(2.0, -10.0 * progress) * Math.sin((progress * 10.0 - 0.75) * c4) + 1.0;
    });
    public static final Interpolation BOUNCE = named("Bounce", progress -> {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (progress < 1.0 / d1) {
            return n1 * progress * progress;
        } else if (progress < 2.0 / d1) {
            double shifted = progress - 1.5 / d1;
            return n1 * shifted * shifted + 0.75;
        } else if (progress < 2.5 / d1) {
            double shifted = progress - 2.25 / d1;
            return n1 * shifted * shifted + 0.9375;
        }
        double shifted = progress - 2.625 / d1;
        return n1 * shifted * shifted + 0.984375;
    });
    public static final Interpolation IOS_EASE_OUT =
            named("Ease Out", progress -> 1.0 - Math.pow(1.0 - progress, 3.0));
    public static final Interpolation IOS_SPRING = named("Spring", progress -> {
        double damping = 0.7;
        double frequency = 1.5;
        double decay = Math.exp(-damping * frequency * progress);
        double oscillation = Math.cos(frequency * progress * Math.PI * 2.0);
        return 1.0 - (decay * oscillation * 0.1 + decay * (1.0 - progress));
    });
    public static final Interpolation IOS_DECELERATE =
            named("Decelerate", progress -> progress * (2.0 - progress));

    private Interpolations() {
    }

    /** Config value to interpolation; unknown names fall back to linear like the original. */
    public static Interpolation getByName(String name) {
        if (name == null) {
            return LINEAR;
        }
        return switch (name) {
            case "Linear" -> LINEAR;
            case "Smooth" -> EASE_IN;
            case "Fast" -> EASE_OUT;
            case "Balanced" -> EASE_IN_OUT;
            case "Back" -> EASE_OUT_BACK;
            case "Overshoot" -> EASE_IN_BACK;
            case "Elastic" -> ELASTIC;
            case "Bounce" -> BOUNCE;
            case "Ease Out" -> IOS_EASE_OUT;
            case "Spring" -> IOS_SPRING;
            case "Decelerate" -> IOS_DECELERATE;
            default -> LINEAR;
        };
    }

    /** Order shown in the select; it is also the order persisted configs were written in. */
    public static String[] getAllNames() {
        return new String[]{"Linear", "Smooth", "Fast", "Balanced", "Back", "Overshoot",
                "Elastic", "Bounce", "Ease Out", "Spring", "Decelerate"};
    }

    private static Interpolation named(String name, java.util.function.DoubleUnaryOperator fn) {
        return new Interpolation() {
            @Override
            public double interpolate(double progress) {
                return fn.applyAsDouble(progress);
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }
}

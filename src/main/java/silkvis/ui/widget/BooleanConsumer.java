package silkvis.ui.widget;

/** Primitive boolean callback (avoids boxing in render paths). */
@FunctionalInterface
public interface BooleanConsumer {
    void accept(boolean value);
}

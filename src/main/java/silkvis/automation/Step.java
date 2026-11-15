package silkvis.automation;

import java.util.Objects;

/**
 * One unit of work produced by a goal.
 *
 * <p>Immutable value with a human-readable target so the status line can say what is happening
 * ("MINE 132 64 -8") without asking the goal again. Coordinates are block coordinates; the
 * executor owns the conversion to world space.</p>
 */
public record Step(StepKind kind, int x, int y, int z, int ticks) {
    public Step {
        Objects.requireNonNull(kind, "kind");
        if (ticks < 0) {
            throw new IllegalArgumentException("negative ticks: " + ticks);
        }
    }

    public static Step gotoBlock(int x, int y, int z) {
        return new Step(StepKind.GOTO, x, y, z, 0);
    }

    public static Step mine(int x, int y, int z) {
        return new Step(StepKind.MINE, x, y, z, 0);
    }

    public static Step place(int x, int y, int z) {
        return new Step(StepKind.PLACE, x, y, z, 0);
    }

    public static Step look(int x, int y, int z) {
        return new Step(StepKind.LOOK, x, y, z, 0);
    }

    public static Step attack(int entityId) {
        return new Step(StepKind.ATTACK, entityId, 0, 0, 0);
    }

    public static Step useItem() {
        return new Step(StepKind.USE_ITEM, 0, 0, 0, 0);
    }

    public static Step selectSlot(int hotbarSlot) {
        return new Step(StepKind.SELECT_SLOT, hotbarSlot, 0, 0, 0);
    }

    public static Step wait(int ticks) {
        return new Step(StepKind.WAIT, 0, 0, 0, Math.max(1, ticks));
    }

    /** Short form for the status line: {@code MINE 132 64 -8}. */
    public String describe() {
        return switch (kind) {
            case GOTO, MINE, PLACE, LOOK -> kind.name() + " " + x + " " + y + " " + z;
            case ATTACK -> "ATTACK #" + x;
            case SELECT_SLOT -> "SLOT " + x;
            case WAIT -> "WAIT " + ticks;
            case USE_ITEM -> "USE";
        };
    }
}

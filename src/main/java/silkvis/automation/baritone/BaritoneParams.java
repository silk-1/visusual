package silkvis.automation.baritone;

import silkvis.automation.BlockPos;

import java.util.EnumSet;

/**
 * What every Baritone page remembers: the picked blocks, the numbers and the text.
 *
 * <p>One instance per action, owned by the runtime rather than the screen, so closing the window
 * does not throw away a selection the player just made. Mutable, Minecraft-free, and free of
 * Baritone classes, which is what lets the self checks build real command lines without a game
 * or the mod installed.</p>
 */
public final class BaritoneParams {
    private final EnumSet<BaritoneBlock> blocks = EnumSet.noneOf(BaritoneBlock.class);
    private int number;
    private int number2;
    private int number3;
    private String text = "";
    private BlockPos point;

    /** Fresh state for one action: its documented defaults, nothing picked, no point. */
    public static BaritoneParams forAction(BaritoneAction action) {
        BaritoneParams params = new BaritoneParams();
        if (action != null) {
            int[] defaults = action.defaults();
            if (defaults.length > 0) {
                params.number = defaults[0];
            }
            if (defaults.length > 1) {
                params.number2 = defaults[1];
            }
            if (defaults.length > 2) {
                params.number3 = defaults[2];
            }
        }
        return params;
    }

    public boolean selected(BaritoneBlock block) {
        return block != null && this.blocks.contains(block);
    }

    /** Adds or removes one block; a checkbox is the only way in, so this is never a maybe. */
    public void toggle(BaritoneBlock block) {
        if (block == null) {
            return;
        }
        if (!this.blocks.remove(block)) {
            this.blocks.add(block);
        }
    }

    public void clearBlocks() {
        this.blocks.clear();
    }

    public int selectedCount() {
        return this.blocks.size();
    }

    /** Space-separated ids of everything picked, in menu order. */
    public String blocksLine() {
        StringBuilder out = new StringBuilder();
        for (BaritoneBlock block : BaritoneBlock.values()) {
            if (!this.blocks.contains(block)) {
                continue;
            }
            for (String id : block.blocks()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(id);
            }
        }
        return out.toString();
    }

    /** First picked id, for the commands that take exactly one block. */
    public String firstBlock() {
        for (BaritoneBlock block : BaritoneBlock.values()) {
            if (this.blocks.contains(block)) {
                return block.blocks().get(0);
            }
        }
        return "";
    }

    public int number() {
        return this.number;
    }

    public void setNumber(int value) {
        this.number = Math.max(0, value);
    }

    public void bumpNumber(int delta, int step, int min, int max) {
        int next = this.number + delta * Math.max(1, step);
        this.number = Math.max(min, Math.min(max, next));
    }

    public int number2() {
        return this.number2;
    }

    public int number3() {
        return this.number3;
    }

    public void bumpNumber2(int delta, int min, int max) {
        this.number2 = Math.max(min, Math.min(max, this.number2 + delta));
    }

    public void bumpNumber3(int delta, int min, int max) {
        this.number3 = Math.max(min, Math.min(max, this.number3 + delta));
    }

    public String text() {
        return this.text;
    }

    public void setText(String value) {
        this.text = value == null ? "" : value;
    }

    /** Target of a point task, or {@code null} when none was read yet. */
    public BlockPos point() {
        return this.point;
    }

    public void setPoint(BlockPos value) {
        this.point = value;
    }
}

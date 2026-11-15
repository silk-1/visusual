package silkvis.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* over the block grid, with Baritone's shape but none of its size.
 *
 * <p>The world sits behind {@link Grid}: the search never touches Minecraft. That is the whole
 * point, because the pathfinder can then be run in the self checks against a hand-built world
 * (a wall with a doorway, a cliff, a lava moat) and a regression shows up without launching the
 * game.</p>
 *
 * <p>Deliberate differences from the original mod:</p>
 * <ul>
 *   <li><b>Breaking is opt-in.</b> A plain "go there" walks around a wall; only a goal that asks
 *       for it prices {@link PathCost#BREAK} and emits MINE steps.</li>
 *   <li><b>No chunk loading, no parkour chains.</b> Both need guarantees a client mod cannot make;
 *       a plan that needs them fails with a reason instead of misbehaving.</li>
 *   <li><b>Budgeted.</b> The search spends a {@link Budget} and returns null when it runs out, so
 *       a long search resumes next tick instead of freezing a frame.</li>
 * </ul>
 */
public final class Movement {

    /** Read-only view of the world the search may query. */
    public interface Grid {
        /** True when the block at that position blocks movement (and is not a door). */
        boolean solid(int x, int y, int z);

        /** True when the player can stand in that cell (air or fluid, no full block). */
        boolean passable(int x, int y, int z);

        /** True when the cell holds a fluid the player can swim through. */
        boolean water(int x, int y, int z);

        /** True when the block can be broken in reasonable time with the current tools. */
        boolean breakable(int x, int y, int z);

        /** True when the cell is a door that walking into opens. */
        boolean door(int x, int y, int z);

        /** Cells the search must never enter: lava, fire, cactus, the void. */
        boolean dangerous(int x, int y, int z);

        int minY();

        int maxY();
    }

    /** Result of a successful search. */
    public record Path(List<Step> steps, int nodesExpanded, double cost) {
        public boolean empty() {
            return steps.isEmpty();
        }
    }

    private static final int[][] HORIZONTAL = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private Movement() {
    }

    /**
     * Searches for a route from {@code start} to {@code goal}.
     *
     * @param allowBreak price breaking as {@link PathCost#BREAK} and emit MINE steps; when false
     *                   a solid block is a wall
     * @param maxNodes   hard cap on expanded nodes ({@link AutomationSettings#searchNodes()})
     * @return the route, or null when there is none inside the cap or the budget ran out
     */
    public static Path find(Grid grid, BlockPos start, BlockPos goal, boolean allowBreak,
                            int maxNodes, Budget budget) {
        if (start.equals(goal)) {
            return new Path(List.of(), 0, 0.0);
        }
        Map<BlockPos, Double> bestCost = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        open.add(new Node(start, null, 0.0, heuristic(start, goal), null));
        bestCost.put(start, 0.0);
        int expanded = 0;

        while (!open.isEmpty()) {
            if (budget != null && !budget.spend()) {
                return null;
            }
            Node current = open.poll();
            Double known = bestCost.get(current.position);
            if (known != null && current.g > known) {
                // A cheaper route to this cell was queued after this node was added.
                continue;
            }
            if (current.position.equals(goal)) {
                return new Path(reconstruct(current), expanded, current.g);
            }
            expanded++;
            if (expanded > maxNodes) {
                return null;
            }
            expand(grid, current, goal, allowBreak, bestCost, open, budget);
            if (budget != null && budget.spent()) {
                return null;
            }
        }
        return null;
    }

    private static void expand(Grid grid, Node node, BlockPos goal, boolean allowBreak,
                               Map<BlockPos, Double> bestCost, PriorityQueue<Node> open,
                               Budget budget) {
        BlockPos from = node.position;
        for (int[] delta : HORIZONTAL) {
            int x = from.x() + delta[0];
            int z = from.z() + delta[1];
            boolean diagonal = delta[0] != 0 && delta[1] != 0;
            // Same level, then one up, then drops of one to three blocks.
            offer(grid, node, goal, x, from.y(), z, diagonal, false, allowBreak, bestCost, open, budget);
            if (from.y() + 1 <= grid.maxY() && grid.passable(from.x(), from.y() + 1, from.z())
                    && grid.passable(from.x(), from.y() + 2, from.z())) {
                offer(grid, node, goal, x, from.y() + 1, z, diagonal, false, allowBreak,
                        bestCost, open, budget);
            }
            for (int dy = 1; dy <= 3; dy++) {
                int y = from.y() - dy;
                if (y < grid.minY() || !grid.passable(x, y, z) || !grid.passable(x, y + 1, z)) {
                    break;
                }
                if (!floor(grid, x, y - 1, z)) {
                    continue;
                }
                offer(grid, node, goal, x, y, z, diagonal, true, allowBreak, bestCost, open, budget);
                break;
            }
            if (budget != null && budget.spent()) {
                return;
            }
        }
    }

    /** Prices one candidate cell and queues it when it is cheaper than what is known. */
    private static void offer(Grid grid, Node node, BlockPos goal, int x, int y, int z,
                              boolean diagonal, boolean falling, boolean allowBreak,
                              Map<BlockPos, Double> bestCost, PriorityQueue<Node> open,
                              Budget budget) {
        if (y < grid.minY() || y > grid.maxY()) {
            return;
        }
        if (grid.dangerous(x, y, z) || grid.dangerous(x, y + 1, z)) {
            return;
        }
        BlockPos target = new BlockPos(x, y, z);
        if (target.equals(node.position)) {
            return;
        }
        // No corner cutting: a diagonal step between two solid blocks is not a move the game
        // lets a player make, and allowing it here produced plans that walk through sealed
        // corners ("it pathed through the wall" is exactly what this prevents).
        if (diagonal && !cornerOpen(grid, node.position, x, y, z)) {
            return;
        }
        boolean water = grid.water(x, y, z);
        double cost;
        Step via;
        boolean standable = !falling && grid.passable(x, y, z) && grid.passable(x, y + 1, z)
                && floor(grid, x, y - 1, z);
        if (standable) {
            cost = PathCost.of(0, diagonal, false, water, false, false);
            via = Step.gotoBlock(x, y, z);
        } else if (falling) {
            cost = PathCost.of(-(node.position.y() - y), diagonal, false, water, false, false);
            via = Step.gotoBlock(x, y, z);
        } else if (grid.door(x, y, z) && grid.passable(x, y + 1, z)) {
            cost = PathCost.DOOR;
            via = Step.gotoBlock(x, y, z);
        } else if (allowBreak && grid.solid(x, y, z) && grid.breakable(x, y, z)
                && grid.passable(x, y + 1, z)) {
            cost = PathCost.BREAK;
            // Keep the dig in the plan: the executor has to break before it can walk in.
            via = Step.mine(x, y, z);
        } else {
            return;
        }
        if (cost == PathCost.IMPOSSIBLE) {
            return;
        }
        if (budget != null && !budget.spend()) {
            return;
        }
        double g = node.g + cost;
        Double known = bestCost.get(target);
        if (known != null && known <= g) {
            return;
        }
        bestCost.put(target, g);
        open.add(new Node(target, node, g, heuristic(target, goal), via));
    }

    /**
     * True when at least one of the two cells beside a diagonal step is walkable at both the feet
     * and the head level. Both blocked means the player would have to squeeze through the corner
     * shared by two solid blocks, which vanilla does not allow.
     */
    private static boolean cornerOpen(Grid grid, BlockPos from, int x, int y, int z) {
        boolean sideX = grid.passable(x, y, from.z()) && grid.passable(x, y + 1, from.z());
        boolean sideZ = grid.passable(from.x(), y, z) && grid.passable(from.x(), y + 1, z);
        return sideX || sideZ;
    }

    /** A floor cell: not passable and not water, so glass/slabs/farmland all count. */
    private static boolean floor(Grid grid, int x, int y, int z) {
        return !grid.passable(x, y, z) && !grid.water(x, y, z);
    }

    private static List<Step> reconstruct(Node node) {
        List<Step> steps = new ArrayList<>();
        Node cursor = node;
        while (cursor != null && cursor.via != null) {
            steps.add(cursor.via);
            cursor = cursor.parent;
        }
        Collections.reverse(steps);
        return steps;
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.x() - to.x());
        int dy = Math.abs(from.y() - to.y());
        int dz = Math.abs(from.z() - to.z());
        // Octile distance: the true lower bound when a diagonal costs 1.4142. Manhattan would
        // overestimate and make A* return longer paths.
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        return PathCost.WALK * (max - min) + PathCost.DIAGONAL * min + PathCost.STEP_UP * dy;
    }

    private static final class Node implements Comparable<Node> {
        private final BlockPos position;
        private final Node parent;
        private final double g;
        private final double f;
        private final Step via;

        Node(BlockPos position, Node parent, double g, double h, Step via) {
            this.position = position;
            this.parent = parent;
            this.g = g;
            this.f = g + h;
            this.via = via;
        }

        @Override
        public int compareTo(Node other) {
            int byF = Double.compare(f, other.f);
            return byF != 0 ? byF : position.compareTo(other.position);
        }
    }
}

package com.norbertfila.mob.ai.navigation;

import com.norbertfila.mob.ai.navigation.PathTerrain.Cell;
import com.norbertfila.world.BlockPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * A* over the feet cells a mob can stand in: walking, diagonal moves without cutting corners, one-block steps up,
 * drops of up to three blocks and swimming. The search is bounded, and an unreachable goal yields a path to the
 * closest cell found, as in Minecraft.
 */
public final class PathFinder {
    public static final int MAX_DROP = 3;
    private static final double DIAGONAL = Math.sqrt(2);
    private static final double STEP_UP_COST = .5;
    private static final double DROP_COST = .25;
    private static final double WATER_COST = 2;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private final int maxVisitedNodes;

    public PathFinder(int maxVisitedNodes) {
        if (maxVisitedNodes < 1) throw new IllegalArgumentException("Node budget must be positive");
        this.maxVisitedNodes = maxVisitedNodes;
    }

    private static final class Node implements Comparable<Node> {
        final int x, y, z;
        double cost, estimate;
        Node parent;
        boolean closed;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(cost + estimate, other.cost + other.estimate);
        }
    }

    /**
     * Searches from the start cell to any cell within {@code reach} blocks of the goal. {@code height} is the number
     * of free cells the mob needs above its feet.
     */
    public Optional<Path> find(PathTerrain terrain, BlockPos start, BlockPos goal, int height, double reach) {
        if (height < 1 || !(reach >= 0)) throw new IllegalArgumentException("Invalid path request");
        Map<Long, Node> nodes = new HashMap<>();
        var open = new PriorityQueue<Node>();
        var first = node(nodes, start.x(), start.y(), start.z());
        first.estimate = distance(first, goal);
        open.add(first);
        Node closest = first;
        int visited = 0;
        while (!open.isEmpty() && visited < maxVisitedNodes) {
            var current = open.poll();
            if (current.closed) continue;
            current.closed = true;
            visited++;
            if (current.estimate < closest.estimate) closest = current;
            if (current.estimate <= reach) return Optional.of(path(current, true));
            for (int[] direction : DIRECTIONS) {
                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                int x = current.x + direction[0], z = current.z + direction[1];
                if (diagonal
                        && (!clear(terrain, current.x + direction[0], current.y, current.z, height)
                                || !clear(terrain, current.x, current.y, current.z + direction[1], height))) continue;
                int y = landing(terrain, current, x, z, height, !diagonal);
                if (y == Integer.MIN_VALUE) continue;
                double step = diagonal ? DIAGONAL : 1;
                if (y > current.y) step += STEP_UP_COST;
                if (y < current.y) step += (current.y - y) * DROP_COST;
                if (terrain.cell(x, y, z) == Cell.WATER) step += WATER_COST;
                var next = node(nodes, x, y, z);
                if (next.closed || (next.parent != null || next == first) && next.cost <= current.cost + step) continue;
                next.cost = current.cost + step;
                next.estimate = distance(next, goal);
                next.parent = current;
                open.add(next);
            }
        }
        return closest == first ? Optional.empty() : Optional.of(path(closest, false));
    }

    /**
     * The feet height a mob ends up at when it moves into the column from {@code fromY}: level ground, one step up
     * (straight moves only, because a jump needs room) or a drop of at most {@link #MAX_DROP} blocks.
     */
    private static int landing(PathTerrain terrain, Node from, int x, int z, int height, boolean mayStepUp) {
        int fromY = from.y;
        if (standable(terrain, x, fromY, z, height)) return fromY;
        if (mayStepUp
                && !passable(terrain.cell(x, fromY, z))
                && passable(terrain.cell(from.x, fromY + height, from.z))
                && standable(terrain, x, fromY + 1, z, height)) return fromY + 1;
        if (!clear(terrain, x, fromY, z, height)) return Integer.MIN_VALUE;
        for (int y = fromY - 1; y >= fromY - MAX_DROP; y--) {
            if (!passable(terrain.cell(x, y, z))) return Integer.MIN_VALUE;
            if (standable(terrain, x, y, z, height)) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Feet and head cells are free, and there is ground below or water to swim in. */
    public static boolean standable(PathTerrain terrain, int x, int y, int z, int height) {
        if (!clear(terrain, x, y, z, height)) return false;
        Cell feet = terrain.cell(x, y, z);
        return feet == Cell.WATER || terrain.cell(x, y - 1, z) == Cell.SOLID;
    }

    private static boolean clear(PathTerrain terrain, int x, int y, int z, int height) {
        for (int dy = 0; dy < height; dy++) if (!passable(terrain.cell(x, y + dy, z))) return false;
        return true;
    }

    private static boolean passable(Cell cell) {
        return cell == Cell.OPEN || cell == Cell.WATER;
    }

    private static Node node(Map<Long, Node> nodes, int x, int y, int z) {
        long key = ((long) x & 0x3ffffff) << 38 | ((long) y & 0xfff) << 26 | (long) z & 0x3ffffff;
        return nodes.computeIfAbsent(key, ignored -> new Node(x, y, z));
    }

    private static double distance(Node node, BlockPos goal) {
        double dx = node.x - goal.x(), dy = node.y - goal.y(), dz = node.z - goal.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Path path(Node end, boolean reachesGoal) {
        var cells = new ArrayList<BlockPos>();
        for (var node = end; node != null; node = node.parent) cells.add(new BlockPos(node.x, node.y, node.z));
        Collections.reverse(cells);
        return new Path(cells, reachesGoal);
    }
}

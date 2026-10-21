package com.norbertfila.mob.ai.navigation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.ai.navigation.PathTerrain.Cell;
import com.norbertfila.world.BlockPos;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PathFinderTest {
    private final Grid grid = new Grid();
    private final PathFinder finder = new PathFinder(4000);

    @Test
    void walksStraightAndDiagonallyOnFlatGround() {
        var path = find(0, 0, 10, 0).orElseThrow();
        assertTrue(path.reachesGoal());
        assertEquals(11, path.nodes().size());
        var diagonal = find(0, 0, 5, 5).orElseThrow();
        assertEquals(6, diagonal.nodes().size());
    }

    @Test
    void goesAroundWallsWithoutCuttingCorners() {
        for (int z = -5; z <= 5; z++) grid.wall(3, z, 3);
        var path = find(0, 0, 6, 0).orElseThrow();
        assertTrue(path.reachesGoal());
        for (var node : path.nodes()) assertNotEquals(Cell.SOLID, grid.cell(node.x(), node.y(), node.z()));
        assertTrue(path.nodes().stream().anyMatch(n -> Math.abs(n.z()) > 5));
        grid.wall(10, 1, 1);
        grid.wall(11, 0, 1);
        var corner = finder.find(grid, new BlockPos(10, 0, 0), new BlockPos(11, 0, 1), 2, 0)
                .orElseThrow();
        assertTrue(corner.reachesGoal());
        assertNotEquals(new BlockPos(11, 0, 1), corner.nodes().get(1), "cut a blocked corner");
    }

    @Test
    void climbsOneBlockButNotTwoAndDropsAtMostThree() {
        for (int z = -10; z <= 10; z++) grid.wall(2, z, 1);
        var up = finder.find(grid, new BlockPos(0, 0, 0), new BlockPos(2, 1, 0), 2, 0)
                .orElseThrow();
        assertTrue(up.reachesGoal());
        var high = new Grid();
        for (int z = -10; z <= 10; z++) high.wall(2, z, 2);
        assertFalse(finder.find(high, new BlockPos(0, 0, 0), new BlockPos(2, 2, 0), 2, 0)
                .map(Path::reachesGoal)
                .orElse(false));
        var cliff = new Grid();
        cliff.floorAt(5, -3);
        assertTrue(finder.find(cliff, new BlockPos(0, 0, 0), new BlockPos(5, -3, 0), 2, 0)
                .orElseThrow()
                .reachesGoal());
        var deep = new Grid();
        deep.floorAt(5, -4);
        assertFalse(finder.find(deep, new BlockPos(0, 0, 0), new BlockPos(5, -4, 0), 2, 0)
                .map(Path::reachesGoal)
                .orElse(false));
    }

    @Test
    void avoidsLavaAndUnknownChunksButSwimsThroughWater() {
        for (int z = -300; z <= 300; z++) grid.set(3, 0, z, Cell.DANGER);
        assertFalse(find(0, 0, 6, 0).map(Path::reachesGoal).orElse(false));
        var lake = new Grid();
        for (int z = -20; z <= 20; z++) {
            lake.set(3, -1, z, Cell.WATER);
            lake.set(3, 0, z, Cell.WATER);
        }
        var swim = finder.find(lake, new BlockPos(0, 0, 0), new BlockPos(6, 0, 0), 2, 0)
                .orElseThrow();
        assertTrue(swim.reachesGoal());
        var unknown = new Grid();
        for (int z = -300; z <= 300; z++) unknown.set(3, 0, z, Cell.UNKNOWN);
        assertFalse(finder.find(unknown, new BlockPos(0, 0, 0), new BlockPos(6, 0, 0), 2, 0)
                .map(Path::reachesGoal)
                .orElse(false));
    }

    @Test
    void unreachableGoalsGiveAPathToTheClosestCellWithinTheBudget() {
        for (int x = -300; x <= 300; x++) grid.wall(x, 4, 3);
        var partial = find(0, 0, 0, 10).orElseThrow();
        assertFalse(partial.reachesGoal());
        assertEquals(3, partial.end().z());
        assertTrue(new PathFinder(5)
                .find(grid, new BlockPos(0, 0, 0), new BlockPos(100, 0, 0), 2, 0)
                .isPresent());
        assertEquals(2, find(0, 0, 3, 0, 1).orElseThrow().end().x());
    }

    private java.util.Optional<Path> find(int fromX, int fromZ, int toX, int toZ) {
        return find(fromX, fromZ, toX, toZ, 0);
    }

    private java.util.Optional<Path> find(int fromX, int fromZ, int toX, int toZ, double reach) {
        return finder.find(grid, new BlockPos(fromX, 0, fromZ), new BlockPos(toX, 0, toZ), 2, reach);
    }

    /** Solid ground at y = -1 and open air above, with explicit overrides. */
    private static final class Grid implements PathTerrain {
        private final Map<BlockPos, Cell> cells = new HashMap<>();
        private final Map<Long, Integer> floors = new HashMap<>();

        void set(int x, int y, int z, Cell cell) {
            cells.put(new BlockPos(x, y, z), cell);
        }

        void wall(int x, int z, int height) {
            for (int y = 0; y < height; y++) set(x, y, z, Cell.SOLID);
        }

        /** Columns from {@code fromX} on have their feet level at {@code feetY}. */
        void floorAt(int fromX, int feetY) {
            floors.put((long) fromX, feetY);
        }

        @Override
        public Cell cell(int x, int y, int z) {
            var cell = cells.get(new BlockPos(x, y, z));
            if (cell != null) return cell;
            int ground = -1;
            for (var floor : floors.entrySet()) if (x >= floor.getKey()) ground = floor.getValue() - 1;
            return y <= ground ? Cell.SOLID : Cell.OPEN;
        }
    }
}

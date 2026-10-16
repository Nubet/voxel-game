package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.function.DoubleBinaryOperator;
import org.junit.jupiter.api.Test;

class RegionalHydrologyTest {
    private static final DoubleBinaryOperator GROUND = (x, z) -> 120 + .015 * x + .025 * z;

    @Test
    void wetChannelsStayConnectedThroughOverlappingValleySegments() {
        DoubleBinaryOperator corrugated =
                (x, z) -> GROUND.applyAsDouble(x, z) + 24 * Math.sin(x * .03) * Math.cos(z * .02);
        var water = new RegionalHydrology(42, 28, corrugated);
        for (int cx = -1; cx <= 1; cx++) {
            var start = water.node(cx, 0);
            var end = water.downstream(start);
            assertNotNull(end);
            int minX = (int) Math.min(start.x(), end.x()) - 64;
            int minZ = (int) Math.min(start.z(), end.z()) - 64;
            int width = (int) Math.abs(start.x() - end.x()) + 129;
            int depth = (int) Math.abs(start.z() - end.z()) + 129;
            boolean[] visited = new boolean[width * depth];
            var queue = new ArrayDeque<Integer>();
            int origin = ((int) start.z() - minZ) * width + (int) start.x() - minX;
            int goal = ((int) end.z() - minZ) * width + (int) end.x() - minX;
            visited[origin] = true;
            queue.add(origin);
            while (!queue.isEmpty() && !visited[goal]) {
                int index = queue.removeFirst();
                int x = minX + index % width, z = minZ + index / width;
                var current = water.sample(x, z, corrugated.applyAsDouble(x, z));
                for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                    int nx = x + d[0], nz = z + d[1];
                    if (nx < minX || nx >= minX + width || nz < minZ || nz >= minZ + depth) continue;
                    int next = (nz - minZ) * width + nx - minX;
                    if (visited[next]) continue;
                    var sample = water.sample(nx, nz, corrugated.applyAsDouble(nx, nz));
                    if (sample.kind() == WaterKind.RIVER
                            && sample.height() < sample.waterLevel()
                            && sample.waterLevel() <= current.waterLevel()) {
                        visited[next] = true;
                        queue.addLast(next);
                    }
                }
            }
            assertTrue(visited[goal], "An unbroken downhill water path must connect drainage nodes");
        }
    }

    @Test
    void drainageAlwaysDescendsAndChannelsMeetAtSharedNodes() {
        var water = new RegionalHydrology(42, 28, GROUND);
        int tributaries = 0;
        for (int cz = -4; cz <= 4; cz++)
            for (int cx = -4; cx <= 4; cx++) {
                var node = water.node(cx, cz);
                var target = water.downstream(node);
                if (target == null) continue;
                assertTrue(target.level() < node.level());
                var sample = water.sample(node.x(), node.z(), GROUND.applyAsDouble(node.x(), node.z()));
                assertEquals(WaterKind.RIVER, sample.kind());
                assertTrue(sample.height() < sample.waterLevel());
                var end = water.sample(target.x(), target.z(), GROUND.applyAsDouble(target.x(), target.z()));
                assertEquals(target.level(), end.waterLevel());
                assertTrue(end.height() < end.waterLevel());
                tributaries++;
                int steps = 0;
                while (target != null) {
                    assertTrue(++steps < 200, "A strictly descending drainage path must terminate");
                    var next = water.downstream(target);
                    if (next != null) assertTrue(next.level() < target.level());
                    target = next;
                }
            }
        assertTrue(tributaries > 20);
    }

    @Test
    void plansAreContinuousAcrossPositiveAndNegativeRegionBoundaries() {
        var water = new RegionalHydrology(-918273, 28, GROUND);
        int crossings = 0;
        for (boolean xAxis : new boolean[] {true, false})
            for (int boundary : new int[] {-2048, 0, 2048})
                for (int along = -2048; along < 2048; along++) {
                    double lx = xAxis ? boundary - 1e-6 : along, lz = xAxis ? along : boundary - 1e-6;
                    double rx = xAxis ? boundary + 1e-6 : along, rz = xAxis ? along : boundary + 1e-6;
                    var left = water.sample(lx, lz, GROUND.applyAsDouble(lx, lz));
                    var right = water.sample(rx, rz, GROUND.applyAsDouble(rx, rz));
                    assertEquals(left.height(), right.height(), .001);
                    if (left.kind() == WaterKind.RIVER && left.height() < left.waterLevel()) {
                        assertEquals(left.waterLevel(), right.waterLevel());
                        crossings++;
                    }
                }
        assertTrue(crossings > 10, "The fixture must exercise wet region crossings");
    }

    @Test
    void workerOrderDoesNotAffectTheRegionalNetwork() throws Exception {
        var expected = new RegionalHydrology(42, 28, GROUND);
        var parallel = new RegionalHydrology(42, 28, GROUND);
        try (var workers = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Future<RegionalHydrology.Sample>>();
            for (int i = 0; i < 64; i++) {
                double x = (i % 8 - 4) * 1024, z = (i / 8 - 4) * 1024;
                tasks.add(workers.submit(() -> parallel.sample(x, z, GROUND.applyAsDouble(x, z))));
            }
            for (int i = 63; i >= 0; i--) {
                double x = (i % 8 - 4) * 1024, z = (i / 8 - 4) * 1024;
                assertEquals(
                        expected.sample(x, z, GROUND.applyAsDouble(x, z)),
                        tasks.get(i).get());
            }
        }
    }
}

package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.fluid.FluidSimulator;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CaveSystemTest {
    private static final GroundSampler GROUND =
            (x, z) -> new TerrainSummary(96, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, -64, 28);

    @Test
    void localSystemsHaveRealCyclesAndTwoLevelsInsteadOfRelabeledSinglePaths() {
        for (var character : List.of(CaveProfile.SPAGHETTI, CaveProfile.INTERCONNECTED)) {
            for (int seed : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 42}) {
                var result = system(seed, character);
                var system = result.system();
                assertTrue(system.routes().size() >= system.junctions().size());
                var carver = new CaveCarver(GROUND, seed);
                var occupied = voxelize(result.segments(), carver);
                var direct = system.routes().get(1).points();
                var midpoint = direct.get(direct.size() / 2);
                occupied.removeIf(p ->
                        Math.pow(p.x() + .5 - midpoint.x(), 2) + Math.pow(p.z() + .5 - midpoint.z(), 2) < 2.8 * 2.8);
                assertTrue(
                        connected(
                                occupied,
                                system.junctions().get(1),
                                system.junctions().get(2)),
                        "Alternate route must survive a blocked main corridor: " + character + " seed=" + seed);
                if (character == CaveProfile.INTERCONNECTED) {
                    var lower = system.routes().stream()
                            .filter(r -> r.profile() == CaveProfile.VERTICAL)
                            .toList();
                    assertEquals(2, lower.size());
                    for (var route : lower) {
                        double drop =
                                Math.abs(system.junctions().get(route.from()).y()
                                        - system.junctions().get(route.to()).y());
                        assertTrue(drop >= 8);
                        assertTrue(connected(
                                voxelize(result.segments(), carver),
                                system.junctions().get(route.from()),
                                system.junctions().get(route.to())));
                    }
                }
            }
        }
    }

    @Test
    void floodedPassageHasDryBanksCrossesChunksAndDoesNotSpreadWhenSimulationStarts() {
        CaveSystemPlanner.Result result = null;
        CaveFeatures features = null;
        CaveFeatures.Feature flooded = null;
        int selectedSeed = 0;
        for (int seed = 0; seed < 64 && flooded == null; seed++) {
            selectedSeed = seed;
            result = system(seed, CaveProfile.CLASSIC);
            var plan = new CavePlan(result.segments(), List.of(result.system()));
            var carver = new CaveCarver(GROUND, seed);
            features = new CaveFeatures(
                    seed, GROUND, carver, (x, z) -> x == 0 && z == 0 ? plan : new CavePlan(List.of()), plan::segments);
            for (int z = -4; z <= 4; z++)
                for (int x = -4; x <= 4; x++)
                    for (var feature : features.at(x, z))
                        if (feature.type() == CaveFeatures.Type.FLOODED) flooded = feature;
        }
        assertNotNull(flooded);
        var plan = new CavePlan(result.segments(), List.of(result.system()));
        var world = new World();
        var carver = new CaveCarver(GROUND, selectedSeed);
        var chunks = new HashSet<ChunkPos>();
        for (var cell : flooded.cells()) chunks.add(Coordinates.worldToChunk(cell));
        assertTrue(chunks.size() > 1, "Water must exercise chunk boundaries");
        for (var position : new ArrayList<>(chunks))
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    for (int x = -1; x <= 1; x++)
                        chunks.add(new ChunkPos(position.x() + x, position.y() + y, position.z() + z));
        for (var position : chunks) {
            byte[] blocks = new byte[4096];
            Arrays.fill(blocks, BlockId.STONE);
            var segments = plan.segments(position.x(), position.z());
            carver.carve(position, blocks, segments);
            features.apply(position, blocks, List.of(flooded), segments, new BitSet());
            world.putChunk(position, new ChunkData(position, blocks).toChunk());
        }
        for (var route : result.system().routes())
            if (route.profile() == CaveProfile.FLOODED)
                for (int bank : new int[] {route.from(), route.to()}) {
                    var point = result.system().junctions().get(bank);
                    assertEquals(
                            BlockId.AIR,
                            world.getBlock(new BlockPos((int) Math.floor(point.x()), (int) Math.floor(point.y()), (int)
                                            Math.floor(point.z())))
                                    .orElseThrow());
                }
        var simulator = new FluidSimulator(world, 8);
        for (var cell : flooded.cells()) {
            assertEquals(BlockId.WATER, world.getBlock(cell).orElseThrow());
            simulator.onBlockChanged(cell, 0);
        }
        for (int tick = 1; tick <= 100; tick++) simulator.tick(tick);
        assertTrue(world.changes().changes().isEmpty(), "Contained source volume must remain settled");
    }

    @Test
    void floodedVolumeIsRejectedIfAnotherUnloadedTunnelPuncturesTheBasin() {
        var center = new CaveSystem.Point(15.5, -20.5, 15.5);
        var system = new CaveSystem(
                CaveProfile.CLASSIC, List.of(center), List.of(), List.of(new CaveSystem.Flood(center, -21)));
        var segments = new ArrayList<CavePlan.Segment>();
        segments.add(new CavePlan.Segment(center.x(), center.y(), center.z(), 3.5, 2.5));
        assertTrue(floodedFeatures(new CavePlan(segments, List.of(system))).stream()
                .anyMatch(f -> f.type() == CaveFeatures.Type.FLOODED));
        for (int x = 16; x < 50; x++) segments.add(new CavePlan.Segment(x + .5, -21.5, 15.5, 1.5, 1.85));
        assertTrue(floodedFeatures(new CavePlan(segments, List.of(system))).stream()
                .noneMatch(f -> f.type() == CaveFeatures.Type.FLOODED));
    }

    private static List<CaveFeatures.Feature> floodedFeatures(CavePlan plan) {
        return new CaveFeatures(
                        42,
                        GROUND,
                        new CaveCarver(GROUND, 42),
                        (x, z) -> x == 0 && z == 0 ? plan : new CavePlan(List.of()),
                        plan::segments)
                .at(0, 0);
    }

    @Test
    void spaghettiRespectsBothNarrowAndWideConfigurations() {
        for (double[] widths : new double[][] {{1.3, 1.5}, {2.3, 2.5}}) {
            var config = new CaveConfig(20, 30, widths[0], widths[1], 768);
            var result = new CaveSystemPlanner(
                            new SplittableRandom(42),
                            config,
                            -64,
                            new CaveRegionSampler.Sample(.5, 1, .5),
                            (x, z) -> 96)
                    .plan(0, 0, 0, CaveProfile.SPAGHETTI);
            for (var segment : result.segments()) {
                if (segment.profile() == CaveProfile.CHAMBER) continue;
                assertTrue(segment.radiusX() >= widths[0] && segment.radiusX() <= widths[1]);
                assertTrue(segment.radiusZ() >= widths[0] && segment.radiusZ() <= widths[1]);
            }
        }
    }

    @Test
    void distributionLeavesSolidRegionsAndClassicSystemsRemainMostCommon() {
        int empty = 0, classic = 0, other = 0;
        var planner = new CavePlanner(42, -64, (x, z) -> 96);
        for (int z = -5; z <= 5; z++)
            for (int x = -5; x <= 5; x++) {
                var plan = planner.plan(x, z);
                if (plan.systems().isEmpty()) empty++;
                for (var system : plan.systems()) {
                    if (system.character() == CaveProfile.CLASSIC) classic++;
                    else other++;
                    for (var route : system.routes()) {
                        var a = route.points().getFirst();
                        var b = route.points().getLast();
                        assertEquals(system.junctions().get(route.from()).x(), a.x(), 1e-10);
                        assertEquals(system.junctions().get(route.to()).z(), b.z(), 1e-10);
                    }
                }
            }
        assertTrue(empty > 0 && empty < 60);
        assertTrue(classic > other);
    }

    private static CaveSystemPlanner.Result system(int seed, CaveProfile character) {
        return new CaveSystemPlanner(
                        new SplittableRandom(seed),
                        CaveConfig.defaults(),
                        -64,
                        new CaveRegionSampler.Sample(.5, 1, .5),
                        (x, z) -> 96)
                .plan(15.5, -20.5, 15.5, character);
    }

    private static Set<BlockPos> voxelize(List<CavePlan.Segment> segments, CaveCarver carver) {
        var air = new HashSet<BlockPos>();
        for (var segment : segments)
            for (int y = (int) Math.floor(segment.y() - segment.boundRadiusY());
                    y <= segment.y() + segment.boundRadiusY();
                    y++)
                for (int z = (int) Math.floor(segment.z() - segment.boundRadiusXZ());
                        z <= segment.z() + segment.boundRadiusXZ();
                        z++)
                    for (int x = (int) Math.floor(segment.x() - segment.boundRadiusXZ());
                            x <= segment.x() + segment.boundRadiusXZ();
                            x++) if (carver.contains(segment, x + .5, y + .5, z + .5)) air.add(new BlockPos(x, y, z));
        return air;
    }

    private static boolean connected(Set<BlockPos> air, CaveSystem.Point from, CaveSystem.Point to) {
        var start = standing(air, from);
        var end = standing(air, to);
        if (start == null || end == null) return false;
        var queue = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        queue.add(start);
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            if (!visited.add(p)
                    || !air.contains(p)
                    || !air.contains(new BlockPos(p.x(), p.y() + 1, p.z()))
                    || air.contains(new BlockPos(p.x(), p.y() - 1, p.z()))) continue;
            if (p.equals(end)) return true;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                for (int dy = -1; dy <= 1; dy++) {
                    if (dy == 1 && !air.contains(new BlockPos(p.x(), p.y() + 2, p.z()))) continue;
                    queue.add(new BlockPos(p.x() + d[0], p.y() + dy, p.z() + d[1]));
                }
        }
        return false;
    }

    private static BlockPos standing(Set<BlockPos> air, CaveSystem.Point point) {
        int x = (int) Math.floor(point.x()), z = (int) Math.floor(point.z());
        for (int y = (int) Math.floor(point.y()) - 1; y >= point.y() - 8; y--)
            if (air.contains(new BlockPos(x, y, z))
                    && air.contains(new BlockPos(x, y + 1, z))
                    && !air.contains(new BlockPos(x, y - 1, z))) return new BlockPos(x, y, z);
        return null;
    }
}

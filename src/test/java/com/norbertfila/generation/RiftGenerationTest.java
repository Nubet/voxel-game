package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.lod.AreaEdits;
import com.norbertfila.lod.FarTerrainGenerator;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.PlantRules;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RiftGenerationTest {
    static RiftPlanner.Plan find(TerrainGenerator terrain) {
        var planner = new RiftPlanner(terrain.seed());
        for (int radius = 0; radius <= 24; radius++)
            for (int z = -radius; z <= radius; z++)
                for (int x = -radius; x <= radius; x++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    var candidate = planner.plan(x, z);
                    if (candidate.isEmpty()) continue;
                    var center = candidate.get().nodes().get(3);
                    var plan = terrain.riftAt((int) center.x(), (int) center.z());
                    if (plan != null) return plan;
                }
        throw new AssertionError("No suitable rift for seed=" + terrain.seed());
    }

    @Test
    void naturalRiftSamplingMatchesChunksAndParallelGeneration() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        var plan = find(terrain);
        var center = plan.nodes().get(3);
        int x = (int) center.x(), z = (int) center.z();
        var surface = terrain.sample(x, z);
        assertEquals(WaterKind.NONE, surface.waterKind());
        assertEquals(BlockId.STONE, surface.surfaceMaterial());
        assertEquals(surface.surfaceHeight(), terrain.surfaceHeight(x, z));
        int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
        int top = surface.surfaceHeight() + 45;
        var positions = IntStream.rangeClosed(Math.floorDiv(surface.surfaceHeight(), 16), Math.floorDiv(top, 16))
                .mapToObj(y -> new ChunkPos(cx, y, cz))
                .toList();
        var expected = positions.stream().map(terrain::generate).toList();
        var fresh = new TerrainGenerator(42, TerrainConfig.defaults());
        IntStream.range(0, positions.size())
                .parallel()
                .forEach(i -> assertArrayEquals(
                        expected.get(i).blocks(),
                        fresh.generate(positions.get(i)).blocks()));
        for (int y = surface.surfaceHeight() + 1; y <= top; y++) {
            var data = expected.get(Math.floorDiv(y, 16) - positions.getFirst().y());
            byte block = data.get(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
            assertEquals(BlockId.AIR, block, "Open rift at y=" + y);
        }
    }

    @Test
    void existingCaveCanOpenOnRiftWallWithoutAForcedEntrance() {
        GroundSampler ground = (x, z) ->
                RiftTerrainFeature.carve(RiftTerrainFeatureTest.STRAIGHT, x, z, RiftTerrainFeatureTest.GROUND);
        var carver = new CaveCarver(ground);
        var tunnel = new CavePlan.Segment(0.5, 60.5, 5.5, 3, 3, 5, CaveProfile.CLASSIC, 0);
        assertEquals(BlockId.AIR, ground.sample(0, 0).materialAt(60));
        assertEquals(BlockId.STONE, ground.sample(0, 5).materialAt(60));
        assertTrue(carver.carves(0, 60, 5, BlockId.STONE, List.of(tunnel)));
        assertFalse(carver.carves(0, 60, 5, BlockId.STONE, List.of()));
    }

    @Test
    void treesUseFinalSoilAndDoNotGrowOnRiftStone() {
        GroundSampler ground = (x, z) ->
                RiftTerrainFeature.carve(RiftTerrainFeatureTest.STRAIGHT, x, z, RiftTerrainFeatureTest.GROUND);
        var trees = new TreeGenerator(42, (x, z) -> 1, ground, BiomeRegistry.defaults());
        int count = 0;
        for (int z = -32; z <= 32; z += 16)
            for (int x = -48; x <= 48; x += 16)
                for (var tree : trees.trees(x, z)) {
                    var soil = ground.sample(tree.x(), tree.z());
                    assertTrue(PlantRules.supports(soil.surfaceMaterial()));
                    assertEquals(soil.surfaceHeight() + 1, tree.baseY());
                    count++;
                }
        assertTrue(count > 0);
    }

    @Test
    void coarseLodPreservesTheCutAndSharesSamplesWithTileBorders() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        var p = find(terrain).nodes().get(3);
        int x = (int) p.x(), z = (int) p.z();
        int tx = Math.floorDiv(x, 64), tz = Math.floorDiv(z, 64);
        var far = new FarTerrainGenerator(terrain);
        for (int step : new int[] {1, 2, 4, 8, 16}) {
            var tile = far.generate(tx, tz, step, AreaEdits.none(tx * 64, tz * 64, 64));
            int cellX = Math.floorMod(x, 64) / step, cellZ = Math.floorMod(z, 64) / step;
            assertTrue(tile.heightAt(cellX, cellZ) <= terrain.surfaceHeight(x, z));
            var border = far.border(tx - 1, tz, step);
            int cells = 64 / step;
            for (int i = 0; i < cells; i++) assertEquals(tile.heightAt(0, i), border[cells + i]);
        }
    }
}

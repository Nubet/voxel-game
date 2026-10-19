package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaveFeaturesTest {
    @Test
    void rubbleFillsOnlyAPlannedAirPocketAndRespectsTheProtectedRoute() {
        var fixture = fixture(42);
        var rubble = new CaveFeatures.Feature(8, -45, 8, -45, -44, 1, CaveFeatures.Type.RUBBLE, BlockId.GRAVEL);
        byte[] blocks = new byte[4096];
        Arrays.fill(blocks, BlockId.AIR);
        fixture.features.apply(new ChunkPos(0, -3, 0), blocks, List.of(rubble), List.of(), new BitSet());
        assertEquals(BlockId.GRAVEL, blocks[((3 * 16 + 8) * 16) + 8]);

        var protectedSegment = new CavePlan.Segment(8.5, -44.5, 8.5, 1.5, 1.85, CaveProfile.CLASSIC);
        Arrays.fill(blocks, BlockId.AIR);
        fixture.features.apply(
                new ChunkPos(0, -3, 0), blocks, List.of(rubble), List.of(protectedSegment), new BitSet());
        assertEquals(BlockId.AIR, blocks[((3 * 16 + 8) * 16) + 8]);
    }

    @Test
    void boundedPoolsAreSealedAndReproduceAcrossChunkBoundaries() {
        var fixture = fixture(42);
        var planned = new HashSet<CaveFeatures.Feature>();
        for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) planned.addAll(fixture.features.at(x, z));
        assertTrue(planned.stream().anyMatch(f -> f.material() == BlockId.LAVA));
        assertTrue(planned.stream().anyMatch(f -> f.type() == CaveFeatures.Type.POOL));
        assertTrue(planned.stream().anyMatch(f -> f.type() == CaveFeatures.Type.SPRING));
        var sections = new HashMap<ChunkPos, Chunk>();
        boolean crossed = false;
        for (var feature : planned) {
            if (feature.type() == CaveFeatures.Type.SPRING) {
                for (int y = feature.minY(); y <= feature.maxY(); y++) {
                    assertEquals(BlockId.WATER, at(fixture, sections, feature.x(), y, feature.z()));
                    var position = new BlockPos(feature.x(), y, feature.z());
                    var local = Coordinates.worldToLocal(position);
                    assertEquals(
                            y == feature.y() ? WaterState.SOURCE : WaterState.FALLING,
                            sections.get(Coordinates.worldToChunk(position))
                                    .waterState(local.x(), local.y(), local.z()));
                }
            }
            if (feature.type() != CaveFeatures.Type.POOL) continue;
            var touched = new HashSet<ChunkPos>();
            for (int z = feature.z() - feature.radius(); z <= feature.z() + feature.radius(); z++)
                for (int x = feature.x() - feature.radius(); x <= feature.x() + feature.radius(); x++) {
                    if (!feature.containsXZ(x, z)) continue;
                    int y = feature.y();
                    var position = new BlockPos(x, y, z);
                    var chunk = Coordinates.worldToChunk(position);
                    touched.add(chunk);
                    assertEquals(feature.material(), at(fixture, sections, x, y, z));
                    assertTrue(BlockRegistry.DEFAULT.occludesFullFace(at(fixture, sections, x, y - 1, z)));
                    assertEquals(BlockId.AIR, at(fixture, sections, x, y + 1, z));
                    for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}})
                        if (!feature.containsXZ(x + d[0], z + d[1]))
                            assertTrue(BlockRegistry.DEFAULT.occludesFullFace(
                                    at(fixture, sections, x + d[0], y, z + d[1])));
                }
            crossed |= touched.size() > 1;
        }
        assertTrue(crossed, "Fixture must exercise a pool split between chunk columns");
        var recreated = fixture(42);
        for (var entry : sections.entrySet()) {
            var repeated = generate(recreated, entry.getKey());
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        assertEquals(entry.getValue().get(x, y, z), repeated.get(x, y, z));
                        assertEquals(entry.getValue().waterState(x, y, z), repeated.waterState(x, y, z));
                    }
        }
    }

    @Test
    void gravelAndPoolsNeverReplaceOresBedrockOrTheProtectedRoute() {
        var fixture = fixture(42);
        CaveFeatures.Feature pool = null;
        search:
        for (int z = 0; z < 8; z++)
            for (int x = 0; x < 8; x++)
                for (var feature : fixture.features.at(x, z))
                    if (feature.type() == CaveFeatures.Type.POOL) {
                        pool = feature;
                        break search;
                    }
        assertNotNull(pool);
        var position = Coordinates.worldToChunk(new BlockPos(pool.x(), pool.y(), pool.z()));
        byte[] blocks = new byte[4096];
        var features = List.of(pool);
        var segments = fixture.plan.segments(position.x(), position.z());
        Arrays.fill(blocks, BlockId.STONE);
        assertTrue(fixture.features.apply(position, blocks, features, segments, new BitSet()));
        Arrays.fill(blocks, BlockId.DIAMOND_ORE);
        fixture.features.apply(position, blocks, features, segments, new BitSet());
        for (byte block : blocks) assertEquals(BlockId.DIAMOND_ORE, block);
        Arrays.fill(blocks, BlockId.BEDROCK);
        fixture.features.apply(position, blocks, features, segments, new BitSet());
        for (byte block : blocks) assertEquals(BlockId.BEDROCK, block);
        Arrays.fill(blocks, BlockId.STONE);
        var gravel = new CaveFeatures.Feature(15, -47, 15, -48, -46, 2, CaveFeatures.Type.GRAVEL, BlockId.GRAVEL);
        fixture.features.apply(
                new ChunkPos(0, -3, 0), blocks, List.of(gravel), fixture.plan.segments(0, 0), new BitSet());
        assertEquals(BlockId.STONE, blocks[(1 * 16 + 15) * 16 + 15]);
    }

    private static byte at(Fixture fixture, HashMap<ChunkPos, Chunk> sections, int x, int y, int z) {
        var position = new BlockPos(x, y, z);
        var chunk = sections.computeIfAbsent(Coordinates.worldToChunk(position), p -> generate(fixture, p));
        var local = Coordinates.worldToLocal(position);
        return chunk.get(local.x(), local.y(), local.z());
    }

    private static Chunk generate(Fixture fixture, ChunkPos position) {
        byte[] blocks = new byte[4096];
        Arrays.fill(blocks, BlockId.STONE);
        var segments = fixture.plan.segments(position.x(), position.z());
        fixture.carver.carve(position, blocks, segments);
        var falling = new BitSet();
        fixture.features.apply(position, blocks, fixture.features.at(position.x(), position.z()), segments, falling);
        return new ChunkData(position, blocks).withFallingWater(falling).toChunk();
    }

    private static Fixture fixture(long seed) {
        GroundSampler ground = (x, z) -> new TerrainSummary(96, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, -64, 28);
        var segments = new ArrayList<CavePlan.Segment>();
        for (int z = 15; z < 128; z += 32)
            for (int x = 15; x < 128; x += 32) {
                segments.add(new CavePlan.Segment(x + .5, -46.5, z + .5, 5.5, 3, CaveProfile.CHAMBER));
                segments.add(new CavePlan.Segment(x + .5, -46.5, z + .5, 1.8, 1.85, CaveProfile.CLASSIC));
            }
        var plan = new CavePlan(segments);
        var carver = new CaveCarver(ground, seed);
        var features = new CaveFeatures(
                seed, ground, carver, (x, z) -> x == 0 && z == 0 ? plan : new CavePlan(List.of()), plan::segments);
        return new Fixture(plan, carver, features);
    }

    private record Fixture(CavePlan plan, CaveCarver carver, CaveFeatures features) {}
}

package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class TerrainBoundsTest {
    @Test
    void bedrockSealsEveryBottomColumnAndOutsideSectionsAreEmpty() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        for (int cx : new int[] {-2, 0, 3}) {
            var bottom = terrain.generate(new ChunkPos(cx, WorldBounds.MIN_CHUNK_Y, -1));
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    assertEquals(BlockId.BEDROCK, bottom.get(x, 0, z));
                    assertEquals(
                            WorldBounds.MIN_Y,
                            terrain.sample(cx * 16 + x, -16 + z).bedrockHeight());
                }
            for (int cy : new int[] {WorldBounds.MIN_CHUNK_Y - 1, WorldBounds.MAX_CHUNK_Y + 1}) {
                var outside = terrain.generate(new ChunkPos(cx, cy, -1));
                assertTrue(outside.isUniform());
                assertEquals(BlockId.AIR, outside.uniformBlock());
            }
        }
    }

    @Test
    void extremeTerrainProfilesKeepSurfaceQueriesAndVoxelsWithinBounds() {
        var disabled = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        for (int base : new int[] {-200, 400}) {
            var config = new TerrainConfig(base, disabled, disabled, disabled);
            var terrain = new TerrainGenerator(7, config, (x, z) -> 0);
            int expected = base < 0 ? WorldBounds.MIN_Y + 1 : WorldBounds.MAX_Y_EXCLUSIVE - 1;
            assertEquals(expected, terrain.surfaceHeight(0, 0));
            var sample = terrain.sample(0, 0);
            assertEquals(expected, sample.surfaceHeight());
            assertTrue(WorldBounds.containsY(sample.waterLevel()));
            var section = terrain.generate(new ChunkPos(0, Math.floorDiv(expected, 16), 0));
            assertEquals(sample.surfaceMaterial(), section.get(0, Math.floorMod(expected, 16), 0));
        }
    }
}

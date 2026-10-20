package com.norbertfila.vegetation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class SugarCaneGrowthRuleTest {
    private final SugarCaneGrowthRule rule = new SugarCaneGrowthRule();

    @Test
    void requiresWaterGroundAndAirAndStopsAtThree() {
        var world = new World();
        var chunk = new Chunk();
        chunk.set(2, 2, 2, BlockId.SAND);
        chunk.set(3, 2, 2, BlockId.WATER);
        chunk.set(2, 3, 2, BlockId.REED);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        var root = new BlockPos(2, 3, 2);
        assertEquals(new BlockPos(2, 4, 2), rule.inspect(world, root).target());
        assertEquals(GrowthSite.Status.READY, rule.inspect(world, root).status());
        chunk.set(2, 4, 2, BlockId.STONE);
        assertEquals(GrowthSite.Status.BLOCKED, rule.inspect(world, root).status());
        chunk.set(2, 4, 2, BlockId.REED);
        chunk.set(2, 5, 2, BlockId.REED);
        assertEquals(GrowthSite.Status.MATURE, rule.inspect(world, root).status());
        chunk.set(3, 2, 2, BlockId.AIR);
        assertEquals(GrowthSite.Status.INVALID, rule.inspect(world, root).status());
        chunk.set(3, 2, 2, BlockId.WATER);
        chunk.set(2, 2, 2, BlockId.STONE);
        assertEquals(GrowthSite.Status.INVALID, rule.inspect(world, root).status());
    }

    @Test
    void missingChunksAreNotAirEvenWhenOverlayContainsWater() {
        var world = new World();
        var chunk = new Chunk();
        chunk.set(15, 2, 2, BlockId.SAND);
        chunk.set(15, 3, 2, BlockId.REED);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        world.setBlock(new BlockPos(16, 2, 2), BlockId.WATER, 64);
        var site = rule.inspect(world, new BlockPos(15, 3, 2));
        assertEquals(GrowthSite.Status.UNAVAILABLE, site.status());
        assertTrue(site.dependencies().contains(new ChunkPos(1, 0, 0)));
    }

    @Test
    void delaysAreRepeatablePositiveAndCalibratedToAboutEighteenMinutes() {
        var root = new BlockPos(-17, 3, 200);
        long total = 0;
        for (int i = 0; i < 10000; i++) {
            long delay = rule.delay(42, root, 0, i);
            assertTrue(delay >= 48);
            assertEquals(delay, rule.delay(42, root, 0, i));
            total += delay;
        }
        assertEquals(65536, total / 10000.0, 1500);
        assertNotEquals(rule.delay(42, root, 0, 0), rule.delay(42, root, 100, 0));
    }

    @Test
    void rejectsMalformedOrDuplicateState() {
        var root = new BlockPos(2, 3, 2);
        var state = new PlantGrowthState(root, BlockId.REED, 0, 100, 0);
        assertThrows(IllegalArgumentException.class, () -> new PlantGrowthState(root, BlockId.REED, 10, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlantGrowthState(root, BlockId.REED, 0, 100, -1));
        assertThrows(IllegalArgumentException.class, () -> new PlantGrowthSnapshot(java.util.List.of(state, state)));
    }
}

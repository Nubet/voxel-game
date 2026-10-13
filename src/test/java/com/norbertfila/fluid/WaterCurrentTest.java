package com.norbertfila.fluid;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class WaterCurrentTest {
    @Test
    void gradientsPointAwayFromSupplyAndOppositeInfluencesCancel() {
        World world = known();
        BlockPos p = new BlockPos(-1, 2, -1);
        water(world, p, 3);
        water(world, new BlockPos(-2, 2, -1), 2);
        water(world, new BlockPos(0, 2, -1), 4);
        assertEquals(new WaterCurrent.Result(1, 0, 0, true), WaterCurrent.sample(world, p));
        water(world, new BlockPos(-2, 2, -1), 4);
        assertEquals(new WaterCurrent.Result(0, 0, 0, true), WaterCurrent.sample(world, p));
        world.setBlock(new BlockPos(0, 2, -1), BlockId.STONE, 64);
        assertEquals(-1, WaterCurrent.sample(world, p).x());
        world.setBlock(new BlockPos(-2, 2, -1), BlockId.AIR, 64);
        assertEquals(new WaterCurrent.Result(0, 0, 0, true), WaterCurrent.sample(world, p));
    }

    @Test
    void fallingIsBoundedAndSourcesHaveZeroDistance() {
        World world = known();
        BlockPos p = new BlockPos(2, 2, 2);
        water(world, p, WaterState.FALLING);
        assertEquals(new WaterCurrent.Result(0, -1, 0, true), WaterCurrent.sample(world, p));
        water(world, new BlockPos(3, 2, 2), 7);
        var current = WaterCurrent.sample(world, p);
        assertEquals(1, Math.hypot(current.x(), current.y()), 1e-12);
        assertTrue(current.y() < 0);
        water(world, p, WaterState.SOURCE);
        assertEquals(new WaterCurrent.Result(1, 0, 0, true), WaterCurrent.sample(world, p));
    }

    @Test
    void missingDataNeverReadsOverlayOrGeneratesAndIntegerLimitsDoNotWrap() {
        World world = new World();
        BlockPos p = new BlockPos(15, 2, 2);
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        water(world, p, 2);
        water(world, new BlockPos(16, 2, 2), 7);
        assertFalse(WaterCurrent.sample(world, p).available());
        assertEquals(0, WaterCurrent.sample(world, p).x());
        assertEquals(1, world.residentCount());
        assertFalse(world.isChunkRequested(new ChunkPos(1, 0, 0)));
        for (int x : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            BlockPos edge = new BlockPos(x, 2, 2);
            world.putChunk(Coordinates.worldToChunk(edge), new Chunk());
            water(world, edge, WaterState.FALLING);
            assertTrue(WaterCurrent.sample(world, edge).available());
            assertEquals(-1, WaterCurrent.sample(world, edge).y());
        }
    }

    private static void water(World world, BlockPos p, int state) {
        world.setBlock(p, BlockId.WATER, (byte) state, 64);
    }

    private static World known() {
        World world = new World();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.putChunk(new ChunkPos(x, 0, z), new Chunk());
        return world;
    }
}

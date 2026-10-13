package com.norbertfila.fluid;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class ExtendedWaterFlowTest {
    @Test
    void closestDropWinsAndEqualRoutesBothReceiveSupply() {
        World world = floor();
        set(world, 8, 1, 8, WaterState.SOURCE);
        world.setBlock(new BlockPos(10, 0, 8), BlockId.AIR, 64);
        assertEquals(1, next(world, 9, 8));
        assertEquals(WaterState.EMPTY, next(world, 7, 8));
        world.setBlock(new BlockPos(6, 0, 8), BlockId.AIR, 64);
        assertEquals(1, next(world, 7, 8));
        assertEquals(1, next(world, 9, 8));
        assertEquals(WaterState.EMPTY, next(world, 8, 7));
    }

    @Test
    void searchCannotSeeThroughWallsOrBeyondFourSteps() {
        World world = floor();
        set(world, 8, 1, 8, WaterState.SOURCE);
        world.setBlock(new BlockPos(13, 0, 8), BlockId.AIR, 64);
        assertEquals(1, next(world, 7, 8));
        world.setBlock(new BlockPos(11, 0, 8), BlockId.AIR, 64);
        world.setBlock(new BlockPos(10, 1, 8), BlockId.STONE, 64);
        world.setBlock(new BlockPos(10, 1, 7), BlockId.STONE, 64);
        world.setBlock(new BlockPos(10, 1, 9), BlockId.STONE, 64);
        assertEquals(1, next(world, 7, 8));
        world.setBlock(new BlockPos(10, 1, 8), BlockId.AIR, 64);
        assertEquals(WaterState.EMPTY, next(world, 7, 8));
        assertEquals(1, next(world, 9, 8));
    }

    @Test
    void supportedTwoSourcesConvertFlowAndUnsupportedFlowCannotConvert() {
        World world = floor();
        set(world, 7, 1, 8, WaterState.SOURCE);
        set(world, 9, 1, 8, WaterState.SOURCE);
        set(world, 8, 1, 8, WaterState.flowing(1));
        assertEquals(WaterState.SOURCE, next(world, 8, 8));
        world.setBlock(new BlockPos(8, 0, 8), BlockId.AIR, 64);
        assertNotEquals(WaterState.SOURCE, next(world, 8, 8));
        set(world, 8, 0, 8, WaterState.SOURCE);
        assertEquals(WaterState.SOURCE, next(world, 8, 8));
        set(world, 8, 0, 8, WaterState.flowing(1));
        assertNotEquals(WaterState.SOURCE, next(world, 8, 8));
    }

    @Test
    void simulatorCreatesAPersistentSourceAndStableKnownLakeStopsWorking() {
        World world = floor();
        for (int cx = -1; cx <= 1; cx++)
            for (int cz = -1; cz <= 1; cz++)
                for (int cy = -1; cy <= 1; cy++) {
                    ChunkPos chunk = new ChunkPos(cx, cy, cz);
                    if (!world.hasChunk(chunk)) world.putChunk(chunk, new Chunk());
                }
        for (int x = 4; x <= 12; x++)
            for (int z = 4; z <= 12; z++) {
                world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                if (x == 4 || x == 12 || z == 4 || z == 12) world.setBlock(new BlockPos(x, 1, z), BlockId.STONE, 64);
            }
        set(world, 7, 1, 8, WaterState.SOURCE);
        set(world, 9, 1, 8, WaterState.SOURCE);
        WaterSimulator simulator = new WaterSimulator(world, 64, 2000, true);
        simulator.onBlockChanged(new BlockPos(7, 1, 8), 0);
        simulator.onBlockChanged(new BlockPos(9, 1, 8), 0);
        for (int tick = 1; tick <= 500; tick++) simulator.tick(tick);
        assertEquals(
                WaterState.SOURCE, world.getWaterState(new BlockPos(8, 1, 8)).orElseThrow());
        assertEquals(0, simulator.pendingUpdates());
        assertEquals(0, simulator.tick(501).processedUpdates());
        world.setBlock(new BlockPos(7, 1, 8), BlockId.STONE, 64);
        world.setBlock(new BlockPos(9, 1, 8), BlockId.STONE, 64);
        simulator.onBlockChanged(new BlockPos(7, 1, 8), 501);
        simulator.onBlockChanged(new BlockPos(9, 1, 8), 501);
        for (int tick = 502; tick <= 700; tick++) simulator.tick(tick);
        assertEquals(
                WaterState.SOURCE, world.getWaterState(new BlockPos(8, 1, 8)).orElseThrow());
    }

    @Test
    void baselineKeepsDiamondAndDoesNotConvertSources() {
        World world = floor();
        set(world, 8, 1, 8, WaterState.SOURCE);
        world.setBlock(new BlockPos(10, 0, 8), BlockId.AIR, 64);
        assertEquals(1, WaterFlowRules.nextState(world, new BlockPos(7, 1, 8), new HashSet<>()));
        set(world, 6, 1, 8, WaterState.SOURCE);
        set(world, 7, 1, 8, WaterState.flowing(1));
        assertEquals(1, WaterFlowRules.nextState(world, new BlockPos(7, 1, 8), new HashSet<>()));
    }

    @Test
    void unknownRouteDefersRatherThanGuessingAndDoesNotRequestGeneration() {
        World world = floor();
        set(world, 13, 1, 8, WaterState.SOURCE);
        var missing = new HashSet<ChunkPos>();
        assertEquals(
                WaterFlowRules.UNAVAILABLE, WaterFlowRules.nextState(world, new BlockPos(12, 1, 8), missing, true));
        assertTrue(missing.contains(new ChunkPos(1, 0, 0)));
        assertEquals(1, world.residentCount());
        assertFalse(world.isChunkRequested(new ChunkPos(1, 0, 0)));
    }

    @Test
    void remoteSupportEditChangesRoutesAndOrphanFlowStillDrains() {
        World world = floor();
        for (int cx = -1; cx <= 1; cx++)
            for (int cz = -1; cz <= 1; cz++)
                for (int cy = -1; cy <= 1; cy++) {
                    ChunkPos position = new ChunkPos(cx, cy, cz);
                    if (!world.hasChunk(position)) world.putChunk(position, new Chunk());
                }
        for (int x = -16; x < 32; x++)
            for (int z = -16; z < 32; z++) {
                world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
                world.setBlock(new BlockPos(x, -1, z), BlockId.STONE, 64);
            }
        WaterSimulator simulator = new WaterSimulator(world, 64, 2000, true);
        BlockPos source = new BlockPos(8, 1, 8), hole = new BlockPos(11, 0, 8);
        world.setBlock(source, BlockId.WATER, 64);
        simulator.onBlockChanged(source, 0);
        for (int tick = 1; tick <= 300; tick++) simulator.tick(tick);
        assertEquals(1, world.getWaterState(new BlockPos(7, 1, 8)).orElseThrow());
        world.setBlock(hole, BlockId.AIR, 64);
        simulator.onBlockChanged(hole, 300);
        for (int tick = 301; tick <= 600; tick++) simulator.tick(tick);
        assertEquals(
                WaterState.EMPTY, world.getWaterState(new BlockPos(7, 1, 8)).orElseThrow());
        assertEquals(WaterState.FALLING, world.getWaterState(hole).orElseThrow());
        world.setBlock(source, BlockId.AIR, 64);
        simulator.onBlockChanged(source, 600);
        for (int tick = 601; tick <= 1200; tick++) simulator.tick(tick);
        for (var change : world.changes().changes()) assertNotEquals(BlockId.WATER, change.value());
    }

    private static int next(World world, int x, int z) {
        return WaterFlowRules.nextState(world, new BlockPos(x, 1, z), new HashSet<>(), true);
    }

    private static void set(World world, int x, int y, int z, byte state) {
        world.setBlock(new BlockPos(x, y, z), BlockId.WATER, state, 64);
    }

    private static World floor() {
        World world = new World();
        Chunk chunk = new Chunk();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) chunk.set(x, 0, z, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        return world;
    }
}

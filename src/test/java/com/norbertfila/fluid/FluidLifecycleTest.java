package com.norbertfila.fluid;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.persistence.TestWorlds;
import com.norbertfila.world.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FluidLifecycleTest {
    private static final ChunkPos LEFT = new ChunkPos(0, 0, 0);
    private static final ChunkPos RIGHT = new ChunkPos(1, 0, 0);
    private static final BlockPos EDGE = new BlockPos(15, 1, 8);

    @TempDir
    Path directory;

    @Test
    void unknownSupplySleepsWithoutPollingAndWakesOnArrival() {
        World world = new World();
        world.putChunk(LEFT, floor());
        world.setBlock(EDGE, BlockId.WATER, WaterState.flowing(3), 8);
        FluidSimulator simulator = new FluidSimulator(world, 8);
        simulator.onBlockChanged(EDGE, 0);
        for (int tick = 1; tick <= 500; tick++) simulator.tick(tick);
        assertTrue(simulator.deferredUpdates() > 0);
        assertEquals(3, world.getWaterState(EDGE).orElseThrow());
        assertEquals(0, simulator.tick(100_000).processedUpdates());
        assertEquals(1, world.residentCount());
        assertFalse(world.isChunkRequested(RIGHT));

        world.putChunk(RIGHT, floor());
        simulator.onChunkChanged(RIGHT, 100_000);
        for (int tick = 100_001; tick <= 101_000; tick++) simulator.tick(tick);
        assertEquals(WaterState.EMPTY, world.getWaterState(EDGE).orElseThrow());
    }

    @Test
    void unloadingFreezesTheRemainingDelayInsteadOfCatchingUp() {
        World world = new World();
        world.putChunk(LEFT, floor());
        BlockPos source = new BlockPos(8, 1, 8), target = new BlockPos(9, 1, 8);
        world.setBlock(source, BlockId.WATER, 8);
        FluidSimulator simulator = new FluidSimulator(world, 8);
        simulator.onBlockChanged(source, 0);
        simulator.tick(5);
        world.removeChunk(LEFT);
        simulator.onChunkChanged(LEFT, 5);
        assertEquals(simulator.pendingUpdates(), simulator.deferredUpdates());
        assertEquals(0, simulator.tick(1005).processedUpdates());
        world.putChunk(LEFT, floor());
        simulator.onChunkChanged(LEFT, 1005);
        assertEquals(0, simulator.tick(1014).changedBlocks());
        assertEquals(WaterState.EMPTY, world.getWaterState(target).orElseThrow());
        assertTrue(simulator.tick(1015).changedBlocks() > 0);
        assertEquals(1, world.getWaterState(target).orElseThrow());
    }

    @Test
    void bothSidesOfAnArrivingBoundaryResumeFlow() {
        for (boolean fromLeft : new boolean[] {true, false}) {
            World world = new World();
            ChunkPos initial = fromLeft ? LEFT : RIGHT, arriving = fromLeft ? RIGHT : LEFT;
            world.putChunk(initial, floor());
            BlockPos source = new BlockPos(fromLeft ? 15 : 16, 1, 8);
            BlockPos target = new BlockPos(fromLeft ? 16 : 15, 1, 8);
            world.setBlock(source, BlockId.WATER, 8);
            FluidSimulator simulator = new FluidSimulator(world, 8);
            simulator.onChunkChanged(initial, 0);
            for (int tick = 1; tick <= 100; tick++) simulator.tick(tick);
            world.putChunk(arriving, floor());
            simulator.onChunkChanged(arriving, 100);
            simulator.tick(115);
            assertEquals(1, world.getWaterState(target).orElseThrow());
        }
    }

    @Test
    void cornerUpdateWaitsForAllKnownMissingDependencies() {
        World world = new World();
        world.putChunk(LEFT, floor());
        BlockPos corner = new BlockPos(15, 1, 15);
        ChunkPos front = new ChunkPos(0, 0, 1);
        world.setBlock(corner, BlockId.WATER, WaterState.flowing(2), 8);
        FluidSimulator simulator = new FluidSimulator(world, 8);
        simulator.onBlockChanged(corner, 0);
        for (int tick = 1; tick <= 500; tick++) simulator.tick(tick);
        var parked = simulator.snapshot(500).updates().stream()
                .filter(u -> u.position().equals(corner))
                .findFirst()
                .orElseThrow();
        assertTrue(parked.missingChunks().contains(RIGHT));
        assertTrue(parked.missingChunks().contains(front));
        world.putChunk(RIGHT, floor());
        simulator.onChunkChanged(RIGHT, 500);
        for (int tick = 501; tick <= 1000; tick++) simulator.tick(tick);
        assertEquals(2, world.getWaterState(corner).orElseThrow());
        assertTrue(simulator.snapshot(1000).updates().stream()
                .anyMatch(u -> u.position().equals(corner) && !u.missingChunks().isEmpty()));
        world.putChunk(front, floor());
        simulator.onChunkChanged(front, 1000);
        simulator.tick(1015);
        assertNotEquals(2, world.getWaterState(corner).orElseThrow());
    }

    @Test
    void localSupplyChangesCanReplaceAnUnresolvedBoundaryDependency() {
        World world = new World();
        world.putChunk(LEFT, floor());
        world.setBlock(EDGE, BlockId.WATER, WaterState.flowing(3), 8);
        FluidSimulator simulator = new FluidSimulator(world, 8);
        simulator.onBlockChanged(EDGE, 0);
        for (int tick = 1; tick <= 500; tick++) simulator.tick(tick);
        assertTrue(simulator.deferredUpdates() > 0);
        BlockPos source = new BlockPos(15, 3, 8);
        world.setBlock(source, BlockId.WATER, 8);
        simulator.onBlockChanged(source, 500);
        simulator.tick(515);
        simulator.tick(530);
        assertEquals(WaterState.FALLING, world.getWaterState(EDGE).orElseThrow());
        assertFalse(world.hasChunk(RIGHT));
    }

    @Test
    void restartPreservesActiveOrderDeferredWorkAndSubsequentTickResults() throws Exception {
        World first = new World();
        first.putChunk(LEFT, floor());
        first.setBlock(EDGE, BlockId.WATER, WaterState.flowing(3), 8);
        FluidSimulator original = new FluidSimulator(first, 8, 3);
        original.onBlockChanged(EDGE, 0);
        for (int tick = 1; tick <= 30; tick++) original.tick(tick);
        BlockPos source = new BlockPos(5, 1, 8);
        first.setBlock(source, BlockId.WATER, 8);
        original.onBlockChanged(source, 30);
        var captured = original.snapshot(30);
        assertTrue(captured.updates().stream().anyMatch(u -> u.missingChunks().isEmpty()));
        assertTrue(captured.updates().stream().anyMatch(u -> !u.missingChunks().isEmpty()));
        Path file = directory.resolve("flow.vxl");
        TestWorlds.save(file, TestWorlds.capture(42, 1, first, captured));
        var loaded = TestWorlds.load(file);
        assertEquals(captured, loaded.fluids());
        World second = new World();
        TestWorlds.restore(second, loaded, 8);
        second.putChunk(LEFT, floor());
        FluidSimulator resumed = new FluidSimulator(second, 8, 3);
        resumed.restore(loaded.fluids());
        for (int tick = 31; tick <= 500; tick++) {
            if (tick == 200) {
                first.putChunk(RIGHT, floor());
                second.putChunk(RIGHT, floor());
                original.onChunkChanged(RIGHT, tick);
                resumed.onChunkChanged(RIGHT, tick);
            }
            var a = original.tick(tick);
            var b = resumed.tick(tick);
            assertEquals(a, b, "tick " + tick);
            assertEquals(first.changes().changes(), second.changes().changes());
        }
    }

    @Test
    void snapshotIsImmutableAndRestoreIntoUnloadedWorldWaitsForData() {
        World world = new World();
        world.putChunk(LEFT, floor());
        world.setBlock(new BlockPos(8, 1, 8), BlockId.WATER, 8);
        FluidSimulator original = new FluidSimulator(world, 8);
        original.onBlockChanged(new BlockPos(8, 1, 8), 0);
        var snapshot = original.snapshot(5);
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.updates().clear());
        World empty = new World();
        FluidSimulator resumed = new FluidSimulator(empty, 8);
        resumed.restore(snapshot);
        assertEquals(snapshot.updates().size(), resumed.deferredUpdates());
        assertEquals(0, resumed.tick(1000).processedUpdates());
        assertEquals(0, empty.residentCount());
        assertThrows(IllegalStateException.class, () -> resumed.restore(snapshot));
    }

    private static Chunk floor() {
        Chunk chunk = new Chunk();
        for (int x = 0; x < Chunk.SIZE; x++) for (int z = 0; z < Chunk.SIZE; z++) chunk.set(x, 0, z, BlockId.STONE);
        return chunk;
    }
}

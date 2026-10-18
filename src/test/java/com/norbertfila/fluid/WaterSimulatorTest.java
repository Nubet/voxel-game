package com.norbertfila.fluid;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WaterSimulatorTest {
    @ParameterizedTest
    @CsvSource({"0,0,0", "15,0,15", "-17,-17,-17"})
    void oneSourceFormsASevenBlockManhattanDiamondAndThenStops(int x, int floorY, int z) {
        Scene scene = new Scene(x, floorY, z, floorY + 2, 2_000);
        scene.set(x, floorY + 1, z, BlockId.WATER);
        scene.settle();
        int waterBlocks = 0;
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -9; dz <= 9; dz++) {
                int distance = Math.abs(dx) + Math.abs(dz);
                int expected = distance == 0 ? WaterState.SOURCE : distance <= 7 ? distance : WaterState.EMPTY;
                assertEquals(expected, scene.state(x + dx, floorY + 1, z + dz), "Offset " + dx + "," + dz);
                if (expected != WaterState.EMPTY) waterBlocks++;
            }
        assertEquals(113, waterBlocks);
        var changes = scene.world.changes().changes();
        for (int tick = 0; tick < 100; tick++) {
            var result = scene.step();
            assertEquals(0, result.processedUpdates());
            assertEquals(0, result.changedBlocks());
            assertTrue(result.dirtyChunks().isEmpty());
        }
        assertEquals(changes, scene.world.changes().changes());
    }

    @Test
    void eachHorizontalWaveWaitsFifteenTicks() {
        Scene scene = new Scene();
        scene.set(0, 1, 0, BlockId.WATER);
        for (int tick = 1; tick < 15; tick++) assertEquals(0, scene.step().changedBlocks());
        assertEquals(WaterState.EMPTY, scene.state(1, 1, 0));
        assertEquals(4, scene.step().changedBlocks());
        assertEquals(1, scene.state(1, 1, 0));
        assertEquals(WaterState.EMPTY, scene.state(1, 1, 1));
        while (scene.time < 29) scene.step();
        assertEquals(WaterState.EMPTY, scene.state(2, 1, 0));
        scene.step();
        assertEquals(2, scene.state(2, 1, 0));
        assertEquals(2, scene.state(1, 1, 1));
    }

    @Test
    void updatesOutsideTheSimulationAreaWaitUntilTheAreaCoversThem() {
        Scene scene = new Scene();
        scene.simulator.setSimulationArea(new ChunkPos(0, 0, 0), 0, 0);
        scene.set(0, 1, 0, BlockId.WATER);
        for (int tick = 0; tick < 300; tick++) scene.step();

        assertEquals(7, scene.state(7, 1, 0));
        assertEquals(WaterState.EMPTY, scene.state(-1, 1, 0));
        assertTrue(scene.simulator.dormantUpdates() > 0);
        assertEquals(scene.simulator.dormantUpdates(), scene.simulator.pendingUpdates());
        assertEquals(
                scene.simulator.pendingUpdates(),
                scene.simulator.snapshot(scene.time).updates().size());

        scene.simulator.setSimulationArea(new ChunkPos(0, 0, 0), 1, scene.time);
        scene.settle();
        assertEquals(1, scene.state(-1, 1, 0));
        assertEquals(7, scene.state(-7, 1, 0));
        assertEquals(7, scene.state(0, 1, -7));
    }

    @Test
    void chunkLoadedOutsideTheSimulationAreaIsScannedWhenTheAreaReachesIt() {
        Scene scene = new Scene();
        scene.simulator.setSimulationArea(new ChunkPos(10, 0, 0), 2, 0);
        scene.edits.setBlock(new BlockPos(0, 1, 0), BlockId.WATER, WaterState.SOURCE);
        scene.simulator.onChunkChanged(new ChunkPos(0, 0, 0), 0);
        assertEquals(0, scene.simulator.pendingUpdates());

        scene.simulator.setSimulationArea(new ChunkPos(0, 0, 0), 2, 0);
        scene.settle();
        assertEquals(7, scene.state(7, 1, 0));
        assertEquals(7, scene.state(-7, 1, 0));
    }

    @Test
    void waterfallExceedsHorizontalRangeAndSpreadsOnlyAtItsFoot() {
        Scene scene = new Scene(0, 0, 0, 40, 2_000);
        scene.set(0, 40, 0, BlockId.WATER);
        scene.settle();
        assertEquals(WaterState.SOURCE, scene.state(0, 40, 0));
        for (int y = 1; y < 40; y++) assertEquals(WaterState.FALLING, scene.state(0, y, 0));
        for (int y = 2; y <= 40; y++) {
            assertEquals(WaterState.EMPTY, scene.state(1, y, 0));
            assertEquals(WaterState.EMPTY, scene.state(0, y, 1));
        }
        assertEquals(1, scene.state(1, 1, 0));
        assertEquals(7, scene.state(7, 1, 0));
        assertEquals(WaterState.EMPTY, scene.state(8, 1, 0));
    }

    @Test
    void removingSourceDrainsBothWaterfallAndItsPool() {
        Scene scene = new Scene(0, 0, 0, 20, 2_000);
        scene.set(0, 20, 0, BlockId.WATER);
        scene.settle();
        scene.set(0, 20, 0, BlockId.AIR);
        scene.settle();
        for (int y = 1; y <= 20; y++) assertEquals(WaterState.EMPTY, scene.state(0, y, 0));
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) assertEquals(WaterState.EMPTY, scene.state(x, 1, z));
        assertEquals(0, scene.simulator.pendingUpdates());
    }

    @Test
    void fallingSupplyNeverReplacesAnExistingSource() {
        Scene scene = new Scene(0, 0, 0, 8, 2_000);
        scene.set(0, 1, 0, BlockId.WATER);
        scene.set(0, 8, 0, BlockId.WATER);
        scene.settle();
        assertEquals(WaterState.SOURCE, scene.state(0, 1, 0));
        assertEquals(WaterState.FALLING, scene.state(0, 2, 0));
        scene.set(0, 8, 0, BlockId.AIR);
        scene.settle();
        assertEquals(WaterState.SOURCE, scene.state(0, 1, 0));
        assertEquals(WaterState.EMPTY, scene.state(0, 2, 0));
        assertEquals(1, scene.state(1, 1, 0));
    }

    @Test
    void replacingSourceWithSolidDrainsFlowWithoutOverwritingTheReplacement() {
        Scene scene = new Scene();
        scene.set(0, 1, 0, BlockId.WATER);
        scene.settle();
        scene.set(0, 1, 0, BlockId.STONE);
        scene.settle();
        assertEquals(BlockId.STONE, scene.world.getBlock(new BlockPos(0, 1, 0)).orElseThrow());
        assertEquals(WaterState.EMPTY, scene.state(1, 1, 0));
        assertEquals(WaterState.EMPTY, scene.state(7, 1, 0));
    }

    @Test
    void removingFlatSourceEventuallyDrainsAllFlow() {
        Scene scene = new Scene();
        scene.set(0, 1, 0, BlockId.WATER);
        scene.settle();
        scene.set(0, 1, 0, BlockId.AIR);
        scene.settle();
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) assertEquals(WaterState.EMPTY, scene.state(x, 1, z));
    }

    @Test
    void remainingSourceRecalculatesDistancesAfterTheOtherIsRemoved() {
        Scene scene = new Scene();
        scene.set(-3, 1, 0, BlockId.WATER);
        scene.set(3, 1, 0, BlockId.WATER);
        scene.settle();
        assertEquals(3, scene.state(0, 1, 0));
        scene.set(-3, 1, 0, BlockId.AIR);
        scene.settle();
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                int distance = Math.abs(x - 3) + Math.abs(z);
                int expected = distance == 0 ? WaterState.SOURCE : distance <= 7 ? distance : WaterState.EMPTY;
                assertEquals(expected, scene.state(x, 1, z), "Position " + x + "," + z);
            }
    }

    @Test
    void unsupportedHorizontalCycleCannotBecomeItsOwnSupply() {
        Scene scene = new Scene();
        scene.set(0, 1, 0, BlockId.WATER, WaterState.flowing(1));
        scene.set(1, 1, 0, BlockId.WATER, WaterState.flowing(1));
        scene.set(1, 1, 1, BlockId.WATER, WaterState.flowing(1));
        scene.set(0, 1, 1, BlockId.WATER, WaterState.flowing(1));
        scene.settle();
        for (int x = -8; x <= 9; x++)
            for (int z = -8; z <= 9; z++) assertEquals(WaterState.EMPTY, scene.state(x, 1, z));
    }

    @Test
    void adjacentSourcesDoNotCreateAnImplicitNewSource() {
        Scene scene = new Scene();
        scene.set(-1, 1, 0, BlockId.WATER);
        scene.set(1, 1, 0, BlockId.WATER);
        scene.settle();
        assertEquals(1, scene.state(0, 1, 0));
        scene.set(-1, 1, 0, BlockId.AIR);
        scene.set(1, 1, 0, BlockId.AIR);
        scene.settle();
        assertEquals(WaterState.EMPTY, scene.state(0, 1, 0));
    }

    @Test
    void blockedCardinalNeighborsPreventDirectDiagonalFlow() {
        Scene scene = new Scene();
        scene.raw(1, 1, 0, BlockId.STONE);
        scene.raw(-1, 1, 0, BlockId.STONE);
        scene.raw(0, 1, 1, BlockId.STONE);
        scene.raw(0, 1, -1, BlockId.STONE);
        scene.set(0, 1, 0, BlockId.WATER);
        scene.settle();
        assertEquals(WaterState.EMPTY, scene.state(1, 1, 1));
        assertEquals(WaterState.EMPTY, scene.state(-1, 1, -1));
        assertEquals(WaterState.SOURCE, scene.state(0, 1, 0));
    }

    @Test
    void openingAndClosingAWallStartsAndStopsTheStreamBehindIt() {
        Scene scene = new Scene();
        for (int z = -10; z <= 10; z++) scene.raw(2, 1, z, BlockId.STONE);
        scene.set(0, 1, 0, BlockId.WATER);
        scene.settle();
        assertEquals(WaterState.EMPTY, scene.state(3, 1, 0));
        scene.set(2, 1, 0, BlockId.AIR);
        scene.settle();
        assertEquals(3, scene.state(3, 1, 0));
        scene.set(2, 1, 0, BlockId.STONE);
        scene.settle();
        assertEquals(WaterState.EMPTY, scene.state(3, 1, 0));
        assertEquals(BlockId.STONE, scene.world.getBlock(new BlockPos(2, 1, 0)).orElseThrow());
    }

    @Test
    void removingSupportChangesLateralFeedEvenThoughTheSourceStateIsUnchanged() {
        Scene scene = new Scene();
        scene.raw(0, 1, 0, BlockId.STONE);
        scene.set(0, 2, 0, BlockId.WATER);
        scene.settle();
        assertEquals(1, scene.state(1, 2, 0));
        scene.set(0, 1, 0, BlockId.AIR);
        scene.settle();
        assertEquals(WaterState.SOURCE, scene.state(0, 2, 0));
        assertEquals(WaterState.FALLING, scene.state(0, 1, 0));
        assertEquals(WaterState.EMPTY, scene.state(1, 2, 0));
        scene.set(0, 1, 0, BlockId.STONE);
        scene.settle();
        assertEquals(1, scene.state(1, 2, 0));
    }

    @Test
    void openingAHoleStopsAnUnchangedFlowDonorFromFeedingDownstream() {
        Scene scene = new Scene();
        scene.floor(-4);
        for (int x = -10; x <= 10; x++) {
            scene.raw(x, 1, -1, BlockId.STONE);
            scene.raw(x, 1, 1, BlockId.STONE);
        }
        scene.set(0, 1, 0, BlockId.WATER);
        scene.settle();
        assertEquals(2, scene.state(2, 1, 0));
        scene.set(1, 0, 0, BlockId.AIR);
        scene.settle();
        assertEquals(1, scene.state(1, 1, 0));
        assertEquals(WaterState.FALLING, scene.state(1, 0, 0));
        assertEquals(WaterState.FALLING, scene.state(1, -3, 0));
        assertEquals(WaterState.EMPTY, scene.state(2, 1, 0));
    }

    @Test
    void schedulingDryPlantsDoesNotRemoveThemWithoutActualIncomingWater() {
        Scene scene = new Scene();
        scene.raw(1, 0, 0, BlockId.GRASS);
        scene.set(1, 1, 0, BlockId.DANDELION_WHITE);
        scene.settle();
        assertEquals(
                BlockId.DANDELION_WHITE,
                scene.world.getBlock(new BlockPos(1, 1, 0)).orElseThrow());
        scene.set(0, 1, 0, BlockId.WATER);
        scene.settle();
        assertEquals(BlockId.WATER, scene.world.getBlock(new BlockPos(1, 1, 0)).orElseThrow());
    }

    @Test
    void nonReplaceableBlocksStopFlowAndPlantsAreWashedAway() {
        for (int id = 1; id <= 255; id++) {
            byte block = (byte) id;
            if (!BlockId.isValid(block) || BlockId.isFluid(block)) continue;
            Scene scene = new Scene();
            scene.raw(1, 1, 0, block);
            scene.set(0, 1, 0, BlockId.WATER);
            scene.settle();
            if (com.norbertfila.world.PlantRules.isPlant(block)) {
                assertEquals(
                        BlockId.WATER,
                        scene.world.getBlock(new BlockPos(1, 1, 0)).orElseThrow());
                assertEquals(1, scene.state(1, 1, 0));
            } else {
                assertEquals(id, scene.world.getBlock(new BlockPos(1, 1, 0)).orElseThrow());
                assertEquals(WaterState.EMPTY, scene.state(1, 1, 0));
            }
        }
    }

    @Test
    void tickCoalescesChangesAndNeighborInvalidationsIntoOneSortedChunkList() {
        Scene scene = new Scene(15, 0, 15, 2, 2_000);
        scene.set(15, 1, 15, BlockId.WATER);
        while (scene.time < 14) scene.step();
        var result = scene.step();
        assertEquals(4, result.changedBlocks());
        assertEquals(
                List.of(new ChunkPos(0, 0, 0), new ChunkPos(0, 0, 1), new ChunkPos(1, 0, 0), new ChunkPos(1, 0, 1)),
                result.dirtyChunks());
        assertEquals(result.dirtyChunks().size(), new HashSet<>(result.dirtyChunks()).size());
        assertThrows(
                UnsupportedOperationException.class, () -> result.dirtyChunks().clear());
        var captured = List.copyOf(result.dirtyChunks());
        scene.settle();
        assertEquals(captured, result.dirtyChunks());
    }

    @Test
    void identicalInputsProduceIdenticalPerTickResultsAndDeltas() {
        Scene first = new Scene(), second = new Scene();
        first.set(0, 1, 0, BlockId.WATER);
        second.set(0, 1, 0, BlockId.WATER);
        for (int tick = 0; tick < 500; tick++) {
            if (tick == 200) {
                first.set(0, 1, 0, BlockId.AIR);
                second.set(0, 1, 0, BlockId.AIR);
            }
            assertEquals(first.step(), second.step());
            assertEquals(first.world.changes().changes(), second.world.changes().changes());
        }
        assertEquals(0, first.simulator.pendingUpdates());
    }

    @Test
    void smallWorkBudgetPreservesTheFinalResultAndNoTaskIsLost() {
        Scene baseline = new Scene();
        Scene limited = new Scene(0, 0, 0, 2, 3);
        baseline.set(0, 1, 0, BlockId.WATER);
        limited.set(0, 1, 0, BlockId.WATER);
        baseline.settle();
        while (limited.simulator.pendingUpdates() > 0) {
            assertTrue(limited.time < 10_000);
            assertTrue(limited.step().processedUpdates() <= 3);
            assertEquals(0, limited.simulator.tick(limited.time).processedUpdates());
        }
        assertEquals(baseline.world.changes().changes(), limited.world.changes().changes());
    }

    @Test
    void missingNeighborDoesNotEraseFlowOrTrustAnUnloadedOverlay() {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        WorldEdit edit = new WorldEdit(world, 64);
        BlockPos edge = new BlockPos(15, 8, 8);
        edit.setBlock(edge, BlockId.WATER, WaterState.flowing(3));
        world.setBlock(new BlockPos(16, 8, 8), BlockId.AIR, 64);
        FluidSimulator simulator = new FluidSimulator(world, 64);
        simulator.onBlockChanged(edge, 0);
        var before = world.changes().changes();
        for (int tick = 1; tick <= 60; tick++) simulator.tick(tick);
        assertEquals(3, world.getWaterState(edge).orElseThrow());
        assertEquals(1, world.residentCount());
        assertFalse(world.isChunkRequested(new ChunkPos(1, 0, 0)));
        assertTrue(simulator.pendingUpdates() > 0);
        assertTrue(world.changes().changes().containsAll(before));
    }

    @Test
    void coordinateLimitsBlockFlowWithoutWrappingToTheOtherEndOfTheWorld() {
        World world = new World();
        BlockPos source = new BlockPos(Integer.MAX_VALUE, WorldBounds.MIN_Y, Integer.MAX_VALUE);
        world.putChunk(Coordinates.worldToChunk(source), new Chunk());
        new WorldEdit(world, 64).setBlock(source, BlockId.WATER);
        FluidSimulator simulator = new FluidSimulator(world, 64);
        simulator.onBlockChanged(source, 0);
        for (int tick = 1; simulator.pendingUpdates() > 0 && tick < 1_000; tick++) simulator.tick(tick);
        assertEquals(0, simulator.pendingUpdates());
        assertEquals(
                7,
                world.getWaterState(new BlockPos(Integer.MAX_VALUE - 7, WorldBounds.MIN_Y, Integer.MAX_VALUE))
                        .orElseThrow());
        assertEquals(1, world.residentCount());
        assertTrue(world.getBlock(new BlockPos(Integer.MIN_VALUE, WorldBounds.MIN_Y, Integer.MAX_VALUE))
                .isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MAX_VALUE})
    void invalidTickCannotPartiallyEditTheWorldOrEnqueueUpdates(long tick) {
        Scene scene = new Scene();
        assertThrows(RuntimeException.class, () -> scene.simulator.onBlockChanged(new BlockPos(0, 1, 0), tick));
        assertThrows(RuntimeException.class, () -> scene.simulator.tick(tick));
        assertEquals(0, scene.simulator.pendingUpdates());
        assertEquals(0, scene.world.changes().size());
    }

    private static final class Scene {
        final World world = new World();
        final WorldEdit edits = new WorldEdit(world, 64);
        final FluidSimulator simulator;
        final int centerX, centerZ;
        long time;

        Scene() {
            this(0, 0, 0, 2, 2_000);
        }

        Scene(int centerX, int floorY, int centerZ, int topY, int budget) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            for (int x = Math.floorDiv(centerX, 16) - 1; x <= Math.floorDiv(centerX, 16) + 1; x++)
                for (int z = Math.floorDiv(centerZ, 16) - 1; z <= Math.floorDiv(centerZ, 16) + 1; z++)
                    for (int y = Math.floorDiv(floorY, 16) - 1; y <= Math.floorDiv(topY, 16) + 1; y++)
                        world.putChunk(new ChunkPos(x, y, z), new Chunk());
            floor(floorY);
            simulator = new FluidSimulator(world, 64, budget);
        }

        void floor(int y) {
            for (int x = centerX - 10; x <= centerX + 10; x++)
                for (int z = centerZ - 10; z <= centerZ + 10; z++) raw(x, y, z, BlockId.STONE);
        }

        void raw(int x, int y, int z, byte block) {
            BlockPos position = new BlockPos(x, y, z);
            BlockPos local = Coordinates.worldToLocal(position);
            world.getChunk(Coordinates.worldToChunk(position)).set(local.x(), local.y(), local.z(), block);
        }

        void set(int x, int y, int z, byte block) {
            set(x, y, z, block, WaterState.defaultFor(block));
        }

        void set(int x, int y, int z, byte block, byte state) {
            BlockPos position = new BlockPos(x, y, z);
            if (edits.setBlock(position, block, state).changed()) simulator.onBlockChanged(position, time);
        }

        int state(int x, int y, int z) {
            return world.getWaterState(new BlockPos(x, y, z)).orElseThrow();
        }

        FluidSimulator.TickResult step() {
            return simulator.tick(++time);
        }

        void settle() {
            long deadline = time + 10_000;
            while (simulator.pendingUpdates() > 0 && time < deadline) step();
            assertEquals(0, simulator.pendingUpdates(), "Water did not settle at tick " + time);
        }
    }
}

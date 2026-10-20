package com.norbertfila.vegetation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlantGrowthSystemTest {
    @Test
    void unloadedPlantsCatchUpToTheSameStateWithoutAnyBackgroundUpdates() {
        var continuous = farm(2, 3);
        var distant = farm(2, 3);
        var initial = distant.growth.snapshot();
        distant.unload();
        for (int i = 1; i <= 100; i++) {
            long now = i * 10000L;
            continuous.growth.update(now, true);
            assertEquals(0, distant.growth.update(now, true).processed());
            assertEquals(0, distant.growth.activeUpdates());
        }
        assertEquals(initial, distant.growth.snapshot());
        distant.load();
        var result = distant.growth.update(1_000_000, true);
        assertEquals(2, result.changedBlocks());
        assertEquals(continuous.growth.snapshot(), distant.growth.snapshot());
        assertEquals(3, distant.height());
        assertEquals(0, distant.growth.activeUpdates());
        distant.unload();
        distant.load();
        assertEquals(0, distant.growth.update(2_000_000, true).changedBlocks());
    }

    @Test
    void harvestingAndReplantingStartFreshCyclesEvenWithinOneTick() {
        var farm = farm(2, 3);
        farm.growth.update(1_000_000, true);
        farm.growth.advanceClock(2_000_000);
        farm.edits.setBlock(new BlockPos(2, 4, 2), BlockId.AIR);
        assertEquals(0, farm.growth.update(2_000_000, true).changedBlocks());
        assertEquals(1, farm.height());
        assertEquals(2_000_000, farm.growth.snapshot().plants().getFirst().startedAt());
        farm.growth.advanceClock(3_000_000);
        farm.edits.setBlock(farm.root, BlockId.AIR);
        farm.edits.setBlock(farm.root, BlockId.REED);
        farm.growth.update(3_000_000, false);
        var replanted = farm.growth.snapshot().plants().getFirst();
        assertEquals(3_000_000, replanted.startedAt());
        assertEquals(0, replanted.cycle());
        assertEquals(0, farm.growth.update(replanted.nextGrowthTick() - 1, true).changedBlocks());
        assertEquals(1, farm.growth.update(replanted.nextGrowthTick(), true).changedBlocks());
    }

    @Test
    void blockedPlantsDoNotBankGrowthAndPauseDoesNotExecuteOverdueUpdates() {
        var farm = farm(2, 3);
        var above = new BlockPos(2, 4, 2);
        farm.edits.setBlock(above, BlockId.STONE);
        farm.growth.update(1_000_000, true);
        assertEquals(0, farm.growth.activeUpdates());
        farm.edits.setBlock(above, BlockId.AIR);
        assertEquals(0, farm.growth.update(1_000_000, true).changedBlocks());
        var deadline = farm.growth.snapshot().plants().getFirst().nextGrowthTick();
        assertEquals(0, farm.growth.update(deadline, false).changedBlocks());
        assertEquals(1, farm.height());
        assertEquals(1, farm.growth.update(deadline, true).changedBlocks());
    }

    @Test
    void waitsForHorizontalAndVerticalNeighborsAndDoesNotDuplicateRoots() {
        var farm = farm(15, 15);
        var neighbor = new ChunkPos(1, 0, 0);
        var water = new Chunk();
        water.set(0, 14, 2, BlockId.WATER);
        farm.world.putChunk(neighbor, water);
        farm.growth.onChunkChanged(neighbor);
        farm.growth.update(0, false);
        assertEquals(0, farm.growth.update(1_000_000, true).changedBlocks());
        var upper = new ChunkPos(0, 1, 0);
        farm.world.putChunk(upper, new Chunk());
        farm.growth.onChunkChanged(upper);
        assertEquals(2, farm.growth.update(1_000_000, true).changedBlocks());
        assertEquals(1, farm.growth.snapshot().plants().size());
        assertTrue(farm.growth.update(1_000_000, true).dirtyChunks().isEmpty());
        assertEquals(3, farm.height());
    }

    @Test
    void restoreKeepsDeadlinesAndObservesNeighborEditsWhileRootIsUnloaded() {
        var farm = farm(15, 3);
        var waterPos = new ChunkPos(1, 0, 0);
        var water = new Chunk();
        water.set(0, 2, 2, BlockId.WATER);
        farm.world.putChunk(waterPos, water);
        farm.growth.onChunkChanged(waterPos);
        farm.growth.update(0, false);
        var saved = farm.growth.snapshot();
        farm.unload();
        var restored = new PlantGrowthSystem(farm.world, 64, 42);
        restored.restore(saved, 1_000_000);
        farm.world.setBlockChangeListener(restored::onBlockChanged);
        farm.edits.setBlock(new BlockPos(16, 2, 2), BlockId.AIR);
        farm.edits.setBlock(new BlockPos(16, 2, 2), BlockId.WATER);
        farm.world.putChunk(farm.chunkPos, farm.chunk);
        restored.onChunkChanged(farm.chunkPos);
        assertEquals(0, restored.update(1_000_000, true).changedBlocks());
        assertEquals(1_000_000, restored.snapshot().plants().getFirst().startedAt());
    }

    @Test
    void updateBudgetPreservesOverdueWorkAndSnapshotsKeepPendingPlantingTime() {
        var farm = farm(2, 3);
        var saved = farm.growth.snapshot();
        var growth = new PlantGrowthSystem(farm.world, 64, 42, new SugarCaneGrowthRule(), 1);
        growth.restore(saved, 0);
        farm.world.setBlockChangeListener(growth::onBlockChanged);
        assertEquals(1, growth.update(1_000_000, true).changedBlocks());
        assertEquals(1, growth.activeUpdates());
        assertEquals(1, growth.update(1_000_000, true).changedBlocks());
        assertEquals(0, growth.activeUpdates());
        growth.advanceClock(2_000_000);
        farm.edits.setBlock(farm.root, BlockId.AIR);
        farm.edits.setBlock(farm.root, BlockId.REED);
        growth.advanceClock(3_000_000);
        assertEquals(2_000_000, growth.snapshot().plants().getFirst().startedAt());
        assertThrows(IllegalArgumentException.class, () -> growth.update(0, true));
    }

    @Test
    void rootDiscoveryWorksWhenTheUpperSectionArrivesFirstAtNegativeCoordinates() {
        var world = new World();
        var growth = new PlantGrowthSystem(world, 64, 42);
        var root = new BlockPos(-1, 0, -1);
        var top = new Chunk();
        top.set(15, 0, 15, BlockId.REED);
        var upper = new ChunkPos(-1, 0, -1);
        world.putChunk(upper, top);
        growth.onChunkChanged(upper);
        growth.update(0, false);
        assertTrue(growth.snapshot().plants().isEmpty());
        var lower = new Chunk();
        lower.set(15, 15, 15, BlockId.SAND);
        lower.set(14, 15, 15, BlockId.WATER);
        var below = new ChunkPos(-1, -1, -1);
        world.putChunk(below, lower);
        growth.onChunkChanged(below);
        for (var pos : List.of(new ChunkPos(0, -1, -1), new ChunkPos(-1, -1, 0))) {
            world.putChunk(pos, new Chunk());
            growth.onChunkChanged(pos);
        }
        growth.update(0, false);
        assertEquals(root, growth.snapshot().plants().getFirst().root());
        assertEquals(2, growth.update(1_000_000, true).changedBlocks());
    }

    private static Farm farm(int x, int y) {
        var world = new World();
        var root = new BlockPos(x, y, 2);
        var chunkPos = Coordinates.worldToChunk(root);
        var chunk = new Chunk();
        chunk.set(x, y - 1, 2, BlockId.SAND);
        if (x < 15) chunk.set(x + 1, y - 1, 2, BlockId.WATER);
        chunk.set(x, y, 2, BlockId.REED);
        world.putChunk(chunkPos, chunk);
        var growth = new PlantGrowthSystem(world, 64, 42);
        world.setBlockChangeListener(growth::onBlockChanged);
        growth.onChunkChanged(chunkPos);
        growth.update(0, false);
        return new Farm(world, growth, new WorldEdit(world, 64), root, chunkPos, chunk);
    }

    private record Farm(
            World world, PlantGrowthSystem growth, WorldEdit edits, BlockPos root, ChunkPos chunkPos, Chunk chunk) {
        void unload() {
            world.removeChunk(chunkPos);
            growth.onChunkChanged(chunkPos);
        }

        void load() {
            world.putChunk(chunkPos, chunk);
            growth.onChunkChanged(chunkPos);
        }

        int height() {
            int height = 0;
            while (world.getBlock(new BlockPos(root.x(), root.y() + height, root.z()))
                            .orElse(-1)
                    == BlockId.REED) height++;
            return height;
        }
    }
}

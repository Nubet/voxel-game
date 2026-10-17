package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JunglePlantTest {
    @Test
    void attachedPlantsWaitForSupportAndVineChainsFallWhenRemoved() {
        var world = new World();
        var plants = new Chunk();
        plants.set(15, 8, 2, BlockId.VINE_EAST);
        plants.set(15, 7, 2, BlockId.VINE_EAST);
        world.putChunk(new ChunkPos(0, 0, 0), plants);
        var edit = new WorldEdit(world, 64);
        edit.validatePlants(new ChunkPos(0, 0, 0));
        assertEquals(
                Byte.toUnsignedInt(BlockId.VINE_EAST),
                world.getBlock(new BlockPos(15, 8, 2)).orElseThrow());
        var support = new Chunk();
        support.set(0, 8, 2, BlockId.JUNGLE_LOG);
        world.putChunk(new ChunkPos(1, 0, 0), support);
        edit.validatePlants(new ChunkPos(1, 0, 0));
        edit.setBlock(new BlockPos(16, 8, 2), BlockId.AIR);
        assertEquals(0, world.getBlock(new BlockPos(15, 8, 2)).orElseThrow());
        assertEquals(0, world.getBlock(new BlockPos(15, 7, 2)).orElseThrow());
    }

    @Test
    void doublePlantsArePlacedAndRemovedAcrossSections() {
        var world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        world.putChunk(new ChunkPos(0, 1, 0), new Chunk());
        var edit = new WorldEdit(world, 64);
        edit.setBlock(new BlockPos(2, 14, 2), BlockId.GRASS);
        edit.setBlock(new BlockPos(2, 15, 2), BlockId.LARGE_FERN);
        assertEquals(
                Byte.toUnsignedInt(BlockId.LARGE_FERN_TOP),
                world.getBlock(new BlockPos(2, 16, 2)).orElseThrow());
        edit.setBlock(new BlockPos(2, 16, 2), BlockId.AIR);
        assertEquals(0, world.getBlock(new BlockPos(2, 15, 2)).orElseThrow());
    }
}

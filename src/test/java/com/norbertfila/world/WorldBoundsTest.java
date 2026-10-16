package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldBoundsTest {
    @ParameterizedTest
    @ValueSource(ints = {-64, 255})
    void boundaryBlocksCanBeEdited(int y) {
        var world = new World();
        var position = new BlockPos(2, y, 2);
        world.putChunk(Coordinates.worldToChunk(position), new Chunk());
        assertTrue(new WorldEdit(world, 8).setBlock(position, BlockId.STONE).changed());
        assertEquals(BlockId.STONE, world.getBlock(position).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(ints = {-65, 256, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejectedEditsDoNotMutateLoadedChunksOrOverrides(int y) {
        var world = new World();
        var position = new BlockPos(2, y, 2);
        var chunk = new Chunk();
        world.putChunk(Coordinates.worldToChunk(position), chunk);
        long revision = chunk.meshDependencyRevision();
        var result = new WorldEdit(world, 8).setBlock(position, BlockId.WATER);
        assertEquals(BlockChangeResult.Status.OUT_OF_BOUNDS, result.status());
        assertFalse(result.changed());
        assertTrue(result.dirtyChunks().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> world.setBlock(position, BlockId.STONE, 8));
        assertEquals(BlockId.AIR, world.getBlock(position).orElseThrow());
        assertEquals(revision, chunk.meshDependencyRevision());
        assertEquals(0, world.changes().size());
    }
}

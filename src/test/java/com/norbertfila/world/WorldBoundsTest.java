package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldBoundsTest {
    @ParameterizedTest
    @ValueSource(ints = {-64, 255})
    void placementBeyondBoundaryDoesNotPublishAChange(int y) {
        var world = new World();
        var support = new BlockPos(8, y, 8);
        int direction = y < 0 ? 1 : -1;
        int outsideY = y - direction;
        world.putChunk(Coordinates.worldToChunk(support), new Chunk());
        world.putChunk(Coordinates.worldToChunk(new BlockPos(8, outsideY, 8)), new Chunk());
        world.setBlock(support, BlockId.STONE, 8);
        var notifications = new java.util.ArrayList<BlockPos>();
        var interaction = new com.norbertfila.interaction.BlockInteraction(world, 8, notifications::add);
        var result = interaction
                .place(
                        8.5,
                        y + .5 - direction * 2,
                        8.5,
                        0,
                        direction,
                        0,
                        BlockId.DIRT,
                        com.norbertfila.player.PlayerController.Mode.SURVIVAL,
                        new Aabb(0, 0, 0, 1, 2, 1))
                .orElseThrow();
        assertEquals(BlockChangeResult.Status.OUT_OF_BOUNDS, result.status());
        assertTrue(notifications.isEmpty());
        assertEquals(1, world.changes().size());
    }

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

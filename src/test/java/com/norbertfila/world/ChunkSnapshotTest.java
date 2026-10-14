package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkSnapshotTest {
    @Test
    void snapshotDoesNotChangeWhenSourceChunkChanges() {
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 3, BlockId.STONE);
        ChunkSnapshot snapshot = chunk.snapshot();
        long initialRevision = snapshot.revision();

        chunk.set(1, 2, 3, BlockId.AIR);

        assertEquals(BlockId.STONE, snapshot.get(1, 2, 3));
        assertEquals(initialRevision + 1, chunk.snapshot().revision());
    }

    @Test
    void snapshotsOfUnchangedContentAreShared() {
        World world = new World();
        Chunk chunk = Chunk.fromBlocks(new byte[Chunk.BLOCK_BYTES]);
        chunk.set(4, 5, 6, BlockId.WATER, WaterState.flowing(2));
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        ChunkSnapshot first = chunk.snapshot();
        assertSame(first, chunk.snapshot());

        world.putChunk(new ChunkPos(1, 0, 0), new Chunk());
        ChunkSnapshot afterNeighbor = chunk.snapshot();
        assertEquals(first.revision(), afterNeighbor.revision());
        assertTrue(afterNeighbor.meshDependencyRevision() > first.meshDependencyRevision());
        assertEquals(WaterState.flowing(2), afterNeighbor.waterState(4, 5, 6));

        chunk.set(4, 5, 6, BlockId.WATER, WaterState.flowing(3));
        assertEquals(WaterState.flowing(2), first.waterState(4, 5, 6));
        assertEquals(WaterState.flowing(3), chunk.snapshot().waterState(4, 5, 6));
    }

    @Test
    void snapshotCopiesExternallyOwnedInput() {
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        blocks[0] = BlockId.STONE;
        ChunkSnapshot snapshot = new ChunkSnapshot(blocks, 7, 11);

        blocks[0] = BlockId.AIR;

        assertEquals(BlockId.STONE, snapshot.get(0, 0, 0));
        assertEquals(7, snapshot.revision());
        assertEquals(11, snapshot.meshDependencyRevision());
    }

    @Test
    void worldEditsLeavePublishedSnapshotsAndOtherChunksUnchanged() {
        World world = new World();
        Chunk chunk = new Chunk();
        Chunk neighbor = new Chunk();
        world.putChunk(new ChunkPos(-1, 0, -1), chunk);
        world.putChunk(new ChunkPos(0, 0, -1), neighbor);
        WorldEdit edits = new WorldEdit(world, 8);
        BlockPos position = new BlockPos(-1, 2, -1);
        ChunkSnapshot empty = chunk.snapshot();

        edits.setBlock(position, BlockId.STONE);
        ChunkSnapshot placed = chunk.snapshot();
        edits.setBlock(position, BlockId.STONE);
        assertEquals(1, chunk.revision());
        edits.setBlock(position, BlockId.AIR);

        assertEquals(BlockId.AIR, empty.get(15, 2, 15));
        assertEquals(0, empty.revision());
        assertEquals(BlockId.STONE, placed.get(15, 2, 15));
        assertEquals(1, placed.revision());
        assertEquals(BlockId.AIR, chunk.get(15, 2, 15));
        assertEquals(2, chunk.revision());
        assertEquals(0, neighbor.revision());
        assertEquals(BlockId.AIR, neighbor.get(0, 2, 15));
        assertEquals(BlockId.AIR, world.changes().get(position).orElseThrow());
    }
}

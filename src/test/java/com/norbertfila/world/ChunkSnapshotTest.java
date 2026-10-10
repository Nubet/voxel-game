package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

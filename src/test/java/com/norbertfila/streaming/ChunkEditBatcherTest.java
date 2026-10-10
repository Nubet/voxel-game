package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.ChunkPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkEditBatcherTest {
    @Test
    void coalescesTenThousandEditsIntoOneLatestRevision() {
        ChunkEditBatcher batcher = new ChunkEditBatcher();
        ChunkPos position = new ChunkPos(2, 0, -3);

        for (int revision = 0; revision < 10_000; revision++) batcher.record(position, revision);

        assertEquals(1, batcher.size());
        assertEquals(List.of(new ChunkEditBatcher.Edit(position, 9_999)), batcher.drain());
        assertEquals(0, batcher.size());
    }

    @Test
    void drainsChunksInDeterministicOrder() {
        ChunkEditBatcher batcher = new ChunkEditBatcher();
        batcher.record(new ChunkPos(1, 0, 0), 1);
        batcher.record(new ChunkPos(-1, 0, 0), 2);

        assertEquals(
                List.of(
                        new ChunkEditBatcher.Edit(new ChunkPos(-1, 0, 0), 2),
                        new ChunkEditBatcher.Edit(new ChunkPos(1, 0, 0), 1)),
                batcher.drain());
    }
}

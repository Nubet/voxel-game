package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LayerStorageTest {
    @Test
    void storesUniformAndDenseLayersIndependently() {
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        Arrays.fill(blocks, 0, LayerStorage.LAYER_BLOCKS, BlockId.STONE);
        blocks[LayerStorage.LAYER_BLOCKS] = BlockId.DIRT;

        LayerStorage storage = LayerStorage.fromBlocks(blocks);

        assertEquals(1 + LayerStorage.LAYER_BLOCKS, storage.estimatedBytes());
        assertEquals(BlockId.STONE, storage.get(15, 0, 0));
        assertEquals(BlockId.DIRT, storage.get(0, 1, 0));
        assertEquals(BlockId.AIR, storage.get(1, 1, 0));
    }

    @Test
    void emptyLayersUseNoBlockBytes() {
        LayerStorage storage = new LayerStorage();

        assertEquals(0, storage.estimatedBytes());
        assertEquals(ChunkData.Kind.EMPTY, storage.kind());
    }

    @Test
    void materializedLayersReuseDenseStorageForSubsequentEdits() {
        for (ChunkData compact : new ChunkData[] {
            ChunkData.empty(LayerStorage.LAYER_BLOCKS), new ChunkData.Uniform(BlockId.STONE, LayerStorage.LAYER_BLOCKS)
        }) {
            ChunkData dense = compact.set(0, BlockId.DIRT);
            assertEquals(ChunkData.Kind.DENSE, dense.kind());
            for (int index = 1; index < LayerStorage.LAYER_BLOCKS; index++) {
                assertSame(dense, dense.set(index, BlockId.GRASS));
                assertEquals(BlockId.GRASS, dense.get(index));
            }
            assertEquals(BlockId.DIRT, dense.get(0));
            assertEquals(LayerStorage.LAYER_BLOCKS, dense.estimatedBytes());
            assertEquals(compact.get(1), compact.get(0));
        }
    }
}

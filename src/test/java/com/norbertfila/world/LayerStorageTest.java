package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

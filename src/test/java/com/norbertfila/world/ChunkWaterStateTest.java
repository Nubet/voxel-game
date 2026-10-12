package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChunkWaterStateTest {
    @Test
    void generatedAndPlacedWaterDefaultToSourcesWithoutMetadataAllocation() {
        Chunk generated = sources();
        assertEquals(Chunk.SIZE, generated.estimatedBytes());
        assertEquals(ChunkData.Kind.UNIFORM, generated.storageKind());
        assertEquals(0, generated.revision());
        assertEquals(WaterState.SOURCE, generated.waterState(15, 15, 15));
        assertEquals(WaterState.SOURCE, generated.snapshot().waterState(0, 0, 0));

        Chunk placed = new Chunk();
        assertEquals(WaterState.EMPTY, placed.waterState(1, 2, 3));
        placed.set(1, 2, 3, BlockId.WATER);
        assertEquals(WaterState.SOURCE, placed.waterState(1, 2, 3));
        assertEquals(LayerStorage.LAYER_BLOCKS, placed.estimatedBytes());
    }

    @Test
    void stateOnlyEditsAdvanceBothRevisionsExactlyOnce() {
        Chunk chunk = sources();
        chunk.set(2, 3, 4, BlockId.WATER, WaterState.flowing(2));
        assertEquals(1, chunk.revision());
        assertEquals(1, chunk.meshDependencyRevision());
        chunk.set(2, 3, 4, BlockId.WATER, WaterState.flowing(2));
        assertEquals(1, chunk.revision());
        assertEquals(1, chunk.meshDependencyRevision());
        chunk.set(2, 3, 4, BlockId.WATER, WaterState.FALLING);
        assertEquals(2, chunk.revision());
        assertEquals(2, chunk.meshDependencyRevision());
        assertEquals(WaterState.FALLING, chunk.waterState(2, 3, 4));
        assertEquals(WaterState.SOURCE, chunk.waterState(2, 3, 5));
    }

    @Test
    void allocatesOneMetadataArrayAndReleasesItAfterTheLastFlowIsRemoved() {
        Chunk chunk = sources();
        ChunkStorage storage = new ChunkStorage();
        storage.put(new ChunkPos(0, 0, 0), chunk);
        long baseline = storage.estimatedBytes();
        chunk.set(0, 0, 0, BlockId.WATER, WaterState.flowing(1));
        assertEquals(baseline + Chunk.BLOCK_BYTES, storage.estimatedBytes());
        chunk.set(15, 15, 15, BlockId.WATER, WaterState.FALLING);
        assertEquals(baseline + Chunk.BLOCK_BYTES, storage.estimatedBytes());
        chunk.set(0, 0, 0, BlockId.WATER);
        assertEquals(baseline + Chunk.BLOCK_BYTES, storage.estimatedBytes());
        chunk.set(15, 15, 15, BlockId.WATER);
        assertEquals(baseline, storage.estimatedBytes());
        chunk.set(7, 8, 9, BlockId.WATER, WaterState.flowing(7));
        assertEquals(baseline + Chunk.BLOCK_BYTES, storage.estimatedBytes());
    }

    @Test
    void changingBlockClearsItsStateAndDoesNotRestoreOldFlowOnReplacement() {
        Chunk chunk = sources();
        chunk.set(3, 4, 5, BlockId.WATER, WaterState.FALLING);
        chunk.set(3, 4, 5, BlockId.STONE);
        assertEquals(WaterState.EMPTY, chunk.waterState(3, 4, 5));
        assertEquals(LayerStorage.LAYER_BLOCKS + Chunk.SIZE - 1, chunk.estimatedBytes());
        chunk.set(3, 4, 5, BlockId.WATER);
        assertEquals(WaterState.SOURCE, chunk.waterState(3, 4, 5));
        assertEquals(3, chunk.revision());
    }

    @Test
    void allLocalCellsRetainIndependentStatesIncludingAxisBoundaries() {
        Chunk chunk = sources();
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++)
                    chunk.set(x, y, z, BlockId.WATER, WaterState.flowing(1 + (x + 3 * y + 5 * z) % 7));
        ChunkSnapshot snapshot = chunk.snapshot();
        assertEquals(Chunk.BLOCK_BYTES, chunk.revision());
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    byte expected = WaterState.flowing(1 + (x + 3 * y + 5 * z) % 7);
                    assertEquals(expected, chunk.waterState(x, y, z));
                    assertEquals(expected, snapshot.waterState(x, y, z));
                    chunk.set(x, y, z, BlockId.WATER);
                    assertEquals(expected, snapshot.waterState(x, y, z));
                }
        assertEquals(Chunk.SIZE, chunk.estimatedBytes());
        assertEquals(2 * Chunk.BLOCK_BYTES, chunk.revision());
    }

    @Test
    void invalidEditsLeaveDataStateRevisionsAndMemoryUnchanged() {
        Chunk chunk = sources();
        assertThrows(IllegalArgumentException.class, () -> chunk.set(0, 0, 0, BlockId.WATER, WaterState.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> chunk.set(0, 0, 0, BlockId.STONE, WaterState.SOURCE));
        assertThrows(IllegalArgumentException.class, () -> chunk.set(0, 0, 0, BlockId.WATER, (byte) 9));
        assertThrows(IndexOutOfBoundsException.class, () -> chunk.set(-1, 0, 0, BlockId.WATER, WaterState.FALLING));
        assertThrows(IndexOutOfBoundsException.class, () -> chunk.waterState(0, 16, 0));
        assertEquals(BlockId.WATER, chunk.get(0, 0, 0));
        assertEquals(WaterState.SOURCE, chunk.waterState(0, 0, 0));
        assertEquals(0, chunk.revision());
        assertEquals(0, chunk.meshDependencyRevision());
        assertEquals(Chunk.SIZE, chunk.estimatedBytes());
    }

    @Test
    void snapshotCopiesBothArraysAndRejectsMismatchedMetadata() {
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        byte[] water = new byte[Chunk.BLOCK_BYTES];
        blocks[0] = BlockId.WATER;
        water[0] = WaterState.FALLING;
        ChunkSnapshot snapshot = new ChunkSnapshot(blocks, water, 7, 11);
        blocks[0] = BlockId.AIR;
        water[0] = 0;
        assertEquals(BlockId.WATER, snapshot.get(0, 0, 0));
        assertEquals(WaterState.FALLING, snapshot.waterState(0, 0, 0));
        assertEquals(WaterState.EMPTY, snapshot.waterState(1, 0, 0));
        assertEquals(7, snapshot.revision());
        assertEquals(11, snapshot.meshDependencyRevision());
        assertThrows(IndexOutOfBoundsException.class, () -> snapshot.waterState(0, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new ChunkSnapshot(blocks, new byte[1], 0, 0));
        water[0] = WaterState.FALLING;
        assertThrows(IllegalArgumentException.class, () -> new ChunkSnapshot(blocks, water, 0, 0));
        blocks[0] = BlockId.WATER;
        water[0] = (byte) 9;
        assertThrows(IllegalArgumentException.class, () -> new ChunkSnapshot(blocks, water, 0, 0));
    }

    private static Chunk sources() {
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        Arrays.fill(blocks, BlockId.WATER);
        return Chunk.fromBlocks(blocks);
    }
}

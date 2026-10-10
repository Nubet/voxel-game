package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CoordinatesTest {
    @Test
    void mapsNegativeBlockToNegativeChunkAndPositiveLocal() {
        BlockPos world = new BlockPos(-1, -1, -1);

        assertEquals(new ChunkPos(-1, -1, -1), Coordinates.worldToChunk(world));
        assertEquals(new BlockPos(15, 15, 15), Coordinates.worldToLocal(world));
    }

    @Test
    void mapsChunkBoundariesWithFloorSemantics() {
        assertMapping(-17, new ChunkPos(-2, -2, -2), new BlockPos(15, 15, 15));
        assertMapping(-16, new ChunkPos(-1, -1, -1), new BlockPos(0, 0, 0));
        assertMapping(-1, new ChunkPos(-1, -1, -1), new BlockPos(15, 15, 15));
        assertMapping(0, new ChunkPos(0, 0, 0), new BlockPos(0, 0, 0));
        assertMapping(1, new ChunkPos(0, 0, 0), new BlockPos(1, 1, 1));
        assertMapping(16, new ChunkPos(1, 1, 1), new BlockPos(0, 0, 0));
        assertMapping(17, new ChunkPos(1, 1, 1), new BlockPos(1, 1, 1));
    }

    @Test
    void chunkAndLocalCoordinatesRoundTrip() {
        ChunkPos chunk = new ChunkPos(-7, 3, 11);
        BlockPos local = new BlockPos(15, 0, 4);
        BlockPos world = Coordinates.chunkLocalToWorld(chunk, local);

        assertEquals(chunk, Coordinates.worldToChunk(world));
        assertEquals(local, Coordinates.worldToLocal(world));
    }

    @Test
    void supportsIntegerBoundariesWithoutFloatingPoint() {
        BlockPos world = new BlockPos(Integer.MIN_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE);

        assertEquals(
                Math.floorDiv(Integer.MIN_VALUE, Chunk.SIZE),
                Coordinates.worldToChunk(world).x());
        assertEquals(
                Math.floorMod(Integer.MAX_VALUE, Chunk.SIZE),
                Coordinates.worldToLocal(world).y());
    }

    @Test
    void rejectsNonLocalCoordinatesWhenCombiningChunkAndLocal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Coordinates.chunkLocalToWorld(new ChunkPos(0, 0, 0), new BlockPos(16, 0, 0)));
    }

    private static void assertMapping(int coordinate, ChunkPos expectedChunk, BlockPos expectedLocal) {
        BlockPos world = new BlockPos(coordinate, coordinate, coordinate);

        assertEquals(expectedChunk, Coordinates.worldToChunk(world));
        assertEquals(expectedLocal, Coordinates.worldToLocal(world));
    }
}

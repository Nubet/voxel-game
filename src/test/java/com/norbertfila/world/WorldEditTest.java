package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorldEditTest {
    @Test
    void editsResidentBlockAndAdvancesItsRevision() {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        WorldEdit edit = new WorldEdit(world, 8);

        WorldEdit.Result result = edit.setBlock(new BlockPos(3, 4, 5), BlockId.STONE);

        assertTrue(result.changed());
        assertEquals(BlockId.AIR, result.previous().orElseThrow());
        assertEquals(1, result.chunkRevision());
        assertEquals(List.of(new ChunkPos(0, 0, 0)), result.dirtyChunks());
        assertEquals(BlockId.STONE, world.getBlock(new BlockPos(3, 4, 5)).orElseThrow());
    }

    @Test
    void marksEveryNeighborWhenEditingAChunkCorner() {
        World world = new World();
        WorldEdit edit = new WorldEdit(world, 8);

        WorldEdit.Result result = edit.setBlock(new BlockPos(15, 15, 15), BlockId.DIRT);

        assertEquals(
                List.of(new ChunkPos(0, 0, 0), new ChunkPos(0, 0, 1), new ChunkPos(0, 1, 0), new ChunkPos(1, 0, 0)),
                result.dirtyChunks());
    }

    @Test
    void preservesOverlayForAnUnloadedChunkAndUsesNegativeCoordinates() {
        World world = new World();
        WorldEdit edit = new WorldEdit(world, 8);
        BlockPos position = new BlockPos(-1, 0, -1);

        WorldEdit.Result result = edit.setBlock(position, BlockId.GRASS);

        assertEquals(-1, result.chunkRevision());
        assertEquals(BlockId.GRASS, world.getBlock(position).orElseThrow());
        assertEquals(1, world.changes().size());
        assertEquals(
                List.of(
                        new ChunkPos(-1, -1, -1),
                        new ChunkPos(-1, 0, -1),
                        new ChunkPos(-1, 0, 0),
                        new ChunkPos(0, 0, -1)),
                result.dirtyChunks());
    }

    @Test
    void repeatedValueDoesNotCreateARevisionOrRemeshRequest() {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        WorldEdit edit = new WorldEdit(world, 8);
        edit.setBlock(new BlockPos(1, 1, 1), BlockId.STONE);

        WorldEdit.Result result = edit.setBlock(new BlockPos(1, 1, 1), BlockId.STONE);

        assertFalse(result.changed());
        assertTrue(result.dirtyChunks().isEmpty());
        assertEquals(1, result.chunkRevision());
    }
}

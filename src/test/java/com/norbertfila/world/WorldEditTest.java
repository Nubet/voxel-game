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

        BlockChangeResult result = edit.setBlock(new BlockPos(3, 4, 5), BlockId.STONE);

        assertTrue(result.changed());
        assertEquals(BlockChangeResult.Status.CHANGED, result.status());
        assertEquals(BlockId.AIR, result.previous().orElseThrow());
        assertEquals(1, result.chunkRevision());
        assertEquals(List.of(new ChunkPos(0, 0, 0)), result.dirtyChunks());
        assertEquals(BlockId.STONE, world.getBlock(new BlockPos(3, 4, 5)).orElseThrow());
        assertEquals(BlockId.STONE, world.changes().get(new BlockPos(3, 4, 5)).orElseThrow());
    }

    @Test
    void marksEveryNeighborWhenEditingAChunkCorner() {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        WorldEdit edit = new WorldEdit(world, 8);

        BlockChangeResult result = edit.setBlock(new BlockPos(15, 15, 15), BlockId.DIRT);

        assertEquals(
                List.of(new ChunkPos(0, 0, 0), new ChunkPos(0, 0, 1), new ChunkPos(0, 1, 0), new ChunkPos(1, 0, 0)),
                result.dirtyChunks());
    }

    @Test
    void unavailableChunkDoesNotCreateAnOverrideOrRemeshRequest() {
        World world = new World();
        WorldEdit edit = new WorldEdit(world, 8);
        BlockPos position = new BlockPos(-1, 0, -1);

        BlockChangeResult result = edit.setBlock(position, BlockId.GRASS);

        assertEquals(BlockChangeResult.Status.UNAVAILABLE, result.status());
        assertFalse(result.changed());
        assertTrue(result.previous().isEmpty());
        assertEquals(-1, result.chunkRevision());
        assertTrue(world.getBlock(position).isEmpty());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().dirtyTiles().isEmpty());
        assertTrue(result.dirtyChunks().isEmpty());
        assertEquals(0, world.residentCount());
    }

    @Test
    void editsResidentChunkAtNegativeCoordinates() {
        World world = new World();
        world.putChunk(new ChunkPos(-1, 0, -1), new Chunk());
        BlockPos position = new BlockPos(-1, 0, -1);

        BlockChangeResult result = new WorldEdit(world, 8).setBlock(position, BlockId.GRASS);

        assertEquals(BlockChangeResult.Status.CHANGED, result.status());
        assertEquals(1, result.chunkRevision());
        assertEquals(BlockId.GRASS, world.getChunk(new ChunkPos(-1, 0, -1)).get(15, 0, 15));
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

        world.changes().drainDirtyTiles();
        BlockChangeResult result = edit.setBlock(new BlockPos(1, 1, 1), BlockId.STONE);

        assertEquals(BlockChangeResult.Status.UNCHANGED, result.status());
        assertFalse(result.changed());
        assertTrue(result.dirtyChunks().isEmpty());
        assertEquals(1, result.chunkRevision());
        assertEquals(1, world.changes().size());
        assertTrue(world.changes().dirtyTiles().isEmpty());
    }

    @Test
    void unchangedProceduralBlockDoesNotCreateAnOverride() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 1, 1, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);

        BlockChangeResult result = new WorldEdit(world, 8).setBlock(new BlockPos(1, 1, 1), BlockId.STONE);

        assertEquals(BlockChangeResult.Status.UNCHANGED, result.status());
        assertEquals(BlockId.STONE, result.previous().orElseThrow());
        assertEquals(1, chunk.revision());
        assertTrue(result.dirtyChunks().isEmpty());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().dirtyTiles().isEmpty());
    }
}

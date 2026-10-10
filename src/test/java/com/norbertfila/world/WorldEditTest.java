package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorldEditTest {
    @ParameterizedTest
    @CsvSource({
        "7,7,7,1",
        "0,7,7,2",
        "15,7,7,2",
        "7,0,7,2",
        "7,15,7,2",
        "7,7,0,2",
        "7,7,15,2",
        "0,0,7,3",
        "0,15,7,3",
        "15,0,7,3",
        "15,15,7,3",
        "0,7,0,3",
        "0,7,15,3",
        "15,7,0,3",
        "15,7,15,3",
        "7,0,0,3",
        "7,0,15,3",
        "7,15,0,3",
        "7,15,15,3",
        "0,0,0,4",
        "0,0,15,4",
        "0,15,0,4",
        "0,15,15,4",
        "15,0,0,4",
        "15,0,15,4",
        "15,15,0,4",
        "15,15,15,4"
    })
    void invalidatesOnlyFaceNeighborsWithoutChangingTheirData(int x, int y, int z, int dirtyCount) {
        World world = new World();
        ChunkPos center = new ChunkPos(-2, -1, -3);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    world.putChunk(new ChunkPos(center.x() + dx, center.y() + dy, center.z() + dz), new Chunk());
        BlockPos block = Coordinates.chunkLocalToWorld(center, new BlockPos(x, y, z));
        WorldEdit edits = new WorldEdit(world, 8);

        BlockChangeResult result = edits.setBlock(block, BlockId.STONE);

        assertEquals(dirtyCount, result.dirtyChunks().size());
        assertEquals(dirtyCount, new HashSet<>(result.dirtyChunks()).size());
        assertTrue(result.dirtyChunks().contains(center));
        for (ChunkPos dirty : result.dirtyChunks()) {
            int dx = dirty.x() - center.x();
            int dy = dirty.y() - center.y();
            int dz = dirty.z() - center.z();
            assertTrue(Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 1);
            if (dx != 0) assertEquals(dx < 0 ? 0 : Chunk.SIZE - 1, x);
            if (dy != 0) assertEquals(dy < 0 ? 0 : Chunk.SIZE - 1, y);
            if (dz != 0) assertEquals(dz < 0 ? 0 : Chunk.SIZE - 1, z);
        }
        for (ChunkPos position : world.residentPositions()) {
            Chunk chunk = world.getChunk(position);
            assertEquals(position.equals(center) ? 1 : 0, chunk.revision());
            assertEquals(result.dirtyChunks().contains(position) ? 1 : 0, chunk.meshDependencyRevision());
        }
        assertEquals(1, world.changes().size());
        assertEquals(block, world.changes().changes().get(0).position());

        assertEquals(
                BlockChangeResult.Status.UNCHANGED,
                edits.setBlock(block, BlockId.STONE).status());
        for (ChunkPos position : result.dirtyChunks())
            assertEquals(1, world.getChunk(position).meshDependencyRevision());
    }

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
        assertEquals(1, world.residentCount());
        assertEquals(1, world.getChunk(new ChunkPos(0, 0, 0)).meshDependencyRevision());
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
        assertEquals(1, chunk.meshDependencyRevision());
        assertTrue(result.dirtyChunks().isEmpty());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().dirtyTiles().isEmpty());
    }

    @Test
    void laterDataEditKeepsDependencyRevisionIndependentFromDataRevision() {
        World world = new World();
        Chunk first = new Chunk();
        Chunk neighbor = new Chunk();
        world.putChunk(new ChunkPos(0, 0, 0), first);
        world.putChunk(new ChunkPos(1, 0, 0), neighbor);
        WorldEdit edits = new WorldEdit(world, 8);

        edits.setBlock(new BlockPos(15, 3, 3), BlockId.STONE);

        assertEquals(0, neighbor.revision());
        assertEquals(1, neighbor.meshDependencyRevision());
        assertEquals(1, world.changes().size());

        edits.setBlock(new BlockPos(17, 3, 3), BlockId.DIRT);

        assertEquals(1, neighbor.revision());
        assertEquals(2, neighbor.meshDependencyRevision());
        assertEquals(1, first.revision());
        assertEquals(1, first.meshDependencyRevision());
        assertEquals(2, world.changes().size());
    }
}

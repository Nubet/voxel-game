package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorldWaterStateTest {
    @ParameterizedTest
    @CsvSource({"-2147483648,2147483647,-2147483648", "2147483647,-2147483648,2147483647"})
    void preservesWaterStateAtFullIntegerCoordinateLimits(int x, int y, int z) {
        World world = new World();
        BlockPos position = new BlockPos(x, y, z);
        ChunkPos chunk = Coordinates.worldToChunk(position);
        BlockPos local = Coordinates.worldToLocal(position);
        world.putChunk(chunk, new Chunk());
        assertTrue(new WorldEdit(world, 64)
                .setBlock(position, BlockId.WATER, WaterState.FALLING)
                .changed());
        world.removeChunk(chunk);
        world.putChunk(chunk, new Chunk());
        assertEquals(WaterState.FALLING, world.getWaterState(position).orElseThrow());
        assertEquals(WaterState.FALLING, world.getChunk(chunk).waterState(local.x(), local.y(), local.z()));
    }

    @ParameterizedTest
    @CsvSource({
        "-16,-9,-9,-2,-1,-1",
        "-1,-9,-9,0,-1,-1",
        "-9,-16,-9,-1,-2,-1",
        "-9,-1,-9,-1,0,-1",
        "-9,-9,-16,-1,-1,-2",
        "-9,-9,-1,-1,-1,0"
    })
    void stateOnlyEditsInvalidateFaceNeighborsAndPreservePublishedSnapshots(
            int x, int y, int z, int cx, int cy, int cz) {
        World world = new World();
        ChunkPos center = new ChunkPos(-1, -1, -1);
        ChunkPos neighbor = new ChunkPos(cx, cy, cz);
        world.putChunk(center, new Chunk());
        world.putChunk(neighbor, new Chunk());
        BlockPos position = new BlockPos(x, y, z);
        BlockPos local = Coordinates.worldToLocal(position);
        WorldEdit edit = new WorldEdit(world, 64);
        edit.setBlock(position, BlockId.WATER);
        ChunkSnapshot before = world.getChunk(center).snapshot();
        long neighborRevision = world.getChunk(neighbor).meshDependencyRevision();
        world.changes().drainDirtyTiles();

        BlockChangeResult changed = edit.setBlock(position, BlockId.WATER, WaterState.flowing(5));

        assertTrue(changed.changed());
        assertEquals(BlockId.WATER, changed.previous().orElseThrow());
        assertEquals(2, changed.chunkRevision());
        assertEquals(2, changed.dirtyChunks().size());
        assertTrue(changed.dirtyChunks().containsAll(List.of(center, neighbor)));
        assertEquals(neighborRevision + 1, world.getChunk(neighbor).meshDependencyRevision());
        assertEquals(0, world.getChunk(neighbor).revision());
        assertEquals(before.meshDependencyRevision() + 1, world.getChunk(center).meshDependencyRevision());
        assertEquals(WaterState.SOURCE, before.waterState(local.x(), local.y(), local.z()));
        assertEquals(WaterState.flowing(5), world.getWaterState(position).orElseThrow());
        assertEquals(
                WaterState.flowing(5), world.getChunk(center).snapshot().waterState(local.x(), local.y(), local.z()));
        assertEquals(WaterState.flowing(5), world.changes().changes().getFirst().waterState());
        assertEquals(1, world.changes().size());
        assertFalse(world.changes().dirtyTiles().isEmpty());

        world.changes().drainDirtyTiles();
        BlockChangeResult unchanged = edit.setBlock(position, BlockId.WATER, WaterState.flowing(5));
        assertEquals(BlockChangeResult.Status.UNCHANGED, unchanged.status());
        assertTrue(unchanged.dirtyChunks().isEmpty());
        assertEquals(2, unchanged.chunkRevision());
        assertEquals(neighborRevision + 1, world.getChunk(neighbor).meshDependencyRevision());
        assertTrue(world.changes().dirtyTiles().isEmpty());
    }

    @Test
    void stateSurvivesUnloadReloadAndRemovalClearsTheOverrideMetadata() {
        World world = new World();
        BlockPos position = new BlockPos(-17, -1, 16);
        ChunkPos chunk = Coordinates.worldToChunk(position);
        BlockPos local = Coordinates.worldToLocal(position);
        world.putChunk(chunk, new Chunk());
        WorldEdit edit = new WorldEdit(world, 64);
        edit.setBlock(position, BlockId.WATER, WaterState.FALLING);
        world.removeChunk(chunk);
        assertFalse(world.hasChunk(chunk));
        assertEquals(WaterState.FALLING, world.getWaterState(position).orElseThrow());
        assertEquals(
                BlockChangeResult.Status.UNAVAILABLE,
                edit.setBlock(position, BlockId.WATER, WaterState.flowing(1)).status());
        assertEquals(WaterState.FALLING, world.getWaterState(position).orElseThrow());

        world.putChunk(chunk, new Chunk());
        assertEquals(WaterState.FALLING, world.getChunk(chunk).waterState(local.x(), local.y(), local.z()));
        assertEquals(1, world.getChunk(chunk).revision());
        edit.setBlock(position, BlockId.AIR);
        world.removeChunk(chunk);
        world.putChunk(chunk, new Chunk());
        assertEquals(BlockId.AIR, world.getBlock(position).orElseThrow());
        assertEquals(WaterState.EMPTY, world.getWaterState(position).orElseThrow());
        assertEquals(WaterState.EMPTY, world.getChunk(chunk).waterState(local.x(), local.y(), local.z()));
        edit.setBlock(position, BlockId.WATER);
        assertEquals(WaterState.SOURCE, world.getWaterState(position).orElseThrow());
    }

    @Test
    void worldDefaultWriteResetsFlowToSourceEvenWhenBlockIdDoesNotChange() {
        World world = new World();
        BlockPos position = new BlockPos(3, 4, 5);
        Chunk chunk = new Chunk();
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        world.setBlock(position, BlockId.WATER, WaterState.FALLING, 64);
        assertEquals(BlockId.WATER, world.setBlock(position, BlockId.WATER, 64).orElseThrow());
        assertEquals(WaterState.SOURCE, world.getWaterState(position).orElseThrow());
        assertEquals(WaterState.SOURCE, chunk.waterState(3, 4, 5));
        assertEquals(2, chunk.revision());
        world.changes().drainDirtyTiles();
        world.setBlock(position, BlockId.WATER, 64);
        assertEquals(2, chunk.revision());
        assertTrue(world.changes().dirtyTiles().isEmpty());
    }

    @Test
    void worldReadDistinguishesMissingDataFromEmptyFluidAndSources() {
        World world = new World();
        BlockPos position = new BlockPos(1, 2, 3);
        assertTrue(world.getWaterState(position).isEmpty());
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        assertEquals(WaterState.EMPTY, world.getWaterState(position).orElseThrow());
        world.getChunk(new ChunkPos(0, 0, 0)).set(1, 2, 3, BlockId.WATER);
        assertEquals(WaterState.SOURCE, world.getWaterState(position).orElseThrow());
        assertEquals(0, world.changes().size());
    }

    @Test
    void invalidStateDoesNotMutateResidentDataOrCreateAnOverride() {
        World world = new World();
        Chunk chunk = new Chunk();
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        BlockPos position = new BlockPos(1, 2, 3);
        WorldEdit edit = new WorldEdit(world, 64);
        assertThrows(IllegalArgumentException.class, () -> edit.setBlock(position, BlockId.STONE, WaterState.FALLING));
        assertThrows(
                IllegalArgumentException.class, () -> world.setBlock(position, BlockId.WATER, WaterState.EMPTY, 64));
        assertThrows(
                IllegalArgumentException.class, () -> world.setBlock(position, BlockId.WATER, WaterState.FALLING, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> world.setBlock(new BlockPos(16, 0, 0), BlockId.AIR, WaterState.SOURCE, 64));
        assertEquals(BlockId.AIR, chunk.get(1, 2, 3));
        assertEquals(WaterState.EMPTY, chunk.waterState(1, 2, 3));
        assertEquals(0, chunk.revision());
        assertEquals(0, chunk.meshDependencyRevision());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().dirtyTiles().isEmpty());
    }

    @Test
    void worldEditDefaultWritePromotesFlowToSource() {
        World world = new World();
        Chunk chunk = new Chunk();
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        BlockPos position = new BlockPos(1, 2, 3);
        WorldEdit edit = new WorldEdit(world, 64);
        edit.setBlock(position, BlockId.WATER, WaterState.flowing(7));
        assertTrue(edit.setBlock(position, BlockId.WATER).changed());
        assertEquals(WaterState.SOURCE, world.getWaterState(position).orElseThrow());
        assertEquals(2, chunk.revision());
    }
}

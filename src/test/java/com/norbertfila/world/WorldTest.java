package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class WorldTest {
    @Test
    void neighborArrivalAndRemovalInvalidateDependenciesWithoutChangingData() {
        World world = new World();
        Chunk center = new Chunk();
        world.putChunk(new ChunkPos(-1, 0, 0), center);
        ChunkSnapshot snapshot = center.snapshot();

        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        assertEquals(1, center.meshDependencyRevision());
        world.putChunk(new ChunkPos(0, 0, 1), new Chunk());
        assertEquals(1, center.meshDependencyRevision());
        world.removeChunk(new ChunkPos(0, 0, 0));
        assertEquals(2, center.meshDependencyRevision());
        world.removeChunk(new ChunkPos(0, 0, 0));
        assertEquals(2, center.meshDependencyRevision());
        assertEquals(0, center.revision());
        assertEquals(0, snapshot.meshDependencyRevision());
        assertEquals(0, world.changes().size());
    }

    @Test
    void distinguishesMissingChunkFromAir() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(0, 0, 0, BlockId.STONE);
        world.putChunk(new ChunkPos(-1, 0, 2), chunk);

        assertEquals(OptionalInt.empty(), world.getBlock(new BlockPos(-32, 0, 32)));
        assertEquals(OptionalInt.of(BlockId.toUnsignedInt(BlockId.STONE)), world.getBlock(new BlockPos(-16, 0, 32)));
        assertEquals(OptionalInt.of(BlockId.toUnsignedInt(BlockId.AIR)), world.getBlock(new BlockPos(-15, 0, 32)));
    }

    @Test
    void looksUpSeveralChunksIncludingNegativeCoordinates() {
        World world = new World();
        Chunk negative = new Chunk();
        Chunk positive = new Chunk();
        negative.set(15, 15, 15, BlockId.DIRT);
        positive.set(0, 0, 0, BlockId.GRASS);
        world.putChunk(new ChunkPos(-1, -1, -1), negative);
        world.putChunk(new ChunkPos(1, 1, 1), positive);

        assertEquals(OptionalInt.of(BlockId.toUnsignedInt(BlockId.DIRT)), world.getBlock(new BlockPos(-1, -1, -1)));
        assertEquals(OptionalInt.of(BlockId.toUnsignedInt(BlockId.GRASS)), world.getBlock(new BlockPos(16, 16, 16)));
        assertTrue(world.hasChunk(new ChunkPos(-1, -1, -1)));
        assertFalse(world.hasChunk(new ChunkPos(0, 0, 0)));
    }

    @Test
    void requestDoesNotCreateOrFakeAChunk() {
        World world = new World();
        ChunkPos position = new ChunkPos(3, 0, -4);

        world.requestChunk(position);

        assertTrue(world.isChunkRequested(position));
        assertFalse(world.hasChunk(position));
        assertEquals(OptionalInt.empty(), world.getBlock(new BlockPos(48, 0, -64)));

        world.putChunk(position, new Chunk());

        assertFalse(world.isChunkRequested(position));
        assertTrue(world.hasChunk(position));
    }

    @Test
    void blockOverrideWinsForResidentWorld() {
        World world = new World();
        Chunk chunk = new Chunk();
        world.putChunk(new ChunkPos(-1, 0, -1), chunk);
        BlockPos block = new BlockPos(-1, 0, -1);

        assertEquals(BlockId.AIR, world.setBlock(block, BlockId.STONE, 8).orElseThrow());
        assertEquals(BlockId.STONE, world.getBlock(block).orElseThrow());
        assertEquals(BlockId.STONE, chunk.get(15, 0, 15));
        world.removeBlockOverride(block, 8);
        assertTrue(world.changes().dirtyTiles().size() >= 1);
    }

    @Test
    void repeatedValueDoesNotCreateAnOverrideOrDirtyTile() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 3, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);

        assertEquals(
                BlockId.STONE,
                world.setBlock(new BlockPos(1, 2, 3), BlockId.STONE, 8).orElseThrow());

        assertEquals(1, chunk.revision());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().dirtyTiles().isEmpty());
    }

    @Test
    void rejectsInvalidEditsBeforeMutatingChunkOrOverlay() {
        World world = new World();
        Chunk chunk = new Chunk();
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        BlockPos position = new BlockPos(1, 2, 3);

        assertThrows(IllegalArgumentException.class, () -> world.setBlock(position, BlockId.STONE, 0));
        assertThrows(IllegalArgumentException.class, () -> world.setBlock(position, (byte) 255, 8));
        assertThrows(IllegalArgumentException.class, () -> world.setBlock(new BlockPos(100, 0, 0), BlockId.STONE, 0));

        assertEquals(BlockId.AIR, chunk.get(1, 2, 3));
        assertEquals(0, chunk.revision());
        assertEquals(0, world.changes().size());
        assertTrue(world.changes().dirtyTiles().isEmpty());
    }
}

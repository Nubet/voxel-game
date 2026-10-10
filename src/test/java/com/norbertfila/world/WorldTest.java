package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class WorldTest {
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
}

package com.norbertfila.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class VoxelRaycasterTest {
    private final VoxelRaycaster raycaster = new VoxelRaycaster();

    @Test
    void hitsAlongAnAxisAndReportsEnteredFace() {
        World world = worldWithBlock(new BlockPos(3, 1, 1));

        VoxelRaycaster.Hit hit = raycaster.cast(world, .5, 1.5, 1.5, 1, 0, 0, 5).orElseThrow();

        assertEquals(new BlockPos(3, 1, 1), hit.block());
        assertEquals(new VoxelRaycaster.Normal(-1, 0, 0), hit.normal());
        assertEquals(2.5, hit.distance());
        assertEquals(BlockId.STONE, hit.blockId());
    }

    @Test
    void returnsAHitWhenTheRayStartsInsideASolidBlock() {
        World world = worldWithBlock(new BlockPos(0, 0, 0));

        VoxelRaycaster.Hit hit =
                raycaster.cast(world, .25, .25, .25, 0, 1, 0, 5).orElseThrow();

        assertEquals(new BlockPos(0, 0, 0), hit.block());
        assertEquals(VoxelRaycaster.Normal.ZERO, hit.normal());
        assertEquals(0, hit.distance());
    }

    @Test
    void handlesDiagonalAndZeroDirectionComponents() {
        World world = worldWithBlock(new BlockPos(1, 0, 0));

        VoxelRaycaster.Hit hit = raycaster.cast(world, .1, .1, .1, 1, 1, 1, 5).orElseThrow();

        assertEquals(new BlockPos(1, 0, 0), hit.block());
        assertEquals(new VoxelRaycaster.Normal(-1, 0, 0), hit.normal());
        assertTrue(hit.distance() > 0);
    }

    @Test
    void crossesNegativeChunkBoundary() {
        World world = worldWithBlock(new BlockPos(-1, 1, 1));

        VoxelRaycaster.Hit hit =
                raycaster.cast(world, .5, 1.5, 1.5, -1, 0, 0, 5).orElseThrow();

        assertEquals(new BlockPos(-1, 1, 1), hit.block());
        assertEquals(new VoxelRaycaster.Normal(1, 0, 0), hit.normal());
    }

    @Test
    void missesWhenTheTargetIsBeyondMaxDistance() {
        World world = worldWithBlock(new BlockPos(3, 1, 1));

        assertTrue(raycaster.cast(world, .5, 1.5, 1.5, 1, 0, 0, 2).isEmpty());
    }

    private static World worldWithBlock(BlockPos position) {
        World world = new World();
        ChunkPos chunkPosition = new ChunkPos(
                Math.floorDiv(position.x(), Chunk.SIZE),
                Math.floorDiv(position.y(), Chunk.SIZE),
                Math.floorDiv(position.z(), Chunk.SIZE));
        Chunk chunk = new Chunk();
        chunk.set(
                Math.floorMod(position.x(), Chunk.SIZE),
                Math.floorMod(position.y(), Chunk.SIZE),
                Math.floorMod(position.z(), Chunk.SIZE),
                BlockId.STONE);
        world.putChunk(chunkPosition, chunk);
        return world;
    }
}

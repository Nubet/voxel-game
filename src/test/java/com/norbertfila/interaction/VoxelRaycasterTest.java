package com.norbertfila.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void stopsAtMissingChunksEvenWhenAnOverlayOrLaterChunkContainsABlock() {
        World world = worldWithBlock(new BlockPos(32, 1, 1));
        world.setBlock(new BlockPos(16, 1, 1), BlockId.STONE, 8);

        assertTrue(raycaster.cast(world, 15.5, 1.5, 1.5, 1, 0, 0, 20).isEmpty());
        assertTrue(raycaster.cast(world, 16.5, 1.5, 1.5, 1, 0, 0, 20).isEmpty());
        assertEquals(2, world.residentCount());
        assertFalse(world.isChunkRequested(new ChunkPos(1, 0, 0)));
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, Double.MAX_VALUE, Double.MIN_VALUE})
    void normalizesDirectionWithoutOverflowOrUnderflow(double direction) {
        World world = worldWithBlock(new BlockPos(3, 1, 1));

        assertEquals(
                2.5,
                raycaster
                        .cast(world, .5, 1.5, 1.5, direction, -0.0, 0, 6)
                        .orElseThrow()
                        .distance());
    }

    @Test
    void includesTheExactReachBoundaryAndRejectsInvalidRays() {
        World world = worldWithBlock(new BlockPos(6, 1, 1));
        world.putChunk(new ChunkPos(-1, 0, 0), new Chunk());

        assertEquals(
                6, raycaster.cast(world, 0, 1.5, 1.5, 1, 0, 0, 6).orElseThrow().distance());
        assertTrue(raycaster.cast(world, -.001, 1.5, 1.5, 1, 0, 0, 6).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> raycaster.cast(world, 0, 0, 0, 0, 0, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> raycaster.cast(world, Double.NaN, 0, 0, 1, 0, 0, 6));
    }

    @ParameterizedTest
    @CsvSource({"1,0,0", "-1,0,0", "0,1,0", "0,-1,0", "0,0,1", "0,0,-1"})
    void skipsWaterIncludingTheOriginAndPreservesFaceDistanceAndReach(int dx, int dy, int dz) {
        BlockPos ground = new BlockPos(2 * dx, 2 * dy, 2 * dz);
        World world = worldWithBlock(ground);
        world.getChunk(new ChunkPos(0, 0, 0)).set(0, 0, 0, BlockId.WATER);
        BlockPos water = new BlockPos(dx, dy, dz);
        BlockPos local = Coordinates.worldToLocal(water);
        world.getChunk(Coordinates.worldToChunk(water)).set(local.x(), local.y(), local.z(), BlockId.WATER);
        var hit = raycaster.cast(world, .5, .5, .5, dx, dy, dz, 1.5).orElseThrow();
        assertEquals(ground, hit.block());
        assertEquals(new VoxelRaycaster.Normal(-dx, -dy, -dz), hit.normal());
        assertEquals(1.5, hit.distance());
        assertTrue(raycaster.cast(world, .5, .5, .5, dx, dy, dz, 1.49).isEmpty());
        var creative =
                raycaster.cast(world, .5, .5, .5, dx, dy, dz, 1.5, id -> true).orElseThrow();
        assertEquals(BlockId.WATER, creative.blockId());
        assertEquals(VoxelRaycaster.Normal.ZERO, creative.normal());
        assertEquals(0, creative.distance());
    }

    private static World worldWithBlock(BlockPos position) {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
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

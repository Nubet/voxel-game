package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.PlayerPhysics;
import com.norbertfila.player.PlayerState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VoxelCollisionTest {
    private final VoxelCollision collision = new VoxelCollision();

    @ParameterizedTest
    @ValueSource(ints = {-17, -16, -1, 0, 15, 16})
    void clipsFastMovementAgainstWallsAcrossSignedChunkBoundaries(int wallX) {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(wallX - 2.5, 1, .5));
        World world = loaded(VoxelCollision.requiredRegion(body, 8, 0, 0));
        block(world, wallX, 1, 0);

        var hit = collision.move(world, body, 8, 0, 0);

        assertTrue(hit.available());
        assertTrue(hit.blockedX());
        assertEquals(wallX, hit.body().maxX(), 1e-12);
        assertFalse(hit.grounded());
        assertTrue(collision.spaceFree(world, hit.body()));
    }

    @Test
    void negativeMovementAndFastFallStopAtExactPlanes() {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(5.5, 30, .5));
        World world = loaded(VoxelCollision.requiredRegion(body, -8, -50, 0));
        block(world, 0, 1, 0);
        for (int x = -4; x <= 6; x++) block(world, x, 0, 0);

        var hit = collision.move(world, body, -8, -50, 0);

        assertEquals(1, hit.body().minY());
        assertEquals(1, hit.body().minX());
        assertTrue(hit.grounded());
        assertTrue(hit.blockedX() && hit.blockedY());
    }

    @Test
    void upwardCeilingCollisionDoesNotGroundPlayer() {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(.5, 1, .5));
        World world = loaded(VoxelCollision.requiredRegion(body, 0, 10, 0));
        block(world, 0, 3, 0);

        var hit = collision.move(world, body, 0, 10, 0);

        assertEquals(3, hit.body().maxY());
        assertTrue(hit.blockedY());
        assertFalse(hit.grounded());
    }

    @Test
    void verticalClippingPrecedesHorizontalAndWallSlidePreservesOtherAxis() {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(1.5, 1, .5));
        World world = loaded(VoxelCollision.requiredRegion(body, 2, 2, 1));
        block(world, 2, 1, 0);
        var overWall = collision.move(world, body, 2, 2, 0);
        assertEquals(2, overWall.x());
        assertEquals(2, overWall.y());

        var slide = collision.move(world, body, 2, 0, 1);
        assertEquals(.2, slide.x(), 1e-12);
        assertEquals(1, slide.z());
        assertTrue(slide.blockedX());
        assertFalse(slide.blockedZ());
    }

    @Test
    void touchingFacesAreNotPenetrationAndGroundRemainsStable() {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(.5, 1, .5));
        World world = loaded(VoxelCollision.requiredRegion(body, 0, -.01, 0));
        block(world, 0, 0, 0);
        assertTrue(collision.spaceFree(world, body));
        for (int i = 0; i < 100; i++) {
            var hit = collision.move(world, body, 0, -.01, 0);
            assertEquals(body, hit.body());
            assertTrue(hit.grounded());
        }
    }

    @Test
    void missingChunksAreNotAirAndWholeStateFreezesUntilDataArrives() {
        PlayerState start = new PlayerState(new PlayerState.Position(15.5, 15, .5), 60, -30, 0, false, .3, false);
        PlayerPhysics physics = new PlayerPhysics();
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        assertFalse(collision.spaceFree(world, PlayerPhysics.body(new PlayerState.Position(16.5, 1, .5))));

        assertSame(start, physics.step(world, start, 1, 0, false, 1.0 / 60));
        assertTrue(physics.requiredChunks(start, 1, 0, false, 1.0 / 60).contains(new ChunkPos(1, 1, 0)));
        for (ChunkPos pos : physics.requiredChunks(start, 1, 0, false, 1.0 / 60))
            if (!world.hasChunk(pos)) world.putChunk(pos, new Chunk());
        PlayerState resumed = physics.step(world, start, 1, 0, false, 1.0 / 60);
        assertTrue(resumed.position().x() > 16);
        assertTrue(resumed.position().y() < 15);
        assertTrue(resumed.velocityY() < -30);
        assertEquals(.3 - 1.0 / 60, resumed.jumpCooldownSeconds());
    }

    @Test
    void availabilityIncludesSupportAndStepClearanceNotOnlyFeetChunk() {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(.5, 16, .5));
        var chunks = VoxelCollision.requiredChunks(VoxelCollision.requiredRegion(body, 0, 0, 0));
        assertTrue(chunks.contains(new ChunkPos(0, 0, 0)));
        assertTrue(chunks.contains(new ChunkPos(0, 1, 0)));
        var corner = PlayerPhysics.body(new PlayerState.Position(0, 0, 0));
        assertEquals(
                8,
                VoxelCollision.requiredChunks(VoxelCollision.requiredRegion(corner, 0, 0, 0))
                        .size());
    }

    @Test
    void waterDoesNotBlockMovementOrProvideFloorSupport() {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(.5, 1, .5));
        World world = loaded(VoxelCollision.requiredRegion(body, 2, -.5, 0));
        world.setBlock(new BlockPos(1, 1, 0), BlockId.WATER, 64);
        world.setBlock(new BlockPos(0, 0, 0), BlockId.WATER, 64);
        world.setBlock(new BlockPos(0, 1, 0), BlockId.WATER, 64);
        assertTrue(collision.spaceFree(world, body));
        var moved = collision.move(world, body, 2, -.5, 0);
        assertTrue(moved.available());
        assertEquals(2, moved.x());
        assertEquals(-.5, moved.y());
        assertFalse(moved.blockedX());
        assertFalse(moved.blockedY());
        assertFalse(moved.grounded());
    }

    @Test
    void authoritativeBlockEditsImmediatelyChangeCollisions() {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(.5, 1, .5));
        World world = loaded(VoxelCollision.requiredRegion(body, 2, 0, 0));
        world.setBlock(new BlockPos(1, 1, 0), BlockId.STONE, 64);
        assertTrue(collision.move(world, body, 2, 0, 0).blockedX());
        world.setBlock(new BlockPos(1, 1, 0), BlockId.AIR, 64);
        assertEquals(2, collision.move(world, body, 2, 0, 0).x());
    }

    @Test
    void sweptClippingMatchesSteppedBaselineForLevelWallAndFloor() {
        Aabb body = PlayerPhysics.body(new PlayerState.Position(.5, 1, .5));
        World world = loaded(VoxelCollision.requiredRegion(body, 4, -.1, 0));
        for (int x = 0; x <= 4; x++) block(world, x, 0, 0);
        block(world, 2, 1, 0);
        var swept = collision.move(world, body, 4, -.1, 0);
        var baseline = collision.moveStepped(world, body, 4, -.1, 0);
        assertEquals(baseline.body().minX(), swept.body().minX(), 1e-6);
        assertEquals(baseline.body().minY(), swept.body().minY(), 1e-6);
        assertTrue(swept.grounded());
    }

    @Test
    void invalidOrUnboundedRegionsFailBeforeScanningOrScheduling() {
        assertThrows(IllegalArgumentException.class, () -> new Aabb(0, 0, 0, Double.NaN, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Aabb(0, 0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> VoxelCollision.requiredChunks(new Aabb(0, 0, 0, 1e9, 1, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> VoxelCollision.requiredChunks(new Aabb(Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE + 2.0, 1, 1)));
        assertThrows(
                IllegalArgumentException.class, () -> VoxelCollision.requiredChunks(new Aabb(0, 0, 0, 1, 20000, 1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1024, -17, -1, 0, 1, 16, 1024, 1000000})
    void repeatedPhysicsTicksDoNotPenetrateWallAfterReconstructingBodyFromFeet(int wallX) {
        for (boolean alongX : new boolean[] {true, false}) {
            for (int direction : new int[] {-1, 1}) {
                double start = wallX + (direction > 0 ? -1.5 : 2.5);
                PlayerState state =
                        PlayerState.at(new PlayerState.Position(alongX ? start : .5, 1, alongX ? .5 : start));
                World world = loaded(VoxelCollision.requiredRegion(
                        PlayerPhysics.body(state.position()),
                        alongX ? 2 * direction : 0,
                        -.1,
                        alongX ? 0 : 2 * direction));
                for (int offset = direction > 0 ? -2 : 1; offset <= (direction > 0 ? 0 : 3); offset++) {
                    block(world, alongX ? wallX + offset : 0, 0, alongX ? 0 : wallX + offset);
                }
                block(world, alongX ? wallX : 0, 1, alongX ? 0 : wallX);
                PlayerPhysics physics = new PlayerPhysics();
                for (int tick = 0; tick < 300; tick++) {
                    state = physics.step(
                            world, state, alongX ? .75 * direction : 0, alongX ? 0 : .75 * direction, false, 1.0 / 60);
                    Aabb body = PlayerPhysics.body(state.position());
                    double min = alongX ? body.minX() : body.minZ();
                    double max = alongX ? body.maxX() : body.maxZ();
                    assertTrue(direction > 0 ? max <= wallX : min >= wallX + 1);
                    assertEquals(1, state.position().y());
                    assertTrue(state.grounded());
                }
                assertEquals(
                        wallX + (direction > 0 ? -.3 : 1.3),
                        alongX ? state.position().x() : state.position().z(),
                        1e-10);
                assertEquals(0, state.velocityX());
                assertEquals(0, state.velocityZ());
            }
        }
    }

    private static World loaded(Aabb region) {
        World world = new World();
        for (ChunkPos pos : VoxelCollision.requiredChunks(region)) world.putChunk(pos, new Chunk());
        return world;
    }

    private static void block(World world, int x, int y, int z) {
        BlockPos local = Coordinates.worldToLocal(new BlockPos(x, y, z));
        world.getChunk(Coordinates.worldToChunk(new BlockPos(x, y, z)))
                .set(local.x(), local.y(), local.z(), BlockId.STONE);
    }
}

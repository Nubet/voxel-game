package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerStepUpTest {
    private static final double DT = 1.0 / 60;
    private static final Aabb FLOOR = new Aabb(-8, 0, -8, 8, 1, 8);
    private final VoxelCollision collision = new VoxelCollision();

    @ParameterizedTest
    @ValueSource(doubles = {.1, .5, .6, .600001, 1})
    void climbsOnlyStepsWithinPointSix(double height) {
        Aabb body = body(.5, 1, .5, false);
        List<Aabb> obstacles = List.of(FLOOR, new Aabb(1, 1, 0, 3, 1 + height, 1));
        var normal = collision.move(obstacles, body, .8, -.01, 0, false, false);
        var stepped = collision.move(obstacles, body, .8, -.01, 0, false, true);
        assertEquals(.2, normal.x(), 1e-12);
        if (height <= .6) {
            assertEquals(.8, stepped.x());
            assertEquals(1 + height, stepped.body().minY(), 1e-12);
            assertTrue(stepped.grounded());
            assertTrue(stepped.blockedY());
            assertFalse(stepped.blockedX());
        } else assertEquals(normal, stepped);
        assertClear(stepped.body(), obstacles);
    }

    @ParameterizedTest
    @ValueSource(doubles = {3.1, 3.3, 3.4})
    void ceilingClearanceClipsTheUpwardLeg(double ceilingY) {
        List<Aabb> obstacles =
                List.of(FLOOR, new Aabb(1, 1, 0, 3, 1.5, 1), new Aabb(-2, ceilingY, -2, 4, ceilingY + 1, 2));
        Aabb body = body(.5, 1, .5, false);
        var normal = collision.move(obstacles, body, .8, -.01, 0, false, false);
        var stepped = collision.move(obstacles, body, .8, -.01, 0, false, true);
        if (ceilingY < 3.3) assertEquals(normal, stepped);
        else {
            assertEquals(.8, stepped.x());
            assertEquals(1.5, stepped.body().minY(), 1e-12);
        }
        assertTrue(stepped.body().maxY() <= ceilingY);
        assertClear(stepped.body(), obstacles);
    }

    @Test
    void ceilingAlongTheHorizontalLegAlsoBlocksStepping() {
        List<Aabb> obstacles = List.of(FLOOR, new Aabb(1, 1, 0, 3, 1.5, 1), new Aabb(1, 3.1, 0, 3, 4, 1));
        Aabb body = body(.5, 1, .5, false);
        assertEquals(
                collision.move(obstacles, body, .8, -.01, 0, false, false),
                collision.move(obstacles, body, .8, -.01, 0, false, true));
    }

    @Test
    void actualCrouchedHeightAllowsAPathThatStandingCannotTake() {
        List<Aabb> obstacles = List.of(FLOOR, new Aabb(1, 1, 0, 3, 1.5, 1), new Aabb(-2, 3.01, -2, 4, 4, 2));
        var standing = collision.move(obstacles, body(.5, 1, .5, false), .8, -.01, 0, false, true);
        var crouched = collision.move(obstacles, body(.5, 1, .5, true), .8, -.01, 0, true, true);
        assertEquals(.2, standing.x(), 1e-12);
        assertEquals(.8, crouched.x());
        assertEquals(1.5, crouched.body().minY(), 1e-12);
        assertClear(crouched.body(), obstacles);
    }

    @Test
    void rejectsTheRaisedRouteWhenItEndsOverAVoid() {
        List<Aabb> obstacles = List.of(new Aabb(0, 0, 0, 1, 1, 1), new Aabb(1, 1, 0, 1.2, 1.5, 1));
        Aabb body = body(.5, 1, .5, false);
        var normal = collision.move(obstacles, body, 2, -.01, 0, false, false);
        assertEquals(normal, collision.move(obstacles, body, 2, -.01, 0, false, true));
        assertEquals(1, normal.body().minY());
        assertEquals(.2, normal.x(), 1e-12);
    }

    @Test
    void noHorizontalGainKeepsTheNormalRouteWithoutRaisingTheBody() {
        List<Aabb> floorOnly = List.of(FLOOR);
        Aabb body = body(.5, 1, .5, false);
        assertEquals(
                collision.move(floorOnly, body, .8, -.01, .4, false, false),
                collision.move(floorOnly, body, .8, -.01, .4, false, true));
        List<Aabb> blocked = List.of(FLOOR, new Aabb(1, 1, 0, 2, 1.5, 1), new Aabb(1, 1.5, 0, 2, 3, 1));
        assertEquals(
                collision.move(blocked, body, .8, -.01, .4, false, false),
                collision.move(blocked, body, .8, -.01, .4, false, true));
    }

    @ParameterizedTest
    @ValueSource(doubles = {.8, 1.0})
    void anImprovementOnOneAxisIsNotEnoughWhenTotalTravelIsSmallerOrEqual(double wallZ) {
        List<Aabb> obstacles = List.of(FLOOR, new Aabb(1, 1, 0, 2, 1.5, 1), new Aabb(1, 1.5, wallZ, 3, 4, 3));
        Aabb body = body(.5, 1, .5, false);
        var normal = collision.move(obstacles, body, .8, -.01, .8, false, false);
        assertEquals(.2, normal.x(), 1e-12);
        assertEquals(.8, normal.z());
        assertEquals(normal, collision.move(obstacles, body, .8, -.01, .8, false, true));
    }

    @Test
    void steppingDoesNotActivateDuringAscentOrUnsupportedFalling() {
        List<Aabb> obstacles = List.of(FLOOR, new Aabb(1, 1, 0, 3, 1.5, 1));
        Aabb body = body(.5, 1, .5, false);
        assertEquals(
                collision.move(obstacles, body, .8, .1, 0, false, false),
                collision.move(obstacles, body, .8, .1, 0, false, true));
        Aabb falling = body(.5, 1.1, .5, false);
        assertEquals(
                collision.move(obstacles, falling, .8, -.01, 0, false, false),
                collision.move(obstacles, falling, .8, -.01, 0, false, true));
    }

    @ParameterizedTest
    @ValueSource(ints = {-17, -1, 0, 15, 16})
    void handlesBothAxesDirectionsAndDiagonalStepsAtSignedBoundaries(int origin) {
        Aabb floor = FLOOR.move(origin, 0, origin);
        Aabb step = new Aabb(origin, 1, origin, origin + 1, 1.5, origin + 1);
        List<Aabb> obstacles = List.of(floor, step);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Aabb body = body(origin + .5 - dx * .8, 1, origin + .5 - dz * .8, false);
                var moved = collision.move(obstacles, body, dx * .8, -.01, dz * .8, false, true);
                assertEquals(dx * .8, moved.x(), 1e-12);
                assertEquals(dz * .8, moved.z(), 1e-12);
                assertEquals(1.5, moved.body().minY(), 1e-12);
                assertClear(moved.body(), obstacles);
            }
    }

    @Test
    void repeatedTicksStayOnTheStepWithoutAnArtificialVerticalImpulse() {
        List<Aabb> obstacles = List.of(FLOOR, new Aabb(1, 1, 0, 3, 1.5, 1));
        var moved = collision.move(obstacles, body(.5, 1, .5, false), .8, -.01, 0, false, true);
        for (int tick = 0; tick < 300; tick++) {
            moved = collision.move(obstacles, moved.body(), 0, -.01, 0, false, true);
            assertEquals(1.5, moved.body().minY(), 1e-12);
            assertEquals(0, moved.y());
            assertTrue(moved.grounded());
            assertClear(moved.body(), obstacles);
        }
        var unsupported = collision.move(List.of(), moved.body(), 0, -.1, 0, false, true);
        assertFalse(unsupported.grounded());
        assertEquals(1.4, unsupported.body().minY(), 1e-12);
    }

    @Test
    void realWorldBlocksRemainFullHeightAndRequireJumping() {
        World world = PlayerDynamicsTest.flatWorld();
        world.getChunk(new ChunkPos(0, 0, 0)).set(1, 1, 0, BlockId.STONE);
        PlayerPhysics physics = new PlayerPhysics();
        PlayerState state = new PlayerState(new PlayerState.Position(.5, 1, .5), 0, 0, 0, true, 0, false);
        for (int tick = 0; tick < 120; tick++) state = physics.step(world, state, 1, 0, false, DT);
        assertEquals(.7, state.position().x(), 1e-12);
        assertEquals(1, state.position().y());
        assertEquals(0, state.velocityX());
        assertFalse(collision.spaceFree(world, body(1.5, 1.75, .5, true)));
        for (int tick = 0; tick < 60; tick++) state = physics.step(world, state, 1, 0, tick == 0, DT);
        assertTrue(state.position().x() > 1.3);
    }

    @Test
    void fixtureCubesAndWorldCubesUseTheSameClippingAndStepSelection() {
        World world = PlayerDynamicsTest.flatWorld();
        world.getChunk(new ChunkPos(0, 0, 0)).set(1, 1, 0, BlockId.STONE);
        List<Aabb> obstacles = List.of(FLOOR, new Aabb(1, 1, 0, 2, 2, 1));
        Aabb body = body(.5, 1, .5, false);
        assertEquals(
                collision.move(world, body, .8, -.01, .1, false, true),
                collision.move(obstacles, body, .8, -.01, .1, false, true));
    }

    @Test
    void unknownStepClearanceFreezesStateAndIsIncludedInPurePreview() {
        World world = new World();
        PlayerState state = new PlayerState(new PlayerState.Position(.5, 14, .5), 1, 0, 0, true, .2, false);
        PlayerPhysics physics = new PlayerPhysics();
        var required = physics.requiredChunks(state, 1, 0, false, DT);
        ChunkPos overhead = new ChunkPos(0, 1, 0);
        assertTrue(required.contains(overhead));
        for (ChunkPos pos : required) world.putChunk(pos, new Chunk());
        world.getChunk(new ChunkPos(0, 0, 0)).set(0, 13, 0, BlockId.STONE);
        world.removeChunk(overhead);
        assertEquals(required, physics.requiredChunks(state, 1, 0, false, DT));
        assertSame(state, physics.step(world, state, 1, 0, false, DT));
        world.putChunk(overhead, new Chunk());
        assertNotSame(state, physics.step(world, state, 1, 0, false, DT));
    }

    private static Aabb body(double x, double y, double z, boolean crouched) {
        return PlayerState.at(new PlayerState.Position(x, y, z))
                .withCrouching(crouched)
                .body();
    }

    private static void assertClear(Aabb body, List<Aabb> obstacles) {
        for (Aabb obstacle : obstacles) assertFalse(body.intersects(obstacle));
    }
}

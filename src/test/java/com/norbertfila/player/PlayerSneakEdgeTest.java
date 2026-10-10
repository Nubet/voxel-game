package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerSneakEdgeTest {
    private static final double DT = 1.0 / 60;
    private final PlayerPhysics physics = new PlayerPhysics();
    private final VoxelCollision collision = new VoxelCollision();

    @ParameterizedTest
    @ValueSource(ints = {-17, -1, 0, 15, 16})
    void holdsAllEdgesAndCornersWithPartialFootprintSupport(int origin) {
        World world = emptyWorld();
        block(world, origin, 0, origin, BlockId.STONE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                PlayerState state = standing(origin + .5, 1, origin + .5);
                for (int tick = 0; tick < 300; tick++) {
                    state = physics.step(world, state, dx, dz, false, DT);
                    assertTrue(state.grounded());
                    assertEquals(1, state.position().y());
                    Aabb body = state.body();
                    assertTrue(body.minX() < origin + 1 && body.maxX() > origin);
                    assertTrue(body.minZ() < origin + 1 && body.maxZ() > origin);
                }
                assertTrue(Math.hypot(
                                state.position().x() - origin - .5,
                                state.position().z() - origin - .5)
                        > .5);
            }
        }
    }

    @Test
    void diagonalProbeRejectsAHoleEvenWhenBothAxisProbesHaveSupport() {
        World world = emptyWorld();
        block(world, 1, 0, 0, BlockId.STONE);
        block(world, 0, 0, 1, BlockId.STONE);
        Aabb body = standing(.99, 1, .99).body();
        assertEquals(.4, collision.move(world, body, .4, 0, 0, true).x());
        assertEquals(.4, collision.move(world, body, 0, 0, .4, true).z());
        var diagonal = collision.move(world, body, .4, -.01, .4, true);
        assertTrue(diagonal.x() < .4 && diagonal.z() < .4);
        assertEquals(diagonal.x(), diagonal.z());
        assertTrue(diagonal.body().minX() < 1 && diagonal.body().minZ() < 1);
        assertTrue(diagonal.grounded());
    }

    @Test
    void blockedEdgeAxisStillAllowsSlidingAlongTheLedge() {
        World world = emptyWorld();
        for (int z = 0; z <= 3; z++) block(world, 0, 0, z, BlockId.STONE);
        var moved = collision.move(world, standing(1.29, 1, .5).body(), .1, -.01, .2, true);
        assertEquals(0, moved.x());
        assertEquals(.2, moved.z());
        assertTrue(moved.blockedX());
        assertFalse(moved.blockedZ());
        assertTrue(moved.grounded());
    }

    @ParameterizedTest
    @ValueSource(doubles = {.59, .6, .61, 1})
    void supportDepthUsesThePointSixStepThreshold(double gap) {
        World world = emptyWorld();
        block(world, 0, 0, 0, BlockId.STONE);
        var moved = collision.move(world, standing(1.1, 1 + gap, .5).body(), .4, 0, 0, true);
        assertEquals(gap < .6, moved.blockedX());
        assertEquals(0, moved.y());
    }

    @Test
    void jumpAndAirborneMotionBypassEdgeProtection() {
        World world = emptyWorld();
        block(world, 0, 0, 0, BlockId.STONE);
        PlayerState start = new PlayerState(new PlayerState.Position(1.29, 1, .5), 4, 0, 0, true, 0, true);
        PlayerState jump = physics.step(world, start, 1, 0, true, DT);
        assertTrue(jump.position().x() > 1.3);
        assertTrue(jump.position().y() > 1);
        assertFalse(jump.grounded());
        PlayerState falling = new PlayerState(new PlayerState.Position(1.29, 1.1, .5), 4, -1, 0, false, 0, true);
        PlayerState next = physics.step(world, falling, 1, 0, false, DT);
        assertTrue(next.position().x() > 1.3);
        assertTrue(next.velocityY() < 0);
    }

    @Test
    void removingSupportDoesNotFreezeGravityOrHorizontalMotion() {
        World world = emptyWorld();
        block(world, 0, 0, 0, BlockId.STONE);
        PlayerState start = standing(.5, 1, .5);
        block(world, 0, 0, 0, BlockId.AIR);
        PlayerState next = physics.step(world, start, 1, 0, false, DT);
        assertTrue(next.position().y() < 1);
        assertTrue(next.position().x() > .5);
        assertTrue(next.velocityY() < 0);
        assertFalse(next.grounded());
    }

    @Test
    void wallsAndCeilingsDoNotCountAsSupport() {
        World world = emptyWorld();
        block(world, 1, 1, 0, BlockId.STONE);
        block(world, 0, 3, 0, BlockId.STONE);
        PlayerState start = standing(.5, 1.3, .5);
        PlayerState next = physics.step(world, start, -1, 0, false, DT);
        assertTrue(next.position().x() < .5);
        assertTrue(next.position().y() < 1.3);
        assertFalse(next.grounded());
    }

    @Test
    void standingAndCreativeFlightCanLeaveTheLedge() {
        World world = emptyWorld();
        block(world, 0, 0, 0, BlockId.STONE);
        PlayerState crouched = standing(1.29, 1, .5);
        PlayerState walking = crouched.withCrouching(false);
        for (int tick = 0; tick < 30; tick++) walking = physics.step(world, walking, 1, 0, false, DT);
        assertTrue(walking.position().x() > 1.3);
        assertTrue(walking.position().y() < 1);
        PlayerState flying = physics.fly(world, crouched, 1, 0, 0, false, DT);
        assertTrue(flying.position().x() > 1.3);
        assertEquals(1, flying.position().y());
    }

    @Test
    void unknownSupportFreezesTheEntireStateAndPurePreviewCoversIt() {
        World world = emptyWorld();
        block(world, 15, 15, 0, BlockId.STONE);
        PlayerState start = new PlayerState(new PlayerState.Position(15.69, 16, .5), 1.2, 0, 0, true, .2, true);
        ChunkPos ahead = new ChunkPos(1, 0, 0);
        Chunk data = world.getChunk(ahead);
        world.removeChunk(ahead);
        var required = physics.requiredChunks(start, 1, 0, false, DT);
        assertTrue(required.contains(ahead));
        assertEquals(required, physics.requiredChunks(start, 1, 0, false, DT));
        assertSame(start, physics.step(world, start, 1, 0, false, DT));
        world.putChunk(ahead, data);
        assertNotSame(start, physics.step(world, start, 1, 0, false, DT));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void survivalAndCreativeWalkingShareProtectionAndReleaseAllowsFalling(boolean creative) {
        World world = emptyWorld();
        block(world, 0, 0, 0, BlockId.STONE);
        PlayerController player = new PlayerController();
        if (creative) player.setMode(world, PlayerController.Mode.CREATIVE);
        player.setPosition(new PlayerState.Position(.5, 1, .5));
        PlayerInput sneak = new PlayerInput(true, false, false, false, false, true, false);
        for (int tick = 0; tick < 180; tick++) player.tick(world, sneak, DT, 0, 0);
        assertTrue(player.state().grounded());
        assertTrue(player.state().position().x() > 1);
        assertTrue(player.state().body().minX() < 1);
        PlayerInput walk = new PlayerInput(true, false, false, false, false, false, false);
        for (int tick = 0; tick < 30; tick++) player.tick(world, walk, DT, 0, 0);
        assertFalse(player.state().crouching());
        assertTrue(player.state().position().y() < 1);
    }

    @Test
    void sneakEdgeScriptIsIndependentOfRenderRate() {
        PlayerState expected = script(60);
        for (int fps : new int[] {30, 144, 256}) assertEquals(expected, script(fps));
    }

    private PlayerState script(int fps) {
        World world = emptyWorld();
        block(world, 0, 0, 0, BlockId.STONE);
        PlayerState[] state = {standing(.5, 1, .5)};
        int[] ticks = {0};
        FixedSimulation simulation = new FixedSimulation(DT, 8);
        while (ticks[0] < 180)
            simulation.advance(1.0 / fps, dt -> {
                if (ticks[0] >= 180) return;
                int tick = ticks[0]++;
                if (tick == 150) block(world, 0, 0, 0, BlockId.AIR);
                state[0] = physics.step(world, state[0], 1, 1, tick == 90, dt);
            });
        return state[0];
    }

    private static PlayerState standing(double x, double y, double z) {
        return new PlayerState(new PlayerState.Position(x, y, z), 0, 0, 0, true, 0, true);
    }

    private static World emptyWorld() {
        World world = new World();
        for (ChunkPos pos : VoxelCollision.requiredChunks(new Aabb(-32, -16, -32, 32, 32, 32)))
            world.putChunk(pos, new Chunk());
        return world;
    }

    private static void block(World world, int x, int y, int z, byte id) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos local = Coordinates.worldToLocal(pos);
        world.getChunk(Coordinates.worldToChunk(pos)).set(local.x(), local.y(), local.z(), id);
    }
}

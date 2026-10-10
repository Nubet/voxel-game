package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class PlayerDynamicsTest {
    private final PlayerPhysics physics = new PlayerPhysics();

    @Test
    void walkingAcceleratesToReferenceSpeedThenCoastsToStop() {
        World world = flatWorld();
        PlayerState state = standing();
        state = physics.step(world, state, 1, 0, false, 1.0 / 60);
        assertTrue(state.velocityX() > 0 && state.velocityX() < 1);
        for (int tick = 1; tick < 120; tick++) state = physics.step(world, state, 1, 0, false, 1.0 / 60);
        assertEquals(4.317, state.velocityX(), 1e-8);
        assertTrue(state.position().x() > 8 && state.position().x() < 9);
        double releasedAt = state.position().x();
        state = physics.step(world, state, 0, 0, false, 1.0 / 60);
        assertTrue(state.velocityX() > 0);
        for (int tick = 1; tick < 30; tick++) state = physics.step(world, state, 0, 0, false, 1.0 / 60);
        assertTrue(state.velocityX() < .011);
        assertTrue(state.position().x() > releasedAt && state.position().x() < releasedAt + .4);
    }

    @Test
    void diagonalInputHasTheSameSpeedAndAirControlIsWeaker() {
        World world = flatWorld();
        PlayerState straight = physics.step(world, standing(), 1, 0, false, 1.0 / 60);
        PlayerState diagonal = physics.step(world, standing(), 1, 1, false, 1.0 / 60);
        assertEquals(straight.velocityX(), Math.hypot(diagonal.velocityX(), diagonal.velocityZ()), 1e-12);
        PlayerState airborne =
                physics.step(world, PlayerState.at(new PlayerState.Position(.5, 8, .5)), 1, 0, false, 1.0 / 60);
        assertTrue(airborne.velocityX() < straight.velocityX() * .2);
        PlayerState drifting = physics.step(
                world, new PlayerState(new PlayerState.Position(.5, 8, .5), 4, 0, 0, false, 0), 0, 0, false, 1.0 / 60);
        assertTrue(drifting.velocityX() > 3.8);
    }

    @Test
    void referenceDragIsPerFiftyMillisecondsAndComposesAcrossTickRates() {
        World world = flatWorld();
        PlayerState ground = new PlayerState(standing().position(), 4, 0, 0, true, 0);
        PlayerState coarse = physics.step(world, ground, 0, 0, false, .05);
        PlayerState fine = ground;
        for (int tick = 0; tick < 3; tick++) fine = physics.step(world, fine, 0, 0, false, 1.0 / 60);
        assertEquals(4 * .546, coarse.velocityX(), 1e-12);
        assertEquals(coarse.velocityX(), fine.velocityX(), 1e-12);
        PlayerState air = new PlayerState(new PlayerState.Position(.5, 8, .5), 4, -10, 0, false, 0);
        coarse = physics.step(world, air, 0, 0, false, .05);
        fine = air;
        for (int tick = 0; tick < 3; tick++) fine = physics.step(world, fine, 0, 0, false, 1.0 / 60);
        assertEquals(4 * .91, coarse.velocityX(), 1e-12);
        double drag = -Math.log(.98) / .05;
        assertEquals(-10 * .98 - 32 * .02 / drag, coarse.velocityY(), 1e-12);
        assertEquals(coarse.velocityY(), fine.velocityY(), 1e-12);
    }

    @Test
    void heldJumpCannotRepeatEarlyUnderALowCeilingOrResetCooldownInAir() {
        World world = flatWorld();
        world.getChunk(new ChunkPos(0, 0, 0)).set(0, 3, 0, BlockId.STONE);
        PlayerState state = standing();
        int takeoffs = 0;
        for (int tick = 0; tick < 30; tick++) {
            PlayerState next = physics.step(world, state, 0, 0, true, 1.0 / 60);
            if (state.grounded() && next.position().y() > state.position().y()) takeoffs++;
            assertTrue(next.position().y() <= 1.2 + 1e-12);
            state = next;
        }
        assertEquals(1, takeoffs);
        assertTrue(state.grounded());
        state = physics.step(world, state, 0, 0, true, 1.0 / 60);
        assertTrue(state.position().y() > 1);
        assertEquals(.5, state.jumpCooldownSeconds());
        PlayerState air = PlayerState.at(new PlayerState.Position(.5, 8, .5));
        assertEquals(physics.step(world, air, 0, 0, false, 1.0 / 60), physics.step(world, air, 0, 0, true, 1.0 / 60));
    }

    @Test
    void jumpAndWalkingAreCalibratedAtSixtyAndOneHundredTwentyTicksPerSecond() {
        double[] sixty = jumpMeasurement(60), faster = jumpMeasurement(120);
        assertEquals(1.25, sixty[0], .02);
        assertEquals(sixty[0], faster[0], .05);
        assertEquals(sixty[1], faster[1], 1.0 / 60);
        World world = flatWorld();
        PlayerState walk60 = standing(), walk120 = standing();
        for (int tick = 0; tick < 120; tick++) walk60 = physics.step(world, walk60, 1, 0, false, 1.0 / 60);
        for (int tick = 0; tick < 240; tick++) walk120 = physics.step(world, walk120, 1, 0, false, 1.0 / 120);
        assertEquals(walk60.velocityX(), walk120.velocityX(), 1e-10);
        assertEquals(walk60.position().x(), walk120.position().x(), .02);
        System.out.printf(
                "MV-03 flat CPU: 60 TPS apex=%.6f flight=%.6fs walk2s=%.6f; 120 TPS apex=%.6f flight=%.6fs walk2s=%.6f%n",
                sixty[0],
                sixty[1],
                walk60.position().x() - .5,
                faster[0],
                faster[1],
                walk120.position().x() - .5);
    }

    @Test
    void tickCommandScriptProducesIdenticalStatesAtDifferentRenderRates() {
        PlayerState expected = scriptedMovement(60);
        for (int fps : new int[] {30, 144, 256}) assertEquals(expected, scriptedMovement(fps));
    }

    @Test
    void previewIsPureAndLoadsTheActualMomentumAndJumpSweep() {
        World world = new World();
        PlayerState start = new PlayerState(new PlayerState.Position(15.5, 15, -.5), 90, 0, -90, true, .01);
        var required = physics.requiredChunks(start, 0, 0, true, 1.0 / 60);
        assertEquals(required, physics.requiredChunks(start, 0, 0, true, 1.0 / 60));
        assertSame(start, physics.step(world, start, 0, 0, true, 1.0 / 60));
        assertEquals(.01, start.jumpCooldownSeconds());
        for (ChunkPos position : required) world.putChunk(position, new Chunk());
        PlayerState result = physics.step(world, start, 0, 0, true, 1.0 / 60);
        assertNotEquals(start, result);
        assertTrue(result.position().x() > 16 && result.position().z() < -1);
        assertTrue(result.position().y() > 15);
        assertEquals(.5, result.jumpCooldownSeconds());
    }

    @Test
    void invalidDynamicsFailBeforeWorldAccess() {
        World world = new World();
        assertThrows(
                IllegalArgumentException.class, () -> physics.step(world, standing(), Double.NaN, 0, false, 1.0 / 60));
        assertThrows(
                IllegalArgumentException.class,
                () -> physics.requiredChunks(standing(), Double.MAX_VALUE, Double.MAX_VALUE, false, 1.0 / 60));
        assertThrows(IllegalArgumentException.class, () -> physics.step(world, standing(), 0, 0, false, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new PlayerState(standing().position(), 0, 0, 0, true, -.1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerState(standing().position(), 0, 0, 0, true, Double.NaN));
    }

    private double[] jumpMeasurement(int tps) {
        World world = flatWorld();
        PlayerState state = standing();
        double apex = 0;
        for (int tick = 0; tick < tps * 2; tick++) {
            state = physics.step(world, state, 0, 0, tick == 0, 1.0 / tps);
            apex = Math.max(apex, state.position().y() - 1);
            if (state.grounded()) return new double[] {apex, (tick + 1.0) / tps};
        }
        throw new AssertionError("Jump did not land");
    }

    private PlayerState scriptedMovement(int fps) {
        World world = flatWorld();
        PlayerController player = new PlayerController();
        player.setPosition(standing().position());
        FixedSimulation simulation = new FixedSimulation(1.0 / 60, 8);
        int[] ticks = {0};
        while (ticks[0] < 720)
            simulation.advance(1.0 / fps, dt -> {
                if (ticks[0] >= 720) return;
                int tick = ticks[0]++;
                PlayerInput input = new PlayerInput(
                        tick < 300,
                        tick >= 360 && tick < 540,
                        false,
                        tick >= 180 && tick < 300,
                        tick >= 120 && tick < 180,
                        false,
                        false);
                player.tick(world, input, dt, 0, 0);
            });
        return player.state();
    }

    private static PlayerState standing() {
        return new PlayerState(new PlayerState.Position(.5, 1, .5), 0, 0, 0, true, 0);
    }

    static World flatWorld() {
        World world = new World();
        for (int x = -2; x <= 3; x++)
            for (int z = -2; z <= 3; z++)
                for (int y = -2; y <= 1; y++) {
                    Chunk chunk = new Chunk();
                    if (y == 0)
                        for (int localX = 0; localX < Chunk.SIZE; localX++)
                            for (int localZ = 0; localZ < Chunk.SIZE; localZ++)
                                chunk.set(localX, 0, localZ, BlockId.STONE);
                    world.putChunk(new ChunkPos(x, y, z), chunk);
                }
        return world;
    }
}

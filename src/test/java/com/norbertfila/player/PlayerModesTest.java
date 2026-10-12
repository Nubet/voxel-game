package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerModesTest {
    private static final double DT = 1.0 / 60;
    private static final PlayerInput FORWARD = new PlayerInput(true, false, false, false, false, false, false);
    private static final PlayerInput SPRINT = new PlayerInput(true, false, false, false, false, false, true);

    @Test
    void sprintIsOnePointThreeTimesWalkAndRequiresForwardWithoutSneak() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController sprint = player(world, PlayerController.Mode.SURVIVAL);
        PlayerController backward = player(world, PlayerController.Mode.SURVIVAL);
        PlayerController sneaking = player(world, PlayerController.Mode.SURVIVAL);
        for (int tick = 0; tick < 120; tick++) {
            sprint.tick(world, SPRINT, DT, 0, 0);
            backward.tick(world, new PlayerInput(false, true, false, false, false, false, true), DT, 0, 0);
            sneaking.tick(world, new PlayerInput(true, false, false, false, false, true, true), DT, 0, 0);
        }
        assertEquals(4.317 * 1.3, sprint.state().velocityX(), 1e-8);
        assertEquals(-4.317, backward.state().velocityX(), 1e-8);
        assertEquals(4.317 * .3, sneaking.state().velocityX(), 1e-8);
    }

    @Test
    void sprintJumpAddsFacingImpulseOnceAndNotInAirOrDuringCooldown() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerPhysics physics = new PlayerPhysics();
        PlayerState start = new PlayerState(new PlayerState.Position(.5, 1, .5), 5.6121, 0, 0, true, 0, false);
        PlayerState jumping = physics.step(world, start, 1, 0, true, true, 90, DT);
        PlayerState walking = physics.step(world, start, 1, 0, false, true, 90, DT);
        assertEquals(walking.velocityX(), jumping.velocityX(), 1e-12);
        assertTrue(jumping.velocityZ() > 3 && jumping.velocityY() > 0);
        assertEquals(.5, jumping.jumpCooldownSeconds());
        assertEquals(
                physics.step(world, jumping, 1, 0, false, true, 90, DT),
                physics.step(world, jumping, 1, 0, true, true, 90, DT));
        PlayerState cooling = new PlayerState(start.position(), start.velocityX(), 0, 0, true, .3, false);
        assertEquals(
                physics.step(world, cooling, 1, 0, false, true, 90, DT),
                physics.step(world, cooling, 1, 0, true, true, 90, DT));
    }

    @Test
    void creativeWalkingUsesExactlyTheSurvivalPhysicsIncludingSprintAndJump() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController survival = player(world, PlayerController.Mode.SURVIVAL);
        PlayerController creative = player(world, PlayerController.Mode.CREATIVE);
        for (int tick = 0; tick < 120; tick++) {
            PlayerInput input = new PlayerInput(true, false, false, tick > 60, tick >= 30 && tick < 50, false, true);
            survival.tick(world, input, DT, -90, 25);
            creative.tick(world, input, DT, -90, 25);
            assertEquals(survival.state(), creative.state());
            assertEquals(survival.renderEyePosition(.5), creative.renderEyePosition(.5));
        }
        assertFalse(creative.flying());
    }

    @Test
    void doubleTapUsesEventSecondsNotTicksAndHeldSpaceNeverTogglesFlight() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController creative = player(world, PlayerController.Mode.CREATIVE);
        creative.tick(world, press(1), DT, 0, 0);
        creative.tick(world, press(1.35), DT, 0, 0);
        assertTrue(creative.flying());
        PlayerState.Position hovering = creative.state().position();
        for (int tick = 0; tick < 60; tick++) creative.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertEquals(hovering, creative.state().position());
        creative.tick(world, press(2), DT, 0, 0);
        creative.tick(world, press(2.2), DT, 0, 0);
        assertFalse(creative.flying());
        assertTrue(creative.state().velocityY() < 0);

        PlayerController held = player(world, PlayerController.Mode.CREATIVE);
        held.tick(world, press(1), DT, 0, 0);
        for (int tick = 0; tick < 120; tick++)
            held.tick(world, new PlayerInput(false, false, false, false, true, false, false), DT, 0, 0);
        assertFalse(held.flying());
    }

    @Test
    void tapsOutsideTheWindowOrClearedByPauseDoNotToggle() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController creative = player(world, PlayerController.Mode.CREATIVE);
        creative.tick(world, press(1), DT, 0, 0);
        creative.tick(world, press(1.351), DT, 0, 0);
        assertFalse(creative.flying());
        creative.clearInputGestures();
        creative.tick(world, press(1.5), DT, 0, 0);
        assertFalse(creative.flying());
        creative.tick(world, press(1.5), DT, 0, 0);
        assertFalse(creative.flying());
        creative.tick(world, press(1.7), DT, 0, 0);
        assertTrue(creative.flying());
    }

    @Test
    void flightPreviewAndMissingDataFreezeTheToggleAndEntireState() {
        World world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        PlayerController creative = new PlayerController();
        creative.setMode(world, PlayerController.Mode.CREATIVE);
        creative.setPosition(new PlayerState.Position(15.68, 4, .5));
        creative.tick(world, press(1), DT, 0, 0);
        PlayerInput second = new PlayerInput(true, false, false, false, false, false, false, true, 1.2);
        PlayerState before = creative.state();
        var required = creative.requiredCollisionChunks(second, DT, 0, 0);
        assertTrue(required.contains(new ChunkPos(1, 0, 0)));
        assertEquals(required, creative.requiredCollisionChunks(second, DT, 0, 0));
        assertFalse(creative.flying());
        creative.tick(world, second, DT, 0, 0);
        assertSame(before, creative.state());
        assertFalse(creative.flying());
        for (ChunkPos position : required) if (!world.hasChunk(position)) world.putChunk(position, new Chunk());
        creative.tick(world, second, DT, 0, 0);
        assertTrue(creative.flying());
        assertTrue(creative.state().position().x() > before.position().x());
    }

    @Test
    void creativeFlightHitsWallsAndCeilingsAndLandingEndsFlight() {
        World world = PlayerDynamicsTest.flatWorld();
        Chunk chunk = world.getChunk(new ChunkPos(0, 0, 0));
        for (int y = 1; y <= 4; y++) chunk.set(2, y, 0, BlockId.STONE);
        chunk.set(1, 3, 0, BlockId.STONE);
        PlayerController creative = flyingPlayer(world);
        for (int tick = 0; tick < 120; tick++) creative.tick(world, FORWARD, DT, 0, 85);
        assertEquals(1.7, creative.state().position().x(), 1e-12);
        assertEquals(0, creative.state().velocityX());
        assertTrue(creative.flying());
        for (int tick = 0; tick < 60; tick++)
            creative.tick(world, new PlayerInput(false, false, false, false, true, false, false), DT, 0, 0);
        assertEquals(1.2, creative.state().position().y(), 1e-12);
        assertEquals(0, creative.state().velocityY());
        assertFalse(creative.state().grounded());
        for (int tick = 0; tick < 60; tick++)
            creative.tick(world, new PlayerInput(false, false, false, false, false, true, false), DT, 0, 0);
        assertEquals(1, creative.state().position().y());
        assertTrue(creative.state().grounded());
        assertFalse(creative.flying());
    }

    @Test
    void flightHasSecondBasedAccelerationDragSprintAndNormalizedThreeDimensionalInput() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerPhysics physics = new PlayerPhysics();
        PlayerState start = PlayerState.at(new PlayerState.Position(.5, 8, .5));
        PlayerState straight = physics.fly(world, start, 1, 0, 0, false, DT);
        PlayerState diagonal = physics.fly(world, start, 1, 1, 1, false, DT);
        assertEquals(
                straight.velocityX(),
                Math.hypot(Math.hypot(diagonal.velocityX(), diagonal.velocityZ()), diagonal.velocityY()),
                1e-12);
        assertEquals(
                straight.velocityX() * 2,
                physics.fly(world, start, 1, 0, 0, true, DT).velocityX(),
                1e-12);
        PlayerState sixty = start, faster = start;
        for (int tick = 0; tick < 60; tick++) sixty = physics.fly(world, sixty, 1, 0, 0, false, DT);
        for (int tick = 0; tick < 120; tick++) faster = physics.fly(world, faster, 1, 0, 0, false, DT / 2);
        assertEquals(10.92, sixty.velocityX(), .001);
        assertEquals(sixty.velocityX(), faster.velocityX(), 1e-12);
        assertEquals(sixty.position().x(), faster.position().x(), .05);
        assertTrue(physics.fly(world, sixty, 0, 0, 0, false, DT).velocityX() > 0);
        assertThrows(IllegalArgumentException.class, () -> physics.fly(world, start, 0, Double.NaN, 0, false, DT));
    }

    @Test
    void modeCyclePreservesEyesAndResetsVelocityCooldownFlightAndInterpolation() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController player = flyingPlayer(world);
        player.tick(world, FORWARD, DT, 0, 0);
        PlayerState.Position eyes = player.renderEyePosition(1);
        assertTrue(player.state().velocityX() > 0);
        for (PlayerController.Mode expected : new PlayerController.Mode[] {
            PlayerController.Mode.SPECTATOR, PlayerController.Mode.SURVIVAL, PlayerController.Mode.CREATIVE
        }) {
            player.requestNextMode();
            player.tick(world, PlayerInput.NONE, DT, 0, 0);
            assertEquals(expected, player.mode());
            assertEquals(PlayerState.at(player.state().position()), player.state());
            assertEquals(eyes, player.renderEyePosition(0));
            assertEquals(eyes, player.renderEyePosition(1));
            assertFalse(player.flying());
            assertFalse(player.modeChangePending());
        }
    }

    @Test
    void noclipExitRejectsSolidOrUnknownBodyAndRequestsMissingSectionsUntilReady() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController spectator = player(world, PlayerController.Mode.SPECTATOR);
        world.getChunk(new ChunkPos(0, 0, 0)).set(0, 1, 0, BlockId.STONE);
        PlayerState before = spectator.state();
        assertFalse(spectator.setMode(world, PlayerController.Mode.SURVIVAL));
        spectator.requestNextMode();
        spectator.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertEquals(PlayerController.Mode.SPECTATOR, spectator.mode());
        assertSame(before, spectator.state());
        assertFalse(spectator.modeChangePending());
        spectator.setPosition(new PlayerState.Position(-1000.5, 50, -1000.5));
        assertFalse(spectator.setMode(world, PlayerController.Mode.CREATIVE));
        spectator.requestNextMode();
        before = spectator.state();
        var required = spectator.requiredCollisionChunks(PlayerInput.NONE, DT, 0, 0);
        assertFalse(required.isEmpty());
        spectator.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertSame(before, spectator.state());
        assertTrue(spectator.modeChangePending());
        for (ChunkPos position : required) world.putChunk(position, new Chunk());
        spectator.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertEquals(PlayerController.Mode.SURVIVAL, spectator.mode());
        assertEquals(before.position(), spectator.state().position());
        assertFalse(spectator.modeChangePending());
    }

    @Test
    void spectatorIgnoresSolidAndUnknownDataAndCreativeRequiresWorld() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController spectator = player(world, PlayerController.Mode.SPECTATOR);
        assertTrue(spectator.requiredCollisionChunks(SPRINT, DT, 0, 0).isEmpty());
        world.getChunk(new ChunkPos(0, 0, 0)).set(1, 1, 0, BlockId.STONE);
        for (int tick = 0; tick < 120; tick++) spectator.tick(null, SPRINT, DT, 0, 0);
        assertTrue(spectator.state().position().x() > 64);
        PlayerController creative = player(world, PlayerController.Mode.CREATIVE);
        assertThrows(NullPointerException.class, () -> creative.tick(null, FORWARD, DT, 0, 0));
    }

    @Test
    void cyclingBackToSpectatorCancelsAnExitWaitingForMissingData() {
        PlayerController spectator = new PlayerController();
        spectator.setMode(null, PlayerController.Mode.SPECTATOR);
        PlayerState before = spectator.state();
        spectator.requestNextMode();
        assertFalse(
                spectator.requiredCollisionChunks(PlayerInput.NONE, DT, 0, 0).isEmpty());
        spectator.requestNextMode();
        spectator.requestNextMode();
        assertTrue(spectator.requiredCollisionChunks(PlayerInput.NONE, DT, 0, 0).isEmpty());
        spectator.tick(null, PlayerInput.NONE, DT, 0, 0);
        assertSame(before, spectator.state());
        assertFalse(spectator.modeChangePending());
        assertEquals(PlayerController.Mode.SPECTATOR, spectator.mode());
    }

    @Test
    void teleportClearsFlightGestureAndPendingModeChange() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController creative = flyingPlayer(world);
        creative.requestNextMode();
        PlayerState.Position target = new PlayerState.Position(.5, 4, .5);
        creative.setPosition(target);
        assertFalse(creative.flying());
        assertFalse(creative.modeChangePending());
        assertEquals(PlayerState.at(target), creative.state());
        assertEquals(target, creative.renderPosition(0));
        creative.tick(world, press(1.3), DT, 0, 0);
        assertFalse(creative.flying());
    }

    @Test
    void creativeFlightAndModeCommandScriptIsIndependentOfRenderRate() {
        PlayerState expected = scriptedModes(60);
        for (int fps : new int[] {30, 144, 256}) assertEquals(expected, scriptedModes(fps));
    }

    private static PlayerState scriptedModes(int fps) {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController player = player(world, PlayerController.Mode.CREATIVE);
        FixedSimulation simulation = new FixedSimulation(DT, 8);
        int[] ticks = {0};
        while (ticks[0] < 480)
            simulation.advance(1.0 / fps, dt -> {
                if (ticks[0] >= 480) return;
                int tick = ticks[0]++;
                if (tick == 240 || tick == 330) player.requestNextMode();
                boolean pressed = tick == 30 || tick == 45 || tick == 90 || tick == 105;
                PlayerInput input = new PlayerInput(
                        tick < 200,
                        false,
                        false,
                        false,
                        tick >= 60 && tick < 75,
                        false,
                        tick >= 120,
                        pressed,
                        tick * dt);
                player.tick(world, input, dt, 0, 0);
            });
        assertEquals(PlayerController.Mode.SURVIVAL, player.mode());
        assertFalse(player.flying());
        return player.state();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1024, -17, -1, 0, 1, 3, 16, 1024, 1000000})
    void repeatedUpwardFlightNeverReconstructsTheBodyInsideTheCeiling(int ceilingY) {
        PlayerState state = PlayerState.at(new PlayerState.Position(.5, ceilingY - 2.5, .5));
        World world = new World();
        for (ChunkPos position : VoxelCollision.requiredChunks(
                VoxelCollision.requiredRegion(PlayerPhysics.body(state.position()), 0, 2, 0)))
            world.putChunk(position, new Chunk());
        BlockPos block = new BlockPos(0, ceilingY, 0);
        BlockPos local = Coordinates.worldToLocal(block);
        world.getChunk(Coordinates.worldToChunk(block)).set(local.x(), local.y(), local.z(), BlockId.STONE);
        PlayerPhysics physics = new PlayerPhysics();
        for (int tick = 0; tick < 300; tick++) {
            state = physics.fly(world, state, 0, 1, 0, false, DT);
            assertTrue(PlayerPhysics.body(state.position()).maxY() <= ceilingY);
            assertFalse(state.grounded());
        }
        assertEquals(ceilingY - 1.8, state.position().y(), 1e-10);
        assertEquals(0, state.velocityY());
    }

    private static PlayerInput press(double time) {
        return new PlayerInput(false, false, false, false, false, false, false, true, time);
    }

    private static PlayerController player(World world, PlayerController.Mode mode) {
        PlayerController player = new PlayerController();
        player.setPosition(new PlayerState.Position(.5, 1, .5));
        assertTrue(player.setMode(world, mode));
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        return player;
    }

    private static PlayerController flyingPlayer(World world) {
        PlayerController creative = player(world, PlayerController.Mode.CREATIVE);
        creative.tick(world, press(1), DT, 0, 0);
        creative.tick(world, press(1.2), DT, 0, 0);
        assertTrue(creative.flying());
        return creative;
    }
}

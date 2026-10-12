package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class PlayerCrouchTest {
    private static final double DT = 1.0 / 60;
    private static final PlayerInput SNEAK = new PlayerInput(false, false, false, false, false, true, false);
    private static final PlayerInput FORWARD = new PlayerInput(true, false, false, false, false, false, false);

    @Test
    void crouchChangesBodyAndInterpolatedEyesWithoutMovingFeet() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController player = standing(world);
        PlayerState.Position feet = player.state().position();
        player.tick(world, SNEAK, DT, 0, 0);
        assertTrue(player.state().crouching());
        assertEquals(feet, player.state().position());
        assertEquals(.6, player.state().body().maxX() - player.state().body().minX(), 1e-12);
        assertEquals(1.5, player.state().body().maxY() - player.state().body().minY());
        assertEquals(1.62, player.renderEyePosition(0).y() - feet.y(), 1e-12);
        assertEquals((1.62 + 1.27) / 2, player.renderEyePosition(.5).y() - feet.y(), 1e-12);
        assertEquals(1.27, player.renderEyePosition(1).y() - feet.y(), 1e-12);
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertFalse(player.state().crouching());
        assertEquals(feet, player.state().position());
        assertEquals(1.8, player.state().height());
        assertEquals(1.27, player.renderEyePosition(0).y() - feet.y(), 1e-12);
        assertEquals(1.62, player.renderEyePosition(1).y() - feet.y(), 1e-12);
    }

    @Test
    void crouchedJumpUsesTheShorterBodyWhenClippingAgainstACeiling() {
        World world = PlayerDynamicsTest.flatWorld();
        world.getChunk(new ChunkPos(0, 0, 0)).set(0, 3, 0, BlockId.STONE);
        PlayerPhysics physics = new PlayerPhysics();
        PlayerState standing = new PlayerState(new PlayerState.Position(.5, 1, .5), 0, 0, 0, true, 0, false);
        PlayerState crouched = standing.withCrouching(true);
        double standingApex = 1, crouchedApex = 1;
        for (int tick = 0; tick < 12; tick++) {
            standing = physics.step(world, standing, 0, 0, tick == 0, DT);
            crouched = physics.step(world, crouched, 0, 0, tick == 0, DT);
            standingApex = Math.max(standingApex, standing.position().y());
            crouchedApex = Math.max(crouchedApex, crouched.position().y());
            assertTrue(standing.body().maxY() <= 3);
            assertTrue(crouched.body().maxY() <= 3);
            assertTrue(crouched.crouching());
        }
        assertEquals(1.2, standingApex, 1e-12);
        assertEquals(1.5, crouchedApex, 1e-12);
    }

    @Test
    void releasingSneakUnderTheCeilingKeepsTheActualPoseUntilClearanceReturns() {
        World world = PlayerDynamicsTest.flatWorld();
        Chunk chunk = world.getChunk(new ChunkPos(0, 0, 0));
        chunk.set(0, 3, 0, BlockId.STONE);
        PlayerController player = standing(world);
        player.tick(world, new PlayerInput(false, false, false, false, true, true, false), DT, 0, 0);
        for (int tick = 0; tick < 2; tick++) player.tick(world, SNEAK, DT, 0, 0);
        assertTrue(player.state().position().y() > 1.2);
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertTrue(player.state().crouching());
        assertTrue(player.state().body().maxY() <= 3);
        assertEquals(1.27, player.state().eyeHeight());
        assertFalse(new VoxelCollision()
                .spaceFree(world, player.state().withCrouching(false).body()));
        chunk.set(0, 3, 0, BlockId.AIR);
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertFalse(player.state().crouching());
        assertEquals(1.62, player.state().eyeHeight());
        assertTrue(new VoxelCollision().spaceFree(world, player.state().body()));
    }

    @Test
    void releasePreviewCoversStandingMotionAndWaitsWithoutMutatingPoseOrVelocity() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController player = standing(world);
        player.setPosition(new PlayerState.Position(15.69, 1, .5));
        player.tick(world, SNEAK, DT, 0, 0);
        PlayerState before = player.state();
        ChunkPos ahead = new ChunkPos(1, 0, 0);
        Chunk data = world.getChunk(ahead);
        world.removeChunk(ahead);
        var required = player.requiredCollisionChunks(FORWARD, DT, 0, 0);
        assertTrue(required.contains(ahead));
        assertEquals(required, player.requiredCollisionChunks(FORWARD, DT, 0, 0));
        assertSame(before, player.state());
        assertFalse(new PlayerPhysics().requiredChunks(before, 1, 0, false, DT).contains(ahead));
        player.tick(world, FORWARD, DT, 0, 0);
        assertSame(before, player.state());
        assertEquals(1.27, player.renderEyePosition(.5).y() - before.position().y(), 1e-12);
        world.putChunk(ahead, data);
        player.tick(world, FORWARD, DT, 0, 0);
        assertFalse(player.state().crouching());
        assertTrue(player.state().position().x() > before.position().x());
    }

    @Test
    void unknownExpansionSpaceFreezesTheWholeCrouchedState() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController player = standing(world);
        player.tick(world, SNEAK, DT, 0, 0);
        PlayerState before = player.state();
        world.removeChunk(new ChunkPos(0, 0, 0));
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertSame(before, player.state());
        assertTrue(player.state().crouching());
    }

    @Test
    void crouchSpeedIsAppliedAfterNormalizingDiagonalInputAndDisablesSprintJump() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController straight = standing(world), diagonal = standing(world);
        for (int tick = 0; tick < 120; tick++) {
            straight.tick(world, new PlayerInput(true, false, false, false, false, true, true), DT, 0, 0);
            diagonal.tick(world, new PlayerInput(true, false, false, true, false, true, true), DT, 0, 0);
        }
        assertEquals(1.2951, straight.state().velocityX(), 1e-8);
        assertEquals(
                straight.state().velocityX(),
                Math.hypot(diagonal.state().velocityX(), diagonal.state().velocityZ()),
                1e-12);
        PlayerPhysics physics = new PlayerPhysics();
        PlayerState start = straight.state();
        assertEquals(
                physics.step(world, start, 1, 0, true, false, 0, DT),
                physics.step(world, start, 1, 0, true, true, 0, DT));
    }

    @Test
    void flightShiftIsDescentWithoutRequestingCrouch() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController creative = new PlayerController();
        creative.setMode(world, PlayerController.Mode.CREATIVE);
        creative.setPosition(new PlayerState.Position(.5, 8, .5));
        creative.tick(world, press(1, false), DT, 0, 0);
        creative.tick(world, press(1.2, false), DT, 0, 0);
        assertTrue(creative.flying());
        double before = creative.state().position().y();
        creative.tick(world, SNEAK, DT, 0, 0);
        assertTrue(creative.flying());
        assertFalse(creative.state().crouching());
        assertTrue(creative.state().position().y() < before);
        assertEquals(1.62, creative.state().eyeHeight());
        creative.setMode(world, PlayerController.Mode.SPECTATOR);
        before = creative.state().position().y();
        creative.tick(null, SNEAK, DT, 0, 0);
        assertFalse(creative.state().crouching());
        assertEquals(before - 12 * DT, creative.state().position().y(), 1e-12);
    }

    @Test
    void flightToggleCannotExpandAForcedCrouchIntoTheCeiling() {
        World world = PlayerDynamicsTest.flatWorld();
        Chunk chunk = world.getChunk(new ChunkPos(0, 0, 0));
        chunk.set(0, 3, 0, BlockId.STONE);
        PlayerController creative = new PlayerController();
        creative.setMode(world, PlayerController.Mode.CREATIVE);
        creative.setPosition(new PlayerState.Position(.5, 1.3, .5));
        creative.tick(world, SNEAK, DT, 0, 0);
        creative.tick(world, press(1, true), DT, 0, 0);
        creative.tick(world, press(1.2, false), DT, 0, 0);
        for (int tick = 0; tick < 20; tick++) creative.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertTrue(creative.flying());
        assertTrue(creative.state().crouching());
        assertTrue(new VoxelCollision().spaceFree(world, creative.state().body()));
        PlayerState.Position hovering = creative.state().position();
        chunk.set(0, 3, 0, BlockId.AIR);
        creative.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertEquals(hovering, creative.state().position());
        assertFalse(creative.state().crouching());
    }

    @Test
    void creativeWalkingAndModeChangesRespectTheCurrentCrouchedBody() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController player = standing(world);
        player.tick(world, SNEAK, DT, 0, 0);
        PlayerState.Position eyes = player.renderEyePosition(1);
        player.setMode(world, PlayerController.Mode.CREATIVE);
        assertTrue(player.state().crouching());
        assertEquals(eyes, player.renderEyePosition(0));
        player.tick(world, SNEAK, DT, 0, 0);
        assertTrue(player.state().crouching());
        player.setMode(world, PlayerController.Mode.SPECTATOR);
        assertFalse(player.state().crouching());
        assertEquals(1.62, player.state().eyeHeight());
    }

    @Test
    void teleportPreservesSmallPoseUntilStandingSpaceIsKnownAndClear() {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController player = standing(world);
        player.tick(world, SNEAK, DT, 0, 0);
        world.getChunk(new ChunkPos(0, 0, 0)).set(0, 10, 0, BlockId.STONE);
        PlayerState.Position target = new PlayerState.Position(.5, 8.4, .5);
        player.setPosition(target);
        assertEquals(target, player.renderPosition(0));
        assertTrue(player.state().crouching());
        assertEquals(0, player.state().velocityY());
        assertEquals(target.y() + 1.27, player.renderEyePosition(0).y(), 1e-12);
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        assertTrue(player.state().crouching());
        assertTrue(new VoxelCollision().spaceFree(world, player.state().body()));
    }

    @Test
    void crouchAndJumpCommandScriptIsIndependentOfRenderRate() {
        PlayerState expected = scriptedCrouch(60);
        for (int fps : new int[] {30, 144, 256}) assertEquals(expected, scriptedCrouch(fps));
    }

    private static PlayerState scriptedCrouch(int fps) {
        World world = PlayerDynamicsTest.flatWorld();
        PlayerController player = standing(world);
        FixedSimulation simulation = new FixedSimulation(DT, 8);
        int[] ticks = {0};
        while (ticks[0] < 360)
            simulation.advance(1.0 / fps, dt -> {
                if (ticks[0] >= 360) return;
                int tick = ticks[0]++;
                player.tick(
                        world,
                        new PlayerInput(
                                tick < 240,
                                false,
                                false,
                                tick < 120,
                                tick >= 60 && tick < 80,
                                tick >= 30 && tick < 120,
                                true),
                        dt,
                        0,
                        0);
            });
        return player.state();
    }

    private static PlayerController standing(World world) {
        PlayerController player = new PlayerController();
        player.setPosition(new PlayerState.Position(.5, 1, .5));
        player.tick(world, PlayerInput.NONE, DT, 0, 0);
        return player;
    }

    private static PlayerInput press(double time, boolean sneak) {
        return new PlayerInput(false, false, false, false, false, sneak, false, true, time);
    }
}
